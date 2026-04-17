# Phase 1 Tasks: Core jar build and mtime check

**Issue:** IW-344
**Phase:** 1 of 3
**Estimate:** 2-3h

## Setup

- [ ] [setup] Verify `.gitignore` already lists `build/` (currently line 18, comment "Staged output for publishing skills to dev-docs"); if absent, add it. Note: existing entry already covers Phase 1 needs, but confirm before assuming.
- [ ] [setup] Confirm placement context in `iw-run`: `INSTALL_DIR` / `IW_CORE_DIR` / `CORE_DIR` definitions live at lines 8-13, and `bootstrap()` lives at lines 26-39 — the new `CORE_JAR` variable belongs adjacent to the `CORE_DIR` block, and the new functions belong immediately above `bootstrap()`.

## Implementation

- [ ] [impl] Add `CORE_JAR` variable definition near the existing `CORE_DIR` block (around line 13): `CORE_JAR="${IW_CORE_JAR:-$INSTALL_DIR/build/iw-core.jar}"` followed by `export IW_CORE_JAR="$CORE_JAR"` so sub-processes see the resolved path.
- [ ] [impl] Add `build_core_jar()` function above `bootstrap()`. Body: emits `"Rebuilding core jar..."` to stderr, runs `mkdir -p "$(dirname "$CORE_JAR")"`, collects `core_files` via the existing `find` recipe, then runs `scala-cli --power package --library -o "$CORE_JAR" $core_files --server=false` with the `# shellcheck disable=SC2086` directive on the `scala-cli` line.
- [ ] [impl] Add `core_jar_stale()` function below `build_core_jar()`. Returns 0 if `$CORE_JAR` is missing; otherwise uses `find "$CORE_DIR" -name "*.scala" -not -path "*/test/*" -not -path "*/.scala-build/*" -newer "$CORE_JAR" -print -quit 2>/dev/null` and returns the result of `[ -n "$newer" ]`.
- [ ] [impl] Add `ensure_core_jar()` helper below `core_jar_stale()`. Body: `if core_jar_stale; then build_core_jar; fi`. Defined now but not yet called by `execute_command()` (Phase 2 will wire it in).
- [ ] [impl] Rewrite `bootstrap()` body (lines 26-39) so it echoes the existing `"Bootstrapping iw-cli installation at $INSTALL_DIR..."` line, then calls `build_core_jar` unconditionally, then echoes the existing `"Bootstrap complete. iw-cli is ready for offline use."` line. Drop the previous `scala-cli compile "$COMMANDS_DIR/version.scala" $core_files --server=false > /dev/null 2>&1` invocation and the now-unused local `core_files` collection.

## Verification

- [ ] [verify] From a clean state (`rm -f /home/mph/Devel/iw/iw-cli-IW-344/build/iw-core.jar`), run `./iw --bootstrap` and confirm `build/iw-core.jar` is created (~1.6MB per spike) and that `"Rebuilding core jar..."` appears on stderr between the bootstrap start/complete lines.
- [ ] [verify] Delete the jar and re-run `./iw --bootstrap`; confirm the jar reappears and the `"Rebuilding core jar..."` stderr message is printed again.
- [ ] [verify] With the jar present, `touch core/model/Settings.scala` (or any other non-test `.scala` under `core/`) and run a one-liner that sources `iw-run` is unsafe (it execs); instead run a small inline script: `bash -c 'CORE_JAR=build/iw-core.jar CORE_DIR=core; <paste core_jar_stale body>; core_jar_stale && echo stale || echo fresh'`. Expect `stale`. Re-run `./iw --bootstrap` and repeat the inline check; expect `fresh`.
- [ ] [verify] Run `IW_CORE_JAR=/tmp/test-iw-core.jar ./iw --bootstrap`; confirm the jar is built at `/tmp/test-iw-core.jar` and not at the default location, and that the variable is reflected in any sub-process inspection (env shows `IW_CORE_JAR=/tmp/test-iw-core.jar`).
- [ ] [verify] Run an existing command (e.g. `./iw version`) and confirm output is unchanged — Phase 1 must not affect command execution at runtime since `execute_command()` still uses `$core_files`.
- [ ] [verify] Run pre-commit checks (format + scala-cli `-Werror` compile + shellcheck if configured) and confirm they pass on the modified `iw-run`.

## Notes

- No new automated tests in this phase. New BATS coverage for missing/stale jar paths and `IW_CORE_JAR` override is deferred to Phase 3.
- `build/` is already present in `.gitignore` (line 18, under the "Staged output for publishing skills to dev-docs" comment), so the setup task is a confirmation rather than an addition.
- `bootstrap()` calls `build_core_jar` directly, not via `ensure_core_jar`, because bootstrap is the explicit pre-build entry point and should always rebuild regardless of mtime.
- `ensure_core_jar()` is intentionally dead code in this phase; it ships as part of the same coherent commit so Phase 2 can simply add call sites without re-touching the function definitions.
- This phase does not touch `execute_command()`, `commands/version.scala`, `scripts/package-release.sh`, or any BATS test file.
