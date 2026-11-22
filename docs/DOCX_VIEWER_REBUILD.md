# DOCX viewer rebuild and preview-screen modularization plan

**Design status:** architecture approved for implementation by the owner prompt; this file is written before subsystem code changes.
**Repository state checked:** branch `arena/ea756912-file-claw-native`, clean after synchronizing the local checkout to remote `50272452a0f81d8e26ae5155ec9851d912528c97` (`Record DOCX blank preview investigation evidence`). The earlier known implementation SHA `1842560fe886712f77275c90da4c7c4a8678d09e` is an ancestor, not current HEAD.
**Root-cause status:** owner reports normal preview blank while toolbar is usable and print of the same file contains content. The exact file/device are unavailable. No cause is asserted. The earlier hidden-staging/layout hypothesis remains unconfirmed; this rebuild is a reliability/observability redesign, not proof of that hypothesis.

## 1. Current source map and dependencies

### DOCX path today

```text
MainActivity / picker / file browser
  -> MainViewModel.openFile + format detection / 25 MiB policy
  -> FileContentState.DocxSuccess(file)
  -> FilePreviewScreen.kt (5,531 lines; 284,162 bytes)
       owns toolbar, route selection, page/search/zoom/fallback Compose state
       -> DocxPreviewWebView (same file, lines 4,885–5,428)
            -> WebViewAssetLoader: /assets/ and /docx-src/document.docx
            -> editor/docx_editor.html (36,891 bytes; HTML/CSS and inline JS)
                 -> jszip.min.js + docx-preview.min.js + docx_search.js
                 -> fetch ZIP bytes -> docx.renderAsync -> output/layout/zoom/search
            -> anonymous JavascriptInterface / WebViewClient callbacks
       -> DocxExtractor on IO for degraded text recovery
  -> FilePrintHelper.printDocx (separate temporary WebView/bridge/asset loader)
       -> same HTML and renderer in `?print=1` mode; fallback extractor -> PrintManager
```

Preview and print share assets and renderer but not a WebView, host lifecycle, viewport configuration, bridge, or print adapter. A successful print is therefore a useful comparison path, not evidence that normal preview is healthy.

### Full `FilePreviewScreen.kt` top-level declaration / ownership map

Line ranges are from the verified HEAD above and should be refreshed after extraction. Most functions are public only because the monolith currently defines their cross-viewer boundary; preserve callable signatures/behavior while moving them, then narrow visibility when callers are checked.

| Lines | Declarations / responsibility | Main dependencies and callers |
|---|---|---|
| 162–197 | `ViewerScrollRequest`; bounded text read, clipboard and operation-error helpers | `FilePreviewScreen`, `TextLoader`, Android clipboard/toast |
| 198–360 | `ViewerFindBarHeader`, `SearchMatchBadgeRow` | Shared preview toolbar; PDF, DOCX and text search callbacks/state |
| 362–2,558 | `FilePreviewScreen` | MainActivity caller; `MainViewModel.FileContentState`, recent file/settings, file manager; shared toolbar, search/zoom/page state, focus/system bars, edit workflows, print/share, route switch, dialogs and sheets |
| 2,560–2,570 | `PropertyItem` | Properties UI helper; candidate relocation with dialog/sheet, caller check required |
| 2,571–3,028 | `MarkdownPreview`, `PlainTextPreview`, `SearchableDocumentWebView`, `WebAppInterface` | Markdown/HTML generation, local WebView, search/outline/zoom/clipboard bridge; `DocumentPreviewSupport.kt`, `TextLoader` |
| 3,057–3,262 | `CodePreview` | Code viewer state, syntax theme and inline editing presentation |
| 3,263–3,498 | `CsvPreview` | Read-only/editable table state, file manager/viewmodel operations |
| 3,499–3,708 | `ZipPreview`, `NodeWithDepth`, `countTotalFiles`, `buildNodesList` | Archive tree expansion, extraction/password navigation; `FileManager.ZipNode`, `MainViewModel` |
| 3,709–3,795 | `ImagePreview` | Coil image display / image zoom |
| 3,796–4,498 | PDF URI helper, `PdfPreview`, opening skeleton, page scrubber, `PdfWebAppInterface` | PDF.js WebView, budget/search/rotation/navigation state, local handler, PDF-specific bridge |
| 4,500–4,660 | `AudioPlayer`, `VideoPlayer` | MediaPlayer / platform player and lifecycle |
| 4,661–4,768 | `HexViewer`, `formatTime`, `shareFile` | Binary display and share intent; `FileProvider` |
| 4,770–4,884 | `WebViewPreview`, `loadLocalFileOnce` | HTML/code/text local preview and script opt-in security policy |
| 4,885–5,428 | `DocxPreviewWebView` | DOCX WebView lifecycle/asset loader/bridge, search, zoom/pinch, page callbacks, recovery UI; `DocxExtractor`, `DocxPreviewDiagnostics`, `DocxPreviewPolicy`, `ViewerAppearancePrefs`, `FilePrintHelper` |
| 5,429–5,523 | `PptxPreviewWebView` | PPTX route/bridge/assets; explicitly out of DOCX behavior scope, move without redesign |
| 5,524–5,531 | `Context.findHostActivity` | Shared WebView host helper; caller search required before relocating |

