# Project status and audit baseline

**Audit date:** 2026-11-21 (UTC)
**Checkout:** `arena/ea756912-file-claw-native` at `2f6d3492de46b3d8f71515404d170af4e35c8459` (`chore: ignore gradle build cache files`)
**Baseline:** this commit was the available `main` / `origin/main` tip at the time of the original audit. The initial working tree was clean and that audit made no application changes. A later owner-authorized DOCX implementation and CI verification are recorded in the dated follow-up at the end of this document; that follow-up supersedes the original “no device/test” status for the specific covered paths only.

## Current DOCX rebuild working-tree update (2026-11-21)

The current branch is rebuilding the DOCX viewer after the design checkpoint; this update supersedes older source-map and test-path descriptions below for the DOCX-only subsystem. New sources are under `app/src/main/java/com/example/ui/preview/docx/`; local renderer assets are under `app/src/main/assets/docx_viewer/`. `FilePreviewScreen.kt` routes only the DOCX success case to the new composable. `FilePrintHelper.kt` uses the same local HTML/assets/stream and keeps the existing bounded extractor fallback. The old DOCX-specific screen, editor HTML/search assets, duplicate DOCX libraries and obsolete Android fixture test were removed. The shared `editor/jszip.min.js` remains because PPTX still uses it. No other format viewer source was changed.

Verification: Node syntax checks passed for both new handwritten JS files and both pinned bundles; Node search tests passed **5/5**; `git diff --check` passed. Local Gradle cannot run (`java`/`JAVA_HOME`/Android SDK absent). CI run [45005699103](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45005699103) on `08f00ca8622db101cc18afdd65e6127bc609faac` passed the combined unit-test, lint, Detekt, Kotlin compile and debug APK assembly step; Node verification passed. The Android instrumentation step had one assertion failure because the print fixture expected an extra callback not required by the production bridge; that redundant expectation was removed for rerun. Actions uploaded a 35,901,113-byte artifact; download of its zip failed with `EOF` from the artifact blob host, so the APK filename could not be independently listed. Physical-device behavior remains unverified.

## Executive summary

File Claw is an existing single-module Android application built with Kotlin, Jetpack Compose, Room, Android platform APIs, and bundled local JavaScript/WebView renderers. It supports a wide format registry; its format coverage is mixed: some formats have full viewers, some are text-extracted/read-only, and some are recognized but intentionally identify-only. DOCX has both a bundled `docx-preview` WebView renderer and a pure-Kotlin text-recovery extractor.

This audit established source-level behavior and a static baseline, not a green build. The checkout has no Java, Kotlin compiler, Android SDK, or connected device. Gradle tests/build/lint/detekt therefore could not be executed here. Node-based syntax checks for the DOCX inline JavaScript and an XML parse of the manifest did pass. No APK was produced and no Android runtime behavior was verified.

## Skill / instructions status

- Inspected upstream `README.md`, `skills/karpathy-guidelines/SKILL.md`, `CLAUDE.md`, `EXAMPLES.md` (all content chunks), `CURSOR.md`, `.cursor/rules/karpathy-guidelines.mdc`, and repository/skill directory listings. The four principles are Think Before Coding, Simplicity First, Surgical Changes, and Goal-Driven Execution; examples emphasize clarifying assumptions, minimal scope, matching existing style, and reproducible tests.
- The upstream README and skill frontmatter identify the material as MIT. GitHub's root file listing did not include a `LICENSE` file; GitHub's license API returned 404. This audit therefore records the stated license and missing standalone license file rather than claiming to have inspected an included LICENSE text. Attribution is retained in `AGENTS.md`; no upstream skill source/plugin was installed.
- Upstream documented installs are a Claude Code plugin or per-project `CLAUDE.md`; Cursor support is a `.cursor/rules/` file with `alwaysApply: true` plus optional personal skill-directory copying. These are tool-specific, not Arena instructions. This Arena Agent Mode session exposes no native skill-install or activation tool; this repo uses `AGENTS.md` as project-level instructions. The current active agent read/followed it, but automatic AGENTS.md discovery by all future Arena agents is unverified.
- Adapted the verified principles into root `AGENTS.md` with the owner's project-specific rules. No unrelated plugin, dependency, or executable script was installed.

## Repository architecture (implementation cross-checked)

