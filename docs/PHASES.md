# Files Claw — The Complete Phase Plan (how the app reaches "done")

**Written:** 2026-11-17 (Phase 0) · **Owner:** product + engineering · **Rule:** one phase at a time,
strictly in order. Never start Phase N+1 with Phase N's gates red. Every phase's last step is
*"write the next phase prompt"* into `docs/prompts/` (one active prompt at a time — see
`docs/prompts/README.md`).

**Ground truth (Phase 0 audit):** `docs/FILE_SUPPORT_AUDIT.md` (what works / AUD-001…034),
`docs/PRD.md` (goals), `docs/ROADMAP.md` (this list), `docs/UI_FRONTIER.md` (Phase 3 spec).

**Testing philosophy (all phases, no exceptions):** everything verifiable must run **in GitHub
Actions** (`.github/workflows/ci.yml` + `build-and-release.yml`), not on faith. Unit tests on the
plain JVM where logic is pure Kotlin (`core/`); emulator instrumented tests in CI where Android is
required; screenshot tests on the JVM (Paparazzi/Roborazzi) where UI is involved; device runs are the
final gate and are recorded in `docs/SANITY_CHECKLIST.md`. Nothing is claimed without a log.

---

## Phase 0 — Audit & repo hygiene ✅ DONE (2026-11-17)

**Goal:** know the real state; clean the repo; write the plan.

**What was done:** unwanted files removed (13 stale/duplicate root files), `.gradle/` untracked (~9.6 MB),
`.gitignore`, structure organised (docs live under `docs/`), `docs/PRD.md` created, full audit
(`FILE_SUPPORT_AUDIT.md`), UI plan (`UI_FRONTIER.md`), docs index, CI workflow added. Branch:
`arena/5100f329-file-claw-native` @ `2e34013`.

**Tests:** none possible (docs-only). **Gate:** none — closed.

---

## Phase 1 — Quality Gate green + broken-format repairs ✅ LANDED (2026-11-17, CI gate pending)

**Goal:** the project **compiles, tests and assembles in CI**; every known broken viewer fails
*honestly* (notice + retry, never a crash/eternal spinner/blank screen).

**Entry:** Phase 0 done. **Prompt:** `docs/prompts/PHASE_1_PROMPT.md` (executed 2026-11-17; see CHANGELOG + audit §9).
**Outcome:** all P1 silent-failure paths fixed; 191/191 pure-JVM tests measured green; the Android gate
was not runnable in the working sandbox — first CI run is the verification step (push approval pending).

**Steps:**
1. Preflight: JDK 17 + SDK 34 (or rely on CI). Fix **all** compile fallout before features.
2. Dependency/supply-chain hygiene: remove dead `com.github.mhiew:android-pdf-viewer`; scope/remove JitPack
   (AUD-001/AUD-019); release signing **fail-closed** when no keystore (AUD-021).
3. Silent-failure kill-list (P1): PPTX error path + watchdog (AUD-005); audio `prepareAsync` on
   `Dispatchers.IO` + `OnErrorListener` (AUD-007); video `setOnErrorListener` (AUD-008); MIDI → registry
   IDENTIFY (AUD-006); Coil `onError` + invalid-font notice (AUD-010/AUD-009).
4. Hex viewer paging: 64 KB windows + offset jump (AUD-003); `core/HexDump` pure Kotlin + unit tests.
5. Room `4.json` committed (AUD-034); `DatabaseMigrationTest` green.
6. Four UI bugs: BUG-004 (CSV clipping), BUG-005 (password IME overlap), BUG-006 (PDF page-1 geometry),
   BUG-007 (DOCX pinch overflow).
7. Docs/strings reconcile: write-back messaging vs `sourceUri` reality (AUD-024); test-count drift fix (AUD-022).
8. Regenerate `docs/FILE_FORMATS.md` after the registry MIDI edit; update audit statuses.

**Tests (CI, every push/PR from now on):**
`compileDebugKotlin` → `testDebugUnitTest` → `jacocoCoreVerify` (≥80 % core lines) → `lintDebug` →
`detekt` (advisory) → `assembleDebug`. Plus: `node --check` for any touched inline JS; `xmllint`/parse
checks; new unit tests for HexDump + MIDI registry + media error policies. Device smoke (recorded in
SANITY_CHECKLIST): open PDF/DOCX/PPTX/MIDI/hex fixtures; corrupt-file cases bounce to honest errors.

