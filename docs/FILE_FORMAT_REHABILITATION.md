# DOCX rehabilitation investigation and approval handoff

**Audit date:** 2026-11-21 UTC
**Inspected base:** `arena/ea756912-file-claw-native` at `2f6d3492de46b3d8f71515404d170af4e35c8459` (grafted checkout)
**Status:** owner-authorized DOCX rehabilitation is implemented on this branch. Node/static checks and GitHub Actions run `44931079956` on commit `c561dbae50be7d81ba03f7293f9ead952fd842ba` passed, including Gradle unit/lint/Detekt/Kotlin compile/debug APK and emulator instrumentation. The artifact is available from that run. A final documentation-only update is being revalidated on its exact commit. Reported physical-device symptoms are not reproduced in this sandbox. Changes remain limited to DOCX behavior, tests, documentation, and a safe verification workflow. No renderer migration, dependency upgrade, production signing change or unrelated-format work is authorized.

## Executive summary

The app accepts DOCX-family ZIP packages and uses a local Android WebView with bundled JSZip and `docx-preview` to create an HTML approximation of Word content. It has a separate Kotlin text extractor for degraded recovery and printing. At the inspected base there was no DOCX regression fixture or Android instrumentation suite; this patch adds both. This sandbox has no Java, so local Gradle checks remain blocked, but CI run `44931079956` passed the JVM test, lint, Detekt task, Kotlin compile, debug APK build, and WebView instrumentation. The fixture covers only the assertions described in the issue register; physical-device behavior remains unverified.

At the inspected base, two source-evidenced concerns were highest priority: (1) DOCX bytes were eagerly read and base64-encoded into ViewModel state despite preview using WebViewAssetLoader; (2) recovery extraction inflated `word/document.xml` without a bound and was invoked from a WebView error callback. Neither was demonstrated to cause a particular user-visible failure. The approved patch removes the DOCX Base64 state path, bounds archive/text recovery, and dispatches extraction off the UI thread. CI compile, unit, and WebView instrumentation checks passed on the verified commit. Do not describe the original concerns as a reproduced OOM, freeze, or crash, or claim their device impact was eliminated without a representative device reproduction.

## Current implementation status

The approved patch adds bounded DOCX archive/text recovery and a size-based formatted-preview limit, streams DOCX bytes via the local asset loader, makes stale-render callbacks inert, adds cross-run literal search with bounded highlights, clarifies estimated page labels, improves pinch cleanup, and routes print cleanup through adapter completion with a bounded recovery fallback. Synthetic unit and WebView fixtures plus a GitHub Actions workflow were added. Node search tests and static checks pass. CI run `44931079956` passed unit tests, lint, Detekt task, Kotlin compile, debug APK build, emulator startup, WebView instrumentation, JS checks, and artifact upload. Earlier manually managed emulator runs failed to boot; enabling KVM and switching to a SHA-pinned runner resolved CI startup. This does not establish that owner-reported symptoms were reproduced or fixed. Android print dialog/cancellation, rotation/lifecycle, and large-file behavior remain unverified. See `PROJECT_STATUS.md` and `ISSUE_REGISTER.md` for exact scope and results.

DOCX rendering is not Microsoft Word compatibility. It is a renderer-defined HTML conversion. `docx-preview` itself describes its semantic-HTML approach as constrained by HTML, says it does not implement realtime page breaking, and notes that Word fields (including an efficient TOC) are not supported. The application’s page count is the number of rendered `.docx` nodes, not an independently verified Word page count. The app must set user expectations accordingly.

**Implementation decision:** the owner approved a bounded, evidence-led repair phase. The current offline renderer and approved changes passed the cited CI run; physical-device feedback remains pending. Do not migrate renderers or upgrade bundled libraries without a comparative spike, fixture results, license/provenance check, offline/APK/device measurements, and separate owner approval.

## Evidence labels and limits

- **Source fact / static finding:** directly visible in the inspected checkout. It proves what code is written, not that a device experienced the behavior.
- **Fixture-runtime-confirmed:** the synthetic OOXML fixture passed the cited Android WebView instrumentation scenarios (visible text parity, Hindi/table text, resize preservation, page-node count, and cross-run search). This does not verify Word pagination or reproduce the owner's device symptoms.
- **Suspected / requires reproduction:** code suggests a risk or output mismatch, but runtime behavior/impact is unproven.
- **Renderer/product limitation:** expected capability boundary of the selected HTML renderer, not automatically an app bug.
- **Not tested:** distinguish missing tools, fixtures and devices from a passing check.

