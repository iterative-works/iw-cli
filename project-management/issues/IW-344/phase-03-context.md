# Phase 3: E2E test adaptation

**Issue:** IW-344
**Phase:** 3 of 3
**Estimate:** 3-5h
**Status:** Pending

## Goals

- Pre-build the core jar once at the start of the project's test runner (`.iw/commands/test.scala`) and expose its path via `IW_CORE_JAR` so every BATS file inherits a usable jar without paying per-test rebuild cost.
- Update the seven BATS setup functions that currently `cp -r "$BATS_TEST_DIRNAME/../core" .iw-install/` so tests run against jar mode cleanly (the copied sources stay, but each temp install points `IW_CORE_JAR` at the shared pre-built jar to avoid 30s-per-test rebuilds).
- Add new BATS coverage for the jar lifecycle wired up in Phases 1 and 2: missing jar triggers auto-rebuild, stale jar triggers auto-rebuild, `IW_CORE_JAR` override is honored, `./iw --bootstrap` produces the jar.
- Verify the full BATS suite (unit + E2E) still passes end-to-end against jar-based execution. `test/project-commands-execute.bats` is the key regression indicator since it exercises a project command that imports `iw.core.adapters.*` and `iw.core.model.*`.

This phase closes IW-344: after it lands, the jar-based execution path is exercised by automated tests, including the mtime-triggered auto-rebuild behaviour introduced in Phase 2.

## Scope

### In scope

- `/home/mph/Devel/iw/iw-cli-IW-344/.iw/commands/test.scala` — pre-build the jar before `runE2ETests()` and export `IW_CORE_JAR` for the child `bats` processes. The unit-test and compile-check paths are not touched (they invoke `scala-cli` directly).
- `/home/mph/Devel/iw/iw-cli-IW-344/core/adapters/Process.scala` — extend `runStreaming` with an optional `env: Map[String, String]` parameter so `test.scala` can set `IW_CORE_JAR` per BATS child. Minimal, single-method extension; mirrors the existing `run` method.
- Seven BATS setup functions at `/home/mph/Devel/iw/iw-cli-IW-344/test/`:
  - `plugin-commands-describe.bats` (setup lines ~7-33)
  - `plugin-commands-execute.bats` (setup lines ~7-37)
  - `plugin-commands-list.bats` (setup lines ~7-33)
  - `plugin-discovery.bats` (setup lines ~7-33)
  - `project-commands-describe.bats` (setup lines ~5-31)
  - `project-commands-execute.bats` (setup lines ~5-48)
  - `project-commands-list.bats` (setup lines ~5-31)
- One new BATS file at `/home/mph/Devel/iw/iw-cli-IW-344/test/core-jar.bats` for the jar lifecycle scenarios (keeps bootstrap.bats focused on release-package concerns).
- Possibly `/home/mph/Devel/iw/iw-cli-IW-344/test/bootstrap.bats` — add one assertion that `build/iw-core.jar` exists after `./iw-run --bootstrap` (the existing test only checks the "Bootstrap complete" message).

### Out of scope (not touched in this phase)

- `iw-run`: Phase 1 and Phase 2 already delivered every launcher change needed. No more bash edits.
- `commands/*.scala`: no source changes.
- `core/**/*.scala`: no changes except the one-line `runStreaming` signature extension in `core/adapters/Process.scala` described above.
- `scripts/package-release.sh`: out of scope per analysis.md; tracked separately.
- `test.scala` unit-test path (`runUnitTests`) and compile-check path (`runCommandCompileCheck`) — these call `scala-cli test` / `scala-cli compile` directly against `core/` and don't go through `iw-run`'s jar path. Leave them alone.
- BATS files whose setup does not copy `core/` (e.g. `init.bats`, `worktrees.bats`, `status.bats` etc.) — they either don't exercise `execute_command` or they stub things out at a higher level.

## Dependencies

Phase 1 and Phase 2 (both merged) deliver the pieces Phase 3 consumes. Verified against `/home/mph/Devel/iw/iw-cli-IW-344/iw-run` at head of branch:

