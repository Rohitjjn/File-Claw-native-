# PRD — Files Claw (FilesClaw)

| | |
|---|---|
| **Product** | Files Claw (app label: "Files Claw", `FilesClaw`) |
| **Status** | Living document — this is the Phase 0 (audit) baseline, written 2026-11-17 |
| **Platform** | Android (minSdk 26 / Android 8.0+, targetSdk 34) |
| **Category** | Offline file manager + universal viewer + lightweight editor |
| **Design direction** | Claude-style warm editorial UI (see `docs/DESIGN.md`, `docs/UI_FRONTIER.md`) |
| **Doc owner** | Product (update on every phase; never let this drift from the code) |

---

## 1. One-liner

**An offline Android app that can preview any file and edit the most common ones — in a premium,
Claude-style interface with no cloud, no accounts, and no data ever leaving the device.**

## 2. Problem

- Android has no single, trustworthy, **fully offline** app that opens arbitrary files (PDF, DOCX,
  archives, SQLite, fonts, 3D models, hex) without uploading them to a server.
- Most viewer apps are ad-heavy, send metadata/files to the cloud, or die on large/complex files
  (500 MB PDFs, huge logs, corrupt headers).
- Editing on mobile is either nonexistent or destructive (truncation, encoding loss, no backups).
- Users want the calm, premium feel of Claude's interface — not a utilitarian "downloader" UI —
  for everyday file work: reading, quick edits, sharing, printing.

## 3. Goals

### 3.1 Primary goals
1. **Preview everything.** Every extension the app claims to know opens into a deliberate, honest
   state — a real viewer, a labelled partial view, or a hex dump with a clear note. "Unknown file"
   must never mean a blank screen, a crash, or an infinite spinner.
2. **Edit the common files safely.** Text, Markdown, code, JSON/XML/YAML, CSV/TSV get real editing
   with round-trip fidelity (charset/BOM preserved), atomic saves, backups, and undo.
3. **Fully offline & private.** Zero network permission, zero analytics, zero accounts. All bundled
   renderers (PDF.js, docx-preview, x-spreadsheet) run locally.
4. **Premium Claude-style UI.** Warm paper/charcoal palette, terracotta accent, restrained glass,
   editorial typography, motion that explains — see `docs/DESIGN.md` and `docs/UI_FRONTIER.md`.
5. **Never lose user data.** Atomic writes, zip-safe archives, size caps with honest read-only
   flags, no silent truncation.

### 3.2 Non-goals (for now)
- Not a cloud drive / sync client (no accounts, no server).
- Not a full office suite (no DOCX/XLSX editing — view-only or recovered-text only; revisit in 2.x).
- Not a code IDE (editor is for inspection and quick edits, not project-scale development).
- Not a root/system tool (no storage access beyond what the user grants).

## 4. Target users

| Persona | Need |
|---|---|
| **Curious power user** | Opens "any file I download" — APKs, ISOs, fonts, DB dumps, E-books — wants instant answers: what is this, what's inside? |
| **Android-first knowledge worker** | Reads PDFs/DOCX/XLSX offline on commute, annotates mentally, prints, shares; needs DOCX/PDF to "just work" |
| **Developer / tinkerer** | JSON/YAML/TOML/env/conf inspection, log files (100 MB+), SQLite dumps, hex dumps, checksums (MD5/SHA-256) |
| **Privacy-conscious user** | Willing to trade polish elsewhere for zero network calls; needs honest offline guarantee |

## 5. Scope

### 5.1 In scope — preview
- **Documents:** PDF (PDF.js, search, dark-neutral, rotation, print), DOCX/PPTX (WebView renderers +
  pure-Kotlin fallbacks), XLSX (view grid), RTF/ODF (text), EPUB, Markdown, HTML, SVG.
