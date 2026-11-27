## DOCX instrumentation warnings and native pinch-out floor (2026-11-21; working-tree follow-up)

| Audit ID | Evidence / change | Verification / status |
|---|---|---|
| AUD-DOCX-021 | Supplied CI output reports unused bridge `payload` parameters and deprecated `WebView.scale` reads, plus native pinch-out stopping at 1.0 despite the 0.5 viewport minimum. Keep JavaScript bridge signatures; suppress only the unused parameter on the three no-payload-use callbacks. Replace deprecated scale reads with `visualViewport.scale` for CSS hit testing/test observations, renderer-reported fit as the initial baseline, and `WebViewClient.onScaleChanged` for subsequent Android scale updates. Keep the floor test's pinch events and 0.49–0.52 assertion unchanged. Restore prior full-width transform and non-important anchor-space CSS after rejected intrinsic-width/`!important` experiments. | Local Node DOCX suite **30/30 PASS**, four viewer JS `node --check` checks PASS, `git diff --check` PASS. No JDK/Android SDK/adb, so Android compile and emulator instrumentation are **NOT RUN**. The native floor remains **unfixed/unverified**; no root cause is claimed and no APK is verified. Require an exact-SHA emulator pass before closing the issue. |

## Current DOCX flashing / duplicate-render remediation (2026-11-21; uncommitted, device-unverified)

| Audit ID | Evidence / acceptance | Current DOCX-only remediation | Verification / remaining limit |
|---|---|---|---|
| AUD-DOCX-020 | Owner reports repeated flashing between fit-card and over-zoom states and duplicate rendered document containers. The symptom has not been reproduced in this sandbox; source inspection is not device evidence. | Generation-scoped load de-duplication and a serialized render queue; clear the root before rendering and require exactly one `.docx-wrapper`; retain one `AndroidView` WebView; disable overview mode; use the fixed 1.0 initial / 0.5–5.0 viewport; compute responsive page fit once after render, with no resize/interval/meta-scale reset loop. Preserve native pinch/pan, double-tap 1×↔2×, single-tap Focus Mode, external links, loading UI and the established tray order. Added source-contract checks and a two-second Android idle assertion for wrapper count, visual viewport scale, one-time page zoom and page bounds. | Final local Node DOCX suite passes 30/30, all four relevant JavaScript syntax checks pass, and `git diff --check` passes. No JDK/SDK for local Android compilation/emulator. Exact-SHA Actions run [45939434544](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45939434544) on `472ccc5a4081266e92286ded25af0664b6f39771` passed JVM/unit/lint/Detekt, Kotlin compilation, debug APK assembly, and Node verification, but two emulator assertions failed because fit used the wide layout viewport (794 CSS px) rather than the 360/412 px visual viewport. Follow-up [45941208678](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45941208678) on `b4c24824ae5e49084d5b5861bde410dd531346bc` still failed those two assertions; diagnostics confirmed the page-level CSS zoom was computed as expected but page DOM bounds remained at 794px. The current uncommitted attempt applies a one-time translated CSS scale transform to the document root and compensates bottom flow height so child page bounds reflect the applied fit; Run [45943107062](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45943107062) on `6933a9d8ad2b492a431bacadcb0126d978a66e07` passed both fit-width assertions but failed the native pinch floor at 1.0 after `[2.6041665, 1.3563367, 1.0, 1.0, 1.0, 1.0]`. Follow-up [45943967767](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45943967767) on `df3ec12cfdd5223a00e71f6326b5b67869c24136` failed double-tap anchor preservation because inline root sizing overrode the temporary `zoom-anchor-space` rules. Run [45944697847](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45944697847) on `2cc7fbb2a0498f76c9dc3e55f7e7552c9b29d45c` showed that `!important` overrides did not restore the anchor; intrinsic width/flex and its CSS experiment are rejected. The working tree restores the previous full-width root transform, for which fit and double-tap passed but pinch floor 0.5 still failed at 1.0 in run 45943107062. Exact-SHA run [45945527902](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45945527902) on `41f62fe7781815bf98d54e2af3faeb4dd40d4c61` passed build/JVM/lint/Detekt/Node and fit/double-tap checks, but still failed native pinch-out at 1.0. Workflow is red; no APK is verified or shared, and the issue remains open. The previous exact-SHA run `45688662676` at `90f2b5b061d8b939041efd32e9ee72ef1ecaf1cc` was red with reason previously unknown. Current follow-up is uncommitted; keep the symptom open and do not share any APK. Keep the symptom open until exact-SHA workflow and device evidence; no APK is verified or shared. |

## Preview coordinator extraction follow-up (2026-11-21)

`FilePreviewScreen.kt` delegates its screen surface (top bar, content routes, floating tray, dialog host, and their interactions) to `ui/preview/viewers/PreviewScreenSurface.kt`; its coordinator is 360 lines, within the 500-line budget. Shared state, bounded text operations, and edit-history stack live in `ui/preview/chrome/PreviewScreenState.kt`, `ui/preview/operations/PreviewTextOperations.kt`, and `ui/preview/editing/TextUndoStack.kt`. Local DOCX/preview Node tests pass 24/24 and `git diff --check` passes. Android Gradle assembly could not start because Java/`JAVA_HOME` is unavailable locally; compilation and APK remain unverified pending CI.

## Latest DOCX visual-frame/readiness investigation (2026-11-21)

| Audit ID | Evidence-based finding / hypothesis | Remediation / status |
|---|---|---|
| AUD-DOCX-018 | DOCX custom JS/CSS zoom was active, native WebView zoom disabled, and zoom bounds/full-edge pan lacked native regression coverage. | Remove DOCX custom scale/gesture plumbing; enable native WebView zoom, hide zoom controls, declare 0.5–5.0 viewport bounds, normalize WebView scale against density, observe single taps without consuming Chromium events, and retain native double-tap/pan. Add Kotlin/JS/Android coverage for default, min/max pinch, edge pan, CSS-scaling absence, and tap distinction. Shared overflow menu: clipped 16dp white surface, 8dp shadow, black icons, label-medium 13sp, no Dark Mode; screen coordinator remains <500 lines. | Local Node 22/22, JS syntax and diff checks pass. Exact-HEAD CI [45361178903](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45361178903) passed JVM tests, lint, Detekt, Kotlin compile, APK assembly, bundled JS checks, and API 34 instrumentation on `ac4f32d90805b4ac73617c56adf970988707e087`. GitHub lists artifact `docx-rehabilitation-ac4f32d90805b4ac73617c56adf970988707e087` (ID `14903799945`, 35,964,134 bytes; not expired); ZIP contents and APK entry checksum are unverified. Physical devices and owner files remain unverified. |
| AUD-DOCX-019 | Earlier run [45206520707](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45206520707) ended Unicode/table/search instrumentation in a process crash; follow-up [45217295936](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45217295936) crashed in the 412×915 test, with no detailed XML/renderer evidence. The exact native trigger is unknown. | Fixtures now handle `onRenderProcessGone` and report `RENDERER_UNAVAILABLE`; failure screenshot capture is skipped after a known renderer death. Teardown runs on main, removes the bridge, detaches before destroy, and finishes the test Activity. The crash did not recur in green run [45220772555](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45220772555), but this alone does not prove which test-harness/lifecycle change removed it or root-cause the original intermittent event. |

Run [45222517558](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45222517558) is green on exact HEAD SHA `9551c6a079eb3b92a149d3e391f47ae57f424aff` (code commit `cd7364d892ed7a89a171bb5f21b91253a682b4fa`): unit tests, lint, Detekt, Kotlin compilation, debug APK, API 34 emulator instrumentation, JS checks, and artifact upload passed. Artifact `docx-rehabilitation-9551c6a079eb3b92a149d3e391f47ae57f424aff` (ID `14840528421`, 35,934,989 bytes) is in the run's Artifacts section. Failed earlier runs [45206520707](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45206520707), [45216402319](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45216402319), and [45217295936](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45217295936) are retained as honest debugging history. Local Node tests pass 12/12; Java is unavailable locally. Keep root-cause of the owner's physical blank frame open pending exact-file/device validation.

# Verified application issue register

**Audit date:** 2026-11-21 UTC · **Baseline inspected revision:** `2f6d3492de46b3d8f71515404d170af4e35c8459`
**Baseline scope:** static source/documentation/workflow audit only; the initial audit made no app changes or device reproduction. This register preserves that evidence and is followed by the owner-authorized DOCX remediation/verification update below. “Confirmed from code” means the relevant implementation path is present; it does not imply that an owner-reported symptom was reproduced. Anything not reproduced is explicitly marked accordingly.

