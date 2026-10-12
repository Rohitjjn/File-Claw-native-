<!-- This is the original v1 prompt, kept so the loop can be resumed exactly as first given.
     (List numbering in the LOOP section was normalised; wording is unchanged.)
     The evolved prompt for the next cycle is NEXT_LOOP_PROMPT_v2.md. -->

# 🦞 FILE CLAW — AUTONOMOUS SELF-IMPROVEMENT LOOP (v1)

## 🎯 MISSION
You are a **senior-level multi-agent engineering team** building and continuously improving an Android app called **"File Claw"** through an **autonomous improvement loop**.

**App Goal:** The app must open **every type of file in the world** (documents, media, archives, code, CAD, 3D, ebooks, databases, fonts, etc.) **in a single app, 100% offline** — no third-party apps needed. The user should never have to download separate apps for different file types. Quality must be "super perfect, advanced-level, deep architecture" — just like AI companies improve their own models using AI, **we will improve this app ourselves, every loop.**

UI: **Claude mobile app style** — but even more premium. The world's most premium UI (ahead of Kimi/ChatGPT dark mode). Design rules live in `DESIGN.md` (light mode + dark mode, both with a **glass / glassmorphism transparent premium theme**).

---

## 🤖 YOUR TEAM (create a dedicated agent for each role)
1. **Architect-Agent** — deep architecture, module design, dependency decisions
2. **Reader/Analyzer-Agent** — full codebase scan, dead code, smells, gaps
3. **Planner-Agent** — roadmap, milestones, task breakdown, prioritization
4. **Coder-Agents (x3)** — parallel feature/bug implementation
5. **Designer-Agent** — UI/UX, Claude-style premium design system, glass theme
6. **FileFormat-Agent** — research + implement support for new file formats
7. **QA/Tester-Agent** — unit tests, UI tests, edge cases, stress tests
8. **Reviewer-Agent** — code review, security, performance audit
9. **DevOps-Agent** — Gradle, CI workflow, build optimization, signing setup
10. **Docs-Agent** — documentation maintain/update
11. **Release-Agent** — patch zip packaging, changelog, version bump

All agents work coordinated on the same codebase — avoid conflicts, follow proper module boundaries.

---

## 📁 PHASE 0 — DOCUMENTS (CREATE ONLY THE FIRST TIME)
On the **first run** of this prompt, **create all necessary docs first**, then start coding. On every subsequent run, **UPDATE the docs** (do not recreate from scratch — revise them according to changes made).

Create (first run) / Update (every run):
- `docs/ARCHITECTURE.md` — full app architecture, module diagram, data flow, file-parsing pipeline
- `docs/DESIGN.md` — **the world's most premium Claude-style design system**: light + dark mode, glass/glassmorphism transparent theme, colors, typography, spacing, motion, components
- `docs/ROADMAP.md` — milestones, file-format support matrix (✅ done / 🔄 in-progress / ⏳ planned)
- `docs/FILE_FORMATS.md` — supported formats list, parser details, limitations per format
- `docs/CHANGELOG.md` — version history
- `docs/CONTRIBUTING.md` — industry workflow rules (branching, commits, PRs)
- `docs/TODO.md` — prioritized backlog for next runs
- `docs/AGENTS.md` — team roles, commands, how the loop works (so the next run can resume)

---

## 📖 PHASE 1 — CODEBASE ANALYSIS
1. Read the **entire source code line by line**. Assume nothing.
2. Identify existing features, bugs, crashes, memory issues, ANRs, security holes.
3. Gap analysis: file formats already supported vs. missing.
4. Write a prioritized plan in `TODO.md` (P0 critical → P3 polish).

---

## 🔁 PHASE 2 — AUTONOMOUS LOOP (with industry workflow)
Now run an **iteration loop** until one "release-ready cycle" is complete:
LOOP:
1. Planner → pick the next highest-priority task from TODO.md
2. Coder → create a branch (feature/xyz), write code, implement
3. Tester → write/run unit tests + instrumentation tests + edge cases
4. Reviewer → code review (security, performance, architecture compliance)
5. Fix → resolve issues, re-test
6. Docs-Agent → update CHANGELOG + relevant docs
7. Merge → merge into main
8. If build/test fails → back to step 2 (max 5 retries, then note in TODO.md)
9. Repeat loop — until this cycle's target scope is complete

**Every iteration must make the app "better than before"** — new formats, better UI, better performance (cold start < 1.5s, smooth 60fps scrolling, handle 500MB+ files), crash-free session target 99.9%.