- `CORE_JAR` variable (iw-run line 14) with `IW_CORE_JAR` env-var override, default `$INSTALL_DIR/build/iw-core.jar`. This is the knob the test harness turns.
- `export IW_CORE_JAR="$CORE_JAR"` (iw-run line 15) — any child process inherits the resolved path.
- `build_core_jar()` (iw-run lines 29-47) — unconditional build, honors `-f` overwrite, writes to `$CORE_JAR`.
- `core_jar_stale()` (iw-run lines 51-62) — `find -newer` mtime check.
- `ensure_core_jar()` (iw-run lines 65-69) — called from top of `execute_command` (Phase 2), so every command invocation auto-rebuilds when stale.
- Jar-based invocation in `execute_command()` (Phase 2) — all three branches now pass `--jar "$CORE_JAR" "$CORE_DIR/project.scala"` instead of `$core_files`. This means tests that previously relied on `cp -r core/` are still valid (the copy gives `ensure_core_jar` a tree to rebuild from) but the jar is what actually ends up on scala-cli's classpath.

External:
- `bats` available on `PATH` (already required by `test.scala`).
- `scala-cli` available on `PATH` (already required by `iw-run`).

## Approach

### 1. `test.scala`: pre-build jar before E2E tests

Current `runE2ETests()` (lines 125-145 of `/home/mph/Devel/iw/iw-cli-IW-344/.iw/commands/test.scala`) runs each `.bats` file individually via `ProcessAdapter.runStreaming` with a 10-minute per-file timeout.

Change: before the `testFiles.sortBy(_.last)` loop, invoke the installation's `./iw-run --bootstrap` to produce a fresh `$IW_INSTALL_DIR/build/iw-core.jar`, then pass `IW_CORE_JAR` through to each BATS invocation. Using `--bootstrap` means tests exercise the real launcher build pipeline rather than a parallel one.

Under `./iw ./test e2e`, `IW_INSTALL_DIR` equals the repo root (iw-run exports it to point at the dev checkout), so the shared jar lands at `<repo>/build/iw-core.jar` — the same path the BATS setup functions will reference via `$BATS_TEST_DIRNAME/../build/iw-core.jar`.

#### 1a. Extend `ProcessAdapter.runStreaming` with env support

Current signature at `/home/mph/Devel/iw/iw-cli-IW-344/core/adapters/Process.scala:82-86`:

```scala
def runStreaming(
    command: Seq[String],
    timeoutMs: Int = DefaultTimeoutMs,
    closeStdin: Boolean = false
): Int
```

`runStreaming` does not accept env today (only `run` does, line 39-43). Phase 3 extends it: add `env: Map[String, String] = Map.empty` as a trailing default parameter and forward it to `os.proc(command).call(..., env = env)`. This is a single-line addition, mirrors the existing `run` method, default-empty preserves every existing call site, and the three current `runStreaming` callers stay source-compatible. This extension is part of Phase 3 scope.

#### 1b. `runE2ETests` rewrite

```scala
def runE2ETests(): Boolean =
  val installDir = os.Path(System.getenv("IW_INSTALL_DIR"))
  val testDir = installDir / "test"
  // ... existing empty-dir short-circuit ...

  // Pre-build the core jar once so BATS tests inherit it via IW_CORE_JAR,
  // avoiding per-test 30s jar rebuilds.
  Output.section("Pre-building core jar for E2E tests")
  val bootstrapExit = ProcessAdapter.runStreaming(
    Seq((installDir / "iw-run").toString, "--bootstrap"),
    timeoutMs = 10 * 60 * 1000
  )
  if bootstrapExit != 0 then
    Output.error("Failed to pre-build core jar; aborting E2E run")
    return false

  val coreJar = installDir / "build" / "iw-core.jar"

  Output.section("Running E2E Tests")
  val sortedFiles = testFiles.sortBy(_.last)
  val results = sortedFiles.map { testFile =>
    ProcessAdapter.runStreaming(
      Seq("bats", testFile.toString),
      timeoutMs = 10 * 60 * 1000,
      env = Map("IW_CORE_JAR" -> coreJar.toString)
    ) == 0
  }
  results.forall(identity)
```

