# Testing strategy and verification rules

## Evidence labels

Every handoff uses these statuses, tied to the exact commit/tool output:

- **PASS** — command completed with exit 0 and output confirms the intended check completed.
- **FAIL** — command ran far enough to execute the check and reported a failing assertion/build/check.
- **NOT RUN — environment/setup** — toolchain, SDK, dependency access or device was absent; quote the blocker. This is not a test failure and not a pass.
- **NOT RUN — scope/approval** — deliberately not run because not authorized.
- **STATIC ONLY** — source/configuration/structure inspection or syntax parse; not compilation, runtime, security proof or behavior verification.
- **DEVICE REQUIRED** — Android runtime, WebView, lifecycle, permission, accessibility, memory, printing or provider behavior that cannot be proved by pure unit tests.

Do not report stale `build.log` entries or old worklog/changelog statements as current results. Record commit SHA, command, toolchain and any fixture used.

## Test layers

1. **Pure Kotlin core/unit tests:** parsers, archive safety, detection, policies, file I/O and deterministic algorithms. Run with `./gradlew :app:testDebugUnitTest --no-daemon` and collect full test results. Run local verifier only when its documented JDK/Kotlin requirements are installed; its script currently has mode `100644`.
2. **Coverage/static checks:** run `jacocoCoreReport`/`jacocoCoreVerify`, `lintDebug`, and `detekt`; report that detekt is currently advisory (`ignoreFailures = true`). Do not infer coverage from test count.
3. **Android compilation/package:** run `:app:compileDebugKotlin` and `:app:assembleDebug`. Record actual output path and artifact size if successful. An APK file existing without a matching completed build is insufficient.
4. **Web asset checks:** syntax checks for extracted JS/HTML scripts, local asset paths, MIME/worker wiring, and license/notices. Syntax checks do not test browser DOM behavior or Android bridge security.
5. **Integration tests:** Compose/navigation/open-file workflows, SAF/content URI grants, persistence/migrations and save/export. Add instrumentation tests where practical; the current checkout has no `androidTest` files.
6. **Device/emulator checks:** file opening from storage and external apps, permission variations/API levels, WebView rendering, large and malformed files, memory, rotation/lifecycle, screen readers/accessibility, actual PrintManager output and export/share. Include representative low-memory and current Android versions.
7. **Release validation:** inspect exact GitHub Actions run for the target SHA; verify signed APK/AAB, signing certificate continuity, hashes, release assets and reproducible input handling. The current workflow is manual only; no CI run was performed during this audit.

## Bug-to-test policy

- First capture a minimal failing fixture and exact steps; do not claim reproducibility without reproducing.
- Add a regression test that fails before the fix and passes after, at the narrowest reliable layer.
- For boundary cases test below, exactly at and above caps; include malformed and hostile-input cases where safe.
- Never delete/weaken existing tests to reach green. Keep expected failures explicit.
- Test supported and fallback/error routes, not just the happy path.

## Current audit baseline

At revision `2f6d3492de46b3d8f71515404d170af4e35c8459`, static inventory: 32 Kotlin test files, 222 `@Test` annotations, no `androidTest` files. `java`, `kotlinc`, Android SDK variables and `adb` were unavailable; Gradle unit tests, lint, detekt, JaCoCo, Kotlin compilation and APK build were not executed. DOCX inline JS syntax and Android manifest XML parsed successfully only. Full command outcomes are in `PROJECT_STATUS.md`.