- **Data:** CSV/TSV/PSV (editable grid, RFC 4180), SQLite (read-only browser), JSON/XML/YAML/TOML.
- **Media:** images (PNG/JPEG/GIF/WebP/BMP/ICO + device-codec HEIF/TIFF/DNG), audio (MediaPlayer),
  video (VideoView) with device-codec caveat.
- **Archives:** zip/7z/tar(+gz/bz2/xz)/ar/deb/cpio/gz/bz2/xz with zip-slip/zip-bomb protection.
- **Misc:** fonts (glyph preview), STL/OBJ 3D wireframe, hex view for everything unidentified.
- **Universal:** content sniffing (signature > extension > heuristic), mismatch flagging,
  399 extensions / 69 format groups at audit time (see `docs/FILE_SUPPORT_AUDIT.md`).

### 5.2 In scope — edit
- **Text family:** plain text, Markdown, code, config, data-as-text — full editing with
  single/multi-line inline editor, undo/redo, line numbers, find/replace, go-to-line.
- **CSV/TSV:** cell edit (explicit Save; no auto-save; RFC 4180 round-trip).
- **Encoding:** BOM/UTF-8/UTF-16 preserved; windows-1252 fallback; non-UTF-8 flagged.
- **Safety:** atomic temp+rename saves, pre-overwrite backup, read-only when truncated or converted.

### 5.3 In scope — file management & productivity
- Recent files, favorites/starred, batch select + move/copy/delete, grid/list toggle,
  global name search, in-document search (PDF/DOCX/TXT/MD), print/share, file properties
  (size, MIME, path, MD5/SHA-256), archive tree preview, sample files for benchmarking.

### 5.4 Explicitly out of scope (tracked in `docs/ROADMAP.md`)
- DOCX/XLSX/PPTX editing (view-only today).
- RAR extraction (identify-only), streaming base64-less DOCX/PPTX pipeline, huge-file pager beyond
  the bounded window, localisation (English-only), tablet-adaptive two-pane layout.

## 6. Functional requirements (must-have = P0/P1)

| ID | Requirement | Acceptance |
|---|---|---|
| FR-1 | Open from picker, tree, recents, shortcuts, other apps (`ACTION_VIEW`) | No crash; content:// imports sanitized & non-colliding |
| FR-2 | Detection is content-first; extension mismatch flagged | Unit-tested (`FormatDetectorTest`) |
| FR-3 | Every format in the registry resolves to a non-blank state | Audit in `docs/FILE_SUPPORT_AUDIT.md`; no empty screens |
| FR-4 | Saves are atomic; failed save never corrupts the original | `AtomicFileWriter` + backup |
| FR-5 | Truncated/large files are read-only with visible notice | `TextLoader` cap + `NoticeBanner` |
| FR-6 | Archive safety: slip, bomb, depth, entry caps; encrypted zip/7z password flow | `ArchiveSafety` + tests |
| FR-7 | In-document search for PDF/DOCX/TXT/Markdown with next/prev | Loop 6–15 pipeline; device-verify pending |
| FR-8 | WebViews are sandboxed: scripts off by default; no network; navigation blocked | `UniversalViewers`, `SearchableDocumentWebView` |
| FR-9 | Backups/checksums: properties sheet shows MD5/SHA-256 | Loop 5 feature |
| FR-10 | CI Quality Gate: compile + unit tests green on every change | `.github/workflows/ci.yml` (added this phase, unverified) |

## 7. Non-functional requirements

| Area | Requirement |
|---|---|
| **Offline** | No INTERNET permission. (Audit: JitPack dependency `android-pdf-viewer` is **dead code** — removing it also removes the only network-sourced build input; see audit §6.) |
| **Privacy** | No telemetry, no accounts, no clipboard exfiltration; imported files stay in app sandbox |
| **Performance** | Cold start < 1.5 s; 60 fps scroll; open 500 MB without OOM (core reader supports it; UI pager pending); 60 fps target measured, not guessed |
| **Reliability** | Crash-free ≥ 99.9 %; atomic saves; corrupt-file error surfaces (no spinner forever) |
| **Memory** | PDF canvas budget ≤ 24 M px/page, ≤ 3 resident canvases; DOCX ≤ 25 MB base64 cap |
| **Security** | PathGuard on all file access; SQLite read-only w/ quoted identifiers; zip-slip detection; WebView hardening; `MANAGE_EXTERNAL_STORAGE` used only for the file-manager role (see audit R-8) |
| **A11y** | Material3 semantics, 48 dp targets, TalkBack labels, font-scale safe layouts (audit gap) |

