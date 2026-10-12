# Security audit: v1.1.0

Scope: code review of the full v1.0 source plus the v1.1.0 changes. No dynamic testing, fuzzing or device pen-testing was performed.

| # | Finding (v1.0) | Severity | v1.1.0 status |
|---|---|---|---|
| 1 | Zip bombs: 7z extraction `ByteArray(entry.size.toInt())`, no ratio/size/entry limits | High | **Fixed**: `ArchiveLimits`, streamed `copyLimited`, entry cap |
| 2 | Zip-slip names displayed/handled unchecked | Medium | **Fixed**: normalised, flagged `unsafe`, never extractable; output paths are cache-chosen |
| 3 | `FileProvider` `<root-path path=".">` exposed the whole filesystem | High | **Fixed**: removed (external, cache, files only) |
| 4 | Local HTML ran JS with file access, plus a JS bridge | High | **Fixed**: scripts off by default, no bridge, nav locked, URL via `Uri.fromFile` |
| 5 | Markdown raw HTML + JS-enabled WebView with a clipboard bridge | Medium | **Fixed**: `HtmlSanitizer` |
| 6 | All WebViews had `allowFileAccess`/`allowContentAccess = true` | Medium | **Fixed** (assets unaffected) |
| 7 | `allowBackup="true"` backed imported documents up to cloud | Medium | **Fixed** |
| 8 | VIEW filter `BROWSABLE` (web-triggerable) | Low | **Fixed** |
| 9 | Import used the raw display name (overwrite / odd names) | Medium | **Fixed**: `PathGuard.sanitizeFileName` + unique names |
| 10 | Whole-file reads (`readBytes`) of untrusted files, OOM | High | **Fixed** for text/charset; DOCX/PPTX capped at 25 MB |
| 11 | Release build signed with the debug key | Medium | **Mitigated**: env-based release signing; falls back to debug key |
| 12 | Untrusted SQLite would be opened by the new viewer | n/a (new) | Opened read-only, quoted identifiers, SELECT-only; residual risk: SQLite parser bugs on hostile files |
| 13 | XML parsing of ODF/EPUB (XXE) | n/a (new) | DTDs/external entities disabled |
| 14 | No `INTERNET` permission | n/a | **Confirmed**: nothing can be exfiltrated even if script injection occurred |
| 15 | Archive password field not masked | Low | **Open** (TODO P0) |
| 16 | Unscoped JitPack repository | Low | **Open** (TODO P2) |
| 17 | `MANAGE_EXTERNAL_STORAGE` still requested (Play policy risk) | Policy | **Open**: now optional for opening files, still needed for browsing/search |
| 18 | Bundled JS libraries unpinned/unlicensed notice | Low | Notices added; license verification open |

Residual risk notes: WebView renderers parse hostile DOCX/PPTX in JS; the mitigation is no network and no bridge capabilities beyond view callbacks.