### 2. BATS setup changes

The seven BATS files share a near-identical setup pattern:

```bash
cp "$BATS_TEST_DIRNAME/../iw-run" "$TEST_DIR/iw-run"
chmod +x "$TEST_DIR/iw-run"
mkdir -p .iw-install/commands
cp -r "$BATS_TEST_DIRNAME/../commands"/*.scala .iw-install/commands/
cp -r "$BATS_TEST_DIRNAME/../core" .iw-install/
rm -rf .iw-install/core/test
export IW_COMMANDS_DIR="$TEST_DIR/.iw-install/commands"
export IW_CORE_DIR="$TEST_DIR/.iw-install/core"
```

**Decision (committed): each of the seven setup functions gets an explicit `IW_CORE_JAR` export pointing at the shared repo-root jar, plus an mtime sync so `core_jar_stale` returns false.**

Two problems must be solved together:
1. The `cp -r core/` step updates mtimes on the copied `.scala` files, so they end up newer than the pre-built jar and `core_jar_stale` triggers a rebuild that writes into the shared `$IW_CORE_JAR` — clobbering the jar mid-suite.
2. Tests need a deterministic `IW_CORE_JAR` value regardless of whether they inherit env from `test.scala` or run standalone.

The fix: set `IW_CORE_JAR` to the repo-root pre-built jar, then `touch -r` the copied sources to match the jar's mtime so the stale-check stays false.

Add this block immediately after `export IW_CORE_DIR=...` in each of the seven setup functions:

```bash
# Point at the shared, pre-built core jar from the repo root and sync the
# copied sources' mtimes to it so core_jar_stale stays false (otherwise the
# copy's fresh mtimes would trigger a rebuild into the shared jar path,
# clobbering it for other tests in the suite).
if [ -f "$BATS_TEST_DIRNAME/../build/iw-core.jar" ]; then
    export IW_CORE_JAR="$BATS_TEST_DIRNAME/../build/iw-core.jar"
    find "$TEST_DIR/.iw-install/core" -name '*.scala' \
        -exec touch -r "$IW_CORE_JAR" {} +
fi
```

When a developer runs a single `.bats` file standalone without a prior `./iw ./test e2e` having produced the jar, the `if` fails, `IW_CORE_JAR` is unset, and `iw-run` falls back to the default path (`$INSTALL_DIR/build/iw-core.jar`) and rebuilds from the copied sources on first command execution — slow (~30s) but correct.

### 3. New BATS tests: `/home/mph/Devel/iw/iw-cli-IW-344/test/core-jar.bats`

Fresh file covering the jar lifecycle. Pattern: matches `bootstrap.bats` style (isolated `TEST_DIR`, full copy of iw-run + commands + core), scoped to jar-specific scenarios.

Scenarios to cover:

1. **Missing jar triggers auto-rebuild on command execution.** Remove `$IW_CORE_JAR` path, run `./iw-run version`, assert exit 0, stderr contains `"Rebuilding core jar..."`, jar now exists at expected path.
2. **Stale jar triggers auto-rebuild.** Build jar, then `touch` a `core/**/*.scala` file with a newer mtime, run `./iw-run version`, assert stderr contains `"Rebuilding core jar..."`, jar mtime is newer than before.
3. **Fresh jar is silent.** Run `./iw-run version` twice; second invocation must not emit `"Rebuilding core jar..."` on stderr.
4. **`IW_CORE_JAR` override honored.** Set `IW_CORE_JAR=/tmp/custom-iw-core.jar`, run `./iw-run --bootstrap`, assert `/tmp/custom-iw-core.jar` exists and `$INSTALL_DIR/build/iw-core.jar` does not.
5. **`./iw-run --bootstrap` produces the jar at the default location.** (Duplicates bootstrap.bats somewhat, but bootstrap.bats skips when the release package is absent; this one runs in dev layout.)
6. **`build_core_jar` overwrite (the `-f` fix from Phase 2) — regression guard.** Build the jar (`./iw-run --bootstrap`), capture its mtime into a variable (e.g. `old_mtime=$(stat -c %Y "$jar")`), `sleep 1` to guarantee distinct mtime resolution, then `touch` a `core/**/*.scala` source so it is newer than the jar. Run any command (e.g. `./iw-run version`). Assert: exit 0; the jar still exists at the same path; `stat -c %Y "$jar"` returns a value strictly greater than `old_mtime`; stderr contained `"Rebuilding core jar..."`; no "file exists" / "already exists" error appeared in `$output`. This directly catches the Phase-2 defect where `scala-cli package` refused to overwrite without `-f`.

