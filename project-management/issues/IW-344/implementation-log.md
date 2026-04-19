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

## Phase 3: E2E test adaptation (2026-04-17)

**Layer:** Test infrastructure (BATS suite + `test.scala` runner + one `core/adapters` signature extension)

**What was built:**
- `core/adapters/Process.scala` — `runStreaming` gained a trailing `env: Map[String, String] = Map.empty` parameter, forwarded to `os.proc(...).call(env = env)`. Mirrors the existing `run` method; default-empty preserves every existing call site. Also refreshed the file PURPOSE header to cover all four public operations.
- `.iw/commands/test.scala` — `runE2ETests()` now pre-builds the core jar via `./iw-run --bootstrap` before the BATS loop (10-min timeout, aborts the suite if bootstrap fails), then injects `IW_CORE_JAR` into each BATS child's env via the new `runStreaming(env = ...)` parameter. Saves the ~30s-per-test jar rebuild cost and models the real launcher contract.
- Seven BATS setup functions (`plugin-commands-describe.bats`, `plugin-commands-execute.bats`, `plugin-commands-list.bats`, `plugin-discovery.bats`, `project-commands-describe.bats`, `project-commands-execute.bats`, `project-commands-list.bats`) — each gained the `IW_CORE_JAR` + `touch -r` block immediately after `export IW_CORE_DIR=...`. The block points at the shared pre-built jar when present and syncs copied-source mtimes so `core_jar_stale` stays false, preventing mid-suite clobbering of the shared jar.
- `test/core-jar.bats` (new, 6 scenarios) — missing-jar auto-rebuild, stale-jar auto-rebuild, fresh-jar silence, `IW_CORE_JAR` override honored, `./iw-run --bootstrap` produces the jar at the default location, `build_core_jar` overwrites cleanly (regression guard for the Phase-2 `-f` fix). Uses a per-test `IW_CORE_JAR` inside `$TEST_DIR` so scenarios that mutate the jar don't contaminate the shared one.

**Dependencies on other layers:**
- Consumes Phase 1's `CORE_JAR`, `IW_CORE_JAR`, `build_core_jar()`, `core_jar_stale()`, `ensure_core_jar()`.
- Consumes Phase 2's `ensure_core_jar` hook inside `execute_command()` and the `-f` overwrite fix in `build_core_jar()`.

**Testing:**
- `./iw ./test unit` — green.
- `./iw ./test compile` — all 35 commands compile.
- `bats test/core-jar.bats` — all 6 scenarios pass standalone.
- `./iw ./test e2e` — `core-jar.bats` green; sentinel `project-commands-execute.bats` all 10 pass; `plugin-commands-execute.bats` all 4 pass. 6 pre-existing, unrelated failures in `phase-merge.bats`, `start-prompt.bats`, and `dashboard.bats`.

**Code review:**
- Iterations: 1
- Review file: `review-phase-03-20260417-202044.md`
- 4 skills: style, testing, security, scala3.
- 0 critical from style/security/scala3; testing flagged 2 criticals (`/tmp/custom-iw-core.jar` shared path; `sleep 1` for mtime granularity).
- `/tmp` issue fixed in-phase: `core-jar.bats` now uses `$TEST_DIR/custom-iw-core.jar`, teardown simplified. Stale test name "Phase-2 -f regression guard" renamed. `Process.scala` PURPOSE comment updated.
- `sleep 1`: explicit pushback in review file — the idiom is correct for 1-second mtime granularity, tests pass reliably, the `touch -d "1 minute ago"` alternative is a stylistic refinement, not a correctness fix. Left as-is.
- Warnings left as-is with rationale: BATS setup-block extraction (8 occurrences now; not worth blocking Phase 3); `env` parameter validation guard (current callers all pass `os.Path`-derived values; add when/if external callers appear); `return false` in `runE2ETests` (readable, local, and mirrors existing short-circuit style in the same file).

**Notable decisions:**
- BATS setup block stays copy-pasted across seven files rather than extracting into `test/helpers/core-jar-setup.bash`. The project already tolerates duplicated setup patterns (`IW_SERVER_DISABLED`, `TEST_DIR` boilerplate), and extracting mid-phase would grow scope. Candidate for a follow-up refactor.
- `core-jar.bats` uses its own per-test `IW_CORE_JAR` (inside `$TEST_DIR`) deliberately — scenarios 2 and 6 mutate the jar and must not contaminate the shared repo-root jar inherited by the other seven BATS files.
- Kept `return false` in `runE2ETests` over restructuring into nested `if/else` — the function is short, the early-exit pattern keeps the happy path at the outer indentation level, and matches the `if testFiles.isEmpty then ... return` already in the same function.

**For next phases:**
- IW-344 feature complete after this phase; remaining follow-ups (BATS helper extraction, `env` key validation guard, cleanup of 6 pre-existing unrelated BATS failures) tracked as separate issues.

**Files changed:**
```
M	.iw/commands/test.scala
M	core/adapters/Process.scala
A	test/core-jar.bats
M	test/plugin-commands-describe.bats
M	test/plugin-commands-execute.bats
M	test/plugin-commands-list.bats
M	test/plugin-discovery.bats
M	test/project-commands-describe.bats
M	test/project-commands-execute.bats
M	test/project-commands-list.bats
```

---
