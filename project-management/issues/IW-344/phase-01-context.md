# Phase 1: Core jar build and mtime check

**Issue:** IW-344
**Phase:** 1 of 3
**Estimate:** 2-3h
**Status:** Pending

## Goals

Add the build infrastructure to `iw-run` for pre-compiling `core/` into a library jar (`build/iw-core.jar`):

- New `build_core_jar()` shell function that runs `scala-cli --power package --library` over the core sources.
- New `core_jar_stale()` shell function that detects when any `core/**/*.scala` file is newer than the built jar (or when the jar is missing).
- Auto-rebuild behavior: when stale or missing, print `"Rebuilding core jar..."` to stderr and run `build_core_jar()`.
- New `IW_CORE_JAR` environment variable: respected if set, otherwise defaults to `$INSTALL_DIR/build/iw-core.jar`.
- Update `bootstrap()` to call `build_core_jar()` instead of compiling `version.scala` together with the core source files.

This phase delivers the build infrastructure only. It does **not** change the three command-execution paths in `execute_command()` — those still use the `$core_files` source-list approach. That switchover is Phase 2.

## Scope

### In scope

- New shell functions in `iw-run`: `build_core_jar()`, `core_jar_stale()`, and a small helper to ensure the jar is fresh (e.g. `ensure_core_jar()`).
- New variable wiring at the top of `iw-run` for `CORE_JAR` (driven by `IW_CORE_JAR` with default `$INSTALL_DIR/build/iw-core.jar`).
- `bootstrap()` rewritten to delegate to `build_core_jar()`.
- Adding `build/` to `.gitignore` (so the produced jar is not tracked).

### Out of scope (deferred)

