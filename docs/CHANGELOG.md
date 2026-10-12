# Changelog

Format: [Keep a Changelog](https://keepachangelog.com). Numbers marked **(measured)** were produced by a real
run in the build sandbox; **(not verified)** means the change could not be compiled or run there (no Android SDK,
no Maven access). See `TODO.md` → KNOWN ISSUES.

## [1.2.0] "Batch Operations & Verification": versionCode 3

### Added
- **Batch Selection Mode in File Browser (`FileTreeComponent`)**:
  - Multi-select mode activated via top "Select" button or long-pressing any file or directory.
  - Interactive checkboxes with smooth selection state management and "Select All" / "Deselect All".
  - **Simultaneous Batch Delete**: Deletes multiple selected files and directories with confirmation preview dialog and instant database/cache cleanup.
  - **Simultaneous Batch Copy**: Non-destructive batch copy with recursive directory support, unique renaming collision safeguards, and interactive destination folder picker.
  - **Simultaneous Batch Move**: Atomic move with cross-filesystem fallback, cycle/self-containment validation, and automatic database index path synchronization.
- **Batch Selection Mode in Recent History (`AllFilesScreen`)**:
  - Multi-select files in history with options to batch remove from history or permanently delete from disk.
- **Pure-Kotlin `BatchFileOperations` Engine & Unit Tests**:
  - `BatchFileOperations.kt` with thread-safe copy, move, and recursive delete routines.
  - Full unit test coverage in `BatchFileOperationsTest.kt` verifying deletion, unique copy name preservation, cycle prevention, and directory moves.
- **GitHub Actions Release Workflow**:
  - Added `.github/workflows/build-and-release.yml` with `workflow_dispatch` manual trigger for parameterized versioning (`version_name`, `version_code`) and automated release artifact publishing.

### Fixed
- **Corrupt PDF Feedback**: Replaced endless loading spinner with honest error UI displaying exact failure diagnostics and a "Go Back" action.
- **Password Field Masking**: Protected user privacy in archive password and PDF password dialogs using `PasswordVisualTransformation`.
- **Honest Auto-Save Messaging**: Renamed "Auto-save Drafts" to "Auto-save on Exit" in Settings and updated editor notification from "Draft auto-saved" to "Changes saved to file" so users know changes write directly to disk.

## [1.1.0] "Foundation & Safety": versionCode 2

### Scoreboard (v1.0 → v1.1.0)
| Metric | v1.0 | v1.1.0 |
|---|---|---|
| Unit tests | 0 | **91** passing (measured) |
| Line coverage of `core/` | n/a (no core, no tests) | **98.3 %** (993/1010), branches 83.2 % (measured, JaCoCo) |
| Registered extensions | ~37 explicit routes + implicit text fallback | **399** (284 full, 32 partial, 83 identify-only) (generated from `FormatRegistry`) |
| New format families with a working route | n/a | **13 families / 63 extensions**: SQLite, SVG, RTF, OpenDocument, EPUB, TTF/OTF fonts, STL, OBJ, gz/bz2/xz files, tar.bz2/tar.xz, ar/deb/cpio, ZIP packages (apk, jar, whl, …), HEIC/AVIF/TIFF/DNG |
| Formats that silently misbehaved | 5 (`.ppt`, `.xls`, `.rar`, plain `.gz`, `.tsv`) | 0 silent: each now parses correctly or says plainly it isn't supported |
| Kotlin source | 9,865 lines / 24 files | 10,653 main lines / 40 files, plus 1,107 test lines |
| `FileManager.kt` | 963 lines | 390 lines |
| `FilePreviewScreen.kt` | 3,880 lines | 2,980 lines |
| Dead code removed | n/a | ~1,500 lines + 4 `.bak` files + 8 one-off patch scripts + a committed build log |
| Unused dependencies removed | n/a | pdfbox-android (~7 MB), kotlin-csv, fastexcel-writer, kotlinx-serialization |

### Benchmarks (JVM, 256 MB heap, `tools/bench/CoreBench.kt`; desktop JVM, **not** device numbers)
| Scenario | Old | New |
|---|---|---|
| Charset check on a 500 MB text file | `OutOfMemoryError` (`readBytes()` of the whole file) | 46 ms |
| Load a 500 MB text file for preview | not possible | 45 ms (250k-char window, flagged truncated) |
| Read a 64 KB window at offset ~499 MB | n/a | 1 ms |
| Archive tree, 100,000 files in one folder | 23,773 ms (O(n²) sibling scan) | 184 ms (≈130×) |
| Archive tree, 100,000 files nested | 373 ms | 562 ms: **slower**, because the new builder also builds a path index and sorts |

Cold start, scroll fps, crash-free rate: **not measured** (needs a device). The only startup change is removing a
hard-coded 500 ms splash wait (now 150 ms) and moving cache-directory I/O off the main thread.

### Fixed: data loss
- **Editing a text file over ~250 KB silently truncated it on save** and wrote a "[truncated]" marker into the file.
  Truncation is now a flag; truncated and converted views are read-only and the ViewModel refuses to save them.
- **Spreadsheets auto-saved on every keystroke**; for `.xlsx` that flattened a multi-sheet workbook to one sheet of
  plain values. Excel is now view-only; CSV/TSV has an explicit Save button and an unsaved-changes prompt.
- `.xls` content was written under an `.xls` name as xlsx bytes; `.tsv` was parsed and saved as comma-CSV. Both fixed.
- All writes were truncate-then-write (a crash left an empty file) and `updateZipEntry` deleted the original before
  renaming. Everything now writes to a temp file, fsyncs, and atomically renames.
- Removed `writeDocxText` / `writePptxText` (unused, but would have destroyed formatting; the PPTX one also emitted
  malformed XML).
- Re-opening the same file name overwrote the earlier imported copy; imports now get unique names, or reuse an identical copy.
- "Remove from history" immediately re-opened the same file (restore effect re-fired on `Idle`).
- Rotation re-imported the intent's file / re-ran shortcuts (effect keyed on non-state `Activity.intent`).

### Fixed: crashes and silent failures
- `determineCharset` read entire files into memory; UTF-16 text was misclassified as binary.
- 7z extraction allocated `ByteArray(entry.size.toInt())`; extraction is now streamed and limit-checked.
- `.rar` showed an empty folder; corrupt/unsupported archives now show a real error.
- Plain `.gz` was parsed as TAR (garbage); handled as a single compressed file.
- Search index covered depth 3 / 5,000 files (missed most of a real device); now depth 8 / 60,000, appended instead of rewritten per open.
- `CacheManager`'s `LruCache` counted entries, not bytes (never bounded memory); cleanup wiped the whole Coil disk cache and deleted open archive entries. Rewritten (age-based, lazy dirs).
- Room used `fallbackToDestructiveMigration()` (any schema bump wiped settings and history). Now fail-fast; schema export enabled.
- PPTX state had no file, so title/share/properties were blank.

### Security
- Zip-bomb and zip-slip protection (entry/size/ratio/depth limits; unsafe names are shown but never extractable).
- `FileProvider` no longer exposes the whole filesystem (`root-path` removed).
- `allowBackup=false` (imported documents were being backed up to the cloud); `BROWSABLE` removed from the VIEW filter.
- HTML previews: scripts off by default, no JS bridge, navigation locked to local files. Markdown/EPUB HTML sanitised.
- All other WebViews: file/content access off (bundled assets still load).
- Untrusted display names sanitised (`PathGuard`); SQLite opened read-only with quoted identifiers; XML parsing forbids DTDs.
- Release signing now comes from environment variables instead of reusing the debug key; archive-password dialog TODO (see backlog).

### Added
- **Universal opener core**: content-sniffing `FormatDetector` (magic bytes + ZIP refinement + heuristics + mismatch reporting).
- Viewers: SQLite browser, font specimen, 3D wireframe (STL/OBJ), SVG, EPUB, RTF/OpenDocument text, hex view labelled with the detected format.
- "Open with" works without all-files access; the permission screen gained "Continue without full access".
- Glass design tokens, `Modifier.glass()`, `GlassBackdrop`; glass cards and app bar.
- `TextWindowReader` (paged access to 500 MB+ files; UI paging still TODO).
- Gradle 8.7 wrapper (AGP 8.4.2 requires ≥ 8.6; the repo pinned 8.5), JaCoCo coverage gate (80 %), detekt (advisory), CI workflow.

### Changed
- Splash wait 500 ms → 150 ms. Opening a file no longer copies it to a temp cache first.
- Docs reorganised into `docs/`; stale pre-1.1.0 docs moved to `docs/legacy/`.

## [1.0.x]: see `docs/legacy/changelogs.md`
