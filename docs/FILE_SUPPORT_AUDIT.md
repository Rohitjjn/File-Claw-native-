# File Claw — File Support Audit (Phase 0, 2026-11-17)

**What this is:** the honest, code-grounded list of every file family the app claims to support,
what actually happens when you open it today, what is broken or fragile, and what to add next.
The generated matrix lives in `docs/FILE_FORMATS.md` (counts re-verified 2026-11-17 — see §1);
this document is the *audit*: evidence, severity, and where each fix lands.

**Verification status of this audit:** static analysis only. **No Android SDK was used in the
authoring sandbox — the app has never been compiled in the environment this audit ran in.**
Nothing below should be read as "device-proven". GitHub Actions (once `ci.yml` is green) and the
`docs/SANITY_CHECKLIST.md` run are the proof steps. Severities: **P0** = crash/data-loss/build-blocker,
**P1** = broken or materially degraded feature, **P2** = quality/polish, **P3** = nice-to-have.

---

## 1. Coverage snapshot (re-derived from `FormatRegistry.kt`, 2026-11-17)

| Metric | Value |
|---|---|
| Format groups | **69** |
| Extensions | **399** |
| `Support.FULL` (dedicated viewer) | **284** extensions |
| `Support.PARTIAL` (degraded/codec-dependent) | **32** extensions |
| `Support.IDENTIFY` (recognised; hex viewer only) | **83** extensions |
| Editable today | TEXT, MARKDOWN, HTML, DELIMITED (CSV/TSV/PSV) — read-only when truncated |

Group counts by `FileKind`: TEXT 11 · MARKDOWN 1 · HTML 1 · SVG 1 · DELIMITED 1 · SPREADSHEET 1 ·
DOCX 1 · PPTX 1 · PDF 1 · RTF 1 · ODF 3 · EPUB 1 · LEGACY_OFFICE 1 · IMAGE 9 · BINARY 4 · AUDIO 7 ·
VIDEO 5 · ARCHIVE 10 · COMPRESSED_STREAM 3 · SQLITE 1 · FONT 2 · MODEL_3D 3.

