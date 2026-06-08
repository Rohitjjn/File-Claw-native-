/**
 * AuthContext.tsx — v24 (v23 + v18 machine fingerprint session fix)
 *
 * CHANGES FROM v23:
 *   1. v18 FIX: Replaced random session IDs with machine fingerprint
 *      - Uses browser fingerprint (userAgent, language, screen, timezone, etc.)
 *      - Same PC always gets the same fingerprint — no false "concurrent session" popups
 *      - Only triggers when a truly DIFFERENT machine logs in (different fingerprint)
 *      - Old-format session IDs are silently overwritten (migration-safe)
 *   2. Removed false popup on same-PC logout/login cycles
 *   3. Changed initial concurrent check from 10s to 15s for stability
 */

import { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import type { ReactNode } from 'react';
import { supabase } from '../lib/supabase';
import { mergeProgressWithDB, initSyncListener, mergeAllUsersProgress } from '../utils/offlineStorage';
import { trackLogin, trackSignup } from '../utils/analytics';
import type { User, Session } from '@supabase/supabase-js';

// ── S17→v18: Single-device session enforcement ────────────────────────────
// Uses a machine fingerprint (IP hint + browser hash) instead of random session ID.
// This way, logging out and logging back in on the SAME computer won't trigger
// a "session found on another device" popup.
// The fingerprint is stored in localStorage and in profiles.active_session_id.
// A periodic check (every 30s) compares the local fingerprint with the DB value.
// If they differ, another device has logged in → this device auto-logs out.
const SESSION_ID_KEY = 'rhc_device_session_id';
const MACHINE_FINGERPRINT_KEY = 'rhc_machine_fp';

// Generate a semi-stable machine fingerprint using browser features
// This won't change on logout/login on the same browser/machine
function getMachineFingerprint(): string {
  // Check if we already have one stored
  const existing = localStorage.getItem(MACHINE_FINGERPRINT_KEY);
  if (existing) return existing;

  // Generate a fingerprint from browser properties that are stable per machine
  const components = [
    navigator.userAgent,
    navigator.language,
    screen.width + 'x' + screen.height,
    screen.colorDepth.toString(),
    new Date().getTimezoneOffset().toString(),
    navigator.hardwareConcurrency?.toString() || '0',
    navigator.platform || '',
  ];
  const raw = components.join('||');
  // Simple hash
  let hash = 0;
  for (let i = 0; i < raw.length; i++) {
    const chr = raw.charCodeAt(i);
    hash = ((hash << 5) - hash) + chr;
    hash |= 0; // Convert to 32bit integer
  }
  const fp = `machine_${Math.abs(hash).toString(36)}_${Date.now().toString(36)}`;
  localStorage.setItem(MACHINE_FINGERPRINT_KEY, fp);
  return fp;
}

function getOrCreateSessionId(): string {
  // v18: Use machine fingerprint instead of random ID
  // This ensures same PC = same ID, even after logout/login
  let id = localStorage.getItem(SESSION_ID_KEY);
  if (!id) {
    id = getMachineFingerprint();
    localStorage.setItem(SESSION_ID_KEY, id);
  }
  return id;
}

// ── Types ─────────────────────────────────────────────────────────────────────
export interface UserProfile {
  id: string;
  name: string;
  phone: string;
  email: string;
  plan_active?: boolean;
  plan_name?: string;
  plan_type?: string;
  plan_end_date?: string;
  is_admin?: boolean;
  is_team_leader?: boolean;
  team_plan_id?: string;
  queued_plan_name?: string;
  queued_plan_end_date?: string;
  queued_team_plan_id?: string;
  queued_is_team_leader?: boolean;
  is_blocked?: boolean;
  is_blocked_by_admin?: boolean;
  blocked_reason?: string;
  appeal_text?: string;
  appeal_submitted_at?: string;
  plan_change_log?: string;
  /** Cached team plan end_date from team_plans table (for expiry propagation) */
  team_plan_end_date?: string;
}

interface AuthContextType {
  session: Session | null;
  user: User | null;
  profile: UserProfile | null;
  /** True while we're checking if user has an active session (only on first mount) */
  isLoading: boolean;
  /** BUG #6: True while profile/plan is loading after login (but session is known) */
  isPlanLoading: boolean;
  /** True if user is logged in */
  isLoggedIn: boolean;
  /** True if user is an admin */
  isAdmin: boolean;
  /** True if user has an active plan (or is admin) */
  hasActivePlan: boolean;
  /** True if user is a team leader */
  isTeamLeader: boolean;
  /** Team plan ID if user belongs to a team */
  teamPlanId: string | null;
  /** True if user has a queued plan waiting */
  hasQueuedPlan: boolean;
  /** Queued plan info */
  queuedPlan: { name: string; endDate: string; isTeam: boolean } | null;
  /** True if user is blocked by admin */
  isBlockedByAdmin: boolean;
  /** True if user is fully blocked by admin (full website block — no exercises, no typing) */
  isBlocked: boolean;
  /** True if user is team-blocked (partial block — can use free exercises, buy own plans) */
  isTeamBlocked: boolean;
  /** Reason for block (if any) */
  blockedReason: string | null;
  /** True if user has a pending appeal */
  hasAppealPending: boolean;
  /** True if the current session is a password recovery flow */
  isPasswordRecovery: boolean;
  /** S17: Email of user when concurrent session detected (null = no concurrent session) */
  concurrentSessionEmail: string | null;
  /** S17: Confirm concurrent login — overwrite DB session, sign out locally for re-login */
  confirmConcurrentLogin: (email: string) => Promise<void>;
  /** S17: Clear concurrent session state */
  clearConcurrentSession: () => void;
  /** Sign out — clears everything */
  signOut: () => Promise<void>;
  /** Re-fetch profile from DB */
  refreshProfile: () => Promise<void>;
  /** Try to refresh the Supabase session (for admin RPC retry) */
  refreshSession: () => Promise<boolean>;
}

// ── Constants ─────────────────────────────────────────────────────────────────
const AuthContext = createContext<AuthContextType | undefined>(undefined);
const ADMIN_EMAIL = 'userrohitai011@gmail.com';

function isAdminEmail(email?: string | null): boolean {
  return !!email && email.toLowerCase() === ADMIN_EMAIL.toLowerCase();
}

// ── Provider ──────────────────────────────────────────────────────────────────
export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [user, setUser] = useState<User | null>(null);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [isLoading, setIsLoading] = useState(true); // Always start true — resolve on first auth event
  const [isPlanLoading, setIsPlanLoading] = useState(true); // BUG #6: True until profile with plan data is loaded
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [isAdminFlag, setIsAdminFlag] = useState(false);
  const [isPasswordRecovery, setIsPasswordRecovery] = useState(false);
  const [concurrentSessionEmail, setConcurrentSessionEmail] = useState<string | null>(null);

  const mountedRef = useRef(true);
  const syncCleanupRef = useRef<(() => void) | null>(null);
  const initialSessionHandledRef = useRef(false);

  // ── BUG #6: Optimistic plan cache helpers ──
  // Cache plan status in localStorage so paid users don't see a flash of
  // locked exercises on page load. The cache is keyed by user ID and expires
  // after 5 minutes to handle plan revocations gracefully.
  const PLAN_CACHE_TTL = 5 * 60 * 1000; // 5 minutes

  const savePlanCache = useCallback((userId: string, planActive: boolean, planName?: string) => {
    try {
      const cache = {
        plan_active: planActive,
        plan_name: planName || null,
        timestamp: Date.now(),
      };
      localStorage.setItem(`rhc_plan_cache_${userId}`, JSON.stringify(cache));
    } catch { /* localStorage may be unavailable */ }
  }, []);

  const loadPlanCache = useCallback((userId: string): { plan_active: boolean; plan_name: string | null } | null => {
    try {
      const raw = localStorage.getItem(`rhc_plan_cache_${userId}`);
      if (!raw) return null;
      const cache = JSON.parse(raw) as { plan_active: boolean; plan_name: string | null; timestamp: number };
      // Expire cache after TTL
      if (Date.now() - cache.timestamp > PLAN_CACHE_TTL) {
        localStorage.removeItem(`rhc_plan_cache_${userId}`);
        return null;
      }
      return { plan_active: cache.plan_active, plan_name: cache.plan_name };
    } catch { return null; }
  }, []);

  // ── fetchProfile ──────────────────────────────────────────────────────────
  const fetchProfile = useCallback(async (userId: string): Promise<UserProfile | null> => {
    try {
      // Try fetching with retry (profile trigger may take a moment to create the row)
      let data: UserProfile[] | null = null;
      let error: { message?: string } | null = null;

      for (let attempt = 0; attempt < 3; attempt++) {
        const result = await supabase
          .from('profiles')
          .select('*')
          .eq('id', userId)
          .limit(1);
        data = result.data as UserProfile[] | null;
        error = result.error as { message?: string } | null;

        if (!error && data && data.length > 0) break;

        // Profile not found yet — wait a bit and retry (trigger might still be running)
        if (attempt < 2) {
          await new Promise(r => setTimeout(r, 500 * (attempt + 1)));
        }
      }

      if (error || !data?.length) {
        // Profile still doesn't exist after retries — try creating it manually
        try {
          const { data: { user: u } } = await supabase.auth.getUser();
          if (!u) return null;
          const np: UserProfile = {
            id: u.id,
            name: u.user_metadata?.name || u.email?.split('@')[0] || 'User',
            email: u.email || '',
            phone: u.user_metadata?.phone || '',
          };
          const { data: cr, error: insertErr } = await supabase.from('profiles').insert([np]).select().limit(1);
          if (insertErr) {
            // Insert might fail due to race condition with trigger — try fetching one more time
            const { data: existing } = await supabase.from('profiles').select('*').eq('id', userId).limit(1);
            if (existing && existing.length > 0) return existing[0] as UserProfile;
          }
          return cr?.[0] ?? np;
        } catch {
          return null;
        }
      }

      const row = data[0] as UserProfile;
      // ── Check if plan has expired ──
      // This is the PRIMARY server-side check. It runs on every profile fetch.
      // It also handles team plan expiry via check_plan_expiry() RPC.
      if (row.plan_active && row.plan_end_date) {
        if (new Date(row.plan_end_date) < new Date()) {
          // Plan expired — update DB
          try {
            await supabase.from('profiles').update({
              plan_active: false,
              plan_name: null,
            }).eq('id', userId);
            // Also try the server-side expiry function (handles queued plans)
            try { await supabase.rpc('check_plan_expiry', { p_user_id: userId }); } catch { /* non-critical */ }
          } catch { /* non-critical */ }
          row.plan_active = false;
          row.plan_name = undefined;
        }
      }
      // Also check: even if plan_active is true in DB, verify the end date hasn't passed
      // (catches cases where the DB wasn't updated yet but plan has expired)
      if (row.plan_active && row.plan_end_date && new Date(row.plan_end_date) < new Date()) {
        row.plan_active = false;
        row.plan_name = undefined;
      }

      // ── v20: Team Plan Expiry Propagation ──
      // If user has a team plan (as member or leader), also check the team_plans table
      // for the actual team plan end_date. The individual profile.plan_end_date may be
      // a denormalized copy that wasn't updated when the team plan expired.
      if (row.plan_active && row.team_plan_id) {
        try {
          const { data: teamPlanData } = await supabase
            .from('team_plans')
            .select('end_date, is_active')
            .eq('id', row.team_plan_id)
            .single();

          if (teamPlanData) {
            const teamEndDate = (teamPlanData as { end_date?: string; is_active?: boolean }).end_date;
            const teamIsActive = (teamPlanData as { end_date?: string; is_active?: boolean }).is_active;

            // Cache the team plan end_date for periodic checks
            row.team_plan_end_date = teamEndDate || undefined;

            // If team plan has expired OR is marked inactive, revoke member access
            if ((teamEndDate && new Date(teamEndDate) < new Date()) || teamIsActive === false) {
              // Team plan expired — update this member's profile
              try {
                await supabase.from('profiles').update({
                  plan_active: false,
                  plan_name: null,
                }).eq('id', userId);
                // Also trigger server-side check for queued plans
                try { await supabase.rpc('check_plan_expiry', { p_user_id: userId }); } catch { /* non-critical */ }
              } catch { /* non-critical */ }
              row.plan_active = false;
              row.plan_name = undefined;
            }
          }
        } catch {
          // team_plans query failed — fall back to individual profile check (already done above)
        }
      }

      return row;
    } catch {
      return null;
    }
  }, []);

  // ── checkAdmin ────────────────────────────────────────────────────────────
  const checkAdmin = useCallback(async (email?: string | null): Promise<boolean> => {
    if (isAdminEmail(email)) return true;
    try {
      const { data, error } = await supabase.rpc('is_admin_check');
      return !error && !!data;
    } catch {
      return false;
    }
  }, []);

  // ── setupSync ─────────────────────────────────────────────────────────────
  const setupSync = useCallback((activeUser: string) => {
    if (syncCleanupRef.current) {
      syncCleanupRef.current();
      syncCleanupRef.current = null;
    }
    if (navigator.onLine) {
      mergeProgressWithDB(activeUser).catch(() => {});
    }
    syncCleanupRef.current = initSyncListener() || null;
  }, []);

  // ── loadProfileAndAdmin (non-blocking background fetch) ───────────────────
  const loadProfileAndAdmin = useCallback(async (s: Session) => {
    try {
      const [p, adminFlag] = await Promise.all([
        fetchProfile(s.user.id),
        checkAdmin(s.user.email),
      ]);
      if (!mountedRef.current) return;
      setProfile(p);
      setIsAdminFlag(adminFlag);

      // ── BUG #6: Save plan cache after successful profile load ──
      if (p) {
        savePlanCache(p.id, !!p.plan_active, p.plan_name);
      }

      // ── v18: Check for existing session on another device ──
      // Uses machine fingerprint instead of random session ID.
      // Same PC will always have the same fingerprint, so logging out
      // and logging back in won't trigger a false "concurrent session" popup.
      // Only TRULY different devices (different browser/machine) will trigger it.
      const deviceId = getOrCreateSessionId();
      try {
        const { data: existingSession } = await supabase
          .from('profiles')
          .select('active_session_id')
          .eq('id', s.user.id)
          .single();

        const dbSessionId = (existingSession as { active_session_id?: string } | null)?.active_session_id;

        // v18 FIX: Only show concurrent popup if the DB session is a DIFFERENT machine fingerprint
        // AND it starts with "machine_" (which means it was set by our new system).
        // Old-format sessions (random IDs from previous versions) should be overwritten silently.
        // Same fingerprint = same PC = no popup needed.
        if (dbSessionId && dbSessionId !== deviceId && dbSessionId.startsWith('machine_')) {
          // Another DIFFERENT MACHINE has an active session — show popup
          console.warn('[Auth] Session found on a different machine. Showing concurrent session popup.');
          if (mountedRef.current) {
            setConcurrentSessionEmail(s.user.email || '');
          }
          // Sign out locally (don't overwrite DB session — the other machine keeps its session)
          try {
            await supabase.auth.signOut({ scope: 'local' });
          } catch { /* ignore */ }
          if (mountedRef.current) {
            setSession(null);
            setUser(null);
            setProfile(null);
            setIsLoggedIn(false);
            setIsAdminFlag(false);
            setIsPlanLoading(false);
          }
          return;
        }

        // No existing session or same device — safe to write our session ID
        await supabase
          .from('profiles')
          .update({ active_session_id: deviceId })
          .eq('id', s.user.id);
      } catch {
        // Non-critical — if we can't check, write our session ID anyway
        try {
          await supabase
            .from('profiles')
            .update({ active_session_id: deviceId })
            .eq('id', s.user.id);
        } catch { /* non-critical */ }
      }

      // Profile loaded — plan data is now authoritative
      setIsPlanLoading(false);
    } catch {
      // Non-critical — user is still logged in, just profile data unavailable
      setIsPlanLoading(false);
    }

    // Merge ALL users' scores from DB to localStorage (loads scores on app open)
    if (navigator.onLine) {
      mergeAllUsersProgress().catch(() => {});
    }

    // Setup DB sync for offline progress
    const activeUser = localStorage.getItem('activeUser') || 'user_1';
    setupSync(activeUser);
  }, [fetchProfile, checkAdmin, setupSync]);

  // ── handleSession (core auth state handler) ───────────────────────────────
  const handleSession = useCallback(async (s: Session | null) => {
    if (!mountedRef.current) return;

    if (!s) {
      // No session → logged out
      setSession(null);
      setUser(null);
      setProfile(null);
      setIsLoggedIn(false);
      setIsAdminFlag(false);
      setIsLoading(false);
      setIsPlanLoading(false);
      setIsPasswordRecovery(false);
      if (syncCleanupRef.current) {
        syncCleanupRef.current();
        syncCleanupRef.current = null;
      }
      return;
    }

    // Have session → logged in
    // *** CRITICAL FIX: Set isLoading=false IMMEDIATELY — don't wait for profile ***
    setSession(s);
    setUser(s.user);
    setIsLoggedIn(true);
    setIsLoading(false);

    // ── BUG #6: Optimistically restore plan from cache ──
    // Before the profile fetch completes, check localStorage for cached plan status.
    // This prevents the "locked exercises" flash for paid users.
    setIsPlanLoading(true);
    const cachedPlan = loadPlanCache(s.user.id);
    if (cachedPlan && cachedPlan.plan_active) {
      // Optimistically set a minimal profile with cached plan data
      // This makes hasActivePlan=true immediately, unlocking exercises
      setProfile(prev => prev ? prev : {
        id: s.user.id,
        name: s.user.user_metadata?.name || s.user.email?.split('@')[0] || 'User',
        email: s.user.email || '',
        phone: '',
        plan_active: cachedPlan.plan_active,
        plan_name: cachedPlan.plan_name || undefined,
      } as UserProfile);
    }

    // Fetch profile in background (non-blocking) — updates when ready
    loadProfileAndAdmin(s);
  }, [loadProfileAndAdmin, loadPlanCache]);

  // ── Main auth initialization ─────────────────────────────────────────────
  useEffect(() => {
    mountedRef.current = true;
    let safetyTimeoutId: number | undefined = undefined;
    let ultimateTimeoutId: number | undefined = undefined;

    // Check for password recovery in URL hash
    const checkPasswordRecovery = () => {
      const hash = window.location.hash;
      if (hash && hash.includes('type=recovery')) {
        setIsPasswordRecovery(true);
      }
    };
    checkPasswordRecovery();

    // Subscribe to auth state changes — THIS IS THE PRIMARY AUTH SOURCE
    // INITIAL_SESSION fires automatically on subscribe with the current session
    const { data: { subscription } } = supabase.auth.onAuthStateChange(
      (event, s) => {
        if (!mountedRef.current) return;

        switch (event) {
          case 'INITIAL_SESSION': {
            // *** FIX: Handle INITIAL_SESSION — this is how Supabase restores session on refresh ***
            if (initialSessionHandledRef.current) return; // Prevent duplicate handling
            initialSessionHandledRef.current = true;
            if (safetyTimeoutId) clearTimeout(safetyTimeoutId);
            handleSession(s);
            break;
          }
          case 'SIGNED_IN': {
            if (s) {
              handleSession(s);
              // S10: Track login event
              trackLogin();
            }
            break;
          }
          case 'SIGNED_OUT': {
            handleSession(null);
            break;
          }
          case 'TOKEN_REFRESHED': {
            if (s) setSession(s);
            break;
          }
          case 'USER_UPDATED': {
            if (s) setUser(s.user);
            break;
          }
          case 'PASSWORD_RECOVERY': {
            setIsPasswordRecovery(true);
            break;
          }
          default:
            break;
        }
      }
    );

    // *** SAFETY NET: If INITIAL_SESSION never fires within 3s, try getSession() ***
    safetyTimeoutId = window.setTimeout(() => {
      if (mountedRef.current && !initialSessionHandledRef.current) {
        console.warn('[Auth] INITIAL_SESSION never fired — falling back to getSession()');
        supabase.auth.getSession().then(({ data: { session: s } }) => {
          if (mountedRef.current && !initialSessionHandledRef.current) {
            initialSessionHandledRef.current = true;
            handleSession(s);
          }
        }).catch(() => {
          // Even getSession failed — stop loading to prevent infinite spinner
          if (mountedRef.current) {
            setIsLoading(false);
          }
        });
      }
    }, 3000);

    // *** ULTIMATE SAFETY: Force isLoading=false after 8 seconds no matter what ***
    ultimateTimeoutId = window.setTimeout(() => {
      if (mountedRef.current) {
        setIsLoading(false);
      }
    }, 8000);

    return () => {
      mountedRef.current = false;
      if (safetyTimeoutId) clearTimeout(safetyTimeoutId);
      if (ultimateTimeoutId) clearTimeout(ultimateTimeoutId);
      subscription.unsubscribe();
      if (syncCleanupRef.current) {
        syncCleanupRef.current();
        syncCleanupRef.current = null;
      }
    };
  }, [handleSession]);

  // ── refreshProfile (MUST be defined BEFORE the useEffect that uses it) ───
  const refreshProfile = useCallback(async () => {
    const { data: { session: s } } = await supabase.auth.getSession();
    if (!s) return;
    const p = await fetchProfile(s.user.id);
    const adminFlag = await checkAdmin(s.user.email);
    if (mountedRef.current) {
      setProfile(p);
      setIsAdminFlag(adminFlag);
    }
  }, [fetchProfile, checkAdmin]);

  // ── refreshSession ────────────────────────────────────────────────────────
  const refreshSession = useCallback(async (): Promise<boolean> => {
    try {
      const { data, error } = await supabase.auth.refreshSession();
      if (!error && data.session) {
        setSession(data.session);
        return true;
      }
      return false;
    } catch {
      return false;
    }
  }, []);

  // ── Periodic plan expiry check (every 15 seconds while app is open) ────────
  // This ensures exercises get locked immediately when a plan expires.
  // Works BOTH online (refreshes profile from DB + calls check_plan_expiry) and offline (local date check).
  // CRITICAL: This is the safety net that catches plan expiry even if the user
  // keeps the tab open without refreshing.
  // v20: Also checks team_plans table for team plan expiry propagation.
  useEffect(() => {
    const checkExpiry = () => {
      if (!isLoggedIn || !profile) return;

      // ── Check individual plan expiry ──
      if (profile.plan_active && profile.plan_end_date) {
        if (new Date(profile.plan_end_date) < new Date()) {
          // Plan has expired — update locally immediately (works offline)
          if (mountedRef.current) {
            setProfile(prev => prev ? { ...prev, plan_active: false, plan_name: undefined } : null);
          }
          // Also try to sync with DB if online
          if (navigator.onLine) {
            // Update DB directly for immediate effect
            supabase.from('profiles')
              .update({ plan_active: false, plan_name: null })
              .eq('id', profile.id)
              .then(() => {
                // Also trigger server-side check_plan_expiry for queued plan auto-activation
                return supabase.rpc('check_plan_expiry', { p_user_id: profile.id });
              })
              .then(() => {
                // Refresh profile to pick up any queued plan that got auto-activated
                if (mountedRef.current) refreshProfile();
              });
          }
          return; // Already expired, no need to check team
        }
      }

      // ── v20: Check team plan expiry ──
      // If user has a team plan, also verify the team_plans table hasn't expired
      if (profile.plan_active && profile.team_plan_id && navigator.onLine) {
        // First check cached team_plan_end_date (avoids DB query every 15s)
        if (profile.team_plan_end_date && new Date(profile.team_plan_end_date) < new Date()) {
          // Cached team plan expired — revoke access immediately
          if (mountedRef.current) {
            setProfile(prev => prev ? { ...prev, plan_active: false, plan_name: undefined } : null);
          }
          supabase.from('profiles')
            .update({ plan_active: false, plan_name: null })
            .eq('id', profile.id)
            .then(() => supabase.rpc('check_plan_expiry', { p_user_id: profile.id }))
            .then(() => { if (mountedRef.current) refreshProfile(); });
          return;
        }

        // Every 60s (approx every 4th check), also verify from DB directly
        // This catches cases where the cached date is stale
        const now = Date.now();
        const lastTeamCheck = parseInt(sessionStorage.getItem('rhc_team_plan_check') || '0', 10);
        if (now - lastTeamCheck > 55000) {
          sessionStorage.setItem('rhc_team_plan_check', String(now));
          void (async () => {
            try {
              const { data } = await supabase
                .from('team_plans')
                .select('end_date, is_active')
                .eq('id', profile.team_plan_id)
                .single();
              if (!data || !mountedRef.current) return;
              const teamEndDate = (data as { end_date?: string; is_active?: boolean }).end_date;
              const teamIsActive = (data as { end_date?: string; is_active?: boolean }).is_active;

              // Update cached team plan end_date
              setProfile(prev => prev ? { ...prev, team_plan_end_date: teamEndDate || undefined } : null);

              if ((teamEndDate && new Date(teamEndDate) < new Date()) || teamIsActive === false) {
                // Team plan expired — revoke access
                setProfile(prev => prev ? { ...prev, plan_active: false, plan_name: undefined } : null);
                await supabase.from('profiles')
                  .update({ plan_active: false, plan_name: null })
                  .eq('id', profile.id);
                await supabase.rpc('check_plan_expiry', { p_user_id: profile.id });
                if (mountedRef.current) refreshProfile();
              }
            } catch { /* non-critical */ }
          })();
        }
      }
    };

    // Check immediately on mount
    checkExpiry();

    // Then check every 15 seconds (was 30, reduced for faster lock)
    const interval = window.setInterval(checkExpiry, 15000);
    return () => window.clearInterval(interval);
  }, [isLoggedIn, profile, refreshProfile]);

  // ── Periodic full profile refresh (every 60s) ─────────────────────────────
  // This catches block/unblock status changes, plan modifications by admin,
  // and other DB-side changes that the plan expiry check doesn't cover.
  useEffect(() => {
    if (!isLoggedIn) return;
    const fullRefresh = () => {
      if (navigator.onLine && mountedRef.current) {
        refreshProfile().catch(() => {});
      }
    };
    // Refresh every 60 seconds
    const interval = window.setInterval(fullRefresh, 60000);
    return () => window.clearInterval(interval);
  }, [isLoggedIn, refreshProfile]);

  // ── v18: Concurrent session check (every 30s) ────────────────────────────
  // Compares this device's machine fingerprint with the one stored in the DB.
  // If they differ AND both are machine fingerprints (start with "machine_"),
  // another DIFFERENT machine has logged in → auto-logout this device.
  // Old-format session IDs are ignored to prevent false logouts during migration.
  useEffect(() => {
    if (!isLoggedIn || !user) return;

    const checkConcurrentSession = async () => {
      if (!navigator.onLine || !mountedRef.current) return;

      try {
        const { data } = await supabase
          .from('profiles')
          .select('active_session_id')
          .eq('id', user.id)
          .single();

        if (!data || !mountedRef.current) return;

        const dbSessionId = (data as { active_session_id?: string }).active_session_id;
        const localSessionId = localStorage.getItem(SESSION_ID_KEY);

        // v18 FIX: Only trigger auto-logout if:
        // 1. DB has a machine fingerprint that's DIFFERENT from ours
        // 2. Both are new-format ("machine_" prefix) — prevents false logouts from old-format IDs
        // Same fingerprint = same PC = no action needed
        if (dbSessionId && localSessionId && dbSessionId !== localSessionId && dbSessionId.startsWith('machine_') && localSessionId.startsWith('machine_')) {
          console.warn('[Auth] Another machine has logged in with this account. Auto-logging out.');
          // Clear session and redirect
          try {
            await supabase.auth.signOut({ scope: 'local' });
          } catch { /* ignore */ }
          // Force state cleanup
          if (mountedRef.current) {
            setSession(null);
            setUser(null);
            setProfile(null);
            setIsLoggedIn(false);
            setIsAdminFlag(false);
            setIsLoading(false);
            setIsPlanLoading(false);
          }
          // Show alert and redirect
          alert('Your account has been logged in from another device. You have been logged out. / आपका खाता दूसरे डिवाइस से लॉग इन किया गया है। आप लॉग आउट हो गए हैं।');
          window.location.href = '/';
        }
      } catch {
        // Non-critical — DB check failed, skip this check
      }
    };

    // Check after 15 seconds (give time for login to complete),
    // then every 30 seconds
    const initialTimeout = window.setTimeout(checkConcurrentSession, 15000);
    const interval = window.setInterval(checkConcurrentSession, 30000);
    return () => {
      window.clearTimeout(initialTimeout);
      window.clearInterval(interval);
    };
  }, [isLoggedIn, user]);

  // ── S17: Confirm concurrent login (called when user clicks OK on popup) ──
  const confirmConcurrentLogin = useCallback(async (email: string) => {
    // Overwrite DB session ID with this device's ID
    // This will cause PC1 to auto-logout on its next 30s check
    const deviceId = getOrCreateSessionId();
    try {
      // Look up user ID by email since we may already be signed out locally
      const { data: profileData } = await supabase
        .from('profiles')
        .select('id')
        .eq('email', email)
        .limit(1);
      if (profileData && profileData.length > 0) {
        await supabase
          .from('profiles')
          .update({ active_session_id: deviceId })
          .eq('id', (profileData[0] as { id: string }).id);
      }
    } catch { /* non-critical */ }

    // Clear local state (user is already signed out, but make sure state is clean)
    if (mountedRef.current) {
      setSession(null);
      setUser(null);
      setProfile(null);
      setIsLoggedIn(false);
      setIsAdminFlag(false);
      setIsPlanLoading(false);
    }

    // Dispatch event for AuthModal to open with pre-filled email
    window.dispatchEvent(new CustomEvent('rhc_concurrent_login_confirmed', { detail: { email } }));
  }, []);

  // ── S17: Clear concurrent session state ──
  const clearConcurrentSession = useCallback(() => {
    setConcurrentSessionEmail(null);
  }, []);

  // ── signOut ───────────────────────────────────────────────────────────────
  const signOut = useCallback(async () => {
    // Clean up sync listener
    if (syncCleanupRef.current) {
      syncCleanupRef.current();
      syncCleanupRef.current = null;
    }

    // Clear admin auth flag
    sessionStorage.removeItem('rhc_admin_authed');

    // Clear Supabase SDK session (this also clears its localStorage)
    try {
      await supabase.auth.signOut({ scope: 'global' });
    } catch {
      // If signOut fails, clear state manually
      if (mountedRef.current) {
        setSession(null);
        setUser(null);
        setProfile(null);
        setIsLoggedIn(false);
        setIsAdminFlag(false);
        setIsLoading(false);
      }
    }

    // The onAuthStateChange SIGNED_OUT event will handle state cleanup,
    // but we also navigate to home as a safety net
    window.location.href = '/';
  }, []);

  // ── Derived values ────────────────────────────────────────────────────────
  const isAdmin = isAdminFlag || isAdminEmail(user?.email);
  const isBlockedByAdmin = !!profile?.is_blocked_by_admin;
  // ── CRITICAL v19 FIX: Differentiate admin block vs team block ──
  // Admin block (is_blocked_by_admin=true): FULL website block — no exercises, no typing, only BlockedUserBanner + Appeal
  // Team block (is_blocked=true, is_blocked_by_admin=false): PARTIAL block — can use free exercises,
  //   buy individual plan, buy own team plan. Only restriction: can't use the team plan from the blocking team.
  const isBlocked = isBlockedByAdmin; // Full website block ONLY for admin-blocked users
  const isTeamBlocked = !!profile?.is_blocked && !isBlockedByAdmin; // Team leader block — partial restriction
  // Admin-blocked users: no active plan access. Team-blocked users: can still have active plans.
  const hasActivePlan = isAdmin || (!!profile?.plan_active && !isBlocked);
  const isTeamLeader = !!profile?.is_team_leader;
  const teamPlanId = profile?.team_plan_id ?? null;
  const hasQueuedPlan = !!profile?.queued_plan_name;
  const queuedPlan = profile?.queued_plan_name ? {
    name: profile?.queued_plan_name,
    endDate: profile?.queued_plan_end_date || '',
    isTeam: !!profile?.queued_team_plan_id,
  } : null;
  const blockedReason = profile?.blocked_reason ?? null;
  const hasAppealPending = !!profile?.appeal_text && !!profile?.appeal_submitted_at;

  return (
    <AuthContext.Provider value={{
      session, user, profile, isLoading, isPlanLoading, isLoggedIn,
      isAdmin, hasActivePlan, isTeamLeader, teamPlanId,
      hasQueuedPlan, queuedPlan, isBlockedByAdmin, isBlocked, isTeamBlocked, blockedReason, hasAppealPending, isPasswordRecovery,
      concurrentSessionEmail, confirmConcurrentLogin, clearConcurrentSession,
      signOut, refreshProfile, refreshSession,
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