Each test MUST export `IW_SERVER_DISABLED=1` in setup per memory.

### 4. Optional: one assertion in `bootstrap.bats`

After the existing `"iw-run --bootstrap pre-compiles successfully"` test, add `[ -f "$TEST_DIR/iw-cli-0.1.0-dev/build/iw-core.jar" ]`. This is a small drive-by improvement; if the release package flow doesn't include `build/`, skip this — not worth scope creep.

### 5. Ordering

1. Extend `ProcessAdapter.runStreaming` with the `env` parameter (`core/adapters/Process.scala`).
2. Edit `.iw/commands/test.scala` (pre-build + env plumbing) using the new signature.
3. Add the `IW_CORE_JAR` + `touch -r` block to each of the seven BATS setup functions.
4. Write `test/core-jar.bats` and iterate on each scenario.
5. Run `./iw ./test e2e` end-to-end; fix any regressions.
6. Run `./iw ./test` (full suite) to catch unit or compile regressions.

## Files to Modify

- `/home/mph/Devel/iw/iw-cli-IW-344/.iw/commands/test.scala` — add jar pre-build before `runE2ETests()` loop (around lines 125-145) and pass `IW_CORE_JAR` env var to each `bats` invocation (the `ProcessAdapter.runStreaming` call spans lines 138-145 — specifically the body between `Output.section("Running E2E Tests")` and the closing `results.forall(identity)`). Under `./iw ./test e2e`, `IW_INSTALL_DIR` equals the repo root (set by `iw-run` for the dev layout), so the pre-built jar is written to `<repo>/build/iw-core.jar`.
- `/home/mph/Devel/iw/iw-cli-IW-344/core/adapters/Process.scala` — extend `runStreaming` (line 82) with a trailing `env: Map[String, String] = Map.empty` parameter and forward it to `os.proc(...).call(env = env)`.
- `/home/mph/Devel/iw/iw-cli-IW-344/test/plugin-commands-describe.bats` — setup (~line 32 after the `IW_CORE_DIR` export).
- `/home/mph/Devel/iw/iw-cli-IW-344/test/plugin-commands-execute.bats` — setup (~line 36).
- `/home/mph/Devel/iw/iw-cli-IW-344/test/plugin-commands-list.bats` — setup (~line 32).
- `/home/mph/Devel/iw/iw-cli-IW-344/test/plugin-discovery.bats` — setup (~line 32).
- `/home/mph/Devel/iw/iw-cli-IW-344/test/project-commands-describe.bats` — setup (~line 30).
- `/home/mph/Devel/iw/iw-cli-IW-344/test/project-commands-execute.bats` — setup (~line 34).
- `/home/mph/Devel/iw/iw-cli-IW-344/test/project-commands-list.bats` — setup (~line 30).

### Files to create

- `/home/mph/Devel/iw/iw-cli-IW-344/test/core-jar.bats` — new, ~6 tests covering jar lifecycle.

### Files deliberately not touched

- `iw-run` — Phases 1/2 already did the work.
- `test/version-check.bats`, `test/plugin-hooks.bats` — these setup an empty `.iw-install/core` without copying real sources; they don't exercise core-type imports at runtime, so jar behaviour is a no-op for them. Leave alone unless a run reveals a problem.
- `test/bootstrap.bats` — already exercises `./iw-run --bootstrap`; the existing assertions are sufficient for a release-package smoke test.