Severity definitions: **Critical** = immediate data/security risk; **High** = substantial security, crash, data loss, or core workflow impact; **Medium** = important functional/compatibility limitation; **Low** = contained polish/debt. Initial priorities were audit triage recommendations; owner-authorized DOCX implementation is now in progress/completed as tracked below. No other format is authorized.

## DOCX native zoom and overflow menu follow-up (2026-11-21; working tree)

| Audit ID | Evidence / requested behavior | Working-tree change and regression coverage | Verification / remaining limit |
|---|---|---|---|
| AUD-DOCX-018 | DOCX pinch/double-tap scaling was implemented in JavaScript/CSS with custom gesture logic, while native WebView zoom was disabled; bounds and full-edge pan were not secured by native-zoom coverage. | Remove DOCX custom JS/CSS scaling and Compose pinch plumbing; enable built-in WebView zoom, hide zoom buttons, declare 0.5–5.0 viewport bounds, report native scale, and keep initial scale at 1.0. Single taps are observed without consuming WebView touch events. Add unit/JS contracts and API 34 instrumentation checks for zoom bounds, both horizontal edges, vertical pan, CSS-scaling absence, and tap distinction. | Local Node suite 22/22, viewer JS syntax, and `git diff --check` pass. Run [45342906713](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45342906713) passed JVM tests/lint/Detekt/main Kotlin/APK tasks but failed Android-test Kotlin compilation on a `WebSettings.supportZoom` property/function mismatch; commit `7bb01831ff9ec769e0a99ec46c0e02a0e55ac1e8` corrected it. Follow-up [45343958111](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45343958111) compiled and built the APK but emulator instrumentation found initial `WebView.scale` was 2.625 (device-density baseline), not user zoom 1.0. The policy and host now normalize reported native scale by display density; normalized initial 100% passed in run [45345326640](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45345326640). Further `zoomBy()` testing reported the base 100% scale rather than the lower viewport bound. To cover the actual native touch path instead of relying on that programmatic helper, instrumentation now injects successive two-finger pinch-in/out events and asserts 500%/50% limits. In run [45347717557](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45347717557), two zoom-in pinches reached 4.551×; four gestures then exercised the upper range. Run [45348958110](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45348958110) still reported baseline `WebView.scale` after pinch-out, so the test now reads `visualViewport.scale` directly as Chromium's page zoom and separately checks the density-normalized WebView default. This is a measurement refinement, not proof that the floor works. Run [45350189486](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45350189486) stopped at Android-test compilation because a density variable was scoped inside a lambda; that was corrected. Follow-up [45351277015](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45351277015) compiled but flagged an off-main-thread `WebView.scale` read inside an assertion message; those reads were removed. An owner-provided screenshot now shows the DOCX first page clipped at the right edge compared with the desired centered, fit-width page. The source already declares `initial-scale=1.0`, but the production WebView did not set an explicit initial scale; the implementation calls `setInitialScale(100)` before loading the document and removes an experimental hidden 200vw pseudo-element. The existing Android fixture checks initial `visualViewport.scale`, page width, balanced margins, and no horizontal scroller overflow, and now mirrors the explicit scale setting. Exact-SHA CI [45560939950](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45560939950) passed the initial-scale, page-fit geometry and double-tap assertions using the same explicit scale setup, then failed later at the independent 50% pinch floor (observed 1.0); overall workflow is not green. The owner screenshot itself and exact file have not been reproduced on a physical device, so visual correction still awaits owner confirmation. |
| AUD-UI-003 | Preview overflow popup required one unified implementation and a specified compact white rounded appearance without Dark Mode. | Style the shared popup with clipped 16dp corners, white surface, 8dp shadow, black icons, label-medium 13sp text, and no Dark Mode item; source contract checks assert one popup implementation. Keep the screen coordinator within 500 lines. | Source/Node contract checks pass; Android compilation/UI rendering have not yet run. Shared chrome affects all preview routes; no other viewer implementation was changed. |

### Current fit-width and pinch follow-up (fit-width emulator-verified; pinch unresolved)

The light DOCX surface now uses a `#f0f0f0` canvas and white (`#ffffff`) page cards. `DocxPreviewInstrumentationTest.initialPageFitsAt360x800And412x915` renders the bundled fixture at both viewport sizes and asserts 100% visual viewport scale, page left/right bounds, readable balanced margins, colors, and no horizontal overflow. It passed under GitHub Actions run [45569454759](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45569454759) on SHA `51771c391868af5ab68a94f5a5e5d535fbb5435b`. Removing the explicit `setInitialScale(100)` did not change the lower native zoom bound; the instrumented sequence remained `[2.6041675, 1.3563371, 1.0, 1.0, 1.0, 1.0]`. Thus the fit-width acceptance test is emulator-verified at both requested CSS viewport sizes, but native 50% zoom is still a confirmed failing test. A follow-up tried applying temporary `zoom-anchor-space` at `ACTION_POINTER_DOWN` to create document extent before Chromium processed pinch; run [45571275778](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45571275778) showed the same lower bound and was rejected. That code is removed. The lower-bound root cause remains unresolved; no production workaround is currently claimed. Run 45571275778 still passed 8/9 Android tests and is not green; exact owner-file/physical-device rendering remains unverified. Current local: Node 25/25, JS syntax checks and `git diff --check` PASS; Android instrumentation is not available locally because Java/JDK is absent.

## Latest DOCX zoom and tray investigation (2026-11-21)

| Audit ID | Evidence-based diagnosis / scope | Remediation and regression coverage | Verification / limitation |
|---|---|---|---|
| AUD-DOCX-016 | The DOCX renderer observed `#scroller` with `ResizeObserver`, derived `fitScale` from `scroller.clientWidth`, then changed content overflow with CSS `zoom`. Where scrollbars consume layout width, overflow changes can alter the next measured width and feed fit-scale changes back into resize handling. Repeated same-value zoom application also reapplied style and emitted redundant callbacks. This is a plausible source-level mechanism for opening-time zoom oscillation; the owner’s actual device/file was not reproduced. | Reserve a stable scrollbar gutter, avoid setting the same user zoom again, report scale only when zoom/fit/effective values change, and preserve a centered document anchor while reflowing after viewport rotation. Delay focus-mode single taps; a recognized double tap cancels the pending single and applies zoom directly. Keep the feature DOCX-only. | Local Node zoom-contract tests pass. API 34 WebView instrumentation asserts default 1.0, 0.5–5.0 clamping, stable fit scale, zoom/scroll/document preservation through a portrait-to-landscape-sized viewport resize, and no focus toggle on double tap. Actions run [45205000092](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45205000092) passed on exact SHA `9f2704387d7da92ca17bbe7f1c884381c241cff3`, including unit/lint/Detekt/Kotlin/APK and API 34 instrumentation. Artifact `docx-rehabilitation-9f2704387d7da92ca17bbe7f1c884381c241cff3` (ID `14833163812`) is available. Exact-owner-device confirmation remains pending. |
| AUD-DOCX-017 | DOCX tray controls did not match the requested five-action reading UI. | Model and render exactly `Page → Rotate → Search → Print → Edit`; Edit displays “Coming Soon”. JVM unit test locks the action order. | The same Actions run passed JVM and API 34 emulator checks. Node contract checks pass. No other format tray was changed. |

## Current DOCX rebuild status (2026-11-21; supersedes older implementation details below)

Work is being integrated on `arena/ea756912-file-claw-native`, based on `2f6d3492de46b3d8f71515404d170af4e35c8459`; the remote branch advanced during implementation, so the local feature commit is being rebased onto it before push. The standalone `ui/preview/docx/` subsystem uses pinned offline `docx-preview` 0.4.1 and JSZip 3.10.2. It adds safe local streaming, package validation, a bounded disk cache, cross-run Unicode search, explicit visible-layout readiness, retry/text recovery, fit/zoom/page controls, information, print/share/open-externally, and Android WebView fixtures. Legacy DOCX copies are removed while the shared PPTX JSZip is retained. The existing PrintManager route remains; other viewer routes are preserved in extracted source files.

