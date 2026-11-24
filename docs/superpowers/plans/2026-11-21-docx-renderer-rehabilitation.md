# DOCX Renderer Rehabilitation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Open DOCX immediately on a white surface with a deterministic readiness transition, stable 100% default zoom, correct four-edge panning, a transparent tray, refined overflow menu, and strict renderer lifecycle handling.

**Architecture:** Keep the existing offline renderer and `ui/preview/docx/` feature. Carry the pending file identity through navigation so the route can select a white DOCX canvas before rendering completes; keep history writes on IO and independent of navigation. Treat readiness as a two-frame JS render signal followed by a WebView visual-state callback; make CSS layout bounds navigable and keep the overlay/tray/menu as Compose UI.

**Tech Stack:** Kotlin, Jetpack Compose, Android WebView/WebViewAssetLoader, local JavaScript, Node `node:test`, API 34 Android instrumentation, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-21-preview-modularization-and-docx-rehabilitation.md`

## Global Constraints

- DOCX user zoom opens at exactly `1.0` (100%) and remains bounded `[0.5, 5.0]`; fit-to-width is an independent rendering scale.
- The loading copy is exactly `Loading document`; its white surface and black circular spinner persist until two `requestAnimationFrame` callbacks and a WebView visual-state callback have completed, then crossfade away.
- White canvas/background must be selected from pending navigation through Compose and WebView; keep the document read-only/offline and the existing size limit.
- Horizontal/vertical scroll at 200% or more must reach both edges; test the actual bundled viewer in WebView instrumentation.
- The tray is a floating transparent overlay; the elevated rounded overflow menu is white with black icons and ~13sp labels and contains no Dark Mode entry.
- Do not claim owner-device root cause or physical-device resolution without reproduction on that exact device/file.
- Use API 34 Actions for Android/Gradle because Java/JDK is unavailable in the sandbox; use Node tests locally.

## Review Focus

- Opening file identity is lost while the state is `Loading`, causing a dark root/window flash → test pending DOCX state and root-canvas routing.
- A stale renderer generation or release callback promotes a superseded view to ready → instrument generation fencing and destruction.
- CSS zoom fits width but makes the leading edge inaccessible or scroll bounds stale → assert all four DOM/document bounds at 200% and at min/max zoom.
- A loading-overlay crossfade starts before Chromium commits pixels → assert ordering of double-frame readiness and visual-state callback.
- A test passes on a synthetic WebView that is not attached/measured like the real host → reuse attached-window fixture helpers and assert rendered pixels/bounds only after layout/visual-state completion.

---

### Task 1: Preserve pending file identity and prove navigation does not wait for history writes

**Files:**
- Modify: `app/src/main/java/com/example/viewmodel/MainViewModel.kt` (`FileContentState.Loading`, `fileOrNull`, `openFile`)
- Modify: `app/src/main/java/com/example/MainActivity.kt` only if route/theme needs pending file state.
- Modify: `app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt` (DOCX identity during loading; coordinated with modularization plan)
- Test: `app/src/test/java/com/example/viewmodel/FileContentStateTest.kt` (or existing package test path).

**Interfaces:**
- Produces: `FileContentState.Loading(file: RecentFileEntity? = null)`; `fileOrNull` returns the pending entity when present. `openFile(fileEntity)` continues emitting navigation before persistence/detection and repository access remains on `Dispatchers.IO`.

- [ ] **Step 1: Write failing state/navigation tests** named `loading state retains the selected file for preview routing` and `open event is emitted before a delayed recent-file write completes`. Assert the pending entity survives state lookup and a deliberately blocked repository write does not block receipt of the preview navigation event.
- [ ] **Step 2: Verify RED** with the focused Gradle JVM test command in CI (local Java is absent); expected failure because loading has no file identity or no test harness proves the ordering.
- [ ] **Step 3: Implement the smallest state-shape change** and pass the selected entity to the loading state; keep recent/history insertion, index mutation, and file work on IO. Update other loading call sites with `null` unless they know the file.
- [ ] **Step 4: Verify GREEN** with focused JVM tests in CI and inspect `openFile` to ensure nav event precedes IO work. Preserve normal route behavior for ZIP-entry and imported files.
- [ ] **Step 5: Commit** as `fix: retain file identity during preview loading`.

### Task 2: Make DOCX root and WebView surfaces white with exact loading UX

**Files:**
- Modify: `ui/screens/FilePreviewScreen.kt` or the extracted `ui/preview/chrome/PreviewContent.kt` route and window/background selection.
- Modify: `ui/preview/docx/DocxPreviewScreen.kt`
- Modify: `ui/preview/docx/DocxWebViewHost.kt`
- Modify: `app/src/main/assets/docx_viewer/index.html`, `render-readiness.js`, `viewer.js`
- Test: `tools/docx-tests/docx-readiness.test.cjs`
- Test: `app/src/androidTest/java/com/example/ui/preview/docx/DocxPreviewInstrumentationTest.kt`.

**Interfaces:**
- Produces: white DOCX canvas in loading/ready/error states; minimal `DocxLoadingOverlay` with exact label and black spinner; readiness callback remains generation-scoped and only fires after JS has completed its two-frame gate and Chromium visual state is committed.

- [ ] **Step 1: Add failing Node contract tests** for exact loading text, white backgrounds, no progress-varying replacement copy, double-RAF before ready callback, and the Compose host visual-state callback. Add an instrumentation assertion for white root/WebView pixels during the initial loading state.
- [ ] **Step 2: Verify RED** using `node --test tools/docx-tests/docx-readiness.test.cjs`; expected failures on current text/color/readiness ordering.
- [ ] **Step 3: Implement white surfaces through the pending/loading/ready/error route** (including activity window/decor background, preview scaffold/top bar, DOCX Compose surface, WebView background, and document canvas) regardless of app dark theme. Use only `Loading document` text and a black circular spinner; fade the overlay only after visual-state completion. Do not use DOM/content readiness as a substitute for visual commit.
- [ ] **Step 4: Verify GREEN** with Node tests and the attached API 34 WebView instrumentation; capture loading/ready screenshots and assert the overlay is not removed before the visual callback.
- [ ] **Step 5: Commit** as `fix: gate docx loading on visual readiness`.

### Task 3: Default to 100% and make zoomed document edges reachable

**Files:**
- Modify: `ui/preview/docx/DocxZoomAndGesture.kt` only if the model needs updated bounds/anchor helpers.
- Modify: `ui/screens/FilePreviewScreen.kt` / extracted preview state to initialize DOCX zoom at `1f` and remove saved-zoom seeding/persistence that contradicts always opening at 100%.
- Modify: `app/src/main/assets/docx_viewer/index.html` and `viewer.js` layout/scroll behavior.
- Create: `app/src/main/assets/docx_viewer/viewport-layout.js` if a pure pan-layout contract is needed by both renderer and Node tests.
- Test: `tools/docx-tests/docx-zoom.test.cjs`
- Test: `app/src/test/java/com/example/ui/preview/docx/DocxZoomAndGestureTest.kt`
- Test: `DocxPreviewInstrumentationTest.kt`.

**Interfaces:**
- Produces: user zoom starts at 1.0; CSS fit scale stays separate; at `userZoom >= 2.0`, scroller `scrollLeft`/`scrollTop` can reach zero and their full maximum and expose page/document geometry at left, right, top, and bottom.

- [ ] **Step 1: Write failing tests**: Node contract that initial user zoom is `1`, limits remain `0.5` and `5`, overwide content alignment starts at the leading edge, and scale updates preserve pinch anchor; Android fixture test sets 2x zoom and verifies both axes at min/max scroll plus first/last page/text visibility.
- [ ] **Step 2: Verify RED** with `node --test tools/docx-tests/docx-zoom.test.cjs`; expected failure for persisted zoom initialization and/or overwide centered-layout bound.
- [ ] **Step 3: Implement minimal layout correction**: remove persisted zoom seed/write so each newly opened document uses 1.0; keep existing coercion; choose a safe leading-edge layout when scaled content exceeds the viewport while centering when it fits; preserve stable scrollbar gutter and anchor math.
- [ ] **Step 4: Verify GREEN** with Node tests and API 34 instrumentation at 200%, 500%, and 50%, checking actual scroll bounds and document-edge visibility (not only source strings).

### Task 4: Render the DOCX tray as a true overlay and refine the overflow menu

**Files:**
- Modify: `ui/screens/FilePreviewScreen.kt` / extracted `ui/preview/chrome/PreviewFloatingTray.kt`
- Modify: `ui/preview/chrome/PreviewOverflowMenu.kt`
- Modify: `ui/preview/docx/DocxTrayAction.kt` only if the explicit DOCX action list needs no-op/label coverage.
- Test: `tools/docx-tests/docx-preview-contract.test.cjs` (or `preview-modularization.test.cjs`)
- Test: `DocxPreviewInstrumentationTest.kt`.

**Interfaces:**
- Produces: tray as a transparent `Box` overlay sibling aligned to bottom-center above the WebView/document, without a `Scaffold` footer/background surface. Overflow menu uses a white rounded elevated surface, black icon tint, ~13sp text, and no Dark Mode action.

- [ ] **Step 1: Add failing contracts** asserting the DOCX tray is composed outside `Scaffold.bottomBar`, no opaque footer surface is present, menu has white/elevation/rounded styling and compact labels, and no Dark Mode entry/toggle callback remains.
- [ ] **Step 2: Verify RED** with the focused Node test; expected failure on the bottom-bar slot and old theme-toggle menu item.
- [ ] **Step 3: Move tray rendering to the overlay layer** while preserving navigation-bar/IME inset and focus-mode animations; style overflow menu and remove the theme toggle only (leave system theme behavior for unrelated formats unchanged).
- [ ] **Step 4: Verify GREEN** with Node tests and API 34 screenshots/pixel or Compose assertions for white menu, transparent document behind the tray, and correct tray click callbacks.

### Task 5: Audit and test renderer generation/lifecycle destruction

**Files:**
- Modify: `app/src/main/java/com/example/ui/preview/docx/DocxPreviewScreen.kt`
- Modify: `app/src/main/java/com/example/ui/preview/docx/DocxWebViewHost.kt`
- Modify: `app/src/main/java/com/example/ui/preview/docx/DocxJsBridge.kt` and `DocxWebViewClient.kt` only if an identified stale callback/render-process path requires it.
- Test: `app/src/test/java/com/example/ui/preview/docx/DocxPreviewViewModelTest.kt`
- Test: `app/src/androidTest/java/com/example/ui/preview/docx/DocxPreviewInstrumentationTest.kt`.

**Interfaces:**
- Produces: release invalidates the active render generation, marks bridge callbacks dead, clears Compose’s WebView reference, stops loads, removes `DocxBridge`, and destroys the released WebView exactly once; stale visual/failure callbacks are ignored.

- [ ] **Step 1: Add failing tests** for stale-generation visual callbacks after invalidation, repeated cleanup idempotence, and releasing a viewer before the renderer callback cannot call the screen’s ready callback.
- [ ] **Step 2: Verify RED** with focused JVM tests and API 34 instrumentation in CI; expected test failure if cleanup/generation handling is not strict.
- [ ] **Step 3: Apply only lifecycle changes identified by tests/source audit**, using existing `AtomicBoolean`/generation ownership rather than adding a new framework. Ensure all WebView operations and destruction occur on the main thread.
- [ ] **Step 4: Verify GREEN** with focused tests, full API 34 instrumentation, and inspection of every factory/update/onRelease/onDispose path.

### Task 6: Record audit evidence and deliver exact-SHA CI/APK results

**Files:**
- Modify: `docs/PROJECT_STATUS.md`
- Modify: `docs/ISSUE_REGISTER.md`
- Modify: `.github/workflows/docx-verify.yml` only if a missing regression check, artifact path, or diagnostic is observed.

**Interfaces:**
- Produces: dated audit entries naming touched files/tests and facts; workflow run URL, exact SHA, conclusion, and APK artifact name/ID/size when available. Physical reproduction status remains explicit.

- [ ] **Step 1: Add the new Node and lifecycle regression tests first** (covered in prior tasks); verify the test commands fail before implementation and pass after.
- [ ] **Step 2: Run locally** `npm test --prefix tools/docx-tests`, `node --check` on every handwritten JS file under `docx_viewer/`, and `git diff --check`. Record Java/JDK absence without attempting Gradle locally.
- [ ] **Step 3: Review/update** `PROJECT_STATUS.md` and `ISSUE_REGISTER.md` with source-backed findings, emulator evidence, and explicit non-claims for owner device/file.
- [ ] **Step 4: Push only the active branch** and monitor the exact `docx-verify.yml` Actions run until completion. If it fails, diagnose, add/fix the regression, and repeat; do not call it complete while pending or red.
- [ ] **Step 5: Verify final output** with `gh run view <run-id>` and Actions artifact metadata; report exact-SHA run URL, APK artifact name/ID/size, and any test limitation.
- [ ] **Step 6: Commit documentation/workflow changes** separately as `docs: record DOCX preview audit and verification` if changes are not already grouped with the corresponding feature commits.