Related existing source seams: `MainViewModel.FileContentState` is the route contract; `DocumentPreviewSupport.kt` contains shared generated text/Markdown HTML, search policy/navigation, recovery policy and PDF open helper; `PdfSearchModels.kt` is PDF state; `InlineTextEditorSupport.kt` owns text-edit wrapping/history components; `DocxExtractor.kt` is bounded text recovery; `FilePrintHelper.kt` owns print adapters for multiple formats. Avoid moving shared code into a DOCX package or changing non-DOCX behavior.

## 2. Renderer decision

Keep the currently bundled offline `docx-preview` + JSZip renderer behind a new subsystem boundary for the first rebuild. It is already packaged locally, is called from the current viewer, and passed the prior generated-fixture Android WebView tests. Existing test evidence covers only a synthetic subset (text/Unicode/table/bold/image/orientation/page break/visible viewport); it does not establish owner-file behavior, full OOXML support, Word pagination, or physical-device reliability.

The exact local `docx-preview.min.js` version/source remains unresolved; the bundle hash recorded in the issue register is not matched to checked npm releases. This is a provenance/maintenance risk, not evidence that the renderer caused the blank screen. No alternative is selected: none is shown to improve Android WebView, offline behavior, package size, Unicode/Hindi, sections, images, memory, licensing and maintainability on comparative fixtures. Do not add a network-hosted renderer or dependency. Revisit replacement only if the rebuilt pipeline and owner-device diagnostics isolate a renderer limitation and a local, licensed comparative candidate passes the same corpus.

## 3. Target architecture

Use the existing single Gradle module and Kotlin/Compose package conventions. DOCX UI lives under `com.example.ui.preview.docx`; framework-neutral package policy/validation stays in `com.example.core.format`; Android transport/print/diagnostics adapters live in DOCX-specific UI/utility files. No one-file-per-class requirement: each boundary must own a real lifecycle or policy.

```text
FilePreviewScreen (shared routing / toolbar coordination only)
  └── DocxPreviewScreen (DOCX-specific Compose surface)
       ├── DocxPreviewViewModel (StateFlow + SavedStateHandle state)
       ├── DocxPreviewController (toolbar/search/page/retry commands)
       └── DocxWebViewHost (AndroidView create/update/release)
             ├── DocxWebViewClient (strict local navigation + asset interception)
             ├── DocxJavascriptBridge (typed, generation-scoped callbacks)
             ├── DocxDocumentLoader (local file descriptor/stream; no base64)
             ├── DocxPackageValidator (bounded ZIP metadata/required parts)
             ├── DocxRenderCoordinator (single render job + state transitions)
             ├── DocxZoomController / DocxGestureHandler (one effective-scale model)
             ├── DocxSearchController (query/highlight/next/previous/clear)
             └── DocxDiagnostics (allowlisted metrics only)

DocxPrintController -> separate WebViewHost in PRINT mode -> same loader/validator,
renderer assets, coordinator/readiness checks -> PrintManager adapter lifecycle.
```

Candidate files (combine where the implementation stays clearer):

- `DocxPreviewScreen.kt`, `DocxPreviewViewModel.kt`, `DocxPreviewState.kt`, `DocxPreviewController.kt`
- `DocxWebViewHost.kt`, `DocxWebViewClient.kt`, `DocxJavascriptBridge.kt`
- `DocxDocumentLoader.kt`, `DocxPackageValidator.kt`, `DocxRenderCoordinator.kt`
- `DocxZoomController.kt`, `DocxGestureHandler.kt`, `DocxSearchController.kt`
- `DocxPrintController.kt`, `DocxDiagnostics.kt`, `DocxErrorState.kt`
- pure policies/tests under `core/format/` where Android imports can be avoided

