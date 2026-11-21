# File Claw: Architecture (source cross-check 2026-11-21)

> Current architecture facts are source-checked; current build/test evidence is in `PROJECT_STATUS.md`. Historical coverage numbers below are not current verification.

## Principle
Everything that can be decided without the Android framework lives in **`com.example.core`**: pure Kotlin,
no `android.*` imports, unit-tested on the plain JVM. The Android layers are thin adapters around it.

```
┌──────────────────────────────────────────────────────────────────────────┐
│ ui/            Compose screens + viewers (Home, AllFiles, Search, Settings,│
│                FilePreview, Editor, UniversalViewers, theme/Glass.kt)       │
├──────────────────────────────────────────────────────────────────────────┤
│ viewmodel/     MainViewModel: routing (FormatDetector -> FileContentState),│
│                saves, recents, search index, intent import                  │
├───────────────────────────────┬──────────────────────────────────────────┤
│ services/                     │ data/ + utils/                            │
│  FileManager (facade)         │  Room (recent_files, settings), repository │
│  SqliteBrowser                │  CacheManager, SmartCacheWorker            │
├───────────────────────────────┴──────────────────────────────────────────┤
│ core/   (pure Kotlin; current coverage not measured in this checkout)       │
│  format/   FormatRegistry · FormatDetector · MagicSniffer · TextHeuristics │
│  io/       TextLoader · TextEncoding · TextSaver · TextWindowReader ·      │
│            AtomicFileWriter                                                │
│  archive/  ArchiveLimits · ArchivePaths · LimitedInputStream ·             │
│            ArchiveTreeBuilder                                              │
│  csv/      DelimitedParser · DelimitedWriter                               │
│  security/ PathGuard                                                       │
│  text/     RtfExtractor · OdfTextExtractor · EpubReader · HtmlSanitizer    │
│  model3d/  MeshParser (STL, OBJ)                                           │
│  sqlite/   SqlIdentifier                                                   │
└──────────────────────────────────────────────────────────────────────────┘
```

## Opening a file (the universal opener pipeline)
1. **Intake.** A file arrives from the picker, the tree, search, recents, a shortcut, or another app
   (`ACTION_VIEW`). The current external URI import path copies the input stream to `filesDir/imported/` under a
   sanitised name; it also stores the original `sourceUri` for attempted write-back on supported editable flows.
   Provider-grant persistence and large-file copy behavior need device verification. Intent handling is in
   `MainActivity` / `MainViewModel`, not a Compose effect.
2. **Detect** (`FormatDetector.detect`). Reads at most 8 KB. Order: strong binary signature (always wins) →
   meaningful extension (checked against content) → weak text signature (only if the extension says
   nothing) → text/binary heuristics. ZIP containers are refined by entry names (docx/xlsx/pptx/odf/epub/apk).
   Result carries `FormatSpec`, `Confidence`, and `mismatch` (extension disagrees with content).
3. **Route** (`MainViewModel.buildContentState`): `FileKind` → a `FileContentState` the preview screen renders.
4. **Render.** Compose viewers, or local WebViews for PDF (bundled PDF.js via `WebViewAssetLoader`), DOCX / PPTX / spreadsheet grid, Markdown, and HTML. DOCX's normal path fetches the original file through a local `WebViewAssetLoader` stream; the ViewModel still precomputes and retains a base64 copy for fallback (AUD-DOCX-001). PPTX and spreadsheet bridges have distinct paths. TXT/Markdown previews use bounded HTML windows; truncated TXT/Markdown search streams the original file and opens bounded excerpts at stored match offsets. Device behavior is not verified in this audit.
5. **Save** (editable states only): `AtomicFileWriter` (temp file + fsync + atomic rename); charset and BOM are
   preserved; truncated or converted views are read-only and the ViewModel refuses to save them.

## Data model
`FileContentState` (sealed) is the single contract between routing and rendering. Text and sheets carry
`truncated` / `readOnlyReason` **as flags, never as text inside the content**.

## Safety boundaries
| Boundary | Mechanism |
|---|---|
| Huge files | 8 KB sniff, 250k-char preview cap with `truncated` flag, `TextWindowReader` for bounded search excerpts, streamed full-file TXT/Markdown search with at most 10k retained offsets, 50k-row grid cap |
| Archives | entry cap, per-entry size and ratio limits, depth cap, `copyLimited`, zip-slip flagging, cache-chosen output names |
| Writes | atomic replace everywhere (text, delimited, ZIP entry update) |
| WebViews | Manifest has no INTERNET permission; HTML scripts are off by default; Markdown is sanitized with a narrow bridge; PDF.js/DOCX use local assets/bridges; DOCX file/content/universal access is disabled and unrelated top-level navigation is blocked. Static inspection only; not a security certification. |
| Database | SQLite opened read-only, identifiers quoted, only generated SELECTs |
| Persistence | Room fails fast on schema change without a migration (no silent wipe); backup disabled |

## Known structural debt (see TODO.md and ISSUE_REGISTER.md)
`FilePreviewScreen.kt` is roughly 5,390 lines; `MainViewModel.kt` is roughly 1,315 lines. Navigation uses shared ViewModel state. Imported files are copied locally and a source URI is stored for attempted write-back (provider permission behavior is unverified). DOCX has a local stream in the normal WebView rendering path but still retains an unnecessary whole-file base64 copy; PPTX uses the bounded base64 route. These are code-size/path observations, not runtime measurements.