**Exit criteria:**
- [ ] CI green with a linked run (or an explicit blocker report) on the PR/branch
- [ ] AUD-001, AUD-005…AUD-010, AUD-013, AUD-021, AUD-034 closed; BUG-004…007 closed
- [ ] Zero silent-failure paths remain in media viewers (checklist in PR description)
- [ ] CHANGELOG carries the **measured** test count and build result
- [x] Last step: write `PHASE_2_PROMPT.md` (done 2026-11-17)

**Estimate:** 1 focused session (compile duty dominates). **Risk:** Loop 6–15 code never compiled —
budget compile-fallout triage generously.

---

## Phase 2 — Test infrastructure & architecture

**Goal:** the monolith shrinks into testable modules and CI proves behavior, not just compilation.

**Entry:** Phase 1 LANDED (gate pending — Phase 2 starts by closing it). **Prompt:** `docs/prompts/PHASE_2_PROMPT.md`.

**Steps:**
1. **Emulator instrumented tests in CI** (`reactivecircus/android-emulator-runner`, AVD API 34):
   open → edit → save → reopen round trips (text, Markdown, CSV); "open with → File Claw" intent path;
   archive password flow; corrupt-PDF error path.
2. **Screenshot tests on the JVM** (Paparazzi or Roborazzi): baseline images for Home/AllFiles/Search/
   Settings (light+dark) — catches accidental UI regressions without an emulator.
3. **Split `FilePreviewScreen.kt` (≈5,390 lines)** into `ui/screens/viewers/` — one file per viewer
   (`PdfViewer.kt`, `DocxViewer.kt`, `PptxViewer.kt`, `TextViewer.kt`, `CsvViewer.kt`, `ZipViewer.kt`,
   `HexViewer.kt`, `ImageViewer.kt`, `AudioViewer.kt`, `VideoViewer.kt`, `CodeViewer.kt`,
   `MediaViewer.kt`) + shared `ReaderChrome.kt` (find bar / command bar / IME-safe tray).
4. **Type-safe navigation** (Navigation Compose serializable routes); delete route-without-args +
   singleton `currentFileState` smuggling.
5. `MainViewModel` decomposition where cheap (move file IO/builders into `core/` or use-cases; keep
   ViewModel as orchestrator).
6. **detekt:** first green run on `core/` only, then `ui/` advisory → baseline committed.
7. **JaCoCo:** gate restored and verified; coverage report uploaded as CI artifact.
8. Extract `strings.xml` for all *new* strings only (full i18n stays in Phase 7).

**Tests (CI):** all Phase 1 gates **plus** — instrumented suite on emulator (API 34) on PR/main;
Paparazzi verify task; jacoco report artifact; detekt baseline. Failing screenshot = PR blocked.

**Exit criteria:**
- [ ] No viewer file > 800 lines; `FilePreviewScreen.kt` deleted or reduced to a shell
- [ ] Navigation is type-safe; adding a route without arguments compiles-out
- [ ] Instrumented + screenshot suites green in CI; `jacocoCoreVerify` blocking
- [ ] AUTHORS/CHANGELOG updated; PHASE 3 prompt written

**Estimate:** 1–2 sessions. **Risk:** behavior drift during the split — mitigated by Paparazzi
baselines taken **before** refactoring (golden-master first, then move code mechanically).

---

## Phase 3 — Claude frontier UI ("The Quiet Desk")

**Goal:** the app *looks and feels* finished — calm, editorial, unmistakably Claude — without touching
data behavior. Spec: `docs/UI_FRONTIER.md` (tokens → navigation → components → viewers → honesty matrix → a11y).

**Entry:** Phase 2 green. **Prompt:** written at the end of Phase 2.

**Sub-phases (each is a mergeable PR):**
- **3a Foundations:** semantic color tier, bundled typefaces (Source Serif 4 / Inter / JetBrains Mono,
  SIL OFL), radius/spacing/hairline tokens, `Motion.kt` (fast/base/slow + emphasized easings),
  glass-at-root NavHost policy (opaque Scaffolds → transparent + fade-through), `Icons.AutoMirrored`
  sweep, deprecation cleanup.
- **3b Home · All Files · Search · navigation:** adaptive two-pane (`WindowSizeClass`), nav rail on
  expanded widths, Quick Open / Command Palette (Ctrl/Cmd+K, files + actions), full keyboard shortcut
  map, predictive back, empty-state illustrations, Home hero composition.
