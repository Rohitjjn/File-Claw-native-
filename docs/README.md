# Files Claw — Documentation Index

Docs are grouped by role. **Start here before any phase.** Never recreate docs — update them, and
label every claim `measured` or `not verified`.

## Start (read in this order)
1. [`AGENTS.md`](AGENTS.md) — how the improvement loop works; state to read on every run
2. [`TODO.md`](TODO.md) — backlog + KNOWN ISSUES (read first)
3. [`FILE_SUPPORT_AUDIT.md`](FILE_SUPPORT_AUDIT.md) — Phase 0 audit: what works, what's broken (AUD register), what to add next
4. [`PHASES.md`](PHASES.md) — the complete 10-phase execution plan (steps + CI test ladder per phase)
5. [`PRD.md`](PRD.md) — product requirements, metrics, release plan
6. [`CHANGELOG.md`](CHANGELOG.md) — what changed; measured vs unverified

## Product
- [`PHASES.md`](PHASES.md) — master plan: 10 phases, exit criteria, universal test ladder
- [`PRD.md`](PRD.md) — vision, scope, FR/NFR, success metrics, phase plan
- [`ROADMAP.md`](ROADMAP.md) — milestones + phase exit criteria
- [`FILE_FORMATS.md`](FILE_FORMATS.md) — generated format matrix (399 ext / 284 full / 32 partial / 83 identify)
- [`FILE_SUPPORT_AUDIT.md`](FILE_SUPPORT_AUDIT.md) — per-format status + issue register (AUD-001…034)

## Design & UX
- [`DESIGN.md`](DESIGN.md) — Claude design system: tokens, glass, rollout status
- [`UI_FRONTIER.md`](UI_FRONTIER.md) — Phase 3 frontend plan ("The Quiet Desk") — no code yet

## Engineering
- [`ARCHITECTURE.md`](ARCHITECTURE.md) — layers, opener pipeline, safety boundaries, structural debt
- [`BUILD_GUIDE.md`](BUILD_GUIDE.md) — requirements, Quality Gate, release, GitHub Actions
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — branching, Conventional Commits, definition of done
- [`SECURITY_AUDIT.md`](SECURITY_AUDIT.md) — v1.1.0 security review + loop notes
- [`SANITY_CHECKLIST.md`](SANITY_CHECKLIST.md) — on-device pre-release checklist
- [`PATCH_NOTES.md`](PATCH_NOTES.md) — v1.1.0 packaging notes (patch-zip era, historical)
- [`known-issues.md`](known-issues.md) — detailed bug tracker with BUG IDs (live, Loop 14 watchlist)
- [`worklog.md`](worklog.md) — append-only loop worklog
- [`changelogs.md`](changelogs.md) — full loop changelog (v1.1.0 → v1.3.x, verbose)

## Prompts
- [`prompts/PHASE_1_PROMPT.md`](prompts/PHASE_1_PROMPT.md) — shipped 2026-11-17 (build hygiene + broken-format repairs)
- [`prompts/PHASE_2_PROMPT.md`](prompts/PHASE_2_PROMPT.md) — **active** (test infrastructure & architecture)
- [`prompts/NEXT_LOOP_PROMPT.md`](prompts/NEXT_LOOP_PROMPT.md) — superseded (history)
- [`prompts/README.md`](prompts/README.md) — prompt index & rules

## Legacy (partially out of date — do not trust without checking)
Everything under [`legacy/`](legacy/): pre-1.1.0 blueprint, mindmap, chart, rules, tech-specs,
security-protocol, testing-checklist, backup-manager, full PERFORMANCE.md, loop prompts 1–6
(`legacy/prompts/`), AI Studio implementation plan (`legacy/aistudio/`). Kept for reference; the
authoritative docs are the ones above.