**Checks to date:** Node search/readiness tests pass **8/8**; JavaScript syntax checks and `git diff --check` pass; instrumentation helper package ID matches Gradle `applicationId`. First run [`45185818904`](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45185818904) on `8f0755d93b687e517213e2f081e02c0a872ff0d6` failed in `:app:compileDebugUnitTestKotlin`: `DocxDocumentLoaderTest` was missing the `DocxPreviewPolicy` import. The import was added. Follow-up run [`45186902054`](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45186902054) on `a0578d19f001c42a8522394eaa203bfa9eb6b4a1` passed JVM tests, lint, Detekt, Kotlin compilation, and APK assembly, then failed the emulator test `bundledViewerRendersVisibleUnicodeTableAndCrossRunSearch` with an instrumentation process crash. GitHub job logs and artifact downloads returned EOF, so the exact cause is not known. The list fixture assertion now checks CSS `list-item` output; that test refinement is not verified as the cause or fix. Current exact-SHA run [`45188692025`](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45188692025) passed JVM tests, lint, Detekt, Kotlin compilation, debug APK assembly, API 34 emulator instrumentation (5/5 tests), Node verification, and artifact upload on `0b69ddb211f7d9135d13ae74e6d27d9f4f75b856`. Artifact `docx-rehabilitation-0b69ddb211f7d9135d13ae74e6d27d9f4f75b856` is 35,893,673 bytes (ID `14823959777`) and is available in the run’s Artifacts section. Physical-device and exact-owner-file behavior remain unverified. No JDK is available locally. The exact owner document/device remains untested; root cause and physical resolution are unconfirmed.

## PROMPT 7 DOCX routing and preview follow-up (2026-11-21; working tree)

Changes are uncommitted on `arena/ea756912-file-claw-native`, based on `b16d9473a8d59752edf56b8182622f1824a15fe0`.

| Audit ID | Confirmed code path / scope | Working-tree change and coverage | Evidence / remaining limit |
|---|---|---|---|
| AUD-DOCX-013 | `FormatDetector`’s signature-expected branch returned `plainText` when a `.docx` header looked textual; `MainActivity.opensInEditor()` then permits editable `TextSuccess` to route to the raw text editor. File-list/search/recent clicks and imported `ACTION_VIEW` content converge on `MainViewModel.openFile`; MIME was retained as `sourceUri` but not consulted during detection. The reported real-device screen is not reproduced. | Force recognized DOCX extensions/MIME through DOCX validation; add extension/MIME/ordinary-text unit cases. Failed render now offers an explicit bounded recovery action rather than automatically presenting extracted text. | The suite ran in CI but failed an existing confidence assertion for a valid `.docx` package; the routing priority is corrected to preserve its HIGH content confidence while retaining corrupt-file routing. Local Gradle is blocked by missing Java. No owner file/device reproduction. |
| AUD-DOCX-014 | Existing tray was a shared preview `bottomBar`; DOCX had zoom/search/print/share but lacked explicit fit/page badge/external-open and theme-token propagation. Existing renderer used hard-coded theme values and `transform` plus synthetic spacer sizing. | Introduce `DocxViewerState`/`rememberDocxViewerState`, bind viewer metrics and print action, add fit/page/open-externally actions, pass FileClaw theme colors into the offline WebView, and use CSS layout zoom without transform/spacer. Extend state and WebView instrumentation coverage. | Four DOCX JS files passed `node --check`; search tests passed 5/5; `git diff --check` passed. Local Gradle could not start because Java/`JAVA_HOME` is absent. CI run [45011759830](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45011759830) found the fallback click-handler invocation error, fixed in commit `c9d2253`. CI run [45012076369](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45012076369) exposed JVM setter clashes; the APIs were renamed. CI run [45012407140](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45012407140) compiled but failed one assertion because structurally valid `.docx` content was reported with MEDIUM instead of HIGH confidence. Routing now preserves the high-confidence content path and only forces DOCX for unverified/corrupt packages. CI run [45012859951](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45012859951) passed unit tests, lint, Detekt, Kotlin compilation, debug APK assembly, JS/search verification, and artifact upload. Its separate emulator/WebView instrumentation step failed with shell exit code 1; log retrieval returned EOF, so the cause is unknown. Physical theme, scrolling, printing, and external-intent behavior remain device checks. |
| AUD-DOCX-015 | Unreferenced `assets/editor/docx-preview.min.js` remained beside the active `docx_viewer` bundle. | Remove only this obsolete DOCX bundle. Keep `assets/editor/jszip.min.js` because `pptx_editor.html` still imports it; retain `DocxExtractor` because print/recovery paths use it. | Reference search found no import of the removed file and confirmed the shared JSZip reference; `git diff` shows only the obsolete DOCX bundle deleted. |


## Owner-authorized DOCX remediation and verification update

The earlier approved DOCX implementation passed CI run `44931079956`; the latest blank-preview follow-up is validated on commit `1842560fe886712f77275c90da4c7c4a8678d09e` by run [44939493750](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/44939493750). Unit tests, lint, Detekt task, Kotlin compilation, debug APK assembly, Android WebView instrumentation, JS tests, and artifact upload passed. This is CI/emulator evidence, not physical-device validation. The owner-reported blank screen has not been reproduced, and root cause remains unconfirmed.

| Audit ID | Baseline root cause / scope | Remediation and regression coverage | Verification and remaining limits |
|---|---|---|---|
| AUD-DOCX-001 | DOCX path eagerly encoded file bytes as Base64 despite local streamed preview. | DOCX content state no longer retains Base64; `WebViewAssetLoader` streams `/docx-src/document.docx`; stream error enters bounded text recovery. Files: `MainViewModel.kt`, `FilePreviewScreen.kt`, `docx_editor.html`. | CI unit/compile/instrumentation passed. Heap profile/large-file device measurement not performed. |
| AUD-DOCX-002 | Recovery inflated an unbounded ZIP XML entry and could block the UI path. | `DocxExtractor` enforces XML, ZIP-scan, character, and paragraph caps; recovery runs on IO and stale-WebView results are ignored. Tests cover XML/scan/text boundaries, paragraph/break/tab content. | CI unit/compile/instrumentation passed. No hostile-file stress/fuzz or physical recovery test. |
| AUD-DOCX-003 | Search inspected individual DOM text nodes and missed phrases split across styled runs. | New literal Unicode case-insensitive matcher joins text-node values, maps ranges back to nodes, groups highlights, and retains bounded navigable matches. Files: `docx_search.js`, `docx_editor.html`. | 5/5 Node tests and CI WebView fixture cross-run search assertions passed. Broader headers/footers/pagination semantics remain untested. |
| AUD-DOCX-004 | Oversized DOCX followed an archive route without a dedicated preview-limit state. | Explicit formatted-preview size policy and DOCX-specific limit UI; no promise of unlimited preview. | Policy unit tests and CI passed. Above-cap real files/low-memory device behavior not measured. |
| AUD-DOCX-005 | Renderer `.docx` nodes were described as pages without verified Word pagination. | Page counts are marked approximate and jump UI explains renderer estimates. | Static/UI implementation compiled in CI; no equivalence to Microsoft Word pagination is claimed or verified. |
| AUD-DOCX-006 | Compositor pinch preview could overflow horizontally and touch cancellation could leave transient state. | Pinch preview clips horizontal overflow; touch end/cancel share cleanup and commit logic. | CI instrumentation exercised resize/render path, not actual pinch gestures. Physical zoom/rotation/viewport behavior remains unverified. |
| AUD-DOCX-012 | Owner reports normal preview blank while toolbar works and print shows content; source root cause is unconfirmed. | Direct attached rendering, scroller/page sizing, visible-content/zoom readiness validation, structured diagnostics, actionable error, forced-offscreen regression. | CI run `44939493750` passed synthetic Android WebView normal/print cases. Exact owner file/device and physical-device resolution remain unverified. |
| AUD-DOCX-008 | No Android/WebView fixture exercised the actual bundled editor; only helper tests existed. | Synthetic OOXML fixture plus instrumentation test exercises streamed local loading, visible content, Hindi/table text, resize, approximate page count, and cross-run search in normal and print-mode WebViews. | CI emulator instrumentation passed. Does not invoke Android `PrintManager`, test cancellation/rotation/recovery, or replace a physical device matrix. |

The prior detailed baseline entries below should be read as findings at the baseline commit; where they say “proposed solution” or “no tests”, the table above supersedes that status for the approved DOCX changes. It does not supersede unrelated-format, release-workflow, or documentation findings.

## A. Confirmed bugs / code-path defects