The verified CI run produced a debug APK and ran the synthetic fixture in Android WebView instrumentation. No complete OOXML conformance suite, Microsoft Word oracle comparison, physical-device session, Android PrintManager job/cancellation, rotation/lifecycle matrix, heavy-file memory profile or accessibility run was performed. Static syntax/XML checks are not runtime evidence.

## Source trace: open through cleanup

### 1. Incoming file and app-owned copy

- `MainActivity.kt` handles incoming `ACTION_VIEW` URI intents and forwards the URI to `MainViewModel.importFileFromUri()` (source path; no external-provider device test).
- `MainViewModel.importFileFromUri()` queries display name/size, sanitizes the destination name, copies the content-resolver stream into `filesDir/imported`, records a recent-file row and opens the copied file. A same-name/size destination can be reused. This means the starting DOCX route is an app-local copy, not a provider stream. URI grant lifetime is therefore not on the renderer’s normal read path, though the URI is retained for other app flows.
- Import copy failure deletes the destination and emits an import-failed event; no provider matrix or large-file disk test was run.

### 2. Detection, validation and route selection

- `MainViewModel.openFile()` runs detection/content-state construction in `Dispatchers.IO`; the source route begins at `FormatDetector.detect(file, file.name)`.
- `FormatRegistry` maps `.docx`, `.docm`, `.dotx`, `.dotm` to the `DOCX` kind. Registry recognition is not proof that each content family renders correctly or that macro/template functionality is provided.
- `FormatDetector` opens ZIP files with `ZipFile`, examines up to 256 central-directory entry names, and selects DOCX when the exact `word/document.xml` name is found. It does not parse `[Content_Types].xml`/relationships to validate an OOXML package at this stage. Its ZIP inspection catches exceptions and returns no entries; extension/family fallback can still classify a ZIP-family extension. This is routing, not full ZIP/OOXML validation or a safety guarantee.
- `MainViewModel.buildContentState()` routes `FileKind.DOCX` to `webDocument()`. That helper routes files **larger than 25 MiB** to `openArchive()`; otherwise it reads all bytes and Base64-encodes them into `FileContentState.DocxSuccess`. The boundary is `>`: exactly 25 MiB still follows the renderer route. `openArchive()` parses the ZIP tree; that is not formatted DOCX preview.
- There is no checked-in real DOCX fixture confirming detection, malformed ZIP routing, the 256-entry edge, package relationships or the 25 MiB boundary.

### 3. Data transport, assets and WebView boundary

- `DocxPreviewWebView` in `FilePreviewScreen.kt` receives both a file entity and the `DocxSuccess.base64Data`. It registers WebViewAssetLoader handlers for `/assets/` and `/docx-src/`; the latter serves only `document.docx` from the local file. The HTML page fetches `/docx-src/document.docx` and calls `arrayBuffer()`; the JavaScript bridge Base64 path is a fallback if the fetch fails.
- Current regular load calls `setZoom(freshZoom); loadDocx(null, freshNightMode)` from `onPageFinished`, so the usual transport is file stream, not bridge Base64. Nevertheless the ViewModel has already allocated and retained Base64 for every DOCX under/equal to the cap. That retained duplicate is a source-evidenced inefficiency; memory peak and user impact are not measured.
- WebView enables JavaScript for this local renderer, disables file/content access and file-URL/universal file-URL access, disables native zoom controls, and limits top-level navigation to the local asset/docx URLs. `shouldInterceptRequest` delegates to the asset loader. Source inspection shows no intentional remote renderer asset request; there has been no network capture confirming runtime offline behavior.
- Editor copies of `docx-preview.min.js` and `jszip.min.js` are byte-identical to copies in `assets/docx/`; the duplicate copies are maintenance/provenance debt. Neither Android build configuration nor these checks prove which copy is packaged at runtime beyond the explicit editor asset path.
- The local `docx-preview.min.js` SHA-256 is `a94a542f2d2180bca47c2d11df9590cc23825b8190b0052255e1280c61f7f76d`; it did not match published `docx-preview` 0.3.6, 0.3.7, 0.4.0 or 0.4.1 bundles fetched for comparison. Exact version/commit provenance remains unknown. Upstream `docxjs` README/package metadata identifies Apache-2.0, but that alone does not prove the exact local artifact’s origin or satisfy shipped-notice requirements. Keep `THIRD_PARTY_NOTICES.md` open pending provenance evidence.

