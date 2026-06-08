/**
 * TeamCodeGuide.tsx — S13: Guided tour for team code copy
 *
 * When a user's team plan becomes active (and they are a team leader),
 * this component shows a step-by-step guide on the landing page:
 *
 * Step 1: Focus profile icon → blue card "Click here to copy your team code"
 * Step 2: Profile menu opens → focus Team Panel button → blue border + card "Get your team code from here"
 * Step 3: Inside Team Panel → focus copy icon → card "Guide finish"
 *
 * Cards are blue with white font (dark mode: dark blue with light font),
 * rectangle with rounded corners, dismiss button on each card.
 * All guide text is in English.
 */

import { useState, useEffect, useRef, useCallback } from 'react';
import { useAuth } from '../context/AuthContext';
import '../styles/teamcodeguide.css';

type GuideStep = 'step1-profile' | 'step2-menu' | 'step3-panel' | 'done';

const GUIDE_DISMISSED_KEY = 'rhc_team_guide_dismissed';

export function TeamCodeGuide() {
  const { profile, isLoggedIn, isTeamLeader } = useAuth();
  const [step, setStep] = useState<GuideStep>('done');
  const [dismissed, setDismissed] = useState(false);
  const profileBtnRef = useRef<HTMLButtonElement | null>(null);
  const teamPanelBtnRef = useRef<HTMLButtonElement | null>(null);

  // v18 FIX: Dynamically add/remove blue border highlight on Team Panel button
  // Only show the highlight when the guide is active (step2 or step3)
  useEffect(() => {
    const teamPanelBtn = document.querySelector('.menu-item.team-leader');
    if (teamPanelBtn) {
      if (step === 'step2-menu' || step === 'step1-profile') {
        teamPanelBtn.classList.add('tcg-border-highlight');
      } else {
        teamPanelBtn.classList.remove('tcg-border-highlight');
      }
    }
    // Cleanup on unmount or step change
    return () => {
      const btn = document.querySelector('.menu-item.team-leader');
      if (btn) btn.classList.remove('tcg-border-highlight');
    };
  }, [step]);

  // Check if the guide should be shown
  useEffect(() => {
    if (!isLoggedIn || !isTeamLeader || !profile?.plan_active || dismissed) {
      return;
    }

    // Check if already dismissed for this team plan
    const dismissedData = localStorage.getItem(GUIDE_DISMISSED_KEY);
    if (dismissedData) {
      try {
        const parsed = JSON.parse(dismissedData) as { teamPlanId: string; timestamp: number };
        // If dismissed for the same team plan within the last 7 days, don't show
        if (parsed.teamPlanId === profile.team_plan_id && Date.now() - parsed.timestamp < 7 * 24 * 60 * 60 * 1000) {
          return;
        }
      } catch { /* ignore */ }
    }

    // Show step 1 after a short delay for the page to render
    const timer = setTimeout(() => setStep('step1-profile'), 1500);
    return () => clearTimeout(timer);
  }, [isLoggedIn, isTeamLeader, profile?.plan_active, profile?.team_plan_id, dismissed]);

  // Listen for custom events from UserProfile to know when menu/panel opens
  useEffect(() => {
    const handleMenuOpen = () => {
      if (step === 'step1-profile') {
        setStep('step2-menu');
      }
    };

    const handlePanelOpen = () => {
      // v15 FIX: Accept panel open from any step that makes sense
      // Previously only accepted from 'step2-menu', but when step2 button
      // dispatches rhc_open_team_panel AND immediately sets step to step3,
      // the event handler might miss it because step already changed.
      if (step === 'step2-menu' || step === 'step3-panel') {
        setStep('step3-panel');
      }
    };

    window.addEventListener('rhc_profile_menu_opened', handleMenuOpen);
    window.addEventListener('rhc_team_panel_opened', handlePanelOpen);

    return () => {
      window.removeEventListener('rhc_profile_menu_opened', handleMenuOpen);
      window.removeEventListener('rhc_team_panel_opened', handlePanelOpen);
    };
  }, [step]);

  const handleDismiss = useCallback(() => {
    setStep('done');
    setDismissed(true);
    // Remember dismissal
    if (profile?.team_plan_id) {
      localStorage.setItem(GUIDE_DISMISSED_KEY, JSON.stringify({
        teamPlanId: profile.team_plan_id,
        timestamp: Date.now(),
      }));
    }
  }, [profile?.team_plan_id]);

  const handleStep1Click = useCallback(() => {
    // Dispatch event to UserProfile to open the dropdown
    window.dispatchEvent(new CustomEvent('rhc_open_profile_menu'));
    setStep('step2-menu');
  }, []);

  // If guide is done or shouldn't show, render nothing
  if (step === 'done' || !isLoggedIn || !isTeamLeader || !profile?.plan_active) {
    return null;
  }

  return (
    <>
      {/* Step 1: Guide card near profile icon */}
      {step === 'step1-profile' && (
        <div className="tcg-overlay" onClick={handleDismiss}>
          <div className="tcg-card tcg-card--step1" onClick={e => e.stopPropagation()}>
            <button className="tcg-dismiss" onClick={handleDismiss} aria-label="Dismiss guide">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
            <div className="tcg-card-icon">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M23 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" />
              </svg>
            </div>
            <p className="tcg-card-text">Click here to copy your team code</p>
            <button className="tcg-card-action" onClick={handleStep1Click}>
              Open Profile Menu
            </button>
            {/* Arrow pointing down-left towards profile icon */}
            <div className="tcg-arrow tcg-arrow--down-left" />
          </div>
        </div>
      )}

      {/* Step 2: Guide card in the profile dropdown menu */}
      {step === 'step2-menu' && (
        <div className="tcg-overlay tcg-overlay--transparent" onClick={handleDismiss}>
          <div className="tcg-card tcg-card--step2" onClick={e => e.stopPropagation()}>
            <button className="tcg-dismiss" onClick={handleDismiss} aria-label="Dismiss guide">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
            <div className="tcg-card-icon">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2" /><path d="M7 11V7a5 5 0 0 1 10 0v4" />
              </svg>
            </div>
            <p className="tcg-card-text">Get your team code from here</p>
            <button className="tcg-card-action" onClick={() => {
              // v15 FIX: Dispatch event to open team panel, then set step after a small delay
              // to ensure the panel has time to open and dispatch rhc_team_panel_opened
              window.dispatchEvent(new CustomEvent('rhc_open_team_panel'));
              // Small delay to let UserProfile open the panel and dispatch rhc_team_panel_opened
              setTimeout(() => setStep('step3-panel'), 300);
            }}>
              Open Team Panel
            </button>
            {/* Arrow pointing towards Team Panel button */}
            <div className="tcg-arrow tcg-arrow--left" />
          </div>
        </div>
      )}

      {/* Step 3: Guide card in the Team Panel */}
      {step === 'step3-panel' && (
        <div className="tcg-overlay tcg-overlay--transparent" onClick={handleDismiss}>
          <div className="tcg-card tcg-card--step3" onClick={e => e.stopPropagation()}>
            <button className="tcg-dismiss" onClick={handleDismiss} aria-label="Dismiss guide">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
            <div className="tcg-card-icon tcg-card-icon--success">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" /><polyline points="22 4 12 14.01 9 11.01" />
              </svg>
            </div>
            <p className="tcg-card-text">Guide Finished! Copy your team code from the copy icon above.</p>
            <button className="tcg-card-action tcg-card-action--finish" onClick={handleDismiss}>
              Done
            </button>
          </div>
        </div>
      )}
    </>
  );
}