- **Phase 2:** Switching `execute_command()`'s three invocation sites (project, plugin, shared) from `$core_files` to `--jar "$CORE_JAR" "$CORE_DIR/project.scala"`. The `core_files` collection logic stays untouched in this phase.
- **Phase 3:** BATS test updates, `test.scala` jar pre-build, new BATS tests covering missing/stale jar paths.
- Changes to `scripts/package-release.sh` (separate concern, mentioned in analysis but not in this issue's phases).

## Dependencies

- Existing `iw-run` script (`/home/mph/Devel/iw/iw-cli-IW-344/iw-run`).
- `scala-cli` already required by the script (checked at top of `iw-run`); `--power package --library` is part of standard scala-cli.
- Spike findings in `analysis.md`: confirmed the recipe `scala-cli --power package --library -o build/iw-core.jar <core files>` produces a 1.6MB library jar with no embedded dep metadata, and that it must be paired with `core/project.scala` at execution time (Phase 2 concern).
- `core/project.scala` already exists and contains only `//> using dep` directives — no changes needed to that file.

## Approach

### Build recipe (from spike, see analysis.md lines 87-103)

```bash
scala-cli --power package --library \
    -o "$CORE_JAR" \
    $core_files
```

Where `$core_files` is the existing `find` invocation already used in `bootstrap()` and `execute_command()`:

```bash
find "$CORE_DIR" -name "*.scala" \
    -not -path "*/test/*" \
    -not -path "*/.scala-build/*" \
    | tr '\n' ' '
```

### Function placement in `iw-run`

- Add `CORE_JAR` variable near the existing `CORE_DIR` definition (around line 11-13) so it is exported/visible early.
- Add `build_core_jar()`, `core_jar_stale()`, and `ensure_core_jar()` immediately above the existing `bootstrap()` definition (currently at line 26). This keeps build infrastructure clustered.
- Rewrite the body of `bootstrap()` (lines 26-39) to call `build_core_jar()`.

### mtime check semantics

`core_jar_stale()` returns 0 (true / "is stale") when any of:

1. The jar file does not exist.
2. Any `.scala` file under `$CORE_DIR` (excluding `*/test/*` and `*/.scala-build/*`) has a newer mtime than the jar.

Implementation approach: use `find "$CORE_DIR" ... -newer "$CORE_JAR" -print -quit` and check whether anything was printed. The `-quit` short-circuits on first hit for efficiency.

### Env var handling

```bash
CORE_JAR="${IW_CORE_JAR:-$INSTALL_DIR/build/iw-core.jar}"
export IW_CORE_JAR="$CORE_JAR"
```

Exporting back ensures sub-processes (e.g. `scala-cli`, project commands) see a consistent value if they care to inspect it.

### Auto-rebuild message

`ensure_core_jar()` checks `core_jar_stale()` and, if stale, emits to stderr and rebuilds:

```bash
ensure_core_jar() {
    if core_jar_stale; then
        echo "Rebuilding core jar..." >&2
        build_core_jar
    fi
}
```

This phase **defines** `ensure_core_jar()` but does not yet wire it into `execute_command()` (that's Phase 2). `bootstrap()` calls `build_core_jar()` directly, not via `ensure_core_jar()`, because bootstrap is the explicit pre-build entry point and should always rebuild.

### `bootstrap()` rewrite

Replace lines 26-39 with a body that:

1. Echoes the existing "Bootstrapping iw-cli installation at $INSTALL_DIR..." message.
2. Calls `build_core_jar` (always builds unconditionally — bootstrap is an explicit pre-build entry point, so it should not skip when the jar happens to be fresh).
3. Echoes the existing "Bootstrap complete." message.

The previous `scala-cli compile "$COMMANDS_DIR/version.scala" $core_files --server=false` call goes away — the jar build is the new pre-compilation step.

## Files to modify

- `/home/mph/Devel/iw/iw-cli-IW-344/iw-run` — only file touched in this phase.
- `/home/mph/Devel/iw/iw-cli-IW-344/.gitignore` — add `build/` entry (verify whether already present before adding).

## Component specifications

### Variable: `CORE_JAR`

- Defined near the top of `iw-run`, alongside the existing `INSTALL_DIR`, `IW_COMMANDS_DIR`, `IW_CORE_DIR` block (around line 11-13).
- Default: `$INSTALL_DIR/build/iw-core.jar`.
- Overridable via `IW_CORE_JAR` env var.
- Re-exported as `IW_CORE_JAR` so sub-processes see the resolved path.

### Function: `build_core_jar()`

```bash
# Build the core library jar from $CORE_DIR sources.
# Writes to $CORE_JAR. Prints a stderr message before building.
build_core_jar() {
    echo "Rebuilding core jar..." >&2

    # Ensure the parent directory exists.
    mkdir -p "$(dirname "$CORE_JAR")"

    # Collect core source files (exclude tests and scala-build cache).
    local core_files
    core_files=$(find "$CORE_DIR" -name "*.scala" \
        -not -path "*/test/*" \
        -not -path "*/.scala-build/*" \
        | tr '\n' ' ')

    # shellcheck disable=SC2086
    scala-cli --power package --library \
        -o "$CORE_JAR" \
        $core_files \
        --server=false
}
```

Notes:
- Always emits the "Rebuilding core jar..." line; this keeps `bootstrap()` and `ensure_core_jar()` consistent.
- `--server=false` keeps behavior consistent with the existing `bootstrap()` invocation (no Bloop server side-effects).
- Output is **not** redirected to `/dev/null` — surfacing errors during a jar build is more important than silence. Bootstrap success messages remain on stderr as before.
- If `scala-cli` exits non-zero, `set -euo pipefail` at the top of `iw-run` ensures the launcher fails immediately. No need for explicit error handling.

### Function: `core_jar_stale()`

```bash
# Returns 0 (true) if the jar is missing or any core/**/*.scala is newer.
# Returns 1 (false) if the jar exists and is at least as new as every source.
core_jar_stale() {
    [ ! -f "$CORE_JAR" ] && return 0

    local newer
    newer=$(find "$CORE_DIR" -name "*.scala" \
        -not -path "*/test/*" \
        -not -path "*/.scala-build/*" \
        -newer "$CORE_JAR" \
        -print -quit 2>/dev/null)

    [ -n "$newer" ]
}
```

Notes:
- `-print -quit` short-circuits as soon as one stale source is found.
- Returns the exit status of the `[ -n "$newer" ]` test directly: 0 if any stale file was found, 1 otherwise.

### Function: `ensure_core_jar()`

```bash
# Ensure $CORE_JAR exists and is up to date; rebuild if stale.
ensure_core_jar() {
    if core_jar_stale; then
        build_core_jar
    fi
}
```

This is defined now but not yet wired into `execute_command()` — Phase 2 will call it.

### `bootstrap()` rewrite

Replace lines 26-39 with:

```bash
bootstrap() {
    echo "Bootstrapping iw-cli installation at $INSTALL_DIR..." >&2
    build_core_jar
    echo "Bootstrap complete. iw-cli is ready for offline use." >&2
}
```

`bootstrap` always calls `build_core_jar` unconditionally — it is the explicit pre-build entry point, so even a fresh-looking jar gets rebuilt.

### `.gitignore` update

Add `build/` if not already present.

## Testing strategy

This phase only adds build infrastructure; the three `execute_command()` paths still pass `$core_files` to `scala-cli`, so command behavior at runtime is unchanged.

### Manual smoke test

1. Run `./iw --bootstrap` from the repo root. Expect: `build/iw-core.jar` exists afterwards (~1.6MB per spike data).
2. Delete the jar (`rm -f build/iw-core.jar`) and re-run `./iw --bootstrap`. Expect: jar reappears, "Rebuilding core jar..." printed to stderr.
3. With jar present, touch a core source (`touch core/model/Settings.scala` or any other `.scala` in `core/`). Run a small script that invokes `core_jar_stale`; expect exit 0. Restore-build the jar via `./iw --bootstrap`, repeat — expect exit 1.
4. Set `IW_CORE_JAR=/tmp/test-iw-core.jar`, run `./iw --bootstrap`. Expect: jar built at `/tmp/test-iw-core.jar` instead of default location.
5. Run an existing command (e.g. `./iw version`). Expect: command works exactly as before (no behavior change in this phase).

### Automated tests

- Existing BATS tests must continue to pass. `bootstrap()` semantics change (no longer compiles `version.scala`), but the externally observable contract — "after bootstrap, iw-cli is ready" — is preserved.
- New BATS coverage for jar build/rebuild scenarios is **deferred to Phase 3**.

### Pre-commit

- `shellcheck` (if in pre-commit) must pass on the modified `iw-run`. Use the existing `# shellcheck disable=SC2086` pattern where needed for the unquoted `$core_files` expansion.

## Acceptance criteria

- [ ] `build_core_jar()` exists in `iw-run` and produces a working library jar at `$CORE_JAR` when invoked.
- [ ] `core_jar_stale()` exists in `iw-run` and:
  - Returns 0 when `$CORE_JAR` does not exist.
  - Returns 0 when any non-test `.scala` file under `$CORE_DIR` is newer than `$CORE_JAR`.
  - Returns 1 otherwise.
- [ ] `IW_CORE_JAR` env var is respected; default value is `$INSTALL_DIR/build/iw-core.jar`.
- [ ] `bootstrap()` calls `build_core_jar()` instead of compiling `version.scala` with inline core files.
- [ ] After running `./iw --bootstrap` from a clean repo, `build/iw-core.jar` exists.
- [ ] `.gitignore` excludes the `build/` directory.
- [ ] Existing BATS tests still pass (no behavior change at command-execution time yet — `execute_command()` is unchanged in this phase).
- [ ] Pre-commit hooks pass (`shellcheck`, formatting).

## Notes / open questions

- The "Rebuilding core jar..." message lives inside `build_core_jar()` itself rather than at the call sites. This means `./iw --bootstrap` will also print "Rebuilding core jar..." between its existing "Bootstrapping..." and "Bootstrap complete." lines. That seems fine — it's accurate and helpful — but flag if a different cadence is preferred.
- This phase deliberately defines `ensure_core_jar()` even though no caller uses it yet. It is the only Phase-2 wiring being pre-staged, and keeping all three new functions together as a coherent commit makes the diff easier to review.
- No changes to `commands/version.scala` or any command file — keep changes confined to `iw-run` (and `.gitignore`).
