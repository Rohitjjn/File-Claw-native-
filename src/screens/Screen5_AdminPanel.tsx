import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useAuth } from '../context/AuthContext';
import { useExam } from '../context/ExamContext';
import { supabase } from '../lib/supabase';
import '../styles/admin.css';
import '../styles/admin-v11-extras.css';

// Logout SVG icon
const LogoutIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
    <polyline points="16 17 21 12 16 7"/>
    <line x1="21" y1="12" x2="9" y2="12"/>
  </svg>
);

// Eye icons for team code reveal
const EyeOpenIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
    <circle cx="12" cy="12" r="3"/>
  </svg>
);
const EyeClosedIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
    <line x1="1" y1="1" x2="23" y2="23"/>
  </svg>
);

// Trash icon for delete
const TrashIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
  </svg>
);

// Gmail-style first letter avatar (no Gravatar)
const AVATAR_COLORS = [
  '#1a73e8', '#e8710a', '#d93025', '#1e8e3e', '#9334e6',
  '#c5221f', '#f9ab00', '#46bdc6', '#7baaf7', '#e6c800',
  '#ee675c', '#5f6368', '#fa7b17', '#34a853', '#a142f4',
];

function getAvatarColor(str: string): string {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = str.charCodeAt(i) + ((hash << 5) - hash);
  }
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length];
}

function InitialAvatar({ email, name, size = 32 }: { email: string; name: string; size?: number }) {
  const initial = (name || email || '?')[0].toUpperCase();
  const bgColor = getAvatarColor((name || email || '').toLowerCase());
  const isLarge = size >= 36;
  return (
    <div
      className="ap-avatar-initial"
      style={{
        width: size,
        height: size,
        minWidth: size,
        fontSize: isLarge ? '1rem' : size < 30 ? '.65rem' : '.75rem',
        background: bgColor,
        borderRadius: '50%',
        color: '#fff',
        fontWeight: 700,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        flexShrink: 0,
        letterSpacing: '0.02em',
        textTransform: 'uppercase',
        userSelect: 'none',
      }}
    >
      {initial}
    </div>
  );
}

interface UserRow {
  id: string;
  name: string;
  email: string;
  phone: string;
  plan_active: boolean;
  plan_name: string | null;
  plan_end_date: string | null;
  is_admin: boolean;
  is_team_leader?: boolean;
  team_plan_id?: string;
  queued_plan_name?: string;
  queued_plan_end_date?: string;
  is_blocked_by_admin?: boolean;
  plan_change_log?: string;
  created_at: string;
}

interface TeamLeaderRow {
  team_plan_id: string;
  leader_user_id: string;
  leader_name: string;
  leader_email: string;
  leader_phone: string;
  team_code: string;
  team_size: number;
  months_duration: number;
  price_paid: number;
  plan_active: boolean;
  plan_end_date: string | null;
  member_count: number;
  created_at: string;
}

interface TeamMemberAdminRow {
  user_id: string;
  name: string;
  email: string;
  phone: string;
  joined_at: string;
  plan_active: boolean;
  is_blocked?: boolean;
}

interface AdminAppealRow {
  id: string;
  user_id: string;
  user_name: string;
  user_email: string;
  appeal_text: string;
  submitted_at: string;
  is_resolved: boolean;
  resolved_by_admin: boolean;
}

type Tab = 'dashboard' | 'users' | 'team-leaders' | 'plans' | 'analytics' | 'appeals' | 'reviews' | 'audit-log' | 'settings';

const ADMIN_EMAIL = 'userrohitai011@gmail.com';

const PLAN_PRICES: Record<string, number> = {
  '1 Month': 99,
  '2 Months': 199,
  '3 Months': 250,
  '6 Months': 450,
  '8 Months': 599,
  '12 Months': 899,
};

const PLAN_DAYS: Record<string, number> = {
  '1 Month': 30,
  '2 Months': 60,
  '3 Months': 90,
  '6 Months': 180,
  '8 Months': 240,
  '12 Months': 360,
};

const TEAM_PLAN_PRICES: Record<number, Record<number, number>> = {
  5:   { 1: 449,   2: 799,   3: 1199,  6: 2199,  8: 2799,  12: 3999  },
  10:  { 1: 799,   2: 1499,  3: 2199,  6: 3999,  8: 4999,  12: 6999  },
  20:  { 1: 1799,  2: 3299,  3: 4499,  6: 7999,  8: 9999,  12: 13999 },
  50:  { 1: 3499,  2: 6499,  3: 8999,  6: 15999, 8: 19999, 12: 27999 },
  100: { 1: 5999,  2: 10999, 3: 15999, 6: 28999, 8: 35999, 12: 48999 },
  150: { 1: 8499,  2: 15499, 3: 22999, 6: 40999, 8: 50999, 12: 68999 },
  200: { 1: 10999, 2: 19999, 3: 29999, 6: 53999, 8: 66999, 12: 89999 },
};

function daysLeft(dateStr: string | null): number {
  if (!dateStr) return 0;
  const diff = new Date(dateStr).getTime() - Date.now();
  return Math.max(0, Math.ceil(diff / 86400000));
}

function formatDate(dateStr: string | null): string {
  if (!dateStr) return '—';
  return new Date(dateStr).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}