### 4. JavaScript load, rendering, layout and page callbacks

- `docx_editor.html` loads the local JSZip and `docx-preview` scripts, checks they exist, reads stream/fallback bytes, then calls `docx.renderAsync(arrayBuffer, staging, null, options)`.
- Options explicitly request a wrapper, page breaks, dimensions/fonts and experimental mode; `ignoreLastRenderedPageBreak` is `false`. The resolved DOM replaces the loading/staging content. The page width is measured from the first `.docx` element and used for a viewport-fit baseline; the UI zoom factor is separate. Resize tries to preserve an anchor page; pinch uses temporary CSS transform followed by committed CSS `zoom`; double-tap toggles relative zoom.
- On render success, the page count is computed as `staging.querySelectorAll('.docx').length`, with fallback 1 if no `.docx` exists. Page visibility comes from `IntersectionObserver` and `scrollIntoView`. This is a count of renderer DOM nodes, not measured pagination against Word or Android printing. Rotation, long flowing content, continuous/mixed sections, accessibility, pinch focus and zoom persistence have not been device-tested.
- HTML docs from upstream `docxjs` describe conversion to semantic HTML, not Word’s layout engine; its README notes realtime pagination is not implemented and fields are not supported. Local options can enable renderer features but do not make unsupported OOXML semantics equivalent to Word.

### 5. Search and UI/bridge feedback

- Search walks text nodes under the rendered container, lowercases each node and finds occurrences only within that node. It wraps matches in `<mark>` elements. A query spanning adjacent DOM text nodes (e.g. a word split across styled Word runs) cannot match as one continuous string in this implementation. This is algorithmically evidenced; no browser fixture reproduced it here.
- The implementation retains/highlights at most 5,000 matches while continuing to count occurrences in later matches. The bridge reports total count but current index/navigation uses the retained match list. The displayed count/index behavior after the 5,000th occurrence needs a clear contract and fixture; do not claim all results are navigable. Search across headers, footers, footnotes, tables, Unicode case behavior and repeated search/clear also need browser/device validation.
- JavaScript bridge callbacks post UI changes to the main handler. Successful load updates readiness/page state; renderer error records a clamped reason and triggers recovery; tap/double-tap and zoom callbacks update Compose. The exact current state wiring should be kept in scope during an approved test pass, because helper tests alone do not exercise it.
- The DOCX branch is read-only. It uses the existing Compose viewer controls/notices; no redesign or user-visible copy change is authorized here.

### 6. Recovery and error states

- `docx_editor.html` displays an error and calls `Android.onDocumentError()` on engine-unavailable, failed fetch/base64, or rejected `renderAsync` paths. `FilePreviewScreen.kt` also handles main-frame WebView errors and renderer-process loss, then presents the labeled text recovery state.
- Error and renderer-gone callbacks enter the main handler and call `DocxExtractor.extractText(File(...))` there. The extractor uses `ZipInputStream`, finds `word/document.xml`, reads it with unbounded `readBytes()`, decodes UTF-8, then extracts paragraphs using regular expressions. There is no inflated-entry byte cap, total work cap or structured XML parser. This makes recovery memory/latency risk source-evident for large or adversarial compressed XML, while the main-thread call means the work is on the UI thread. No hostile fixture was created and no freeze/OOM was observed.
- Regex extraction only recovers selected `<w:p>`, `<w:t>`, `<w:br/>` and `<w:tab/>` patterns; it is intentionally not equivalent to rendering and may not preserve tabs/newlines correctly (the current code appends `<w:t>` chunks after replacing break/tab markup in the input; no fidelity test covers them). It does not establish a complete text extraction guarantee for all valid OOXML encodings/namespace spellings/markup.
- `DocxTextRecoveryTest` validates small pure decision/label rules (show only when paragraph count positive, failure headline, reason clamp, stats). It does not invoke the WebView callback, extractor on a failed real renderer, main-thread dispatcher or recovery UI.

