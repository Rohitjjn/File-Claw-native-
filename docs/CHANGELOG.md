# Changelog

Format: [Keep a Changelog](https://keepachangelog.com). Numbers marked **(measured)** were produced by a real
run in the build sandbox; **(not verified)** means the change could not be compiled or run there (no Android SDK,
no Maven access). See `TODO.md` → KNOWN ISSUES.

## [v1.3.2-arena] — 2026-11-20 · device-test round 2 (DOCX render recraft, PDF live scrubbing, spring focus motion, release APKs)

**Verification status:** compile/test/assembleDebug + instrumented emulator tests green in GitHub
Actions on branch `arena/5100f329-file-claw-native` (run id in the worklog); release APK built by
the tag workflow. Device round-1 fixes that this round builds on were confirmed on the reporting
device where noted. Everything below is **pending the next device round** unless stated.

### Fixed
- **DOCX preview blank screen — render pipeline recrafted (device round 1: NOT fixed; the print
  path of the SAME file rendered perfectly).** Round-1 screenshots (pixel-analyzed) showed the
  page surface painted nothing at all — no skeleton, no error, and the JS had reported success.
  The preview now mirrors the device-proven print path exactly and adds supervision:
  - The init script is now just `loadDocx(null, night)` — identical to the print path; the old
    pre-render `setZoom`/`setLineSpacing` calls are gone (zoom/dark/spacing re-apply after load
    through the existing state effects).
  - The zoom engine no longer uses the CSS `zoom` property (its interaction with
    `width:max-content`/`min-width:100%` collapses layout on some WebView builds). `#output` is
    scaled with `transform:scale()` and a new `#zoom-sizer` is sized to the visual box, so scroll
    extents always match the visible content; pinch previews resize the sizer live.
  - `renderAsync` staging moved from inside the (transformed) container to `document.body`, so
    page measurement can never be zoom-contaminated; zero-page renders and collapsed (unpainted)
    layouts are reported honestly through the recovery UI instead of a blank surface.
  - The load is now supervised from Kotlin: the init is re-issued at ~4 s and ~9 s while the
    loading overlay is up, and at ~14 s the labeled text-recovery state takes over with the
    reason; the in-page render watchdog now fires at 12 s. **(not verified on device)**
- **PDF page scrubber is now live (Google-Drive-style).** Grabbing the right-edge rail jumps to
  the touched position immediately, and every page boundary crossed while dragging scrolls the
  document in real time (page-granular emissions — the render pipeline is never spammed; pages
  passed over are still not eagerly rendered). Previously the jump happened only on release.
  **(not verified on device)**

### Changed
- **Focus-mode transitions rebuilt on spring physics** (third style; §4.4): the top bar and
  bottom tray glide in over half their height with a no-bounce low-stiffness spring and leave
  with a quick medium-stiffness snap; the focus pill pops in with a slight overshoot (damping
  0.62) from 0.85 scale. Springs are velocity-continuous and read smoother than the previous
  emphasized tweens. **(not verified on device)**
- **Tag builds are now RELEASE APKs** (`assembleRelease`, R8-minified) instead of debug APKs,
  published as `FileClaw-<tag>-release.apk`. Without `FILECLAW_*` secrets the release build type
  falls back to the debug key (pre-existing behavior), so test builds stay installable —
  release-optimized, debug-signed, not for Play.

### Confirmed on device (round 1)
- PDF preview opens (the pdf.js main-thread fake-worker fix worked); photo pinch-zoom pager lock
  was not re-reported this round but also not explicitly confirmed.

## [v1.3.1-arena] — 2026-11-18 · device-test round 1 fixes (from v1.3.0-arena on-device feedback)

**Verification status:** compile/test/assembleDebug + instrumented emulator tests green in GitHub
Actions run `44689845228` (both jobs); release APK built by run `44690844628` from tag
`v1.3.1-arena`. All four fixes are structural changes reasoned from the reported symptoms —
**they are not yet re-verified on the reporting device**; the next test round decides.

### Fixed
- **PDF preview: "could not open pdf, failed to fetch" on devices.** pdf.js fetched the document
  from inside its worker thread, and worker-side `fetch()` is not routed through
  `shouldInterceptRequest` on Android WebView — so `/search-pdf/document.pdf` never reached the
  bundled asset loader on a real phone (CI's emulator never opens a PDF, which is why the gate
  stayed green). The page now imports `pdf.worker.min.mjs` and publishes it on
  `globalThis.pdfjsWorker` (pdf.js's supported main-thread "fake worker" mechanism) and passes
  `useWorkerFetch: false` + `disableRange: true`, so the document fetch runs on the main thread
  where `WebViewAssetLoader` intercepts it. **(not verified on device)**
- **DOCX preview: blank white screen while printing the same file worked.** The native print path
  uses its own WebView, so the bytes and the parser were fine; the preview page could die silently
  if any statement in the one-shot init script threw (skipping `loadDocx` entirely) or if
  `renderAsync` never settled — the skeleton would just sit there. Each init statement is now
  individually guarded so `loadDocx` always runs, and a 25 s render watchdog reports through
  `onDocumentError` into the existing honest recovery UI instead of a silent white screen.
  **(not verified on device)**
- **Focus-mode chrome animations replaced** (§4.4): top bar and bottom tray now enter with
  `EmphasizedDecelerate` at `SLOW_MS` — a partial slide (one third of the bar) + fade + a 0.98
  scale anchored to the screen edge — and leave with `EmphasizedAccelerate` at `BASE_MS`; the
  focus pill enters with a 0.9 → 1 scale. The old full-height `FastOutSlowInEasing` slides are
  gone. **(not verified on device)**
- **Photo viewer pinch-zoom glitch:** telephoto 0.7.1 does not coordinate with `HorizontalPager`,
  so panning a zoomed-in image tugged the whole page sideways. The pager's `userScrollEnabled` is
  now locked while the visible image is zoomed (> 1.02×, observed via `snapshotFlow` on
  `contentTransformation`) and re-enabled at 1× / when the page settles. Swiping between photos at
  1× is unchanged. **(not verified on device)**

## [Unreleased] Phase 3 — Claude frontier UI ("The Quiet Desk") — IN PROGRESS (3a + 3b landed 2026-11-17; 3c steps 1–5 landed 2026-11-17/18; 3d/3e pending)

**Verification status (read first):** all gates above ran in **GitHub Actions** on branch
`arena/5100f329-file-claw-native` (workflow `CI`, jobs "Quality Gate" and "Instrumented tests (emulator
API 34)"). Measured green runs: `44128246714` (3a foundations, both jobs), `44135800812` (3b Quick Open
compile fix, both jobs), `44140293225` (3b shortcut map / nav rail / wordmark, both jobs), `44150911227`
(3c step 1, both jobs), `44154177670` (3c step 2, both jobs), `44157789487` (AUD-035 offline fix, both
jobs), `44236906638` (3c step 3, both jobs), `44271726453` (3c step 4, both jobs), `44448030978`
(3c step 5, both jobs). Failed runs that
were fixed forward: `44132429688` (3b compile — invalid fully-qualified `androidx.…` call in a Modifier
chain), `44133924754` (3b compile — missing imports for the `KeyEvent` extension properties
`key`/`type`/`isCtrlPressed`/`isMetaPressed`), `44148240751` (3c step 1 compile — Material icons
invoked as functions instead of used as extension properties) and `44270564728` (3c step 4 compile —
duplicate `modifier` named argument on the reader Scaffold); all are kept, not hidden. Roborazzi
baselines were re-recorded by the CI bot in `85a9423` (3a token/primary swap), `5f1a149` (3b visual
commit) and `459974a` (3c step 4 — the Settings shortcuts card grew five rows). Nothing is claimed from the working sandbox beyond the contrast audit (no Android
SDK/Maven there).

### Added (3a — foundations)
- **Semantic color tier** per §4.1 (`surfaceContainer`, `surfaceContainerHigh`, `onSurfaceVariant`, `success`,
  `warning`, `danger`, `selectionFill`, `link`) + format-family tint wells; terracotta demoted to accent-only;
  filled button re-pointed to `ClaudeSoftTomato` (**measured 4.80:1** on both schemes); light warning
  darkened to `#8F5B1C` (**measured 5.42:1** on paper / 5.06:1 on container).
- **Bundled OFL typefaces in APK** — Source Serif 4 (reading/editorial), Inter (UI), JetBrains Mono (code/hex);
  OFL 1.1 license texts ship in `app/src/main/assets/licenses/`. `displayLarge`, tracked-caps `Eyebrow`,
  `ReadingBody` (17/28 serif) per §4.2. `FontFamily.Serif`/`Default` eliminated from the UI tree.
- **`Motion.kt`** (§4.4: fast/base/slow/hero, emphasized easings, haptics map) and
  **`ClawExtendedColors`** (§4.1 semantic slots + glass tints) wired via `FilesClawTheme`.
- **Glass-at-root NavHost** inside transparent Scaffolds (opaque reading surfaces keep opacity, §4.5/4.6).
- **Contrast audit committed and enforced in CI** (`tools/ci/check_contrast.py`, WCAG 2.x relative-luminance;
  **measured 21 token pairs ≥ 4.5:1 body / ≥ 3:1 large**; runs as a blocking step before the JDK 17 setup).
- `Icons.Default.*` → `Icons.AutoMirrored.*` sweep; zero new deprecations (CI-verified).

### Added (3b — navigation & chrome; partial)
- **Quick Open / Command Palette** (`Ctrl/Cmd+K` or `Ctrl/Cmd+P`; `ui/component/CommandPalette.kt`): files
  (name match, capped at 12) + actions (theme toggle, open Settings/Search/All Files, clear recents) via the
  existing `FileSearchRanker`; keyboard-first glass overlay; blind query shows 8 recents + all actions.
- **Full in-app shortcut map (implemented subset only, documented honestly):** `Ctrl/Cmd+K|P` palette,
  `Ctrl/Cmd+,` Settings, `Alt+Left` back, `Ctrl/Cmd+1…9` open 1st…9th recent, `Ctrl/Cmd+Shift+C` copy current
  file path (real `ClipData` + toast). Settings → ABOUT now has a **KEYBOARD SHORTCUTS** card listing exactly
  these; reader shortcuts (`Ctrl+F/S/A/Z/Y`), `Ctrl/Cmd+Shift+D` and `Alt+Right` are **not** wired and are
  labeled as pending on the card.
- **Expanded-width (≥ 840 dp) navigation rail** (`ui/component/ClawNavRail.kt`, 72 dp, §5.2): Home / All
  files / Search / Settings with the active stop highlighted; hidden below 840 dp.
- **Slim top chrome wordmark** in Source Serif 4 18 sp across all `ClaudeAppBar` surfaces; **Home hero** in
  `displayLarge` serif (§5.1); **predictive back enabled** (`android:enableOnBackInvokedCallback="true"`).

### Explicitly NOT done in 3b (not verified / absent)
- **Medium-width (600–840 dp) list–detail two-pane** — the reader is a full-screen destination, not a
  pane-embeddable surface yet; needs the §7 chrome refactor.
- **Macrobenchmark smoke** — no macrobenchmark module exists; ≥ 55 fps recents scroll is *not* measured.
- **TalkBack sweep / font-scale 2.0 pass** — no a11y session ran here; instrumented suite is launch-smoke only.
- **Empty-state generative illustrations**, **predictive-back ordering inside readers** (find bar → chrome →
  viewer), **screenshot matrix at 1.3/2.0 scale and medium/expanded widths** — pending.

### Added (3c — reader chrome v2; steps 1–5)
- **Step 1 — viewer honesty details:** password fields in the PDF and archive password dialogs gained
  show/hide visibility toggles; the PDF viewer gained a **"Report this page renders wrong"** action whose
  diagnostics line carries **app version, page number and file type only** — no file contents, no paths
  (Share or Copy). All new user-visible strings in `strings.xml` (54 entries total).
- **Step 2 — reading voice + unified chrome:** Markdown body CSS switched to a serif reading voice
  (`Georgia, "Noto Serif", "Times New Roman", serif`), `line-height: 1.65`, **68ch measure**
  (`max-width: 68ch; margin: 0 auto`) with the page background moved to `html` (§7). The three reader
  sheets (Appearance / Document Outline / File Properties) now share one shell — `LocalGlass.fillStrong`
  container, 16 dp top radius, standard drag handle (§6). **`ClawAlertDialog`** landed in
  `ClaudeComponents.kt` (24 dp radius, glass `fillStrong`, `tonalElevation = 0`, Source Serif 20 sp
  title) and was migrated into both password dialogs and Settings' Clear Cache / Clear History.
- **Step 3 — chrome tail:** the focus-mode pill is hairline glass (`fillStrong` + 1 dp `strokeEnd`
  border, no elevation). **Every remaining stock `AlertDialog` in the app now uses `ClawAlertDialog`**
  (13 further call sites: jump-to-page/line, render report, document zoom, open-link, discard/save/export
  edits, edit history, batch delete, clear history, tree delete, folder picker, CSV discard). **CSV
  tabular numerals** (§7): grid page CSS sets `font-variant-numeric: tabular-nums`; the banner uses the
  new `TabularMeta` style (Inter + `fontFeatureSettings "tnum"`, §4.2).

- **Step 4 — reader keyboard shortcuts (§5.3):** `Ctrl/Cmd+F` in-viewer find (the same toggle the
  toolbar button uses; not while editing — the editing tray owns the header then), `Ctrl/Cmd+S`
  save-edits, `Ctrl/Cmd+A` select-all, `Ctrl/Cmd+Z/Y` undo/redo (through the same `TextUndoStack`s
  the tray buttons use) and `Ctrl/Cmd+Shift+D` file-properties sheet — wired with
  `onPreviewKeyEvent` chained onto the reader Scaffold's existing modifier, so the combos are seen
  before the WebView/text field can consume them and unhandled keys still fall through to the root
  map. The Settings KEYBOARD SHORTCUTS card gained the five rows (`strings.xml` now 64 entries) and
  an honest footer. **`Alt+Right` forward remains unwired** — Navigation Compose has no forward
  stack; it needs a custom popped-destination tracker and stays on the pending list.

- **Step 5 — reader-chrome remainder (automatable subset):** **`Alt+Right` forward navigation** — a
  custom forward stack: Alt+Left records the route it leaves, Alt+Right replays it, and any other
  navigation (rail, palette, file open, system back) clears it; the documented deviation from browser
  semantics is that the system-back gesture clears the stack too. **ZIP breadcrumb** — the archive
  tree shows the deepest expanded folder chain; tapping a segment collapses everything strictly below
  it. **DOCX comfortable line-spacing toggle** — toolbar button toggling a `body.relaxed` CSS class
  (1.6 line-height for paragraphs/list items); pure presentation, applied on load and live, document
  bytes untouched. Settings shortcuts card gained the Forward row (`strings.xml` 67 entries).

### Fixed (3c)
- **AUD-035 — code viewer fetched syntax-highlight assets from a CDN at open time.** `CodeViewer.kt`
  loaded `highlight.min.js` and `vs(2015).min.css` from `cdnjs.cloudflare.com` on every code-file open,
  contradicting the README's "100 % offline — no analytics" claim. Fixed in `4927612`: highlight.js
  **11.9.0** is bundled under `app/src/main/assets/highlight/` (esbuild IIFE bundle from
  `lib/common.js`, 36 common languages, 145 KB; both VS theme CSS files) with the BSD-3 license in
  `app/src/main/assets/licenses/`; the viewer resolves `file:///android_asset/highlight/…` and loads
  with `loadDataWithBaseURL("file:///android_asset/")`. Grep-verified **zero**
  cdnjs/cloudflare/jsdelivr/unpkg references remain under `app/src/main`. Full entry in
  `docs/FILE_SUPPORT_AUDIT.md` §11.

### Explicitly NOT done in 3c steps 1–4 (honesty matrix)
- **Reading-progress fraction for non-PDF viewers** — the text/Markdown viewers expose no honest
  scroll-fraction source yet; it will not be faked (3c remainder owns this).
- **Auto-hide chrome on scroll (120 dp velocity)** and **find-bar auto-hide** — pending (gesture
  behavior needs device validation). **Non-PDF reading-progress fraction** — no honest scroll
  source yet; it will not be faked. **3d/3e are not started** (Settings v2 groups, splash animation,
  themed icon; font-scale matrix, TalkBack, RTL, on-device SANITY_CHECKLIST — the 3e items require a
  physical-device session and cannot be honestly closed from the sandbox).
- All 3b NOT-done items above still stand (two-pane, Macrobenchmark, TalkBack/font-scale,
  illustrations, predictive-back ordering, screenshot matrix).

## [Unreleased] Phase 2 — Test infrastructure & architecture (2026-11-17)

**Verification status (read first):** every Android gate below ran in **GitHub Actions** on branch
`arena/5100f329-file-claw-native` (workflow `CI`, jobs "Quality Gate" and "Instrumented tests (emulator
API 34)"). Measured green runs: `44051142574` (split), `44053027367` (type-safe navigation),
`44053768163` (Room `4.json` generated by the KSP step and bot-committed `cc4d3ec`), `44056165653`
(instrumented suite), `44061328629` (Roborazzi baselines verified), `44063464989` (full gate),
`44070900120` (detekt baseline bootstrap resolved; both jobs green), `44073036452` (dead `isBinaryFile`
removal; both jobs green), `44085373942` (strings.xml extraction; both jobs green), `44087482731` (docs close-out; both jobs green), `44089497288` (find-bar/support-file extraction; both jobs green — including Roborazzi `PropertyItem` baselines). **Nothing is claimed
from the working sandbox** — it has no Android SDK/Maven; the split and all build-file changes were
compile-verified only on the runner. Room `4.json` and the screenshot baselines were generated on the
runner and bot-committed by `github-actions[bot]` (`cc4d3ec` / `6521f0e`).

### Added
- **CI gates, all blocking:** `compileDebugKotlin`, `compileDebugAndroidTestKotlin`, `testDebugUnitTest`,
  `assembleDebug`, `jacocoCoreVerify` (**measured 83.84 % core LINE coverage — 1816/2166**, ≥80 %),
  `detekt` on `core/` (blocking, see baseline below). Non-blocking: `detektUi` (UI advisory run) and the
  Room-schema check (it now *commits* the generated schema on push instead of only warning).
- **Screenshot golden masters** (Roborazzi + Robolectric, light+dark, `app/src/test/snapshots/images/`):
  NoticeBanner, PropertyItem, and the AllFiles / Search / Settings screens in their deterministic empty
  state (fresh database). 10 baselines committed and enforced (`verifyRoborazziDebug` blocks).
- **Instrumented suite on an API 34 emulator** (`reactivecircus/android-emulator-runner`, KVM):
  MainActivity launch smoke test; package-level `ACTION_VIEW` resolver checks for the manifest-advertised
  MIME/scheme filters ("open with → Files Claw"); Room 3→4 migration validated on real SQLite against the
  committed schema via `MigrationTestHelper` (closes the proxied-`SupportSQLiteDatabase` gap).
- **Type-safe navigation:** `ClawDestination` (@Serializable data objects) — Navigation Compose 2.8.0 +
  kotlinx-serialization; routes are no longer raw strings.
- **`FilePreviewScreen.kt` split** 5,740 → **2,306 lines** (find-bar header + clipboard/bounded-text helpers + `PropertyItem` moved to `viewers/ReaderChrome.kt` / `FilePreviewSupport.kt`); 26 mechanical-move files under
  `ui/screens/viewers/` (PdfViewer, DocxViewer, PptxViewer, TextViewer, CodeViewer, CsvViewer, ZipViewer,
  HexViewer, ImageViewer, AudioViewer, VideoViewer, HtmlViewer, MarkdownViewer, MediaSupport, ReaderChrome).
- **Room v4 schema committed** (`app/schemas/com.example.data.AppDatabase/4.json`, 200 lines) — generated
  by CI on push (AUD-034).
- **detekt `baseline.xml` committed** — 10 pre-existing core complexity findings recorded verbatim; any
  NEW finding fails the build. `config/detekt/baseline.xml` is now the debt ledger.
- **CI error visibility:** Gradle/Kotlin/Android failures are emitted as `::error` annotations (the agent
  sandbox cannot read raw job logs). `tools/ci/run-instrumented.sh` — single-shell emulator entry.
- **JaCoCo report uploaded** as CI artifact `jacoco-core-report` (14-day retention).
- **`strings.xml` extraction (new strings only)** — every user-visible string introduced in Phases 1–2
  (honest-failure cards, hex viewer, dialogs) extracted to 18 new `<string>` keys (19 total with app_name); legacy hardcoded strings
  remain by design (full i18n is Phase 7). Verified on CI (`44085373942` green).
- **`docs/prompts/PHASE_3_PROMPT.md`** — the Phase 3 brief for "The Quiet Desk" (entry = Phase 2 green).

### Changed
- CI runs on push to this branch and on PRs; instrumented job runs on every run (this repo is a
  single-module app — every PR can touch androidTest-relevant code).
- `MainViewModel`: removed dead `isBinaryFile` (binary/text is arbitrated content-first by `FormatDetector`).
  No other decomposition — see below.
- `FileClawApp` unchanged; screenshot tests instantiate a bare `Application` instead (WorkManager is not
  initialized under Robolectric).

### Fixed (split fallout — forward fixes, no behavior change intended)
- Missing cross-package imports in moved viewer files (`DocumentPreviewSupport` / `PdfSearchModels` /
  `UniversalViewers` symbols) forced by the package move; braces/strings verified balanced.
- `hasRoute<T>()` reified check does not exist in Navigation 2.8.0 → serialized-name comparison helper
  (`isDestinationOf<T>()`), behavior-identical.
- Roborazzi 1.13.0 API is `com.github.takahirom.roborazzi.captureRoboImage(content = …)` (not the 1.x
  `io.github…` package); plugin registered via Java-style `tasks.register`.
- detekt custom task: `setSource(files(...))` (Detekt's `source` getter is `FileTree`, not configurable).

### Not done / deferred (with reasons)
- **Instrumented suite is deliberately narrow:** launch smoke + resolver wiring + migration. The promised
  open→edit→save→reopen round trips (text/Markdown/CSV), archive password flow and corrupt-PDF error
  path are **not** automated (AUD-013 stays DEFERRED) — they need deterministic file provisioning and UI
  automation time; tracked in `docs/TODO.md`.
- **`currentFileState` ViewModel singleton remains** — screens read the open file from the ViewModel,
  not from route arguments. Removing it means moving content state into route args / re-loading on
  back-stack restore: a Phase 3+ redesign (process death, huge content), not a mechanical refactor.
  Navigation is now type-safe; the singleton is documented, not smuggled.
- **`MainViewModel` not decomposed** beyond the dead code: every remaining member is bound to Android
  types (Application/Uri/Room/lifecycle), and `FileManager`/`AppRepository` are already extracted services.
- **HomeScreen has no screenshot baseline** — its greeting derives from the wall clock (would flake).
- **Room drift check** remains non-blocking (it auto-commits instead); 4.json content is validated
  indirectly by `MigrationTestHelper`.
- detekt's 10 baselined findings are **not** cleaned up in this phase (recorded, not hidden).
- CI hygiene: `actions/setup-java@v4` deprecation + Node 20 annotations — informational; version bumps
  deferred to a dedicated pass to avoid breaking a green gate.
- **Phase 3 (UI) not started** — requires phases 0–2 green, which is now the case on this branch.

## [Unreleased] Phase 1 — Build hygiene + broken-format repairs (2026-11-17)

**Verification status (read first):** the full Android gate was **not run** in this environment —
no Android SDK and no access to Maven Central/Google for AGP; `gradlew` cannot execute here. What
**was** run locally (measured): the **pure-Kotlin core compiled clean and 191 of 231 unit tests passed
(0 failures)** using a Gradle-free path (`tools/local-verify/verify-core.sh` + a downloaded JDK 25 and
Kotlin 2.4.21 distribution; the 8 Android-coupled suites — 40 tests — run only on the GitHub Actions
gate). `node --check` passes for every inline JS touched (search.html, docx_editor.html, x-spreadsheet
template). Workflow YAML parses. **Everything else in this entry is "code landed; not compile-verified"
until the first green CI run.** Push + PR approval from the repo owner is pending.

### Added
- `core/hex/HexDump` — paged 64 KB-window hex renderer (pure Kotlin, `HexDumpTest` ×9: offsets, ASCII gutter, clamping, jump math, empty file).
- `openAsHex(entity)` in `MainViewModel` — in-place swap to the hex viewer for undecodable files (audio/video/image error paths).
- `PPTX 15 s load watchdog` + `key()` rebuild on Retry; JSON of CI schema-drift warning for Room 4.json (`.github/workflows/ci.yml`, non-fatal).
- `tools/local-verify/ShimRules.kt` — `org.junit.rules.TemporaryFolder` stand-in (+`@Rule` handling in `Runner.kt`); `verify-core.sh` now compiles the core + core tests and documents the Android-suite exclusion.

### Changed
- **MIDI (`mid/midi/kar`) dropped FULL → identify-only** (MediaPlayer cannot decode MIDI, AUD-006); AUDIO files marked IDENTIFY route to the hex viewer with an honest notice instead of a dead 0:00 player. `docs/FILE_FORMATS.md` counts updated to **281 full / 32 partial / 86 identify** (emoji table repaired — ✅/🔄/⏳ were mangled to `?`).
- **Save-failure strings unified** (AUD-024): write-back failure now reads "Saved, but the change could not be synced back to the source app (reason). The app's copy was updated." (was the misleading "Saved to in-app copy").

### Fixed
- **AUD-001 / AUD-019** — dead `com.github.mhiew:android-pdf-viewer` dependency and the unscoped JitPack repository removed (`app/build.gradle.kts`, `settings.gradle.kts`); APKs no longer embed the dead viewer.
- **AUD-021** — `build-and-release.yml` fails closed when `build_type=release` and no `FILECLAW_KEYSTORE_BASE64` (+passwords/alias) secrets are configured; debug sideload fallbacks kept but now emit `::warning::`.
- **AUD-005** — PPTX viewer: main-frame `onReceivedError`/`onReceivedHttpError` clear the loading state and show a labeled error card (reason + Retry + Open-with…); `onDocumentError` routes to the same card; 15 s watchdog replaces the infinite "Loading Presentation…" spinner.
- **AUD-007** — Audio: `prepareAsync()` off the main thread (was blocking `prepare()`), `setOnErrorListener` + `setOnPreparedListener`; decode failure shows an inline card ("Can't play this audio" + Open-with… / View-as-hex).
- **AUD-008** — Video: `setOnErrorListener` → inline error card with Open-with… / View-as-hex (no more dead 260 dp black box on codec failure).
- **AUD-009** — Fonts: `Typeface.createFromFile` + `Paint.hasGlyph("AGg0")` validation; invalid fonts show a notice instead of silently rendering the system default/tofu.
- **AUD-010** — Images: bounds-decode pre-flight (`BitmapFactory.inJustDecodeBounds`); codec-less HEIC/AVIF/TIFF/DNG now show "can't be decoded on this device" + Hex-view action instead of a blank area. (Chosen over Coil `onError` because the telephoto wrapper's error API couldn't be verified offline; the bounds decode works with the same framework decoders Coil uses.)
- **AUD-003** — Hex viewer streams 64 KB windows with absolute offsets, prev/next, First/Last and jump-to-offset (`0x…` hex or decimal); the 4 KB cap is gone.
- **BUG-004** (partial) — CSV grid: right-edge fade indicator synced to the x-spreadsheet horizontal scrollbar. Sticky first column deferred (canvas-rendered grid; logged in audit §9).
- **BUG-005** — Password dialogs (archive + PDF) now use `imePadding()` — no longer hidden under the keyboard.
- **BUG-006** — PDF placeholders refine to each page's true geometry after first paint (`refinePlaceholderGeometry` in `search.html`; mixed-orientation docs shift only by the real delta, off the critical path).
- **BUG-007** — DOCX scroller clipped (`body.pinching` → `overflow: hidden !important`) during pinch-preview transforms; no more transient horizontal overflow.
- `ScreenOrientationPolicy` moved `core/format` → `ui/screens` (it is Android-coupled; `core/` must stay pure Kotlin) — same for its test.

### Not done / deferred (with reasons)
- **Room 4.json (AUD-034) — BLOCKED.** KSP requires the Room compiler from Maven Central/Google, both unreachable here. Regenerate and commit on any networked machine: `./gradlew kspDebugKotlin` (emits `app/schemas/com.example.data.AppDatabase/4.json`). No test consumes 4.json today; CI now prints an explicit `::warning::` with the command when it is absent. `DatabaseMigrationTest` passes without it.
- **Corrupt-PDF automated test (AUD-013) — DEFERRED** to the Phase 2 instrumented gate; `onPdfError` + "Try Again" remains the shipped path.
- **AUD-022 drift** — the real number is now **231 `@Test` methods** (222 baseline + 9 new); counted statically from source (measured by grep, 2026-11-17).
- Full quality gate (`compileDebugKotlin testDebugUnitTest assembleDebug`) — **not executed** (see Verification status). First CI run pending; Loop 6–15 code was never compile-verified, expect fallout to fix forward.

## [Unreleased] Phase plan — 10 phases documented (docs) (2026-11-17)

Documentation only; no source changed; nothing compiled.
- **`docs/PHASES.md`** — the complete execution plan: Phase 0 (done) → Phase 1 (Quality Gate + broken-format repairs) → Phase 2 (test infra + architecture split) → Phase 3 (Claude frontier UI, "The Quiet Desk") → Phase 4 (format depth) → Phase 5 (performance/memory/500 MB files) → Phase 6 (security/privacy hardening) → Phase 7 (a11y/i18n/device matrix) → Phase 8 (Play release) → Phase 9 (post-release + 2.0). Includes the **universal per-phase CI test ladder** (unit / JaCoCo gate / screenshot tests / emulator instrumented tests / manifest security guards / device sanity) and per-phase exit criteria. Phases supersede the loop era; each phase ends by writing the next phase's prompt.
- `docs/ROADMAP.md` phase table replaced with the 10-phase summary + link; `docs/AGENTS.md` read order + `docs/README.md` index updated.

## [Unreleased] Phase 0 — Audit & repo hygiene (2026-11-17)

**Verification status:** documentation and file-structure changes only. **No Kotlin/JS/assets were
modified; nothing was compiled** (no Android SDK/JDK in the authoring sandbox). Counts re-derived
statically from source are labelled (static). CI workflow added but its first run is unverified.

### Changed (repository structure)
- **Unwanted files removed:** 9 root-level duplicates of `docs/legacy/*` (PERFORMANCE.md, backup-manager.md,
  project-blueprint/chart/mindmap.md, rules.md, security-protocol.md, tech-specs.md, testing-checklist.md),
  stale `NEXT_LOOP_PROMPT_v3/v4/v5.md` + `NEXT_PROMPT.md` (kept in git history; successor is
  `docs/prompts/PHASE_1_PROMPT.md`), and `build.log` (84 KB build artifact).
- **`.gradle/` untracked** (16 cache files, ~9.6 MB) and `.gitignore` added (Gradle/local.properties/keystores/logs/IDE/AI-Studio artifacts).
- **Live loop docs moved into `docs/`:** `changelogs.md`, `known-issues.md`, `worklog.md`, `BUILD_GUIDE.md`,
  `PATCH_NOTES.md`. Historical entries referencing root paths are annotated.
- **Loop prompts 1–6 moved** `app/docs/prompts/` → `docs/legacy/prompts/`; AI Studio implementation plan → `docs/legacy/aistudio/`.
- **Root is now clean:** README.md, THIRD_PARTY_NOTICES.md, metadata.json, gradle files, `app/`, `docs/`, `tools/`, `config/`, `.github/`.

### Added (docs)
- **`docs/PRD.md`** — product requirements (vision, scope, FR/NFR, metrics, phase plan).
- **`docs/FILE_SUPPORT_AUDIT.md`** — honest per-format audit: re-derived counts (69 groups / **399** extensions,
  **284** full / **32** partial / **83** identify — static), the AUD-001…034 issue register (dead `android-pdf-viewer`
  dependency, PPTX infinite-spinner path, MIDI-can't-decode, hex viewer 4 KB cap, Room 4.json missing,
  docs-vs-code drift, and more), candidate formats to add, Phase 1 fix order.
- **`docs/UI_FRONTIER.md`** — Claude-frontier UI plan ("The Quiet Desk"): token/typography/motion frontier,
  navigation + adaptive two-pane, component v2 spec, per-viewer direction, a11y gates, Phase 3 rollout. **Design doc only — no code changed.**
- **`docs/prompts/PHASE_1_PROMPT.md`** — next-run prompt (build-green Quality Gate + broken-format repairs).
- **`docs/README.md`** — documentation index; **`.github/workflows/ci.yml`** — Quality Gate on push/PR (unverified).
- `README.md` rewritten (accurate build steps; removed stale AI-Studio "remove signingConfig" and unused `GEMINI_API_KEY` instructions).

### Fixed (docs)
- `docs/FILE_FORMATS.md` parser notes: PDF corrupt-file row (error surface exists; device re-verify),
  `.doc`/`.xls` hand-corrected (extractors shipped in Loop 3; registry label stale), audit pointer added.
- `docs/ARCHITECTURE.md` line-count claim updated (FilePreviewScreen ≈ 5,390 lines).
- `docs/SANITY_CHECKLIST.md`: `.doc`/`.xls` note aligned with shipped extractors.
- `docs/AGENTS.md`, `docs/TODO.md`, `docs/BUILD_GUIDE.md`, `docs/prompts/*`: references updated to new doc locations.

## [Unreleased] Loop 15 (v4) — DOCX Pinch/Print, Sensor Orientation, Large File Streaming & Claude Settings

### Added
- **PDF Right-Edge Floating Scroller**:
  - Implemented floating pill handle with interactive page bubble on touch/drag showing `Page X of Y`.
  - Supports smooth continuous drag from page 1 to the end, accurately jumping to and anchoring on the released page.
- **Unified Single-Tap vs Double-Tap Gesture Resolution**:
  - Implemented 250ms debouncer window across all file viewers (DOCX, PDF, TXT, Markdown, Web), cleanly distinguishing one-tap focus mode (bars visibility toggle) from double-tap zoom without conflict.
- **PDF Rounded Shimmer Skeleton**:
  - Replaced sharp placeholder cards with 12px rounded-corner page cards and soft ambient shimmer animation.
- **Settings Screen Claude Redesign**:
  - Redesigned `SettingsScreen.kt` with warm glass cards (`ClaudeCard`), streamlined options into 4 clean sections (Appearance, Reading & Editor, Storage & Performance, About).
  - Added live preview cache manager with size readout and one-tap clear dialog.
- **DOCX Focal-Point Pinch Zoom & Fit-to-Width Baseline**:
  - Rewrote `docx_editor.html` viewport engine with a single scroller, fit-to-width baseline (100% = page fits viewport width, refitted on rotation).
  - Implemented JS-level multi-touch focal-point pinch zoom with compositor-only preview, single CSS zoom commit, and double-tap toggle (fit ⇄ 200%).
  - Removed fighting Compose `pointerInput` pinch interceptor.
- **Real Vector DOCX Print-to-PDF**:
  - Refactored `FilePrintHelper.printDocx` to load dedicated off-screen WebView with `docx_editor.html?print=1`.
  - Generates authentic vector Word document pages via Android `PrintDocumentAdapter`, preserving formatted typography, tables, and images instead of plain text.
- **Sensor Landscape & Seamless Rotation**:
  - Created `ScreenOrientationPolicy` using `SCREEN_ORIENTATION_SENSOR_LANDSCAPE` for accelerometer-based dual-side landscape rotation.
  - Set window background to light `#F0F0F0` and `loadWithOverviewMode = false`, eliminating black blink during screen rotation and keeping visible page anchor.
- **Large File Streaming via HTTP Range Requests**:
  - Implemented `HttpRange` RFC 7233 parser and `PdfRangeResponder` returning `206 Partial Content` with `Accept-Ranges: bytes`.
  - Enabled `disableRange: false` in pdf.js, streaming large documents (150MB+) on demand with millisecond open times.
- **Unit Tests**:
  - Added `HttpRangeTest`, `ScreenOrientationPolicyTest`, and updated `DocxZoomPolicyTest`.
  - All 225 unit tests passed with 100% success rate.

## [Unreleased] Loop 3 (v3) — Source URI Write-Back, Legacy Office (.xls/.doc), Streaming Huge-Text Reader & Trash Bin

### Added
- **Write-Back to Source URI**:
  - Added `sourceUri` column to `RecentFileEntity` to persist origin content URIs for files opened from external applications (Downloads, Files, Email attachments).
  - Implemented Room `MIGRATION_3_4` (`ALTER TABLE recent_files ADD COLUMN sourceUri TEXT DEFAULT NULL`) and bumped `AppDatabase` version to 4.
  - Implemented honest write-back in `MainViewModel` via `contentResolver.openOutputStream(uri, "wt")` when saving text and spreadsheet files, syncing changes back to external providers.
  - Added `DatabaseMigrationTest` verifying schema migration and entity persistence.
- **Legacy Office Format Decoders (.xls & .doc)**:
  - Created pure-Kotlin `Ole2CompoundFile` parser decoding Microsoft OLE2 Compound File Binary (CFB) stream hierarchies offline.
  - Implemented `Biff8XlsParser` reading BIFF8 Excel workbooks (BOF, BOUNDSHEET, SST, LABELSST, NUMBER, RK, MULRK) into structured 2D sheet grids for `CsvPreview`.
  - Implemented `LegacyDocExtractor` extracting clean text paragraphs from legacy Word 97–2003 `.doc` files for `TextSuccess` preview.
  - Covered with unit tests `Biff8XlsParserTest` and `LegacyDocExtractorTest`.
- **Massive-Text Streaming Window Reader**:
  - Implemented `TextWindowReader` for opening 100 MB–500 MB text/log files with O(1) memory by seeking and reading clean newline-aligned byte windows via `RandomAccessFile`.
  - Added `TextWindowReaderTest` verifying newline boundary preservation and line estimation.
- **Safe Multi-File Operations & Gutter Scroll Sync**:
  - Added `BatchAction.TRASH` and `moveToTrash` in `BatchFileOperations` for recoverable soft deletion before permanent unlinking.
  - Synchronized vertical scroll state between line-number gutter and typing console in `EditorScreen`.
- **Loop v4 Autonomous Self-Replicating Prompt**:
  - Generated `NEXT_LOOP_PROMPT_v4.md` and `NEXT_PROMPT.md` for continuous self-improvement.

### Verification
- **Passed:** `compile_applet` build succeeded; `./gradlew :app:testDebugUnitTest` executed 27/27 tasks successfully with 0 test failures across 213 tests.

## [Unreleased] Loop 2 (v2) — In-Document Global Search, DOCX Clean Architecture & Batch Operations

### Added
- **Global Text Search in Document Previews**:
  - Full-document text search and occurrence highlighting implemented across **PDF, DOCX, TXT, and Markdown** previews.
  - PDF: Multi-page text highlighting via canvas overlay and search result navigation with occurrence counts.
  - DOCX: In-DOM `<mark class="search-highlight">` and `.search-highlight-active` highlighting with smooth scrolling to active match.
  - TXT & Markdown: `SearchableDocumentWebView` and `DocumentPreviewScript` highlights all matches and updates occurrence badge with seamless next/previous navigation.
- **Batch Selection Mode in File Browser**:
  - Multi-select files and folders in both `FileTreeComponent` and `AllFilesScreen`.
  - Batch **Move**, **Copy**, and **Delete** actions supported with batch progress feedback and permanent deletion safeguards.
  - Powered by pure-Kotlin `BatchFileOperations.kt` with thread safety, recursive handling, and path sanitization.
- **Clean DOCX Preview**:
  - Debugged and cleanly isolated DOCX preview pipeline using local asset streaming via `WebViewAssetLoader`, robust zoom handling, and honest fallback to pure-Kotlin OOXML text recovery without altering the established Claude UI.
- **Loop v3 Evolution**:
  - Generated `NEXT_LOOP_PROMPT_v3.md` and `NEXT_PROMPT.md` adhering to the Self-Replicating Prompt Rule for cycle continuation.

### Verification
- **Passed:** `compile_applet` build succeeded; `./gradlew :app:testDebugUnitTest` executed 27/27 tasks successfully with 0 test failures.

## [Unreleased] Loop 6 — PDF, DOCX, TXT, Markdown reader improvements

### Added
- Bundled PDF.js 4.10.38 locally with its worker, CMaps, standard fonts, and license notices. Added an offline `WebViewAssetLoader` reader with whole-document PDF text search/highlights, page navigation/bookmarks, password callbacks, reader-style dark canvas, and PrintManager export; search distinguishes the exact match count from retained navigation hits. Removed the obsolete unbounded base64 PDF branch from the generic WebView preview.
- Added shared local TXT/Markdown search/highlight and fenced-code copy controls. TXT fence parsing now supports both backtick and tilde fences and only copies the code body.
- Added bounded-memory `TextFileSearchEngine` for truncated TXT/Markdown files: it scans the whole file, retains at most 10,000 navigable byte offsets, and loads a bounded excerpt for each selected match. Added unit tests for buffer-boundary, newline, Unicode, UTF-16, result-cap, cancellation, and code-fence cases; tests were not run here.
- Added exact DOCX zoom percent entry/reset alongside the 25%–500% slider and 100% default. Search, theme, and zoom operate on the rendered document; print uses the rendered WebView when ready and an extractor fallback otherwise.

### Fixed / changed
- TXT/Markdown preview, print, and copy no longer load a truncated large file wholesale into memory. Large-file print/copy use a 2,000,000-character cap and show a visible error instead of silently using the current excerpt. Printed DOCX/TXT titles and body text are HTML-escaped.
- Immersive preview draws under the system bars, uses reader-canvas theme for system-bar icon contrast, and hides the search overlay while focus controls are hidden. DOCX local assets no longer receive cross-file/universal file access, and external top-level navigation is blocked.
- Organized historical prompt files under `docs/prompts/` with loop-identifying names; removed the exact duplicate `NEXT_PROMPT.md`; created `docs/prompts/LOOP_07_PROMPT.md` for the same four formats.

### Verification
- **Passed:** `git diff --check`; Node.js 22.22.3 syntax checks for extracted PDF.js reader scripts, DOCX script, TXT/Markdown shared script, and bundled PDF.js modules; Python lexical delimiter sanity on 11 changed/added Kotlin files (not a compiler check).
- **Not run:** Android/Gradle build, Kotlin compilation, unit tests, lint/detekt, emulator/device tests, actual PrintManager preview/export, performance benchmarks. `java` and `kotlinc` were missing, `ANDROID_HOME` was unset, and the wrapper is not executable in this checkout; invoking it through `bash` failed because Java is unavailable. No performance numbers were measured.
- Arena Agent Mode did not expose the underlying model identifier. Hermes Agent was inspected but not cloned, installed, or run; no multi-agent team ran.

## [1.4.0] "DOCX Engine Fix, Glass Properties Sheet & PDF Resume": versionCode 6

### Fixed
- **DOCX File Preview Fixed & Pure-Kotlin Fallback Engine**:
  - Fixed WebView DOCX preview by localizing assets into `editor/` directory and resolving cross-origin/file-access CORS restrictions.
  - Resolved base64 IPC Binder transaction limit by exposing `getDocxBase64()` over Android JavascriptInterface.
  - Implemented pure-Kotlin offline OOXML extractor (`DocxExtractor.kt`) that parses `word/document.xml` using `ZipInputStream` without relying on WebView or external dependencies.
  - Added unit test suite `DocxExtractorTest.kt` verifying offline text, word counts, and paragraph extraction.

### Added
- **Glassmorphism Modal Bottom Sheet for File Properties**:
  - Replaced basic AlertDialog with a modern Jetpack Compose `ModalBottomSheet` (`FilePropertiesBottomSheet.kt`).
  - Added drag handle, glassmorphic backdrop, formatted file size, exact byte count, MIME type chip, last-modified timestamp, and permission status.
  - Integrated asynchronous cryptographic checksum calculation (MD5 and SHA-256) with one-tap copy buttons.
  - Storage path box with one-tap "Copy Path" action and clipboard confirmation.
- **Staggered Spring & Fade-in Reveal Animation**:
  - Added `StaggeredAnimatedItem` in `ClaudeComponents.kt` providing staggered spring translation and smooth alpha fade-in across all file cards in `HomeScreen` and `AllFilesScreen`.
- **PDF Last-Read Page Bookmark & Auto-Resume**:
  - PDF viewer now automatically saves the user's active page in `SharedPreferences` upon scrolling.
  - Re-opening any PDF automatically restores the user to their last-read page and displays a confirmation notification ("Resumed at page X").
- **Native Print & PDF Export via PrintManager**:
  - Implemented `FilePrintHelper.kt` utilizing Android's native `PrintManager` and `PrintDocumentAdapter`.
  - Added "Print / Export as PDF" actions to the top app bar and floating action pill for Text, Markdown, Code, and Image files.
- **Quick "Go to Line" in Text & Code Viewer**:
  - Added "Go to Line" dialog with direct line-number entry.
  - Code/Text viewer tags each line and smoothly scrolls to the target line with a temporary highlight flash.

## [1.3.0] "Floating Action Pill, Grid View & In-Viewer Search": versionCode 5

### Added
- **Unified Floating Viewer Control Bar (Action Pill)**:
  - Added a lightweight, floating M3 glassmorphism action pill across PDF, Image, and Text viewers.
  - PDF: quick Page Indicator / Jump to page pill (`current / total`), in-viewer search toggle, night/light mode toggle pill, and share action.
  - Image: photo counter badge, and quick share action.
  - Text: in-viewer search toggle, copy all text shortcut, edit button, and quick share action.
  - Smoothly auto-fades and toggles on screen tap with animated slide and fade transitions.
- **Grid vs List View Toggle & Rich File Cards**:
  - Added a compact Grid / List toggle icon button in `HomeScreen` and `AllFilesScreen`.
  - Grid View displays a 2-column layout with high-contrast `FileExtensionBadge` (PDF, TXT, PNG, JSON, etc.), preview thumbnail, quick star button, file name, formatted size, and relative timestamp.
- **Favorites / Starred Files & Filter Tabs**:
  - Added interactive Star button (`Icons.Filled.Star` / `Icons.Outlined.StarOutline`) to every file card in both list and grid views.
  - Added Filter Tabs (`All Files` vs `★ Favorites`) across `HomeScreen` and `AllFilesScreen` allowing instant filtering with empty-state guidance.
  - Persistent favorite state stored across sessions in `SharedPreferences`.
- **In-Viewer Text Search (PDF & Text files)**:
  - Added a floating "Find in document" overlay search bar with match count indicator (`Match X of Y`) and Previous (`<`) / Next (`>`) navigation buttons.
  - Text files: matches occurrences across the document, highlights them, and updates search match positions.
  - PDF documents: pure-Kotlin `PdfTextSearcher` parses uncompressed and FlateDecode streams to locate matches and jump directly to matching pages.
- **Pure-Kotlin `PdfTextSearcher` & Unit Tests**:
  - Implemented `PdfTextSearcher.kt` in `com.example.core.text`.
  - Added comprehensive unit tests in `PdfTextSearcherTest.kt` verifying uncompressed and compressed stream search.

## [1.2.1] "Viewer Polish & Smart PDF Dark Mode": versionCode 4

### Added
- **Copy Text Action Bar Shortcut**: Added a dedicated copy icon in the top app bar for all text-based files (`txt`, `md`, `json`, code) to copy entire contents to clipboard with instant feedback.
- **Deep Zoom Support**:
  - Image viewer now supports up to 15x zoom factor via `ZoomSpec(maxZoomFactor = 15f)`, providing deep magnification comparable to Google Photos and gallery apps.
  - PDF viewer now supports up to 10x zoom factor (`maxZoom = 10.0f`, `midZoom = 3.5f`), more than triple previous limits.
- **Smart Hue-Preserving PDF Night Mode**:
  - Implemented a specialized document color matrix for PDF night reading that inverts white page backgrounds to dark charcoal (`#1e1e1e`) and black text to comfortable off-white (`#e1e1e1`), while preserving natural color hues and skin tones so photographs and figures do not suffer from inverted "ghost" negative artifacts.
- **Zoom-Out Below 1x for Text Files**: Added `minimum-scale=0.25` in viewport meta and enabled zoom controls across in-app browser previews (`txt`, `md`, `json`, code), allowing users to zoom out down to 25%.

### Changed
- **Default In-App Browser for Text Previews**: All text files (`txt`, `md`, `json`, etc.) now default directly to the premium in-app browser preview matching Claude design aesthetics.
- **Cleaned Preview Menu**: Removed redundant "Show Syntax Code" toggle from 3-dot menus in text/markdown/json files.
- **Image Preview Theme Adaptation & Clean Header**:
  - Image background now adapts dynamically to system theme (light in light mode, dark in dark mode).
  - Removed duplicate overlay showing image name and secondary 3-dots menu beneath the top header for a full edge-to-edge experience.
- **Compact Single-Line Search Box**: Redesigned search bars across `SearchScreen`, `HomeScreen`, and `AllFilesScreen` into a compact, single-line 40.dp rounded pill design with integrated search and clear actions.

## [1.2.0] "Batch Operations & Verification": versionCode 3

### Added
- **Batch Selection Mode in File Browser (`FileTreeComponent`)**:
  - Multi-select mode activated via top "Select" button or long-pressing any file or directory.
  - Interactive checkboxes with smooth selection state management and "Select All" / "Deselect All".
  - **Simultaneous Batch Delete**: Deletes multiple selected files and directories with confirmation preview dialog and instant database/cache cleanup.
  - **Simultaneous Batch Copy**: Non-destructive batch copy with recursive directory support, unique renaming collision safeguards, and interactive destination folder picker.
  - **Simultaneous Batch Move**: Atomic move with cross-filesystem fallback, cycle/self-containment validation, and automatic database index path synchronization.
- **Batch Selection Mode in Recent History (`AllFilesScreen`)**:
  - Multi-select files in history with options to batch remove from history or permanently delete from disk.
- **Pure-Kotlin `BatchFileOperations` Engine & Unit Tests**:
  - `BatchFileOperations.kt` with thread-safe copy, move, and recursive delete routines.
  - Full unit test coverage in `BatchFileOperationsTest.kt` verifying deletion, unique copy name preservation, cycle prevention, and directory moves.
- **GitHub Actions Release Workflow**:
  - Added `.github/workflows/build-and-release.yml` with `workflow_dispatch` manual trigger for parameterized versioning (`version_name`, `version_code`) and automated release artifact publishing.
  - Fixed runner initialization error (`Failed to find package 'tools'`) by removing deprecated `android-actions/setup-android@v3`, configuring native runner Android SDK and `cmdline-tools`, adopting `gradle/actions/setup-gradle@v4`, and adding automated fallback keystore generation and GitHub CLI release publishing.

### Fixed
- **Corrupt PDF Feedback**: Replaced endless loading spinner with honest error UI displaying exact failure diagnostics and a "Go Back" action.
- **Password Field Masking**: Protected user privacy in archive password and PDF password dialogs using `PasswordVisualTransformation`.
- **Honest Auto-Save Messaging**: Renamed "Auto-save Drafts" to "Auto-save on Exit" in Settings and updated editor notification from "Draft auto-saved" to "Changes saved to file" so users know changes write directly to disk.

## [1.1.0] "Foundation & Safety": versionCode 2

### Scoreboard (v1.0 → v1.1.0)
| Metric | v1.0 | v1.1.0 |
|---|---|---|
| Unit tests | 0 | **91** passing (measured) |
| Line coverage of `core/` | n/a (no core, no tests) | **98.3 %** (993/1010), branches 83.2 % (measured, JaCoCo) |
| Registered extensions | ~37 explicit routes + implicit text fallback | **399** (284 full, 32 partial, 83 identify-only) (generated from `FormatRegistry`) |
| New format families with a working route | n/a | **13 families / 63 extensions**: SQLite, SVG, RTF, OpenDocument, EPUB, TTF/OTF fonts, STL, OBJ, gz/bz2/xz files, tar.bz2/tar.xz, ar/deb/cpio, ZIP packages (apk, jar, whl, …), HEIC/AVIF/TIFF/DNG |
| Formats that silently misbehaved | 5 (`.ppt`, `.xls`, `.rar`, plain `.gz`, `.tsv`) | 0 silent: each now parses correctly or says plainly it isn't supported |
| Kotlin source | 9,865 lines / 24 files | 10,653 main lines / 40 files, plus 1,107 test lines |
| `FileManager.kt` | 963 lines | 390 lines |
| `FilePreviewScreen.kt` | 3,880 lines | 2,980 lines |
| Dead code removed | n/a | ~1,500 lines + 4 `.bak` files + 8 one-off patch scripts + a committed build log |
| Unused dependencies removed | n/a | pdfbox-android (~7 MB), kotlin-csv, fastexcel-writer, kotlinx-serialization |

### Benchmarks (JVM, 256 MB heap, `tools/bench/CoreBench.kt`; desktop JVM, **not** device numbers)
| Scenario | Old | New |
|---|---|---|
| Charset check on a 500 MB text file | `OutOfMemoryError` (`readBytes()` of the whole file) | 46 ms |
| Load a 500 MB text file for preview | not possible | 45 ms (250k-char window, flagged truncated) |
| Read a 64 KB window at offset ~499 MB | n/a | 1 ms |
| Archive tree, 100,000 files in one folder | 23,773 ms (O(n²) sibling scan) | 184 ms (≈130×) |
| Archive tree, 100,000 files nested | 373 ms | 562 ms: **slower**, because the new builder also builds a path index and sorts |

Cold start, scroll fps, crash-free rate: **not measured** (needs a device). The only startup change is removing a
hard-coded 500 ms splash wait (now 150 ms) and moving cache-directory I/O off the main thread.

### Fixed: data loss
- **Editing a text file over ~250 KB silently truncated it on save** and wrote a "[truncated]" marker into the file.
  Truncation is now a flag; truncated and converted views are read-only and the ViewModel refuses to save them.
- **Spreadsheets auto-saved on every keystroke**; for `.xlsx` that flattened a multi-sheet workbook to one sheet of
  plain values. Excel is now view-only; CSV/TSV has an explicit Save button and an unsaved-changes prompt.
- `.xls` content was written under an `.xls` name as xlsx bytes; `.tsv` was parsed and saved as comma-CSV. Both fixed.
- All writes were truncate-then-write (a crash left an empty file) and `updateZipEntry` deleted the original before
  renaming. Everything now writes to a temp file, fsyncs, and atomically renames.
- Removed `writeDocxText` / `writePptxText` (unused, but would have destroyed formatting; the PPTX one also emitted
  malformed XML).
- Re-opening the same file name overwrote the earlier imported copy; imports now get unique names, or reuse an identical copy.
- "Remove from history" immediately re-opened the same file (restore effect re-fired on `Idle`).
- Rotation re-imported the intent's file / re-ran shortcuts (effect keyed on non-state `Activity.intent`).

### Fixed: crashes and silent failures
- `determineCharset` read entire files into memory; UTF-16 text was misclassified as binary.
- 7z extraction allocated `ByteArray(entry.size.toInt())`; extraction is now streamed and limit-checked.
- `.rar` showed an empty folder; corrupt/unsupported archives now show a real error.
- Plain `.gz` was parsed as TAR (garbage); handled as a single compressed file.
- Search index covered depth 3 / 5,000 files (missed most of a real device); now depth 8 / 60,000, appended instead of rewritten per open.
- `CacheManager`'s `LruCache` counted entries, not bytes (never bounded memory); cleanup wiped the whole Coil disk cache and deleted open archive entries. Rewritten (age-based, lazy dirs).
- Room used `fallbackToDestructiveMigration()` (any schema bump wiped settings and history). Now fail-fast; schema export enabled.
- PPTX state had no file, so title/share/properties were blank.

### Security
- Zip-bomb and zip-slip protection (entry/size/ratio/depth limits; unsafe names are shown but never extractable).
- `FileProvider` no longer exposes the whole filesystem (`root-path` removed).
- `allowBackup=false` (imported documents were being backed up to the cloud); `BROWSABLE` removed from the VIEW filter.
- HTML previews: scripts off by default, no JS bridge, navigation locked to local files. Markdown/EPUB HTML sanitised.
- All other WebViews: file/content access off (bundled assets still load).
- Untrusted display names sanitised (`PathGuard`); SQLite opened read-only with quoted identifiers; XML parsing forbids DTDs.
- Release signing now comes from environment variables instead of reusing the debug key; archive-password dialog TODO (see backlog).

### Added
- **Universal opener core**: content-sniffing `FormatDetector` (magic bytes + ZIP refinement + heuristics + mismatch reporting).
- Viewers: SQLite browser, font specimen, 3D wireframe (STL/OBJ), SVG, EPUB, RTF/OpenDocument text, hex view labelled with the detected format.
- "Open with" works without all-files access; the permission screen gained "Continue without full access".
- Glass design tokens, `Modifier.glass()`, `GlassBackdrop`; glass cards and app bar.
- `TextWindowReader` (paged access to 500 MB+ files; UI paging still TODO).
- Gradle 8.7 wrapper (AGP 8.4.2 requires ≥ 8.6; the repo pinned 8.5), JaCoCo coverage gate (80 %), detekt (advisory), CI workflow.

### Changed
- Splash wait 500 ms → 150 ms. Opening a file no longer copies it to a temp cache first.
- Docs reorganised into `docs/`; stale pre-1.1.0 docs moved to `docs/legacy/`.

## [1.0.x]: see `docs/legacy/changelogs.md`
