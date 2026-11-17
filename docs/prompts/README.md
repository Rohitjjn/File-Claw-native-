# Loop prompts & phase prompts

One active prompt per phase. Old prompts are deleted once their phase ships — the repo keeps ONLY the
current next-phase prompt so agents never work from stale instructions. (Historical loop prompts live
in `docs/legacy/prompts/`; superseded ones are marked in place.)

| File | Status | Notes |
|---|---|---|
| `PHASE_1_PROMPT.md` | Shipped 2026-11-17 | Phase 1 — Quality Gate + broken-format repairs. CI gate green (run `44051142574` and later). |
| `PHASE_3_PROMPT.md` | **Active** | Phase 3 — Claude frontier UI ("The Quiet Desk"): tokens, glass-at-root NavHost, motion system, bundled OFL typefaces, adaptive layout, reader chrome v2, a11y hardening. |
| `PHASE_2_PROMPT.md` | Shipped 2026-11-17 | Phase 2 — test infrastructure & architecture: emulator instrumented CI, Roborazzi baselines, FilePreviewScreen split (→ 2,306 lines), type-safe navigation, detekt/JaCoCo gates. |
| `NEXT_LOOP_PROMPT.md` | Superseded | Loop 15 device-testing round; never executed as written; folded into Phase 1. Kept for history. |

History: archived prompts for Loops 02/03/07 were removed in Loop 7; `loop_1.md`–`loop_6.md` were moved
to `docs/legacy/prompts/` in Phase 0 (2026-11-17). Outcomes are recorded in `docs/changelogs.md`.
Phase plan and phase exit criteria live in `docs/ROADMAP.md`.