### 7. Navigation, lifetime and local temporary files

- `MainViewModel.openFile()` updates loading/current state and emits navigation events. DOCX archive-entry opening is a separate path: `openZipEntry()` extracts to cache and later opens the local extracted file (recheck exact archive extraction cleanup before implementation). No archived DOCX fixture or back-navigation test exists.
- The Compose `AndroidView` uses `onRelease` to clear its reference and destroy the DOCX WebView. This is a positive explicit cleanup path. WebView renderer-process loss is handled in source. No rotation/configuration change, rapid file switching, back navigation, stale JS callback, process death, WebView destruction or cache cleanup race was exercised.
- `FilePreviewScreen` holds `DocxSuccess` (including Base64) as ViewModel state; inspect state clearing/retention if changing transport. Do not assume `WebView.destroy()` releases the retained Base64 string or temporary extracted archive files.

### 8. Printing

- `FilePrintHelper.printDocx()` creates an off-screen WebView and a local asset loader for editor assets and the DOCX file stream, enables JS, loads `docx_editor.html?print=1`, and calls `loadDocx(null, false)` when the editor page finishes. Renderer callbacks trigger `PrintManager`; a 12-second timeout or render/main-frame error falls back to HTML built from `DocxExtractor` paragraphs.
- The formatted path and text-only fallback are materially different outputs. Print rendering uses the same HTML renderer and cannot be assumed Word-identical. The extractor fallback is not visually faithful. The asynchronous print adapter/WebView lifecycle uses delayed destruction; verify cancellation, print completion/failure and cleanup on devices. No print preview or paper output was run.
- `FilePrintHelper` code catches initialization errors, but cleanup and callback races across timeout, late render completion, user cancellation and WebView destruction require device tests. This is a suspected lifecycle test gap, not a confirmed user-visible print defect.

## Compatibility/capability matrix (evidence-based, not a Word-compliance claim)

| Case / feature | Current route or evidence | Assessment | What remains to verify |
|---|---|---|---|
| `.docx` package with `word/document.xml` | ZIP name detection then `docx-preview` WebView | Supported best-effort HTML rendering | Real fixture, relationship/package validity, text/layout fidelity |
| `.docm`, `.dotx`, `.dotm` names | Registry sends these extensions to DOCX route | Recognized/routed; macro execution/editing is not implied | Representative package and explicit view-only policy; do not assert macros run |
| Text paragraphs and basic runs/styles | Renderer conversion to HTML; extractor regex handles basic text runs | Basic content intended; fidelity is renderer-dependent | Unicode, split runs, tabs/breaks, styles, fields, special markup fixtures |
| Tables, images, hyperlinks, lists | Renderer path and default option set; no app real fixture coverage | Plausible partial renderer support, not application-verified | Table/nested table, image relation, external/anchor link, numbering fixtures; inspect offline/network behavior |
| Headers/footers, footnotes/endnotes, comments, tracked changes | Upstream renderer exposes controls/defaults; local options mostly rely on defaults | Capabilities vary; comments and tracked-change rendering documented experimental upstream; no app proof | Separate fixture per part, references/relationships and exact visible content; do not imply comprehensive support |
| Manual page breaks and page geometry/sections | `breakPages: true`, `ignoreLastRenderedPageBreak: false`, dimensions enabled | Some explicit break/section paths are intended; renderer does not do realtime Word pagination | Manual vs last-rendered break, continuous section, mixed orientation/margins, long table fixture |
| Fields / generated TOC / exact Word pagination | Upstream README says fields not supported and realtime page breaking absent | Product/renderer limitation; not an app defect by itself | UI must not imply Word-complete rendering; compare only supported subset |
| Search in rendered content | DOM text node search, up to 5,000 retained marks | Same-node search only; full cross-run search unavailable by current algorithm | Split runs, headers/tables, >5,000, counts and next/previous browser tests |
| Page count / page index | Count `.docx` DOM elements; observer picks visible element | Estimated renderer section/page-node count; physical accuracy unverified | Known-break fixture compared to Word and Android print across devices |
| >25 MiB DOCX | Archive listing instead of renderer | Explicit current product limitation / fallback route | Boundary tests, user expectation/copy, low-memory device measurement if owner wants support |
| Invalid/malformed/hostile ZIP | Zip detector and renderer/recovery paths; no DOCX safety suite | Safe behavior not established; not evidence of exploitable behavior | Bounded malformed package fixtures, no UI-thread decompression, parser failure and resource ceilings |
| Formatted print / fallback print | Off-screen WebView; extracted text fallback | Both routes exist, fidelity differs | Real PrintManager PDF preview/output, page-break fixture, cancel/timeout/failure cleanup |

