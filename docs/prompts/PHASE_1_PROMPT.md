# FILE CLAW — PHASE 1 PROMPT: Build Green + Broken-Format Repairs

> Paste this whole file into the next session. Branch: `arena/5100f329-file-claw-native`.
> Phase 0 (audit + repo hygiene + PRD + UI plan) is **done** — read its outputs first.

## Mission
Make the project **compile, test and assemble green in CI**, then fix the audit's P0/P1 findings so every
viewer fails *honestly* (never a crash, an infinite spinner, or a blank screen). No feature work, no UI redesign in this phase — Phase 3 owns UI.

## Step 0 — Preflight (do not skip)
1. Read in order: `docs/AGENTS.md` → `docs/TODO.md` (KNOWN ISSUES) → `docs/FILE_SUPPORT_AUDIT.md` (the AUD register) → `docs/PRD.md` → `docs/CHANGELOG.md` (top entries).
2. Confirm JDK 17 + Android SDK 34 exist (`java -version`, `$ANDROID_HOME`). If missing, use GitHub Actions (`.github/workflows/ci.yml`, added in Phase 0) — **do not claim success from a sandbox that cannot build.**
3. Branch: work on the same branch (`arena/5100f329-file-claw-native`). Do not create new branches. Do not rewrite history.
4. Baseline: `./gradlew testDebugUnitTest` — recount `@Test` methods and write the real number into `docs/CHANGELOG.md` (Phase 1 fixes the 134/146/167/199/225 drift, AUD-022).

## Phase 1 scope (in order)

### 1) Quality Gate (AUD-026)
```
./gradlew compileDebugKotlin testDebugUnitTest assembleDebug
```
Fix **all** compile fallout before anything else. Suspected hot spots (never compile-verified): Loop 6–15 code —
`search.html` bridges, `DocxPreviewWebView` (base64 + `new Function`), `PdfPreview`, `FilePrintHelper`,
`ScreenOrientationPolicy`, unused imports, Compose API drift. Keep fixes minimal and documented.

### 2) Dependency & supply-chain hygiene (AUD-001, AUD-019, AUD-021)
- Remove `com.github.mhiew:android-pdf-viewer` (zero imports; PDF is PDF.js) from `app/build.gradle.kts`.
- If JitPack ends unused → remove the repo from `settings.gradle.kts`; if still needed, scope it: `content { includeGroup("com.github.mhiew") }`.
- Release workflow: when `build_type=release` and no signing material is configured → **fail the job** instead of silently signing with the debug key (build-and-release.yml).
- Delete `debug.keystore.base64` fallback logic? — **No.** Keep the fallback for sideloading; just make *release* fail closed (AUD-021).

### 3) No silent failure screens (P1 media/PPTX) — AUD-005…AUD-010
- **PPTX**: clear `isWebViewLoading` on `onReceivedError`/`onReceivedHttpError`; port the DOCX honest-recovery pattern (reason + Retry); add a 15 s load watchdog.
- **Audio**: `Dispatchers.IO` + `prepareAsync()`; `setOnErrorListener` → inline "can't decode this audio" + Open-with/Hex options (MIDI included).
- **Video**: `setOnErrorListener` → inline error + Open-with fallback; keep 260 dp box but never black-and-dead.
- **Fonts**: validate the typeface actually loaded before rendering the specimen; else show "invalid font" notice (don't fail silently).
- **Coil images**: `onError` → "couldn't decode on this device" + Hex view action (HEIF/AVIF/TIFF/DNG path).
- **MIDI registry honesty**: `mid/midi/kar` → drop from FULL to IDENTIFY (or partial) until a synth ships. Patch `FormatRegistry.kt`, regenerate `docs/FILE_FORMATS.md` via `tools/GenFormatDocs.kt`, note in CHANGELOG.

### 4) Hex viewer paging (AUD-003)
- Stream windows of 64 KB with offset input + prev/next + jump-to-offset; keep first window = current behavior for small binaries. Pure-Kotlin `HexDump` model in `core/` with unit tests; UI consumes.

### 5) Room schema (AUD-034)
- `./gradlew kspDebugKotlin` must emit `app/schemas/com.example.data.AppDatabase/4.json` (with `sourceUri`); commit it. Verify `DatabaseMigrationTest` still passes.

### 6) Known UI bugs (do these four only)
- BUG-004 CSV wide-grid clipping (edge fade + optional sticky first column)
- BUG-005 password-dialog keyboard overlap (`imePadding()`)
- BUG-006 PDF page-1 placeholder geometry for mixed-orientation docs
- BUG-007 DOCX pinch transient horizontal overflow (clip during preview transform)

### 7) Docs & strings reconciliation (AUD-002, AUD-024)
- Sweep user-facing strings: "saved to the in-app copy" vs the real write-back behavior; unify to honest copy.
- `SANITY_CHECKLIST.md` + `TODO.md` KNOWN ISSUES #3 aligned to implemented `sourceUri` write-back (flag for device verification, don't claim it works).

## Explicitly OUT of scope (later phases)
FilePreviewScreen split (Phase 2) · type-safe navigation (Phase 2) · Compose UI tests beyond save-path round trips (Phase 2) · any UI redesign (Phase 3) · new formats (Phase 4) · macrobenchmarks (Phase 5) · applicationId change (Phase 5 decision).

## Verification & honesty rules
- Run what you can; label everything else **not verified** in `docs/TODO.md` and `docs/CHANGELOG.md`.
- `node --check` every inline JS you touch; XML-parse the manifest; brace-balance changed Kotlin.
- Add/adjust unit tests where pure logic changes (HexDump, MIDI registry, error-state policies).
- Update: `docs/CHANGELOG.md` (measured/not-verified labels), `docs/TODO.md` (tick the AUD/BUG items), `docs/FILE_SUPPORT_AUDIT.md` (mark fixed items FIXED with date), `docs/FILE_FORMATS.md` (regenerate), `docs/worklog.md` (append entry).

## Deliverable checklist
- [ ] CI green: compileDebugKotlin + testDebugUnitTest + assembleDebug (link the run)
- [ ] AUD-001/005/006/007/008/009/010 + BUG-004/005/006/007 + AUD-013/AUD-034 addressed or deferred with written reasons
- [ ] FormatRegistry MIDI honesty + FILE_FORMATS regenerated
- [ ] 4.json committed; DatabaseMigrationTest green
- [ ] CHANGELOG with real test count; audit doc statuses updated
- [ ] No new dependencies without written justification (app stays 100 % offline; no INTERNET permission)

## If something is unfixable in this phase
Document precisely in `docs/TODO.md` → KNOWN ISSUES (what, why, evidence), keep the failure honest in the UI, and move on. **Never** paper over a crash with a silent catch.
