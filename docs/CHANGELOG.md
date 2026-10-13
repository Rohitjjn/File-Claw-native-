# Changelog

Format: [Keep a Changelog](https://keepachangelog.com). Numbers marked **(measured)** were produced by a real
run in the build sandbox; **(not verified)** means the change could not be compiled or run there (no Android SDK,
no Maven access). See `TODO.md` → KNOWN ISSUES.

## [1.4.0] "DOCX Engine Fix, Glass Properties Sheet & PDF Resume": versionCode 6

### Fixed
- **DOCX File Preview Fixed & Pure-Kotlin Fallback Engine**:
  - Fixed WebView DOCX preview by localizing assets into `editor/` directory and resolving cross-origin/file-access CORS restrictions.
  - Resolved base64 IPC Binder transaction limit by exposing `getDocxBase64()` over Android JavascriptInterface.
  - Implemented pure-Kotlin offline OOXML extractor (`DocxExtractor.kt`) that parses `word/document.xml` using `ZipInputStream` without relying on WebView or external dependencies.
  - Added unit test suite `DocxExtractorTest.kt` verifying offline text, word counts, and paragraph extraction.

### Added
- **Glassmorphism Modal Bottom Sheet for File Properties**:
  - Replaced basic AlertDialog with a modern Jetpack Compose `ModalBottomSheet` (`FilePropertiesBottomSheet.kt`).
  - Added drag handle, glassmorphic backdrop, formatted file size, exact byte count, MIME type chip, last-modified timestamp, and permission status.
  - Integrated asynchronous cryptographic checksum calculation (MD5 and SHA-256) with one-tap copy buttons.
  - Storage path box with one-tap "Copy Path" action and clipboard confirmation.
- **Staggered Spring & Fade-in Reveal Animation**:
  - Added `StaggeredAnimatedItem` in `ClaudeComponents.kt` providing staggered spring translation and smooth alpha fade-in across all file cards in `HomeScreen` and `AllFilesScreen`.
- **PDF Last-Read Page Bookmark & Auto-Resume**:
  - PDF viewer now automatically saves the user's active page in `SharedPreferences` upon scrolling.
  - Re-opening any PDF automatically restores the user to their last-read page and displays a confirmation notification ("Resumed at page X").
- **Native Print & PDF Export via PrintManager**:
  - Implemented `FilePrintHelper.kt` utilizing Android's native `PrintManager` and `PrintDocumentAdapter`.
  - Added "Print / Export as PDF" actions to the top app bar and floating action pill for Text, Markdown, Code, and Image files.
- **Quick "Go to Line" in Text & Code Viewer**:
  - Added "Go to Line" dialog with direct line-number entry.
  - Code/Text viewer tags each line and smoothly scrolls to the target line with a temporary highlight flash.

## [1.3.0] "Floating Action Pill, Grid View & In-Viewer Search": versionCode 5

### Added
- **Unified Floating Viewer Control Bar (Action Pill)**:
  - Added a lightweight, floating M3 glassmorphism action pill across PDF, Image, and Text viewers.
  - PDF: quick Page Indicator / Jump to page pill (`current / total`), in-viewer search toggle, night/light mode toggle pill, and share action.
  - Image: photo counter badge, and quick share action.
  - Text: in-viewer search toggle, copy all text shortcut, edit button, and quick share action.
  - Smoothly auto-fades and toggles on screen tap with animated slide and fade transitions.
- **Grid vs List View Toggle & Rich File Cards**:
  - Added a compact Grid / List toggle icon button in `HomeScreen` and `AllFilesScreen`.
  - Grid View displays a 2-column layout with high-contrast `FileExtensionBadge` (PDF, TXT, PNG, JSON, etc.), preview thumbnail, quick star button, file name, formatted size, and relative timestamp.
- **Favorites / Starred Files & Filter Tabs**:
  - Added interactive Star button (`Icons.Filled.Star` / `Icons.Outlined.StarOutline`) to every file card in both list and grid views.
  - Added Filter Tabs (`All Files` vs `★ Favorites`) across `HomeScreen` and `AllFilesScreen` allowing instant filtering with empty-state guidance.
  - Persistent favorite state stored across sessions in `SharedPreferences`.
- **In-Viewer Text Search (PDF & Text files)**:
  - Added a floating "Find in document" overlay search bar with match count indicator (`Match X of Y`) and Previous (`<`) / Next (`>`) navigation buttons.
  - Text files: matches occurrences across the document, highlights them, and updates search match positions.
  - PDF documents: pure-Kotlin `PdfTextSearcher` parses uncompressed and FlateDecode streams to locate matches and jump directly to matching pages.
- **Pure-Kotlin `PdfTextSearcher` & Unit Tests**:
  - Implemented `PdfTextSearcher.kt` in `com.example.core.text`.
  - Added comprehensive unit tests in `PdfTextSearcherTest.kt` verifying uncompressed and compressed stream search.

## [1.2.1] "Viewer Polish & Smart PDF Dark Mode": versionCode 4

### Added
- **Copy Text Action Bar Shortcut**: Added a dedicated copy icon in the top app bar for all text-based files (`txt`, `md`, `json`, code) to copy entire contents to clipboard with instant feedback.
- **Deep Zoom Support**:
  - Image viewer now supports up to 15x zoom factor via `ZoomSpec(maxZoomFactor = 15f)`, providing deep magnification comparable to Google Photos and gallery apps.
  - PDF viewer now supports up to 10x zoom factor (`maxZoom = 10.0f`, `midZoom = 3.5f`), more than triple previous limits.
- **Smart Hue-Preserving PDF Night Mode**:
  - Implemented a specialized document color matrix for PDF night reading that inverts white page backgrounds to dark charcoal (`#1e1e1e`) and black text to comfortable off-white (`#e1e1e1`), while preserving natural color hues and skin tones so photographs and figures do not suffer from inverted "ghost" negative artifacts.
- **Zoom-Out Below 1x for Text Files**: Added `minimum-scale=0.25` in viewport meta and enabled zoom controls across in-app browser previews (`txt`, `md`, `json`, code), allowing users to zoom out down to 25%.

### Changed
- **Default In-App Browser for Text Previews**: All text files (`txt`, `md`, `json`, etc.) now default directly to the premium in-app browser preview matching Claude design aesthetics.
- **Cleaned Preview Menu**: Removed redundant "Show Syntax Code" toggle from 3-dot menus in text/markdown/json files.
- **Image Preview Theme Adaptation & Clean Header**:
  - Image background now adapts dynamically to system theme (light in light mode, dark in dark mode).
  - Removed duplicate overlay showing image name and secondary 3-dots menu beneath the top header for a full edge-to-edge experience.
- **Compact Single-Line Search Box**: Redesigned search bars across `SearchScreen`, `HomeScreen`, and `AllFilesScreen` into a compact, single-line 40.dp rounded pill design with integrated search and clear actions.

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
  - Fixed runner initialization error (`Failed to find package 'tools'`) by removing deprecated `android-actions/setup-android@v3`, configuring native runner Android SDK and `cmdline-tools`, adopting `gradle/actions/setup-gradle@v4`, and adding automated fallback keystore generation and GitHub CLI release publishing.

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
