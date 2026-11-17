# FILE CLAW — PHASE 2 PROMPT: Test Infrastructure & Architecture

> Paste this whole file into the next session. Branch: `arena/5100f329-file-claw-native`.
> Phase 1 (Quality Gate + broken-format repairs) landed on 2026-11-17 — read its outputs first:
> `docs/CHANGELOG.md` ([Unreleased] Phase 1 entry), `docs/FILE_SUPPORT_AUDIT.md` (§9 resolution log),
> `docs/worklog.md` (PHASE-1-QUALITY-GATE).

## 0. Read first (in order)
1. `docs/AGENTS.md` → `docs/TODO.md` (KNOWN ISSUES, Phase 1 additions) → `docs/FILE_SUPPORT_AUDIT.md` (§9)
2. `docs/PHASES.md` → "Phase 2 — Test infrastructure & architecture"
3. `docs/CHANGELOG.md` → Phase 1 entry: what is measured (core: 191/191 pure-JVM tests green) and what is **not verified** (the full Android gate). Phase 2 must close that gap first.

## 1. Preflight — close the Phase 1 gate honestly (do not skip)
1. Confirm a JDK 17 + Android SDK 34 environment (or the CI runner).
2. Run exactly what Phase 1 could not:
   ```
   ./gradlew compileDebugKotlin testDebugUnitTest assembleDebug
   ```
   Expect fallout: **Loop 6–15 code was never compile-verified** (Compose API drift, unused imports,
   base64/`new Function` bridges, `PdfPreview`, `FilePrintHelper`). Fix forward, minimal diffs, no feature work.
3. If the repo owner has approved pushing: open/track the PR and paste the first green (or red→fixed→green)
   CI run link into `docs/CHANGELOG.md` (Phase 1 entry) and `docs/TODO.md`.
4. `./gradlew kspDebugKotlin` on a networked machine → commit `app/schemas/com.example.data.AppDatabase/4.json`
   (AUD-034; today CI only warns). Verify `DatabaseMigrationTest` still passes with the committed schema.
5. Verify the Room schema-drift warning disappears once `4.json` is committed.

## 2. Phase 2 scope (in order)
1. **Emulator instrumented tests in CI** (`reactivecircus/android-emulator-runner`, AVD API 34):
   open → edit → save → reopen round trips (text, Markdown, CSV); "open with → File Claw" intent path;
   archive password flow; corrupt-PDF error path. (Closes AUD-013 properly.)
2. **Screenshot tests on the JVM** (Paparazzi or Roborazzi): baselines for Home / AllFiles / Search /
   Settings (light+dark). **Take baselines BEFORE any refactoring** (golden-master first).
3. **Split `FilePreviewScreen.kt` (~5,900 lines)** into `ui/screens/viewers/` — one file per viewer
   (`PdfViewer.kt`, `DocxViewer.kt`, `PptxViewer.kt`, `TextViewer.kt`, `CsvViewer.kt`, `ZipViewer.kt`,
   `HexViewer.kt`, `ImageViewer.kt`, `AudioViewer.kt`, `VideoViewer.kt`, …) + shared `ReaderChrome.kt`
   (find bar / command bar / IME-safe tray). Move code mechanically; behavior must not change.
4. **Type-safe navigation** (Navigation Compose serializable routes); delete route-without-args and any
   singleton `currentFileState` smuggling that navigation still relies on.
5. **`MainViewModel` decomposition where cheap** — file IO/builders into use-cases or `core/`; keep the
   ViewModel as orchestrator. Do **not** redesign state management wholesale (Phase 3+ territory).
6. **detekt:** first green run on `core/` only; then `ui/` advisory; commit baseline.
7. **JaCoCo:** gate verified locally and in CI; coverage report uploaded as CI artifact (target: `jacocoCoreVerify` ≥ 80 % core lines, blocking).
8. Extract `strings.xml` for **new** strings only (full i18n stays Phase 7).

## 3. Explicitly OUT of scope
UI redesign / new visual language (Phase 3 — "The Quiet Desk") · new file formats (Phase 4) ·
macrobenchmarks & 500 MB files (Phase 5) · applicationId change (Phase 5 decision) · signing for Play (Phase 8).

## 4. Test & honesty rules (same as Phase 1)
- Every gate runs in CI (push/PR). Do not claim anything ran that did not. Label **measured** vs **not verified**.
- Keep `core/` pure Kotlin + unit-tested. `tools/local-verify/verify-core.sh` must keep passing locally.
- Behavior parity during the split: screenshot baselines gate it; `node --check` every inline JS touched;
  brace-balance changed Kotlin; XML-parse the manifest.
- New strings → `strings.xml` (new ones only).

## 5. Deliverable checklist
- [ ] Phase 1 gate green with a linked CI run (or an explicit written blocker report) — *hard blocker for the rest of this phase*
- [ ] `app/schemas/com.example.data.AppDatabase/4.json` committed; Room-drift warning gone (AUD-034 closed)
- [ ] Instrumented suite runs on PRs touching `app/` (or nightly + `workflow_dispatch`, with a written reason if not per-PR)
- [ ] Paparazzi/Roborazzi baselines committed and enforced; failing screenshot = PR blocked
- [ ] `FilePreviewScreen.kt` deleted or reduced to a thin shell; no viewer file > 800 lines
- [ ] Type-safe navigation; detekt baseline committed; `jacocoCoreVerify` blocking in CI
- [ ] CHANGELOG updated with measured numbers; audit doc statuses updated; worklog entry appended
- [ ] `docs/prompts/PHASE_3_PROMPT.md` written as the last step (references `docs/UI_FRONTIER.md`)

## 6. Known risks / watch-list from Phase 1
- AUD-001…010 silent-failure fixes are compile-verified only — a wrong Compose/WebView API signature will
  surface as the first CI failure. Fix forward; do not revert the honesty pattern.
- `ImagePreview` uses a bounds-decode pre-flight instead of telephoto `onError` (telephoto API unverifiable
  offline). When the Android gate is green, consider switching to the library's native error callback
  (small, isolated change) and note it in the audit.
- `MainViewModel.openAsHex` swaps state in place without a navigation event — by design (preview route is
  the same). Keep it that way during the navigation refactor, or route explicitly and delete the swap.
- HexDump UI depends on `main`/`io` Dispatchers only for loading a window; the `HexDump` core is unit-tested —
  extend those tests rather than adding UI tests for hex.
- x-spreadsheet sticky first column deferred (canvas renderer); edge fade is shipped.

## 7. If something cannot be done in this phase
Document precisely in `docs/TODO.md` → KNOWN ISSUES (what, why, evidence), keep failure modes honest,
and move on. Never paper over a crash with a silent catch.
