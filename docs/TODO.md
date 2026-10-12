# Backlog and known issues

Priorities: **P0** data loss / crash / security · **P1** high-impact feature or correctness · **P2** quality · **P3** polish.

## KNOWN ISSUES (read first)
1. **Nothing in the Android layer has been compiled or run by the author of this patch.** The sandbox had no Android SDK and
   no access to Maven/Google repositories, so `./gradlew build`, `test`, `lint`, `detekt`, `assembleDebug` were **not run**.
   What *was* verified: the entire `core/` package compiles under Kotlin 1.9.22 and passes 91 tests with 98.3 % line coverage
   (via a stand-in JUnit runner, not Gradle); every changed Kotlin file passes a parse check. Type-level errors in
   `FileManager`, `MainViewModel`, `MainActivity`, `FilePreviewScreen`, `UniversalViewers`, `CacheManager` (library and
   Compose API usage) are possible. **Run the Quality Gate in `BUILD_GUIDE.md` first.**
2. Gradle wiring for JaCoCo (`jacocoCoreReport` / `jacocoCoreVerify`), `enableUnitTestCoverage`, detekt, and Room schema export is
   unverified. detekt is `ignoreFailures = true` until its first green run.
3. Edits to files opened from another app are saved to the in-app copy only (the user is told). Write-back is P1 below.
4. WebView renderers (DOCX, PPTX, grid) still receive whole files as base64 (now capped at 25 MB, then falls back to the archive tree).
5. Hex viewer shows only the first 4 KB. Hard-coded `onDocumentLoaded(1)` makes DOCX page counts meaningless.
6. 3D viewer draws a wireframe sample (≤ 25k triangles); no shading, no GLB/PLY/DWG.
7. Legacy `.doc` / `.xls` / `.ppt` and `.rar` are identified but not openable.
8. Bundled JS libraries (docx-preview, pptx-preview, x-spreadsheet) have no license text in the shipped files; verify upstream licenses (JSZip is MIT/GPL dual, confirmed from its header).
9. `applicationId` is still the AI-Studio-generated `com.aistudio.filesclaw.apxqzy`; JitPack repository is unscoped (supply-chain exposure).

## P0
- [ ] Run the Quality Gate on a real machine and fix compile fallout (see KNOWN ISSUES 1).
- [ ] Archive-password dialog should mask input (`PasswordVisualTransformation`).
- [ ] `autoSaveDrafts` overwrites the original on exit; it's an auto-save, not a draft. Rename or write real drafts.

## P1
- [ ] **Write-back to source URI** for files opened from other apps (store `sourceUri` on `RecentFileEntity`, Room `Migration(3,4)`, `openOutputStream(uri, "wt")`).
- [ ] Don't copy 500 MB media imports: stream `content://` directly into ExoPlayer/Coil/PDF viewer.
- [ ] Paged text viewer on top of `TextWindowReader` (UI for files > 250k chars).
- [ ] Editor: line-number gutter shares scroll state with the text field; undo stack stores full text per keystroke; "Smart indent" strips indentation (destructive for Python/YAML/Makefile).
- [ ] Legacy Office: BIFF8 `.xls` reader first, then `.doc` text extraction.
- [ ] Root-level `GlassBackdrop` + NavHost transition policy; glass sheets/dialogs/search.
- [ ] Split `FilePreviewScreen.kt` (2,980 lines) into per-viewer files; type-safe navigation instead of shared singleton state.
- [ ] `ZipPreview`: show the `unsafe` warning icon; show archive-bomb refusals distinctly.
- [ ] Instrumented tests (Compose UI) for open → edit → save → reopen on each editable kind.

## P2
- [ ] RAR via a pure-Java reader (previous decision was to drop junrar; revisit).
- [ ] GLB/PLY/DXF viewers; shaded STL; DWG identify-only → metadata.
- [ ] CJK charset detection (GBK, Shift-JIS, Big5); currently non-UTF-8 falls back to windows-1252.
- [ ] Remove the dead pdf.js branch inside `WebViewPreview` and `canBeRenderedInWeb("pdf")`.
- [ ] Deduplicate `formatFileSize` / `formatElapsedTime` (4 copies) and migrate off deprecated `Icons.Default.ArrowBack/Undo`.
- [ ] Replace JitPack unscoped repo with scoped content filter.
- [ ] Benchmark on device: cold start, scroll jank (Macrobenchmark), 500 MB open.

## P3
- [ ] HEIC/AVIF fallbacks below API 31; animated WebP.
- [ ] Sample-file generator removed in 1.1.0; add an optional "demo files" screen if wanted.
- [ ] Localisation (all strings are hard-coded English).
