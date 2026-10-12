# 🦞 FILE CLAW — AUTONOMOUS SELF-IMPROVEMENT LOOP (v2)

## 🎯 MISSION
You are a **senior multi-agent engineering team** improving the Android app **File Claw** (Kotlin + Jetpack Compose) in an autonomous loop.
**Goal:** open **every kind of file, 100 % offline, in one app**, with a Claude-style premium UI (light + dark, restrained glass theme, spec in `docs/DESIGN.md`).
**Prime directive: never lose, corrupt, or silently alter the user's data, and never claim something works unless you ran it.**

## 🧪 STEP −1 — ENVIRONMENT PROBE (new in v2, do this before anything else)
Check what you can actually run: `java -version`, `ANDROID_HOME`, whether `./gradlew` can resolve dependencies (Google/Maven/Gradle distribution reachable?), whether GitHub is reachable.
- **Gradle + Android SDK available** → run the Quality Gate FIRST on the untouched patch, fix all compile/lint/test fallout, only then add features.
- **Not available** → keep new logic in the pure-Kotlin `com.example.core` package, verify it with `tools/local-verify/verify-core.sh` (kotlinc 1.9.22 + JaCoCo CLI from GitHub releases), and label everything else **"not verified"** in `docs/TODO.md` → KNOWN ISSUES. Never write "build passes" without a log that proves it.

## 🤖 TEAM (one dedicated agent per role; coordinate, respect module boundaries)
Architect · Reader/Analyzer · Planner · Coder ×3 · Designer · FileFormat · QA/Tester · Reviewer (security + performance) · DevOps · Docs · Release.

## 📚 STATE (read in this order; UPDATE docs, never recreate)
`docs/AGENTS.md` → `docs/TODO.md` (KNOWN ISSUES first) → `docs/CHANGELOG.md` → `docs/ROADMAP.md` → `docs/ARCHITECTURE.md`, `docs/DESIGN.md`, `docs/FILE_FORMATS.md` (generated: `tools/GenFormatDocs.kt`), `docs/SECURITY_AUDIT.md`, `docs/SANITY_CHECKLIST.md` → source. Then read the changed source line by line; assume nothing.

## 🔁 LOOP (industry workflow; max 5 retries per failure, then record in KNOWN ISSUES)
Planner picks next TODO item → Coder branches + implements → Tester writes tests (JUnit for `core/`, Compose/instrumented for UI) → Reviewer (security/perf/architecture) → Fix → Docs → Merge. Repeat until the cycle scope is done.

## 🎯 CYCLE 2 SCOPE (highest impact first; v1.1.0 left these)
**P0**
1. Run the Quality Gate and fix compile fallout in `FileManager`, `MainViewModel`, `MainActivity`, `FilePreviewScreen`, `UniversalViewers`, `CacheManager`; get `jacocoCoreVerify`, `lintDebug`, `detekt` (drop `ignoreFailures`) green.
2. Mask the archive-password field; rename/redo `autoSaveDrafts` (it overwrites the original).
3. Surface corrupt-PDF errors (currently an endless spinner).

**P1**
4. **Write-back to the source URI** for files opened from other apps (`sourceUri` column + Room `Migration(3,4)` with a migration test; `openOutputStream(uri, "wt")`; honest messaging on failure).
5. Paged viewer for huge text using `TextWindowReader` (open 500 MB with smooth scrolling).
6. Legacy `.xls` (BIFF8 reader), then `.doc` text extraction; keep RAR decision explicit.
7. Editor correctness: shared scroll for the line-number gutter, bounded undo (diffs, not full copies), make "smart indent" non-destructive.
8. Split `FilePreviewScreen.kt` (~2,980 lines) into per-viewer files; type-safe navigation; Compose UI tests for open → edit → save → reopen.
9. Root-level `GlassBackdrop` + a NavHost transition policy so opaque→glass screens don't overlap; glass sheets/dialogs/search; check contrast on the glass-over-glow.

**P2** device benchmarks (Macrobenchmark: cold start, scroll jank, 500 MB open) · GLB/PLY/DXF viewers · CJK charset detection · remove dead pdf.js branch · deduplicate `formatFileSize` · verify bundled-JS licenses · scope the JitPack repo.

## ✅ PHASE 3 — QUALITY GATE
`./gradlew build` · `./gradlew testDebugUnitTest jacocoCoreVerify` (≥ 80 % lines on `core/`; keep it ≥ 98 %) · `./gradlew lintDebug detekt` (0 critical) · `./gradlew assembleDebug` · walk `docs/SANITY_CHECKLIST.md`. Record the exact command output status in the CHANGELOG.

## 📦 PHASE 4 — DELIVERABLE
`fileclaw-patch-vX.Y.Z.zip` (full source) containing: `PATCH_NOTES.md`, updated `docs/` (CHANGELOG version bump), `ROADMAP.md` backlog, `BUILD_GUIDE.md` (single command), `NEXT_PROMPT.md` (the v1 prompt, unchanged), and `NEXT_LOOP_PROMPT_v3.md`.

## 📏 RULES
- New logic that doesn't need Android goes in `com.example.core` (no `android.*` imports) with tests. Every feature ships with tests. No placeholder code without a TODO ticket. Conventional commits.
- **Data safety:** all writes atomic (`AtomicFileWriter`); all reads of user files size-bounded; archives always through `ArchiveLimits`; truncated/converted/view-only content is a **flag** on state, never text inside the content; never write back anything the user didn't edit.
- **Honest numbers:** CHANGELOG metrics must be measured (state the tool, the machine class, and "JVM vs device"); if not measured, say "not measured". Report regressions too.
- **Behaviour changes** that users can see must be listed explicitly in the CHANGELOG and AGENTS.md "Decisions".
- 100 % offline: no `INTERNET` permission, no remote assets, WebViews locked down (no file access unless needed, scripts off for untrusted HTML, no JS bridge for untrusted content, sanitise injected HTML).
- Environment lessons: `/bin/sh` has no brace expansion; Kotlin block comments nest (never write `*/` or `/*` inside KDoc, e.g. `image/*`); `InputStream.readNBytes` needs API 33; mask bytes with `and 0xFF` before comparing; `AutoCloseable` streams owned by `AtomicFileWriter` must not be closed inside its block.
- Questions: use best judgment and log the decision in `docs/AGENTS.md`.

## 🧬 SELF-REPLICATING PROMPT RULE (MANDATORY — EVERY LOOP)
At the end of every loop, you (Claude) must **write the next loop's prompt yourself**:
1. **Brainstorm with your full agent team** — Architects, Planner, FileFormat-Agent, Designer, QA — review what was done, what failed, and what the next highest-impact improvements are.
2. **Read the current codebase** and the updated docs before writing.
3. Generate an **improved next-loop prompt** (bump version: v3, v4, v5...) that is smarter than this one — with refined priorities, newly discovered edge cases, updated format targets, performance goals, and lessons learned from this loop.
4. **Crucially: include this exact same self-replicating rule inside the next prompt too** — so that prompt can also generate the one after it. The chain must never break.
5. Save it as `NEXT_LOOP_PROMPT_vX.md` in the final zip, alongside (or instead of) `NEXT_PROMPT.md`, and also print it at the very end of your response so the user can copy it directly.

Each generation of the prompt should be **better than the previous** — this is prompt evolution through the loop, exactly like the app itself evolves.

---
**START NOW:** Step −1 → read state → Loop → Quality Gate → Deliverable → write the v3 prompt. The user's latest source is attached; if you have questions, use best judgment and document the decision in `docs/AGENTS.md`. 🚀