### AUD-DOCX-001 — Redundant base64 copy retained despite streamed DOCX loading
- **Feature / format:** DOCX open and preview; **severity:** High (memory/performance risk for large documents; exact device impact unmeasured).
- **Affected files/modules:** `app/src/main/java/com/example/viewmodel/MainViewModel.kt:685-695,1312-1313`; `app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt:5038-5042,5055-5071,5101-5103,5161-5172`; `app/src/main/assets/editor/docx_editor.html:438-442,509-536`.
- **Observed behavior:** `webDocument()` first reads the entire file into a `ByteArray` and creates a base64 `String` for every DOCX up to 25 MiB. The DOCX WebView separately streams the same disk file from `/docx-src/document.docx` and calls `loadDocx(null, ...)`; the base64 is not used in the normal path but is retained in the content state and exposed as recovery fallback.
- **Expected behavior:** Normal streamed rendering should avoid an unnecessary whole-file base64 allocation; keep only a deliberate, bounded fallback if necessary.
- **Root cause (evidenced):** The old `webDocument()` base64 transport remains on the ViewModel route after the renderer switched to the custom asset-loader stream.
- **Reproduction:** Open a DOCX ≤25 MiB with memory profiling; compare app heap during load and verify source reads show both full base64 preprocessing and the later stream. **Not reproduced in this environment.**
- **Existing coverage:** `DocxExtractorTest`, `DocxZoomPolicyTest`, `PinchGestureGateTest`, `DocxTextRecoveryTest` (parts of policy/extractor); no test asserts the actual ViewModel/WebView transport or peak memory.
- **Proposed solution:** In the approved DOCX phase, trace and simplify the transport so normal open does not base64-encode the whole document; preserve one explicit bounded fallback and test it. Do not implement in this audit.
- **Regression risks:** DOCX from archive extraction, asset-loader stream failure fallback, renderer retry, and print may depend on the base64 value.
- **Verification:** Unit test the transport decision if separable; device heap profile for near-cap fixture, local-storage DOCX and DOCX extracted from ZIP; force the stream to fail and verify recovery.
- **Evidence / status:** Static code-path confirmed; user-visible OOM/ANR not reproduced; implementation investigation needed before a fix.

### AUD-DOCX-002 — DOCX recovery extraction inflates an unbounded XML entry synchronously on the main thread
- **Feature / format:** DOCX renderer failure recovery; **severity:** High (hostile/malformed archive can cause memory exhaustion or UI stall; impact not experimentally demonstrated).
- **Affected files/modules:** `FilePreviewScreen.kt:5138-5150`; `utils/FilePrintHelper.kt:196-204,255-269,295-300`; `core/format/DocxExtractor.kt:34-45`.
- **Observed behavior:** The WebView bridge posts renderer errors to the main handler, then calls `DocxExtractor.extractText(File(...))`. DOCX print fallback also extracts text in its main-thread callback/timeout path. The extractor finds `word/document.xml` in a `ZipInputStream` and calls `zip.readBytes()` with no inflated-entry or total-byte limit.
- **Expected behavior:** Recovery work should be bounded and should not block UI; hostile compressed input should be refused safely with an honest recovery message.
- **Root cause (evidenced):** Unbounded decompression/read of the OOXML document part plus the call being made inside the main-thread handler.
- **Reproduction:** Provide a small compressed DOCX whose `word/document.xml` inflates beyond the intended safe limit, trigger formatted-render failure, observe time/memory/main-thread behavior. **Not run; do not construct/test on a production device.**
- **Existing coverage:** `DocxExtractorTest` covers basic extraction only; no size/decompression-limit or dispatcher tests.
- **Proposed solution:** After approval, define a conservative extraction limit, enforce it while reading the ZIP entry, and move extraction off the main thread; surface capped failure clearly.
- **Regression risks:** Large but legitimate files; preserving renderer-recovery UX; archive streams from extracted ZIP entries.
- **Verification:** Malformed/oversized and ordinary DOCX fixtures, strict memory/time cap tests, main-thread policy test, device recovery test.
- **Evidence / status:** Unbounded read and main-thread call are confirmed statically; malicious-input impact awaits a safe reproducer.

## B. Incomplete functionality / declared limitations

### AUD-DOCX-003 — Search does not join text across Word styling runs
- **Feature / format:** DOCX find; **severity:** Medium.
- **Affected files/modules:** `app/src/main/assets/editor/docx_editor.html:571-615`.
- **Observed behavior:** Search walks each DOM text node and runs `indexOf()` within that node. A phrase split between adjacent text nodes (commonly caused by separate bold/italic/style runs) cannot match as one query.
- **Expected behavior:** If full rendered-document search is presented, common text spanning formatting runs should be findable or the limitation should be explicitly labeled.
- **Root cause (evidenced):** Search does not form a normalized cross-node text stream or map joined offsets back to DOM ranges.
- **Reproduction:** Create a DOCX with “File” in one style run and “Claw” in another; query `File Claw`. Not reproduced in WebView/device here; algorithm predicts the limitation.
- **Existing coverage:** No checked-in DOM/browser test for search; Kotlin tests cover search command/navigation policy only.
- **Proposed solution:** Consider a DOM text-run mapping/search index only after approval; first establish behavior with a fixture and define punctuation/whitespace semantics.
- **Regression risks:** Search matching counts, highlights around adjacent nodes, table/header/footer behavior and large-document performance.
- **Verification:** Browser fixture test plus device tests for same-run, cross-run, tables, headers/footers, Unicode and next/previous navigation.
- **Evidence / status:** Static behavior evidenced; visual reproduction pending. Existing roadmap/TODO already calls out DOCX search across styled runs.

### AUD-DOCX-004 — DOCX documents larger than 25 MiB are routed to an archive tree, not a document preview
- **Feature / format:** DOCX large files; **severity:** Medium.
- **Affected files/modules:** `MainViewModel.kt:685-695,1312-1313`.
- **Observed behavior:** A file greater than `MAX_WEB_DOCUMENT_BYTES` is sent to `openArchive()` and shown as a ZIP/archive listing rather than rendered as Word content.
- **Expected behavior:** Product behavior should either support larger files within a measured memory budget or clearly disclose the limit before/following open.
- **Root cause (evidenced):** Explicit 25 MiB threshold and archive fallback.
- **Reproduction:** Open a valid DOCX larger than 25 MiB. No such fixture/device test was run.
- **Existing coverage:** No boundary test for `MAX_WEB_DOCUMENT_BYTES` or UI expectation.
- **Proposed solution:** Owner decision: retain and message the cap, or design a measured streamed path. No limit change proposed in this audit.
- **Regression risks:** OOM, renderer load time, archive password handling, status/error-state distinctions.
- **Verification:** Files below/at/above boundary on low-memory and mid-tier Android devices; confirm wording and fallback behavior.
- **Evidence / status:** Route and limit confirmed; user impact not reproduced.

### AUD-DOCX-005 — DOCX page count is a DOM-section estimate, not verified physical pagination
- **Feature / format:** DOCX page display / navigation; **severity:** Medium (accuracy risk).
- **Affected files/modules:** `docx_editor.html:479-497`; `FilePreviewScreen.kt` page callbacks; `docs/TODO.md` current known-issues section.
- **Observed behavior:** The viewer counts `.docx` DOM elements returned by `docx-preview` and reports that count as `totalPages`.
- **Expected behavior:** Page count and navigation labels should correspond to user-visible page boundaries for supported documents, or be described as approximate.
- **Root cause:** Not established as a defect; the code maps renderer section nodes to page count, but section-vs-page semantics and renderer behavior depend on input.
- **Reproduction:** Compare against DOCX fixtures with section breaks, continuous sections, mixed page orientation, long flowing content, tables and headers. Not run.
- **Existing coverage:** No automated fixture test for page-count fidelity. Historical docs explicitly say device page-count accuracy is pending.
- **Proposed solution:** Reproduce and define expected behavior before choosing any change.
- **Regression risks:** Page jump, scroll observer, printed page layout and user expectation.
- **Verification:** Compare known page-break fixtures across Chromium/WebView and Android print output.
- **Evidence / status:** Potential issue requiring reproduction; not classified as confirmed bug.

