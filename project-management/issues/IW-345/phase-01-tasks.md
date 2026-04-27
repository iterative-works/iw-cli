# Phase 1 Tasks: Mill bootstrap + core jar production

## Setup

- [x] [setup] Create `deps/` directory at repo root if it does not already exist (for vendored Mill and Node artifacts).
- [x] [setup] Download the Mill 1.1.5 launcher binary from `https://github.com/com-lihaoyi/mill/releases/download/1.1.5/1.1.5` and place it at `deps/mill` (used by the CI Dockerfile; the local `./mill` wrapper downloads its own cached copy at runtime).
- [x] [setup] Download the Node 20 linux-x64 tarball (`node-v20.x.x-linux-x64.tar.xz`) and place it at `deps/node.tar.xz` for CI image consumption.
- [x] [setup] Add `out/` entry to `.gitignore` so Mill's scratch directory stays out of commits.

## Tests

- [x] [test] Regression checkpoint: before making any build changes, run `./iw ./test unit` on the current scala-cli-built jar to capture the baseline (141 passing tests) for post-change comparison.
- [x] [test] Regression checkpoint: after the Mill switchover, run `./iw ./test unit` end to end and confirm all 141 existing munit tests still pass against `build/iw-core.jar` produced by Mill.
- [x] [test] Regression checkpoint: run the pre-push hook (format + scalafix + `-Werror` core compile + unit tests + command compilation) and confirm it passes with the Mill-built jar.

## Implementation

- [x] [impl] Create `.mill-version` at repo root containing exactly `1.1.5` on a single line with no trailing content.
- [x] [impl] Create the `./mill` wrapper script at repo root mirroring procedures' wrapper shape: read `.mill-version`, check for a cached launcher under `~/.cache/mill/download/1.1.5` (or platform equivalent), download from `https://github.com/com-lihaoyi/mill/releases/download/1.1.5/1.1.5` on miss, `chmod +x`, and `exec` with all args passed through.
- [x] [impl] Mark `./mill` wrapper executable (`chmod +x mill`).
- [x] [impl] Create `build.mill` at repo root with a `PURPOSE:` header, a single `object core extends ScalaModule` (Scala 3.3.7, `Task.Sources(moduleDir / os.up / "core")`, `mvnDeps` mirroring `core/project.scala` including the dashboard-only deps `cask`, `scalatags`, `flexmark`), and a top-level `iwCoreJar` task that copies `core.jar().path` to `build/iw-core.jar`.
- [x] [impl] Add paired SYNC sync-comment lines in `core/project.scala` and `build.mill`, each referencing the other file as the mirror of the dep list (single conceptual change, edit both files together).
- [x] [impl] Edit `iw-run`'s `build_core_jar()` body: preserve the `"Rebuilding core jar..."` log and `mkdir -p "$(dirname "$CORE_JAR")"` line, replace the `scala-cli --power package --library ...` invocation with `(cd "$INSTALL_DIR" && ./mill iwCoreJar)`. Leave `ensure_core_jar()` and `core_jar_stale()` untouched.
- [x] [impl] Edit `.github/Dockerfile.ci`: between the existing Coursier block and the BATS block, insert the Node 20 tarball extraction (`COPY deps/node.tar.xz`, extract to `/opt`, symlink `/opt/node`, extend `PATH` with `/opt/node/bin`), `RUN corepack enable`, and Mill 1.1.5 install (`COPY deps/mill /usr/local/bin/mill`, `chmod +x`, `mill --version` sanity check).
- [x] [impl] Extend the final Dockerfile.ci verification line (currently at line 51) to also run `node --version`, `corepack --version`, and `mill --version` alongside the existing tool checks.

## Integration

- [x] [integration] From a clean tree (wipe `out/` and `build/iw-core.jar`), run `./mill iwCoreJar` and confirm `build/iw-core.jar` appears at the expected path; the first run should download Mill via the `./mill` wrapper, a second run should be a cache hit.
- [x] [integration] Run `./iw ./test unit` against the Mill-built jar end to end and confirm 141/141 munit tests pass.
- [x] [integration] Smoke-run `./iw status` (or another scala-cli command that exercises the runtime classpath from `core/project.scala`) and confirm it executes end to end without `NoClassDefFoundError` or classpath drift.
- [x] [integration] Dry-run the pre-push hook locally and confirm green (format + scalafix + `-Werror` compile + unit tests + command compilation) with the Mill-built jar in place.
- [x] [integration] Verify (by reading the diff) that the Dockerfile.ci final verification line invokes `mill --version`, `node --version`, and `corepack --version` alongside the pre-existing tool checks (`java`, `scala-cli`, `scalafix`, `bats`, `tmux`, `jq`, `python3`, `gh`).
- [x] [integration] Confirm `rg -n "scala-cli package" iw-run` returns no results inside `build_core_jar()` (unrelated mentions in other comments are acceptable).

## Acceptance Criteria Coverage

- AC1 `./mill iwCoreJar` produces `build/iw-core.jar` from a clean tree → `[integration]` clean-tree `./mill iwCoreJar` run.
- AC2 `./iw ./test unit` passes all 141 munit tests against the Mill-built jar → `[test]` post-change regression checkpoint; `[integration]` full `./iw ./test unit` run.
- AC3 Pre-push hook passes → `[test]` pre-push regression checkpoint; `[integration]` pre-push dry-run.
- AC4 `rg -n "scala-cli package" iw-run` returns no results inside `build_core_jar()` → `[impl]` edit `iw-run` `build_core_jar()` body; `[integration]` rg verification.
- AC5 `rg -n "^out/$" .gitignore` finds the new entry → `[setup]` add `out/` to `.gitignore`.
- AC6 `build.mill` uses Mill 1.1.x syntax with one `core` module + top-level `iwCoreJar` → `[impl]` create `build.mill`.
- AC7 `.github/Dockerfile.ci` provisions Node 20 + Corepack + Mill 1.1.5 with an extended verification line → `[setup]` fetch `deps/node.tar.xz` and `deps/mill`; `[impl]` Dockerfile.ci insertion; `[impl]` extend verification line; `[integration]` Dockerfile.ci diff verification.
- AC8 Sync-comment present in both `core/project.scala` and `build.mill` → `[impl]` paired SYNC sync-comment edit.
- AC9 `./mill` wrapper is executable and `.mill-version` contains exactly `1.1.5` → `[impl]` create `.mill-version`; `[impl]` create `./mill` wrapper; `[impl]` `chmod +x mill`.
- AC10 Smoke-run: at least one scala-cli command runs end to end against the Mill-built jar → `[integration]` `./iw status` smoke run.
**Phase Status:** Complete