## 8. Success metrics

- **Coverage:** ≥ 90 % of registry extensions open to a deliberate state (today: **284 of 399** extensions full (**71 %**), 316/399 full+partial (**79 %**); target 90 % full / 95 % full+partial by 2.0).
- **Trust:** zero data-loss defects (P0) across release cycles; save round-trip byte-fidelity tests.
- **Speed:** median open-to-preview < 1 s for ≤ 50 MB PDFs; < 300 ms for ≤ 10 MB text.
- **Delight:** Play Store rating ≥ 4.5; "premium" cited in reviews; retention ≥ 40 % D30.
- **Build health:** Quality Gate green on CI; 80 % core line coverage enforced (`jacocoCoreVerify`).

## 9. Current state (Phase 0 snapshot, 2026-11-17)

- v1.3.0 in tree; Loops through 15 implemented; **most loops never compile-verified** in the authoring
  sandbox — the first real Gradle run is the top Phase 1 risk.
- `FilePreviewScreen.kt` ≈ 5,390 lines (monolith); navigation is shared-state singleton (P1 refactor).
- Docs drift: `FILE_FORMATS.md` still says "corrupt PDF spins" and ".doc/.xls identify-only" although
  code implements error surfacing and BIFF8/legacy-doc extraction; counts re-verified 2026-11-17
  (69 groups / 399 ext / 284-32-83) are accurate.
- Known broken/fragile list: **`docs/FILE_SUPPORT_AUDIT.md`**. UI improvement plan:
  **`docs/UI_FRONTIER.md`**. Next phase scope: **`docs/prompts/PHASE_1_PROMPT.md`**.

## 10. Release plan (maps to `docs/ROADMAP.md`)

| Phase | Theme | Exit criteria |
|---|---|---|
| **Phase 0** | Audit + repo hygiene | This document + audit + UI plan + Phase 1 prompt; unwanted files removed |
| **Phase 1** | Build green + broken-format repairs | Quality Gate green in CI; P0/P1 audit items fixed; dead deps removed |
| **Phase 2** | Architecture: split `FilePreviewScreen`, typed nav, Compose UI tests | Screen < 800 lines each; type-safe routes |
| **Phase 3** | Claude frontier UI rollout (per `docs/UI_FRONTIER.md`) | Root glass, tokens, motion system, adaptive tablet |
| **Phase 4** | Format depth: .ppt, streaming DOCX/PPTX, CJK charsets, RAR, GLB/PLY | See audit §7 candidate list |
| **Phase 5** | Performance + release hardening | Macrobenchmark gates, Play listing, applicationId decision |

## 11. Risks & open questions

1. **Never compiled.** Loops 02–15 were authored without an Android SDK; compile fallout is likely.
   Mitigation: Phase 1 starts with the Quality Gate; failures fixed before features.
2. **`applicationId` is still `com.aistudio.filesclaw.apxqzy`.** Changing it orphans existing
   installs/signing; decide deliberately before Play release (Phase 5), not ad hoc.
3. **`MANAGE_EXTERNAL_STORAGE`** is Play-policy sensitive; an SAF-first mode may be required.
4. **Docs trust.** Every doc claim must be labelled measured/unverified; keep
   `docs/FILE_SUPPORT_AUDIT.md` honest or the loop loses its compass.
5. **PDF.js vs pdfium**: two PDF stacks were once shipped; only PDF.js remains in use — the pdfium
   dependency must go (Phase 1).