### AUD-FMT-001 — Several registered formats are identify-only or intentionally partial
- **Feature / formats:** legacy Office, RAR, unsupported 3D, archives and other binary families; **severity:** Medium for expectation mismatch, not automatically a defect.
- **Affected files/modules:** `FormatRegistry.kt:81-90,131-142`; `MainViewModel.kt:621-660`; `docs/FILE_FORMATS.md`.
- **Observed behavior:** `.doc` has text extraction and `.xls` a read-only grid; `.ppt` and other legacy OLE2 formats return identify-only. RAR is identify-only; some 3D formats return identify-only. RTF/ODF are text extraction, not full-fidelity editing.
- **Expected behavior:** UI/docs should distinguish recognized, previewable, read-only, partial extraction and identify-only formats accurately.
- **Root cause:** Intentional capability matrix, not a confirmed defect.
- **Reproduction:** Try `.ppt`, `.doc`, `.xls`, `.rar`, `.glb` and compare exact state/messages; no device test run.
- **Existing coverage:** Registry/parser unit tests exist; no UI test for every identify-only route.
- **Proposed solution:** Maintain an accurate capability matrix; only implement formats after separate approval.
- **Regression risks:** Changing registry support flags can alter detection and routing.
- **Verification:** Registry tests and sample-format open tests.
- **Evidence / status:** Routes confirmed in code. Legacy docs claim all `.doc`/`.xls` are unopenable, contradicting implementation and need correction.

### AUD-SEARCH-001 — Global file search is filename-based, not file-content search
- **Feature:** Device-wide search; **severity:** Low/Medium, depending on product promise.
- **Affected files/modules:** `MainViewModel.kt:173-242,1213-1262`; search screens.
- **Observed behavior:** The index stores paths; query matches `file.name`, and limits are depth 8 / 60,000 files with at most 150 existing results returned.
- **Expected behavior:** If product wording implies in-file text search, that expectation is not met; current implementation is a bounded filename search.
- **Root cause:** By design, current search uses path index/name matching; no content index is present in this path.
- **Reproduction:** Search for a word only present inside an otherwise indexed text file; it will not be a match. Not tested on a device.
- **Existing coverage:** `FileSearchRankerTest` tests ranking; no storage scanner integration or content-search tests.
- **Proposed solution:** Keep the filename-only boundary explicit unless content search is separately approved.
- **Regression risks:** Storage permissions, indexing latency, battery and privacy if scope expands.
- **Verification:** Unit tests for current contract; device test only if product requirements change.
- **Evidence / status:** Static implementation confirmed; current backlog describes this as planned/incomplete.

## C. UI/UX defects / watch items

### AUD-DOCX-006 — Pinch preview may temporarily overflow the horizontal viewport
- **Feature / format:** DOCX pinch zoom; **severity:** Low.
- **Affected files/modules:** `docx_editor.html:302-400` (gesture/preview path); `known-issues.md` Loop 12 watchlist.
- **Observed behavior:** During the compositor transform preview, scaling does not reflow layout until settle; a wide page can temporarily paint outside the scroller before the authoritative CSS zoom commit.
- **Expected behavior:** The pinch preview remains clipped and navigable without transient horizontal escape.
- **Root cause:** The implementation intentionally uses transform-first preview to avoid per-frame re-layout; clipping behavior is not proven across WebView viewport/device sizes.
- **Reproduction:** Pinch a wide-page DOCX on a narrow phone and inspect during active gesture. Not executed here.
- **Existing coverage:** `DocxZoomPolicyTest` and `PinchGestureGateTest` test pure Kotlin policy, not rendered overflow.
- **Proposed solution:** Reproduce on device and constrain only the preview if confirmed.
- **Regression risks:** Gesture smoothness, scroll anchor, horizontal scrolling and text selection.
- **Verification:** Device matrix with narrow/large screens, RTL and wide page layouts.
- **Evidence / status:** Legacy register already records the watch item; not re-reproduced in this audit.

### AUD-WEB-001 — HTML find highlights but has no next/previous match navigation
- **Feature:** HTML viewer search; **severity:** Low.
- **Affected files/modules:** `FilePreviewScreen.kt:4791-4796`; `known-issues.md` BUG-008.
- **Observed behavior:** `findAllAsync()` is invoked/cleared; this path has no navigation commands/count UI equivalent to DOCX/PDF/text viewers.
- **Expected behavior:** Either expose navigation or document the reduced search capability.
- **Root cause:** Search path uses WebView find highlighting only.
- **Reproduction:** Load multi-match HTML and invoke search; attempt previous/next. Not run on device.
- **Existing coverage:** No HTML search UI integration tests.
- **Proposed solution:** Separate future HTML-viewer task; out of DOCX scope.
- **Regression risks:** Executable HTML content and navigation boundaries.
- **Verification:** WebView test with multi-match fixture.
- **Evidence / status:** Static feature gap confirmed; currently already listed as a low watch item.

## D. Performance / memory concerns

### AUD-IO-001 — Open-with imports duplicate full files into app storage
- **Feature:** Content URI import/open; **severity:** Medium for large files.
- **Affected files/modules:** `MainViewModel.kt:1121-1187`; import/open flow.
- **Observed behavior:** The complete input stream is copied into `filesDir/imported` before opening. The app keeps a separate local copy even for large media/documents.
- **Expected behavior:** Large supported files should not silently consume a second full file's worth of storage if a streaming path is feasible; limitations should be communicated.
- **Root cause:** Current import model is copy-then-open; no streaming provider path is used here.
- **Reproduction:** Open a large external content URI and inspect app storage. Not tested.
- **Existing coverage:** Database migration / source URI tests; no large import storage tests.
- **Proposed solution:** The TODO already defers avoiding 500 MiB media copies. Treat as design backlog, not a bug until product requirements say otherwise.
- **Regression risks:** Temporary URI permission lifecycle, random-access viewers, write-back and archive handling.
- **Verification:** Large URI import on API versions and storage providers; measure disk/heap and verify cleanup.
- **Evidence / status:** Static behavior confirmed; resource impact unmeasured.

## E. Security and compatibility risks

### AUD-REL-001 — Manual release workflow interpolates inputs into shell source
- **Feature:** GitHub Actions release; **severity:** High (workflow-token context risk).
- **Affected files/modules:** `.github/workflows/build-and-release.yml:134-145, 250-260, 269-294` (also direct expressions elsewhere).
- **Observed behavior:** `workflow_dispatch` fields such as `version_name`, `version_code`, title/notes and prerelease are expanded by GitHub into `run:` shell bodies; the job has `contents: write` and uses `GITHUB_TOKEN` to publish releases.
- **Expected behavior:** User-controlled inputs should be passed through environment variables/validated rather than interpolated as shell source.
- **Root cause (evidenced):** Direct `${{ inputs.* }}` / `${{ env.* }}` insertion in shell-script text.
- **Reproduction:** Static audit only; do not execute a payload against a privileged workflow.
- **Existing coverage:** No workflow static-security or injection test evident; Actions is manual-only.
- **Proposed solution:** After owner approval, pass inputs through `env`, validate allowed version/prerelease forms, quote values and minimize token scope/permissions.
- **Regression risks:** Release field handling, multiline notes, workflow syntax and published tag names.
- **Verification:** Workflow linter plus a non-executing adversarial fixture and a controlled test release in a safe repository.
- **Evidence / status:** Risky construction is confirmed statically; exploitability depends on workflow dispatch authorization and must not be tested against production.

### AUD-REL-002 — No-secret release path generates a fresh, publicly predictable-signing fallback
- **Feature:** Release APK/AAB; **severity:** High for update continuity / trust; no release was tested.
- **Affected files/modules:** `.github/workflows/build-and-release.yml:91-132`; `app/build.gradle.kts` release signing fallback; `BUILD_GUIDE.md` release section.
- **Observed behavior:** Without configured release secrets/project keystore, the workflow generates an ephemeral keystore at `$RUNNER_TEMP` with fixed password `fileclawpass`; the build script can fall back to the debug key. The generated CI key is not persisted for future runs.
- **Expected behavior:** Public releases use one controlled, stable signing identity; development/sideloading artifacts are clearly distinguished.
- **Root cause (evidenced):** Workflow's generated per-run key and non-secret fallback paths.
- **Reproduction:** Compare signing certificate fingerprints across two isolated workflow runs without secrets. Not performed.
- **Existing coverage:** No signing identity/continuity check evident.
- **Proposed solution:** Require a stable protected signing secret for release publishing, or gate publishing and identify debug artifacts as non-upgradeable test builds.
- **Regression risks:** Lost signing material can prevent app updates; changing keys can break installed-user upgrade path.
- **Verification:** Confirm certificate fingerprint continuity and `apksigner verify` on controlled artifacts.
- **Evidence / status:** Workflow behavior confirmed in source; no APK/release/signature generated in this audit.

