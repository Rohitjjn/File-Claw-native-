# File Claw project instructions

This repository contains an existing offline-first Android application. Stabilization and verified behavior take priority over feature work.

## Engineering posture

Follow the four principles adapted from the MIT-described `karpathy-guidelines` skill by multica-ai: think before coding and surface assumptions; choose the smallest adequate solution; make surgical changes; define success criteria and verify them. Source: <https://github.com/multica-ai/andrej-karpathy-skills> (README and `skills/karpathy-guidelines/SKILL.md`). Arena Agent Mode in this checkout has no verified native skill installer or skill activation interface. These are project instructions, not a claim that the upstream skill was installed. The upstream README and skill metadata say MIT; the repository listing had no root LICENSE file, so keep attribution and re-check the upstream license before copying its text verbatim.

## Required workflow

1. Read `docs/AGENTS.md`, `docs/PROJECT_STATUS.md`, `docs/ISSUE_REGISTER.md`, then the relevant format/workflow docs before source.
2. Inspect current branch/status and the exact relevant call paths before editing. Do not rely on old prompt files, changelog claims, or cached `build.log` as current test evidence.
3. Reproduce a reported bug or mark it as un-reproduced/static evidence. Identify the root cause; do not patch a symptom or infer a defect from a limitation.
4. Keep changes within the approved scope. Do not alter unrelated formats, data schemas, dependencies, release configuration, or UI design without explicit approval.
5. Add a regression test for each reproducible bug. Preserve all existing tests. Never weaken checks or turn failed checks into a green status.
6. Run relevant tests and checks after each change. Report exactly what ran, its output/result, what failed, what could not run, and what still needs device verification.
7. Update the authoritative issue register and relevant docs with verified changes. Do not claim compilation, APK generation, device behavior, or release readiness without evidence from that exact run/commit.
8. Use recoverable Git checkpoints according to the task approval and current branch rules; do not switch branches.

## Permanent policy

- Inspect before coding; read related code and understand complete execution paths.
- Reproduce before fixing; fix root causes with minimal, maintainable changes.
- Avoid speculative abstractions and dependencies.
- Preserve working behavior and write regression tests for reproducible defects.
- Run relevant tests, update docs, and keep scope approved.
- Never silently weaken/delete tests or claim success without evidence.
- Keep DOCX the first format rehabilitation target. During a DOCX task, leave PDF and other formats untouched unless a demonstrably shared dependency is responsible and the owner approves expanding scope.
- The owner explicitly authorized DOCX implementation in the current task. Follow its DOCX-only scope; leave other format viewers untouched, and do not migrate renderers, change dependencies, alter production signing or redesign UI without separate approval.

## Source of truth

`docs/PROJECT_STATUS.md` records the latest inspected checkout and baseline. `docs/ISSUE_REGISTER.md` is the audited issue inventory; legacy `known-issues.md`, TODO entries, prompt history, and changelogs can be stale and must be cross-checked. `docs/DEVELOPMENT_WORKFLOW.md` and `docs/TESTING_STRATEGY.md` describe the verification policy. The active Arena agent can read and follow this file in the current session; future automatic loading depends on the host agent's repository-instruction support and is not asserted here.
