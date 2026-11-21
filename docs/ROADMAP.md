# Roadmap

This roadmap reflects the inspected checkout, not a promise that every historical milestone was built or device-verified. Source-level capability detail is in [`FILE_FORMATS.md`](FILE_FORMATS.md); audited issues and verification status are in [`ISSUE_REGISTER.md`](ISSUE_REGISTER.md) and [`PROJECT_STATUS.md`](PROJECT_STATUS.md).

## Immediate sequence

1. **Audit and workflow setup (current task):** record baseline/toolchain limits, adopt project instructions, correct high-impact documentation conflicts and create an evidence-based issue register. No application code changes.
2. **DOCX investigation (next phase, awaiting owner review):** reproduce the DOCX findings, validate local/WebView/print/search/fallback paths, prepare a detailed defect and test plan. No fixes until explicit product-owner approval.
3. **Approved DOCX fixes only:** implement narrowly, regression-test and device-test; update findings with exact evidence. PDF and other formats remain untouched unless a shared cause is proven and scope is explicitly expanded.
4. **Owner review:** stop after DOCX verification and wait for approval before selecting the next file format.

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