### State contract

`DocxPreviewState` is a sealed, immutable UI state. Lifecycle path:

```text
IDLE → LOADING → VALIDATING → RENDERING → LAYOUT → READY
                       └────────────── any stage → FAILED(reason)
```

`FAILED` carries one explicit category: `INVALID_DOCUMENT`, `LOAD_FAILED`, `ENGINE_UNAVAILABLE`, `RENDER_FAILED`, `LAYOUT_FAILED`, `EMPTY_DOCUMENT`, or `RESOURCE_LIMIT_EXCEEDED`, plus safe message, retry eligibility, recovery availability, and diagnostic code. Loading remains visible through `LAYOUT`; `READY` requires non-empty renderable page content, finite positive page dimensions and scale, and a page/content rectangle intersecting the scroller viewport. No callback merely means “promise resolved.”

`DocxRenderCoordinator` owns one monotonically increasing document generation and one active job. Opening a different document cancels/invalidates the prior generation; same-generation repeated `render()` is idempotent; all bridge/callback messages carry the generation and are ignored when stale. WebView destruction cancels host-bound work. Retry creates one fresh render generation. Recovery is bounded and dispatched to IO; it is an explicit labeled state/action, not a silent READY substitute.

`DocxPreviewViewModel` survives Compose recomposition and stores document identity plus restorable page, query/match and zoom preferences through `SavedStateHandle`. The WebView itself is not persisted across process death. If Activity recreation destroys it, recreate from the app-local source and restore page/query/zoom after visible layout; do not pre-read or Base64-copy the DOCX. Current Activity `configChanges` and file route will be checked before lifecycle changes. Rotation should ordinarily resize/re-layout the existing WebView rather than reopen the file.

### Viewport, layout, zoom and gestures

Use one CSS scaling mechanism (planned: Chromium `zoom`) for fit-width and user zoom; remove the transform-preview/zoom combination rather than multiplying separate scale systems. The renderer's page width is measured only after it is attached. Fit scale is computed from the actual scroller content box and page width; output is centered; scroller owns vertical/horizontal extent and scroll. On resize, keep a stable page anchor plus its normalized viewport offset, recompute fit, apply zoom, then restore that anchor after a frame/layout callback. Preserve page index and user zoom when changing orientation where possible. Do not guess screen dimensions or insert time delays.

The WebView rendering DOM is the place where pinch/double-tap coordinates and CSS layout meet. Gesture input may be handled in a small JS module if browser event semantics require it; that module delegates effective zoom changes to one controller and reports updates. Toolbar fit/in/out/reset and pinch/double tap must share the same clamp, zoom state, focal-point math and scroll restoration. Tests must include CSS page rects, scroller bounds/scroll extents, clipped/zero/offscreen rejection, and gesture-event/model results. If CSS `zoom` yields unacceptable reflow or viewport behavior on the tested WebView matrix, reassess with instrumented evidence before adopting a second mechanism.

### Loading, package validation, resources and security

- `DocxDocumentLoader` serves the app-owned local file via WebViewAssetLoader/InputStream; it does not create a second whole-document byte array or Base64 string. Track expected size and bytes consumed without retaining payload data.
- `DocxPackageValidator` validates ZIP readability, required OOXML entries, entry-count/declared-size/resource ceilings and the bounded `word/document.xml` path before rendering. Reuse documented size/recovery limits where appropriate; any new cap must be explicit, tested below/at/above, and not an unmeasured silent rejection. The existing 25 MiB formatted-preview route stays unless evidence/product approval changes it.
- Only the trusted packaged DOCX HTML and the exact `/docx-src/document.docx` response are allowed. Disable file/content access; intercept local assets; block external/top-level navigation; no INTERNET permission, external renderer fetch, scripts from document relationship links, or Base64 bridge.
- `DocxErrorState` provides “Retry formatted preview” and “Open recovered text” only when available. Recovery uses existing bounded `DocxExtractor`, run on IO, with cancellation and byte/paragraph/character limits.
- `DocxDiagnostics` records safe scalar metadata and renderer stage/timing/layout/error codes. Never record text, binary payload, full paths or unconstrained JS exceptions; redact names/URLs in owner-device logs before sharing.