## Component Specifications / API Contracts

### `test.scala` E2E entry contract

- Pre-condition: `IW_INSTALL_DIR` is set (already true, via `iw-run`).
- Side effect (new): writes `$IW_INSTALL_DIR/build/iw-core.jar` before launching any `bats` process.
- Post-condition: each `bats` invocation runs with `IW_CORE_JAR=$IW_INSTALL_DIR/build/iw-core.jar` in its environment, alongside existing env vars.
- Failure mode: if `./iw-run --bootstrap` exits non-zero, `runE2ETests` returns `false` immediately without running any BATS files. The message "Failed to pre-build core jar; aborting E2E run" appears on stderr.

### BATS setup contract (post-change)

Each of the seven modified setup functions:
- Keeps `cp -r .../core` (no change — needed for `ensure_core_jar` to have a tree to rebuild from).
- Adds conditional `export IW_CORE_JAR=...` pointing at the repo-root pre-built jar when present.
- Behaviour when `IW_CORE_JAR` is already set (inherited from `test.scala`): the explicit `export` in setup is idempotent (same path), so no conflict.
- Behaviour when run standalone without `test.scala`: the fallback path rebuilds from copied sources on first command execution, which is slow (~30s) but correct.

### New: `test/core-jar.bats` tests

- Standard setup pattern: `IW_SERVER_DISABLED=1`, `TEST_DIR=$(mktemp -d)`, `cp iw-run + commands + core`, `export IW_COMMANDS_DIR` / `IW_CORE_DIR` / `IW_CORE_JAR=$TEST_DIR/.iw-install/build/iw-core.jar`.
- Each test is independent: creates/deletes the jar as needed and inspects stderr for the "Rebuilding core jar..." marker.
- `run ./iw-run <cmd> 2>&1` captures both stdout and stderr so assertions can inspect `$output` for the rebuild message.

## Testing Strategy

### How this phase verifies itself

1. `./iw ./test unit` — verifies the test-runner changes compile (test.scala change is a Scala-3 file, so the compile-check path will catch syntax errors).
2. `./iw ./test compile` — verifies all `commands/*.scala` still compile. Unchanged in this phase, so should pass untouched.
3. `./iw ./test e2e` — runs all 38 BATS files individually. Must pass green. Key indicators:
   - `test/project-commands-execute.bats` — the canonical regression sentinel. If jar-mode breaks core-type classpath visibility, this breaks first.
   - `test/plugin-commands-execute.bats` — similar indicator for plugin paths with hooks and lib files.
   - `test/core-jar.bats` (new) — all 6 scenarios must pass.
4. Spot-check a single BATS file standalone: `bats test/project-commands-execute.bats` without setting `IW_CORE_JAR`. Expect slower first run (core rebuild) but all tests pass.

### What to watch for

- **Stale jar when a BATS test modifies `$IW_CORE_DIR` contents mid-test.** If a test *intentionally* writes a new `.scala` file under `.iw-install/core/` (as `core-jar.bats` scenarios 2 and 6 do), `ensure_core_jar` will detect the mtime mismatch and rebuild into the shared `$IW_CORE_JAR` path, clobbering the pre-built jar for subsequent tests in the suite. Mitigation: `core-jar.bats` setup MUST override `IW_CORE_JAR` to a per-test path (`$TEST_DIR/.iw-install/build/iw-core.jar`) rather than inheriting the shared one — these tests deliberately exercise the rebuild path and must not contaminate the shared jar. The other seven BATS files do not mutate `.iw-install/core/*.scala`, so the mtime-sync via `touch -r` (see section 2) keeps them from triggering rebuilds.
- **`project-commands-execute.bats` test 3** (`project command can import core library (Config)`) imports `iw.core.adapters.ConfigFileRepository` and `iw.core.model.ProjectConfiguration`. Per memory, it uses full sub-package paths. Under jar mode this import path must still resolve, which it does as long as the jar contains the full compiled core tree (it does, per Phase-1 spike).
- Pristine test output. If `"Rebuilding core jar..."` leaks into assertion diffs, tests will fail with confusing mismatches. Each new `core-jar.bats` test that checks for this message must use `stderr`-aware capture (`run bash -c '... 2>&1'`).

