import { useState, useEffect, useCallback, useRef } from 'react';
import { useAuth } from '../context/AuthContext';
import { useExam } from '../context/ExamContext';
import { isInstalled } from '../registerSW';
import { supabase } from '../lib/supabase';

interface NotificationItem {
  id: string;
  title?: string;
  message: string;
  is_read: boolean;
  target_group: string | null;
  created_at: string;
  notification_type?: string;
  font_guide?: boolean;
}

/**
 * NotificationBell — Bell icon with unread badge pointing to full page notifications screen (Screen 6)
 * v37: Fixed admin notification badge not clearing after reading font notification
 * v15: Added local browser notification fallback for new in-app notifications (S20)
 * v17: Added Supabase realtime subscription for instant notification detection + plan grant popup
 */
export function NotificationBell() {
  const { user, isLoggedIn, refreshProfile } = useAuth();
  const { navigateTo } = useExam();
  const [unreadCount, setUnreadCount] = useState(0);
  const prevUnreadRef = useRef(0);
  const [planGrantPopup, setPlanGrantPopup] = useState<NotificationItem | null>(null);

  // v17: Show plan grant popup when a new plan/team_plan notification arrives
  const showPlanGrantPopup = useCallback((notif: NotificationItem) => {
    if (notif.notification_type === 'individual_plan' || notif.notification_type === 'team_plan' || notif.notification_type === 'plan_grant' || notif.notification_type === 'extension' || notif.notification_type === 'plan_activation') {
      setPlanGrantPopup(notif);
      // Auto-dismiss after 15 seconds
      setTimeout(() => setPlanGrantPopup(null), 15000);
      // Also refresh profile so plan status updates immediately
      refreshProfile().catch(() => {});
    }
  }, [refreshProfile]);

  // v15: Show a local browser notification when new in-app notifications arrive
  const showLocalBrowserNotification = useCallback((newItems: NotificationItem[]) => {
    if (!('Notification' in window) || Notification.permission !== 'granted') return;

    // Only show for the latest unread notification
    const latestUnread = newItems.find(n => !n.is_read);
    if (!latestUnread) return;

    // Don't show for font guide notifications (too noisy)
    if (latestUnread.font_guide || latestUnread.notification_type === 'font_guide' || latestUnread.notification_type === 'font_install') return;

    try {
      if (navigator.serviceWorker?.controller) {
        // Use service worker notification (works even when tab is in background)
        navigator.serviceWorker.controller.postMessage({
          type: 'SHOW_NOTIFICATION',
          payload: {
            title: latestUnread.title || 'RHC Steno',
            body: latestUnread.message?.substring(0, 150) || 'You have a new notification',
            tag: `rhc-notif-${latestUnread.id}`,
            url: '/',
          },
        });
      } else {
        // Fallback to direct Notification API
        new Notification(latestUnread.title || 'RHC Steno', {
          body: latestUnread.message?.substring(0, 150) || 'You have a new notification',
          icon: '/icons/icon-192.png',
          tag: `rhc-notif-${latestUnread.id}`,
        });
      }
    } catch {
      // Non-critical — browser notifications are optional
    }
  }, []);

  // Load guest notifications from sessionStorage — only font notification for new/guest users
  const loadGuestNotificationsCount = useCallback(() => {
    try {
      // New/guest users should only see the font notification
      const fontRead = localStorage.getItem("krutidev_font_read") === "true";
      let count = fontRead ? 0 : 1;
      // Add PWA install notification count if applicable
      if (!isInstalled() && (window as { __pwaInstallReady?: boolean }).__pwaInstallReady && localStorage.getItem('pwa_install_dismissed') !== 'true') {
        if (localStorage.getItem('pwa_install_read') !== 'true') count += 1;
      }
      setUnreadCount(count);
    } catch {
      setUnreadCount(0);
    }
  }, []);

  // Fetch notifications from DB to count unread
  const fetchUnreadCount = useCallback(async () => {
    if (!isLoggedIn || !user?.id) {
      loadGuestNotificationsCount();
      return;
    }

    try {
      const { data, error } = await supabase.rpc('get_user_notifications', {
        p_user_id: user.id,
      });
      if (!error && data) {
        const items = (data as NotificationItem[]).filter(n => {
          // Exclude dismissed notifications (both client-side and via notification_dismissals table)
          if (n.target_group && localStorage.getItem(`notif_read_${n.id}`)) {
            return false;
          }
          // Check if this notification was permanently dismissed
          if (localStorage.getItem(`notif_dismissed_${n.id}`)) {
            return false;
          }
          return true;
        });

        // Count truly unread notifications from DB
        let unread = items.filter(n => !n.is_read).length;

        // Sync DB font notification read state with localStorage so badge stays accurate
        const dbFontNotif = items.find(n => n.font_guide || n.notification_type === 'font_guide');
        if (dbFontNotif) {
          if (dbFontNotif.is_read) {
            // DB font notification is read — sync localStorage so bell stays accurate
            localStorage.setItem('krutidev_font_read', 'true');
          } else {
            // DB font notification is NOT read — make sure localStorage reflects this too
            localStorage.removeItem('krutidev_font_read');
          }
        }
        // Always account for the static font notification if no DB-based font_guide exists
        const hasFontNotif = !!dbFontNotif;
        if (!hasFontNotif) {
          const fontRead = localStorage.getItem('krutidev_font_read') === 'true';
          if (!fontRead) unread += 1;
        }
        // Add PWA install notification count if applicable
        if (!isInstalled() && (window as { __pwaInstallReady?: boolean }).__pwaInstallReady && localStorage.getItem('pwa_install_dismissed') !== 'true') {
          if (localStorage.getItem('pwa_install_read') !== 'true') unread += 1;
        }
        setUnreadCount(unread);

        // v15: Show local browser notification when new unread notifications appear
        if (unread > prevUnreadRef.current && prevUnreadRef.current >= 0) {
          showLocalBrowserNotification(items);
          // v17: Show plan grant popup for plan-related notifications
          const newNotifs = items.filter(n => !n.is_read);
          const planNotif = newNotifs.find(n =>
            n.notification_type === 'individual_plan' ||
            n.notification_type === 'team_plan' ||
            n.notification_type === 'plan_grant' ||
            n.notification_type === 'extension' ||
            n.notification_type === 'plan_activation'
          );
          if (planNotif) {
            showPlanGrantPopup(planNotif);
          }
        }
        prevUnreadRef.current = unread;
      }
    } catch (err) {
      console.error('[NotificationBell] Fetch unread error:', err);
    }
  }, [isLoggedIn, user, loadGuestNotificationsCount, showLocalBrowserNotification, showPlanGrantPopup]);

  // Listen for local storage changes or custom events from Screen 6
  useEffect(() => {
    const handleStorageChange = () => fetchUnreadCount();
    const handleNotificationsRead = () => {
      // Small delay to allow localStorage to be updated by Screen6
      setTimeout(() => fetchUnreadCount(), 100);
    };
    window.addEventListener('storage', handleStorageChange);
    window.addEventListener('notifications_read', handleNotificationsRead);
    return () => {
      window.removeEventListener('storage', handleStorageChange);
      window.removeEventListener('notifications_read', handleNotificationsRead);
    };
  }, [fetchUnreadCount]);

  // Fetch on mount + periodically
  useEffect(() => {
    fetchUnreadCount();
    const interval = setInterval(fetchUnreadCount, 30000); // Check every 30s
    return () => clearInterval(interval);
  }, [fetchUnreadCount]);

  // v17/v18: Supabase realtime subscription for instant notification detection
  // v18 FIX: Listen to ALL notification inserts (not just user-specific) because
  // broadcast notifications have user_id=NULL and Supabase Realtime does NOT support
  // filter syntax like "user_id=is.null". We listen to all inserts and then
  // fetchUnreadCount() which properly filters via the RPC function.
  useEffect(() => {
    if (!isLoggedIn || !user?.id) return;

    const channel = supabase
      .channel('notification-bell-realtime')
      .on(
        'postgres_changes',
        {
          event: 'INSERT',
          schema: 'public',
          table: 'notifications',
        },
        (payload) => {
          // New notification inserted — check if it's relevant to this user
          const newRow = payload.new as { user_id?: string; target_group?: string } | null;
          if (!newRow) return;
          // Personal notification for this user
          if (newRow.user_id === user.id) {
            console.log('[NotificationBell] Realtime: new personal notification');
            fetchUnreadCount();
            return;
          }
          // Broadcast notification (user_id is null)
          if (!newRow.user_id) {
            console.log('[NotificationBell] Realtime: new broadcast notification');
            fetchUnreadCount();
            return;
          }
        }
      )
      .subscribe((status) => {
        if (status === 'SUBSCRIBED') {
          console.log('[NotificationBell] Realtime subscription active');
        }
      });

    return () => {
      supabase.removeChannel(channel);
    };
  }, [isLoggedIn, user?.id, fetchUnreadCount]);

  const handleBellClick = () => {
    navigateTo(6);
  };

  const handlePopupView = () => {
    setPlanGrantPopup(null);
    navigateTo(6);
  };

  // v17: Determine popup icon and color based on notification type
  const getPopupConfig = (notif: NotificationItem) => {
    const nt = notif.notification_type;
    if (nt === 'team_plan') return {
      icon: '👥',
      color: '#3b82f6',
      bg: '#eff6ff',
      borderColor: '#bfdbfe',
      title: notif.title || 'Team Plan Activated!',
      actionText: 'View Team Panel',
    };
    if (nt === 'extension' || nt === 'plan_extension') return {
      icon: '⏩',
      color: '#8b5cf6',
      bg: '#f5f3ff',
      borderColor: '#ddd6fe',
      title: notif.title || 'Plan Extended!',
      actionText: 'View Plan',
    };
    return {
      icon: '⭐',
      color: '#16a34a',
      bg: '#f0fdf4',
      borderColor: '#bbf7d0',
      title: notif.title || 'Plan Activated!',
      actionText: 'Start Practice',
    };
  };

  return (
    <>
      <div className="notif-bell-container">
        <button
          className="notif-bell-btn hover:scale-105 active:scale-95 transition-transform"
          onClick={handleBellClick}
          aria-label={`Notifications${unreadCount > 0 ? ` (${unreadCount} unread)` : ''}`}
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
            <path d="M13.73 21a2 2 0 0 1-3.46 0" />
          </svg>
          {unreadCount > 0 && (
            <span className="notif-badge">{unreadCount > 99 ? '99+' : unreadCount}</span>
          )}
        </button>
      </div>

      {/* v17: Plan Grant Popup */}
      {planGrantPopup && (() => {
        const cfg = getPopupConfig(planGrantPopup);
        return (
          <div
            style={{
              position: 'fixed',
              top: '16px',
              right: '16px',
              zIndex: 10000,
              background: cfg.bg,
              border: `2px solid ${cfg.borderColor}`,
              borderRadius: '14px',
              padding: '16px 20px',
              maxWidth: '340px',
              boxShadow: `0 8px 32px rgba(0,0,0,0.15), 0 0 0 1px ${cfg.borderColor}`,
              animation: 'slideInRight 0.3s ease-out',
              fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
            }}
          >
            <button
              onClick={() => setPlanGrantPopup(null)}
              style={{
                position: 'absolute', top: '8px', right: '8px',
                background: 'none', border: 'none', cursor: 'pointer',
                color: '#6b7280', fontSize: '16px', padding: '4px', lineHeight: 1,
              }}
              aria-label="Close popup"
            >
              ✕
            </button>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
              <span style={{ fontSize: '24px' }}>{cfg.icon}</span>
              <h3 style={{ margin: 0, fontSize: '0.95rem', fontWeight: 700, color: cfg.color }}>
                {cfg.title}
              </h3>
            </div>
            <p style={{ margin: '0 0 12px 0', fontSize: '0.82rem', color: '#374151', lineHeight: 1.5, whiteSpace: 'pre-wrap' }}>
              {planGrantPopup.message?.split('\n').filter(l => l.trim()).slice(0, 3).join(' • ')}
            </p>
            <button
              onClick={handlePopupView}
              style={{
                background: cfg.color,
                color: '#fff',
                border: 'none',
                borderRadius: '8px',
                padding: '8px 16px',
                fontSize: '0.82rem',
                fontWeight: 600,
                cursor: 'pointer',
                width: '100%',
                transition: 'opacity 0.15s',
              }}
              onMouseEnter={e => (e.currentTarget.style.opacity = '0.9')}
              onMouseLeave={e => (e.currentTarget.style.opacity = '1')}
            >
              {cfg.actionText} →
            </button>
            <style>{`
              @keyframes slideInRight {
                from { transform: translateX(100%); opacity: 0; }
                to { transform: translateX(0); opacity: 1; }
              }
            `}</style>
          </div>
        );
      })()}
    </>
  );
}
