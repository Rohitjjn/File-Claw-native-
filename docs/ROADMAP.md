# Roadmap

This roadmap reflects the inspected checkout, not a promise that every historical milestone was built or device-verified. Source-level capability detail is in [`FILE_FORMATS.md`](FILE_FORMATS.md); audited issues and verification status are in [`ISSUE_REGISTER.md`](ISSUE_REGISTER.md) and [`PROJECT_STATUS.md`](PROJECT_STATUS.md).

## Immediate sequence

1. **DOCX fit-width verification (active):** the initial WebView fit-width geometry passed in a prior emulator run, but the latest exact-SHA workflow failed the native 50% pinch-floor assertion. The working tree now adds explicit page-edge/overflow assertions at 360×800 and 412×915 and changes the light reader surface to a grey canvas with white page cards; these new Android assertions have not yet run. Keep the viewer incomplete until all regression checks pass and the exact pushed SHA has a fully green workflow. See `PROJECT_STATUS.md` and `ISSUE_REGISTER.md` for current evidence; do not infer device behavior from source or syntax checks.
2. **Owner review:** present the complete DOCX result and limitations. Do not call the DOCX task complete until its agreed verification gates pass.
3. **Other formats:** remain out of scope unless separately approved. Do not change PDF or other formats as part of DOCX remediation.

## Historical milestones

Older version/loop records remain in `docs/CHANGELOG.md`, `worklog.md`, `known-issues.md`, and `docs/prompts/`. They are not current CI/runtime evidence. Several status and test-count claims have been superseded or conflict with current source; refer to the audit register before relying on them.

## Source-checked format landscape

| Area | Current implementation examples | Important limits / not implied |
|---|---|---|
| Documents | PDF, DOCX, PPTX, RTF, EPUB, Markdown, HTML, text/code | DOCX renderer and fallback have open verification items; RTF/ODF are text-extracted; legacy `.doc` is text extraction and `.xls` read-only grid, `.ppt` identify-only. |
| Data | CSV/TSV/PSV, XLSX read-only, SQLite, JSON/XML/YAML | Feature parity and round-trip behavior vary; XLSX remains view-only. |
| Archives | ZIP, 7z, tar variants, gzip/bzip2/xz, ar/deb/cpio and others | RAR and some archive families are identify-only/unsupported. |
| Media | Images, audio/video, fonts and selected 3D formats | Device codec/API support varies; STL/OBJ are wireframe, other 3D formats may be identify-only. |

Consult `FormatRegistry.kt`, route logic in `MainViewModel.kt`, and `docs/FILE_FORMATS.md` before changing a claim.

## Performance goals (not measured in this audit)

Existing historical goals such as cold start, smooth scrolling, 500 MB files and crash-free sessions require a device benchmark plan and measurements before being treated as release gates. Do not quote historic performance claims as current measurements.