The matrix describes this app’s source path plus public upstream renderer contract. It does not claim the particular bundled artifact implements every current upstream feature.

## Findings and triage

The detailed issue records are in `docs/ISSUE_REGISTER.md`, section **DOCX rehabilitation addendum**. Current priority order for owner review:

1. **P0 / safety and responsiveness, static risk:** bounded and off-main-thread recovery extraction (`AUD-DOCX-002`). Define limits and explicit failure UI before implementation; safely test with bounded synthetic fixtures.
2. **P1 / memory waste, source-evidenced:** remove or justify duplicate Base64 ownership while retaining a deliberate tested fallback (`AUD-DOCX-001`). Measure actual heap on device before setting performance claims.
3. **P1 / coverage gap:** add representative real fixture set and Android WebView integration/device evidence; current helper-only tests do not cover the lifecycle (`AUD-DOCX-008` addendum).
4. **P2 / capability contract:** specify and test page count and the 5,000-result search cap; cross-run search is a current algorithm limitation. Do not expand to Word-like completeness absent product decision.
5. **P2 / large-file product choice:** confirm whether >25 MiB archive-tree behavior is accepted; measure lower-memory device costs before changing it.
6. **P2 / print and lifecycle verification:** formatted/text-fallback distinction, timeout/cancel/destruction cleanup, rotations and rapid navigation need device verification before calling them broken.
7. **Release compliance / provenance:** identify exact renderer bundle version/source and ship required notices before a public release. This is separate from visual compatibility and no dependency update is proposed.

No application fix is approved or performed by this report. Static concerns that require repro are not reclassified as confirmed runtime defects.

## Existing tests and coverage-layer audit

### Relevant checked-in tests

- `app/src/test/java/com/example/core/format/DocxExtractorTest.kt`: builds synthetic ZIPs in test code with a very small hand-authored XML document; tests two paragraphs/basic entities and missing `word/document.xml`. No binary DOCX fixture, complex OOXML parts, compressed-size boundary, malformed XML, encoding, namespaces, tab/break fidelity or adversarial entry.
- `app/src/test/java/com/example/core/format/DocxZoomPolicyTest.kt`: pure zoom math/clamp/toggle policy; does not render CSS, use touch events or verify focus/scroll/layout.
- `app/src/test/java/com/example/ui/screens/DocxTextRecoveryTest.kt`: pure recovery wording/decision helpers only; not bridge, UI or extraction integration.
- `app/src/test/java/com/example/ui/screens/DocumentPreviewSupportTest.kt`: shared support policies, not a DOCX browser test.
- `app/src/test/java/com/example/core/format/FormatDetectorTest.kt`: relevant ZIP/signature detection logic, but no discovered real DOCX fixture and no full import/WebView route test.
- Other archive-safety and gesture-gate tests can help test shared pure policies; they do not establish DOCX runtime behavior.

Inventory reports 32 JVM test source files and 222 literal `@Test` annotations across the checkout. These are counts, not executed tests or coverage. There is no `app/src/androidTest/` directory and no discovered real DOCX fixture under the searched app test/assets paths.

### Execution status

- **NOT RUN — environment/setup:** `bash ./gradlew testDebugUnitTest --no-daemon` was attempted previously and stopped before Gradle because `java`/`JAVA_HOME` is unavailable. This is not a test failure and not a pass. Do not count any test annotation as passing.
- **STATIC ONLY / PASS:** extracted inline JavaScript in `docx_editor.html` passed `node --check`; `AndroidManifest.xml` parsed as XML. This proves syntax/well-formedness only, not Kotlin compilation, DOM operation, asset resolution, bridge behavior or Android WebView rendering.
- **NOT RUN — environment/setup:** no Java/Kotlin compiler, Android SDK/adb, Chromium/Chrome or Playwright was available in the inspected environment; no APK or device/print check was produced.
- No lint, detekt, JaCoCo, build, release or application-source test ran for this DOCX investigation.

