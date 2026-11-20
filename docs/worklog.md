# File Claw — Worklog

> **Moved 2026-11-17 (Phase 0):** this file lived at the repository root before; historical entries that reference root paths mean the same file at its new location.


Append-only multi-agent log. Newest entries at the bottom.

---

Task ID: LOOP10-SEARCH-IME-ZOOM
Agent: Loop 10 senior virtual agent team (Mira lead; Arjun / Sana / Viktor / Elena / Mateo audits run sequentially by one implementation owner — no parallel sub-agent runtime was available, so all six personas were executed in order and their reports below are individually labeled)
Date: 2026-10-16
Baseline: branch `main`, commit `1a637d1946ac02e3971623f6550e2f15ecc936f3` (v1.3.0). Draft PR #22 (`feat/loop-10-premium-document-preview-fixes` @ `8790d2f`) was inspected per the loop prompt and found to be ~20 commits behind `main` with merge-base `7c37d82`; its Loop 10 commits (DOCX zoom controls, TXT search, reader toolbar) are superseded by the v1.3.0 content already on `main`. Decision: implement on top of current `main` and deliver a patch ZIP for upload (per user instruction the code lands on a new branch `v1.3.1` from `main`; PR #22 stays unmerged and untouched — no duplicate PR was created from here).

Task: Fix the three user-reported viewer problems: (1) move find-in-document into the top header as a Chrome-style find bar; (2) lift the inline-edit tray above the Android keyboard; (3) make DOCX zoom genuinely work and PDF zoom re-render sharply without memory spikes or blank flashes.

Work Log:

- Read-only preflight: `git ls-remote` + full clone + `git fetch` of `refs/pull/*/head`; PR #22 diff inspected (`git diff origin/main...pr/22`); governance docs read (`rules.md`, `changelogs.md`, `known-issues.md`, `docs/TODO.md`, `docs/prompts/NEXT_LOOP_PROMPT.md`, `.github/workflows/build-and-release.yml`, `app/build.gradle.kts`, `AndroidManifest.xml`, `MainActivity.kt`). Test-count baseline re-established from the current checkout: 167 `@Test` methods before this loop (the historical 134/146 figures are stale); no hard-coded count is used anywhere.

- **Arjun — search & navigation audit.** The in-viewer search UI was a floating `AnimatedVisibility` overlay below the header (FilePreviewScreen.kt, "Floating In-Viewer Search Bar Overlay"); `goToNextMatch`/`goToPrevMatch` were scoped inside that overlay, so the header could not host them. Closing the overlay reset state but did not hide the keyboard. All four format pipelines already clear highlights on an empty query (`searchPdf("")` → `clearHighlights()`; `searchWord("")` → `clearSearch()` in both DOCX and generated TXT/MD HTML), so "close = empty query" is the correct clear operation. Confidence: HIGH.

- **Sana — IME audit.** The bottom tray wrapper used only `navigationBarsPadding()`; the activity is edge-to-edge (`enableEdgeToEdge()`, `Scaffold(contentWindowInsets = WindowInsets(0,0,0,0))`) and `AndroidManifest.xml` declared **no** `windowSoftInputMode`, so IME insets were never applied to the tray and the system could pan instead of dispatching insets. Confidence: HIGH. Fix owner: union bottom inset (`WindowInsets.safeDrawing.only(Bottom)`) only while `isEditMode`, plus `adjustResize` as the single inset-dispatch owner.

- **Viktor — DOCX zoom audit.** Traced gesture → `calculateZoom` → `onPinchZoom` → `docxZoomFactor` → `LaunchedEffect` → `evaluateJavascript(setZoom)` → `#output.style.zoom`. Two real defects found. (a) PRIMARY: the pointer-input interceptor fed **every** event into `calculateZoom()`, including the *transition* event where the second finger had just landed while the first still carried single-finger history — that event yields a wildly wrong span ratio, so `applyPinch` slammed the zoom into the 25%/500% clamp on first contact ("percentage chip changes but the document jumps to an extreme / wrong size"). Confidence: HIGH (pure-logic reproduction in `PinchGestureGateTest`). (b) SECONDARY: `onPageFinished` captured the factory-time `zoomFactor`/`isNightMode` values, so a stale zoom could be re-applied after page load; zoom commands arriving while `isWebViewLoading == true` relied on the same stale value. Also confirmed: the pure-Kotlin fallback renderer (`pureKotlinFallback != null`) never runs any zoom pipeline, yet the bottom bar still showed dead zoom-out/chip/zoom-in controls. Confidence: HIGH.

- **Elena — PDF rendering audit.** The overall HiDPI architecture is correct (backing store = viewport × devicePixelRatio × absolute pinch scale from `onScaleChanged`, CSS size fixed; PDF.js-standard `transform` matrix). Defects found: (a) the fixed `pagePixelBudget = 9e6` + `maxRenderRatio = 6` undersample high-density screens beyond ~2× pinch (dpr≈3 phone at 3×: ~12.9M backing pixels needed vs ~5.7M allocated) — visible blur exactly in the practical 2–3× range; (b) `setPdfRenderScale` re-rendered into the SAME canvas after resizing it, destroying the old bitmap before the async render completed — the blank flash after every pinch; (c) pages whose render was in flight during a scale change kept their stale sharpness until the next geometry change; (d) one fixed budget ignored device capability. Prompt rule respected: `memoryClass` is treated strictly as an app-heap signal, never as physical RAM. Confidence: HIGH on (a)/(b) from source math; device dpr values pending verification.

- **Mateo — independent QA review.** Challenged two proposals: re-arming the gate on every finger-count change (adopted — also covers 2→3 finger transitions) and a full tile-rendering rewrite at extreme zoom (rejected this loop — budget raise + swap render covers the practical 2–3× range without an unbounded rewrite; recorded as next-loop candidate).

- Implemented (safe increments, diff inspected after each file):
  - `app/src/main/java/com/example/core/format/PinchGestureGate.kt` (new): pure gesture gate — skips transition events, forwards only confirmed stable two-pointer deltas, disarms on <2 pointers, `reset()` on stream end.
  - `app/src/main/java/com/example/core/format/PdfRenderBudgetPolicy.kt` (new): heap-signal-tiered per-page canvas budget (lowRam 6M / <128MB-class 9M / 128–255 12M / ≥256 16M pixels) with hard clamp [4M, 24M].
  - `app/src/main/java/com/example/ui/screens/DocumentPreviewSupport.kt`: added pure `InViewerSearchNavigation` (`nextIndex`/`prevIndex`, null when no matches, clamp stale indices).
  - `app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt`: new `ViewerFindBarHeader` (own header row while search is open: close, focused single-line field, `current / total` badge incl. `0 / 0`, prev/next, clear; filename + overflow hidden); floating overlay removed (−149 lines); navigation lambdas hoisted to screen scope (header arrows and IME Search action share them); `toggleInViewerSearch` reveals bars + requests focus once; closing hides keyboard and clears state/highlights via each engine's empty-query path; BackHandler now closes find first (deliberate, documented); bottom tray IME-aware only in edit mode via `safeDrawing.bottom` union (no double nav-bar padding, reading mode untouched); DOCX zoom controls hidden when the renderer fell back; `onRendererFallback` wired from both fallback paths; `onPageFinished` reads fresh zoom/theme via `rememberUpdatedState`; `PdfPreview` computes the device budget and pushes `setPdfQualityBudget(...)`; pinch interception uses `PinchGestureGate`; obsolete `scaleIn`/`scaleOut` imports removed.
  - `app/src/main/assets/pdfjs/search.html`: `maxRenderRatio` 6→8; budget mutable via `setPdfQualityBudget()`; `renderRecord` renders into a fresh offscreen canvas and swaps on completion (old bitmap stays visible — no blank flash), skips redundant re-renders at an already-achieved ratio, tracks `renderedRatio`, releases failed offscreen buffers, and does one bounded follow-up when the achievable ratio grew mid-render.
  - `app/src/main/assets/editor/docx_editor.html`: post-render `setZoom(currentZoom)` reapply is now unconditional (idempotent) to close the render/zoom race.
  - `app/src/main/AndroidManifest.xml`: `android:windowSoftInputMode="adjustResize"` on `MainActivity` (single inset-dispatch owner; no `adjustPan`).
  - Tests added (21): `PinchGestureGateTest` (7), `PdfRenderBudgetPolicyTest` (8), `InViewerSearchNavigationTest` (6).

