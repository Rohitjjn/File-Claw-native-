# Roadmap

This roadmap reflects the inspected checkout, not a promise that every historical milestone was built or device-verified. Source-level capability detail is in [`FILE_FORMATS.md`](FILE_FORMATS.md); audited issues and verification status are in [`ISSUE_REGISTER.md`](ISSUE_REGISTER.md) and [`PROJECT_STATUS.md`](PROJECT_STATUS.md).

## Immediate sequence

1. **DOCX fit-width verification (active):** exact-SHA run [45569454759](https://github.com/Rohitjjn/File-Claw-native-/actions/runs/45569454759) on `51771c391868af5ab68a94f5a5e5d535fbb5435b` passed 360×800 and 412×915 fit-width instrumentation, but failed the native 50% pinch-floor assertion at `1.0`. Removing explicit `setInitialScale(100)` did not change the floor. The latest unverified hypothesis prepares temporary layout space when a native pinch starts; verify the gesture still pans/zooms naturally and the floor reaches 0.5 before treating this as a fix. Keep DOCX incomplete until full exact-SHA workflow passes.
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