### Search, navigation and print

Search module owns safe highlighting (store/restore original text nodes or ranges, never wrap already wrapped markup recursively), Unicode/cross-style run matching, bounded retained matches, total-vs-navigable count semantics, previous/next/clear, and query/current match state restoration. Page navigation reports renderer-estimated pages and must not claim Word pagination. Navigation scrolls through the same page model and preserves the current page index on resize/reload.

Print is a separate host mode using the same loader, package checks, renderer assets, layout readiness contract and document generation policy. It must not inherit preview-specific user zoom. `DocxPrintController` waits for a valid print render before creating the adapter; error/fallback, cancellation, late callback, adapter `onFinish`, watchdog and WebView destroy all converge on idempotent cleanup. Existing text recovery printing remains clearly identified and bounded. Tests compare content parity but do not treat print as proof of preview.

## 4. `FilePreviewScreen.kt` extraction plan and dependency map

Keep the current public `FilePreviewScreen(viewModel, onBackClick, onNavigateToEditor, modifier)` contract and route enum/states unchanged. First extract leaf viewers in dependency order, preserving their existing package/import behavior; keep PPTX/PDF/non-DOCX logic as moved, not redesigned. Then extract shared chrome/state/dialogs into orchestration components. Final target is routing/coordination (roughly 300–600 lines), but correctness and clear responsibilities outrank an arbitrary line count.

1. `text/`: `MarkdownPreview`, `PlainTextPreview`, `SearchableDocumentWebView`, `WebAppInterface`, `DocumentPreviewScript` helpers and text utilities. Keep editing in its current support component; no text behavior rewrite.
2. `code/`: `CodePreview` and its syntax theme presentation.
3. `csv/`: `CsvPreview`.
4. `archive/`: `ZipPreview`, tree flatten/count helpers.
5. `image/`: `ImagePreview`.
6. `pdf/`: PDF screen, scrubber, skeleton, URI helper and PDF bridge; preserve pure PDF search models.
7. `media/`: `AudioPlayer`, `VideoPlayer`.
8. `other/`: `HexViewer`, time/format/share utilities only after all call sites and shared behavior are audited.
9. `web/`: generic `WebViewPreview` and local-file helper.
10. `docx/`: replace old embedded `DocxPreviewWebView` with `DocxPreviewScreen` at same route seam.
11. Move PPTX only as a mechanical extraction to a separate viewer file; no PPTX behavior change.
12. Extract find header, toolbar and modal/sheet components with explicit parameter/state contracts. Keep `FilePreviewScreen` responsible for collecting shared ViewModel state and selecting a viewer; use small local state holders rather than one cross-format state object.

Dependency direction: preview route → viewer Composables; viewer → specific controllers and core policies; core policies must not import Compose/Android. Shared UI utilities must not depend on a format-specific package. Keep `MainViewModel` as file-route authority; do not change DB schema or routing semantics. Avoid circular viewer imports by moving shared models/helpers to `ui.preview` or `core` only when actual shared use is demonstrated.

## 5. Test plan and acceptance evidence

Keep existing tests and add focused suites before/with each extraction:

- Pure Kotlin: package validator and size/OOXML required parts, preview state transitions, duplicate-open suppression, generation/stale callback, cancellation, zoom clamp/anchor math, page restoration, bounded search index semantics, error/retry eligibility, resource ownership/disposal.
- JavaScript DOM: actual local renderer output for basic/Hindi/Unicode, mixed runs/search, tables, image/hyperlink, explicit page/section breaks, portrait/landscape, header/footer, long/multi-page, empty/malformed, and resource-limit documents. Assert visible page geometry/intersection, not just `renderAsync` completion or text in DOM. Force invisible/offscreen/zero-size/clipped states and expect no READY.
- Android WebView instrumentation: exact local asset/intercept route, offline fetch and bytes, normal/print host setup, JS bridge generation filtering, engine errors, Retry/recovery, 360×800 and 412×915 CSS viewports, zoom/gesture commands, resize/rotation restoration, no external navigation, destroy/cancel/leak callbacks. Add PrintManager adapter lifecycle tests without assuming an emulator is a physical printer.
- App modularization: compile and test after each viewer extraction; preserve shared `FilePreviewScreen` caller and UI contracts; add route smoke tests/Compose tests if existing harness supports them. No test deletion or assertion weakening to achieve green.
- CI gate: `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:detekt`, `:app:compileDebugKotlin`, `:app:assembleDebug`, `:app:connectedDebugAndroidTest`, Node DOCX tests and artifact upload. Record exact SHA/run and all failures. Local environment currently has no Java/Android SDK; use CI and do not claim local Gradle execution.
- Physical acceptance remains required: owner’s exact DOCX visibly renders on the owner’s device, preview and print compared, diagnostics captured, rotation/retry/search/zoom checked. Until that happens report root cause and resolution as unconfirmed.