### AUD-BUILD-001 — Gradle and local verification scripts are tracked non-executable
- **Feature:** Local development/build workflow; **severity:** Medium.
- **Affected files/modules:** Git file modes for `gradlew` and `tools/local-verify/verify-core.sh` are both `100644`; `BUILD_GUIDE.md:9-20` and contributing docs show direct execution.
- **Observed behavior:** `./gradlew --version` exits 126 with `Permission denied`; direct local verify script also exits 126. CI compensates for `gradlew` by running `chmod +x`.
- **Expected behavior:** Documented commands work on a fresh checkout, or docs explicitly start scripts through a shell.
- **Root cause (evidenced):** Git does not store executable mode for either script.
- **Reproduction:** Direct commands were attempted in the current checkout and failed before executing the scripts.
- **Existing coverage:** Workflow chmod step only; no fresh-clone check.
- **Proposed solution:** Decide whether to commit executable modes or consistently document `bash ./gradlew` (the wrapper script is designed for shell execution). Do not conflate with test results.
- **Regression risks:** Windows checkout behavior and zip-based delivery.
- **Verification:** Fresh checkout command smoke test.
- **Evidence / status:** Confirmed by `git ls-files -s` and direct shell outputs.

### AUD-SUPPLY-001 — JitPack repository is unscoped
- **Feature:** Dependency resolution; **severity:** Medium (supply-chain hardening risk).
- **Affected files/modules:** `settings.gradle.kts` repository block.
- **Observed behavior:** JitPack is enabled without a group/content filter.
- **Expected behavior:** Narrow external repository resolution to the dependency coordinates that require it.
- **Root cause:** Repository declaration has no content filter; actual exploit/ambiguity is not demonstrated.
- **Reproduction:** Static inspection only.
- **Existing coverage:** No dependency repository policy check evident.
- **Proposed solution:** Scope JitPack to the exact required group after verifying dependency coordinates; no dependency changes in this audit.
- **Regression risks:** A too-narrow filter can break resolution or builds.
- **Verification:** Dependency resolution and Gradle dependency insight on a clean cache.
- **Evidence / status:** Configuration confirmed; impact is a risk, not a demonstrated compromise.

### AUD-DATA-001 — Persisted content URI permission lifetime needs platform verification
- **Feature:** Open-with and source URI write-back; **severity:** Medium (possible save-back failure).
- **Affected files/modules:** `MainActivity.kt` incoming intent handling; `MainViewModel.kt:1121-1187` import, `:823-858` URI output; `RecentFileEntity.sourceUri`.
- **Observed behavior:** Import stores `uri.toString()` for later output writes. This path does not call `takePersistableUriPermission`; whether a URI grant survives depends on how the source app/provider supplied the intent flags.
- **Expected behavior:** If write-back is promised, the read/write grant remains valid for the supported lifecycle, or the app reliably falls back with explicit user messaging.
- **Root cause:** Not yet established; intent flags and provider behavior vary.
- **Reproduction:** Open DOCX or editable file from each supported external provider, force-stop/reboot, attempt save/export, inspect grant flags. Not performed.
- **Existing coverage:** Database migration test, not Android URI-grant integration tests.
- **Proposed solution:** Reproduce with real provider grants before changing; do not infer a bug from absence of one API call alone.
- **Regression risks:** Persisted grant lifecycle, privacy, provider-specific `openOutputStream` support.
- **Verification:** Instrumented tests / device matrix across `ACTION_VIEW` and picker URI flows.
- **Evidence / status:** Potential compatibility issue; no confirmed failure.

## F. Documentation inaccuracies / contradictions (verified against source)

### AUD-DOC-001 — Multiple legacy status statements conflict with current implementation
- **Feature:** Engineering continuity; **severity:** Medium.
- **Affected files/modules:** `docs/TODO.md:6-15,38-44`; `known-issues.md:15-16,62-72,100`; `docs/FILE_FORMATS.md:18-19`; `BUILD_GUIDE.md:16`; `docs/prompts/README.md`; current source paths cited above.
- **Observed behavior:** TODO says external-file edits save only to in-app copy despite URI write-back code; says DOCX still uses the old whole-file base64 transport despite a stream handler in the viewer; says `.doc`/`.xls` cannot open though parser routes exist; says PDF corrupt files spin despite explicit later fix notes; README index calls Loop 9 active while its prompt is Loop 15; test totals (91/134/205/225) vary across docs and are not a current test result.
- **Expected behavior:** One current status document and a register distinguish current source evidence from historical claims and device-pending work.
- **Root cause:** Long-lived prompt/changelog/backlog snapshots have not been reconciled as source evolved.
- **Reproduction:** Compare listed paths/claims to `MainViewModel.kt`, `FilePreviewScreen.kt`, `FormatRegistry.kt`, `docs/prompts/NEXT_LOOP_PROMPT.md`; current source contradicts the statements above.
- **Existing coverage:** This audit's source cross-check; no doc consistency checker.
- **Proposed solution:** Keep this register and `PROJECT_STATUS.md` authoritative; update old docs carefully, preserving historical dated records.
- **Regression risks:** Erasing historical context or promoting a code path to runtime-verified status.
- **Verification:** Re-check each changed claim against current code and test output.
- **Evidence / status:** Contradictions are confirmed; only the audit's new status docs were authored here, legacy docs are retained pending careful reconciliation.

### AUD-DOC-002 — Third-party bundled JavaScript license verification remains open
- **Feature:** Legal/release compliance; **severity:** Medium.
- **Affected files/modules:** `THIRD_PARTY_NOTICES.md`; `app/src/main/assets/docx/`, `pptx/`, `xspreadsheet/`.
- **Observed behavior:** Notice table marks docx-preview, pptx-preview and x-spreadsheet license claims “verify”; no shipped license text is recorded for these libraries. For DOCX specifically, the public `VolodymyrBaydalka/docxjs` repository and package metadata identify Apache-2.0, but the local `docx-preview.min.js` artifact’s exact version/source is unresolved. Its hash did not match npm bundles 0.3.6, 0.3.7, 0.4.0 or 0.4.1 compared in this audit.
- **Expected behavior:** Confirm exact bundled artifact provenance and include required notices before public release.
- **Root cause:** Not established; exact local bundle provenance has not been identified.
- **Reproduction:** Hash comparison against those fetched npm packages; this is a provenance check, not a rendering test.
- **Existing coverage:** No automated notice/provenance inventory check evident.
- **Proposed solution:** Trace the original vendoring source/build and verify the precise license/version; do not make a legal assertion based only on a similarly named upstream project.
- **Regression risks:** Wrong upstream revision attribution and missing notice conditions.
- **Verification:** Record exact upstream commit/version, license text and packaged-notice check.
- **Evidence / status:** Upstream Apache-2.0 metadata verified for the repository; the local bundle is not matched, so the release-compliance question remains open. This is not a conclusion that the local artifact is unlicensed.

### AUD-REL-003 — Manual debug builds are published through the same GitHub Release path
- **Feature:** Manual release workflow; **severity:** Medium.
- **Affected files/modules:** `.github/workflows/build-and-release.yml:17-23,157-176,269-294`.
- **Observed behavior:** The workflow offers `build_type: debug` as a valid choice, builds and packages a debug APK for that choice, then proceeds to the same unconditional `Publish GitHub Release` step that creates/updates tag `v<version>`.
- **Expected behavior:** Debug artifacts should be clearly distinguished as test artifacts and should not be published as production releases by accident.
- **Root cause (evidenced):** Release publication has no condition gating on `inputs.build_type == 'release'`.
- **Reproduction:** Inspect the workflow path for `build_type=debug`; no workflow was run.
- **Existing coverage:** No workflow validation test found.
- **Proposed solution:** Owner decision; gate publishing or change artifact channel/labels for debug builds in a separate approved release workflow task.
- **Regression risks:** Existing debug-download workflow users and release tagging expectations.
- **Verification:** Dry-run workflow on a test repository and verify debug mode cannot create/update a production GitHub Release.
- **Evidence / status:** Static workflow path confirmed; no release was created in this audit.

