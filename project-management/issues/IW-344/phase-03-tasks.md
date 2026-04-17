# Phase 3 Tasks: E2E test adaptation

**Issue:** IW-344
**Phase:** 3 of 3
**Estimate:** 3-5h

## Setup

- [ ] [setup] Re-read `.iw/commands/test.scala` `runE2ETests()` (lines ~125-145) to confirm the current BATS loop structure matches phase-03-context.md before editing.
- [ ] [setup] Re-read `core/adapters/Process.scala` `runStreaming` signature (lines ~82-86) and the existing `run` method (lines ~39-43) to confirm the `env` parameter pattern to mirror.
- [ ] [setup] Re-read the seven BATS setup functions listed in the context doc (`plugin-commands-describe.bats`, `plugin-commands-execute.bats`, `plugin-commands-list.bats`, `plugin-discovery.bats`, `project-commands-describe.bats`, `project-commands-execute.bats`, `project-commands-list.bats`) and locate the line immediately after `export IW_CORE_DIR=...` where the new block will be inserted.
- [ ] [setup] Confirm Phase 1/2 pieces still in place in `iw-run`: `CORE_JAR` (line 14), `IW_CORE_JAR` export (line 15), `build_core_jar` (lines 29-47), `core_jar_stale` (lines 51-62), `ensure_core_jar` (lines 65-69), and the `ensure_core_jar` call at the top of `execute_command()` (Phase 2).
- [ ] [setup] Capture a baseline: run `./iw ./test e2e` on current HEAD and record which BATS files pass/fail so Phase 3 regressions are distinguishable from pre-existing noise.

## Implementation

### 1. Extend `ProcessAdapter.runStreaming` with env support

- [ ] [impl] In `core/adapters/Process.scala`, extend `runStreaming` by adding a trailing `env: Map[String, String] = Map.empty` parameter.
- [ ] [impl] Forward `env` to the underlying `os.proc(command).call(..., env = env)` invocation inside `runStreaming`, mirroring the existing `run` method.
- [ ] [impl] Confirm all three existing `runStreaming` callers remain source-compatible (default-empty `env` preserves current behaviour). Grep `runStreaming(` under `core/` and `.iw/commands/` to verify.

### 2. `test.scala`: pre-build jar before E2E tests

- [ ] [impl] In `.iw/commands/test.scala` `runE2ETests()`, immediately before the `testFiles.sortBy(_.last)` loop, add a `Output.section("Pre-building core jar for E2E tests")` line followed by a `ProcessAdapter.runStreaming(Seq((installDir / "iw-run").toString, "--bootstrap"), timeoutMs = 10 * 60 * 1000)` call.
- [ ] [impl] If the bootstrap exit code is non-zero, print `"Failed to pre-build core jar; aborting E2E run"` via `Output.error` and `return false` before entering the BATS loop.
- [ ] [impl] Compute `val coreJar = installDir / "build" / "iw-core.jar"` after the bootstrap call succeeds.
- [ ] [impl] Change the per-BATS `ProcessAdapter.runStreaming` call inside the loop to pass `env = Map("IW_CORE_JAR" -> coreJar.toString)` alongside the existing `Seq("bats", testFile.toString)` and `timeoutMs` arguments.

### 3. BATS setup changes (seven files)

- [ ] [impl] In `test/plugin-commands-describe.bats`, insert the `IW_CORE_JAR` + `touch -r` block immediately after `export IW_CORE_DIR=...`.
- [ ] [impl] Same block in `test/plugin-commands-execute.bats` after `export IW_CORE_DIR=...`.
- [ ] [impl] Same block in `test/plugin-commands-list.bats` after `export IW_CORE_DIR=...`.
- [ ] [impl] Same block in `test/plugin-discovery.bats` after `export IW_CORE_DIR=...`.
- [ ] [impl] Same block in `test/project-commands-describe.bats` after `export IW_CORE_DIR=...`.
- [ ] [impl] Same block in `test/project-commands-execute.bats` after `export IW_CORE_DIR=...`.
- [ ] [impl] Same block in `test/project-commands-list.bats` after `export IW_CORE_DIR=...`.

The block (exact text per context doc section 2):
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

## Tests

