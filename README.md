# Files Claw

**An offline Android app that previews any file and edits the common ones — in a calm, premium,
Claude-style interface. No accounts, no cloud, no network permission. Your files never leave your device.**

Open a PDF, DOCX, spreadsheet, archive, database, font, 3D model — or a mystery binary — and get an
honest, deliberate answer: a real viewer, a clearly-labelled partial view, or a paged hex dump.
Edit text, Markdown, code, and CSV/TSV with round-trip-safe saves (BOM/charset preserved, atomic
writes, backups). Warm paper `#FAF9F5`, charcoal ink, terracotta accent — the Claude look, executed
with restraint.

- **100 % offline** — bundled PDF.js / docx-preview / x-spreadsheet renderers; no analytics, no accounts
- **Preview breadth** — 399 extensions across 69 format groups (284 full viewers, 32 partial)
- **Editable** — text, Markdown, code, config, CSV/TSV (grid), with atomic save + backup
- **Safe** — zip-slip/zip-bomb protection, sandboxed WebViews, read-only when truncated
- **Claude-style design system** — see [`docs/DESIGN.md`](docs/DESIGN.md) and [`docs/UI_FRONTIER.md`](docs/UI_FRONTIER.md)

## Status

v1.3.0 in tree. Phase 0 audit (2026-11-17) done: repo hygiene, [PRD](docs/PRD.md),
[file-support audit](docs/FILE_SUPPORT_AUDIT.md), [UI frontier plan](docs/UI_FRONTIER.md), and the
[Phase 1 prompt](docs/prompts/PHASE_1_PROMPT.md). The app has **not** been compile-verified in its
authoring sandboxes — the Phase 1 Quality Gate (GitHub Actions) is the next step. See
[`docs/TODO.md`](docs/TODO.md) → KNOWN ISSUES.

## Build

**Requirements:** JDK 17 · Android SDK (platform 34, build-tools 34)

```bash
./gradlew assembleDebug        # APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # JVM unit tests (pure-Kotlin core)
./gradlew jacocoCoreVerify     # 80 % core line-coverage gate
```

No keystore is required for debug builds (AGP debug key is used). Full Quality Gate, CI, signing and
release instructions: **[`docs/BUILD_GUIDE.md`](docs/BUILD_GUIDE.md)**.

> No API keys or `GEMINI_API_KEY` are needed — there is no server-side AI in this app. (The AI Studio
> `metadata.json` mentions a server-side Gemini capability, but no code path uses it.)

## Documentation

Everything lives in **[`docs/`](docs/README.md)** — the index lists every doc by role. Start with:

| Doc | What |
|---|---|
| [`docs/PRD.md`](docs/PRD.md) | Product requirements — goals, scope, metrics, release plan |
| [`docs/FILE_SUPPORT_AUDIT.md`](docs/FILE_SUPPORT_AUDIT.md) | What opens today, what's broken, what to add next |
| [`docs/FILE_FORMATS.md`](docs/FILE_FORMATS.md) | Generated format matrix (from `FormatRegistry`) |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Layers, the universal opener pipeline, safety boundaries |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Milestones & phase plan |
| [`docs/CONTRIBUTING.md`](docs/CONTRIBUTING.md) | Definition of done, local checks |

## License & notices

App code: see repository. Bundled third-party assets (PDF.js, Foxit/Liberation fonts, JSZip,
docx-preview, pptx-preview, x-spreadsheet): [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