### AUD-CI-001 — No routine pull-request/push verification workflow
- **Feature:** Engineering / CI; **severity:** Medium.
- **Affected files/modules:** `.github/workflows/build-and-release.yml:1-5`; repository workflow inventory.
- **Observed behavior:** The only workflow is manually dispatched and builds/publishes a selected release; there is no checked-in push/pull-request workflow running tests, lint or compilation.
- **Expected behavior:** Changes should have a repeatable automated verification gate before release or merge, if that is the team's intended policy.
- **Root cause:** No CI workflow other than the manual release workflow exists in `.github/workflows/`.
- **Reproduction:** Inspect all workflow files; only `build-and-release.yml` is present.
- **Existing coverage:** Manual workflow does not run test/lint tasks; no automated PR CI found.
- **Proposed solution:** Owner decision on CI requirements; if approved, add an appropriately scoped verification workflow in a separate workflow task. Not implemented in this audit.
- **Regression risks:** CI SDK availability, dependency/network restrictions, long job times and false-green conditions.
- **Verification:** Trigger on a test PR and verify exact SHA's tests/analysis/build outcomes.
- **Evidence / status:** Workflow inventory confirms gap; this is a process gap, not an application runtime defect.

## G. Potential issues requiring further reproduction

### AUD-TEST-001 — Critical Android integration paths have no checked-in instrumentation tests
- **Feature:** Test coverage / app reliability; **severity:** Medium.
- **Affected files/modules:** `app/src/androidTest/` is absent; Android flows include `MainActivity`, `MainViewModel`, `FilePreviewScreen`, WebView bridges, content URI import/write-back, Room and `FilePrintHelper`.
- **Observed behavior:** The checkout has 32 JVM test files (222 `@Test` annotations) but no Android instrumentation test files. Existing DOCX tests cover extractors/policies and not the complete Compose → ViewModel → WebView → bridge → print flow.
- **Expected behavior:** High-risk Android integration flows should have repeatable tests in addition to pure Kotlin policy tests and manual device checks.
- **Root cause:** No instrumentation tests are present; reason/intent is unknown.
- **Reproduction:** `find app/src/androidTest -type f` returns no files; no instrumented suite is configured in the current source inventory.
- **Existing coverage:** Pure Kotlin unit tests exist; current Gradle run and coverage unavailable in this environment.
- **Proposed solution:** Prioritize integration tests after the target behavior is specified; this audit does not add tests or dependencies.
- **Regression risks:** Emulator/SDK maintenance, flaky WebView/print tests and longer CI.
- **Verification:** Add an emulator test covering open/render/recovery/back navigation; add storage URI/save/print tests as approved, and run exact CI SHA.
- **Evidence / status:** Test inventory confirmed; coverage percentage and runtime failure rates unknown.

1. **DOCX page count and mixed layout:** see AUD-DOCX-005. Requires representative generated fixtures and Android WebView comparison.
2. **DOCX pinch clipping / focus gestures / lifecycle:** see AUD-DOCX-006 and `known-issues.md` watchlists. Not device-verified.
3. **DOCX content families:** registry sends `.docx`, `.docm`, `.dotx`, `.dotm` to the DOCX route, but there is no format-specific integration fixture for each; verify macro-enabled/template package opening and expected view-only behavior before calling it broken.
4. **Content URI source write-back:** see AUD-DATA-001; provider and grant-specific.
5. **Import/large file handling:** see AUD-IO-001; disk use and failure states require device/provider tests.
6. **PDF / other formats:** historical watchlists contain unverified implementation claims; this audit did not reproduce them. They are not DOCX scope and should remain untouched in the next phase absent approval.

## Coverage snapshot (inventory only)

- 32 Kotlin unit-test files; 222 static `@Test` annotations; no `androidTest` files.
- DOCX-related test files include `DocxExtractorTest`, `DocxZoomPolicyTest`, `DocxTextRecoveryTest`, `PinchGestureGateTest`, and broader `FormatDetectorTest` / `FormatRegistryTest` / `DocumentPreviewSupportTest`.
- No Android Gradle tests ran in this environment, so no coverage percentage, passing test count, or runtime conclusion is claimed.

## DOCX rehabilitation addendum (2026-11-21)

Expanded source trace, renderer limitations, test inventory, repair plan, compatibility matrix and architecture assessment are in `docs/FILE_FORMAT_REHABILITATION.md`. The blank-preview follow-up is owner-authorized and implemented with synthetic WebView coverage, but the owner’s device symptom remains unreproduced and the root cause unconfirmed. The issue entries below distinguish source findings, emulator evidence, and unverified device behavior.

### AUD-DOCX-007 — Search count exceeds retained navigable/highlighted matches after 5,000
- **Feature / format:** DOCX in-document search; **severity:** Medium (result-count/navigation contract ambiguity).
- **Affected files/modules:** `app/src/main/assets/editor/docx_editor.html:545-553,571-639`; `FilePreviewScreen.kt` DOCX search callback/UI.
- **Observed behavior:** JavaScript caps `foundMatches`/`searchMatches` at 5,000 while incrementing `totalCount` for every matching occurrence. It reports the full total, but next/previous operations wrap over only the stored matches and current index is based on that smaller collection.
- **Expected behavior:** Count and navigability should be consistent or the UI should explicitly say that only the first N results are navigable.
- **Root cause (evidenced):** Separate uncapped total counter and capped retained mark list.
- **Reproduction:** Browser fixture with at least 5,001 matches; compare bridge count/current index and navigate past the retained range. **Not run here; source behavior only.**
- **Existing coverage:** No DOM/browser test; Kotlin policy/helper tests do not cover JS cap behavior.
- **Proposed solution:** Owner chooses explicit capped result semantics or a different bounded indexing strategy after measuring large-document cost. Do not remove the cap without performance evidence.
- **Regression risks:** Memory/DOM growth, wrap/index labels, highlight mutation time and search responsiveness.
- **Verification:** Browser test at 4,999 / 5,000 / 5,001 and much larger counts, then Android WebView UI test.
- **Evidence / status:** Static code fact; user-visible mismatch requires runtime reproduction.

### AUD-DOCX-008 — DOCX end-to-end coverage remains partial (updated 2026-11-21)
- **Feature / format:** DOCX open/render/print; **severity:** Medium (remaining regression-detection gap).
- **Affected files/modules:** `app/src/androidTest/java/com/example/ui/screens/DocxPreviewPipelineTest.kt`, `app/src/main/assets/editor/docx_editor.html`, `FilePreviewScreen.kt`, `FilePrintHelper.kt`.
- **Observed behavior:** The earlier helper-only test gap is partially addressed by generated OOXML fixtures rendered through the actual bundled editor and local `WebViewAssetLoader` in Android WebView. Coverage includes normal/print modes, representative text/table/image/orientation/explicit-page-break content, 360×800 and 412×915 CSS viewports, fetch bytes, visible geometry/zoom/readiness, and a forced offscreen-page failure. It is not a Compose navigation or actual PrintManager job test.
- **Root cause:** Not applicable to this coverage gap.
- **Existing coverage:** CI run `44939493750` on `1842560fe886712f77275c90da4c7c4a8678d09e` passed unit/lint/Detekt/Kotlin compile/debug APK and pinned-emulator instrumentation. Five Node search tests pass locally. No physical-device, rotation/lifecycle, cancellation, memory, accessibility, or owner-file test was performed. Java is unavailable locally.
- **Proposed solution:** Keep the synthetic suite and add focused device/app lifecycle cases only after measuring the owner-reported scenario. Do not describe this as complete format conformance.
- **Regression risks:** WebView/runtime variation; emulator fixture behavior may not represent actual device viewport or PrintManager behavior.
- **Evidence / status:** Synthetic integration gap is partially closed; owner-device symptom remains unverified. Details and artifact link are in `FILE_FORMAT_REHABILITATION.md`.
### AUD-DOCX-009 — ZIP detector only inspects the first 256 central-directory names for OOXML refinement
- **Feature / format:** DOCX format detection; **severity:** Low/Medium (edge-case misrouting risk).
- **Affected files/modules:** `app/src/main/java/com/example/core/format/FormatDetector.kt:362-402`.
- **Observed behavior:** `inspectZip()` stops after collecting 256 names; `refineZip()` identifies DOCX only if exact `word/document.xml` is in that collected list. Other ZIP-family logic can fall back to the extension’s registered ZIP family.
- **Expected behavior:** Either valid OOXML package routing is robust to legal ZIP entry ordering or the limitation is understood and tested.
- **Root cause:** Fixed 256-name scan limit; no package-level OOXML content-type check on this path.
- **Reproduction:** Build a valid DOCX central directory with more than 256 entries before `word/document.xml`, use a misleading or neutral extension, and inspect detected spec. **Not run; suspected routing edge, not confirmed end-to-end.**
- **Existing coverage:** General detector tests; no discovered fixture targeting this entry-count boundary.
- **Proposed solution:** Reproduce first; if real valid package is misrouted, choose a bounded/robust package-disambiguation strategy without scanning/decompressing arbitrary unbounded contents.
- **Regression risks:** Detection CPU/IO cost, malformed archive handling and misclassifying other ZIP families.
- **Verification:** Test valid ordered/unusual-order DOCX, unrelated ZIP, malformed directory and extension mismatch.
- **Evidence / status:** Scan limit and exact-name condition confirmed statically; practical impact awaits a valid fixture.