- **3c Reader chrome v2:** `ReaderCommandBar` (single floating contextual pill), reading-progress
  hairline, per-viewer spec (PDF/DOCX/TXT-MD/code/CSV/archive/hex/media/SQLite), sheets unified
  (Properties/Outline/Appearance — glass + one grabber), dialogs v2 (`imePadding`, show/hide).
- **3d Settings/sheets/dialogs/splash/icon:** live theme previews, theme/font/density groups,
  splash claw-mark draw animation, adaptive + **themed monochrome icon** (Android 13+).
- **3e Hardening:** font-scale 1.0→2.0 matrix, reduced-motion, RTL sweep, TalkBack pass, contrast
  audit on composited backdrops (≥4.5:1 body), 48 dp targets, content-description coverage 100 %.

**Tests (CI):** all Phase 2 gates **plus** — Paparazzi matrix (component × theme × scale) green;
contrast-audit script (token pairs over composited backdrops) as a CI job; Compose semantics tests
for critical flows; Macrobenchmark smoke (indicative on emulator); `SANITY_CHECKLIST` UI section signed on device.

**Exit criteria:**
- [ ] Screenshot matrix (light/dark × 1.0/1.3/2.0 × compact/medium/expanded) committed to `docs/ui-snapshots/`
- [ ] Independent "does this still feel like Claude?" review passes (stakeholder sign-off)
- [ ] Zero viewer regressions (PDF find/scrubber/rotation, DOCX zoom, save paths) via instrumented suite
- [ ] PHASE 4 prompt written

**Estimate:** 2–3 sessions. **Risk:** scope sprawl — 3a gate (app compiles + screenshots approved) is mandatory before 3b.

---

## Phase 4 — Format depth & new viewers

**Goal:** move the needle on real-world coverage — the audit's candidate list (`FILE_SUPPORT_AUDIT.md` §7),
in ranked order. Every addition follows the **Contribution contract**: `FormatRegistry` entry → `FileKind`
route → pure-Kotlin decoder/extractor in `core/` (+ JUnit + golden test fixtures) → registry/list regen →
`FILE_FORMATS.md` + audit doc updated → `SANITY_CHECKLIST` row → CHANGELOG.

**Entry:** Phase 3 done. **Prompt:** written at the end of Phase 3.

**Order of work:**
1. **Tier 1:** legacy `.ppt` text recovery (reuse `Ole2CompoundFile`), `.xlsb` (BIFF12),
   CJK charset detection (`juniversalchardet` — kills windows-1252 mojibake), MIDI decision
   (ship tiny synth or keep IDENTIFY — decide with user), animated WebP/progressive JPEG verify.
2. **Tier 2:** WOFF/WOFF2→TTF preview; XLSX multi-sheet picker; GLB/glTF + PLY shaded 3D (extend
   `MeshParser`); DXF wireframe; RAR — *only if* a maintained pure-JVM/libarchive path passes license +
   footprint review (junrar abandoned — re-evaluate); `.pages`/`.numbers`/`.key` text recovery;
   Parquet (Apache Arrow Kotlin) read-only preview — measure APK weight, keep gate at +3 MB.