- Checks actually run in this environment (no Android SDK/JDK available — Kotlin compile/test/APK assembly are delegated to GitHub Actions, see PATCH_NOTES_LOOP10.md):
  - `node --check` on every edited inline script block (`assets/pdfjs/search.html` classic + module scripts, `assets/editor/docx_editor.html` script): PASS (exit 0).
  - `AndroidManifest.xml` XML well-formedness parse: PASS.
  - Brace/parenthesis balance scan on all 7 touched/new Kotlin files: PASS.
  - `./gradlew :app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:assembleDebug`: **NOT RUN here** (environment lacks Android SDK 34 + JDK 17 toolchain; per BUILD_GUIDE.md these run in GitHub Actions). Explicitly not claimed as passing.

Stage Summary:
- Root causes: (1) search UI was a floating duplicate below the header with overlay-scoped navigation; (2) tray had no IME inset strategy under edge-to-edge + missing `windowSoftInputMode`; (3a) pinch transition event produced a bogus first zoom delta that slammed into the clamp + stale post-load zoom reapply + dead zoom controls in fallback mode; (3b) fixed 9M-pixel budget undersampled high-dpr screens at 2–3× and re-render resized the live canvas (blank flash).
- Changed files (10): `AndroidManifest.xml`, `assets/editor/docx_editor.html`, `assets/pdfjs/search.html`, `ui/screens/DocumentPreviewSupport.kt`, `ui/screens/FilePreviewScreen.kt`, `core/format/PinchGestureGate.kt` (new), `core/format/PdfRenderBudgetPolicy.kt` (new), `test/.../PinchGestureGateTest.kt` (new), `test/.../PdfRenderBudgetPolicyTest.kt` (new), `test/.../InViewerSearchNavigationTest.kt` (new). Documentation: `worklog.md`, `changelogs.md`, `known-issues.md`, `docs/TODO.md`, `docs/prompts/NEXT_LOOP_PROMPT.md`, `PATCH_NOTES_LOOP10.md`. App version stays 1.3.0 in Gradle (branch name `v1.3.1` + workflow-dispatch version input carry the release identity; no inconsistent numbers introduced).
- Pending device verification (honest list): pinch feel on real hardware (transition-skip must not add perceptible lag), PDF sharpness at 2×/3× on high-dpr screens, IME tray behavior with three-button nav + landscape + large fonts, back-closes-find interaction, low-RAM device render memory. All items are in the PATCH_NOTES device smoke checklist.

---
Task ID: LOOP12-DOCX-SEARCH-PDF-DARK-PERF
Agent: Super Z (main implementation agent, GLM 5.3 Flash session)
Task: Implement FILE_CLAW_LOOP_12 (GLM 5.3 Flash prompt): five bugs — A) DOCX zoom unreliable, B) search highlighting/arrows stop early, C) heavy PDFs & very large pages, D) dark-mode photo negatives, E) evidence-based performance fixes. Deliver an upload-ready patch ZIP.