### AUD-DOCX-010 — Text recovery extractor is deliberately narrow and its break/tab handling is not fidelity-verified
- **Feature / format:** DOCX degraded text recovery and fallback printing; **severity:** Low/Medium (recovered content fidelity risk).
- **Affected files/modules:** `app/src/main/java/com/example/core/format/DocxExtractor.kt:34-103`; `FilePrintHelper.kt:196-227`.
- **Observed behavior:** Extractor uses regexes for literal `<w:p>`, `<w:t>`, `<w:br/>`, and `<w:tab/>`, then concatenates `<w:t>` fragments. The current implementation replaces break/tab tags in paragraph content with literal newline/tab characters before searching for `<w:t>` nodes; those inserted separators are not appended to the text builder. It also does not use namespace-aware XML parsing or relationship traversal.
- **Expected behavior:** Recovery/print fallback must label its best-effort nature and either retain expected line/tab boundaries or document the text-only limitations.
- **Root cause (evidenced):** Selected-tag regex extraction and separator replacement before text-tag matching.
- **Reproduction:** Synthetic fixture with break/tab between `<w:t>` nodes, XML prefix variation and XML whitespace preservation; compare exact extracted output. **Not run in this investigation.**
- **Existing coverage:** Existing synthetic tests cover two simple paragraphs and basic entities only; no line break/tab or namespace-variation assertion.
- **Proposed solution:** After approval and bounded-read changes, specify recovery semantics and test them; do not present it as a formatted-document substitute.
- **Regression risks:** Paragraph boundaries, whitespace/entities, malformed XML behavior, print fallback appearance and performance.
- **Verification:** Expected-text tests for paragraphs, inline breaks/tabs, whitespace, namespace changes, entities and empty/corrupt input.
- **Evidence / status:** Source path and test omissions confirmed; output mismatch requires a focused test/reproduction.

### AUD-DOCX-011 — Bundled docx-preview artifact version/provenance is unresolved despite known upstream license
- **Feature / format:** WebView renderer maintenance and third-party compliance; **severity:** Medium before public distribution.
- **Affected files/modules:** `app/src/main/assets/editor/docx-preview.min.js`, duplicate `app/src/main/assets/docx/docx-preview.min.js`, `THIRD_PARTY_NOTICES.md`.
- **Observed behavior:** Editor and `docx/` copies are byte-identical. The editor bundle hash `a94a542f2d2180bca47c2d11df9590cc23825b8190b0052255e1280c61f7f76d` did not match npm `docx-preview` bundles 0.3.6, 0.3.7, 0.4.0 or 0.4.1 checked during this audit. Upstream `VolodymyrBaydalka/docxjs` identifies Apache-2.0 in its README/package metadata and repository license, but exact local artifact provenance/version is still unknown.
- **Expected behavior:** Record exact bundle source/version/hash and include required notices/license text for the shipped artifact; avoid attributing a tested upstream feature set to an unidentified local bundle.
- **Root cause:** Not established; no local source map/version marker or matching checked package was found in this limited comparison.
- **Reproduction:** Hash comparison is repeatable; not a runtime rendering issue.
- **Existing coverage:** `THIRD_PARTY_NOTICES.md` currently marks the upstream license “verify”; no automated bundle provenance check evident.
- **Proposed solution:** Trace repository history/vendor source/build artifact or obtain authoritative provenance. Update notices only when the exact source is established; no library upgrade is proposed.
- **Regression risks:** Incorrectly pinning an unrelated upstream release, altering the bundle or forgetting legal notices.
- **Verification:** Reproducible artifact hash/source mapping, exact upstream license and shipped notice check.
- **Evidence / status:** Hashes and public upstream metadata inspected; bundled version/source remains unresolved. This is not a conclusion that the artifact is unlicensed.

### AUD-DOCX-012 — Owner-reported blank normal DOCX preview (root cause unconfirmed)
- **Feature / format:** DOCX normal preview; **severity:** High (reported complete blank content area while toolbar remains usable).
- **Affected files/modules:** `app/src/main/assets/editor/docx_editor.html`, `app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt`, `MainViewModel.kt`, `FilePrintHelper.kt`, `DocxPreviewDiagnostics.kt`.
- **Owner report:** Normal preview is blank with toolbar working; printing the same DOCX shows content. The picture-in-picture video at the screenshot edge is a YouTube overlay, not document content.
- **Source evidence before change:** Renderer rendered in an offscreen hidden staging node, transferred its children to the visible output, sized against window dimensions, and called ready without verifying final visible page/content geometry. This is an evidenced implementation pattern, not a confirmed causal diagnosis.
- **Changes:** Render directly in attached output; measure fit against scroller/page; validate page/content intersection, dimensions and positive effective zoom after animation-frame layout synchronization; delay Android ready until valid; show an actionable error when render succeeds without visible content; add privacy-conscious Kotlin/JS diagnostics and normal/print synthetic WebView fixtures.
- **Expected behavior:** A visible document page/text precedes the ready callback and loading-overlay dismissal; otherwise show the error/recovery action. Normal preview and print use the same local editor/renderer but separate WebViews and layout/zoom configurations.
- **Reproduction / root cause:** The owner’s file/device was not available for a captured run. **Not reproduced here; root cause remains unconfirmed.** No actual owner-file HTTP/asset byte trace or before/after diagnostic payload exists.
- **Verification:** CI run [`44939493750`](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/44939493750), exact implementation SHA `1842560fe886712f77275c90da4c7c4a8678d09e`, passed all JVM/lint/Detekt/compile/APK/Android WebView/JS steps. Synthetic tests include forced page-offscreen rejection, normal/print fixture runs, 360×800 and 412×915 CSS viewports, local fetch status/bytes, representative document content, portrait/landscape, image and explicit multi-page input. Five Node search tests passed locally.
- **Limitations / status:** Emulator fixtures verify the implemented checks but do not prove the owner’s issue is fixed. Physical-device verification of the exact file, WebView version, print path, rotation and callback/log sequence remains mandatory before a resolution claim. CI artifact is `docx-rehabilitation-1842560fe886712f77275c90da4c7c4a8678d09e` (35,786,947 bytes; APK/reports) and is linked from the Actions run. See `FILE_FORMAT_REHABILITATION.md` for physical-device steps.

### AUD-DOCX-016 — Double-tap focal point and post-rotation fit recalculation (verification pending)
- **Feature / format:** DOCX WebView native zoom and responsive fit; **severity:** High (reading position and usable width after gesture/orientation change).
- **Expected behavior:** A double tap zooms around the document point under the tap; confirmed single taps/Focus Mode remain separate. A changed container width triggers one debounced fit recalculation after layout settles without re-rendering, blink or observer loop, preserving normalized vertical reading progress. Fit uses measured available width less the 12px side gutters divided by natural page width; native zoom bounds and pinch-floor coverage remain intact.
- **Changes in current working tree:** Focal coordinates include `visualViewport.offsetLeft/offsetTop`; fit derives from scroller width and measured natural page width; `ResizeObserver` schedules a debounced refit and restores scroll ratio; new Node width cases span 320, 360, 412, 480, 800 and 915px; Android instrumentation adds off-center anchor and container-resize/scroll-ratio assertions.
- **Verification at time of update:** Node suite **31/31 PASS**, six DOCX JavaScript `node --check` checks **PASS**, `git diff --check` **PASS**. Android compilation/emulator instrumentation and exact-SHA Actions status are **pending** because local Java/Android SDK are unavailable. No APK is verified/shareable yet; retain the double-tap anchor and pinch-floor assertions even if CI exposes a regression.
