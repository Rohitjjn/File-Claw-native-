import { useState, useRef, useEffect, useCallback } from 'react';
import { useAuth } from '../context/AuthContext';
import { AuthModal } from './AuthModal';
import { UserPanel } from './UserPanel';
import { TeamLeaderPanel } from './TeamLeaderPanel';
import { NotificationBell } from './NotificationBell';
import '../styles/userprofile.css';

interface UserProfileProps {
  onAdminClick?: () => void;
}

export function UserProfile({ onAdminClick }: UserProfileProps) {
  const { user, profile, signOut, isAdmin, isLoggedIn, isLoading, isTeamLeader, refreshProfile, teamPlanId, concurrentSessionEmail } = useAuth();
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);
  const [authMode, setAuthMode] = useState<'login' | 'signup'>('signup');
  const [isUserPanelOpen, setIsUserPanelOpen] = useState(false);
  const [isTeamLeaderPanelOpen, setIsTeamLeaderPanelOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  // v15: S17 FIX - Auto-open AuthModal when concurrent session detected
  // When concurrentSessionEmail is set, the user has been signed out locally
  // but we need to show them the "Session found on another device" popup.
  // The AuthModal only shows this popup when it's open AND concurrentSessionEmail is set.
  useEffect(() => {
    if (concurrentSessionEmail && !isLoggedIn) {
      setAuthMode('login');
      setIsAuthModalOpen(true);
    }
  }, [concurrentSessionEmail, isLoggedIn]);

  // Show Team Panel if user is_team_leader OR has a team_plan_id with leader flag
  const showTeamPanel = isTeamLeader || !!profile?.is_team_leader || (!!teamPlanId && !!profile?.is_team_leader);

  // Parse plan_change_log for latest extend/decrease info
  // Only shows modifier for TRUE extensions (+ button with Rs.0) and decreases (- button)
  // Renewals (GRANTED or EXTENDED with non-zero price) are NOT shown
  const getPlanModifier = (): string | null => {
    if (!profile?.plan_change_log || !profile?.plan_active) return null;
    const lines = profile.plan_change_log.split('\n').filter(l => l.trim());
    if (lines.length === 0) return null;
    // Find the last EXTENDED or DECREASED line
    for (let i = lines.length - 1; i >= 0; i--) {
      const line = lines[i].trim();
      if (line.toUpperCase().includes('EXTENDED BY')) {
        const match = line.match(/EXTENDED BY\s+(\d+)\s+DAYS/i);
        if (match) {
          // Check if this is a renewal (price > 0) vs true extension (price = 0)
          // True extensions from + button have Rs.0, renewals have Rs.99/250/etc.
          const priceMatch = line.match(/Rs\.(\d+)/i);
          if (priceMatch && parseInt(priceMatch[1]) > 0) {
            // This is a renewal, not a true extension — skip, keep looking
            continue;
          }
          return `Extended by ${match[1]} days`;
        }
      }
      if (line.toUpperCase().includes('DECREASED BY')) {
        const match = line.match(/DECREASED BY\s+(\d+)\s+DAYS/i);
        if (match) return `Decreased by ${match[1]} days`;
      }
    }
    return null;
  };
  const planModifier = getPlanModifier();

  // Listen for 'open_team_panel' custom event (dispatched from notification CTA)
  const handleOpenTeamPanel = useCallback(() => {
    refreshProfile().catch(() => {});
    setIsTeamLeaderPanelOpen(true);
    setIsDropdownOpen(false);
    // S13: Notify guide that team panel opened
    window.dispatchEvent(new CustomEvent('rhc_team_panel_opened'));
  }, [refreshProfile]);

  useEffect(() => {
    window.addEventListener('open_team_panel', handleOpenTeamPanel);
    return () => window.removeEventListener('open_team_panel', handleOpenTeamPanel);
  }, [handleOpenTeamPanel]);

  // S13: Listen for guide events to open profile menu and team panel
  useEffect(() => {
    const handleOpenProfileMenu = () => {
      setIsDropdownOpen(true);
      // Notify guide that menu opened
      setTimeout(() => window.dispatchEvent(new CustomEvent('rhc_profile_menu_opened')), 100);
    };
    const handleOpenTeamPanelFromGuide = () => {
      setIsTeamLeaderPanelOpen(true);
      setIsDropdownOpen(false);
      window.dispatchEvent(new CustomEvent('rhc_team_panel_opened'));
    };
    window.addEventListener('rhc_open_profile_menu', handleOpenProfileMenu);
    window.addEventListener('rhc_open_team_panel', handleOpenTeamPanelFromGuide);
    return () => {
      window.removeEventListener('rhc_open_profile_menu', handleOpenProfileMenu);
      window.removeEventListener('rhc_open_team_panel', handleOpenTeamPanelFromGuide);
    };
  }, []);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  useEffect(() => {
    if (isDropdownOpen && menuRef.current && dropdownRef.current) {
      const menu = menuRef.current;
      const container = dropdownRef.current;
      const rect = container.getBoundingClientRect();
      const menuWidth = menu.offsetWidth;
      menu.style.left = '';
      menu.style.right = '';
      if (rect.left < menuWidth / 2) {
        menu.style.left = '0';
        menu.style.right = 'auto';
      } else {
        menu.style.right = '0';
        menu.style.left = 'auto';
      }
    }
  }, [isDropdownOpen]);

  const getInitials = (name: string) => {
    if (!name) return 'U';
    return name.charAt(0).toUpperCase();
  };

  const getDisplayName = () => {
    if (profile?.name && profile.name.trim()) return profile.name.trim();
    if (user?.user_metadata?.name && user.user_metadata.name.trim()) return user.user_metadata.name.trim();
    if (user?.email) return user.email.split('@')[0];
    if (profile?.email) return profile.email.split('@')[0];
    if (isLoggedIn) return 'User';
    return 'Guest';
  };

  const displayName = getDisplayName();

  const handleSignUp = () => { setAuthMode('signup'); setIsAuthModalOpen(true); setIsDropdownOpen(false); };
  const handleLogin  = () => { setAuthMode('login');  setIsAuthModalOpen(true); setIsDropdownOpen(false); };

  return (
    <>
      <div className="user-container" ref={dropdownRef} style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>

        {/* Compact User Button */}
        <button
          className="user-btn"
          onClick={() => setIsDropdownOpen(!isDropdownOpen)}
          aria-expanded={isDropdownOpen}
          aria-haspopup="true"
        >
          <span className="user-avatar">
            {isLoading ? (
              <span className="user-avatar-spinner" />
            ) : isLoggedIn ? (
              getInitials(displayName)
            ) : (
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="8" r="4"/>
                <path d="M4 20c0-4 4-6 8-6s8 2 8 6"/>
              </svg>
            )}
          </span>
          <span className="user-label">
            {isLoading ? 'Loading...' : displayName}
          </span>
          <svg className={`user-chevron ${isDropdownOpen ? 'open' : ''}`} width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
            <polyline points="6 9 12 15 18 9"/>
          </svg>
        </button>

        {/* Notification Bell — positioned to the RIGHT of the user box */}
        <NotificationBell />

        {/* Dropdown Menu */}
        {isDropdownOpen && (
          <div className="user-menu" ref={menuRef}>
            {isLoading ? (
              <div className="menu-hint">Loading...</div>
            ) : !isLoggedIn ? (
              <>
                <div className="menu-hint">Sign up to unlock all features</div>
                <div className="menu-actions">
                  <button className="menu-btn primary" onClick={handleSignUp}>Sign Up</button>
                  <button className="menu-btn secondary" onClick={handleLogin}>Login</button>
                </div>
              </>
            ) : (
              <>
                <div className="menu-info">
                  <div className="menu-name">{profile?.name || displayName}</div>
                  <div className="menu-email">{profile?.email || user?.email}</div>
                  {profile?.phone && <div className="menu-phone">{profile.phone}</div>}
                  {profile?.plan_active && (
                    <div className="menu-plan">
                      <span className="plan-tag">
                        {profile.plan_type === 'individual' || (!profile.is_team_leader && !profile.team_plan_id)
                          ? 'Individual Plan'
                          : profile.plan_name || 'Team Premium'}
                      </span>
                      {planModifier && (
                        <span className="plan-modifier" style={{ display: 'block', fontSize: '0.7rem', color: planModifier.includes('Extended') ? '#16a34a' : '#d97706', marginTop: '2px', fontWeight: 500 }}>
                          {planModifier}
                        </span>
                      )}
                    </div>
                  )}
                </div>

                {showTeamPanel && (
                  <button
                    className="menu-item team-leader"
                    onClick={() => { setIsTeamLeaderPanelOpen(true); setIsDropdownOpen(false); }}
                  >
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>
                    </svg>
                    Team Panel
                  </button>
                )}

                {isLoggedIn && !isAdmin && (
                  <button
                    className="menu-item my-panel"
                    onClick={() => { setIsUserPanelOpen(true); setIsDropdownOpen(false); }}
                  >
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>
                    </svg>
                    User Panel
                  </button>
                )}

                {isAdmin && (
                  <button
                    className="menu-item admin"
                    onClick={() => { if (onAdminClick) onAdminClick(); setIsDropdownOpen(false); }}
                  >
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <path d="M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z"/>
                      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1Z"/>
                    </svg>
                    Admin Panel
                  </button>
                )}

                <button
                  className="menu-item logout"
                  onClick={() => { signOut(); setIsDropdownOpen(false); }}
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
                    <polyline points="16 17 21 12 16 7"/>
                    <line x1="21" y1="12" x2="9" y2="12"/>
                  </svg>
                  Logout
                </button>
              </>
            )}
          </div>
        )}

        <AuthModal
          isOpen={isAuthModalOpen}
          onClose={() => setIsAuthModalOpen(false)}
          initialMode={authMode}
        />

        <UserPanel
          isOpen={isUserPanelOpen}
          onClose={() => setIsUserPanelOpen(false)}
        />

        <TeamLeaderPanel
          isOpen={isTeamLeaderPanelOpen}
          onClose={() => setIsTeamLeaderPanelOpen(false)}
        />
      </div>
    </>
  );
}