- Gradle root with one Android app module (`:app`); Kotlin 1.9.22, AGP 8.4.2, Gradle wrapper 8.7, Java target 17, `compileSdk`/`targetSdk` 34 and `minSdk` 26 (`app/build.gradle.kts`, version catalog and wrapper properties).
- Android entry / Compose navigation: `MainActivity.kt`, `FileClawApp.kt`; application state and file-routing: `viewmodel/MainViewModel.kt` (about 1,315 lines); filesystem and archive operations: `services/FileManager.kt`.
- Pure Kotlin core: detection/registry, archive safety, parsers, bounded text I/O/search, file operations, and format-specific policies under `app/src/main/java/com/example/core/`.
- UI: Compose screens/components/themes under `ui/`; `FilePreviewScreen.kt` is about 5,390 lines and contains several viewer implementations and WebView bridges.
- Persistence: Room `AppDatabase` v4, recent-file/settings entities and DAOs; migration 3→4 adds `sourceUri`.
- Resources and viewers: local Android resources plus about 198 files in `app/src/main/assets/`, including PDF.js, DOCX/PPTX/spreadsheet JavaScript bundles and document viewer HTML. The app manifest has storage/media permissions and no `INTERNET` permission.
- Tests: 32 Kotlin test files under `app/src/test`; static count is 222 `@Test` annotations. No `app/src/androidTest` test files are present in this checkout. This count is not a test run or coverage result.

## Baseline evidence

| Check | Result | Evidence / limit |
|---|---|---|
| Initial Git status | PASS, clean | `git status --short --branch` showed branch only; no modified/untracked files before audit. |
| Recent history / base | Limited | One reachable/grafted commit; `main` and `origin/main` point at the same SHA. Earlier commit/test claims could not be independently checked from this shallow checkout. |
| Toolchain probe | BLOCKED | `java` and `kotlinc` absent; `ANDROID_HOME` and `ANDROID_SDK_ROOT` unset; no `adb`. Node and Python are available. |
| Direct `./gradlew --version` | NOT RUN | Fails before wrapper startup: file mode is `100644`, so shell reports `Permission denied`. |
| `bash ./gradlew testDebugUnitTest --no-daemon` | NOT RUN (environment blocker) | Wrapper started but exited: `JAVA_HOME is not set and no 'java' command could be found in your PATH.` No tests executed. |
| `./tools/local-verify/verify-core.sh` | NOT RUN | Also tracked mode `100644`; direct invocation failed `Permission denied`. Do not change mode solely to imply verification. Its requirements also include Java/Kotlin. |
| Unit test result | NOT EXECUTED | No current pass/fail count available. Static inventory: 222 `@Test` annotations in 32 test files. |
| Lint / detekt / JaCoCo / Kotlin compilation | NOT EXECUTED | Requires Gradle + JDK and Android SDK/dependencies. detekt is configured `ignoreFailures = true` in the build. |
| Debug APK / release APK or AAB | NOT BUILT | No current build outputs found under `app/build`. Historical `build.log` is tracked but has no conclusive completed build marker; it is not treated as evidence. |
| DOCX JavaScript syntax | PASS (static only) | Extracted all three inline `<script>` blocks from `app/src/main/assets/editor/docx_editor.html`; `node --check` passed each. Does not verify WebView, DOM behavior, rendering, or Android bridge. |
| Android manifest XML | PASS (static only) | Python `xml.etree.ElementTree` parsed `app/src/main/AndroidManifest.xml`. |
| Device / emulator | NOT EXECUTED | No emulator/device toolchain. DOCX rendering, permissions, printing, lifecycle, performance and accessibility need runtime reproduction. |

## Workflow / CI assessment

- `.github/workflows/build-and-release.yml` is the only workflow in `.github/workflows/`. It is `workflow_dispatch` only, with release/debug and AAB inputs; it does not run PR/push unit tests, lint, static analysis, coverage, or APK build as a routine CI gate.
- The manual workflow sets Java 17, uses the runner Android SDK, makes `gradlew` executable, builds a selected APK and optional AAB, stages checksums, uploads artifacts, then creates/updates a GitHub Release. The `debug` build choice also reaches the same unconditional release-publishing step.
- The workflow has a fallback path that creates a fresh CI keystore with a fixed password when secrets/project keys are absent. Release signing and upgrade continuity need owner review. Several workflow inputs are interpolated directly into shell script source; see `AUD-REL-001` through `AUD-REL-003` in `ISSUE_REGISTER.md`.
- The repository guide claims direct `./gradlew ...` commands, but both `gradlew` and `tools/local-verify/verify-core.sh` are tracked non-executable. Workflow compensates for the wrapper with `chmod +x`; local docs do not consistently do so.
- Release signing/build behavior was reviewed statically only. No Actions run, published artifact, signing identity, or artifact checksum was verified in this audit.

## Authorized DOCX follow-up (2026-11-21)