### Pre-commit / pre-push gates

- Pre-commit: format check + `scala-cli compile --scalac-option -Werror core/`. Unaffected by this phase.
- Pre-push: compile core + unit tests + command compilation. Unaffected.
- CI: runs the full E2E suite; this is where Phase 3 really proves itself.

## Acceptance Criteria

- [ ] `.iw/commands/test.scala` calls `./iw-run --bootstrap` (or equivalent) once before the BATS loop, aborts if bootstrap fails.
- [ ] `.iw/commands/test.scala` passes `IW_CORE_JAR` to each `bats` invocation in `runE2ETests`.
- [ ] All seven BATS setup functions that copy `core/` now also set `IW_CORE_JAR` to a pre-built jar path (either inherited from parent env or constructed per-test).
- [ ] New `/home/mph/Devel/iw/iw-cli-IW-344/test/core-jar.bats` exists with at least the six scenarios listed above.
- [ ] All six new BATS scenarios pass individually (`bats test/core-jar.bats`).
- [ ] `./iw ./test e2e` runs green end-to-end (all 39 BATS files including the new one).
- [ ] `test/project-commands-execute.bats` passes unchanged — the regression sentinel for core-type import resolution under jar mode.
- [ ] `ProcessAdapter.runStreaming` accepts an `env: Map[String, String] = Map.empty` parameter and forwards it to `os.proc(...).call(env = env)`.
- [ ] No `iw-run` edits in this phase. No `commands/` source edits. The only `core/` edit is the `runStreaming` signature extension.
- [ ] `./iw ./test` full suite (unit + compile + e2e) passes.
- [ ] No new compile warnings (`-Werror` gate remains green).

## Risks & Notes

### Risk: shared jar path across concurrent test runs

If CI runs multiple test shards in parallel sharing the same repo checkout, a shared `build/iw-core.jar` could be clobbered mid-flight. Mitigation: use `$TEST_DIR/.iw-install/build/iw-core.jar` per-test and pay the small cost of multiple builds, OR document that parallel E2E runs require separate worktrees. Given iw-cli's current CI setup (single self-hosted runner per memory), shared is fine; flag this for future-proofing only.

### Risk: `./iw-run --bootstrap` fails on a fresh clone if `scala-cli` isn't warmed up

Bootstrap does a full `scala-cli package` which downloads dependencies on cold. On a fresh CI runner this might exceed the 10-minute per-bats timeout (bootstrap itself has no explicit timeout in `ProcessAdapter.runStreaming`). Per memory the default is 5 minutes. Since `test.scala` uses `runStreaming` without a custom timeout for bootstrap, this could silently fail on cold runners. Mitigation: pass a 10-minute timeout explicitly to the bootstrap call, matching the per-bats timeout already in the file.

### Note: `plugin-hooks.bats` and `version-check.bats` don't copy `core/`

They create empty `.iw-install/core` directories. They don't exercise core-type imports at runtime (they either test hook discovery semantics or version-file parsing). Jar mode is effectively a no-op for them, but if `ensure_core_jar` runs (it will, from `execute_command`) against an empty `$CORE_DIR`, `core_jar_stale`'s `find` returns nothing and the jar gets re-used (or built from nothing — which would fail). Watch this during the full-suite run. If it breaks, either (a) have these tests also point `IW_CORE_JAR` at the pre-built jar, or (b) have `ensure_core_jar` short-circuit when `$CORE_DIR` is empty. Option (a) is cheaper and scoped to this phase.

### Note: per-file BATS execution sidesteps temp-dir races

Per memory, `test.scala` runs each `.bats` file individually to avoid race conditions. Phase 3 keeps that pattern — the jar pre-build happens once, outside the loop, and each BATS file inherits `IW_CORE_JAR`. No new race surface is introduced.
