# Implementation Log: Pre-compile core into jar (IW-344)

Issue: IW-344

This log tracks the evolution of implementation across phases.

---

## Phase 1: Core jar build and mtime check (2026-04-17)

**Layer:** Build infrastructure (bash launcher)

**What was built:**
- `iw-run` — added `CORE_JAR` variable with `IW_CORE_JAR` env override, `build_core_jar()`, `core_jar_stale()`, `ensure_core_jar()` functions; rewrote `bootstrap()` to call `build_core_jar`.

**Dependencies on other layers:**
- None — this phase stands alone. Phase 2 will wire `ensure_core_jar()` into `execute_command()`.

**Testing:**
- No automated tests added (deferred to Phase 3 per phase plan).
- Manual smoke tests all passed: clean bootstrap produces jar, missing/stale jar triggers rebuild, `IW_CORE_JAR` override respected, `./iw version` still works.

**Code review:**
- Iterations: 1
- Review file: `review-phase-01-20260417-142314.md`
- 0 critical issues, 3 warnings (all minor), 6 suggestions (mostly deferred to Phase 3).

**Notable decisions:**
- `ensure_core_jar()` is defined in Phase 1 but unused until Phase 2 — ships together for a coherent diff. Flagged in review as intentional scaffolding.
- `bootstrap()` calls `build_core_jar` directly (unconditional rebuild) rather than `ensure_core_jar` because bootstrap is the explicit pre-build entry point.
- `build/` is already in `.gitignore` — no change needed.

**For next phases:**
- Phase 2 should switch `execute_command()`'s three invocation sites from `$core_files` to `--jar "$CORE_JAR" "$CORE_DIR/project.scala"`, calling `ensure_core_jar` before each.
- Phase 3 should add BATS coverage for missing-jar, stale-jar, `IW_CORE_JAR` override, and `ensure_core_jar` (via its Phase 2 call site); also cover the missing `$CORE_DIR` edge case flagged in testing review.

**Files changed:**
```
M	iw-run
```

---

## Phase 2: Update command execution to use jar (2026-04-17)

**Layer:** Command execution (bash launcher)

**What was built:**
- `iw-run` — wired `ensure_core_jar()` into `execute_command()` (single call at top, before branching) so every command run auto-rebuilds the jar when core sources are newer.
- Rewrote all three `execute_command()` branches (project / plugin / shared) to pass `--jar "$CORE_JAR" "$CORE_DIR/project.scala"` to `scala-cli run` in place of the old `$core_files` find-and-collect.
- Removed the three redundant `core_files=$(find ... )` blocks from `execute_command()`.
- Fixed a latent Phase 1 defect: `build_core_jar()` now passes `-f` to `scala-cli package` so it can overwrite an existing jar. Phase 1 only exercised `build_core_jar` in bootstrap (always fresh); Phase 2 wired `ensure_core_jar` into every command, which exposed the overwrite failure on stale-jar rebuild.
- Removed a now-dead `# shellcheck disable=SC2086` directive on the project-command `exec` line (no word-split variables remain there after the rewrite).

**Dependencies on other layers:**
- Consumes Phase 1's `CORE_JAR`, `IW_CORE_JAR`, `build_core_jar()`, `core_jar_stale()`, `ensure_core_jar()`, and `$CORE_DIR`.

**Testing:**
- No automated tests added in this phase (BATS coverage deferred to Phase 3 per plan).
- Manual smoke tests all passed: fresh jar / stale jar / missing jar each produce expected rebuild behavior; `./iw status` (core-heavy shared command) and `./iw ./test compile` (project command, 35 commands compiled) both succeed; `IW_CORE_JAR` override builds at the alternate path; cold-start speedup realised.

**Code review:**
- Iterations: 1
- Review file: `review-phase-02-20260417-173311.md`
- 0 critical, 2 warnings, 6 suggestions. Verdict: approve.
- Warning 1 (ensure_core_jar runs before command-name validation): accepted per contract in phase-02-context.md.
- Warning 2 (dead shellcheck directive on project exec): fixed in-phase.

**Notable decisions:**
- `ensure_core_jar` placed at top of `execute_command()` (one call) rather than per-branch (three calls); decision documented in phase-02-context.md.
- `"$CORE_DIR/project.scala"` must be passed as a source file alongside `--jar` because `scala-cli` only honors `//> using dep` directives from source-set files, not from jar-packaged sources.
- Fixing `build_core_jar`'s missing `-f` was treated as an in-phase Phase 1 correction rather than a separate hotfix: it is a one-line change, Phase 2 makes the defect user-visible for the first time, and deferring it would leave the headline scenario (mtime-triggered rebuild) broken on shipped code.

**For next phases:**
- Phase 3: add BATS coverage for missing/stale jar paths, `IW_CORE_JAR` override, and the `build_core_jar -f` overwrite behavior (to prevent regression). Pre-build jar once in `test.scala` and export `IW_CORE_JAR` so E2E tests don't pay the rebuild cost per test.

**Files changed:**
```
M	iw-run
```

---