The owner authorized scoped DOCX rehabilitation. The implementation is recorded in `FILE_FORMAT_REHABILITATION.md`; the source-audited issue mapping is `ISSUE_REGISTER.md`. Keep the renderer/offline architecture and read-only behavior; do not expand into PDF or other formats.

- Local checks: Node search regression tests passed 5/5; inline JS syntax checks, manifest XML parsing, and `git diff --check` passed. `bash ./gradlew :app:testDebugUnitTest --no-daemon` was attempted and blocked before Gradle startup because this sandbox has no Java.
- First remote verification run `44925707980`, commit `d3c4d2ae9fb8124c780798bdd5150fb9891f1a36`: JVM tests, lint, Detekt task, Kotlin compilation, debug APK build, bundled-JS checks, and artifact upload all succeeded. The emulator/instrumentation step failed with exit 127 before tests. Missing SDK tools on PATH was a plausible initial explanation, so the workflow appended platform-tools and emulator directories to `GITHUB_PATH`.
- Earlier emulator runs failed to register/boot. The successful workflow now enables KVM and uses a SHA-pinned emulator-runner action. Run `44931079956` passed all steps on commit `c561dbae50be7d81ba03f7293f9ead952fd842ba`: JVM unit tests, lint, Detekt task, Kotlin compile, debug APK, Android WebView instrumentation, JS regressions, and artifact upload. No CI annotations indicated test failures. The immediately preceding APK artifacts from failed runs were not delivered.
- Successful run artifact: `docx-rehabilitation-c561dbae50be7d81ba03f7293f9ead952fd842ba` (35,748,211 bytes; debug APK and reports), available from https://github.com/Rohitjjn/File-Claw-native-/actions/runs/44931079956. The sandbox cannot fetch the artifact ZIP from GitHub's external artifact-storage host, so it is linked at the Actions run rather than copied into the workspace. No release was published.
- Print dialog/cancellation, renderer-failure recovery, rotation/lifecycle, heavy-file behavior, and owner-device symptoms have not been physically reproduced or verified. The instrumented fixture establishes only the covered preview/render/search scenarios. No user-visible bug root cause has been confirmed.

## Latest DOCX blank-preview follow-up (2026-11-21)

The owner authorized investigation and repair of the normal-preview blank screen only; no PDF/other-format scope was added. Current branch is `arena/ea756912-file-claw-native`. Implementation commit `1842560fe886712f77275c90da4c7c4a8678d09e` passed GitHub Actions run [`44939493750`](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/44939493750): JVM unit tests, lint, Detekt task, Kotlin compilation, debug APK build, pinned Android emulator WebView instrumentation, bundled-JS checks, and artifact upload. Uploaded artifact `docx-rehabilitation-1842560fe886712f77275c90da4c7c4a8678d09e` is 35,786,947 bytes and contains the debug APK/reports. The sandbox cannot download from GitHub’s external artifact-storage host; use the run’s Artifacts panel. Local Gradle tests remain blocked because Java is absent, but CI ran them successfully.

The implementation removes the hidden staging-to-visible node transfer, uses attached output/scroller measurements, delays ready until visible layout/content/positive zoom is validated, presents an actionable render error, adds content-free structured diagnostics and normal/print fixture coverage. The emulator suite covers synthetic basic/Hindi/table/bold, image, portrait/landscape and explicit multi-page documents, local status/bytes, visible page/text, CSS viewports 360×800 and 412×915, print mode, and a forced offscreen-page rejection. Node search tests passed 5/5.

**Root cause is not confirmed.** Before-change source showed hidden offscreen staging, later transfer, and readiness without checking final viewport visibility. That is a plausible geometry/layout failure mode only. The owner’s exact document/device was not reproduced, no real before/after logs or asset-byte measurement were captured, and CI does not substitute for physical-device verification. Normal preview and print share the local HTML/renderer/asset route but use separate WebViews and different viewport/zoom setup. Print readiness was synthesized in tests; actual PrintManager output/cancellation was not device-tested.

**Required next action:** on the owner’s device and exact file, capture `DocxPreviewDiag` from routing through ready/error, check asset response and streamed bytes, WebView URL/lifecycle, JS engine/fetch/render outcome, page/output/scroller/viewport rectangles and visibility styles, zoom/transform/scroll, and Android callback timing. Compare normal with print for the same file; rotate and retry once. Do not claim resolution until visible normal preview and callback timing are confirmed on that device. Details are in `FILE_FORMAT_REHABILITATION.md` and `ISSUE_REGISTER.md` (`AUD-DOCX-012`).

The prior workflow assessment above describes the original audit point; `.github/workflows/docx-verify.yml` was added for this authorized DOCX verification and runs on push/PR.