## 6. Implementation sequence / rollback boundaries

1. Complete and review this design/map before source edits. **Done.**
2. Build standalone core states/policies/validator tests and the DOCX asset entry point in isolation; run pure tests/static checks.
3. Implement host/coordinator/bridge integration and fixture-backed WebView test before swapping the route.
4. Replace DOCX route only when the new host passes normal/print instrumentation. Keep a short-lived compatibility wrapper only during migration; remove old implementation after all callers and tests point to the new one.
5. Extract `FilePreviewScreen` leaf viewers in logical commits; after each extraction check API visibility/imports, run tests/compile, and compare route behavior. Do not batch mechanical moves without a successful compile checkpoint.
6. Run complete CI, review full diff/artifact/reports, update architecture/issue/changelog. Then request owner physical-device check; no resolution claim before it.

## 7. Implementation checkpoint (2026-11-22)

Current implementation branch is `arena/ea756912-file-claw-native`; commits on it begin with `8522847` (design), `3af73d5` (mechanical viewer extraction), and `5c7fb4b` (generation-scoped lifecycle). The original 5,531-line file has been reduced to about 2,550 lines by moving unchanged viewer implementations into `TextDocumentPreviews.kt`, `CodeAndCsvPreviews.kt`, `ArchiveAndImagePreviews.kt`, `PdfPreview.kt`, `MediaAndBinaryPreviews.kt`, `WebAndPptxPreviews.kt`, and `DocxPreviewWebView.kt`. Public route/composable signatures are retained; shared `PlainTextPreview`, `shareFile`, and Activity-context lookup are internal cross-file seams. Non-DOCX viewers were mechanically extracted, not redesigned. The main screen remains far above the 300–600-line target and still needs toolbar/modal/state orchestration extraction.

A pure Kotlin `DocxPreviewLifecycle` plus unit tests now enforce ordered stages, terminal failure, positive page count and visible-layout evidence, and generation mismatch rejection. The DOCX bridge maps local renderer diagnostic events (fetch complete, render start, layout, failure, engine unavailable) to these stages; the renderer's `onDocumentLoaded` callback is accepted as READY only after the existing JS visible-layout check. A per-document `loadStarted` guard ignores duplicate loads. WebView release now stops loading, removes the JavaScript interface and destroys the host. This is an incremental seam: WebView ownership and controllers still live in one large composable, no full cancellation/replacement coordinator has been introduced, and actual stream cancellation/package validation/print lifecycle unification remain open.

Verification so far: the pre-existing Node DOCX search suite passes (5/5), and `node --check` plus `git diff --check` pass. Local Gradle cannot run because this environment has no Java/Android SDK. GitHub Actions runs for implementation SHAs `5c7fb4b`, `632369b` and `7a9cb79` failed during the combined JVM/lint/Detekt/Kotlin/APK step; emulator tests were skipped. GitHub's job log/artifact downloads redirect to a results blob host that is not reachable from this sandbox, so the specific Gradle failure is not yet known. The workflow now adds the final Gradle log lines and parsed JVM test failures to the job summary/check annotations. Runs `44984121457` and `44984494589` exposed two classifier gaps in order: `DOCX_PAGE_OUTSIDE_VIEWPORT` was incorrectly `LOAD_FAILED`, and `DOCX_RENDERED_WITHOUT_PAGES` fell through to `RENDER_FAILED`. Both mappings were corrected. Full CI passed on code SHA `778e04d91919f40af965d0a0097c814f4779b5e0`, run [44984956879](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/44984956879): JVM unit tests, lint, Detekt, Kotlin compilation, debug APK assembly, Android emulator WebView instrumentation, JS checks and artifact upload all passed. Local Gradle remains unrun due the missing Java/Android SDK. The owner’s physical device/file has not been tested; root cause and resolution remain unconfirmed.