**Answer to "which files does the app properly support right now"** — §2 (fully), §3 (partially),
§4 (identify-only). **Answer to "which are broken"** — §5, the issue register (AUD-###).
**Answer to "what can we add"** — §6.

---

## 2. Working today — dedicated in-app viewer (FULL, 284 extensions)

Code paths from `MainViewModel.buildContentState` + `FilePreviewScreen.kt`. "Verified" = unit-tested
on the JVM; device behaviour is **not** verified unless marked.

| Area | Formats (ext) | How it renders | Caveats / notes | Verification |
|---|---|---|---|---|
| Plain text & code | `txt log nfo …`, all source code (`kt java py js ts cpp rs …`), config (`toml ini env gitignore …`), data-as-text (`json xml yaml sql …`) | `TextLoader` (250k-char cap) → `TextSuccess` → in-app viewer + inline editor | > cap ⇒ read-only banner; non-UTF-8 sin/BOM ⇒ windows-1252; BOM/charset round-trip saved | Kotlin tests ✔ (`TextIoTest` etc.); device ✖ |
| Markdown | `md markdown mkd mdx rmd …` | JetBrains markdown → sanitized HTML → `SearchableDocumentWebView`; inline edit | Same cap story; find-in-document works; copy fenced code blocks | Tests ✔; device ✖ |
| HTML / XHTML | `html htm xhtml` | WebView, **scripts off by default** (opt-in), blocked navigation | Interactive pages render inert (by design) | Device ✖ |
| SVG | `svg` | `<img>` data-URI in scriptless WebView | ≤ 5 MB; scripts stripped by design | Device ✖ |
| CSV / TSV / PSV | `csv tsv tab psv` | `DelimitedParser` (RFC 4180) → editable grid | 50k × 256 cap else read-only; `;`/tab-sniffed; BUG-004 wide-grid clipping | Tests ✔; device ✖ |
| XLSX (view) | `xlsx xlsm xltx xltm` | fastexcel-reader (cached values) + x-spreadsheet grid WebView | **View-only**, first sheet, cap; cannot edit | Tests ✔; device ✖ |
| DOCX | `docx docm dotx dotm` | docx-preview in WebView (`/assets/` bridge after Loop 14 fix) + pure-Kotlin `DocxExtractor` fallback with honest "recovered text" banner | ≤ 25 MB (base64); page count not reported; search/zoom/dark mode/print shipped in Loops 6–15 | Tests ✔ partial; device ✖ |
| PPTX | `pptx pptm ppsx potx potm` | pptx-preview in WebView | ≤ 25 MB; **no search, no zoom UI parity, no print path**; see AUD-005/AUD-011 | Device ✖ |
| PDF | `pdf` | Bundled PDF.js 4.10.38 via `WebViewAssetLoader`; search/rotation/scrubber/skeletons/8× zoom (Loops 6–15) | Password supported; corrupt-file path exists (see AUD-013); dark mode removed by design; 8× zoom memory unverified | Tests ✔ (9 policy suites); device ✖ |
| RTF | `rtf` | `RtfExtractor` → read-only text | Text only — pictures/tables/styles dropped | Tests ✔ |
| EPUB | `epub` | `EpubReader` + `HtmlSanitizer` → HTML view | Spine order; images/CSS/scripts stripped; ≤ 3M chars | Tests ✔ |
| ODF (text/sheet/slides) | `odt ods odp (+ ott/ots/otp/fodt)` | `OdfTextExtractor` (SAX) → read-only text | Text only; DTDs refused; ≤ 64 MB content.xml | Tests ✔ |
| Images | `png apng jpg jpeg gif webp bmp ico` | Coil + telephoto 15× zoom; theme-adaptive backdrop | Animated GIF plays; **animated WebP/APNG unverified** | Device ✖ |
| Audio | `mp3 wav ogg m4a flac midi` | `MediaPlayer` + hand-rolled UI | **MIDI listed FULL but MediaPlayer cannot decode it — see AUD-006**; prepare() blocks main thread — AUD-007 | Device ✖ |
| Video | `mp4 mkv webm 3gp` | `VideoView` + MediaController | Black-box on codec failure; no error surface — AUD-008 | Device ✖ |
| Archives | `zip jar apk …`, `7z`, `tar`, `tgz/tbz2/txz`, `ar deb`, `cpio` | zip4j/commons-compress tree view; zip-slip flagged; bomb caps; encrypted needs password | RAR: identify-only (see §4) | Tests ✔ |
| Compressed streams | `gz bz2 xz` | Stream decompression viewer | | Tests ✔ |
| SQLite | `sqlite sqlite3 db …` | `SqliteBrowser` (read-only, 100 rows/page, quoted IDs) | Hostile-DB parser risk remains | Tests ✔ |
| Fonts | `ttf otf ttc otc` | `Typeface.createFromFile` glyph specimen | **WOFF/WOFF2/EOT = identify-only**; invalid fonts fall back silently — AUD-009 | Device ✖ |
| 3D models | `stl`, `obj` | `MeshParser` → Compose wireframe | ≤ 300k tris parsed (stride-sampled), ≤ 25k drawn; no shading | Tests ✔; device ✖ |
| Legacy Office | `.xls` → read-only grid via `Biff8XlsParser`; `.doc` → text via `LegacyDocExtractor` | Dedicated extractors; everything else OLE2 = identify-only | **FILE_FORMATS.md is stale — says "identify-only" for all OLE2**; see AUD-002 | Tests ✔ |

---

## 3. Partial (32 extensions) — opens, degraded

| Format (ext) | Reality |
|---|---|
| HEIC/HEIF/AVIF (`heic heif avif`) | Device decoder or nothing; on codec-less devices the image area is blank with no message (AUD-010) |
| TIFF (`tif tiff`) | Same — no bundled libtiff |
| Camera RAW DNG (`dng`) | Same — no LibRaw |
| Other audio (`amr wma aiff ape …`) | MediaPlayer decode where device supports it; no gap detection |
| Other video (`avi mov mpg flv wmv ogv mts …`) | VideoView gamble on device codecs |

---

## 4. Identify-only (83 extensions) — honest hex viewer + label

Legacy Office binary (`.ppt .msg .vsd .xlsb .wps .mpp`) · Photoshop/layered (`psd xcf ai eps ps kra
sketch fig`) · Other archives/disk images (`iso cab dmg img wim zst lz4 arj lzh rpm …`) ·
Executables (`exe dll so dylib elf class dex wasm msi …`) · Binary data (`bin dat parquet avro orc
h5 hdf5 mat pkl npy tflite onnx gguf …`) · Web fonts (`woff woff2 eot`) · Other 3D/CAD (`ply glb
gltf fbx blend dwg dxf step …`) · **RAR** (`rar r00`).

Hex viewer shows only the **first 4 KB** (`MainViewModel.kt`, `maxBytesToDump = 4096`) — for
identify-only binaries this is the entire experience, and it is truncated (AUD-003).

---

## 5. Issue register — broken / fragile / no-preview (Phase 1 targets)

Status legend: **OPEN** = not fixed · severity in (P0–P3).

### P0 / build & data integrity
| ID | Issue | Evidence | Fix hint |
|---|---|---|---|
| **AUD-001** | **Dead dependency `com.github.mhiew:android-pdf-viewer`** — zero imports anywhere; it is the only JitPack-sourced artifact and the unscoped JitPack repo is un-filtered. Supply-chain exposure + APK/build bloat. | `app/build.gradle.kts` vs grep of `app/src` | Remove dependency + scoped `content { includeGroup }` for JitPack (or drop JitPack entirely) |
| |↳ **FIXED 2026-11-17** — dependency removed; JitPack removed. | | |
| **AUD-002** | **Docs vs code drift** — `FILE_FORMATS.md` says "corrupt PDFs currently spin" and "legacy .doc/.xls = identify-only"; code surfaces `onPdfError` and extracts both. `TODO.md` #3 still says write-back missing although `MIGRATION_3_4`/`sourceUri` exist. | FILE_FORMATS.md vs `MainActivity.kt:onPdfError`, `AppDatabase.kt`, `MainViewModel.kt` | Fix docs (done in this phase for FILE_FORMATS); device-verify; regenerate generated table |
| |↳ **PARTIAL 2026-11-17** — MIDI/registry change regenerated FILE_FORMATS (counts 281/32/86) and repaired mangled ✅/🔄/⏳ glyphs; `.doc`/`.xls` registry alignment stays a Phase 1/4 task per the existing note. | | |
| **AUD-034** | **Room schema drift** — `AppDatabase` is version **4** with `exportSchema = true`, but `app/schemas/…/` contains only **3.json**. The v4 schema (`sourceUri`) was never committed. Migration tests rely on a stale baseline. | `app/schemas/com.example.data.AppDatabase/` | Regenerate schemas via `ksp` (`room.schemaLocation`) and commit 4.json |
| |↳ **BLOCKED 2026-11-17** — KSP cannot run in the working sandbox (no network to Maven Central/Google); `4.json` must be generated on a networked machine (`./gradlew kspDebugKotlin`); nothing consumes 4.json in tests today; CI now emits an explicit ::warning:: with the command (see §9). | | |
| **AUD-026** | **App has never been compile-verified in its authoring sandboxes**; Loops 02–15 mostly shipped with "not verified" stamps. Real compile fallout is unknown. | `docs/TODO.md` KNOWN ISSUES #1; worklog | Phase 1 gate: green `compileDebugKotlin` + `testDebugUnitTest` + `assembleDebug` in CI before any feature work |

### P1 / user-facing breakage
| ID | Issue | Evidence | Fix hint |
|---|---|---|---|
| **AUD-003** | **Hex viewer dumps only the first 4 KB** and has no paging/offset navigation — for identify-only formats (the whole §4 list, including every `.rar`/`.iso`) this is broken-by-truncation. | `MainViewModel.kt:476`; `FilePreviewScreen.kt:4624` | Paged hex view (64 KB windows, offset input) in the shared `HexViewer` |
| |↳ **FIXED 2026-11-17** — paged 64 KB windows + offset jump; pure `HexDump` core + 9 unit tests. | | |
| **AUD-004** | **DOCX/PPTX whole-file base64 hand-off** — ~2.7× memory duplication, hard 25 MB cap, > cap silently degrades to archive listing; DOCX print runs a second off-screen WebView from the same base64 path. | `MainViewModel.kt:685/1312`, `DocxPreviewWebView`, `FilePrintHelper` | Stream via `HttpRange`/`PdfRangeResponder`-style local HTTP server for DOCX/PPTX bytes (Loop-15 PDF pattern) |
| **AUD-005** | **PPTX viewer can spin forever** — `onReceivedError`/`onReceivedHttpError` only `Log.e` and never clear `isWebViewLoading`; main-frame failure ⇒ "Loading Presentation…" eternally. Loop 14 fixed the same class of bug for DOCX only. | `PptxPreviewWebView` (`FilePreviewScreen.kt:5288`) | Clear loading on error + show honest error screen w/ retry (port `DocxTextRecovery` policy); add timeout watchdog |
| |↳ **FIXED 2026-11-17** — main-frame errors + 15 s watchdog flip to labeled error card (reason + Retry); `onDocumentError` routes to the same card. | | |
| **AUD-006** | **MIDI advertised FULL but `MediaPlayer` can't decode MIDI** — `prepare()` throws, the catch block swallows (`// handle error`), screen shows a dead player (0:00 / 0:01) with no message. | FormatRegistry `mid/midi/kar` FULL; `AudioPlayer` catch swallow | Detect unsupported games via `MediaPlayer.OnErrorListener`; show "MIDI preview isn't supported — export as audio" notice; drop MIDI to identify-only until a synth ships |
| |↳ **FIXED 2026-11-17** — MIDI dropped to identify-only (`mid/midi/kar`); AUDIO+IDENTIFY routes to hex view with honest notice. | | |
| **AUD-007** | **Audio `prepare()` blocks the main thread** (`LaunchedEffect` default dispatcher) — ANR risk on slow decodes; also no error listener at all. | `AudioPlayer` | `Dispatchers.IO` + `prepareAsync()` + `setOnErrorListener` + error UI |
| |↳ **FIXED 2026-11-17** — `prepareAsync()` + `setOnErrorListener` + Dispatchers-based ticker; inline "can't decode" card with Open-with / View-as-hex. | | |
| **AUD-008** | **Video black-box on codec failure** — `VideoView` with no `setOnErrorListener`; unsupported `.mkv`/`.avi` ⇒ 260 dp black rectangle, zero feedback. | `VideoPlayer` | OnErrorListener → inline "can't decode on this device" + share/open-with fallback |
| |↳ **FIXED 2026-11-17** — `setOnErrorListener` → inline error card (Open-with / View-as-hex) instead of dead black box. | | |
| **AUD-009** | **Invalid fonts silently fall back** — bad TTF/OTF shows the system default with no indication; "FULL" is a lie for corrupt files. | `FileKind.FONT` branch never validates | Verify glyph load (`Typeface` paint test); show "invalid font — showing system default" notice |
| |↳ **FIXED 2026-11-17** — `Typeface.createFromFile` validated via `Paint.hasGlyph("AGg0")`; honest invalid-font banner. | | |
| **AUD-010** | **HEIF/AVIF/TIFF/DNG blank-image failure mode** — device decoder absent ⇒ empty area, no message. | `IMAGE` branch via Coil; no error fallback | Coil `onError` → honest notice + offer hex view |
| |↳ **FIXED 2026-11-17** — bounds-decode pre-flight (`inJustDecodeBounds`); undecodable images render an honest error card with Hex-view action instead of a blank area. | | |
| **AUD-011** | **PPTX feature desert** — no find-in-document, no zoom UI parity, no print path, night mode rudimentary; feature gap vs DOCX despite same WebView stack. | `PptxPreviewWebView` | Port DOCX pipeline (search bridge, `PrintHelper`, zoom policy) or scope PPTX down honestly in the registry |
| **AUD-012** | **HTML preview find has no next/prev** (BUG-008 carried); highlight-only `findAllAsync`. | known-issues BUG-008 | Add match navigation bridge or drop the find affordance for HTML |
| **AUD-013** | **Corrupt-PDF behaviour unverified** — code has `onPdfError` + "Try again" affordance but FILE_FORMATS.md still documented infinite spin; no unit/UI test covers the corrupt path. | `PdfPreview`; docs drift | Device/CI test with truncated/corrupt PDF; keep docs in sync |
| |↳ **DEFERRED 2026-11-17** — corrupt-PDF path exists (`onPdfError` + "Try Again"); no automated corruption test yet (needs WebView/CI or device); tracked in Phase 2. | | |

### P2 / correctness, security, polish
| ID | Issue | Evidence | Fix hint |
|---|---|---|---|
| **AUD-014** | **CSV wide grids clip** (BUG-004) — no horizontal summary/fade for > screen width. | known-issues BUG-004 | Edge fade + sticky first column option |
| |↳ **PARTIAL 2026-11-17** — right-edge fade indicator added to the grid WebView (scroll-synced via the x-spreadsheet scrollbar element); sticky first column deferred (canvas-rendered grid; would need renderer patch). | | |
| **AUD-015** | **Password dialog keyboard overlap** (BUG-005). | known-issues BUG-005 | `imePadding()` on dialog content |
| |↳ **FIXED 2026-11-17** — `imePadding()` on both password dialogs. | | |
| **AUD-016** | **PDF placeholder geometry from page 1** (BUG-006) — mixed-orientation docs shift on first paint. | known-issues BUG-006 | Per-page placeholder sizing as sizes resolve |
| |↳ **FIXED 2026-11-17** — placeholders refine to each page's real geometry off the critical path after first paint (search.html `refinePlaceholderGeometry`); zoom/rotation paths unchanged. | | |
| **AUD-017** | **DOCX pinch transient horizontal overflow** (BUG-007). | known-issues BUG-007 | Clip during preview transform |
| |↳ **FIXED 2026-11-17** — scroller clipped (`overflow: hidden !important` via `body.pinching`) during pinch-preview transforms. | | |
| **AUD-018** | **`MANAGE_EXTERNAL_STORAGE` requested** — needed for the file-manager role, but Play-policy restricted; no SAF-only mode. | Manifest; security-audit #17 | SAF-first browse path; keep all-files behind explicit "file manager mode" |
| **AUD-019** | **JitPack unscoped** repo declaration (`FAIL_ON_PROJECT_REPOS` with bare jitpack). | `settings.gradle.kts` | Scoped `content {}` (or remove with AUD-001) |
| |↳ **FIXED 2026-11-17** — JitPack repository removed from `settings.gradle.kts`. | | |
| **AUD-020** | **`applicationId = com.aistudio.filesclaw.apxqzy`** — AI-Studio-generated; changing post-release orphans updates/signing continuity. | gradle | Decide + document before Play; not ad hoc |
| **AUD-021** | **Release signing fails open** — falls back to the debug key silently; Play requires real keystore (env plumbing exists, fail-open defeats it). | build-and-release.yml | Fail closed when `build_type=release` and no keystore present |
| |↳ **FIXED 2026-11-17** — build-and-release.yml fails closed when `build_type=release` and no keystore secret is configured; debug fallbacks kept for sideloading with `::warning::`. | | |
| **AUD-022** | **Test-count drift** — docs cite 134/146/167/199/225 `@Test` methods at various loops; no single source of truth. | TODO/worklog/changelog | Phase 1: recount and write one number into CHANGELOG |
| |↳ **ADDRESSED 2026-11-17** — CHANGELOG/PHASES/audit recount to 231 `@Test` methods (this run); drift watch moved to Phase 2 (JaCoCo gate). | | |
| **AUD-023** | **detekt advisory** (`ignoreFailures = true`), JaCoCo gate unverified, `lint abortOnError=true` never run clean in-sandbox. | build.gradle.kts | Baseline on CI; ratchet gradually |
| **AUD-024** | **Write-back vs in-app-copy dual messaging** — code implements source-URI write-back (Room `MIGRATION_3_4`); UI strings/sanity checklist still say "saved to the in-app copy". Verify + unify copy. | MainViewModel vs SANITY_CHECKLIST | Reconcile strings; device-verify round trip |
| |↳ **ADDRESSED 2026-11-17** — code strings already write-back-aware; docs (SANITY_CHECKLIST/TODO/AGENTS/PATCH_NOTES) reconciled; **device round trip NOT verified**. | | |
| **AUD-025** | **Archive unsafe-entry UX** — slip-flagged entries render indistinguishable from safe ones; no distinct archive-bomb refusal state. | P1 TODO item | Warning icon + distinct refusal dialog (wire existing `ArchiveSafety` flags) |
| **AUD-027** | **No Compose UI tests** — open→edit→save→reopen round trip untested; Robolectric CUJs absent. | TODO P1 | Add for text/CSV/markdown save paths |
| **AUD-028** | **English-only hard-coded strings** — `strings.xml` has only `app_name`; every label is literal Kotlin. | res/values/strings.xml | Extract (Phase 2+; i18n-ready) |
| **AUD-029** | **CJK encodings fall back to windows-1252** — GBK/Shift-JIS/Big5 files mojibake. | FILE_FORMATS notes | Charset detector (juniversalchardet) |
| **AUD-030** | **`FilePreviewScreen.kt` ≈ 5,390 lines** — viewers, chrome, and preview logic interleaved; P1 split pending (was 2,980 at Loop-2 writing). | wc -l | Phase 2 decomposition |

### P3 / nice-to-have
| ID | Issue |
|---|---|
| **AUD-031** | Print/export lacks progress + failure callback in some paths (image print) |
| **AUD-032** | No in-app "unsupported → request support" feedback loop (settings mailto/prefilled issue) |
| **AUD-033** | SampleFilesGenerator only in Settings; no "open sample" shortcut from empty states |

---

## 6. Docs that were wrong and are now corrected (this phase)
- `FILE_FORMATS.md` parser notes: PDF corrupt-file row, LEGACY_OFFICE row (`.doc`/`.xls` extraction
  shipped in Loop 3), hex-dump cap note — corrected; generated extension table unchanged (still
  accurate: 399/284/32/83, re-verified).
- `README.md` rewritten (stale AI-Studio "remove signingConfig" instructions and Gemini key steps — the app has **zero** Gemini/API-key code paths).
- Live loop docs moved from repo root into `docs/` (`changelogs.md`, `known-issues.md`, `worklog.md`, `BUILD_GUIDE.md`, `PATCH_NOTES.md`); all references updated.
- Stale root prompts (`NEXT_LOOP_PROMPT_v3/v4/v5`, `NEXT_PROMPT.md`) deleted; git history retains them.

## 7. Candidate formats & features to add next (ranked)

**Tier 1 (high value / low risk)**
1. **`.ppt` legacy text recovery** via the OLE2 path (reuse `Ole2CompoundFile` + a small `Ppt`-text
   extractor) — currently the only common Office binary with zero recovery.
2. **`.xlsb`** (BIFF12) via the Biff8 parser's successor or an `excelize`-style reader.
3. **CJK charset detection** (`juniversalchardet`) — unblocks a whole class of real-world text files.
4. **MIDI** → drop to identify-only or bundle a tiny synth/decoder (see AUD-006 decision).
5. **Animated WebP / progressive JPEG** verification pass (free correctness).

**Tier 2 (medium effort)**
6. **RAR read** via a pure-Java extractor (previous junrar decision revisited) or libarchive-JNI — needs license + footprint review.
7. **GLB/glTF + PLY viewers** — natural successors of the STL/OBJ `MeshParser`; shaded STL too.
8. **WOFF/WOFF2 → TTF decompression** (zlib-wrapped tables) for web-font preview.
9. **`.pages` / `.numbers` / `.key`** — best-effort text recovery from the iWork ZIP (index.xml); registry: partial.
10. **DXF** (ASCII) wireframe — pair with the 3D pipeline.
11. **Sheet picker for XLSX** (all sheets, not just first) — small ViewModel change.
12. **Parquet/Avro read-only preview** (Apache Arrow Kotlin) — big-file story for data users.

**Tier 3 (frontier)**
13. **ISO/CAB** extraction via commons-compress — identify-only table gains one real viewer.
14. **Subtitle burn-in** for `.srt/.vtt` over video.
15. **Markdown WYSIWYG editing** (mirror of the DOCX-lite dream, honest scope).
16. **Diff viewer** for two text files (pairs beautifully with the "recents" model).
17. **EPUB images/CSS** with a sanitizer-constrained style subset (opt-in "rich EPUB").
18. **Zstandard** (`.zst`) via xerial-snappy/zstd-jni.

**Not recommended:** DOCX/XLSX *editing* (round-trip fidelity trap), video transcoding, cloud
hand-off of any kind, `MANAGE_EXTERNAL_STORAGE` removal without an SAF-first UX (keep — see AUD-018).

---

## 8. Phase 1 fix order (feeds `PHASE_1_PROMPT.md`)

1. AUD-026 → Quality Gate green in CI (compile/test/assemble) — everything else queues behind it.
2. AUD-001 + AUD-019 + AUD-021 → dependency & supply-chain hygiene.
3. AUD-005, AUD-006, AUD-007, AUD-008, AUD-010, AUD-009 → no silent failure screens (P1 media/PPTX).
4. AUD-003 → paged hex viewer (AUD-003 also unblocks the whole identify-only family).
5. AUD-034 → Room 4.json committed (schemas regenerate via ksp).
6. AUD-002/AUD-024 → docs/strings reconciliation.
7. AUD-014…AUD-017 → the four known UI bugs (BUG-004/005/006/007).
8. AUD-027/AUD-028 → tests before features.


## 9. Phase 1 resolution log (2026-11-17)

Scope per `docs/prompts/PHASE_1_PROMPT.md`. Status legend: **FIXED** (code landed; not device-verified unless said), **DEFERRED** (with reason), **BLOCKED** (environment). Nothing here is claimed device-verified — the app still requires the device SANITY_CHECKLIST.

| ID | Phase 1 outcome |
|---|---|
| AUD-001 | FIXED — `com.github.mhiew:android-pdf-viewer` removed from `app/build.gradle.kts` (zero imports). |
| AUD-002 | PARTIAL — FILE_FORMATS regenerated (281 full / 32 partial / 86 identify; MIDI moved); emojis repaired; `.doc`/`.xls` note stands. |
| AUD-003 | FIXED — paged 64 KB hex windows (`core/hex/HexDump`), prev/next, jump-to-offset; unit-tested (9 tests, run green locally). |
| AUD-005 | FIXED — main-frame `onReceivedError`/`onReceivedHttpError` now clear loading and show a labeled card (reason + Retry); 15 s watchdog; `onDocumentError` included. |
| AUD-006 | FIXED — MIDI identify-only; AUDIO+IDENTIFY → hex viewer with notice; no more dead MIDI player. |
| AUD-007 | FIXED — async prepare, error listener, inline error UI with Open-with / View-as-hex; main thread no longer blocks on decode. |
| AUD-008 | FIXED — video codec failure → inline error card (Open-with / View-as-hex). |
| AUD-009 | FIXED — font validity proven with `Paint.hasGlyph("AGg0")` before specimen render; invalid → banner. |
| AUD-010 | FIXED — bounds-decode pre-flight; honest undecodable-image card + Hex view. |
| AUD-013 | DEFERRED — instrumented CI now exists (API 34 emulator, run `44056165653`/`44063464989` green) but is deliberately narrow (launch, intent-resolver wiring, Room 3→4 migration). No corrupt-PDF error-path test yet; `onPdfError` + Try Again remains the shipped path. See `docs/TODO.md` KNOWN ISSUES #10. |
| AUD-014 | PARTIAL — edge fade added (scroll-synced); sticky first column deferred (canvas grid). |
| AUD-015 | FIXED — `imePadding()` both password dialogs. |
| AUD-016 | FIXED — per-page placeholder refine after first paint (mixed-orientation PDFs). |
| AUD-017 | FIXED — scroller clipped during pinch transforms. |
| AUD-019 | FIXED — JitPack removed. |
| AUD-021 | FIXED — release fails closed without signing secrets. |
| AUD-022 | ADDRESSED — count is now **249 `@Test` methods** (static grep, 2026-11-17, after Phase 2 added instrumented + screenshot + HexDump suites). |
| AUD-024 | ADDRESSED — strings/docs unified around sourceUri write-back; device round trip unverified. |
| AUD-034 | FIXED — `app/schemas/com.example.data.AppDatabase/4.json` (200 lines) committed; generated by the CI KSP step and bot-committed (`cc4d3ec`, 2026-11-17). Validated indirectly: instrumented `MigrationTestHelper` test migrates 3→4 on a real emulator (run `44056165653` green). |
| AUD-026 (quality gate) | FIXED — the full Android gate runs blocking in CI on every push to this branch (`compileDebugKotlin` → `compileDebugAndroidTestKotlin` → `testDebugUnitTest` → `jacocoCoreVerify` (83.84 % core lines, measured) → `assembleDebug`; plus `detekt` blocking on `core/`). Latest green: run `44073036452` (2026-11-17). Local Gradle-free verifier remains for the pure-`core/` tree. |

### Deferred with written reasons
- **Sticky first column (BUG-004 tail)** — x-spreadsheet renders to canvas; pinning col 0 means patching the minified renderer. Revisit with the Phase 3 grid work.
- **Corrupt-PDF automated test (AUD-013)** — needs a WebView/instrumented test or device; scheduled under Phase 2 (test infrastructure) instrumented gate.
## 10. Phase 2 resolution log (2026-11-17)

Branch `arena/5100f329-file-claw-native`; every claim below is anchored to a GitHub Actions run (the authoring sandbox has no Android SDK). Status legend: **FIXED** (verified in CI), **BASELINED** (recorded debt, still blocking new regressions), **DEFERRED** (written reason).

| ID / item | Phase 2 outcome |
|---|---|
| AUD-026 (gate) | FIXED — full Android gate blocking in CI; instrumented job (API 34 emulator) also green. See §9 table note rewritten. |
| AUD-034 | FIXED — 4.json committed; migration validated on-device by `MigrationTestHelper` (instrumented). |
| Split `FilePreviewScreen.kt` | DONE — 5,740 → **2,306 lines**; largest viewer (`PdfViewer.kt`) 747 lines; `FilePreviewScreen` keeps only the orchestrating screen composable (effects, find orchestration, Scaffold, menu, dialogs) — further reduction is a behavioral refactor, deliberately not done this phase. 26 files under `ui/screens/viewers/`; no viewer file exceeds 800 lines (enforced by review). |
| Navigation | DONE — type-safe `ClawDestination` (@Serializable) Navigation Compose 2.8 routes; `hasRoute` compile issue worked around via serial-name comparison. `currentFileState` singleton remains deliberately (see §9/CHANGELOG — Phase 3+). |
| detekt | BASELINED — `core/` blocking with an **empty-then-generated baseline** (`config/detekt/baseline.xml`): 10 pre-existing findings above the threshold (ComplexCondition ×7, CyclomaticComplexMethod ×3 incl. `TextIO.read` 45 / `RtfExtractor.extract` 55, NestedBlockDepth, LongParameterList). New findings fail the build. `detektUi` (UI) is advisory-only until a dedicated triage. **Debt is recorded, not hidden.** |
| JaCoCo | FIXED — `jacocoCoreVerify` ≥ 80 % core lines, blocking; **measured 83.84 % (1816/2166)**; report uploaded as CI artifact `jacoco-core-report`. |
| Screenshot baselines | FIXED — Roborazzi golden masters (NoticeBanner, PropertyItem, AllFiles / Search / Settings empty states, light+dark; 10 PNGs under `app/src/test/snapshots/images/`); `verifyRoborazziDebug` is a blocking CI step. |
| Instrumented suite | FIXED (narrow) — launch smoke, manifest `ACTION_VIEW` resolver wiring, Room 3→4 migration; run `44056165653` / `44063464989` green. |
| Strings governance | FIXED for new strings — every user-visible string introduced in Phases 1–2 extracted to `strings.xml` (17 keys); legacy hardcoded strings remain and are Phase 7 (i18n) scope. |
| AUD-013 | DEFERRED — corrupt-PDF error-path test still absent (§9 note rewritten; `docs/TODO.md` KNOWN ISSUES #10). |
| AUD-027/AUD-028 | ADDRESSED — instrumented + screenshot + 9 HexDump tests + Room migration test landed before Phase 3. |
| AUD-022 | ADDRESSED — 249 `@Test` methods (static grep 2026-11-17). |

### Still open after Phase 2 (nothing new opened)
- 10 baselined core detekt findings (complexity hotspots in `TextIO`, `RtfExtractor`, `Delimited`, `FormatDetector`, `ArchiveSafety`, `Ole2CompoundFile`, `LegacyDocExtractor`, `TextFileSearchEngine`).
- `currentFileState` ViewModel singleton — removing it requires state-management redesign (Phase 3+).
- AUD-013 corrupt-PDF instrumented test; archive-password flow test; save-round-trip tests (emulator work, needs provisioning).
- HomeScreen screenshot baseline (wall-clock greeting is non-deterministic under Robolectric).