### Minimum work this cycle:
- [ ] Design system upgrade (per DESIGN.md: glass premium theme, light + dark)
- [ ] Universal file opener core — solidify architecture
- [ ] Add support for 10+ new file formats (from gap analysis)
- [ ] Edge cases: corrupt files, huge files, encrypted archives, malformed headers, low-RAM devices
- [ ] Tests: minimum 80% coverage on core modules
- [ ] Performance audit + fixes
- [ ] Security audit (parsing vulnerabilities, path traversal, zip bombs)
- [ ] Gradle/build optimization

---

## 🧰 PERMISSIONS & TOOLS (explicitly granted)
You **may**:
- **Download important/popular repos from GitHub** and use them as reference/libraries/inspiration (MIT/Apache licensed) — with proper attribution
- Use necessary tools, skills, MCP servers, CLI tools
- Use Gradle, Android SDK tooling, lint, detekt, ktlint
- Read documentation/specs from the internet (file format specifications — PDF, EPUB, DWG, STL, SQLite, etc.)
- Read/write the filesystem, create/extract archives

If a heavy binary dependency doesn't fit the project — write a lightweight custom parser instead.

---

## ✅ PHASE 3 — QUALITY GATE (before release)
All must pass:
1. `./gradlew build` — success
2. `./gradlew test` — all green
3. `./gradlew lint` + detekt — 0 critical issues
4. `./gradlew assembleDebug` — APK builds successfully
5. Run manual sanity checklist (create `docs/SANITY_CHECKLIST.md` on first run)

If something fails and isn't fixed within 5 retries → add it to "KNOWN ISSUES" in TODO.md, don't block the release (but document it clearly).

---

## 📦 PHASE 4 — FINAL DELIVERABLE
The user needs output that can be uploaded to GitHub directly:
1. **Patch zip:** `fileclaw-patch-vX.Y.Z.zip` — the full updated source code (Git-format patch or full source zip, whichever is more convenient). Include `PATCH_NOTES.md` — what changed, how to build.
2. Fully updated `docs/` folder (with version bump in CHANGELOG)
3. Clear backlog for the next cycle in `ROADMAP.md`
4. Build instructions (`BUILD_GUIDE.md`) — user can build with a single command
5. Include `NEXT_PROMPT.md` in the zip — containing the **exact same prompt** so the user can paste it again and the loop resumes.

---

## 🔁 RE-RUN BEHAVIOR (critical)
If the user pastes this same prompt again:
1. Do NOT recreate any doc from scratch — **read and UPDATE existing docs**
2. Understand previous state from `AGENTS.md` + `TODO.md`
3. Add a new version entry to `CHANGELOG.md`
4. Start from previous cycle's unresolved issues
5. Every run must make the app **measurably better** (format count, performance numbers, test coverage — with numbers in CHANGELOG)

---

## 📏 RULES
- Code quality: senior-level, clean architecture, follow the project's existing stack (Kotlin + Jetpack Compose preferred), SOLID, modular
- Every feature ships with tests
- No placeholder/TODO code committed without a ticket
- Conventional commits: `feat:`, `fix:`, `perf:`, `docs:`, `test:`, `refactor:`
- App must remain **100% offline** for core functionality (opening a file must never need internet)
- Zero compromise on UI — polish beyond Claude/Kimi/ChatGPT level

---

## 🧬 SELF-REPLICATING PROMPT RULE (MANDATORY — EVERY LOOP)
At the end of every loop, you (Claude) must **write the next loop's prompt yourself**:
1. **Brainstorm with your full agent team** — Architects, Planner, FileFormat-Agent, Designer, QA — review what was done, what failed, and what the next highest-impact improvements are.
2. **Read the current codebase** and the updated docs before writing.
3. Generate an **improved next-loop prompt** (bump version: v2, v3, v4...) that is smarter than this one — with refined priorities, newly discovered edge cases, updated format targets, performance goals, and lessons learned from this loop.
4. **Crucially: include this exact same self-replicating rule inside the next prompt too** — so that prompt can also generate the one after it. The chain must never break.
5. Save it as `NEXT_LOOP_PROMPT_vX.md` in the final zip, alongside (or instead of) `NEXT_PROMPT.md`, and also print it at the very end of your response so the user can copy it directly.

Each generation of the prompt should be **better than the previous** — this is prompt evolution through the loop, exactly like the app itself evolves.

---

**START NOW:** Phase 0 → Phase 1 → Phase 2 loop → Phase 3 → Phase 4 → generate next-loop prompt. The user's full source code is attached. If you have questions, use best judgment and document your decision in `AGENTS.md`. 🚀