### Required fixture set before approved implementation

Create small synthetic OOXML packages with authored expected results, plus a small redistributable external corpus only if its license/source is recorded. Keep generated/large data out of Git unless specifically approved. Minimum fixture cases:

1. Minimal valid DOCX: `[Content_Types].xml`, relationships, styles, settings, one and multiple paragraphs, Unicode/emoji, XML entities, split formatting runs, `xml:space="preserve"`, tab, line break.
2. Layout features: single/nested tables, numbering/bullets, inline and anchored images, hyperlink, headers, footers, footnotes/endnotes, page-number fields, comments, tracked insert/delete.
3. Pagination: explicit page break, `lastRenderedPageBreak`, section break, continuous section, portrait/landscape change, margins, long paragraph, long table and 100-page generated content.
4. Boundaries: exactly 25 MiB and one byte below/above with valid packages; ZIP with `word/document.xml` past entry 256; DOCX extracted from app archive flow.
5. Recovery/security: missing parts, invalid central directory, CRC failure, truncated ZIP, malformed XML, namespace variation, empty document, small compressed XML with bounded large inflated output. Never put an unbounded ZIP bomb on a physical device.
6. Format routing: `.docx`, `.docm`, `.dotx`, `.dotm`, misleading extension, malformed ZIP and unrelated ZIP.

For each fixture, record SHA-256, generator/source, license, expected text/structure, and where comparisons came from. Do not use proprietary documents or claim Word comparison unless a named Word version is actually used.

## Repair architecture assessment (no implementation authorized)

### Keep current renderer as the baseline

The present offline `docx-preview` + WebView architecture is appropriate for a view-only, broad-but-best-effort preview if it is made resource-bounded, transparent about fidelity, and covered by browser/device fixtures. It preserves offline behavior and already has local assets, restricted navigation, a recovery path and a separate print route. First stabilize the path actually shipped rather than adding a second renderer.

### Alternatives considered

- **Keep and harden current WebView:** lowest migration/regression risk; reuse assets/controls and exercise exact Android WebView. Main costs: HTML/render fidelity ceiling, DOM/memory scaling, WebView/device variability, and need to maintain an explicit offline JS bundle. Recommended first.
- **Different JS renderer:** only consider after a fixture-based comparison of supported OOXML features, license/provenance, package size, minimum WebView/API, offline asset behavior, search/page/print hooks, security/update cadence, render latency and heap. Migration can silently regress custom controls and cannot be justified by a name/version bump.
- **Native Kotlin/Android OOXML renderer:** substantial parser/layout engineering; no evidence in this audit that it offers Word fidelity. Consider only for narrowly scoped, objectively measured gaps with maintained OOXML libraries and explicit compatibility goals.
- **Server/cloud conversion:** incompatible with the current offline/local-only design and creates privacy/network/deployment requirements. Not recommended for this task.
- **PDF conversion within app:** may improve stable pagination if a reliable local converter exists, but adds a conversion engine and loses editable DOM/search semantics; no converter was found/assessed here. Do not add a large dependency without an owner-approved comparison.

No migration recommendation can be stronger than “defer until supported use cases and fixtures reveal a blocker.”

## Phased plan (all implementation phases require explicit approval)

### Phase 0 — Owner intake / scope lock (now)

- Receive the owner’s concrete additional defects, affected files/examples, priority and expected behavior.
- Decide which current limitations are acceptable: 25 MiB cap, approximate page count, search cap/cross-run matching, text fallback and print fidelity.
- Approve only individually named fixes. No source change until then.

### Phase 1 — Establish repeatable baselines

