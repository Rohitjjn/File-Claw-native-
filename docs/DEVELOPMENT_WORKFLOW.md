# Development workflow and continuity

## Purpose

Stabilize the existing File Claw application before expanding it. Work one approved file-format rehabilitation target at a time. The active target is DOCX; this audit authorizes discovery and documentation only, not source fixes.

## Agent/session handoff order

1. Read root `AGENTS.md` and `docs/AGENTS.md`.
2. Read `docs/PROJECT_STATUS.md`, `docs/ISSUE_REGISTER.md`, `docs/TODO.md`, `docs/CHANGELOG.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/FILE_FORMATS.md`, `docs/SECURITY_AUDIT.md`, `docs/SANITY_CHECKLIST.md`, `known-issues.md`, and the currently active prompt only when relevant.
3. Check Git branch/status, base and recent commits. Treat the current code as primary evidence; historical prompts, worklogs, `build.log`, and prior test counts are not proof of current behavior.
4. Check toolchain availability and run the untouched baseline before source changes. Record literal command, environment blocker/output and exact commit.
5. Read the relevant implementation, callers, tests, asset pipelines, lifecycle and save/error paths. Map inputs through detection, routing, rendering, recovery, persistence/export and sharing.
6. Write a reproduction and test plan. If not reproducible, report static evidence and uncertainty rather than label a verified runtime bug.
7. Make only approved changes; add regression coverage before/with the minimal fix. Never broaden format scope silently.
8. Run targeted tests, then repository checks. Review `git diff`, `git diff --check`, test changes and docs. Do not bypass failing gates.
9. Update `ISSUE_REGISTER.md`, relevant format/architecture docs and changelog. Report verified, failed, not run, and device-only checks separately.
10. Stop at the requested approval gate. The owner decides whether to implement, change scope, or defer.

## Required engineering rules

- Inspect before coding; understand complete execution paths.
- Reproduce before fixing; identify root cause rather than patch symptoms.
- Prefer minimal, surgical, maintainable changes; do not add unnecessary dependencies/abstractions.
- Write regression tests for reproducible defects.
- Preserve working behavior; never silently weaken or delete tests.
- Run relevant tests after each implementation and document measured evidence.
- Keep each change within approved scope; do not redesign, modernize or touch adjacent formats without approval.
- Do not alter Room schemas/migrations, release configuration, signing, or dependencies as incidental cleanup.
- Never claim APK/test/runtime success without output for the exact run and commit.
- Keep Git work recoverable and on the current Arena branch; no branch switching.

## File-format sequence and guardrails

1. **DOCX rehabilitation (currently authorized by the owner)** — continue from `docs/FILE_FORMAT_REHABILITATION.md`; validate reported runtime symptoms, add fixtures, then fix only the DOCX issues named in the current task. Stop and request separate approval for renderer migration, production signing changes or other-format work.
2. Verify the scoped DOCX patch with regression, integration, device and print/export checks as applicable; update issue status with evidence.
3. Present results and wait for approval before moving on.
4. Choose the next format only after DOCX is reviewed and the owner approves the next target.
5. PDF and all other format viewers remain out of DOCX scope unless a shared responsible cause is demonstrated and the owner explicitly approves.

## Current repository-specific limitations

- Baseline checkout lacks Java/Kotlin/Android SDK and has non-executable Gradle wrapper; see `PROJECT_STATUS.md` for exact attempts. Install/configure toolchain in a suitable environment before test claims.
- `docs/ISSUE_REGISTER.md` is the current audited register. Legacy `known-issues.md` and other long-lived docs contain stale claims; cross-check them.
- Only manual release workflow exists; do not treat it as routine pull-request CI.
- Do not claim virtual reviewers or multiple agents ran unless actual independent tool output exists.