- [ ] [test] Create `test/core-jar.bats` with the standard BATS setup pattern: `setup()` exports `IW_SERVER_DISABLED=1`, creates `TEST_DIR=$(mktemp -d)`, copies `iw-run` + `commands/*.scala` + `core/` into `.iw-install/`, and sets `IW_COMMANDS_DIR` / `IW_CORE_DIR` / a per-test `IW_CORE_JAR=$TEST_DIR/.iw-install/build/iw-core.jar` (NOT the shared repo-root jar — these tests deliberately mutate the jar and must not contaminate the shared one).
- [ ] [test] `core-jar.bats` scenario 1: missing jar triggers auto-rebuild. Ensure `$IW_CORE_JAR` path does not exist, run `./iw-run version` with `2>&1` capture, assert exit 0, assert `$output` contains `"Rebuilding core jar..."`, assert the jar now exists.
- [ ] [test] `core-jar.bats` scenario 2: stale jar triggers auto-rebuild. Build the jar (`./iw-run --bootstrap`), `sleep 1`, `touch` a `.iw-install/core/**/*.scala` file, run `./iw-run version` with `2>&1` capture, assert `$output` contains `"Rebuilding core jar..."`, assert jar mtime is newer than the pre-touch baseline.
- [ ] [test] `core-jar.bats` scenario 3: fresh jar is silent. Run `./iw-run version` twice back-to-back; assert the second run's `$output` does NOT contain `"Rebuilding core jar..."`.
- [ ] [test] `core-jar.bats` scenario 4: `IW_CORE_JAR` override honored. Export `IW_CORE_JAR=/tmp/custom-iw-core.jar` (cleaned up in teardown), run `./iw-run --bootstrap`, assert `/tmp/custom-iw-core.jar` exists and the default `$TEST_DIR/.iw-install/build/iw-core.jar` does not.
- [ ] [test] `core-jar.bats` scenario 5: `./iw-run --bootstrap` produces the jar at the default location. Run `./iw-run --bootstrap`, assert `$TEST_DIR/.iw-install/build/iw-core.jar` exists.
- [ ] [test] `core-jar.bats` scenario 6: `build_core_jar` overwrite regression guard (Phase-2 `-f` fix). Run `./iw-run --bootstrap`, capture `old_mtime=$(stat -c %Y "$jar")`, `sleep 1`, `touch` a `.iw-install/core/**/*.scala` source, run `./iw-run version` with `2>&1` capture. Assert: exit 0; jar still exists at same path; `stat -c %Y "$jar"` strictly greater than `$old_mtime`; `$output` contains `"Rebuilding core jar..."`; `$output` does NOT contain `"file exists"` or `"already exists"`.
- [ ] [test] Add `test/core-jar.bats` to the test runner's discovered set by confirming `test.scala`'s BATS loop picks it up automatically via the `test/*.bats` glob — no explicit wiring required.

## Verification

- [ ] [verify] `./iw ./test unit` passes — verifies `test.scala` and `core/adapters/Process.scala` compile under `-Werror`.
- [ ] [verify] `./iw ./test compile` passes — verifies no `commands/*.scala` regressed (unchanged in this phase).
- [ ] [verify] `bats test/core-jar.bats` passes all 6 scenarios standalone.
- [ ] [verify] `./iw ./test e2e` runs green end-to-end across all 39 BATS files (38 existing + `core-jar.bats`).
- [ ] [verify] `test/project-commands-execute.bats` passes unchanged — the sentinel for core-type import resolution under jar mode (imports `iw.core.adapters.*` and `iw.core.model.*`).
- [ ] [verify] `test/plugin-commands-execute.bats` passes — sentinel for plugin paths with hooks/lib files.
- [ ] [verify] Standalone spot-check: `bats test/project-commands-execute.bats` without a prior `./iw ./test e2e` having produced the shared jar. Expect slower first run (core rebuild into fallback path) but all tests pass.
- [ ] [verify] Inspect `./iw ./test e2e` stdout/stderr: confirm `"Rebuilding core jar..."` appears exactly once (from the initial `./iw-run --bootstrap` pre-build) and does not leak into BATS assertion diffs.
- [ ] [verify] Watch `plugin-hooks.bats` and `version-check.bats` in the full E2E run — they don't copy `core/` but `ensure_core_jar` still fires. If they break, either point `IW_CORE_JAR` at the pre-built jar in their setup (preferred, in-scope) or flag for follow-up.
- [ ] [verify] Pre-commit hooks pass (format + `scala-cli compile --scalac-option -Werror core/`). Pre-push hooks pass (compile + unit tests + command compilation).

## Notes

- The `env` parameter addition to `runStreaming` is the only `core/` change this phase. Default-empty preserves all existing call sites.
- Per-file BATS execution is preserved — the jar pre-build happens once before the loop, outside the temp-dir race surface that motivated per-file execution in the first place.
- `core-jar.bats` uses a per-test `IW_CORE_JAR` (inside `$TEST_DIR`) deliberately: scenarios 2 and 6 mutate the jar, and must not contaminate the shared repo-root jar inherited by the other seven BATS files.
- The `touch -r` mtime sync on copied sources is load-bearing: without it, the `cp -r core/` step would make the copied `.scala` files newer than the pre-built jar, triggering `core_jar_stale` and a mid-suite rebuild into the shared jar path.
- When a developer runs a single `.bats` file standalone without the pre-built shared jar, the `if [ -f ... ]` guard fails, `IW_CORE_JAR` is unset, and `iw-run` falls back to `$INSTALL_DIR/build/iw-core.jar` and rebuilds from copied sources on first command execution — slow (~30s) but correct.
- This phase does not touch `iw-run`, `commands/*.scala`, `scripts/package-release.sh`, or the unit-test / compile-check paths in `test.scala`.
- `bootstrap.bats` additional assertion (`build/iw-core.jar` exists after `--bootstrap`) is optional per the context doc; skip if the release-package flow does not include `build/`.

**Phase Status:** Pending
