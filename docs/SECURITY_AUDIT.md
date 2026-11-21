# Security audit: historical v1.1.0 review + current static follow-up

The table below preserves the historical v1.1.0 review; it is not a current security certification. A 2026-11-21 source-only follow-up found additional release-workflow and DOCX fallback concerns listed in `docs/ISSUE_REGISTER.md`. No dynamic testing, fuzzing, device pen-testing, Gradle build or APK inspection was performed in this audit.

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
| 10 | Whole-file reads (`readBytes`) of untrusted files, OOM | High | **Partially bounded**: DOCX/PPTX compressed file size capped at 25 MiB, but DOCX still creates base64 copy and recovery inflates `word/document.xml` without a cap; see `AUD-DOCX-001/002` |
| 11 | Release build signed with the debug key | Medium | Env-based signing exists, but workflow has a fresh-per-run fallback key and direct shell input interpolation; see `AUD-REL-001/002` |
| 12 | Untrusted SQLite would be opened by the new viewer | n/a (new) | Opened read-only, quoted identifiers, SELECT-only; residual risk: SQLite parser bugs on hostile files |
| 13 | XML parsing of ODF/EPUB (XXE) | n/a (new) | DTDs/external entities disabled |
| 14 | No `INTERNET` permission | n/a | Manifest has no declared `INTERNET` permission; this reduces direct network access but is not a complete security guarantee |
| 15 | Archive password field not masked | Low | Historical issue; current TODO records masking as fixed, but not runtime-tested in this audit |
| 16 | Unscoped JitPack repository | Low | **Open** (TODO P2) |
| 17 | `MANAGE_EXTERNAL_STORAGE` still requested (Play policy risk) | Policy | **Open**: now optional for opening files, still needed for browsing/search |
| 18 | Bundled JS libraries unpinned/unlicensed notice | Low | Notices added; license verification open |

Residual risk notes: WebView renderers parse hostile DOCX/PPTX in JS; the mitigation is no network and no bridge capabilities beyond view callbacks.

## Loop 6 review notes (static review only; no device test)
- PDF.js 4.10.38 and its worker/CMaps/fonts are bundled locally. The PDF viewer serves only `/assets/` from app assets and the requested `document.pdf` through an exact path check; PDF WebView file/content access remains disabled, and top-level navigation is limited to those local reader/source paths. The trusted local reader uses a JavaScript bridge for page/search/password/tap callbacks. No remote assets or `INTERNET` permission were added.
- TXT preview HTML escapes source text; Markdown HTML is sanitized. Their local JavaScript bridge is limited to code-copy and reader callbacks. External navigation from those generated previews is blocked.
- DOCX keeps file access enabled only to load `android_asset/editor/` resources; content access, cross-file access, and universal file access are disabled. Top-level navigation outside the editor asset path is blocked. The bridge exposes DOCX bytes plus view/search callbacks; it does not expose file writes.
- Large TXT/Markdown search streams the original file and retains bounded hit offsets; it does not load the complete source into the WebView. Print/copy use a bounded character load with an error on overflow. This does not remove parser/renderer risks for hostile document contents.
- Java/Kotlin compilation and dynamic Android/WebView security checks were not run in this sandbox. Confirm manifest permission, asset interception, navigation blocking, and memory bounds on a built app before release.

## 2026-11-21 static follow-up

- **DOCX recovery path:** renderer-error bridge posts to the main thread and invokes `DocxExtractor`; that extractor calls `ZipInputStream.readBytes()` for `word/document.xml` without an inflated-size bound. This is an unbounded decompression/UI responsiveness risk (AUD-DOCX-002), not a reproduced exploit.
- **DOCX normal path:** the WebView normally streams bytes from a local exact-path handler, but the ViewModel still reads the complete file and base64-encodes it first for fallback (AUD-DOCX-001). DOCX file-size cap does not bound inflated package-part size.
- **Release workflow:** dispatch fields are interpolated into `run:` shell source in a job with release-write permission; no hostile input was executed. The no-secrets path creates a per-run fallback signing key (AUD-REL-001/002).
- **Other supply-chain/process risks:** JitPack is unscoped; third-party JS license entries remain unverified. See AUD-SUPPLY-001 / AUD-DOC-002.
- **Current verification limit:** these observations are static only. The current environment could not build the app or run Android/WebView tests.