- Add/approve small synthetic fixture generator and expected-result manifest; include fixture hashes/provenance.
- Run existing DOCX-related JVM suite and detector/archive suites on a Java-enabled clean checkout; capture JUnit XML and exact SHA.
- Add testable seams for transport, bounded extraction and bridge policy only as approved; add Android instrumentation harness for WebViewAssetLoader/bridge and app navigation.
- CI gate: existing unit suite + lint/compile at minimum; add instrumentation on a pinned emulator/API only if stable and available. No ignored failure or test weakening.

### Phase 2 — Resource-safe open and recovery

- Remove redundant eager Base64 transport if proven unnecessary; retain one explicit bounded fallback path and test that the stream works for direct and archive-extracted files.
- Define maximum inflated document XML bytes and work/time policy; enforce while reading, move recovery off the main thread, cancel/stale-guard against file switch, and report capped/empty recovery honestly.
- Tests: below/at/above read cap, malformed ZIP/XML, normal fixture extraction, main-thread responsiveness policy, forced asset-stream failure and recovery callback integration.
- Risks: legitimate large docs; fallback serialization/lifetime; keeping print behavior intact.

### Phase 3 — Behavior contracts: search, layout, zoom, navigation

- Based on owner acceptance, fix only reproduced search/page/zoom problems. Decide whether cross-run search is required and whether >5,000 is a cap on highlight or navigation; ensure UI result contract is explicit.
- Add DOM/browser tests for DOM search, page callbacks and zoom where possible; physical Android WebView tests for viewport/rotation, gesture anchoring, large tables, navigation and screen sizes.
- Risks: DOM mutations, count/index behavior, performance on large docs, CSS zoom differences and accessibility.

### Phase 4 — Print and lifecycle hardening

- Exercise formatted and fallback print separately; test cancellation, timeout, late callbacks, WebView destroy, rapid file switching, archive temp cleanup, back navigation, rotation/process lifecycle.
- Introduce code changes only for reproduced failures and preserve view-only/print output expectations.
- Risks: Android PrintManager asynchronous ownership, adapter lifetime, stale bridge callbacks and file descriptor cleanup.

### Phase 5 — Device/APK/release readiness

- On pinned API levels and at least one low-memory and one current/mid-range device: verify local open, import, archive-extracted open, all failure states, memory/latency, large-file boundary, offline network capture, screen reader basics, print preview/output and cleanup.
- Build debug APK only after the above approved repairs and required checks; no release build is included in this audit. Record APK path/hash/size and exact commit/toolchain; inspect bundle/license notices and renderer provenance. Release signing/release workflow is a separate scope.

## Measurable DONE definition

The owner-approved DOCX repair phase is DONE only when all of the following hold for the exact reviewed commit:

1. Every approved issue has a source link, before/after reproduction fixture or device steps, and status; no unapproved issue was changed.
2. All required synthetic fixture cases have deterministic expected results; fixture source/license/hash are recorded; no large generated data is accidentally committed.
3. The DOCX JVM regression suite, relevant detector/archive suite, app compile and agreed lint/static checks complete with exit 0 in Java/Android-equipped CI. Any unsupported check is explicitly NOT RUN with blocker, never counted green.
4. At least one Android instrumented/WebView test verifies normal render callbacks, failure/recovery, lifecycle/navigation and local asset/stream loading on a pinned API. Manual device matrix covers agreed zoom, page, memory, print and accessibility cases; record actual WebView version/device/API and outputs.
5. Open and recovery resource bounds have numeric documented limits and tests; a hostile input cannot force an unbounded document XML read on the main thread. Normal supported fixtures still work.
6. Search count/navigation, large-file route, page count semantics, fallback state and print limitations match approved product wording; no Microsoft Word-complete claim appears.
7. Offline behavior is verified by runtime network observation, and WebView/temporary file/print resources are released after close, failure and cancellation tests.
8. Third-party renderer artifact provenance/license notices are verified for the actual bundled hashes before any public release (not a prerequisite to describe source behavior, but required before release).
9. No source or other-format behavior outside approved scope changed; Git diff and test report reviewed; no failed check was bypassed.

## Approval boundary and next action

This document is the investigation/plan deliverable. **No fix, UI redesign, dependency upgrade, renderer migration, other-format change or release build is authorized.** Please send the remaining concrete issue descriptions and expected behavior, then explicitly approve the named implementation scope. Until that approval arrives, this task stops at documentation and evidence reporting.
