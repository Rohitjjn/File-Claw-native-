# AGENTS.md: how the improvement loop works

This file lets the next run resume with full context. It is the operating manual for the multi-agent loop defined in `NEXT_PROMPT.md`.

## Roles (one agent per role; same repo, strict module boundaries)
Architect · Reader/Analyzer · Planner · Coder ×3 · Designer · FileFormat · QA/Tester · Reviewer · DevOps · Docs · Release.

## State to read on every run (in this order)
`docs/AGENTS.md` → `docs/TODO.md` (KNOWN ISSUES first) → `docs/CHANGELOG.md` → `docs/ROADMAP.md` → source.
Never recreate docs; update them. Add a CHANGELOG entry with **measured** numbers.

## Commands
```
./gradlew testDebugUnitTest jacocoCoreVerify   # tests + 80% core coverage gate
./gradlew lintDebug detekt                      # static analysis
./gradlew assembleDebug                         # APK
kotlinc + tools/bench/CoreBench.kt              # JVM benchmarks (old vs new)
kotlinc + tools/GenFormatDocs.kt                # regenerate the format matrix
```

## Environment lessons from cycle 1 (important for the next run)
- The authoring sandbox had **no Android SDK and no Maven/Google/Gradle-distribution access**, only GitHub. Gradle could not run there. If the
  next run has the same limits, repeat the strategy: keep new logic in `core/` (pure Kotlin), compile with a GitHub-sourced `kotlinc`, run JUnit tests,
  measure with the JaCoCo CLI, and clearly label everything else *not verified*. If the next run **can** run Gradle, do the Quality Gate first and
  fix compile fallout before adding features.
- `/bin/sh` does not brace-expand: use `bash -c` or separate `mkdir` args.
- Kotlin supports nested block comments: never write `*/` or `/*` inside a KDoc (e.g. `image/*`).
- `InputStream.readNBytes` is not available below API 33; `java.nio.file` is fine on API 26+.
- Byte signature checks must mask with `and 0xFF` before comparing (signed-byte bug found by tests).

## Decisions taken without asking (cycle 1)
1. Excel became **view-only** (editing flattened workbooks); CSV/TSV keep editing with explicit save.
2. HTML preview scripts default **off** with a per-file opt-in.
3. RAR stays unsupported (previous owner dropped junrar), but reported honestly.
4. Files opened from other apps are edited as an in-app copy (with a visible message) until write-back is built.
5. Glass design shipped as tokens + cards + app bar + permission screen; root-level backdrop deferred because opaque Scaffolds + NavHost crossfades would overlap.
6. Gradle wrapper moved 8.5 → 8.7 (AGP 8.4.2 needs ≥ 8.6).
7. Preview cap kept at 250k chars (unmeasured perf), made safe via read-only flag rather than raising it.

## Release checklist
Version bump (`app/build.gradle.kts`) · CHANGELOG · TODO/ROADMAP · regenerate FILE_FORMATS · SANITY_CHECKLIST run · zip + `PATCH_NOTES.md` · next-loop prompt.
