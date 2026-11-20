# FILE CLAW — PHASE 3 PROMPT: Claude Frontier UI ("The Quiet Desk")

> Paste this whole file into the next session. Branch: `arena/5100f329-file-claw-native`.
> Phase 2 (test infrastructure & architecture) landed and is CI-green — read its outputs first:
> `docs/CHANGELOG.md` ([Unreleased] Phase 2 entry; green runs `44070900120`, `44073036452`,
> `44085373942`), `docs/FILE_SUPPORT_AUDIT.md` (§10 resolution log), `docs/worklog.md` (PHASE-2-TEST-INFRA-ARCH).

**North star:** "The Quiet Desk" — Files Claw is a calm, well-lit study desk, not a dashboard.
One accent, hairlines instead of shadows, type that breathes, motion that explains.
Premium = *restraint executed perfectly*, not more surface.

**The law:** `docs/UI_FRONTIER.md` is the binding spec (tokens §4 → navigation §5 → components §6 → viewers §7 →
honesty matrix §8 → a11y §9 → anti-patterns §10 → order & gates §11). Do not redesign beyond it; if a spec item
is impossible, write the reason in `docs/TODO.md` and keep going.

## 0. Read first (in order)
1. `docs/AGENTS.md` → `docs/TODO.md` (KNOWN ISSUES — incl. #10 instrumented scope) → `docs/CHANGELOG.md` → Phase 2 entry
2. `docs/PHASES.md` → "Phase 3 — Claude frontier UI" (sub-phase order, per-sub-phase gates) — **and `docs/UI_FRONTIER.md` top to bottom**
3. `docs/DESIGN.md` (existing tokens) · `app/src/main/java/com/example/ui/theme/{Color,Type,Glass,Theme}.kt`
4. `app/src/test/snapshots/images/` — the 10 Roborazzi baselines. Token changes will invalidate some of them.

## 1. Preflight — prove the entry gate first (do not skip)
1. `git rev-parse HEAD == origin/arena/5100f329-file-claw-native` (the workspace can restore HEAD to the branch
   base while keeping files — assert before every commit; see worklog).
2. Confirm the branch-tip CI run for Phase 2's closing commit is green (Quality Gate **and** Instrumented jobs).
   If not green: stop, fix forward — never build UI on a red gate.
3. Run `tools/local-verify/verify-core.sh` locally — must keep passing (core stays pure Kotlin).
4. Decide the screenshot policy up front: token/geometry changes invalidate Roborazzi baselines, so baselines may
   be re-recorded **only in the same commit as the visual change**, with the diff reviewed by a human. Never push
   `-Precord` noise on a separate branch state.

## 2. Scope (in sub-phase order; each sub-phase is a mergeable unit with its own CI green)
### 3a — Foundations
- Semantic color tier per §4.1 (`surfaceContainer`, `surfaceContainerHigh`, `onSurfaceVariant`, `success`, `warning`,
  `danger`, `selectionFill`, `link`) + format-family tint wells (iconography only); **banned colors enforced** (no
  pure black/white, no saturated accents, no rainbow badges).
- **Bundle the voices (Sil OFL, in APK):** Source Serif 4 (reading & editorial), Inter (UI), JetBrains Mono (code/hex).
  Ship the OFL license texts under `assets/licenses/`. Replace `FontFamily.Serif`/`Default` usages; add
  `displayLarge`, `labelSmall` (tracked eyebrows), `readingBody` (17/28 serif, 68 ch measure) per §4.2.
- Shape/spacing/stroke tokens per §4.3 (8/12/16/24/full radii; 4-dp grid; 1 dp hairlines; 1.5 dp terracotta focus ring).
- New `ui/theme/Motion.kt` per §4.4 (durations fast/base/slow/hero; emphasized easings; haptics map; reduced-motion honoring).
- **Glass-at-root:** mount `GlassBackdrop` at the NavHost root inside a **transparent-Scaffold** policy (fade-through,
  240 ms). Opaque reading surfaces stay opaque (DESIGN rule #1). Apply glass to bars/sheets/dialogs/snackbars/search.
- `Icons.Default.*` → `Icons.AutoMirrored.*` sweep; deprecation cleanup; zero new deprecations.
- **Gate 3a (hard stop before 3b):** app compiles; light/dark screenshot matrix updated deliberately; contrast audit of
  token pairs ≥ 4.5:1 (body) / 3:1 (large) — scripted and committed as a CI step or its absence documented.

### 3b — Home · All Files · Search · navigation
- Adaptive layout per §5.2: `WindowSizeClass` compact = single pane; medium = **list–detail two-pane**;
  expanded = 72 dp nav rail + 400 dp list pane + detail (persistent outline for DOCX/PDF/MD).
  Foldable postures honored; `supportsRtl` verified with logical start/end everywhere.
- **Quick Open / Command Palette** (`Ctrl/Cmd+K`, long-press Home, hardware mic key): fuzzy finder over
  **all files + actions** (reuse `FileSearchRanker`); the single most Claude-like interaction — do not water it down.
- Full keyboard shortcut map per §5.3 (documented in Settings → About → Shortcuts), focus rings, Tab order,
  typeahead in file lists; predictive back wired to AndroidX predictive APIs.
- Home hero composition (displayLarge serif), empty-state illustrations (generative warm-paper spots),
  `ClaudeTopBar` with collapsing large editorial title on Home/Search/Settings.
- **Gate 3b:** Macrobenchmark smoke (≥ 55 fps recents scroll on mid-tier **indicative on emulator** — do not fail the
  build on emulator FPS; record device numbers in SANITY_CHECKLIST); TalkBack sweep of Home→open→back; font-scale 2.0 pass.

### 3c — Reader chrome v2
- `ReaderCommandBar v2` per §6: single floating 48 dp pill (context chip + 3–5 contextual actions + `⋯`),
  auto-hide on scroll, hairline+glass, no scrim, no shadow. Find bar stays in the header (Loop 10 rule) — the pill
  does not duplicate it.
- Reading-progress hairline (1.5 dp terracotta) at the very top of readers.
- Per-viewer spec per §7 (PDF / DOCX / TXT-MD / code / CSV / archive / hex / media / SQLite) — including:
  serif `readingBody` for TXT/MD/RTF, Claude syntax theme toggles (Appearance) for code, tabular numerals +
  sticky first-column toggle for CSV, breadcrumb glass bar for archives, offset gutter + 64 KB windows kept for hex,
  "Report this page renders wrong" overflow item for PDF.
- **Sheets unified** (Properties/Outline/Appearance): glass shell, one 32×4 dp grabber, two-column meta grid,
  checksum chips with monospace tap-to-copy. **Dialogs v2:** 24 dp radius glass, serif titles, ≤ 3 actions,
  destructive danger-left; password fields keep the BUG-005 `imePadding()` fix and gain show/hide toggles.
- **Gate 3c:** Loop 10–14 reader behaviors intact (find bar, header navigation, DOCX zoom two-phase, PDF budgets,
  search arrows) — prove by the instrumented suite + screenshot diffs, not by eyeball; focus-mode regression sweep.

### 3d — Settings · sheets · dialogs · splash · icon
- Settings: keep the Loop 15 grouped-card structure; apply new tokens, serif section eyebrows, live theme previews
  (three mini-previews), theme/font/density groups, contrast warning chip.
- Splash: paper background, claw-mark sigil draws in 600 ms (vector `Path` animation, once, reduced-motion honored).
- Launcher icon: terracotta claw/plane sigil on warm paper; **adaptive + themed monochrome** icon (Android 13+).
- **Gate 3d:** signed release-candidate APK builds; `docs/SANITY_CHECKLIST.md` device sections signed (device required —
  do not fake this; if no device is available, list exactly which boxes are unchecked).

### 3e — Hardening
- Font-scale 1.0 → 2.0 matrix (no clipped labels; cards reflow); reduced-motion honoring everywhere (gate non-essential motion);
  RTL sweep (Home/AllFiles/Search/Preview); TalkBack pass (reader pages announced, tables announce header+cell, custom
  rotors for documents); contrast audit on **composited glass-over-glow** backdrops (≥ 4.5:1 body); 48 dp targets;
  `contentDescription` coverage 100 % for interactive elements.
- **Gate 3e:** Play internal-track dry run; crash-free ≥ 99.9 % over 200 sessions (post-release metric — plan the telemetry).

## 3. Explicitly OUT of scope
New formats (Phase 4) · 500 MB-class performance & Baseline Profiles (Phase 5) · applicationId change (Phase 5 decision) ·
signing & Play staged rollout (Phase 8) · full i18n/RTL localization beyond the sweep above (Phase 7) · state-management
redesign (`currentFileState` stays until a dedicated redesign).

## 4. Test & honesty rules (same as Phase 2)
- Every gate runs in CI (push/PR) — compileDebugKotlin, testDebugUnitTest, jacocoCoreVerify, detekt, Roborazzi verify,
  instrumented suite. Do not claim anything ran that did not; label **measured** vs **not verified**.
- `core/` stays pure Kotlin + unit-tested. `tools/local-verify/verify-core.sh` keeps passing.
- Screenshot baselines change **with** the visual commit, never in a silent mass re-record.
- `node --check` every inline JS touched; `xmllint` the manifest/strings; brace-balance changed Kotlin; keep goldens reviewable.
- **No new hardcoded user-visible strings** — `strings.xml` or it doesn't ship (Phase 7 i18n will consume them).
- Honest failure states are inviolable — the AUD-005/006/008 pattern (specific reason + retry, never an eternal spinner)
  extends to every error surface this phase touches.
- Commit per sub-phase (or finer); keep CI green on every push; no force-pushes.

## 5. Deliverable checklist (phase is done when)
- [ ] Gates 3a–3e green in CI with linked runs (screenshot matrix committed to `docs/ui-snapshots/` + CI artifacts)
- [ ] Bundled OFL typefaces in APK with license texts; `FontFamily.Serif`/`Default` eliminated from the UI tree
- [ ] Quick Open / Command Palette shipped (files + actions) and keyboard-map documented in Settings → About → Shortcuts
- [ ] Adaptive two-pane (medium) + nav rail (expanded) live; RTL + font-scale 2.0 passes recorded
- [ ] ReaderCommandBar v2 + unified sheets/dialogs on the §6 spec; 0 regressions in the instrumented suite
- [ ] Themed monochrome icon + splash draw animation; contrast audit script (or a documented absence with reasons)
- [ ] CHANGELOG/audit/worklog updated with **measured** numbers; screenshot diffs reviewed
- [ ] `docs/prompts/PHASE_4_PROMPT.md` written as the **last** step (references `docs/PHASES.md` Phase 4 + new format priorities)

## 6. Known risks / watch-list from Phase 2
- **Scope sprawl** — 3a's gate is mandatory before any 3b work; do not interleave.
- **Roborazzi churn** — expect baseline diffs in 3a/3c; plan review time for the PNG diffs; baselines are per-platform,
  so never hand-edit them.
- **Glass over WebView** — DOCX/PPTX/grid readers must stay opaque; audit every `Scaffold` whose background becomes
  transparent, and verify system-bar contrast per reader (extend `enableEdgeToEdge` + `WindowInsets` carefully).
- **Predictive back vs WebView find bar** — the back gesture must close find first, then collapse chrome, then leave the
  viewer; test against PDF/DOCX with keyboard and gesture nav.
- **Bundled font licensing** — OFL requires the license text shipped; a release without it is a takedown risk.
- **detekt** — new UI code is baselined separately (`detektUi` advisory); do not add UI debt to the core baseline. If a UI
  finding is trivially fixable, fix it; otherwise leave advisory and note it in the worklog.
- **CI brittleness** — annotations (`::error`) carry the failure tails; the sandbox cannot fetch raw job logs. Keep
  failure output greppable; do not "fix" CI by silencing steps.

## 7. If something cannot be done in this phase
Document it precisely in `docs/TODO.md` → KNOWN ISSUES (what, why, evidence), keep failure modes honest, and move on.
Never paper over a crash with a silent catch; never ship a placeholder button; never fake a green checkbox — unchecked
is a valid state, a false check is a defect.