function formatRelativeTime(dateStr: string | null): string {
  if (!dateStr) return '—';
  const diff = Date.now() - new Date(dateStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'Just now';
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}d ago`;
  return formatDate(dateStr);
}

// ── Mini Chart Components ──────────────────────────────────────────────────────
function BarChart({ data }: { data: { l: string; v: number }[] }) {
  const max = Math.max(...data.map(d => d.v), 1);
  return (
    <div style={{ display: 'flex', alignItems: 'flex-end', gap: '8px', height: '120px', padding: '0 0.5rem' }}>
      {data.map((d, i) => (
        <div key={i} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '4px' }}>
          <span style={{ fontSize: '.68rem', fontWeight: 600, color: 'var(--text-primary, #1a1a2e)' }}>{d.v}</span>
          <div style={{
            width: '100%', borderRadius: '4px 4px 0 0',
            height: `${Math.max((d.v / max) * 80, 4)}px`,
            background: 'linear-gradient(180deg, #ea580c, #d97706)',
            transition: 'height 0.3s ease',
          }} />
          <span style={{ fontSize: '.62rem', color: 'var(--text-secondary, #6b7280)' }}>{d.l}</span>
        </div>
      ))}
    </div>
  );
}

function DonutChart({ segments }: { segments: { label: string; value: number; color: string }[] }) {
  const total = segments.reduce((s, seg) => s + seg.value, 0);
  if (total === 0) return <div style={{ textAlign: 'center', color: 'var(--text-secondary, #6b7280)', padding: '2rem' }}>No data</div>;

  const radius = 50;
  const circumference = 2 * Math.PI * radius;

  const processedSegments: { label: string; value: number; color: string; pct: number; offset: number }[] = [];
  let currentSum = 0;
  segments.filter(s => s.value > 0).forEach(seg => {
    const pct = seg.value / total;
    const offset = (currentSum / total) * circumference;
    currentSum += seg.value;
    processedSegments.push({ ...seg, pct, offset });
  });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.75rem' }}>
      <svg width="120" height="120" viewBox="0 0 120 120">
        {processedSegments.map((seg, i) => (
          <circle key={i} cx="60" cy="60" r={radius} fill="none"
            stroke={seg.color} strokeWidth="16"
            strokeDasharray={`${seg.pct * circumference} ${(1 - seg.pct) * circumference}`}
            strokeDashoffset={-seg.offset}
            transform="rotate(-90 60 60)"
            style={{ transition: 'stroke-dasharray 0.3s ease' }}
          />
        ))}
        <text x="60" y="60" textAnchor="middle" dominantBaseline="central"
          style={{ fontSize: '1.1rem', fontWeight: 700, fill: 'var(--text-primary, #1a1a2e)' }}>
          {total}
        </text>
      </svg>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', justifyContent: 'center' }}>
        {segments.filter(s => s.value > 0).map((seg, i) => (
          <span key={i} style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '.68rem' }}>
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: seg.color, display: 'inline-block' }} />
            {seg.label} ({seg.value})
          </span>
        ))}
      </div>
    </div>
  );
}

// ── Premium Admin Auth Screen (Apple-inspired) ─────────────────────────────────
function AdminAuthScreen({ onSuccess }: { onSuccess: () => void }) {
  const { navigateTo } = useExam();
  const [pw, setPw] = useState('');
  const [err, setErr] = useState('');
  const [loading, setLoading] = useState(false);
  const ADMIN_PW = import.meta.env.VITE_ADMIN_PASSWORD || 'admin123';

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setErr('');
    setTimeout(() => {
      if (pw === ADMIN_PW) {
        onSuccess();
      } else {
        setErr('Incorrect password. Try again.');
        setPw('');
      }
      setLoading(false);
    }, 500);
  };

  return (
    <div className="ap-root ap-auth-page">
      <div className="ap-auth-container">
        <div className="ap-auth-orb" aria-hidden="true" />
        <div className="ap-auth-card">
          <div className="ap-auth-icon-wrap">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
            </svg>
          </div>
          <h1 className="ap-auth-heading">Admin Console</h1>
          <p className="ap-auth-desc">Enter your administrator password to continue</p>
          <form className="ap-auth-form" onSubmit={handleSubmit}>
            {err && <div className="ap-auth-err">{err}</div>}
            <div className="ap-auth-input-group">
              <label htmlFor="admin-pw">Password</label>
              <input
                id="admin-pw"
                type="password"
                value={pw}
                onChange={e => setPw(e.target.value)}
                placeholder="Enter admin password"
                autoFocus
                autoComplete="current-password"
                disabled={loading}
              />
            </div>
            <button type="submit" className="ap-auth-submit" disabled={loading || !pw}>
              {loading ? (
                <span className="ap-spin" />
              ) : (
                <>
                  Sign In
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                    <path d="m9 18 6-6-6-6"/>
                  </svg>
                </>
              )}
            </button>
          </form>
          <button type="button" className="ap-auth-back-link" onClick={() => navigateTo(1)}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="m15 18-6-6 6-6"/>
            </svg>
            Back to Home
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Admin Reviews Section Component ──────────────────────────────────────────
interface AdminReviewRow {
  id: string;
  user_id: string;
  user_name: string;
  user_email: string;
  rating: number;
  review_text: string;
  created_at: string;
  updated_at: string;
}

function AdminReviewsSection() {
  const [reviews, setReviews] = useState<AdminReviewRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [deleting, setDeleting] = useState<string | null>(null);

  const fetchReviews = useCallback(async () => {
    setLoading(true);
    try {
      const { data, error } = await supabase.rpc('admin_get_reviews');
      if (error) {
        console.warn('[Admin] Reviews RPC failed:', error.message);
        setReviews([]);
      } else {
        setReviews((data as AdminReviewRow[]) || []);
      }
    } catch (err) {
      console.error('[Admin] Fetch reviews error:', err);
      setReviews([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchReviews(); }, [fetchReviews]);

  const deleteReview = async (reviewId: string) => {
    if (!confirm('Delete this review? This cannot be undone.')) return;
    setDeleting(reviewId);
    try {
      const { error } = await supabase.rpc('admin_delete_review', { p_review_id: reviewId });
      if (error) throw error;
      setReviews(prev => prev.filter(r => r.id !== reviewId));
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to delete review.');
    } finally {
      setDeleting(null);
    }
  };

  const avgRating = reviews.length > 0
    ? (reviews.reduce((sum, r) => sum + r.rating, 0) / reviews.length).toFixed(1)
    : '0.0';

  const ratingDist = [5, 4, 3, 2, 1].map(star => ({
    star,
    count: reviews.filter(r => r.rating === star).length,
  }));

  return (
    <div className="ap-section">
      <div className="ap-section-title">Reviews Management</div>

      {/* Stats Row */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '12px', marginBottom: '20px' }}>
        <div className="ap-stat-card" style={{ '--accent': '#f59e0b' } as React.CSSProperties}>
          <div className="ap-stat-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><polygon points="12,2 15.09,8.26 22,9.27 17,14.14 18.18,21.02 12,17.77 5.82,21.02 7,14.14 2,9.27 8.91,8.26"/></svg>
          </div>
          <div className="ap-stat-value">{reviews.length}</div>
          <div className="ap-stat-label">Total Reviews</div>
        </div>
        <div className="ap-stat-card" style={{ '--accent': '#ea580c' } as React.CSSProperties}>
          <div className="ap-stat-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>
          </div>
          <div className="ap-stat-value">{avgRating}</div>
          <div className="ap-stat-label">Avg Rating</div>
        </div>
        <div className="ap-stat-card" style={{ '--accent': '#16a34a' } as React.CSSProperties}>
          <div className="ap-stat-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><polygon points="12,2 15.09,8.26 22,9.27 17,14.14 18.18,21.02 12,17.77 5.82,21.02 7,14.14 2,9.27 8.91,8.26"/></svg>
          </div>
          <div className="ap-stat-value">{ratingDist[0].count}</div>
          <div className="ap-stat-label">5-Star Reviews</div>
        </div>
      </div>

      {/* Rating Distribution */}
      <div style={{ background: 'var(--bg-card, #fff)', border: '1px solid var(--border-color, #e5e7eb)', borderRadius: '14px', padding: '16px', marginBottom: '20px' }}>
        <div style={{ fontSize: '.8rem', fontWeight: 700, color: 'var(--text-primary)', marginBottom: '12px' }}>Rating Distribution</div>
        {ratingDist.map(rd => (
          <div key={rd.star} style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
            <span style={{ fontSize: '.75rem', fontWeight: 600, color: 'var(--text-secondary)', minWidth: '40px' }}>{rd.star} ★</span>
            <div style={{ flex: 1, height: '8px', background: 'var(--bg-secondary, #f3f4f6)', borderRadius: '4px', overflow: 'hidden' }}>
              <div style={{ width: `${reviews.length > 0 ? (rd.count / reviews.length) * 100 : 0}%`, height: '100%', background: '#f59e0b', borderRadius: '4px', transition: 'width 0.3s' }} />
            </div>
            <span style={{ fontSize: '.7rem', color: 'var(--text-secondary)', minWidth: '24px', textAlign: 'right' }}>{rd.count}</span>
          </div>
        ))}
      </div>

      {/* Reviews List */}
      {loading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: '3rem' }}>
          <span className="ap-spin lg" />
        </div>
      ) : reviews.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-secondary)' }}>
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" style={{ marginBottom: '8px', opacity: 0.4 }}><polygon points="12,2 15.09,8.26 22,9.27 17,14.14 18.18,21.02 12,17.77 5.82,21.02 7,14.14 2,9.27 8.91,8.26"/></svg>
          <p>No reviews yet.</p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {reviews.map(review => (
            <div key={review.id} style={{
              display: 'flex', alignItems: 'flex-start', gap: '12px',
              padding: '14px 16px', background: 'var(--bg-card, #fff)',
              border: '1px solid var(--border-color, #e5e7eb)', borderRadius: '12px',
              transition: 'border-color 0.15s',
            }}>
              <InitialAvatar email={review.user_email} name={review.user_name} size={36} />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                  <span style={{ fontWeight: 600, fontSize: '.85rem', color: 'var(--text-primary)' }}>{review.user_name || '—'}</span>
                  <span style={{ fontSize: '.75rem', color: '#f59e0b', letterSpacing: '-0.5px' }}>
                    {'★'.repeat(review.rating)}{'☆'.repeat(5 - review.rating)}
                  </span>
                </div>
                <div style={{ fontSize: '.7rem', color: 'var(--text-secondary)', marginBottom: '6px' }}>{review.user_email} • {formatRelativeTime(review.created_at)}</div>
                {review.review_text && (
                  <div style={{ fontSize: '.82rem', color: 'var(--text-primary)', lineHeight: 1.5 }}>{review.review_text}</div>
                )}
              </div>
              <button
                onClick={() => deleteReview(review.id)}
                disabled={deleting === review.id}
                style={{
                  flexShrink: 0, padding: '6px 10px', background: deleting === review.id ? '#fee2e2' : '#fef2f2',
                  border: '1px solid #fecaca', borderRadius: '8px', color: '#dc2626',
                  fontSize: '.72rem', fontWeight: 600, cursor: deleting === review.id ? 'wait' : 'pointer',
                  transition: 'background 0.12s',
                }}
                title="Delete review"
              >
                {deleting === review.id ? '...' : <TrashIcon />}
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ── Admin Audit Log Section Component ──────────────────────────────────────────
interface AuditLogEntry {
  id: string;
  admin_email: string;
  action_type: string;
  target_user_email: string;
  target_user_name: string;
  details: string;
  created_at: string;
}

function AdminAuditLogSection() {
  const [logs, setLogs] = useState<AuditLogEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<string>('all');
  const [searchTerm, setSearchTerm] = useState('');

  const fetchLogs = useCallback(async () => {
    setLoading(true);
    try {
      // Try RPC first
      const { data, error } = await supabase.rpc('admin_get_audit_logs');
      if (error) {
        console.warn('[Admin] Audit logs RPC failed, trying direct query:', error.message);
        // Fallback: direct query
        const { data: fallback, error: fbErr } = await supabase
          .from('admin_audit_log')
          .select('*')
          .order('created_at', { ascending: false })
          .limit(200);
        if (fbErr) throw fbErr;
        setLogs((fallback as AuditLogEntry[]) || []);
      } else {
        setLogs((data as AuditLogEntry[]) || []);
      }
    } catch (err) {
      console.error('[Admin] Fetch audit logs error:', err);
      setLogs([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchLogs(); }, [fetchLogs]);

  const filteredLogs = logs.filter(log => {
    const q = searchTerm.toLowerCase();
    const matchSearch = !q ||
      log.action_type?.toLowerCase().includes(q) ||
      log.target_user_email?.toLowerCase().includes(q) ||
      log.target_user_name?.toLowerCase().includes(q) ||
      log.admin_email?.toLowerCase().includes(q) ||
      log.details?.toLowerCase().includes(q);
    const matchFilter = filter === 'all' || log.action_type === filter;
    return matchSearch && matchFilter;
  });

  const actionTypes = [...new Set(logs.map(l => l.action_type).filter(Boolean))];

  const getActionColor = (type: string) => {
    if (!type) return '#6b7280';
    const t = type.toUpperCase();
    if (t.includes('GRANT') || t.includes('ACTIVATE')) return '#16a34a';
    if (t.includes('REVOKE') || t.includes('DEACTIVATE')) return '#dc2626';
    if (t.includes('BLOCK')) return '#ef4444';
    if (t.includes('UNBLOCK')) return '#10b981';
    if (t.includes('EXTEND')) return '#f59e0b';
    if (t.includes('DECREASE')) return '#f97316';
    if (t.includes('NOTIFICATION')) return '#3b82f6';
    if (t.includes('MAINTENANCE')) return '#8b5cf6';
    if (t.includes('APPEAL')) return '#06b6d4';
    return '#6b7280';
  };

  const getActionIcon = (type: string) => {
    if (!type) return '📋';
    const t = type.toUpperCase();
    if (t.includes('GRANT') || t.includes('ACTIVATE')) return '✅';
    if (t.includes('REVOKE') || t.includes('DEACTIVATE')) return '❌';
    if (t.includes('BLOCK')) return '🚫';
    if (t.includes('UNBLOCK')) return '✅';
    if (t.includes('EXTEND')) return '⏩';
    if (t.includes('DECREASE')) return '⏪';
    if (t.includes('NOTIFICATION')) return '🔔';
    if (t.includes('MAINTENANCE')) return '🔧';
    if (t.includes('APPEAL')) return '📝';
    return '📋';
  };

  return (
    <div className="ap-section">
      <div className="ap-section-title">Audit Log</div>

      {/* Filters */}
      <div style={{ display: 'flex', gap: '12px', marginBottom: '16px', flexWrap: 'wrap' }}>
        <input
          type="text"
          value={searchTerm}
          onChange={e => setSearchTerm(e.target.value)}
          placeholder="Search by email, action, details..."
          style={{
            flex: 1, minWidth: '200px', padding: '8px 12px',
            border: '1px solid var(--border-color, #e5e7eb)',
            borderRadius: '8px', fontSize: '.85rem',
            background: 'var(--bg-card, #fff)', color: 'var(--text-primary, #1a1a2e)',
          }}
        />
        <select
          value={filter}
          onChange={e => setFilter(e.target.value)}
          style={{
            padding: '8px 12px', border: '1px solid var(--border-color, #e5e7eb)',
            borderRadius: '8px', fontSize: '.85rem',
            background: 'var(--bg-card, #fff)', color: 'var(--text-primary, #1a1a2e)',
          }}
        >
          <option value="all">All Actions</option>
          {actionTypes.map(t => (
            <option key={t} value={t}>{t}</option>
          ))}
        </select>
      </div>

      {/* Stats */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '12px', marginBottom: '20px' }}>
        <div className="ap-stat-card" style={{ '--accent': '#3b82f6' } as React.CSSProperties}>
          <div className="ap-stat-value">{logs.length}</div>
          <div className="ap-stat-label">Total Actions</div>
        </div>
        <div className="ap-stat-card" style={{ '--accent': '#10b981' } as React.CSSProperties}>
          <div className="ap-stat-value">{logs.filter(l => l.action_type?.toUpperCase().includes('GRANT') || l.action_type?.toUpperCase().includes('ACTIVATE')).length}</div>
          <div className="ap-stat-label">Plan Grants</div>
        </div>
        <div className="ap-stat-card" style={{ '--accent': '#ef4444' } as React.CSSProperties}>
          <div className="ap-stat-value">{logs.filter(l => l.action_type?.toUpperCase().includes('REVOKE') || l.action_type?.toUpperCase().includes('BLOCK')).length}</div>
          <div className="ap-stat-label">Revokes/Blocks</div>
        </div>
      </div>

      {/* Logs List */}
      {loading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: '3rem' }}>
          <span className="ap-spin lg" />
        </div>
      ) : filteredLogs.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-secondary)' }}>
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" style={{ marginBottom: '8px', opacity: 0.4 }}><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
          <p>No audit logs found.</p>
          <p style={{ fontSize: '.8rem', marginTop: '4px' }}>Run migration_v47.sql in Supabase SQL Editor to create the audit log table and RPC.</p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {filteredLogs.map(log => (
            <div key={log.id} style={{
              display: 'flex', alignItems: 'flex-start', gap: '12px',
              padding: '12px 14px', background: 'var(--bg-card, #fff)',
              border: '1px solid var(--border-color, #e5e7eb)', borderRadius: '10px',
              borderLeft: `3px solid ${getActionColor(log.action_type)}`,
            }}>
              <span style={{ fontSize: '1.2rem', flexShrink: 0, marginTop: '2px' }}>
                {getActionIcon(log.action_type)}
              </span>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px', flexWrap: 'wrap' }}>
                  <span style={{
                    padding: '2px 8px', borderRadius: '6px', fontSize: '.7rem', fontWeight: 600,
                    background: `${getActionColor(log.action_type)}20`, color: getActionColor(log.action_type),
                  }}>
                    {log.action_type || 'UNKNOWN'}
                  </span>
                  <span style={{ fontSize: '.8rem', color: 'var(--text-secondary)' }}>
                    {formatRelativeTime(log.created_at)}
                  </span>
                </div>
                <div style={{ fontSize: '.82rem', color: 'var(--text-primary)', marginBottom: '4px' }}>
                  {log.details || 'No details'}
                </div>
                <div style={{ fontSize: '.72rem', color: 'var(--text-secondary)', display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
                  {log.target_user_email && <span>Target: {log.target_user_name || log.target_user_email}</span>}
                  <span>By: {log.admin_email || 'Admin'}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ── Main Admin Panel ──────────────────────────────────────────────────────────
export function Screen5_AdminPanel() {
  const { isAdmin, isLoading } = useAuth();
  const { navigateTo } = useExam();

  const [isAuthed, setIsAuthed] = useState(() => {
    return sessionStorage.getItem('rhc_admin_authed') === '1';
  });
  const [activeTab, setActiveTab] = useState<Tab>('dashboard');
  const [users, setUsers] = useState<UserRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [filterPlan, setFilterPlan] = useState<'all' | 'active' | 'none' | 'blocked' | 'team'>('all');
  const [selectedUser, setSelectedUser] = useState<UserRow | null>(null);
  const [showPlanModal, setShowPlanModal] = useState(false);
  const [planForm, setPlanForm] = useState<{ planName: string; days: number; customDays: boolean; pricePaid?: number }>({ planName: '1 Month', days: 30, customDays: false, pricePaid: 99 });
  const [saving, setSaving] = useState(false);
  const [planType, setPlanType] = useState<'individual' | 'team'>('individual');
  const [teamSize, setTeamSize] = useState(5);
  const [teamMonths, setTeamMonths] = useState(1);
  const [generatedTeamCode, setGeneratedTeamCode] = useState<string | null>(null);
  const [showTeamCodeModal, setShowTeamCodeModal] = useState(false);

  // Team leader management state
  const [teamLeaders, setTeamLeaders] = useState<TeamLeaderRow[]>([]);
  const [expandedTeams, setExpandedTeams] = useState<Record<string, boolean>>({});
  const [teamMembers, setTeamMembers] = useState<Record<string, TeamMemberAdminRow[]>>({});
  const [revealedCodes, setRevealedCodes] = useState<Record<string, boolean>>({});
  const [copiedCode, setCopiedCode] = useState<string | null>(null);

  // Custom days input for extend/decrease
  const [customDaysInput, setCustomDaysInput] = useState<Record<string, string>>({});

  // Appeals state
  const [appeals, setAppeals] = useState<AdminAppealRow[]>([]);

  // Maintenance mode
  const [maintenanceMode, setMaintenanceMode] = useState(false);
  const [maintenanceLoading, setMaintenanceLoading] = useState(false);

  if (isLoading) {
    return (
      <div className="ap-root">
        <div className="ap-loading-page">
          <span className="ap-spin lg" />
          <p>Loading admin panel...</p>
        </div>
      </div>
    );
  }

  if (!isAdmin) {
    return (
      <div className="ap-root">
        <div className="ap-access-denied">
          <div className="ap-access-denied-icon">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="8" x2="12" y2="12"/>
              <line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
          </div>
          <h2>Access Denied</h2>
          <p>Admin privileges required to access this panel.</p>
          <button className="ap-btn-primary" onClick={() => navigateTo(1)}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="m15 18-6-6 6-6"/>
            </svg>
            Back to Home
          </button>
        </div>
      </div>
    );
  }

  if (!isAuthed) return <AdminAuthScreen onSuccess={() => {
    sessionStorage.setItem('rhc_admin_authed', '1');
    setIsAuthed(true);
  }} />;

  return <AdminPanelContent
    users={users} setUsers={setUsers}
    loading={loading} setLoading={setLoading}
    error={error} setError={setError}
    searchTerm={searchTerm} setSearchTerm={setSearchTerm}
    filterPlan={filterPlan} setFilterPlan={setFilterPlan}
    selectedUser={selectedUser} setSelectedUser={setSelectedUser}
    showPlanModal={showPlanModal} setShowPlanModal={setShowPlanModal}
    planForm={planForm} setPlanForm={setPlanForm}
    saving={saving} setSaving={setSaving}
    activeTab={activeTab} setActiveTab={setActiveTab}
    navigateTo={navigateTo}
    planType={planType} setPlanType={setPlanType}
    teamSize={teamSize} setTeamSize={setTeamSize}
    teamMonths={teamMonths} setTeamMonths={setTeamMonths}
    generatedTeamCode={generatedTeamCode} setGeneratedTeamCode={setGeneratedTeamCode}
    showTeamCodeModal={showTeamCodeModal} setShowTeamCodeModal={setShowTeamCodeModal}
    teamLeaders={teamLeaders} setTeamLeaders={setTeamLeaders}
    expandedTeams={expandedTeams} setExpandedTeams={setExpandedTeams}
    teamMembers={teamMembers} setTeamMembers={setTeamMembers}
    revealedCodes={revealedCodes} setRevealedCodes={setRevealedCodes}
    copiedCode={copiedCode} setCopiedCode={setCopiedCode}
    customDaysInput={customDaysInput} setCustomDaysInput={setCustomDaysInput}
    appeals={appeals} setAppeals={setAppeals}
    maintenanceMode={maintenanceMode} setMaintenanceMode={setMaintenanceMode}
    maintenanceLoading={maintenanceLoading} setMaintenanceLoading={setMaintenanceLoading}
  />;
}

// ── Panel Content ──────────────────────────────────────────────────────────────
function AdminPanelContent({
  users, setUsers, loading, setLoading, error, setError,
  searchTerm, setSearchTerm, filterPlan, setFilterPlan,
  selectedUser, setSelectedUser, showPlanModal, setShowPlanModal,
  planForm, setPlanForm, saving, setSaving,
  activeTab, setActiveTab, navigateTo,
  planType, setPlanType, teamSize, setTeamSize,
  teamMonths, setTeamMonths,
  generatedTeamCode, setGeneratedTeamCode,
  showTeamCodeModal, setShowTeamCodeModal,
  teamLeaders, setTeamLeaders,
  expandedTeams, setExpandedTeams,
  teamMembers, setTeamMembers,
  revealedCodes, setRevealedCodes,
  copiedCode, setCopiedCode,
  customDaysInput, setCustomDaysInput,
  appeals, setAppeals,
  maintenanceMode, setMaintenanceMode,
  maintenanceLoading, setMaintenanceLoading,
}: {
  users: UserRow[]; setUsers: React.Dispatch<React.SetStateAction<UserRow[]>>;
  loading: boolean; setLoading: (b: boolean) => void;
  error: string; setError: (s: string) => void;
  searchTerm: string; setSearchTerm: (s: string) => void;
  filterPlan: 'all' | 'active' | 'none' | 'blocked' | 'team'; setFilterPlan: (f: 'all' | 'active' | 'none' | 'blocked' | 'team') => void;
  selectedUser: UserRow | null; setSelectedUser: (u: UserRow | null) => void;
  showPlanModal: boolean; setShowPlanModal: (b: boolean) => void;
  planForm: { planName: string; days: number; customDays: boolean; pricePaid?: number }; setPlanForm: (f: { planName: string; days: number; customDays: boolean; pricePaid?: number }) => void;
  saving: boolean; setSaving: (b: boolean) => void;
  activeTab: Tab; setActiveTab: (t: Tab) => void;
  navigateTo: (n: 1|2|3|4|5) => void;
  planType: 'individual' | 'team'; setPlanType: (t: 'individual' | 'team') => void;
  teamSize: number; setTeamSize: (s: number) => void;
  teamMonths: number; setTeamMonths: (m: number) => void;
  generatedTeamCode: string | null; setGeneratedTeamCode: (c: string | null) => void;
  showTeamCodeModal: boolean; setShowTeamCodeModal: (b: boolean) => void;
  teamLeaders: TeamLeaderRow[]; setTeamLeaders: React.Dispatch<React.SetStateAction<TeamLeaderRow[]>>;
  expandedTeams: Record<string, boolean>; setExpandedTeams: React.Dispatch<React.SetStateAction<Record<string, boolean>>>;
  teamMembers: Record<string, TeamMemberAdminRow[]>; setTeamMembers: React.Dispatch<React.SetStateAction<Record<string, TeamMemberAdminRow[]>>>;
  revealedCodes: Record<string, boolean>; setRevealedCodes: React.Dispatch<React.SetStateAction<Record<string, boolean>>>;
  copiedCode: string | null; setCopiedCode: (c: string | null) => void;
  customDaysInput: Record<string, string>; setCustomDaysInput: React.Dispatch<React.SetStateAction<Record<string, string>>>;
  appeals: AdminAppealRow[]; setAppeals: React.Dispatch<React.SetStateAction<AdminAppealRow[]>>;
  maintenanceMode: boolean; setMaintenanceMode: (b: boolean) => void;
  maintenanceLoading: boolean; setMaintenanceLoading: (b: boolean) => void;
}) {
  const { signOut, refreshSession, isLoading: authLoading, isLoggedIn, profile: adminProfile } = useAuth();
  const autoRefreshRef = useRef<number | null>(null);

  // ── v18: Audit log helper — uses log_admin_action RPC (SECURITY DEFINER) ─
  // Previously used direct insert which failed due to RLS.
  // Now uses the SECURITY DEFINER RPC function which bypasses RLS.
  // Falls back to direct insert only if RPC doesn't exist.
  const logAdminAction = useCallback(async (
    actionType: string,
    targetUserId?: string,
    targetUserEmail?: string,
    targetUserName?: string,
    details?: string,
    metadata?: Record<string, unknown>,
  ) => {
    try {
      // Try RPC first (SECURITY DEFINER, bypasses RLS)
      const { error: rpcErr } = await supabase.rpc('log_admin_action', {
        p_admin_email: adminProfile?.email || '',
        p_action_type: actionType,
        p_target_user_id: targetUserId || null,
        p_target_user_email: targetUserEmail || '',
        p_target_user_name: targetUserName || '',
        p_details: details || '',
        p_metadata: metadata || {},
      });
      if (!rpcErr) return;

      // RPC failed — try direct insert as fallback
      console.warn('[Admin] Audit RPC failed, trying direct insert:', rpcErr.message);
      await supabase.from('admin_audit_log').insert({
        admin_email: adminProfile?.email || '',
        action_type: actionType,
        target_user_id: targetUserId || null,
        target_user_email: targetUserEmail || '',
        target_user_name: targetUserName || '',
        details: details || '',
        metadata: metadata || {},
      });
    } catch (err) {
      // Non-critical — table might not exist yet
      console.warn('[Admin] Audit log insert failed:', err);
    }
  }, [adminProfile?.email]);

  const handleLogout = async () => {
    sessionStorage.removeItem('rhc_admin_authed');
    await signOut();
    navigateTo(1);
  };

  const fetchUsers = useCallback(async (retryCount = 0): Promise<void> => {
    setLoading(true);
    setError('');
    try {
      const { data, error: rpcErr } = await supabase.rpc('admin_get_all_users');
      if (rpcErr) {
        const msg = rpcErr.message?.toLowerCase() || '';
        if ((msg.includes('jwt') || msg.includes('token') || msg.includes('401') || msg.includes('403') || msg.includes('access denied')) && retryCount < 1) {
          console.warn('[Admin] Auth error, refreshing session...');
          const refreshed = await refreshSession();
          if (refreshed) {
            return fetchUsers(retryCount + 1);
          }
        }
        console.warn('[Admin] RPC failed, trying direct query:', rpcErr.message);
        const { data: fallback, error: fbErr } = await supabase
          .from('profiles')
          .select('id, name, email, phone, plan_active, plan_name, plan_end_date, is_admin, is_team_leader, team_plan_id, is_blocked_by_admin, plan_change_log, queued_plan_name, queued_plan_end_date, created_at')
          .order('created_at', { ascending: false });
        if (fbErr) throw fbErr;
        setUsers(fallback || []);
      } else {
        setUsers(data || []);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to fetch users. Run schema_v5.sql in Supabase.';
      setError(msg);
      console.error('[Admin] Fetch error:', err);
    } finally {
      setLoading(false);
    }
  }, [refreshSession, setUsers, setError, setLoading]);

  const fetchTeamLeaders = useCallback(async (): Promise<void> => {
    try {
      const { data, error: rpcErr } = await supabase.rpc('admin_get_team_leaders');
      if (rpcErr) {
        console.warn('[Admin] Team leaders RPC failed:', rpcErr.message);
        // v34: No direct team_plans fallback — RLS no longer has admin policies
        // The RPC is SECURITY DEFINER and should always work
        setTeamLeaders([]);
      } else {
        setTeamLeaders((data as TeamLeaderRow[]) || []);
      }
    } catch (err: unknown) {
      console.error('[Admin] Fetch team leaders error:', err);
    }
  }, [setTeamLeaders]);

  const fetchAppeals = useCallback(async (): Promise<void> => {
    try {
      const { data, error: rpcErr } = await supabase.rpc('admin_get_appeals');
      if (rpcErr) {
        console.warn('[Admin] Appeals RPC failed, trying fallback:', rpcErr.message);
        // Fallback: direct query
        const { data: fallback, error: fbErr } = await supabase
          .from('admin_appeals')
          .select('id, user_id, appeal_text, submitted_at, is_resolved, resolved_by_admin')
          .order('is_resolved', { ascending: true })
          .order('submitted_at', { ascending: false });
        if (fbErr) throw fbErr;
        if (fallback) {
          // Fetch user names for each appeal
          const appealRows: AdminAppealRow[] = await Promise.all(
            (fallback as Record<string, unknown>[]).map(async (a) => {
              let userName = 'Unknown';
              let userEmail = 'unknown@email.com';
              try {
                const { data: prof } = await supabase
                  .from('profiles')
                  .select('name, email')
                  .eq('id', a.user_id as string)
                  .single();
                if (prof) {
                  userName = (prof as Record<string, unknown>).name as string || 'Unknown';
                  userEmail = (prof as Record<string, unknown>).email as string || 'unknown@email.com';
                }
              } catch { /* ignore */ }
              return {
                id: a.id as string,
                user_id: a.user_id as string,
                user_name: userName,
                user_email: userEmail,
                appeal_text: a.appeal_text as string,
                submitted_at: a.submitted_at as string,
                is_resolved: a.is_resolved as boolean,
                resolved_by_admin: a.resolved_by_admin as boolean,
              };
            })
          );
          setAppeals(appealRows);
        }
      } else {
        setAppeals((data as AdminAppealRow[]) || []);
      }
    } catch (err: unknown) {
      console.error('[Admin] Fetch appeals error:', err);
    }
  }, [setAppeals]);

  const toggleTeamExpand = useCallback(async (teamPlanId: string) => {
    const isExpanded = expandedTeams[teamPlanId] || false;

    if (!isExpanded && !teamMembers[teamPlanId]) {
      try {
        // v34: Use SECURITY DEFINER RPC instead of direct query (no admin RLS on team_members)
        const { data: rpcData, error: rpcErr } = await supabase.rpc('admin_get_team_members', {
          p_team_plan_id: teamPlanId,
        });
        if (rpcErr) {
          console.warn('[Admin] Team members RPC failed:', rpcErr.message);
        } else if (rpcData) {
          const members = (rpcData as Record<string, unknown>[]).map((m): TeamMemberAdminRow => ({
            user_id: String(m.user_id ?? ''),
            name: String(m.name ?? '—') || '—',
            email: String(m.email ?? '—') || '—',
            phone: String(m.phone ?? '—') || '—',
            joined_at: String(m.joined_at ?? ''),
            plan_active: Boolean(m.plan_active) || false,
            is_blocked: Boolean(m.is_blocked) || false,
          }));
          setTeamMembers((prev: Record<string, TeamMemberAdminRow[]>) => ({ ...prev, [teamPlanId]: members }));
        }
      } catch (err: unknown) {
        console.error('[Admin] Fetch team members error:', err);
      }
    }

    setExpandedTeams((prev: Record<string, boolean>) => ({ ...prev, [teamPlanId]: !isExpanded }));
  }, [expandedTeams, teamMembers, setTeamMembers, setExpandedTeams]);

  const toggleRevealCode = useCallback((teamPlanId: string) => {
    setRevealedCodes((prev: Record<string, boolean>) => ({ ...prev, [teamPlanId]: !prev[teamPlanId] }));
  }, [setRevealedCodes]);

  const copyTeamCode = useCallback((code: string, teamPlanId: string) => {
    navigator.clipboard.writeText(code).catch(() => {});
    setCopiedCode(teamPlanId);
    setTimeout(() => setCopiedCode(null), 2000);
  }, [setCopiedCode]);

  const removeTeamMember = useCallback(async (teamPlanId: string, userId: string) => {
    if (!confirm('Remove this member from the team?')) return;
    try {
      // v34: Use SECURITY DEFINER RPC instead of direct delete (no admin RLS on team_members)
      const { error: rpcErr } = await supabase.rpc('admin_remove_team_member', {
        p_team_plan_id: teamPlanId,
        p_user_id: userId,
      });
      if (rpcErr) throw rpcErr;
      setTeamMembers((prev: Record<string, TeamMemberAdminRow[]>) => ({
        ...prev,
        [teamPlanId]: (prev[teamPlanId] || []).filter(m => m.user_id !== userId),
      }));
      fetchTeamLeaders();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Failed to remove member.');
    }
  }, [setTeamMembers, fetchTeamLeaders]);

  // ── Maintenance & Notification Fetch (must be before useEffect that uses them) ──
  const fetchMaintenanceStatus = useCallback(async () => {
    try {
      const { data, error } = await supabase.rpc('get_maintenance_status');
      if (!error && data) {
        const result = data as { enabled: boolean };
        setMaintenanceMode(result.enabled);
      }
    } catch { /* ignore */ }
  }, [setMaintenanceMode]);

  // Wait for auth to settle before fetching
  useEffect(() => {
    if (authLoading) return;
    if (!isLoggedIn) return;
    fetchUsers();
    fetchTeamLeaders();
    fetchAppeals();
    fetchMaintenanceStatus();
  }, [authLoading, isLoggedIn, fetchUsers, fetchTeamLeaders, fetchAppeals, fetchMaintenanceStatus]);

  // Auto-refresh + visibility re-fetch
  useEffect(() => {
    autoRefreshRef.current = window.setInterval(() => {
      fetchUsers();
      fetchTeamLeaders();
      fetchAppeals();
    }, 2 * 60 * 1000);

    const handleVisibility = () => {
      if (document.visibilityState === 'visible') {
        fetchUsers();
        fetchTeamLeaders();
        fetchAppeals();
      }
    };
    document.addEventListener('visibilitychange', handleVisibility);

    return () => {
      if (autoRefreshRef.current) clearInterval(autoRefreshRef.current);
      document.removeEventListener('visibilitychange', handleVisibility);
    };
  }, [fetchUsers, fetchTeamLeaders, fetchAppeals]);

  // ── Stats ──────────────────────────────────────────────────────────────────
  const now = new Date();
  const individualActiveUsers = users.filter(u => u.plan_active && !u.is_blocked_by_admin && !u.is_team_leader && !u.team_plan_id);
  const teamActiveUsers = users.filter(u => u.plan_active && !u.is_blocked_by_admin && (u.is_team_leader || u.team_plan_id));
  const blockedUsers = users.filter(u => u.is_blocked_by_admin);

  const stats = {
    total: users.length,
    active: users.filter(u => u.plan_active && !u.is_blocked_by_admin).length,
    free: users.filter(u => !u.plan_active && !u.is_blocked_by_admin && u.email?.toLowerCase() !== ADMIN_EMAIL.toLowerCase()).length,
    blockedCount: blockedUsers.length,
    expiringSoon: users.filter(u => u.plan_active && daysLeft(u.plan_end_date) <= 7).length,
    newToday: users.filter(u => {
      const d = new Date(u.created_at);
      return d.getDate() === now.getDate() && d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear();
    }).length,
    newThisWeek: users.filter(u => {
      const d = new Date(u.created_at);
      const weekAgo = new Date(); weekAgo.setDate(weekAgo.getDate() - 7);
      return d >= weekAgo;
    }).length,
    individualRevenue: individualActiveUsers.reduce((sum, u) => sum + (PLAN_PRICES[u.plan_name!] || 0), 0),
    teamRevenue: teamLeaders.filter(tl => tl.plan_active).reduce((sum, tl) => sum + (Number(tl.price_paid) || 0), 0),
    conversionRate: users.length > 0 ? Math.round((users.filter(u => u.plan_active).length / users.length) * 100) : 0,
    teamLeaders: teamLeaders.length,
    totalTeamMembers: teamLeaders.reduce((sum, tl) => sum + tl.member_count, 0),
    pendingAppeals: appeals.filter(a => !a.is_resolved).length,
  };

  const totalRevenue = stats.individualRevenue + stats.teamRevenue;

  const planDist = [
    { label: '1 Month', value: users.filter(u => u.plan_active && u.plan_name === '1 Month').length, color: '#2563eb' },
    { label: '2 Months', value: users.filter(u => u.plan_active && u.plan_name === '2 Months').length, color: '#7c3aed' },
    { label: '3 Months', value: users.filter(u => u.plan_active && u.plan_name === '3 Months').length, color: '#1a3a6b' },
    { label: '6 Months', value: users.filter(u => u.plan_active && u.plan_name === '6 Months').length, color: '#059669' },
    { label: '8 Months', value: users.filter(u => u.plan_active && u.plan_name === '8 Months').length, color: '#b45309' },
    { label: '12 Months', value: users.filter(u => u.plan_active && u.plan_name === '12 Months').length, color: '#dc2626' },
    { label: 'Team Plans', value: teamActiveUsers.length, color: '#ea580c' },
    { label: 'Free', value: stats.free, color: '#94a3b8' },
  ];

  const monthlyData = Array.from({ length: 6 }, (_, i) => {
    const d = new Date(); d.setMonth(d.getMonth() - (5 - i));
    const m = d.getMonth(); const y = d.getFullYear();
    return {
      l: d.toLocaleDateString('en-IN', { month: 'short' }),
      v: users.filter(u => { const ud = new Date(u.created_at); return ud.getMonth() === m && ud.getFullYear() === y; }).length
    };
  });

  // ── Filtered users ──
  const filteredIndividualUsers = users.filter(u => {
    const q = searchTerm.toLowerCase();
    const isAdminUser = u.email?.toLowerCase() === ADMIN_EMAIL.toLowerCase();
    const matchSearch = !q || u.name?.toLowerCase().includes(q) || u.email?.toLowerCase().includes(q) || u.phone?.includes(q);
    const isTeamUser = u.is_team_leader || !!u.team_plan_id;
    const matchFilter =
      filterPlan === 'all' ||
      (filterPlan === 'active'   && u.plan_active && !isTeamUser) ||
      (filterPlan === 'team'     && isTeamUser) ||
      (filterPlan === 'blocked'  && u.is_blocked_by_admin) ||
      (filterPlan === 'none'     && !u.plan_active && !u.is_blocked_by_admin);
    return matchSearch && matchFilter && !isAdminUser;
  });

  // ── Actions ────────────────────────────────────────────────────────────────
  const activatePlan = async () => {
    if (!selectedUser) return;
    setSaving(true);
    try {
      if (planType === 'team') {
        const teamPrice = TEAM_PLAN_PRICES[teamSize]?.[teamMonths] ?? 0;
        let teamCode = '';
        let rpcSucceeded = false;
        let lastError: string = '';

        // v33: Try RPC with session refresh retry (NO direct DB fallback — RLS blocks it)
        for (let attempt = 0; attempt < 2; attempt++) {
          try {
            const { data, error: rpcErr } = await supabase.rpc('admin_grant_team_plan', {
              p_leader_user_id: selectedUser.id,
              p_team_size: teamSize,
              p_months: teamMonths,
              p_price_paid: Number(teamPrice),
            });
            if (rpcErr) {
              lastError = rpcErr.message || 'Unknown RPC error';
              console.error('[Admin] Team plan RPC failed (attempt ' + (attempt + 1) + '):', rpcErr.message, rpcErr.details, rpcErr.hint, rpcErr.code);
              // If auth error on first attempt, refresh session and retry
              if (attempt === 0 && (lastError.includes('JWT') || lastError.includes('token') || lastError.includes('401') || lastError.includes('Access denied'))) {
                const refreshed = await refreshSession();
                if (refreshed) continue;
              }
            } else {
              const result = data as { success: boolean; team_code?: string; team_plan_id?: string };
              if (result?.success && result.team_code) {
                teamCode = result.team_code;
                rpcSucceeded = true;
                break;
              } else {
                lastError = 'RPC returned unexpected result';
                console.warn('[Admin] Team plan RPC unexpected result:', result);
              }
            }
          } catch (e) {
            lastError = e instanceof Error ? e.message : 'Unknown error';
            console.warn('[Admin] Team plan RPC error (attempt ' + (attempt + 1) + '):', e);
          }
        }

        if (!rpcSucceeded) {
          throw new Error('Failed to grant team plan. Please ensure schema.sql v33 has been run in Supabase SQL Editor. Error: ' + lastError);
        }

        // RPC succeeded — notification was already created inside admin_grant_team_plan

        if (teamCode) {
          setGeneratedTeamCode(teamCode);
          setShowTeamCodeModal(true);
        }
      } else {
        // ── Individual Plan ──
        const days = planForm.customDays ? planForm.days : (planForm.planName === '2 Months' ? 60 : planForm.planName === '3 Months' ? 90 : planForm.planName === '6 Months' ? 180 : planForm.planName === '8 Months' ? 240 : planForm.planName === '12 Months' ? 360 : 30);
        const planName = planForm.customDays ? `Custom (${days}d)` : planForm.planName;
        const isExtension = !!(selectedUser.plan_active && selectedUser.plan_end_date && new Date(selectedUser.plan_end_date) > new Date());
        const pricePaid = planForm.customDays ? (Number(planForm.pricePaid) || 0) : (PLAN_PRICES[planForm.planName] || 0);
        let rpcSucceeded = false;
        let lastError: string = '';

        // v33: Try RPC with session refresh retry (NO direct DB fallback — RLS blocks it)
        for (let attempt = 0; attempt < 2; attempt++) {
          try {
            const { data, error: rpcErr } = await supabase.rpc('admin_grant_plan', {
              target_user_id: selectedUser.id,
              plan_name_val: planName,
              days_val: days,
              price_paid: pricePaid,
              is_extension: isExtension,
            });
            if (rpcErr) {
              lastError = rpcErr.message || 'Unknown RPC error';
              console.error('[Admin] Individual plan RPC failed (attempt ' + (attempt + 1) + '):', rpcErr.message, rpcErr.details, rpcErr.hint, rpcErr.code);
              // If auth error on first attempt, refresh session and retry
              if (attempt === 0 && (lastError.includes('JWT') || lastError.includes('token') || lastError.includes('401') || lastError.includes('Access denied'))) {
                const refreshed = await refreshSession();
                if (refreshed) continue;
              }
            } else {
              const result = data as { success: boolean } | null;
              if (result?.success) {
                rpcSucceeded = true;
                break;
              } else {
                lastError = 'RPC returned unexpected result';
                console.warn('[Admin] Individual plan RPC unexpected result:', result);
              }
            }
          } catch (e) {
            lastError = e instanceof Error ? e.message : 'Unknown error';
            console.warn('[Admin] Individual plan RPC error (attempt ' + (attempt + 1) + '):', e);
          }
        }

        if (!rpcSucceeded) {
          throw new Error('Failed to grant individual plan. Please ensure schema.sql v33 has been run in Supabase SQL Editor. Error: ' + lastError);
        }
        // RPC succeeded — notification was already created inside admin_grant_plan
      }

      setShowPlanModal(false);
      setSelectedUser(null);

      // ── v15: Client-side audit log ──
      const actionType = planType === 'team' ? 'PLAN_GRANT_TEAM' : 'PLAN_GRANT';
      const planName = planType === 'team' ? `Team Plan (${teamSize} members, ${teamMonths} months)` : planForm.planName;
      logAdminAction(
        actionType,
        selectedUser.id,
        selectedUser.email,
        selectedUser.name,
        `Granted ${planName}${planType === 'team' && generatedTeamCode ? ` (Code: ${generatedTeamCode})` : ''}`,
        { planType, planName, teamSize: planType === 'team' ? teamSize : undefined, teamMonths: planType === 'team' ? teamMonths : undefined, days: planType === 'individual' ? planForm.days : undefined },
      ).catch(() => {});

      fetchUsers();
      setTimeout(() => fetchTeamLeaders(), 600);
    } catch (err: unknown) {
      // v29: Show actual error message — Supabase PostgrestError is NOT an instanceof Error
      const errMsg = (err instanceof Error) ? err.message
        : (err as Record<string, unknown>)?.message ? String((err as Record<string, unknown>)?.message)
        : (err as Record<string, unknown>)?.details ? String((err as Record<string, unknown>)?.details)
        : 'Failed to activate plan.';
      console.error('[Admin] activatePlan error:', err);
      alert('Failed to activate plan: ' + errMsg);
    } finally { setSaving(false); }
  };

  const deactivatePlan = async (userId: string) => {
    if (!confirm('Deactivate this plan?')) return;
    try {
      const { error: rpcErr } = await supabase.rpc('admin_revoke_plan', {
        target_user_id: userId,
      });
      if (rpcErr) {
        console.warn('[Admin] RPC revoke failed, trying direct update:', rpcErr.message);
        const { error: updateErr } = await supabase
          .from('profiles')
          .update({ plan_active: false, plan_name: null, plan_end_date: null })
          .eq('id', userId);
        if (updateErr) throw updateErr;
      }
      // v15: Client-side audit log
      const targetUser = users.find(u => u.id === userId);
      logAdminAction('PLAN_REVOKE', userId, targetUser?.email, targetUser?.name, `Revoked plan: ${targetUser?.plan_name || 'Unknown'}`).catch(() => {});
      fetchUsers();
    } catch (err: unknown) { alert(err instanceof Error ? err.message : 'Failed to revoke plan.'); }
  };

  // v28: extendPlanDays uses admin_extend_plan RPC (creates notification inside DB)
  const extendPlanDays = async (userId: string, days: number) => {
    if (!days || days <= 0) return;
    try {
      const { error: rpcErr } = await supabase.rpc('admin_extend_plan', {
        p_user_id: userId,
        p_days: days,
        p_price_paid: 0,
      });
      if (rpcErr) {
        console.warn('[Admin] admin_extend_plan RPC failed, using fallback:', rpcErr.message);
        // Fallback: direct DB update
        const user = users.find(u => u.id === userId);
        if (!user) return;
        const currentEnd = user.plan_end_date ? new Date(user.plan_end_date) : new Date();
        const newEnd = new Date(currentEnd.getTime() + days * 86400000);
        const { error: updateErr } = await supabase
          .from('profiles')
          .update({ plan_end_date: newEnd.toISOString() })
          .eq('id', userId);
        if (updateErr) throw updateErr;

        // Update plan_change_log
        try {
          await supabase.rpc('admin_append_plan_change_log', {
            p_user_id: userId,
            p_change_text: `EXTENDED by ${days} days (Rs.0) at ${new Date().toLocaleString('en-IN')}`,
          });
        } catch {
          // Fallback: direct append to plan_change_log
          try {
            const { data: profData } = await supabase
              .from('profiles')
              .select('plan_change_log')
              .eq('id', userId)
              .single();
            const existingLog = (profData as { plan_change_log?: string })?.plan_change_log || '';
            await supabase
              .from('profiles')
              .update({
                plan_change_log: existingLog + '\nEXTENDED by ' + days + ' days (Rs.0) at ' + new Date().toLocaleString('en-IN'),
              })
              .eq('id', userId);
          } catch { /* non-critical */ }
        }

        // RPC failed — create notification manually since RPC didn't create it
        try {
          await supabase.from('notifications').insert({
            user_id: userId,
            title: 'Plan Extended',
            message: `Your plan has been extended by ${days} days by the administrator. New expiry: ${newEnd.toLocaleDateString('en-IN')}`,
            target_group: null,
            is_read: false,
            notification_type: 'extension',
            cta_text: 'Start Practice Now',
            cta_action: 'navigate:2',
          });
        } catch (notifErr) {
          console.warn('[Admin] Failed to insert extension notification:', notifErr);
        }
      }
      // If RPC succeeded, the notification was already created inside admin_extend_plan

      setCustomDaysInput((prev: Record<string, string>) => ({ ...prev, [`ext_${userId}`]: '' }));
      // v15: Client-side audit log
      const extTargetUser = users.find(u => u.id === userId);
      logAdminAction('EXTEND_PLAN', userId, extTargetUser?.email, extTargetUser?.name, `Extended plan by ${days} days`, { days }).catch(() => {});
      fetchUsers();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Failed to extend plan.');
    }
  };

  // v17: decreasePlanDays uses admin_decrease_plan RPC (creates notification inside DB)
  const decreasePlanDays = async (userId: string, days: number) => {
    if (!days || days <= 0) return;
    let notificationCreated = false;
    try {
      const { error: rpcErr } = await supabase.rpc('admin_decrease_plan', {
        p_user_id: userId,
        p_days: days,
      });
      if (rpcErr) {
        console.warn('[Admin] admin_decrease_plan RPC failed, using fallback:', rpcErr.message);
        // Fallback: direct DB update
        const user = users.find(u => u.id === userId);
        if (!user || !user.plan_end_date) return;
        const currentEnd = new Date(user.plan_end_date);
        const newEnd = new Date(currentEnd.getTime() - days * 86400000);
        if (newEnd <= new Date()) {
          const { error: updateErr } = await supabase
            .from('profiles')
            .update({ plan_active: false, plan_name: null, plan_end_date: null })
            .eq('id', userId);
          if (updateErr) throw updateErr;
        } else {
          const { error: updateErr } = await supabase
            .from('profiles')
            .update({ plan_end_date: newEnd.toISOString() })
            .eq('id', userId);
          if (updateErr) throw updateErr;
        }

        // Update plan_change_log
        try {
          // Try RPC first
          await supabase.rpc('admin_append_plan_change_log', {
            p_user_id: userId,
            p_change_text: `DECREASED by ${days} days at ${new Date().toLocaleDateString('en-IN')}`,
          });
        } catch {
          // Fallback: direct append to plan_change_log
          try {
            const { data: profData } = await supabase
              .from('profiles')
              .select('plan_change_log')
              .eq('id', userId)
              .single();
            const existingLog = (profData as { plan_change_log?: string })?.plan_change_log || '';
            await supabase
              .from('profiles')
              .update({
                plan_change_log: existingLog + '\nDECREASED by ' + days + ' days at ' + new Date().toLocaleString('en-IN'),
              })
              .eq('id', userId);
          } catch { /* non-critical */ }
        }

        // Create notification manually — guaranteed attempt
        try {
          const { error: notifErr } = await supabase.from('notifications').insert({
            user_id: userId,
            title: 'Plan Duration Decreased',
            message: `Your plan duration has been decreased by ${days} days by the administrator.`,
            target_group: null,
            is_read: false,
            notification_type: 'plan_decrease',
            cta_text: 'Start Practice Now',
            cta_action: 'navigate:2',
          });
          if (!notifErr) notificationCreated = true;
          else console.warn('[Admin] Direct notification insert failed:', notifErr.message);
        } catch (notifErr) {
          console.warn('[Admin] Failed to insert decrease notification:', notifErr);
        }
      } else {
        // RPC succeeded — notification was created inside admin_decrease_plan SQL function
        notificationCreated = true;
      }

      // If notification still wasn't created, try one more time with a simple insert
      if (!notificationCreated) {
        try {
          await supabase.from('notifications').insert({
            user_id: userId,
            title: 'Plan Duration Decreased',
            message: `Your plan duration has been decreased by ${days} days by the administrator.`,
            target_group: null,
            is_read: false,
            notification_type: 'plan_decrease',
            cta_text: 'Start Practice Now',
            cta_action: 'navigate:2',
          });
        } catch { /* best effort */ }
      }

      setCustomDaysInput((prev: Record<string, string>) => ({ ...prev, [`ext_${userId}`]: '' }));
      // v15: Client-side audit log
      const decTargetUser = users.find(u => u.id === userId);
      logAdminAction('DECREASE_PLAN', userId, decTargetUser?.email, decTargetUser?.name, `Decreased plan by ${days} days`, { days }).catch(() => {});
      fetchUsers();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Failed to decrease plan.');
    }
  };

  const blockUser = async (userId: string) => {
    if (!confirm('Block this user? They will lose plan access and see a block notice with admin contact.')) return;
    // Optimistic update — toggle button immediately
    setUsers(prev => prev.map(u => u.id === userId ? { ...u, is_blocked_by_admin: true } : u));
    try {
      const { error: updateErr } = await supabase
        .from('profiles')
        .update({ is_blocked_by_admin: true })
        .eq('id', userId);
      if (updateErr) throw updateErr;
      // v15: Client-side audit log
      const blockTargetUser = users.find(u => u.id === userId);
      logAdminAction('BLOCK_USER', userId, blockTargetUser?.email, blockTargetUser?.name, 'Blocked user by admin').catch(() => {});
      fetchUsers();
      fetchTeamLeaders();
    } catch (err: unknown) {
      // Revert optimistic update on error
      setUsers(prev => prev.map(u => u.id === userId ? { ...u, is_blocked_by_admin: false } : u));
      alert(err instanceof Error ? err.message : 'Failed to block user.');
    }
  };

  const unblockUser = async (userId: string) => {
    // Optimistic update — toggle button immediately
    setUsers(prev => prev.map(u => u.id === userId ? { ...u, is_blocked_by_admin: false } : u));
    try {
      const { data, error: rpcErr } = await supabase.rpc('admin_unblock_user', {
        p_user_id: userId,
      });
      if (rpcErr) {
        console.warn('[Admin] admin_unblock_user RPC failed, using fallback:', rpcErr.message);
        const { error: updateErr } = await supabase
          .from('profiles')
          .update({ is_blocked_by_admin: false })
          .eq('id', userId);
        if (updateErr) throw updateErr;
        try {
          const user = users.find(u => u.id === userId);
          if (user && user.plan_name && user.plan_end_date && new Date(user.plan_end_date) > new Date() && !user.plan_active) {
            await supabase
              .from('profiles')
              .update({ plan_active: true })
              .eq('id', userId);
          }
        } catch { /* non-critical */ }
      } else {
        const result = data as { success: boolean; error?: string };
        if (!result.success) {
          alert(result.error || 'Failed to unblock user');
          return;
        }
      }
      // v15: Client-side audit log
      const unblockTargetUser = users.find(u => u.id === userId);
      logAdminAction('UNBLOCK_USER', userId, unblockTargetUser?.email, unblockTargetUser?.name, 'Unblocked user by admin').catch(() => {});
      fetchUsers();
      fetchTeamLeaders();
    } catch (err: unknown) {
      // Revert optimistic update on error
      setUsers(prev => prev.map(u => u.id === userId ? { ...u, is_blocked_by_admin: true } : u));
      alert(err instanceof Error ? err.message : 'Failed to unblock user.');
    }
  };

  // v17: Delete team plan RPC
  const deleteTeamPlan = async (teamPlanId: string) => {
    if (!confirm('DELETE this team plan entirely? This will remove all members and the team plan record. This cannot be undone.')) return;
    try {
      const { data, error: rpcErr } = await supabase.rpc('admin_delete_team_plan', {
        p_team_plan_id: teamPlanId,
      });
      if (rpcErr) throw rpcErr;
      const result = (data as { success: boolean; error?: string }) || { success: false };
      if (!result.success) {
        alert(result.error || 'Failed to delete team plan');
        return;
      }
      fetchUsers();
      fetchTeamLeaders();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Failed to delete team plan.');
    }
  };

  // v17: Revoke team plan RPC (deactivate without deleting)
  const revokeTeamPlan = async (teamPlanId: string) => {
    if (!confirm('Revoke this team plan? The plan will be deactivated but NOT deleted (can be renewed later).')) return;
    try {
      const { data, error: rpcErr } = await supabase.rpc('admin_revoke_team_plan', {
        p_team_plan_id: teamPlanId,
      });
      if (rpcErr) throw rpcErr;
      const result = data as { success: boolean; error?: string };
      if (!result.success) {
        alert(result.error || 'Failed to revoke team plan');
        return;
      }
      fetchUsers();
      fetchTeamLeaders();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Failed to revoke team plan.');
    }
  };

  // v17: Clear user plans RPC
  const clearUserPlans = async (userId: string) => {
    if (!confirm('Clear ALL plans for this user? This will deactivate their plan, clear queued plans, remove from teams, and deactivate any team they lead. This cannot be undone.')) return;
    try {
      const { data, error: rpcErr } = await supabase.rpc('admin_clear_user_plans', {
        p_user_id: userId,
      });
      if (rpcErr) throw rpcErr;
      const result = data as { success: boolean; error?: string };
      if (!result.success) {
        alert(result.error || 'Failed to clear user plans');
        return;
      }
      fetchUsers();
      fetchTeamLeaders();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Failed to clear user plans.');
    }
  };

  const resolveAppeal = async (appealId: string, unblock: boolean) => {
    try {
      const { data, error: rpcErr } = await supabase.rpc('admin_resolve_appeal', {
        p_appeal_id: appealId,
        p_resolved_by_admin: unblock,
      });
      if (rpcErr) {
        console.warn('[Admin] Resolve appeal RPC failed, trying fallback:', rpcErr.message);
        // Fallback: direct update
        const { error: updateErr } = await supabase
          .from('admin_appeals')
          .update({ is_resolved: true, resolved_by_admin: unblock })
          .eq('id', appealId);
        if (updateErr) throw updateErr;
        // If unblock, also unblock the user
        if (unblock) {
          try {
            const { data: appealData } = await supabase
              .from('admin_appeals')
              .select('user_id')
              .eq('id', appealId)
              .single();
            if (appealData) {
              const userId = (appealData as Record<string, unknown>).user_id as string;
              await supabase
                .from('profiles')
                .update({ is_blocked_by_admin: false })
                .eq('id', userId);
            }
          } catch { /* non-critical */ }
        }
      } else {
        const result = data as { success: boolean; error?: string };
        if (!result.success) {
          alert(result.error || 'Failed to resolve appeal');
          return;
        }
      }
      // v15: Client-side audit log
      logAdminAction('RESOLVE_APPEAL', undefined, undefined, undefined, `Appeal resolved: ${unblock ? 'unblocked user' : 'denied unblock'}`, { appealId, unblock }).catch(() => {});
      fetchAppeals();
      fetchUsers();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Failed to resolve appeal.');
    }
  };

  // ── Maintenance & Notification Functions ───────────────────────────────────
  const toggleMaintenance = useCallback(async (enabled: boolean) => {
    setMaintenanceLoading(true);
    try {
      const { error } = await supabase.rpc('admin_set_maintenance', { p_enabled: enabled });
      if (error) {
        alert('Failed to toggle maintenance mode: ' + error.message);
        return;
      }
      setMaintenanceMode(enabled);
      // v15: Client-side audit log
      logAdminAction('TOGGLE_MAINTENANCE', undefined, undefined, undefined, enabled ? 'Enabled maintenance mode' : 'Disabled maintenance mode', { enabled }).catch(() => {});
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Failed to toggle maintenance');
    } finally {
      setMaintenanceLoading(false);
    }
  }, [setMaintenanceMode, setMaintenanceLoading, logAdminAction]);

  // ── Admin Notification Send ───────────────────────────────────────────────
  const [adminNotifMessage, setAdminNotifMessage] = useState('');
  const [adminNotifTarget, setAdminNotifTarget] = useState<'guest' | 'free_users' | 'paid_users' | 'all'>('all');
  const [adminNotifSending, setAdminNotifSending] = useState(false);
  const [adminNotifResult, setAdminNotifResult] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const handleAdminSendNotification = useCallback(async () => {
    if (!adminNotifMessage.trim()) {
      setAdminNotifResult({ type: 'error', text: 'Please enter a message.' });
      return;
    }
    setAdminNotifSending(true);
    setAdminNotifResult(null);
    try {
      const { data, error } = await supabase.rpc('admin_send_notification', {
        p_title: 'Admin Notification',
        p_message: adminNotifMessage.trim(),
        p_target_group: adminNotifTarget,
      });
      if (error) {
        // v17 FIX: If the RPC fails because log_admin_action doesn't exist,
        // try direct notification insert as fallback
        const errMsg = error.message || '';
        if (errMsg.includes('log_admin_action') || errMsg.includes('does not exist')) {
          console.warn('[Admin] RPC failed due to missing log_admin_action, using direct insert fallback');
          // Direct insert: single broadcast row with user_id=NULL
          const { error: insertErr } = await supabase
            .from('notifications')
            .insert({
              title: 'Admin Notification',
              message: adminNotifMessage.trim(),
              notification_type: 'admin_broadcast',
              target_group: adminNotifTarget,
            });
          if (insertErr) {
            setAdminNotifResult({ type: 'error', text: insertErr.message });
          } else {
            setAdminNotifResult({ type: 'success', text: 'Notification sent successfully!' });
            setAdminNotifMessage('');
            logAdminAction('SEND_NOTIFICATION', undefined, undefined, undefined, `Sent notification to ${adminNotifTarget}: ${adminNotifMessage.trim().substring(0, 100)}`, { target_group: adminNotifTarget }).catch(() => {});
          }
        } else {
          setAdminNotifResult({ type: 'error', text: errMsg });
        }
      } else if (data) {
        const result = data as { success: boolean; recipients?: number; error?: string };
        if (result.success) {
          setAdminNotifResult({ type: 'success', text: `Notification sent to ${result.recipients ?? 0} recipient(s)!` });
          setAdminNotifMessage('');
          // v15: Client-side audit log
          logAdminAction('SEND_NOTIFICATION', undefined, undefined, undefined, `Sent notification to ${adminNotifTarget}: ${adminNotifMessage.trim().substring(0, 100)}`, { target_group: adminNotifTarget, recipients: result.recipients }).catch(() => {});
        } else {
          setAdminNotifResult({ type: 'error', text: result.error || 'Failed to send notification' });
        }
      }
    } catch (err: unknown) {
      setAdminNotifResult({ type: 'error', text: err instanceof Error ? err.message : 'Network error' });
    } finally {
      setAdminNotifSending(false);
    }
  }, [adminNotifMessage, adminNotifTarget, logAdminAction]);


  // ── Tabs definition ────────────────────────────────────────────────────────
  const TABS: { id: Tab; label: string; icon: React.ReactNode }[] = [
    { id: 'dashboard', label: 'Dashboard', icon: <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></svg> },
    { id: 'users', label: 'Users', icon: <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg> },
    { id: 'team-leaders', label: 'Teams', icon: <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg> },
    { id: 'plans', label: 'Plans', icon: <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="1" y="4" width="22" height="16" rx="2" ry="2"/><line x1="1" y1="10" x2="23" y2="10"/></svg> },
    { id: 'analytics', label: 'Analytics', icon: <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg> },
    { id: 'appeals', label: 'Appeals', icon: <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg> },
    { id: 'reviews', label: 'Reviews', icon: <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polygon points="12,2 15.09,8.26 22,9.27 17,14.14 18.18,21.02 12,17.77 5.82,21.02 7,14.14 2,9.27 8.91,8.26"/></svg> },
    { id: 'audit-log', label: 'Audit Log', icon: <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg> },
    { id: 'settings', label: 'Settings', icon: <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1Z"/></svg> },
  ];

  return (
    <div className="ap-root">
      {/* ── Top Bar ── */}
      <header className="ap-topbar">
        <div className="ap-topbar-left">
          <div className="ap-topbar-logo">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
            </svg>
          </div>
          <span className="ap-topbar-title">Admin Console</span>
          <span className="ap-topbar-badge">v18</span>
        </div>
        <div className="ap-topbar-right">
          <div className="ap-topbar-status">
            <span className="ap-status-dot" />
            <span>{stats.active} Active</span>
          </div>
          {stats.pendingAppeals > 0 && (
            <span style={{ background: '#fef3c7', color: '#92400e', padding: '2px 8px', borderRadius: '999px', fontSize: '.68rem', fontWeight: 700 }}>
              {stats.pendingAppeals} Appeal{stats.pendingAppeals > 1 ? 's' : ''}
            </span>
          )}
          <button className="ap-icon-btn" onClick={() => { fetchUsers(); fetchTeamLeaders(); fetchAppeals(); }} title="Refresh data">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/>
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
            </svg>
          </button>
          <button className="ap-icon-btn ap-close-icon" onClick={() => navigateTo(1)} title="Close">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
      </header>

      <div className="ap-layout">
        {/* ── Sidebar ── */}
        <aside className="ap-sidebar">
          <nav className="ap-nav">
            {TABS.map(t => (
              <button
                key={t.id}
                className={`ap-nav-item ${activeTab === t.id ? 'active' : ''}`}
                onClick={() => setActiveTab(t.id)}
              >
                <span className="ap-nav-icon">{t.icon}</span>
                <span className="ap-nav-label">{t.label}</span>
                {t.id === 'users' && <span className="ap-nav-count">{users.length}</span>}
                {t.id === 'team-leaders' && <span className="ap-nav-count">{teamLeaders.length}</span>}
                {t.id === 'appeals' && stats.pendingAppeals > 0 && <span className="ap-nav-count" style={{ background: '#fef3c7', color: '#92400e' }}>{stats.pendingAppeals}</span>}
              </button>
            ))}
          </nav>
          <div className="ap-sidebar-bottom">
            <button className="ap-nav-item ap-logout-btn" onClick={handleLogout}>
              <span className="ap-nav-icon"><LogoutIcon /></span>
              <span className="ap-nav-label">Logout</span>
            </button>
          </div>
        </aside>

        {/* ── Main Content ── */}
        <main className="ap-main">
          {error && (
            <div className="ap-banner error">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
              </svg>
              {error}
            </div>
          )}

          {/* ── DASHBOARD ── */}
          {activeTab === 'dashboard' && (
            <div className="ap-section">
              <div className="ap-section-title">Overview</div>
              <div className="ap-stats-grid">
                {[
                  { label: 'Total Users', value: stats.total, icon: <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg>, color: '#1a3a6b' },
                  { label: 'Active Plans', value: stats.active, icon: <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>, color: '#10b981' },
                  { label: 'Free Users', value: stats.free, icon: <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><circle cx="12" cy="12" r="10"/><path d="M8 12h8"/></svg>, color: '#64748b' },
                  { label: 'New This Week', value: stats.newThisWeek, icon: <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><line x1="19" y1="8" x2="19" y2="14"/><line x1="22" y1="11" x2="16" y2="11"/></svg>, color: '#f59e0b' },
                  { label: 'Expiring Soon', value: stats.expiringSoon, icon: <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>, color: '#ef4444' },
                  { label: 'Individual Revenue', value: `₹${stats.individualRevenue.toLocaleString('en-IN')}`, icon: <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/></svg>, color: '#8b5cf6' },
                  { label: 'Team Revenue', value: `₹${stats.teamRevenue.toLocaleString('en-IN')}`, icon: <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>, color: '#ea580c' },
                  { label: 'Total Revenue', value: `₹${totalRevenue.toLocaleString('en-IN')}`, icon: <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/></svg>, color: '#059669' },
                ].map((s, i) => (
                  <div key={i} className="ap-stat-card" style={{ '--accent': s.color } as React.CSSProperties}>
                    <div className="ap-stat-icon">{s.icon}</div>
                    <div className="ap-stat-value">{s.value}</div>
                    <div className="ap-stat-label">{s.label}</div>
                  </div>
                ))}
              </div>
              <div className="ap-premium-row">
                <div className="ap-conversion-card">
                  <div className="ap-conversion-label">Conversion Rate</div>
                  <div className="ap-conversion-value">{stats.conversionRate}%</div>
                  <div className="ap-conversion-bar"><div className="ap-conversion-fill" style={{ width: `${stats.conversionRate}%` }} /></div>
                  <div className="ap-conversion-desc">{stats.active} of {stats.total} users have active plans</div>
                </div>
                <div className="ap-quick-actions">
                  <div className="ap-quick-title">Quick Actions</div>
                  <div className="ap-quick-grid">
                    <button className="ap-quick-btn" onClick={() => setActiveTab('users')}><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg>Manage Users</button>
                    <button className="ap-quick-btn" onClick={() => setActiveTab('team-leaders')}><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>Manage Teams</button>
                    <button className="ap-quick-btn" onClick={() => setActiveTab('appeals')}><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>View Appeals ({stats.pendingAppeals})</button>
                    <button className="ap-quick-btn" onClick={() => { fetchUsers(); fetchTeamLeaders(); fetchAppeals(); }}><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="23 4 23 10 17 10"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10"/></svg>Refresh Data</button>
                  </div>
                </div>
              </div>
              <div className="ap-charts-row">
                <div className="ap-chart-card"><div className="ap-card-title">Registrations (6 months)</div><BarChart data={monthlyData} /></div>
                <div className="ap-chart-card"><div className="ap-card-title">Plan Distribution</div><DonutChart segments={planDist} /></div>
              </div>
              {stats.expiringSoon > 0 && (
                <div className="ap-banner warning">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
                  {stats.expiringSoon} plan{stats.expiringSoon > 1 ? 's' : ''} expiring within 7 days
                </div>
              )}
              <div className="ap-card-title" style={{ marginTop: '1.5rem', marginBottom: '0.75rem' }}>Recent Signups</div>
              <div className="ap-recent-list">
                {users.slice(0, 5).map(u => (
                  <div key={u.id} className="ap-recent-item">
                    <InitialAvatar email={u.email} name={u.name || u.email} size={36} />
                    <div className="ap-recent-info">
                      <div className="ap-recent-name">{u.name || '—'}</div>
                      <div className="ap-recent-email">{u.email}</div>
                    </div>
                    <span className={`ap-badge ${u.is_blocked_by_admin ? 'blocked' : u.plan_active ? 'green' : 'gray'}`}>
                      {u.is_blocked_by_admin ? 'Blocked' : u.plan_active ? u.plan_name : 'Free'}
                    </span>
                    <div className="ap-recent-date">{formatRelativeTime(u.created_at)}</div>
                  </div>
                ))}
                {users.length === 0 && !loading && (<div className="ap-empty">No users yet.</div>)}
              </div>
            </div>
          )}

          {/* ── USERS ── */}
          {activeTab === 'users' && (
            <div className="ap-section">
              <div className="ap-section-title">All Users</div>
              <p style={{ fontSize: '.82rem', color: 'var(--text-secondary, #6b7280)', marginBottom: '1rem' }}>
                Manage individual and team users. Use custom +N/-N days buttons to extend or reduce plans by any number of days.
              </p>
              <div className="ap-toolbar">
                <div className="ap-search-wrap">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                  <input type="text" placeholder="Search name, email, phone..." value={searchTerm} onChange={e => setSearchTerm(e.target.value)} />
                </div>
                <select value={filterPlan} onChange={e => setFilterPlan(e.target.value as typeof filterPlan)} style={{ minWidth: '160px' }}>
                  <option value="all">All Users</option>
                  <option value="active">Active Plans</option>
                  <option value="none">Free Users</option>
                  <option value="blocked">Blocked</option>
                </select>
                <span className="ap-count-badge">{filteredIndividualUsers.length} found</span>
              </div>
              {loading ? (
                <div className="ap-loading"><span className="ap-spin lg" /></div>
              ) : (
                <div className="ap-table-wrap">
                  <table className="ap-table">
                    <thead>
                      <tr>
                        <th>#</th><th>Name</th><th>Email</th><th>Phone</th><th>Plan</th><th>Expiry</th><th>Days Left</th><th>Status</th><th>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filteredIndividualUsers.map((u, i) => {
                        const dl = daysLeft(u.plan_end_date);
                        return (
                          <tr key={u.id} className={u.is_blocked_by_admin ? 'row-blocked' : u.plan_active && dl <= 7 ? 'row-warning' : ''}>
                            <td className="td-num">{i + 1}</td>
                            <td className="td-name">
                              <div className="ap-user-cell">
                                <InitialAvatar email={u.email} name={u.name || u.email} size={28} />
                                <span>{u.name || '—'}</span>
                                {u.is_team_leader && <span className="ap-admin-badge" style={{ background: '#ea580c', color: '#fff' }}>TL</span>}
                              </div>
                            </td>
                            <td className="td-email">{u.email || '—'}</td>
                            <td className="td-phone">{u.phone || '—'}</td>
                            <td>
                              <span className={`ap-badge ${u.is_blocked_by_admin ? 'blocked' : u.plan_active ? 'green' : 'gray'}`}>
                                {u.is_blocked_by_admin ? 'Blocked' : u.plan_active ? u.plan_name : 'Free'}
                              </span>
                              {u.is_blocked_by_admin && u.plan_name && (<span className="ap-badge" style={{ background: '#fef3c7', color: '#92400e', marginLeft: '4px', fontSize: '.68rem' }}>Had: {u.plan_name}</span>)}
                              {u.queued_plan_name && (<span className="ap-badge" style={{ background: '#fef3c7', color: '#92400e', marginLeft: '4px', fontSize: '.68rem' }}>Queued: {u.queued_plan_name}</span>)}
                            </td>
                            <td className="td-date">{(u.plan_active || u.is_blocked_by_admin) && u.plan_end_date ? formatDate(u.plan_end_date) : '—'}</td>
                            <td>
                              {(u.plan_active || u.is_blocked_by_admin) && u.plan_end_date ? (
                                <span className={`ap-days ${dl <= 3 ? 'critical' : dl <= 7 ? 'warn' : 'ok'}`}>{dl}d</span>
                              ) : '—'}
                            </td>
                            <td>
                              {u.is_blocked_by_admin ? (<span className="ap-badge blocked">Blocked</span>) : u.plan_active ? (<span className="ap-badge green">Active</span>) : (<span className="ap-badge gray">Inactive</span>)}
                            </td>
                            <td>
                              <div className="ap-actions">
                                <button className="ap-btn-sm primary" onClick={() => {
                                  setSelectedUser(u);
                                  const initPlanName = u.plan_active ? (u.plan_name || '1 Month') : '1 Month';
                                  const initDays = PLAN_DAYS[initPlanName] || 30;
                                  setPlanForm({ planName: initPlanName, days: initDays, customDays: false, pricePaid: PLAN_PRICES[initPlanName] || 0 });
                                  setPlanType('individual');
                                  setShowPlanModal(true);
                                }} title={u.plan_active ? 'Renew / Change Plan' : 'Activate Plan'}>
                                  {u.plan_active ? 'Renew' : 'Activate'}
                                </button>
                                {u.plan_active && !u.is_blocked_by_admin && (
                                  <>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '2px' }}>
                                      <input type="number" min={1} max={365} value={customDaysInput[`ext_${u.id}`] || ''} onChange={e => setCustomDaysInput((prev: Record<string, string>) => ({ ...prev, [`ext_${u.id}`]: e.target.value }))} placeholder="days" style={{ width: '48px', padding: '2px 4px', fontSize: '.68rem', border: '1px solid #d1d5db', borderRadius: '4px', textAlign: 'center' }} />
                                      <button className="ap-btn-sm" style={{ background: '#059669', color: '#fff', padding: '3px 6px', fontSize: '.68rem', minWidth: 'auto' }} onClick={() => extendPlanDays(u.id, parseInt(customDaysInput[`ext_${u.id}`]) || 0)} title="Extend custom days" disabled={!customDaysInput[`ext_${u.id}`] || parseInt(customDaysInput[`ext_${u.id}`]) <= 0}>+</button>
                                      <button className="ap-btn-sm" style={{ background: '#d97706', color: '#fff', padding: '3px 6px', fontSize: '.68rem', minWidth: 'auto' }} onClick={() => decreasePlanDays(u.id, parseInt(customDaysInput[`ext_${u.id}`]) || 0)} title="Decrease custom days" disabled={!customDaysInput[`ext_${u.id}`] || parseInt(customDaysInput[`ext_${u.id}`]) <= 0}>-</button>
                                    </div>
                                    <button className="ap-btn-sm danger" onClick={() => deactivatePlan(u.id)} title="Revoke Plan">Revoke</button>
                                  </>
                                )}
                                {/* v17: Clear User Plans button */}
                                <button className="ap-btn-sm" style={{ background: '#7c3aed', color: '#fff' }} onClick={() => clearUserPlans(u.id)} title="Clear ALL plans (reset user completely)">Reset</button>
                                {u.is_blocked_by_admin ? (
                                  <button className="ap-btn-sm" style={{ background: '#10b981', color: '#fff' }} onClick={() => unblockUser(u.id)} title="Unblock user">Unblock</button>
                                ) : (
                                  <button className="ap-btn-sm" style={{ background: '#dc2626', color: '#fff' }} onClick={() => blockUser(u.id)} title="Block user">Block</button>
                                )}
                              </div>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                  {filteredIndividualUsers.length === 0 && (<div className="ap-empty">No users found matching your search.</div>)}
                </div>
              )}
            </div>
          )}

          {/* ── TEAM LEADERS ── */}
          {activeTab === 'team-leaders' && (
            <div className="ap-section">
              <div className="ap-section-title">Team Leaders</div>
              <p style={{ fontSize: '.82rem', color: 'var(--text-secondary, #6b7280)', marginBottom: '1rem' }}>
                Team leaders who have purchased team plans. Click the arrow to expand and view sub-members. Use &quot;Revoke&quot; to deactivate without deleting, &quot;Delete&quot; to fully remove.
              </p>
              {loading ? (
                <div className="ap-loading"><span className="ap-spin lg" /></div>
              ) : (
                <div className="ap-table-wrap">
                  <table className="ap-table">
                    <thead>
                      <tr><th></th><th>#</th><th>Leader Name</th><th>Leader Email</th><th>Team Code</th><th>Team Size</th><th>Members</th><th>Duration</th><th>Price Paid</th><th>Expiry</th><th>Days Left</th><th>Actions</th></tr>
                    </thead>
                    <tbody>
                      {teamLeaders.map((tl, i) => {
                        const dl = daysLeft(tl.plan_end_date);
                        const isExpanded = expandedTeams[tl.team_plan_id] || false;
                        const isRevealed = revealedCodes[tl.team_plan_id] || false;
                        const isCopied = copiedCode === tl.team_plan_id;
                        const members = teamMembers[tl.team_plan_id] || [];
                        return (
                          <React.Fragment key={tl.team_plan_id}>
                            <tr className={tl.plan_active && dl <= 7 ? 'row-warning' : ''}>
                              <td>
                                <button className="ap-btn-sm" style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '2px 6px', color: 'var(--text-primary, #1a202c)' }} onClick={() => toggleTeamExpand(tl.team_plan_id)} title={isExpanded ? 'Collapse' : 'Expand members'}>
                                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={{ transform: isExpanded ? 'rotate(90deg)' : 'rotate(0deg)', transition: 'transform 0.15s' }}><path d="m9 18 6-6-6-6"/></svg>
                                </button>
                              </td>
                              <td className="td-num">{i + 1}</td>
                              <td className="td-name">
                                <div className="ap-user-cell">
                                  <InitialAvatar email={tl.leader_email} name={tl.leader_name} size={28} />
                                  <span>{tl.leader_name}</span>
                                </div>
                              </td>
                              <td className="td-email">{tl.leader_email}</td>
                              <td>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                                  <span style={{ fontFamily: 'monospace', fontSize: '.82rem', letterSpacing: '1px' }}>{isRevealed ? tl.team_code : '••••••'}</span>
                                  <button className="ap-btn-sm" style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '2px', color: 'var(--text-secondary, #6b7280)' }} onClick={() => toggleRevealCode(tl.team_plan_id)} title={isRevealed ? 'Hide code' : 'Reveal code'}>{isRevealed ? <EyeClosedIcon /> : <EyeOpenIcon />}</button>
                                  <button className="ap-btn-sm" style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '2px', color: isCopied ? '#10b981' : 'var(--text-secondary, #6b7280)', fontSize: '.72rem', fontWeight: 600, minWidth: '42px' }} onClick={() => copyTeamCode(tl.team_code, tl.team_plan_id)} title="Copy team code">{isCopied ? 'Copied' : 'Copy'}</button>
                                </div>
                              </td>
                              <td>{tl.team_size}</td>
                              <td>{tl.member_count}/{tl.team_size}</td>
                              <td>{tl.months_duration}M</td>
                              <td>₹{tl.price_paid.toLocaleString('en-IN')}</td>
                              <td className="td-date">{formatDate(tl.plan_end_date)}</td>
                              <td><span className={`ap-days ${dl <= 3 ? 'critical' : dl <= 7 ? 'warn' : 'ok'}`}>{dl}d</span></td>
                              <td>
                                <div className="ap-actions">
                                  <button className="ap-btn-sm primary" onClick={() => {
                                    const user: UserRow = {
                                      id: tl.leader_user_id,
                                      name: tl.leader_name,
                                      email: tl.leader_email,
                                      phone: tl.leader_phone,
                                      plan_active: tl.plan_active,
                                      plan_name: `Team ${tl.months_duration}M`,
                                      plan_end_date: tl.plan_end_date,
                                      is_admin: false,
                                      is_team_leader: true,
                                      team_plan_id: tl.team_plan_id,
                                      created_at: tl.created_at,
                                    };
                                    setSelectedUser(user);
                                    setPlanType('team');
                                    setTeamSize(tl.team_size);
                                    setTeamMonths(tl.months_duration);
                                    const renewPlanName = `Team ${tl.months_duration}M`;
                                    setPlanForm({ planName: renewPlanName, days: tl.months_duration * 30, customDays: false, pricePaid: TEAM_PLAN_PRICES[tl.team_size]?.[tl.months_duration] || 0 });
                                    setShowPlanModal(true);
                                  }}>Renew</button>
                                  {/* v17: Revoke team plan (deactivate without deleting) */}
                                  <button className="ap-btn-sm danger" onClick={() => revokeTeamPlan(tl.team_plan_id)} title="Revoke team plan (deactivate, can renew later)">Revoke</button>
                                  {/* v17: Delete team plan (fully remove) */}
                                  <button className="ap-btn-sm" style={{ background: '#991b1b', color: '#fff' }} onClick={() => deleteTeamPlan(tl.team_plan_id)} title="Delete team plan entirely (cannot undo)">
                                    <TrashIcon /> Delete
                                  </button>
                                </div>
                              </td>
                            </tr>
                            {isExpanded && (
                              <tr key={`${tl.team_plan_id}-members`}>
                                <td colSpan={12} style={{ padding: 0, background: 'var(--bg-secondary, #f8fafc)' }}>
                                  <div style={{ padding: '0.5rem 1rem 1rem 2.5rem' }}>
                                    <div style={{ fontSize: '.78rem', fontWeight: 600, color: 'var(--text-secondary, #6b7280)', marginBottom: '0.5rem' }}>Team Members ({members.length})</div>
                                    {members.length === 0 ? (
                                      <div style={{ fontSize: '.78rem', color: 'var(--text-secondary, #6b7280)', padding: '0.5rem' }}>No members yet.</div>
                                    ) : (
                                      <table className="ap-table" style={{ fontSize: '.78rem' }}>
                                        <thead><tr><th>#</th><th>Name</th><th>Email</th><th>Phone</th><th>Plan Active</th><th>Status</th><th>Joined</th><th>Actions</th></tr></thead>
                                        <tbody>
                                          {members.map((m, mi) => {
                                            const isLeader = m.user_id === tl.leader_user_id;
                                            return (
                                            <tr key={m.user_id} className={isLeader ? 'row-leader' : m.is_blocked ? 'row-blocked' : ''}>
                                              <td className="td-num">{mi + 1}</td>
                                              <td className="td-name">
                                                {m.name}
                                                {isLeader && <span className="ap-badge" style={{ marginLeft: '6px', background: '#fef3c7', color: '#92400e', fontSize: '.65rem' }}>Leader</span>}
                                              </td>
                                              <td className="td-email">{m.email}</td>
                                              <td className="td-phone">{m.phone || '—'}</td>
                                              <td><span className={`ap-badge ${m.plan_active ? 'green' : 'gray'}`}>{m.plan_active ? 'Active' : 'Inactive'}</span></td>
                                              <td>{isLeader ? (<span className="ap-badge" style={{ background: '#eff6ff', color: '#1d4ed8' }}>Team Owner</span>) : m.is_blocked ? (<span className="ap-badge blocked">Blocked</span>) : (<span className="ap-badge green">Member</span>)}</td>
                                              <td className="td-date">{formatDate(m.joined_at)}</td>
                                              <td>
                                                {isLeader
                                                  ? <span style={{ fontSize: '.72rem', color: 'var(--text-secondary,#9ca3af)' }}>—</span>
                                                  : <button className="ap-btn-sm danger" onClick={() => removeTeamMember(tl.team_plan_id, m.user_id)} title="Remove member">Remove</button>
                                                }
                                              </td>
                                            </tr>
                                            );
                                          })}
                                        </tbody>
                                      </table>
                                    )}
                                  </div>
                                </td>
                              </tr>
                            )}
                          </React.Fragment>
                        );
                      })}
                    </tbody>
                  </table>
                  {teamLeaders.length === 0 && (<div className="ap-empty">No team leaders yet.</div>)}
                </div>
              )}
            </div>
          )}

          {/* ── PLANS ── */}
          {activeTab === 'plans' && (
            <div className="ap-section">
              <div className="ap-section-title">Plan Management</div>
              <div className="ap-card-title" style={{ marginBottom: '0.75rem' }}>Individual Plans</div>
              <div className="ap-plan-overview">
                {Object.entries(PLAN_PRICES).map(([name, price]) => {
                  const count = users.filter(u => u.plan_active && u.plan_name === name).length;
                  return (
                    <div key={name} className="ap-plan-card" style={{ '--plan-color': '#1a3a6b' } as React.CSSProperties}>
                      <div className="ap-plan-top"><span className="ap-plan-name">{name}</span><span className="ap-plan-price">₹{price}</span></div>
                      <div className="ap-plan-count">{count}</div>
                      <div className="ap-plan-label">active subscribers</div>
                      <div className="ap-plan-rev">Revenue: ₹{(count * price).toLocaleString('en-IN')}</div>
                    </div>
                  );
                })}
              </div>
              <div className="ap-card-title" style={{ marginTop: '1.5rem', marginBottom: '0.75rem' }}>Team Plans</div>
              <div className="ap-plan-overview">
                {Object.entries(TEAM_PLAN_PRICES).map(([sizeKey, monthsMap]) => (
                  Object.entries(monthsMap).map(([monthKey, price]) => {
                    const sz = parseInt(sizeKey);
                    const mo = parseInt(monthKey);
                    const count = teamLeaders.filter(tl => tl.team_size === sz && tl.months_duration === mo && tl.plan_active).length;
                    if (count === 0) return null;
                    return (
                      <div key={`team-${sz}-${mo}`} className="ap-plan-card" style={{ '--plan-color': '#ea580c' } as React.CSSProperties}>
                        <div className="ap-plan-top"><span className="ap-plan-name">Team {sz}×{mo}M</span><span className="ap-plan-price">₹{price.toLocaleString('en-IN')}</span></div>
                        <div className="ap-plan-count">{count}</div>
                        <div className="ap-plan-label">active teams</div>
                        <div className="ap-plan-rev">Revenue: ₹{(count * price).toLocaleString('en-IN')}</div>
                      </div>
                    );
                  })
                ))}
                {teamLeaders.filter(tl => tl.plan_active).length === 0 && (
                  <div className="ap-empty">No active team plans.</div>
                )}
              </div>
            </div>
          )}

          {/* ── ANALYTICS ── */}
          {activeTab === 'analytics' && (() => {
            const totalMembers = teamLeaders.reduce((s, tl) => s + tl.member_count, 0);
            const totalSeats   = teamLeaders.reduce((s, tl) => s + tl.team_size, 0);
            const teamUtil     = totalSeats > 0 ? Math.round(totalMembers / totalSeats * 100) : 0;
            const avgRev       = users.length > 0 ? Math.round(totalRevenue / users.length) : 0;
            const avgIndivRev  = individualActiveUsers.length > 0 ? Math.round(stats.individualRevenue / individualActiveUsers.length) : 0;
            const avgTeamRev   = teamLeaders.filter(tl => tl.plan_active).length > 0
              ? Math.round(stats.teamRevenue / teamLeaders.filter(tl => tl.plan_active).length) : 0;

            // Plan-wise individual count
            const planCounts: Record<string, number> = {};
            individualActiveUsers.forEach(u => {
              const k = u.plan_name || 'Unknown';
              planCounts[k] = (planCounts[k] || 0) + 1;
            });
            // Team plan size distribution
            const teamSizeDist: Record<string, number> = {};
            teamLeaders.filter(tl => tl.plan_active).forEach(tl => {
              const k = `${tl.team_size} seats`;
              teamSizeDist[k] = (teamSizeDist[k] || 0) + 1;
            });

            return (
              <div className="ap-section">
                <div className="ap-section-title">Analytics</div>

                {/* Revenue cards */}
                <div className="ap-card-title" style={{ marginBottom: '0.75rem' }}>Revenue Breakdown</div>
                <div className="ap-stats-grid" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))', marginBottom: '1.5rem' }}>
                  {[
                    { label: 'Total Revenue', value: `₹${totalRevenue.toLocaleString('en-IN')}`, sub: 'All plans combined', color: '#059669' },
                    { label: 'Individual Revenue', value: `₹${stats.individualRevenue.toLocaleString('en-IN')}`, sub: `${individualActiveUsers.length} active users`, color: '#8b5cf6' },
                    { label: 'Team Revenue', value: `₹${stats.teamRevenue.toLocaleString('en-IN')}`, sub: `${teamLeaders.filter(tl=>tl.plan_active).length} active teams`, color: '#ea580c' },
                    { label: 'Avg / Total User', value: `₹${avgRev.toLocaleString('en-IN')}`, sub: `across ${users.length} users`, color: '#0ea5e9' },
                    { label: 'Avg / Indiv Plan', value: `₹${avgIndivRev.toLocaleString('en-IN')}`, sub: 'per individual plan user', color: '#7c3aed' },
                    { label: 'Avg / Team Plan', value: `₹${avgTeamRev.toLocaleString('en-IN')}`, sub: 'per active team', color: '#c2410c' },
                  ].map((m, mi) => (
                    <div key={mi} className="ap-stat-card" style={{ '--accent': m.color } as React.CSSProperties}>
                      <div className="ap-stat-value" style={{ fontSize: '1.05rem' }}>{m.value}</div>
                      <div className="ap-stat-label">{m.label}</div>
                      <div style={{ fontSize: '.68rem', color: 'var(--text-secondary, #9ca3af)', marginTop: '2px' }}>{m.sub}</div>
                    </div>
                  ))}
                </div>

                {/* Charts row */}
                <div className="ap-charts-row">
                  <div className="ap-chart-card"><div className="ap-card-title">Registrations (6 months)</div><BarChart data={monthlyData} /></div>
                  <div className="ap-chart-card"><div className="ap-card-title">Plan Distribution</div><DonutChart segments={planDist} /></div>
                </div>

                {/* Revenue split visual bar */}
                <div className="ap-card-title" style={{ marginTop: '1.5rem', marginBottom: '0.75rem' }}>Revenue Mix</div>
                {totalRevenue > 0 && (
                  <div style={{ marginBottom: '1.5rem' }}>
                    <div style={{ display: 'flex', height: '28px', borderRadius: '8px', overflow: 'hidden', width: '100%', gap: '2px' }}>
                      <div style={{ width: `${Math.round(stats.individualRevenue / totalRevenue * 100)}%`, background: '#8b5cf6', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '.7rem', color: '#fff', fontWeight: 700, minWidth: '30px' }}>
                        {Math.round(stats.individualRevenue / totalRevenue * 100)}%
                      </div>
                      <div style={{ flex: 1, background: '#ea580c', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '.7rem', color: '#fff', fontWeight: 700, minWidth: '30px' }}>
                        {Math.round(stats.teamRevenue / totalRevenue * 100)}%
                      </div>
                    </div>
                    <div style={{ display: 'flex', gap: '1.5rem', marginTop: '0.4rem', fontSize: '.75rem', color: 'var(--text-secondary, #6b7280)' }}>
                      <span><span style={{ display: 'inline-block', width: '10px', height: '10px', borderRadius: '2px', background: '#8b5cf6', marginRight: '4px' }}/>Individual Plans</span>
                      <span><span style={{ display: 'inline-block', width: '10px', height: '10px', borderRadius: '2px', background: '#ea580c', marginRight: '4px' }}/>Team Plans</span>
                    </div>
                  </div>
                )}

                {/* User metrics */}
                <div className="ap-card-title" style={{ marginBottom: '0.75rem' }}>User & Plan Metrics</div>
                <div className="ap-stats-grid" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))', marginBottom: '1.5rem' }}>
                  {[
                    { label: 'Conversion Rate', value: `${stats.conversionRate}%`, color: '#059669' },
                    { label: 'Team Utilization', value: `${teamUtil}%`, color: '#ea580c' },
                    { label: 'Blocked Rate', value: `${users.length > 0 ? Math.round(blockedUsers.length / users.length * 100) : 0}%`, color: '#dc2626' },
                    { label: 'Active Teams', value: `${teamLeaders.filter(tl => tl.plan_active).length}`, color: '#0ea5e9' },
                    { label: 'Team Members', value: `${totalMembers}/${totalSeats}`, color: '#7c3aed' },
                    { label: 'Indiv Active', value: `${individualActiveUsers.length}`, color: '#8b5cf6' },
                  ].map((m, mi) => (
                    <div key={mi} className="ap-stat-card" style={{ '--accent': m.color } as React.CSSProperties}>
                      <div className="ap-stat-value" style={{ fontSize: '1.1rem' }}>{m.value}</div>
                      <div className="ap-stat-label">{m.label}</div>
                    </div>
                  ))}
                </div>

                {/* Plan-wise breakdown tables */}
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '1rem' }}>
                  {/* Individual plan breakdown */}
                  <div style={{ background: 'var(--bg-card, #fff)', borderRadius: '12px', border: '1px solid var(--border-color, #e5e7eb)', padding: '1rem' }}>
                    <div style={{ fontSize: '.82rem', fontWeight: 700, marginBottom: '0.75rem', color: 'var(--text-primary, #1a1a2e)' }}>Individual Plan Breakdown</div>
                    {Object.keys(planCounts).length === 0 ? (
                      <div style={{ fontSize: '.78rem', color: 'var(--text-secondary, #9ca3af)' }}>No individual plans active</div>
                    ) : (
                      <table style={{ width: '100%', fontSize: '.78rem', borderCollapse: 'collapse' }}>
                        <thead><tr style={{ borderBottom: '1px solid var(--border-color, #e5e7eb)' }}><th style={{ textAlign: 'left', padding: '4px 0', fontWeight: 600 }}>Plan</th><th style={{ textAlign: 'right', padding: '4px 0', fontWeight: 600 }}>Users</th><th style={{ textAlign: 'right', padding: '4px 0', fontWeight: 600 }}>Revenue</th></tr></thead>
                        <tbody>
                          {Object.entries(planCounts).sort((a, b) => b[1] - a[1]).map(([planName, count]) => (
                            <tr key={planName} style={{ borderBottom: '1px solid var(--border-color, #e5e7eb)' }}>
                              <td style={{ padding: '5px 0' }}>{planName}</td>
                              <td style={{ textAlign: 'right', padding: '5px 0' }}>{count}</td>
                              <td style={{ textAlign: 'right', padding: '5px 0', color: '#8b5cf6', fontWeight: 600 }}>₹{((PLAN_PRICES[planName] || 0) * count).toLocaleString('en-IN')}</td>
                            </tr>
                          ))}
                          <tr style={{ fontWeight: 700 }}>
                            <td style={{ padding: '6px 0' }}>Total</td>
                            <td style={{ textAlign: 'right', padding: '6px 0' }}>{individualActiveUsers.length}</td>
                            <td style={{ textAlign: 'right', padding: '6px 0', color: '#059669' }}>₹{stats.individualRevenue.toLocaleString('en-IN')}</td>
                          </tr>
                        </tbody>
                      </table>
                    )}
                  </div>

                  {/* Team plan breakdown */}
                  <div style={{ background: 'var(--bg-card, #fff)', borderRadius: '12px', border: '1px solid var(--border-color, #e5e7eb)', padding: '1rem' }}>
                    <div style={{ fontSize: '.82rem', fontWeight: 700, marginBottom: '0.75rem', color: 'var(--text-primary, #1a1a2e)' }}>Team Plan Breakdown</div>
                    {teamLeaders.filter(tl => tl.plan_active).length === 0 ? (
                      <div style={{ fontSize: '.78rem', color: 'var(--text-secondary, #9ca3af)' }}>No active team plans</div>
                    ) : (
                      <table style={{ width: '100%', fontSize: '.78rem', borderCollapse: 'collapse' }}>
                        <thead><tr style={{ borderBottom: '1px solid var(--border-color, #e5e7eb)' }}><th style={{ textAlign: 'left', padding: '4px 0', fontWeight: 600 }}>Team Leader</th><th style={{ textAlign: 'right', padding: '4px 0', fontWeight: 600 }}>Size</th><th style={{ textAlign: 'right', padding: '4px 0', fontWeight: 600 }}>Members</th><th style={{ textAlign: 'right', padding: '4px 0', fontWeight: 600 }}>Paid</th></tr></thead>
                        <tbody>
                          {teamLeaders.filter(tl => tl.plan_active).map(tl => (
                            <tr key={tl.team_plan_id} style={{ borderBottom: '1px solid var(--border-color, #e5e7eb)' }}>
                              <td style={{ padding: '5px 0', maxWidth: '100px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{tl.leader_name}</td>
                              <td style={{ textAlign: 'right', padding: '5px 0' }}>{tl.team_size}</td>
                              <td style={{ textAlign: 'right', padding: '5px 0' }}>{tl.member_count}</td>
                              <td style={{ textAlign: 'right', padding: '5px 0', color: '#ea580c', fontWeight: 600 }}>₹{(tl.price_paid || 0).toLocaleString('en-IN')}</td>
                            </tr>
                          ))}
                          <tr style={{ fontWeight: 700 }}>
                            <td style={{ padding: '6px 0' }}>Total</td>
                            <td style={{ textAlign: 'right', padding: '6px 0' }}>{totalSeats}</td>
                            <td style={{ textAlign: 'right', padding: '6px 0' }}>{totalMembers}</td>
                            <td style={{ textAlign: 'right', padding: '6px 0', color: '#059669' }}>₹{stats.teamRevenue.toLocaleString('en-IN')}</td>
                          </tr>
                        </tbody>
                      </table>
                    )}
                  </div>
                </div>
              </div>
            );
          })()}

          {/* ── APPEALS ── */}
          {activeTab === 'appeals' && (
            <div className="ap-section">
              <div className="ap-section-title">Admin Appeals</div>
              <p style={{ fontSize: '.82rem', color: 'var(--text-secondary, #6b7280)', marginBottom: '1rem' }}>
                Appeals from blocked users requesting unblock.
              </p>
              {appeals.length === 0 ? (
                <div className="ap-empty">No appeals yet.</div>
              ) : (
                <div className="ap-table-wrap">
                  <table className="ap-table">
                    <thead><tr><th>#</th><th>User</th><th>Email</th><th>Appeal</th><th>Submitted</th><th>Status</th><th>Actions</th></tr></thead>
                    <tbody>
                      {appeals.map((a, i) => (
                        <tr key={a.id} className={a.is_resolved ? 'row-resolved' : ''}>
                          <td className="td-num">{i + 1}</td>
                          <td className="td-name">
                            <div className="ap-user-cell">
                              <InitialAvatar email={a.user_email} name={a.user_name} size={28} />
                              <span>{a.user_name}</span>
                            </div>
                          </td>
                          <td className="td-email">{a.user_email}</td>
                          <td style={{ maxWidth: '300px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{a.appeal_text}</td>
                          <td className="td-date">{formatRelativeTime(a.submitted_at)}</td>
                          <td>
                            {a.is_resolved ? (
                              <span className="ap-badge" style={{ background: a.resolved_by_admin ? '#dcfce7' : '#fef3c7', color: a.resolved_by_admin ? '#16a34a' : '#92400e' }}>
                                {a.resolved_by_admin ? 'Unblocked' : 'Dismissed'}
                              </span>
                            ) : (
                              <span className="ap-badge" style={{ background: '#fef2f2', color: '#dc2626' }}>Pending</span>
                            )}
                          </td>
                          <td>
                            {!a.is_resolved && (
                              <div className="ap-actions">
                                <button className="ap-btn-sm" style={{ background: '#10b981', color: '#fff' }} onClick={() => resolveAppeal(a.id, true)}>Unblock</button>
                                <button className="ap-btn-sm" style={{ background: '#6b7280', color: '#fff' }} onClick={() => resolveAppeal(a.id, false)}>Dismiss</button>
                              </div>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}

          {/* ── REVIEWS ── */}
          {activeTab === 'reviews' && (
            <AdminReviewsSection />
          )}

          {/* ── AUDIT LOG ── */}
          {activeTab === 'audit-log' && (
            <AdminAuditLogSection />
          )}

          {/* ── SETTINGS ── */}
          {activeTab === 'settings' && (
            <div className="ap-section">
              <div className="ap-section-title">Settings</div>

              {/* Admin Panel Info */}
              <div className="ap-settings-section">
                <div className="ap-settings-section-title">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
                  Admin Panel Info
                </div>
                <div style={{ background: 'var(--bg-secondary, #f9fafb)', padding: '1rem', borderRadius: '12px', border: '1px solid var(--border-color, #e5e7eb)' }}>
                  <div style={{ display: 'grid', gap: '0.5rem', fontSize: '.85rem' }}>
                    <div><strong>Version:</strong> v22 (Notification Rebuild)</div>
                    <div><strong>Admin Email:</strong> {ADMIN_EMAIL}</div>
                    <div><strong>Active Users:</strong> {stats.active}</div>
                    <div><strong>Total Revenue:</strong> ₹{totalRevenue.toLocaleString('en-IN')}</div>
                    <div><strong>Pending Appeals:</strong> {stats.pendingAppeals}</div>
                  </div>
                </div>
              </div>

              {/* ── Notification Section ── */}
              <div className="ap-settings-section">
                <div className="ap-settings-section-title">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>
                  Send Notification
                </div>
                <div style={{ background: 'var(--bg-card, #fff)', padding: '1.25rem', borderRadius: '14px', border: '1px solid var(--border-color, #e5e7eb)' }}>
                  <div style={{ marginBottom: '1rem' }}>
                    <label style={{ display: 'block', fontSize: '.75rem', fontWeight: 700, color: '#475569', textTransform: 'uppercase', letterSpacing: '.5px', marginBottom: '6px' }}>Message</label>
                    <textarea
                      value={adminNotifMessage}
                      onChange={e => setAdminNotifMessage(e.target.value)}
                      placeholder="Type your notification message here..."
                      rows={4}
                      style={{
                        width: '100%', padding: '0.625rem 0.875rem', background: '#ffffff',
                        border: '1.5px solid #e2e8f0', borderRadius: '10px', color: '#1a202c',
                        fontSize: '0.875rem', outline: 'none', resize: 'vertical', fontFamily: 'inherit',
                        boxSizing: 'border-box',
                      }}
                    />
                  </div>
                  <div style={{ marginBottom: '1rem' }}>
                    <label style={{ display: 'block', fontSize: '.75rem', fontWeight: 700, color: '#475569', textTransform: 'uppercase', letterSpacing: '.5px', marginBottom: '8px' }}>Send To</label>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                      {([
                        { value: 'guest', label: 'All Guests', desc: 'Unauthenticated visitors only', icon: '👤' },
                        { value: 'free_users', label: 'All Free Users', desc: 'Logged-in users with no active plan', icon: '🆓' },
                        { value: 'paid_users', label: 'All Paid Users', desc: 'Active Individual & Team plan holders', icon: '💎' },
                        { value: 'all', label: 'Broadcast to ALL Users', desc: 'Every registered user + guests', icon: '📢' },
                      ] as const).map(opt => (
                        <label
                          key={opt.value}
                          style={{
                            display: 'flex', alignItems: 'center', gap: '10px', padding: '10px 12px',
                            background: adminNotifTarget === opt.value ? 'linear-gradient(135deg, #eff6ff 0%, #f0fdf4 100%)' : '#f8fafc',
                            border: `1.5px solid ${adminNotifTarget === opt.value ? '#1a3a6b' : '#e2e8f0'}`,
                            borderRadius: '10px', cursor: 'pointer', transition: 'all 0.15s ease',
                          }}
                        >
                          <input
                            type="radio"
                            name="notif-target"
                            value={opt.value}
                            checked={adminNotifTarget === opt.value}
                            onChange={() => setAdminNotifTarget(opt.value)}
                            style={{ accentColor: '#1a3a6b' }}
                          />
                          <span style={{ fontSize: '1.1rem' }}>{opt.icon}</span>
                          <div>
                            <div style={{ fontSize: '.85rem', fontWeight: 600, color: '#1a202c' }}>{opt.label}</div>
                            <div style={{ fontSize: '.72rem', color: '#64748b' }}>{opt.desc}</div>
                          </div>
                        </label>
                      ))}
                    </div>
                  </div>
                  {adminNotifResult && (
                    <div style={{
                      padding: '8px 12px', borderRadius: '8px', marginBottom: '0.75rem',
                      background: adminNotifResult.type === 'success' ? '#dcfce7' : '#fef2f2',
                      color: adminNotifResult.type === 'success' ? '#16a34a' : '#dc2626',
                      fontSize: '.82rem', fontWeight: 600,
                    }}>
                      {adminNotifResult.text}
                    </div>
                  )}
                  <button
                    onClick={handleAdminSendNotification}
                    disabled={adminNotifSending || !adminNotifMessage.trim()}
                    style={{
                      width: '100%', padding: '0.7rem',
                      background: adminNotifSending ? '#94a3b8' : 'linear-gradient(135deg, #1a3a6b 0%, #2d5a9e 100%)',
                      border: 'none', borderRadius: '10px', color: '#fff', cursor: adminNotifSending ? 'not-allowed' : 'pointer',
                      fontSize: '0.875rem', fontWeight: 700, transition: 'all 0.15s ease',
                      display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px',
                      boxShadow: adminNotifSending ? 'none' : '0 2px 8px rgba(26, 58, 107, 0.2)',
                    }}
                  >
                    {adminNotifSending ? (
                      <><span className="ap-spin" style={{ width: 16, height: 16, borderWidth: 2 }} />Sending...</>
                    ) : (
                      <>
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
                        Send Notification
                      </>
                    )}
                  </button>
                </div>
              </div>

              {/* Maintenance Mode */}
              <div className="ap-settings-section">
                <div className="ap-settings-section-title">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/></svg>
                  Maintenance Mode
                </div>
                <div className={`ap-maintenance-toggle ${maintenanceMode ? 'active' : ''}`}>
                  <div className="ap-maintenance-toggle-icon">
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 9v4l3 3"/><circle cx="12" cy="12" r="10"/></svg>
                  </div>
                  <div className="ap-maintenance-toggle-info">
                    <div className="ap-maintenance-toggle-label">
                      {maintenanceMode ? 'Maintenance Mode Active' : 'Site is Online'}
                    </div>
                    <div className="ap-maintenance-toggle-desc">
                      {maintenanceMode
                        ? 'Users see a maintenance page. Only admins can access the site.'
                        : 'Toggle to put the site in maintenance mode.'}
                    </div>
                  </div>
                  <label className="ap-toggle-switch">
                    <input
                      type="checkbox"
                      checked={maintenanceMode}
                      disabled={maintenanceLoading}
                      onChange={(e) => toggleMaintenance(e.target.checked)}
                    />
                    <span className="ap-toggle-slider" />
                  </label>
                </div>
              </div>

            </div>
          )}
        </main>
      </div>

      {/* ── Plan Modal ── */}
      {showPlanModal && selectedUser && (
        <div className="ap-modal-overlay" onClick={() => setShowPlanModal(false)}>
          <div className="ap-plan-modal" onClick={e => e.stopPropagation()}>
            <div className="ap-plan-modal-header">
              <div>
                <h3>{selectedUser.plan_active ? 'Renew / Change Plan' : 'Activate Plan'}</h3>
                <p className="ap-plan-modal-subtitle">Select plan type and duration for this user</p>
              </div>
              <button className="ap-plan-modal-close" onClick={() => setShowPlanModal(false)}>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
              </button>
            </div>

            {/* User Info Card */}
            <div className="ap-plan-modal-user-card">
              <InitialAvatar email={selectedUser.email} name={selectedUser.name || selectedUser.email} size={44} />
              <div className="ap-plan-modal-user-info">
                <div className="ap-plan-modal-user-name">{selectedUser.name || '—'}</div>
                <div className="ap-plan-modal-user-email">{selectedUser.email}</div>
              </div>
              {selectedUser.plan_active && (
                <span className="ap-badge green" style={{ marginLeft: 'auto' }}>Active</span>
              )}
            </div>

            {/* Plan Type Toggle */}
            <div className="ap-plan-type-toggle">
              <button className={`ap-plan-type-btn ${planType === 'individual' ? 'active' : ''}`} onClick={() => setPlanType('individual')}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{marginRight: 6}}><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                Individual
              </button>
              <button className={`ap-plan-type-btn ${planType === 'team' ? 'active' : ''}`} onClick={() => setPlanType('team')}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{marginRight: 6}}><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
                Team
              </button>
            </div>

            <div className="ap-plan-modal-form">
              {planType === 'individual' ? (
                <>
                  <div className="ap-plan-modal-field">
                    <label className="ap-plan-modal-label">Plan Duration</label>
                    <select className="ap-plan-modal-select" value={planForm.planName} onChange={e => {
                      const val = e.target.value;
                      const isCustom = val === 'custom';
                      setPlanForm({
                        ...planForm,
                        planName: val,
                        customDays: isCustom,
                        pricePaid: isCustom ? planForm.pricePaid : (PLAN_PRICES[val] || 0),
                        days: isCustom ? planForm.days : (PLAN_DAYS[val] || 30),
                      });
                    }}>
                      {Object.keys(PLAN_PRICES).map(name => (<option key={name} value={name}>{name} — ₹{PLAN_PRICES[name]}</option>))}
                      <option value="custom">Custom Days</option>
                    </select>
                  </div>
                  {planForm.customDays && (
                    <div className="ap-plan-modal-field">
                      <label className="ap-plan-modal-label">Custom Days</label>
                      <input type="number" className="ap-plan-modal-input" min={1} max={3650} value={planForm.days} onChange={e => setPlanForm({ ...planForm, days: parseInt(e.target.value) || 30 })} />
                    </div>
                  )}
                  {!planForm.customDays && (
                    <div className="ap-plan-modal-price-preview">
                      <span className="ap-plan-modal-price-label">Price</span>
                      <span className="ap-plan-modal-price-value">₹{PLAN_PRICES[planForm.planName] || 0}</span>
                    </div>
                  )}
                </>
              ) : (
                <>
                  <div className="ap-plan-modal-field-row">
                    <div className="ap-plan-modal-field" style={{ flex: 1 }}>
                      <label className="ap-plan-modal-label">Team Size</label>
                      <select className="ap-plan-modal-select" value={teamSize} onChange={e => setTeamSize(parseInt(e.target.value))}>
                        {Object.keys(TEAM_PLAN_PRICES).map(s => (<option key={s} value={s}>{s} Users</option>))}
                      </select>
                    </div>
                    <div className="ap-plan-modal-field" style={{ flex: 1 }}>
                      <label className="ap-plan-modal-label">Duration</label>
                      <select className="ap-plan-modal-select" value={teamMonths} onChange={e => setTeamMonths(parseInt(e.target.value))}>
                        {Object.keys(TEAM_PLAN_PRICES[teamSize] || {}).map(m => (<option key={m} value={m}>{m} Month{parseInt(m) > 1 ? 's' : ''} — ₹{(TEAM_PLAN_PRICES[teamSize]?.[parseInt(m)] ?? 0).toLocaleString('en-IN')}</option>))}
                      </select>
                    </div>
                  </div>
                  <div className="ap-plan-modal-price-preview">
                    <span className="ap-plan-modal-price-label">Team Price</span>
                    <span className="ap-plan-modal-price-value">₹{(TEAM_PLAN_PRICES[teamSize]?.[teamMonths] ?? 0).toLocaleString('en-IN')}</span>
                  </div>
                </>
              )}
            </div>

            <div className="ap-plan-modal-actions">
              <button className="ap-plan-modal-cancel" onClick={() => setShowPlanModal(false)}>Cancel</button>
              <button className="ap-plan-modal-confirm" disabled={saving} onClick={activatePlan}>
                {saving ? (<><span className="ap-spin" style={{marginRight: 8}} />Processing...</>) : planType === 'team' ? 'Grant Team Plan' : 'Activate Plan'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Team Code Modal ── */}
      {showTeamCodeModal && generatedTeamCode && (
        <div className="ap-modal-overlay" onClick={() => setShowTeamCodeModal(false)}>
          <div className="ap-modal" onClick={e => e.stopPropagation()}>
            <div className="ap-modal-header">
              <h3>Team Plan Created!</h3>
              <button className="ap-close-modal" onClick={() => setShowTeamCodeModal(false)}>×</button>
            </div>
            <div className="ap-modal-body" style={{ textAlign: 'center' }}>
              <p style={{ fontSize: '.85rem', color: 'var(--text-secondary, #6b7280)', marginBottom: '1rem' }}>
                Share this team code with members so they can join:
              </p>
              <div style={{ background: '#f9fafb', padding: '1rem', borderRadius: '12px', fontFamily: 'monospace', fontSize: '1.5rem', letterSpacing: '3px', fontWeight: 700, color: '#ea580c' }}>
                {generatedTeamCode}
              </div>
              <button className="ap-btn-sm primary" style={{ marginTop: '1rem' }} onClick={() => {
                navigator.clipboard.writeText(generatedTeamCode).catch(() => {});
              }}>Copy Code</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