Work Log:
- Preflight: fresh clone of Rohitjjn/File-Claw-App-Native-Cloned; main @ 67fd7c2 (tag v10.0.4) = merge of PR #26 from branch v1.3.1 — the Loop 10 patch is fully merged (verified byte-identical against the Loop 10 ZIP). Stale draft PR #22 (feat/loop-10-premium-document-preview-fixes) left untouched. Static @Test baseline in this checkout: 167.
- Research (GitHub, cited in PATCH_NOTES_LOOP12.md): pdf.js official viewer mechanisms (PDFPageViewBuffer LRU max(10, 2×visible+1), render visible + 1 ahead, pdfPage.cleanup() on eviction, 30s idle document cleanup, mobile maxCanvasPixels 5MP + 32767 dim cap); Android canvas-size limits (silent blank above device limits); seapeak233/AndroidDocViewer (Apache-2.0, delegates DOCX zoom to WebView pinch — no JS mechanism to copy); docxjs/docx-preview (Apache-2.0, no zoom support, upstream wontfix #109 — scaling is the host app's job); MDN/web.dev (CSS zoom = layout property per change; transform: scale() = compositor-only).
- Bug B (root cause PROVEN by code path): both WebView search effects (DocxPreviewWebView, SearchableDocumentWebView) combined query + navigation into one script; every arrow press re-ran searchWord() which rebuilds highlights and resets the renderer's index to 0, then advanced one step — counter permanently stuck at 2/N ("7 results, arrows reach only 2-3"). Fix: pure InViewerSearchCommandPolicy (DocumentPreviewSupport.kt) + effect rewiring in both call sites; PDF navigation audited and already correct.
- Bug A: two-phase DOCX zoom in docx_editor.html — setZoomDisplay() (compositor-only transform preview, no layout) during pinch, throttled ~30/s from Kotlin (SystemClock-nanos gate + pinchActive state + onPinchSettled callback wired through pointerInput gesture end); setZoom() (CSS zoom commit + wrapper min-width + relative scroll restore) on settle/toolbar/percent/load with a 260ms in-page backstop. Removed dead transition on #viewport-wrapper.
- Bug C: search.html lifecycle rework — lazy placeholder open (page 1 fetched only, official-viewer pattern; was N sequential getPage() calls before onPdfDocumentLoaded), ensurePage() with renderStarting race guard, releaseRecord() now page.cleanup() + proxy drop, resident-window LRU (max 3 canvases, LRU non-visible eviction), setPdfQualityBudget reinterpreted as TOTAL RGBA budget (PdfRenderBudgetPolicy.totalCanvasBytes = perPage x 3 x 4), renderPixelRatio() with area + 8192px dimension caps and 0.05 floor (no more blank oversized pages), 128-page text-index LRU, per-page failure overlay with retry, disposed-record cleanup on document swap, post-render visibility check.
- Bug D: PdfDarkModePolicy (pure, 14 tests) + reader classifier rewrite — invert only near-binary text pages (≤1.5% colorful, ≤12% midtone, ≥55% light); natural+dim for everything else; CSS classes .invert/.natural replace .photo; default before classification is natural (safe).
- Bug E: DOCX bytes streamed via WebViewAssetLoader /docx-src/document.docx (was ~5 base64 copies; bridge fallback kept); onRenderProcessGone handled on PDF (Try Again rebuild via pdfReloadKey) and DOCX (text fallback); AndroidView onRelease destroys preview WebViews (DOCX, PDF, SearchableDocumentWebView); search no longer re-highlights the whole DOM per arrow press.
- Tests added (30 methods): InViewerSearchCommandPolicyTest (7), PdfCanvasDimensionPolicyTest (9), PdfDarkModePolicyTest (14), PdfRenderBudgetPolicyTest +2 (totalCanvasBytes/residency). Expected CI total: 167 + 30 = 197.
- Checks run: node --check PASS for all three inline scripts (search.html classic + module, docx_editor.html); HTML script/style/structure balance PASS; brace/paren balance of FilePreviewScreen.kt matches the pristine baseline (+3/+4 cleaner noise pre-existing, unchanged). Gradle compile/test/assemble NOT RUN (no Android SDK/JDK here) — delegated to GitHub Actions.
- Docs updated: worklog.md (this entry), changelogs.md (new unreleased v1.3.2 section; v1.3.1 marked shipped via PR #26/v10.0.4; sections renumbered 3-10), known-issues.md (BUG-006/007/008 added, Loop 12 watchlist, superseded Loop 8 watch items, 6 new Recently Fixed entries, 2 By-Design tradeoffs), docs/TODO.md (Loop 12 section), docs/prompts/NEXT_LOOP_PROMPT.md (rewritten for Loop 13), PATCH_NOTES_LOOP12.md (new).

Stage Summary:
- Changed/new files (14): assets/pdfjs/search.html, assets/editor/docx_editor.html, ui/screens/FilePreviewScreen.kt, ui/screens/DocumentPreviewSupport.kt, core/format/PdfRenderBudgetPolicy.kt, core/format/PdfCanvasDimensionPolicy.kt (new), core/format/PdfDarkModePolicy.kt (new), test/.../InViewerSearchCommandPolicyTest.kt (new), test/.../PdfCanvasDimensionPolicyTest.kt (new), test/.../PdfDarkModePolicyTest.kt (new), test/.../PdfRenderBudgetPolicyTest.kt, changelogs.md, known-issues.md, docs/TODO.md, docs/prompts/NEXT_LOOP_PROMPT.md, worklog.md.
- Key decisions: safety-first dark mode (documented tradeoff: mixed pages stay light); residency-aware total budget with residentPageWindow=3 mirrored in Kotlin+JS; docx-preview NOT upgraded (bundled version kept, migration unproven this loop); tile rendering NOT shipped (documented staged design).
- Unresolved risks: device behaviors pending (see PATCH_NOTES_LOOP12.md checklist); mixed-orientation PDFs get page-1-estimated placeholders until first approach; DOCX pinch preview can transiently overflow horizontally; CI must confirm 197 tests compile green.

---
Task ID: LOOP14-DOCX-RESTORE-PDF-SCRUBBER-ROTATE-LIGHT
Agent: Super Z (main implementation agent, GLM 5.3 Flash session)
Task: Implement FILE_CLAW_LOOP_14 — restore the real (Word-like) DOCX rendering after the "looks like TXT extraction" regression; PDF: restore right-edge page scrubber, reorder toolbar (rotate/search swap), remove PDF dark mode, fix rotation password/position lifecycle, add skeletons, expand zoom to 8x — preserving the Loop 12 reliability work. Deliver an upload-ready patch ZIP. No merge.

Work Log:
- Preflight: fresh clone of Rohitjjn/File-Claw-App-Native-Cloned; main @ dd4681e (= merge of PR #27 from v1.3.1, tag v10.0.5; Loop 10 + Loop 12 patches verified byte-identical against their delivery ZIPs). Open PRs re-checked via the GitHub web UI: #21 and #22 still open (untouched, no duplicate PR created). Branch `feat/loop-14-docx-pdf-viewer-reliability` created from main (name verified unused). Docs read: worklog.md, docs/prompts/NEXT_LOOP_PROMPT.md (Loop 13), rules.md, tech-specs.md, changelogs.md, known-issues.md, docs/TODO.md, BUILD_GUIDE.md, security-protocol.md. Static @Test baseline: 199 (the historical 167/197 figures are stale).
- Audits (sequential virtual reviews, no parallel runtime): Arjun (DOCX routing), Sana (lifecycle), Viktor (PDF renderer/zoom/scrubber history), Mira (toolbar UX diff), Elena (visual/scrubber recovery), Mateo (tests). No file was rewritten before the root cause was traced.
- **DOCX root cause PROVEN (evidence, not appearance)**: `DocxPreviewWebView` registered the editor under `WebViewAssetLoader` prefix `/editor/` and loaded `/editor/docx_editor.html` (FilePreviewScreen.kt). androidx.webkit hands each handler the prefix-STRIPPED suffix path (`getSuffixPath`), so `AssetsPathHandler` opened asset `docx_editor.html` — which lives at `editor/docx_editor.html` — and the main frame failed on EVERY DOCX; `onReceivedError(isForMainFrame)` then silently swapped in `DocxExtractor.extractText` output (the "Clean Document View" plain text). Introduced by the Loop 12 streaming change (commit 7d11163); the pre-Loop-12 build loaded `file:///android_asset/editor/docx_editor.html` (verified last-known-good via git history). PDF viewer unaffected (its `/assets/` registration is the documented pattern). Confirmed against the official androidx WebViewAssetLoader.java source.
- **Second latent regression found by testing**: the Loop 12 rewrite of `docx_editor.html` dropped the `maxSearchHighlights` declaration while `searchWord()` still READ it — every matching DOCX query threw `ReferenceError` and find never reported results (zero-match queries masked the bug; Kotlin-side Loop 12 search tests could not see it). All five search-engine globals declared again (cap 5000, as in Loop 10 / the TXT-MD script).
- Implemented DOCX fix: `/assets/` registration + `/assets/editor/docx_editor.html` load URL + whitelist update (`/assets/editor/`, `/docx-src/` unchanged semantics); honest recovery state (error-styled banner "Formatted preview failed — showing recovered text", clamped content-free reason, "Retry formatted view" button rebuilding the WebView via a reload key; fallback now entered even with zero recovered paragraphs — no dead surface, no masquerading preview); pure `DocxTextRecovery` policy (DocumentPreviewSupport.kt) + 4 tests.
- Implemented PDF work (Loop 12 reliability preserved; no rewrite of the render pipeline):
  - Scrubber: `PdfPageScrubber` (right-edge rail+thumb, 44dp accessible hit target, drag bubble `n / total`, `PdfRotationPolicy.pageFromFraction` mapping, navigation on release via the shared gotoPdfPage pipeline + `pageJumpToken` so repeated jumps to the same page still fire; visible-page observer keeps it synchronized).
  - Toolbar: Page indicator → **Rotate** (Search's old slot) → **Search** (Night's old slot) → Print → Share; same sizes/spacing.
  - Rotation: `viewRotation` state → `setPageRotation(deg)` in search.html — pdf.js viewport re-derived with `page.rotate + pageRotation` normalized, placeholders swap axes, old canvases kept until the rotated replacements swap atomically (keepCanvas path in updateRecordGeometry), scroll preserved via page + within-page-fraction anchor (captureScrollAnchor/restoreScrollAnchor), highlights stored UNROTATED and mapped at draw time (`rotateNormRect` — an earlier search-time mapping was caught and fixed in the browser test), square-page re-render kick + re-observe. No reload, no WebView recreation, no password re-prompt.
  - Lifecycle: `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|keyboard|uiMode|density|locale|layoutDirection"` on MainActivity — device rotation/theme flip no longer recreates the Activity, WebView, authenticated document, password or zoom (BUG-003 mechanism closed).
  - Dark mode removed: toolbar + menu toggles gone (menu item now DOCX-only), `setPdfNightMode` + body.dark CSS + per-page classifier removed from search.html, `PdfDarkModePolicy.kt` + its 14 tests deleted, `isPdfNightMode` renamed `isDocxNightMode` (DOCX/PPTX keep dark mode; status-bar canvas mapping updated: PDF always light).
  - Skeletons: `PdfOpeningSkeleton` (page-shaped shimmer cards replacing the spinner-only open state), per-page `.skeleton` shimmer in search.html tied to "no usable bitmap" (clears on canvas swap, reappears on LRU release), DOCX open skeleton in docx_editor.html (replaces the lone text line); `prefers-reduced-motion` respected.
  - Zoom 8x: viewport `maximum-scale=8`, `setPdfZoom` clamp 8, `setPdfRenderScale` clamp 8. Budgets, dimension caps, LRU and cancellation untouched.
- Tests: `PdfRotationPolicyTest` (16: normalize/step/rotatedSize/rotateRect incl. 4x round-trip + area preservation + full-page invariant/anchor clamp + scrubber mapping incl. inverse property), `DocxTextRecoveryTest` (4); `PdfDarkModePolicyTest` (14) deleted with the policy. Expected CI total: 199 − 14 + 16 + 4 = 205.
- Browser end-to-end verification (headless Chromium, real files, this environment's substitute for missing Android SDK): DOCX — real minimal DOCX rendered by the bundled docx-preview through the actual editor page: 1 page section, table (6 cells), 11 paragraphs, bold/heading text, `searchWord('the')` = 4 matches, next/prev navigation works, `setZoom(2.5)` applies, dark toggle works, screenshot captured. PDF — real 3-page PDF through the actual reader page: 3 pages, virtualized (offscreen pages stay skeleton until approached), rotation 0→90→180→270→0 swaps geometry correctly (portrait 1256x1625 → landscape 1256x970), scroll anchor preserved, highlight rects re-map per rotation (verified numerically), zoom clamp to 8x (elements scale to fit×8), per-page failure overlay unaffected, zero page errors. Rig: python http.server + agent-browser; staging scripts under /home/z/my-project/scripts/ (outside the repo; no repo pollution).
- Checks run: node --check PASS x3 script blocks; manifest XML parse PASS; brace/paren balance PASS on all touched Kotlin files; grep-verified no leftover `setPdfNightMode`/`isPdfNightMode`/`body.dark`/classifier references. Gradle compile/test/assemble NOT RUN (no Android SDK/JDK here) — delegated to GitHub Actions, per BUILD_GUIDE.md.
- Docs: worklog.md (this entry), changelogs.md (v1.3.3 unreleased section; v1.3.2 marked shipped via PR #27/v10.0.5), known-issues.md (BUG-003 closed, two regression entries, Loop 14 watchlist, dark-mode By-Design entry superseded), docs/TODO.md (Loop 14 section), docs/prompts/NEXT_LOOP_PROMPT.md (rewritten for Loop 15), PATCH_NOTES_LOOP14.md (new).

Stage Summary:
- Changed/new/deleted files (14): AndroidManifest.xml (M), assets/editor/docx_editor.html (M), assets/pdfjs/search.html (M), ui/screens/DocumentPreviewSupport.kt (M), ui/screens/FilePreviewScreen.kt (M), core/format/PdfRotationPolicy.kt (new), test/.../PdfRotationPolicyTest.kt (new), test/.../DocxTextRecoveryTest.kt (new), core/format/PdfDarkModePolicy.kt (deleted), test/.../PdfDarkModePolicyTest.kt (deleted), changelogs.md, known-issues.md, docs/TODO.md, docs/prompts/NEXT_LOOP_PROMPT.md, worklog.md (+ PATCH_NOTES_LOOP14.md in the delivery ZIP).
- Key decisions: `/assets/` registration (documented pattern) instead of a custom `/editor/` handler; rotation as pure viewer state in the loaded document (never a reload); configChanges as the lifecycle root-cause fix; scrubber navigation on release only (no eager renders); dark-mode policy deleted rather than dead-coded; DocxZoomPolicy untouched (DOCX zoom cap stays 5x — the 8x scope is PDF-only).
- Unresolved risks (honest): Kotlin compile/tests/APK pending GitHub Actions; device behaviors pending (Loop 14 watchlist in known-issues.md): rotate feel on slow devices, scrubber over gesture nav on tall screens, configChanges breadth (theme/locale/split-screen) needs a regression sweep, 8x memory probe on low-tier devices. The DOCX fix is deterministic (main-frame 404 removed) but must be confirmed with a real device DOCX open.


---

Task ID: DOCX-ZOOM-VIEWPORT-2026-10-16
Agent: GPT-6 technical lead; source change applied via GitHub repository integration (no parallel agent runtime available in this session)
Date: 2026-10-16
Baseline: `main` at `47e3347cc93b184a1353cddb034f8515036b052a`; new branch `feature/docx-preview-zoom-reliability-2026-10-16`.

Task: Restore reliable DOCX preview zoom and keep the existing File Claw native preview UI. DOCX remains offline, preview-only; no editing engine, cloud renderer, remote asset or dependency was added.

Observed source facts:
- `docx_editor.html` applied CSS `zoom` to `#output`, but also multiplied `#viewport-wrapper`'s minimum width by the zoom factor while the output itself was width:100%. This couples two layout widths and can cause clipping/alignment and incorrect horizontal scroll geometry on phones.
- `commitZoom()` preserved a ratio of global scrollTop to total scrollHeight. Because CSS zoom changes layout height, this can move the reading position to unrelated content.
- `renderDocument()` cleared the live output before `docx.renderAsync()` completed, hiding the existing skeleton and leaving an empty viewport during large document rendering.

Changes made on branch:
- Changed the existing web preview viewport to own horizontal scrolling, use a stable width contract, and avoid double-applying zoom to the wrapper width.
- Changed zoom commit anchoring to preserve the nearest visible page's viewport offset and horizontal scroll position.
- Rendered into a hidden staging element while the existing skeleton stays visible; swaps rendered nodes into the same output only after successful rendering, and retains an error path on failure.
- Kept the existing native Compose toolbar/controls and bundled offline `docx-preview` renderer. No new dependency was added.

Verification status (honest):
- PASS: source targets and baseline inspected; branch created from the recorded main SHA; GitHub contents update returned commit `cd17b59a407f3b3578bd90067be06db213366115`.
- NOT RUN: local clone/build/test/JS syntax check. The shell clone attempt failed because this environment could not resolve github.com; therefore no Gradle or Node result is claimed.
- NOT RUN: CI, APK installation and real-device gestures. Must verify fit-width/default 40%, toolbar zoom, slider, pinch in/out, horizontal scroll at 150–500%, anchor stability, search/next-prev after zoom, skeleton on a large DOCX, and failure recovery before merge.

Residual risk: WebView Chromium CSS zoom and scroll anchoring need runtime verification on Android; the staging render temporarily duplicates rendered DOM memory during the swap window. If the device test exposes memory pressure, measure before changing the renderer or adding dependencies.


## Follow-up: PDF edge page handle, orientation action and repository cleanup — 2026-10-17
- Restored a compact, right-edge draggable page-number tab inspired by the supplied screenshot. It displays the current page and commits a page jump on drag release through the existing navigation callback.
- Rewired the existing PDF tray rotation icon to request portrait/landscape screen orientation from the host Activity; it no longer changes the PDF page's rotation state.
- Removed four tracked Kotlin `.bak` files from app source/theme folders. They were editor backups, not source inputs; canonical Kotlin files remain unchanged.
- Verification: GitHub source updates committed. Android compile/tests, CI and device orientation/gesture checks are NOT RUN in this environment.

---

Task ID: PHASE0-AUDIT-REPO-HYGIENE
Agent: Arena Agent (Phase 0, single session) · Date: 2026-11-17 · Branch: `arena/5100f329-file-claw-native` (branched from 2f6d349 of main)

Task: Phase 0 — unwanted-file removal, repo structure organisation, PRD create/update, file-support audit (supported/broken/candidates), UI-frontier design plan (docs-only), and the Phase 1 prompt. No Kotlin/JS/asset code changes; nothing was compiled (no Android SDK/JDK in this environment) — all verification is static.

Work Log:

- Preflight: read `docs/AGENTS.md`, `docs/TODO.md`, `docs/FILE_FORMATS.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/DESIGN.md`, full source tree; re-derived registry counts statically (69 groups / 399 ext / 284 FULL / 32 PARTIAL / 83 IDENTIFY — FILE_FORMATS.md was accurate).
- File ops: deleted 13 stale/duplicate root files (9 identical to `docs/legacy/*`; stale v3/v4/v5 prompts + NEXT_PROMPT; `build.log`); `git rm -r --cached .gradle` (16 files); `.gitignore` created.
- Moves: `BUILD_GUIDE.md`, `PATCH_NOTES.md`, `changelogs.md`, `known-issues.md`, `worklog.md` → `docs/`; `app/docs/prompts/loop_*.md` → `docs/legacy/prompts/`; `.aistudio/` implementation plan → `docs/legacy/aistudio/`. Root now holds only README/THIRD_PARTY_NOTICES/metadata.json + build files.
- New docs: `docs/PRD.md`, `docs/FILE_SUPPORT_AUDIT.md` (AUD-001…034), `docs/UI_FRONTIER.md`, `docs/prompts/PHASE_1_PROMPT.md`, `docs/README.md` (index). `README.md` rewritten (stale Gemini/signing instructions removed — no Gemini code path exists; app is 100 % offline).
- Fixes (docs): FILE_FORMATS.md PDF/.doc/.xls rows; ARCHITECTURE.md line count; SANITY_CHECKLIST .doc/.xls; AGENTS.md read order; ROADMAP.md Phase plan; prompts/README.md re-indexed (NEXT_LOOP_PROMPT marked superseded, banner added in place); CHANGELOG entry added.
- CI: `.github/workflows/ci.yml` added (unverified first run — call this out in the Phase 1 preflight).
- Honesty notes: `android-pdf-viewer` dependency is dead code (zero imports) — not removed yet (Phase 1, AUD-001) because this phase changes no build files. MIDI is listed FULL by the registry but MediaPlayer cannot decode it — documented (AUD-006), not yet re-labelled. `app/schemas/…/4.json` missing for Room v4 — documented (AUD-034). Everything device-behaviour-related in the audit is unverified on hardware.
- Branch note: user asked for a new branch; the session is pinned to `arena/5100f329-file-claw-native`, so all work landed there (no other branch was created or pushed).

---

Task ID: PHASE-PLAN
Agent: Arena Agent (Phase 0 follow-up, single session) · Date: 2026-11-17 · Branch: `arena/5100f329-file-claw-native`

Task: User asked for the complete phase-by-phase plan for finishing the app, with per-phase CI/GitHub-Actions testing and the total phase count.

Work Log:
- Wrote `docs/PHASES.md`: 10 phases (0–9) — 0 Audit (done) · 1 Quality Gate + broken-format repairs · 2 Test infra + architecture (monolith split, typed nav, emulator instrumented CI) · 3 Claude frontier UI · 4 Format depth · 5 Performance/memory/500 MB paging · 6 Security hardening (manifest guard, SAF-first) · 7 a11y/i18n/device matrix · 8 Play release (staged rollout, rollback) · 9 post-release + 2.0 streaming renderers. Universal CI test ladder + per-phase steps and exit criteria included; phases strictly sequential from Phase 1; each phase ends by writing the next phase's prompt.
- Updated ROADMAP.md (10-phase table + link), docs/README.md (index), docs/AGENTS.md (read order), docs/CHANGELOG.md (entry).
- Verification: docs-only; nothing compiled. Per-phase test strategy is written against `.github/workflows/ci.yml` (unverified first run) and to be extended per phase (emulator AVDs, Paparazzi, macrobenchmarks, manifest guards).

---

Task ID: PHASE-1-QUALITY-GATE
Agent: Arena.ai Agent Mode (single owner; no parallel sub-agent runtime available)
Date: 2026-11-17
Baseline: branch `arena/5100f329-file-claw-native`, HEAD `783cf87` (Phase 0 + PHASES.md). Working tree started clean.

Task: Implement Phase 1 — Quality Gate + broken-format repairs (`docs/prompts/PHASE_1_PROMPT.md`): build hygiene, honest-failure viewers, paged hex viewer, Room schema, the four UI bugs, docs/strings reconciliation. Report with measured vs unverified labels.

Work Log:
- Environment: no JDK/Gradle/Android SDK in sandbox; Maven Central / Google / JetBrains hosts unreachable (000/SSL errors). Acquired JDK 25 via `pip install jdk4py` and a complete Kotlin 2.4.21 distribution via `npm pack kotlin-compiler` (kotlinc-jvm + stdlib); both under /tmp (not committed). Android SDK remains unavailable → full Gradle gate not run; see Verification section.
- AUD-001/AUD-019: removed dead `com.github.mhiew:android-pdf-viewer` and the JitPack repository entirely (nothing else used it).
- AUD-021: `build-and-release.yml` now fails closed when `build_type=release` and no signing secret is configured; debug sideload fallbacks kept with `::warning::`.
- AUD-005: PPTX WebView — main-frame `onReceivedError`/`onReceivedHttpError` clear loading and show labeled error card (reason + Retry via `pptxReloadKey` + Open-with…); 15 s watchdog; `onDocumentError` routes to the same card.
- AUD-006: MIDI (`mid/midi/kar`) registry FULL → IDENTIFY; AUDIO+IDENTIFY routes to hex viewer with honest notice. FILE_FORMATS regenerated counts 284→281 full / 83→86 identify; repair of mangled ✅/🔄/⏳ glyphs in the generated table.
- AUD-007: audio switched to `prepareAsync()` + `setOnPreparedListener`/`setOnErrorListener`; inline "Can't play this audio" card with Open-with… / View-as-hex.
- AUD-008: video `setOnErrorListener` → inline error card (Open-with… / View-as-hex).
- AUD-009: FontViewer validates via `Typeface.createFromFile` + `Paint.hasGlyph("AGg0")`; invalid fonts show a notice.
- AUD-010: image bounds-decode pre-flight (`BitmapFactory.inJustDecodeBounds`) + honest undecodable-image card with Hex-view action (telephoto `onError` API unverifiable offline; documented).
- AUD-003: new pure-Kotlin `core/hex/HexDump` (64 KB windows, absolute offsets, `0x…`/decimal jump parser) + `HexDumpTest` (9 tests). `generateHexDump` (4 KB cap) deleted; `BinarySuccess` now carries `filePath`; `HexViewer` rewritten with First/Prev/Next/Last, window counter, offset input, empty-file state.
- BUG-004: CSV grid right-edge fade (scroll-synced via `.x-spreadsheet-scrollbar.horizontal`); sticky first column deferred (canvas renderer).
- BUG-005: `imePadding()` on both password dialogs.
- BUG-006: `search.html` — `refinePlaceholderGeometry()` patches each placeholder to its true page geometry after first paint (bounded, aborts on document swap, keeps proxies only transiently).
- BUG-007: `docx_editor.html` — `body.pinching` clips `#document-scroller` (`overflow: hidden !important`) during two-finger pinch.
- AUD-002: FILE_FORMATS emoji repair + recount; AUD-022: CHANGELOG recount to 231 `@Test` methods (verified by grep on source).
- AUD-024: save-failure strings unified around sourceUri write-back (both save paths); SANITY_CHECKLIST/TODO/AGENTS/PATCH_NOTES updated; device round trip NOT verified.
- AUD-013: corrupt-PDF automated test deferred (needs WebView/instrumented or device).
- AUD-034: BLOCKED — 4.json cannot be produced without Maven access (KSP needs the Room compiler); `./gradlew kspDebugKotlin` on a networked machine regenerates it. CI emits a non-fatal `::warning::` with the exact command. Nothing consumes 4.json in tests today.
- Moved `ScreenOrientationPolicy` (+test) `core/format` → `ui/screens` — it was Android-coupled in the pure-Kotlin `core/` tree and broke the Gradle-free local verifier.
- Extended `tools/local-verify` with `ShimRules.kt` (TemporaryFolder) + `@Rule` support in `Runner.kt`; `verify-core.sh` now scopes tests to `app/src/test/java/com/example/core` and says why.
- Verification (measured): core main + core tests compiled with Kotlin 2.4.21 targeting JVM 17; **191 tests passed, 0 failed** (includes new HexDumpTest; excludes 8 Android-coupled suites / 40 tests — they run on the CI gate). `node --check` OK for search.html (2 scripts), docx_editor.html, x-spreadsheet inline template. Both workflow files parse as YAML. **Not run:** full `./gradlew compileDebugKotlin testDebugUnitTest assembleDebug` (no Android SDK in sandbox) — CI first run pending; user push approval pending.

Files changed: app/build.gradle.kts, settings.gradle.kts, .github/workflows/ci.yml, .github/workflows/build-and-release.yml, app/src/main/java/com/example/core/hex/HexDump.kt (new), app/src/main/java/com/example/ui/screens/{FilePreviewScreen.kt,ScreenOrientationPolicy.kt (moved),UniversalViewers.kt,DocumentPreviewSupport.kt}, app/src/main/java/com/example/viewmodel/MainViewModel.kt, app/src/main/java/com/example/core/format/FormatRegistry.kt, app/src/main/assets/pdfjs/search.html, app/src/main/assets/editor/docx_editor.html, app/src/test/java/com/example/core/hex/HexDumpTest.kt (new), app/src/test/java/com/example/ui/screens/ScreenOrientationPolicyTest.kt (moved), tools/local-verify/{verify-core.sh,Shim.kt,ShimRules.kt,Runner.kt}, docs/{CHANGELOG.md,TODO.md,AGENTS.md,PATCH_NOTES.md,SANITY_CHECKLIST.md,FILE_SUPPORT_AUDIT.md,FILE_FORMATS.md,worklog.md}.

---

Task ID: PHASE-2-TEST-INFRA-ARCH
Agent: Arena.ai Agent Mode (single owner)
Date: 2026-11-17
Baseline: branch `arena/5100f329-file-claw-native`; Phase 1 landed as `0a88fa2`. Phase 2 closed on `4e1f906`.

Task: Implement Phase 2 — test infrastructure & architecture (`docs/prompts/PHASE_2_PROMPT.md`): emulator instrumented CI, screenshot baselines, FilePreviewScreen split, type-safe navigation, detekt/JaCoCo gates, strings.xml governance, docs; write PHASE_3_PROMPT.md last.

Work Log:
- Mechanical split of `FilePreviewScreen.kt` (5,740 → 2,501 lines; later 2,306 after the find-bar/support extraction) into 26 files under `ui/screens/viewers/` (`PdfViewer.kt`, `DocxViewer.kt`, `PptxViewer.kt`, `TextViewer.kt`, `CodeViewer.kt`, `CsvViewer.kt`, `ZipViewer.kt`, `HexViewer.kt`, `ImageViewer.kt`, `AudioViewer.kt`, `VideoViewer.kt`, `HtmlViewer.kt`, `MarkdownViewer.kt`, `MediaSupport.kt`, `ReaderChrome.kt`, …) — `d39ed5d` + cross-package import fix `d4ed837`.
- Type-safe navigation via `ClawDestination` (@Serializable) — `e50eda5`; Navigation Compose 2.8.0 has no reified `hasRoute` → serial-name comparison `isDestinationOf<T>()` — `1f84f64` (`1f84f64`/`44053027367` green).
- Emulator instrumented CI job (`reactivecircus/android-emulator-runner`, API 34, KVM) via `tools/ci/run-instrumented.sh` — `3f200e0`/`969ae8d`: launch smoke; manifest `ACTION_VIEW` resolver checks; Room 3→4 migration through `MigrationTestHelper` against the committed schema. Green `44056165653` / `44063464989` / `44073036452`.
- AUD-034: CI KSP step generates and bot-commits `app/schemas/com.example.data.AppDatabase/4.json` on push (`1c92712` → bot `cc4d3ec`).
- Screenshot golden masters (Roborazzi 1.13.0 + Robolectric, `@Config(application = Application::class)` — WorkManager is not initialized under Robolectric): light/dark for NoticeBanner, PropertyItem, AllFiles / Search / Settings (empty state). Baselines bot-committed (`6521f0e`); `verifyRoborazziDebug` blocking. Mostly red-then-green cycle `8b4fc71` → `67f227e` → `44061328629`.
- detekt (`core/` blocking, `ui/` advisory via `detektUi`): `346ccad` introduced the step; root-caused a script-compilation failure to `Detekt` being a `SourceTask` (no `source.setFrom`) → `setSource(files(...))` — `3fe2526`; `4dbee40` expanded CI compile-error annotations (they carry the `::error` tails; the sandbox cannot fetch raw job logs). First blocking run found **10 real core findings** (ComplexCondition ×7, CyclomaticComplexMethod ×3 (`TextIO.read` 45, `RtfExtractor.extract` 55), NestedBlockDepth, LongParameterList) → recorded verbatim in `config/detekt/baseline.xml` by CI bootstrap (`feaf3de` → empty-check bug → `a2cb6cb`) and committed `32f660e`; new findings stay red.
- JaCoCo: `jacocoCoreVerify` ≥ 80 % core lines, blocking; measured **83.84 % (1816/2166)**; `jacoco-core-report` artifact uploaded; percentage printed as `::notice` annotation in CI.
- `isBinaryFile` (MainViewModel) deleted as dead code — content-first `FormatDetector` is the only binary/text arbiter — `2d3c2dd` (`44073036452` green).
- Strings governance: every user-visible string introduced in Phases 1–2 extracted to `strings.xml` (17 keys, `4e1f906`); legacy hardcoded strings stay for Phase 7 (i18n).
- Node/`node --check` and brace balance re-verified for touched inline JS/Kotlin where applicable (no inline JS touched in Phase 2).

Verification (all on CI — the sandbox has no Android SDK): green runs `44051142574` (split), `44053027367` (nav), `44053768163` (Room schema), `44056165653` & `44063464989` (instrumented + gate), `44061328629` (screenshots), `44070900120` (detekt bootstrap + full gate), `44073036452` (dead-code removal). Red-then-fixed runs: `44065135157`, `44066582822`, `44067756740` (detekt script compilation), `44068827446` (first blocking detekt findings), `44069879801` (empty-baseline check bug). Pending: strings-extraction run (`44085373942`, in flight at write time — see CHANGELOG verification note).

Notes / scars: the workspace twice restored git HEAD to the branch base while the working tree kept branch content; one commit (`4c6ebf9`) was accidentally parented at `2f6d349`. It was **not pushed** (non-fast-forward); the identical change set was re-parented onto `2d3c2dd` as `4e1f906`. Lesson recorded: after any fetch/merge, assert `git rev-parse HEAD == origin/<branch>` before committing.

Deferred (written reasons in `docs/FILE_SUPPORT_AUDIT.md` §10 and `docs/TODO.md` KNOWN ISSUES #10): corrupt-PDF instrumented test (AUD-013); archive-password flow; save round trips (text/Markdown/CSV); `currentFileState` singleton redesign (Phase 3+); HomeScreen screenshot baseline (wall-clock greeting); 10 baselined core detekt findings; `detektUi` baselining.

### PHASE-2 close-out addendum (2026-11-17, later same day)
- `1b9a901` — extracted the in-document find-bar header (`ViewerFindBarHeader`, 162 lines, now `internal` in `ui/screens/viewers/ReaderChrome.kt`) and the clipboard/bounded-text helpers + `PropertyItem` (`FilePreviewSupport.kt`); `FilePreviewScreen.kt` 5,740 → **2,306 lines**; largest viewer is `PdfViewer.kt` (747 lines). CI green: run `44089497288` (both jobs; screenshot baselines for `PropertyItem` unchanged and passing).
- Docs synchronized to the final numbers (CHANGELOG, §10 audit, PHASES exit criteria, ROADMAP, this worklog).
Task ID: PHASE-3A-FOUNDATIONS
Agent: Arena Agent (Claude)
Date: 2026-11-17
Baseline: branch `arena/5100f329-file-claw-native` @ `85a9423`-descendant (post Phase 2).

Work Log:
- Implemented the §4 foundations: semantic color tier in `Color.kt` (with measured WCAG ratios recorded in `docs/UI_FRONTIER.md`),
  terracotta demoted to accent-only, filled button → SoftTomato (measured 4.80:1 both schemes), light warning → #8F5B1C (5.42:1 on paper).
- Authored `ui/theme/Motion.kt` (durations, emphasized easings, haptics map, reduced-motion contract) and `ui/theme/ClawExtendedColors.kt`; wired
  `Theme.kt` (container slots, LocalClawColors); extended `Type.kt` (displayLarge, Eyebrow, ReadingBody 17/28 serif) with bundled
  Source Serif 4 / Inter / JetBrains Mono (+ OFL 1.1 license texts under `assets/licenses/`).
- Glass-at-root `GlassBackdrop` at the NavHost (transparent Scaffolds); `Icons.*` → `Icons.AutoMirrored.*` sweep; 5 Scaffolds transparent.
- `tools/ci/check_contrast.py`: fixed 8-hex parsing (`0x(?:FF)?…`); **measured 21 token pairs AA-pass**; committed as a blocking CI step
  before the JDK 17 setup in `.github/workflows/ci.yml`.
- Roborazzi baselines re-recorded by the CI bot (`85a9423`); CI run `44128246714` both jobs green. Goldens remain compact/1.0-scale only.

Task ID: PHASE-3B-NAV-CHROME
Agent: Arena Agent (Claude)
Date: 2026-11-17
Baseline: branch `arena/5100f329-file-claw-native` @ 3a-green (`5f1a149` descendant after re-record).

Work Log:
- Added Quick Open / Command Palette (`ui/component/CommandPalette.kt` + root Ctrl/Cmd+K|P toggle): files ranked with `FileSearchRanker`
  (cap 12) and actions (theme toggle, Settings/Search/All Files navigation, clear recents); glass overlay; blind query = 8 recents + actions.
  Two failed CI runs fixed forward (`44132429688` invalid FQ Modifier call; `44133924754` missing `KeyEvent` extension-property imports) —
  final green `44135800812`.
- Expanded the root key handler into the honest shortcut map: K/P palette, comma → Settings, Alt+Left → back, Ctrl+Shift+C → `ClipData`
  path copy (+ toast), Ctrl/Cmd+1…9 → open nth recent. `docs/UI_FRONTIER.md §5.3` reader shortcuts deliberately not wired; Settings shows
  only what works (new KEYBOARD SHORTCUTS card in the ABOUT group, all strings in `strings.xml`).
- Added expanded-width (≥ 840 dp) `ClawNavRail` (72 dp, §5.2) hidden below 840 dp; Source Serif wordmark across `ClaudeAppBar`; Home greeting
  → `displayLarge` serif; `android:enableOnBackInvokedCallback="true"` (manifest, not yet per-reader ordered).
- Visual changes re-baselined with the commit (CI bot `5f1a149`, 10/10 PNGs). CI run `44140293225` both jobs green.
- NOT done (documented, TODO KNOWN ISSUES #11): medium list–detail two-pane (reader not pane-embeddable), Macrobenchmark, TalkBack /
  font-scale 2.0 passes, palette instrumented test, empty-state illustrations, predictive-back ordering inside readers, 1.3/2.0-scale matrix.

Task ID: PHASE-3C-READER-CHROME (steps 1–3 + AUD-035)
Agent: Arena Agent (Claude)
Date: 2026-11-17/18
Baseline: branch `arena/5100f329-file-claw-native` @ 3b docs tail (`1d57cf2`).

Work Log:
- Step 1 (`02f43f0`, fix `92da32a`): show/hide toggles on the PDF + archive password dialogs; PDF-only
  "Report this page renders wrong" dialog (diagnostics = app version + page + file type only; Share/Copy);
  new strings in `strings.xml` (54 entries). CI `44148240751` FAILED (compile: Compose Material icons are
  extension properties, were invoked like functions), fixed forward `92da32a`; green `44150911227` (both jobs).
- Step 2 (`93ea45b`): MarkdownViewer body CSS → serif reading voice, 68ch measure, line-height 1.65
  (background moved to `html`, §7); Appearance/Outline/Properties sheets unified on `LocalGlass.fillStrong`
  + 16 dp top radius (§6); `ClawAlertDialog` (24 dp glass, Source Serif 20 sp title, tonalElevation 0) added
  to ClaudeComponents.kt, migrated into both password dialogs + Settings Clear Cache/History.
  CI green `44154177670` (both jobs).
- AUD-035 (`4927612`): CodeViewer loaded highlight.min.js + vs(2015).min.css from cdnjs.cloudflare.com at
  open time — undocumented violation of the README "100 % offline, no analytics" claim. Bundled highlight.js
  11.9.0 (npm pack → esbuild IIFE from `package/lib/common.js`; 36 common languages; 145 KB) + vs/vs2015 CSS
  + BSD-3 LICENSE under `app/src/main/assets/{highlight,licenses}/`; viewer now loads
  `file:///android_asset/highlight/…` with `loadDataWithBaseURL("file:///android_asset/")`. Grep-verified zero
  cdnjs/cloudflare/jsdelivr/unpkg refs left under `app/src/main`. CI green `44157789487` (both jobs).
  Audit entry: `docs/FILE_SUPPORT_AUDIT.md` §11.
- Step 3 (`6b04113`): focus-mode pill → hairline glass (fillStrong + 1 dp strokeEnd border, 0 elevation);
  every remaining stock AlertDialog migrated app-wide to ClawAlertDialog (13 sites: jump-to-page/line,
  render report, docx zoom, open-link, discard/save/export edits, edit history, batch delete, clear history,
  tree delete, folder picker, CSV discard — FilePreviewScreen/AllFilesScreen/EditorScreen/FileTreeComponent/
  InlineTextEditorSupport/CsvViewer; EditorScreen's now-unused RoundedCornerShape import removed); CSV
  tabular numerals (§7): page CSS `font-variant-numeric: tabular-nums` + new `TabularMeta` Inter/tnum style
  in Type.kt (§4.2). CI `44236906638` green (both jobs). No Roborazzi surface captured changed → no re-record.
- Not done in 3c yet (TODO KNOWN ISSUES #11): reading-progress fraction for non-PDF viewers (no honest
  source; never faked), auto-hide chrome on scroll, Ctrl+F/S/A/Z/Y reader keys, DOCX line-spacing toggle,
  find-bar auto-hide, ZIP breadcrumb.

Environment note (2026-11-18): the authoring sandbox was rebuilt mid-phase (fresh single-branch clone of
`main` @ `2f6d349`); recovered by `git fetch origin arena/5100f329-file-claw-native` + mixed reset onto
`4927612` — worktree and full history verified intact (only the in-flight step-3 edits were unstaged).
No work lost; pushes before and after reconcile exactly.
- Step 4 (`c8d2074` + fix `f7d3b00`, green `44271726453`): reader keyboard shortcuts (§5.3) — Ctrl/Cmd+F
  in-viewer find, Ctrl/Cmd+S save, Ctrl/Cmd+A select-all, Ctrl/Cmd+Z/Y undo/redo, Ctrl/Cmd+Shift+D
  properties, via onPreviewKeyEvent chained onto the reader Scaffold's pre-existing modifier (testTag
  preserved; unhandled keys fall through to the root map). First push failed compile (`44270564728`,
  FilePreviewScreen.kt:1639 — duplicate `modifier` named argument: the Scaffold already ended with
  `modifier = modifier.testTag("file_preview_screen")`; lesson: read the END of long composable argument
  lists before inserting at the head). Settings KEYBOARD SHORTCUTS card +5 rows + honest footer (Alt+Right
  forward still unimplemented — Navigation Compose has no forward stack); strings.xml 64 entries. Goldens
  deleted in the visual commit; CI bootstrap re-recorded 10/10 (bot `459974a`; settings PNGs grew with the
  new rows). Device verification of the key map remains pending (instrumented suite is launch-smoke only).
- Step 5 (`53a2e7f`, green first push `44448030978`): Alt+Right forward navigation (custom forward stack —
  Alt+Left records the route, Alt+Right replays; any other navigation clears it; documented deviation:
  system-back gesture clears too), ZIP breadcrumb (deepest expanded chain, tap a segment to collapse
  deeper folders), DOCX comfortable line-spacing toggle (body.relaxed CSS, setLineSpacing bridge, toolbar
  button, applied in onPageFinished so it survives reloads/retry). Settings card +Forward row; strings 67.
  Deliberately NOT done and why: chrome/find-bar auto-hide on scroll (gesture feel needs a device),
  non-PDF reading-progress fraction (no honest source; never faked), 3d/3e (device session required for
  TalkBack / font-scale / SANITY_CHECKLIST).

Task ID: DEVICE-TEST-ROUND-1 (2026-11-18, from v1.3.0-arena device feedback)
Agent: Arena Agent (Claude)
Baseline: branch `arena/5100f329-file-claw-native` @ `64e1add` (release tag `v1.3.0-arena`).

Work Log (4 device-reported bugs):
- PDF "Failed to fetch": pdf.js fetched the document from INSIDE its worker thread; worker-side
  fetch() is not routed through shouldInterceptRequest on Android WebView, so /search-pdf/ never
  reached the asset loader on real devices (CI emulator never opens a PDF, so this was never seen).
  Fix: import pdf.worker.min.mjs and publish it on globalThis.pdfjsWorker (pdf.js main-thread
  "fake worker" — official mechanism) + useWorkerFetch:false, disableRange:true. Fetch now happens
  on the main thread where WebViewAssetLoader intercepts it.
- DOCX blank white screen (print worked via the native fallback): init-script statements are now
  individually try/catch-guarded so nothing can skip loadDocx (setZoom/setLineSpacing are cosmetic;
  loadDocx always runs), and the page arms a 25 s render watchdog that reports through
  onDocumentError -> the honest recovery UI instead of a silent skeleton. Byte path already had a
  fetch -> JS-bridge fallback.
- Focus-mode chrome animation replaced (§4.4): top bar / bottom tray now settle with
  EmphasizedDecelerate (SLOW_MS enter) and leave with EmphasizedAccelerate (BASE_MS exit) —
  partial slide (1/3 height) + fade + 0.98 edge-anchored scale; the focus pill scales 0.9 -> 1.
  Old FastOutSlowInEasing full-slide tweens removed.
- Photo pinch-zoom glitch: telephoto 0.7.1 does not coordinate with HorizontalPager — panning a
  zoomed image tugged the page sideways. The pager's userScrollEnabled is now locked while the
  visible image is zoomed (> 1.02x, observed via snapshotFlow on contentTransformation) and
  re-enabled at 1x / on page settle.
All four are structural fixes reasoned from the reported symptoms; they are compile- and
CI-verified, device re-verification pending in the next test build.

Task ID: DEVICE-TEST-ROUND-2 (2026-11-20, from v1.3.1-arena device feedback)
Agent: Arena Agent (Claude)
Baseline: branch `arena/5100f329-file-claw-native` @ `b6957b9`.

Evidence gathering (the two tester screenshots were OCR'd + pixel-analyzed in the sandbox —
rapidocr-onnxruntime; no vision): screenshot 1 = DOCX viewer with toolbar (search Q, 125% zoom)
but a body that is 96.6% pure #faf9f5 with ZERO skeleton cards, error text, or spinner pixels;
screenshot 2 = the Android print preview of the same file rendering perfectly (2 pages,
Devanagari, shading/watermark effects). Conclusion: the print path of the SAME html/asset-loader
works on the device; the preview path painted nothing while reporting success, and "still
loading" had no timeout at all.

Work log:
- DOCX recraft: init reduced to the exact print-proven call; CSS-`zoom` engine replaced with
  transform:scale + #zoom-sizer (visual-box sizing, live-sized pinch previews, ResizeObserver for
  line-spacing reflows); renderAsync staging moved to document.body; zero-page and collapsed-
  layout renders reported honestly; Kotlin-supervised load (init re-issued ~4 s/~9 s, honest
  text recovery at ~14 s); JS render watchdog 12 s; loadDocx/renderDocument state machine makes
  Kotlin re-injection a no-op once loading/done.
- PDF scrubber: live Drive-style scrubbing (jump on grab + one emission per page boundary crossed
  during the drag; release only commits if the last live page differs).
- Focus-mode chrome animations: rebuilt on springs (third style) — NoBouncy MediumLow glide in,
  Medium snap out; pill pops with 0.62 damping overshoot.
- apk-release.yml: assembleDebug -> assembleRelease (R8), artifact FileClaw-<tag>-release.apk;
  release build type falls back to debug signing without secrets (pre-existing gradle logic).
- Photo pinch-zoom (round-1 fix) was not re-reported; left unchanged, still not device-confirmed.
All changes are CI-verified only; device re-verification pending in the next test build.