3. **Tier 3 (evaluate, don't promise):** ISO/CAB via commons-compress; `.srt/.vtt` burn-in over video;
   Markdown WYSIWYG; two-file diff viewer; rich EPUB (opt-in sanitized CSS); zstd streams.
4. Registry honesty pass: `LEGACY_OFFICE` per-extension nuance or split kinds (.doc/.xls = partial);
   regenerate `FILE_FORMATS.md`.

**Tests (CI):** golden-fixture unit tests per decoder; fuzzed/corrupt inputs (truncated/garbage) assert
*graceful* failure states; registry consistency (no dup ids/exts); `jacocoCoreVerify` stays ≥ 80 %;
instrumented open test on 3 representative files per new kind; corrupt/password/bomb fixtures in
`app/src/androidTest/assets/`.

**Exit criteria:**
- [ ] All Tier 1 items shipped or explicitly declined with reasons in the audit doc
- [ ] Coverage target: ≥ 90 % of registry extensions reach a deliberate state (no blank screens)
- [ ] Zero new P0/P1 audit items; archive/decoder fuzz corpora committed
- [ ] PHASE 5 prompt written

**Estimate:** 2–3 sessions. **Risk:** dependency creep — every new library needs APK-size and offline justification.

---

## Phase 5 — Performance, memory & 500 MB-class files

**Goal:** prove the speed/scale claims with on-device numbers; make big files boring.

**Entry:** Phase 4 done. **Prompt:** written at the end of Phase 4.

**Steps:**
1. **Macrobenchmark module** (`:macrobenchmarks`): cold start, recents scroll (120 Hz), PDF open
   (500-page), list→detail transition, save round-trip. Runs on emulator in CI (indicative) and on a
   **real reference device** before release gates (numbers recorded, not guessed).
2. **Baseline Profiles** generated + packaged (cuts cold start ~30 %); profile installer verified.
3. **Complete the huge-file pager:** virtualized 100–500 MB TXT/log reading UI on `TextWindowReader`
   (bounded memory, newline-aligned); Markdown block context across page boundaries; >10k search
   offset navigation (bounded windowing).
4. **Memory audits:** PDF resident window + canvas budget on 3 GB-RAM device; DOCX base64 path
   replaced by streamed bytes if not already (Phase 4 may land it); Coil memory trim on background;
   LeakCanary in debug builds for a full-session leak sweep.
5. **Startup:** R8 full mode re-check; unused resources shrink; font subsetting (keep OFL subset
   minimal); splash→home jank eliminated.
6. **Jank gate:** 95 % frames < 16 ms on reference device for scroll flows; report to CHANGELOG.

**Tests (CI):** unit/instrumented gates unchanged; new Macrobenchmark job (emulator, informational
trend + regression warning at +10 % vs baseline JSON); optional Firebase Test Lab device run for
Macrobenchmarks before release (paid tier — user decision); 500 MB open instrumented test on AVD with
large heap (assert < 512 MB RSS, no OOM).

**Exit criteria:**
- [ ] Cold start < 1.5 s (release build, reference device) · 60 fps scroll · 500 MB opens without OOM
- [ ] Benchmark trend committed; regressions visible in PRs
- [ ] Baseline Profile in release APK; PHASE 6 prompt written

**Estimate:** 1–2 sessions. **Risk:** emulator perf numbers misleading — always pair with device numbers.

---

## Phase 6 — Security & privacy hardening

**Goal:** the offline promise is *auditable*: nothing leaves the device, nothing hostile gets executed.

**Entry:** Phase 5 done. **Prompt:** written at the end of Phase 5.

**Steps:**
1. **Manifest guard test in CI:** fail the build if `INTERNET` (or any new dangerous permission) appears;
   fail if source gains a non-local URL/host literal (grep-based guard job).
2. **SAF-first mode:** browse/open via Storage Access Framework by default; `MANAGE_EXTERNAL_STORAGE`
   strictly for explicit "file manager mode" (Play policy). Picker flows untouched.
3. Scope + audit **WebView surface**: enumerate every `loadUrl`/`evaluateJavascript`/bridge; confirm
   navigation locks; red-team the DOCX/PPTX base64 bridges; document in `docs/SECURITY_AUDIT.md` (new revision).
4. **Fuzz gates:** PathGuard + ArchiveSafety property-based tests; zip-slip AV corpus; SVG sanitizer
   corpus; EPUB XXE fixtures (already RFC-wrapped — keep tests).
5. **Dependency/CVE sweep:** `gradle:dependencyInsight` + manual CVE review of every runtime dep;
   update or drop; license audit table (THIRD_PARTY_NOTICES) completed and verified.
6. **CI secrets scan** (gitleaks) + **fail-closed release** check re-verified end-to-end.
7. `dataExtractionRules` / `backup_rules` re-audit; `allowBackup=false` reaffirmed; FileProvider paths minimal.

**Tests (CI):** new `security` job — manifest guard, gitleaks, URL-literal scan, zip-slip corpus, existing
`ArchiveSafetyTest`/`PathGuardTest`/`HtmlSanitizerTest` must stay green; instrumented WebView-navigation
block test (attempt top-level navigation to `https://` → must be refused).

**Exit criteria:**
- [ ] No Critical/High findings open; Mediums tracked with dates
- [ ] Manifest guard green on `main` for 30 days (soak) before Play upload
- [ ] Security audit revision committed; PHASE 7 prompt written

**Estimate:** 1 session. **Risk:** SAF-first UX friction — prototype behind a setting first, default later.

---

## Phase 7 — Accessibility, i18n & device matrix

**Goal:** anyone with any phone — any font size, language, screen reader, screen size — gets the full app.

**Entry:** Phase 6 done. **Prompt:** written at the end of Phase 6.

**Steps:**
1. **Localisation:** extract *every* user-facing string to `strings.xml` (incl. viewers/notices);
   first translations: English (source of truth) + Hindi (user locale) — structure allows adding more.
2. **RTL verified:** full layout sweep in `ar` pseudo-locale; logical start/end everywhere; icons mirrored.
3. **TalkBack:** every icon/button labeled; pages announced ("Page 3 of 12"); tables announce header+cell;
   document-outline rotor added.
4. **Font scale 1.0/1.3/1.5/2.0:** no clipped/overlapping text anywhere (screenshot tests parametrize this).
5. **Device matrix (CI + lab):** API 26 (minSdk) smoke in CI if AVD available; API 34 emulator suite;
   3 GB-RAM low-end lab device; foldable posture emulator; tablet two-pane.
6. **Contrast final audit** incl. glass compositing; `#A09D96`→`#B9B6AE` fix if needed.
7. `contentDescription` coverage enforced via lint; missing labels block merge.

**Tests (CI):** string-extraction lint gate; pseudolocale build (`enXA`/`arXB`) boots in instrumented test;
screenshot tests × font scales; TalkBack scripted pass recorded on device (docs); API 26 smoke job.

**Exit criteria:**
- [ ] Zero missing labels (lint clean); contrast ≥ 4.5:1 everywhere measured
- [ ] Localized release candidate builds; TalkBack checklist in SANITY_CHECKLIST signed
- [ ] PHASE 8 prompt written

**Estimate:** 1–2 sessions. **Risk:** Hindi translation quality — native-speaker review step required.

---

## Phase 8 — Release & Play Store readiness

**Goal:** ship 1.4.0/2.0.0 to production through a safe, staged rollout — and be able to roll back.

**Entry:** Phase 7 done. **Prompt:** written at the end of Phase 7.

**Steps:**
1. **applicationId decision** (user signs off): keep `com.aistudio.filesclaw.apxqzy` or migrate to
   `app.filesclaw.files` — migration = new install identity, update path is *uninstall + reinstall*
   (document for users) or a staged dual-publish; decide ONCE, in writing, in `docs/DECISIONS.md`.
2. Signing fail-closed end-to-end; `apksigner verify` in the release workflow; SHA-256 sums published.
3. Versioning: `versionName`/`versionCode` policy documented; CHANGELOG final; `README` release badge.
4. **Store assets:** feature graphic (Claude-style, paper + terracotta), 6–8 screenshots (phone+tablet,
   light+dark), short/full descriptions (EN+HI), content rating (Everyone), Data Safety = "No data collected",
   privacy policy (trivial: files never leave device) hosted on GitHub Pages.
5. **Tracks:** internal → closed (20 Jaipur testers) → open (10 %) → 50 % → 100 %, 14 days vitals between steps.
6. Install/upgrade test: v1.3.0 → release candidate on-device (Room 3→4 migration verified!); export/restore sanity.
7. **Rollback plan:** previous APK retained; 100 % → halt procedure documented; vitals dashboard link shared.
8. Optional: Play App Signing enrollment (recommended; still compatible with fail-closed CI signing for uploads).

**Tests (CI):** release build only via `workflow_dispatch` (never on PR); release-signed APK/AAB assembled;
`apksigner verify --verbose`; AAB validated (`bundletool validate`); install path via `adb install -r` on lab device.

**Exit criteria:**
- [ ] Production release approved & live; vitals green for 14 days (crash-free ≥ 99.9 %)
- [ ] All phase prompts written & executed; `docs/PHASES.md` statuses all ✅
- [ ] Post-mortem template ready (`docs/POSTMORTEM.md` skeleton)

**Estimate:** 1 session + 14-day rollout window. **Risk:** Play policy on `MANAGE_EXTERNAL_STORAGE`
— Phase 6's SAF-first mode is the mitigation; keep the all-files toggle opt-in.

---

## Phase 9 — Post-release loop & 2.0 streaming renderers (ongoing)

**Goal:** keep the store listing honest and start the 2.0 architecture — *no base64 hand-offs anywhere*.

**Steps (recurring):**
1. **Field triage:** Play reviews/vitals → `TODO.md` → patch releases (1.4.x) within 7 days for P0s.
2. **2.0 architecture:** streamed bytes into WebViews (local HTTP range server for DOCX/PPTX, like PDF);
   25 MB caps removed; memory floor established; Macrobenchmark-gated budget in CI.
3. **Content search index:** optional, on-demand, incrementally-built SQLite FTS table over text-like
   files (name-only → content) — opt-in, cache-bounded, cleared in Settings.
4. **Quarterly hygiene:** dependency bumps (monthly), docs-drift audit (counts, line references),
   deprecation sweep, license re-audit, `SANITY_CHECKLIST` re-run.
5. Candidates parked for 2.x: DOCX/XLSX *editing* (round-trip fidelity R&D — separate spike),
   foldable postures, desktop/ChromeOS polish, tablet-first Creator mode.

**Tests (CI):** same gates as always + regression suite frozen per release; benchmark trend alarms;
release workflow hardened per Phase 8.

**Exit criteria:** none — this phase *is* the steady state. Success metric: crash-free ≥ 99.9 %,
rating ≥ 4.5, audit re-run shows no P1 items older than 60 days.

---

## The list — 10 phases total

| # | Phase | Theme | Status |
|---|---|---|---|
| 0 | Audit & repo hygiene | know the truth, clean the house | ✅ done (2026-11-17) |
| 1 | Quality Gate + broken-format repairs | CI green; honest failures everywhere | ⏳ next |
| 2 | Test infrastructure & architecture | instrumented/screenshot/screenshot-matrix CI; 5,390-line monolith split; typed nav |
| 3 | Claude frontier UI ("The Quiet Desk") | tokens, bundled typefaces, glass-at-root, Quick Open, adaptive two-pane, a11y |
| 4 | Format depth | .ppt, .xlsb, CJK, MIDI, RAR re-eval, GLB/PLY, sheet picker, Parquet |
| 5 | Performance, memory & big files | Macrobenchmark, Baseline Profiles, 500 MB paging, <1.5 s cold start |
| 6 | Security & privacy hardening | manifest guard in CI, SAF-first, WebView red-team, fuzz gates |
| 7 | Accessibility, i18n & devices | strings extracted, EN+HI, RTL, TalkBack, font-scale matrix, API 26 → 34 |
| 8 | Release & Play Store | applicationId decision, signing fail-closed, staged rollout, rollback plan |
| 9 | Post-release & 2.0 streaming renderers | triage loop, streamed WebView bytes, content-search index, 2.x spikes |

**Total: 10 phases (0–9), of which 1 is complete.** Phases 1→8 are strictly sequential;
Phase 9 runs forever. Each phase ends by writing the next phase's prompt — the loop never loses context.

## Universal per-phase test ladder (what "tests in CI" means here)

| Layer | Tool | Runs in | Blocks merge? |
|---|---|---|---|
| Static analysis | detekt, lint (abortOnError) | CI + `detekt` job | yes (lint) |
| Unit tests (pure Kotlin core) | JUnit via `testDebugUnitTest` | CI every push/PR | yes |
| Coverage gate | JaCoCo `jacocoCoreVerify` (≥80 % lines, `com.example.core`) | CI every push/PR | yes (Phase 2+) |
| Screenshot/golden-master | Paparazzi or Roborazzi | CI every push/PR | yes (Phase 2+) |
| Instrumented (emulator, API 34 + API 26 smoke) | android-emulator-runner | CI on PR/main | yes (Phase 2+) |
| Build | `assembleDebug` (and `assembleRelease` in release workflow) | CI every push/PR | yes |
| Benchmarks | Macrobenchmark (emulator trend; device for gates) | CI informational + device for release gates | trend-block only |
| Security guards | manifest/URL/gitleaks scan | CI every push/PR | yes (Phase 6+) |
| Release signing | `apksigner verify` | release workflow only | yes (release) |
| Device sanity | `docs/SANITY_CHECKLIST.md` on physical hardware | human, per release | yes (release) |

**Non-negotiables across every phase:** 100 % offline (no `INTERNET`, no CDN assets, no telemetry) ·
atomic saves only · read-only when truncated/converted · docs updated with measured/unverified labels ·
`core/` logic pure Kotlin + unit-tested · branch stays `arena/5100f329-file-claw-native` until the user says otherwise.
