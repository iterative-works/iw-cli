# Phase 1 Tasks: Packaging + Launcher + Bootstrap

**Issue:** IW-346
**Phase:** 1 of 2
**Estimate:** 2.5-5.5h
**Status:** Not started
**Context:** [phase-01-context.md](phase-01-context.md)

## Guard rails (read first)

- **Decision 2:** Do NOT modify the three `scala-cli run … "$CORE_DIR/project.scala" --jar "$CORE_JAR" …` invocations at `iw-run:565, 644, 729`. They stay exactly as written; `project.scala` remains the deps manifest, the resolved `$CORE_JAR` provides compiled classes.
- **Decision 3:** Resolution is pure file-presence — no sentinel file, no `IW_PREFER_MILL` escape hatch. The existing `IW_*_JAR` env vars remain the only escape hatch.
- **Decision 5:** Do NOT add `./mill`, `.mill-version`, or `build.mill` to the tarball. These must remain absent from the staged release.
- **No refactoring tasks** are planned for Phase 1.
- **No CI / BATS / RELEASE.md** changes in Phase 1 — those belong to Phase 2.

## Setup

- [x] [setup] Confirm prerequisites are available locally: `./mill` runs (Mill 1.1.5), Node 20 + Yarn via Corepack, `WEBAWESOME_NPM_TOKEN` exported in env, plus `scala-cli`, `jq`, `tar`, `rsync`, `curl`. Without these the packaging step fails at Vite build.
- [x] [setup] Confirm IW-344 (commit 259bc54, `core.jar` Mill task) and IW-345 (commit 8a9b603, `dashboard.assembly` Mill task) are in the working tree.
- [x] [setup] **Audit gate (Decision 1, Risk 3 mitigation).** Run `rg -n 'core/' commands/ core/` and `rg -n 'IW_CORE_DIR|CORE_DIR' commands/ core/ iw-run`; classify every match as build-time (deps manifest, scala-cli invocation) or runtime read (file open, asset load, hook discovery). Specifically check for: hook discovery scanning `$IW_CORE_DIR`, dashboard server reading `core/*.css` or `core/*.js` as static assets, and command code calling `Source.fromFile` on a `core/` path. The three `scala-cli run … "$CORE_DIR/project.scala" --jar "$CORE_JAR"` invocations at `iw-run:565, 644, 729` are EXPECTED references to `project.scala` and are fine. If anything else surfaces, STOP and surface as CLARIFY before proceeding. Record the result in `implementation-log.md`.
  - Surfaced finding: `commands/review-state.scala` walked `core/` at runtime (`os.walk` over `core/**/*.scala`). Resolved by consolidating dispatcher + 3 subcommands into a single scala-cli script — runtime walk eliminated; the consolidated command now runs through the normal `iw-run:565` `--jar "$CORE_JAR"` path like every other command. See `implementation-log.md` Resolution section.

## Implementation — `scripts/package-release.sh`

- [x] [impl] Add a Mill build step at the top of the staging flow (before any rsync/cp into `$RELEASE_PACKAGE_DIR`): from `$PROJECT_ROOT`, invoke `./mill --ticker false show core.jar` and `./mill --ticker false show dashboard.assembly`.
- [x] [impl] Parse the Mill JSON-quoted output the same way `iw-run:50` does — `jq -r '.' | sed -E 's#^ref:v[0-9]+:[a-f0-9]+:##'`. Do not hardcode `out/core/jar.dest/out.jar` or any other Mill-internal layout.
- [x] [impl] Validate each resolved jar path: fail loudly (rely on existing `set -euo pipefail`) if either path is empty, missing, not a `.jar`, or zero bytes.
- [x] [impl] Create the `build/` staging dir: `mkdir -p "$RELEASE_PACKAGE_DIR/build"`.
- [x] [impl] `cp` the resolved core jar to `"$RELEASE_PACKAGE_DIR/build/iw-core.jar"` (literal name — the launcher does a literal path test).
- [x] [impl] `cp` the resolved dashboard jar to `"$RELEASE_PACKAGE_DIR/build/iw-dashboard.jar"` (literal name).
- [x] [impl] Replace the current `core/` rsync at `package-release.sh:40-49` (which copies all `*.scala`, `*.css`, `*.js` under `core/`) with a single-file `cp "$PROJECT_ROOT/core/project.scala" "$RELEASE_PACKAGE_DIR/core/project.scala"`. Keep the `core/` directory creation at `package-release.sh:19`. (Decision 1.)
- [x] [impl] Verify the script does NOT add `./mill`, `.mill-version`, `build.mill`, `.bsp/`, `out/`, `dashboard/jvm/`, or `dashboard/frontend/` to the tarball. The current script already excludes these implicitly; just don't introduce them. (Decision 5.)
- [x] [impl] Strengthen the verification tail after `tar -czf`: either widen the existing `tar -tzf … | head -20` to `head -30`, or add a dedicated `tar -tzf "$tarball" | grep -E '^iw-cli-[^/]+/build/'` so CI logs visibly confirm both jars are in the tarball.

## Implementation — `iw-run`

- [x] [impl] Add a comment block above `ensure_core_jar` (currently `iw-run:65-72`) documenting the resolution order (env override → `$INSTALL_DIR/build/*.jar` → Mill) AND the convention "Mill writes to `out/`, scala-cli writes to `.scala-build/`, releases write to `build/`". This comment is load-bearing per Decision 3 (Risk 2 mitigation).
- [x] [impl] Update `ensure_core_jar()` (`iw-run:65-72`) to the three-tier resolution from Decision 3:
  1. If `$IW_CORE_JAR` is set and points to a readable `.jar` → use it.
  2. Else if `$INSTALL_DIR/build/iw-core.jar` exists → use it (set `CORE_JAR` and export `IW_CORE_JAR`).
  3. Else → `CORE_JAR="$(mill_jar_path core.jar)"`; export `IW_CORE_JAR`.
  Contract on return: `CORE_JAR` is a readable absolute path to a `.jar`, and `IW_CORE_JAR` is exported to the same value so child processes inherit it.
- [x] [impl] Update `ensure_dashboard_jar()` (`iw-run:77-84`) with the same three-tier pattern: env override → `$INSTALL_DIR/build/iw-dashboard.jar` → `mill_jar_path dashboard.assembly`. On return, set `DASHBOARD_JAR` and export `IW_DASHBOARD_JAR`.
- [x] [impl] Update `bootstrap()` (`iw-run:87-91`): after `ensure_core_jar`, if `$INSTALL_DIR/build/iw-core.jar` exists, print `iw-cli is ready (pre-built jars present at $INSTALL_DIR/build/)`. Otherwise keep the existing "Bootstrap complete" message (which now reflects the dev-checkout case where Mill ran during `ensure_core_jar`).
- [x] [impl] Add a guard in `mill_jar_path` (or before calling it) that detects missing `./mill` in `$INSTALL_DIR` and prints `ERROR: pre-built jars not found at $INSTALL_DIR/build/ and Mill is not available` before exiting. This is an additive safety net that does NOT change the resolution order; it only replaces the confusing shell "No such file or directory" when an extracted tarball lacks `build/` AND lacks `./mill`.
- [x] [impl] **Verify (do not modify)** the three `scala-cli run … "$CORE_DIR/project.scala" --jar "$CORE_JAR" …` invocations at `iw-run:565, 644, 729` are unchanged. Decision 2 forbids touching them.

## Implementation — `iw-bootstrap` (optional)

- [ ] [optional] [impl] Optional: after `tar -xzf` in `download_release()`, add a defensive presence check `[[ -f "$version_dir/build/iw-core.jar" ]] || { echo "Error: extracted tarball missing build/iw-core.jar"; exit 1; }`. Gives a friendlier error than letting `iw-run --bootstrap` discover the problem two layers deeper. Not required by the analysis (`analysis.md:79` calls it "optional polish") — implementer's call.
  - SKIPPED (implementer's call). The new `mill_jar_path` guard in `iw-run` already produces a clear error if an extracted tarball lacks both `build/` and `./mill`, which covers the same failure mode at the layer where it manifests. Adding a duplicate check in `iw-bootstrap` would be redundant.

## Verification (manual, Phase 1 only — BATS deferred to Phase 2)

- [x] [verify] **Build a tarball locally.** With `WEBAWESOME_NPM_TOKEN` exported and Node 20 + Yarn available, run `./scripts/package-release.sh 0.5.0-test`. Then inspect: `tar -tzf release/iw-cli-0.5.0-test.tar.gz | grep -E 'build/'` lists exactly two non-zero entries: `iw-cli-0.5.0-test/build/iw-core.jar` and `iw-cli-0.5.0-test/build/iw-dashboard.jar` (confirm sizes are non-zero in the tar listing's size column).
- [x] [verify] **Tarball contains only `core/project.scala` from `core/`.** Confirm via `tar -tzf` that `iw-cli-0.5.0-test/core/project.scala` is present and no other `core/**/*.scala`, `core/**/*.css`, or `core/**/*.js` entries exist.
- [x] [verify] **Extract and run without Mill.** Extract the tarball to a temp dir, then with `PATH=/usr/bin:/bin` (Mill removed):
  - `./iw-run --bootstrap` — must succeed silently (no Mill invocation).
  - `./iw-run --list` — must succeed and list all commands.
  - `IW_PROJECT_DIR=/tmp ./iw-run version` (or any command exercising `ensure_core_jar`) — must succeed using the bundled `build/iw-core.jar`.
- [x] [verify] **Dev-checkout regression.** From the iw-cli repo root (no `build/` dir): `./iw --list` and `./iw <some-command>` continue to drive Mill exactly as today (no new behavior); `./iw --bootstrap` continues to invoke Mill via the rung-3 fallback in `ensure_core_jar`.
- [x] [verify] **Env override still wins (rung 1 > rung 2).** `IW_CORE_JAR=/tmp/fake.jar ./iw-run --bootstrap` uses the env value (and fails clean because it's not a real jar) rather than picking up `build/iw-core.jar`.
- [x] [verify] Record the audit-gate result and the four manual verification outcomes in `implementation-log.md` before opening the PR.

## Acceptance Checklist

- [x] `./scripts/package-release.sh 0.5.0-test` runs to completion locally and produces `release/iw-cli-0.5.0-test.tar.gz`.
- [x] `tar -tzf release/iw-cli-0.5.0-test.tar.gz` listing contains `iw-cli-0.5.0-test/build/iw-core.jar` and `iw-cli-0.5.0-test/build/iw-dashboard.jar`, both non-empty.
- [x] The same listing contains `iw-cli-0.5.0-test/core/project.scala` and does NOT contain other `core/**/*.scala`, `core/**/*.css`, or `core/**/*.js` files.
- [x] Extracted tarball, with `PATH=/usr/bin:/bin` (Mill hidden), can run:
  - [x] `./iw-run --bootstrap` (succeeds without invoking Mill)
  - [x] `./iw-run --list`
  - [x] At least one real command (e.g. `./iw-run version`) that exercises `ensure_core_jar` end-to-end.
- [x] In the dev checkout (no `build/` dir), `./iw <cmd>` and `./iw --bootstrap` continue to drive Mill — no regression.
- [x] `IW_CORE_JAR=/path/to/explicit.jar` env override still wins over `$INSTALL_DIR/build/iw-core.jar` (rung 1 > rung 2).
- [x] Audit gate clear: no runtime read of `core/**/*.{scala,css,js}` beyond `core/project.scala` found in `commands/` or `core/`. Result recorded in implementation log.
- [x] Decisions 1, 2, 3, 5 honored:
  - 1: only `core/project.scala` shipped from `core/`.
  - 2: no changes to the three `scala-cli run … --jar` invocations at `iw-run:565, 644, 729`.
  - 3: pure file-presence resolution at `$INSTALL_DIR/build/*.jar`; no sentinel, no `IW_PREFER_MILL`.
  - 5: no `./mill`, `.mill-version`, or `build.mill` shipped in the tarball.
- [x] Decision 4 (CI on self-hosted + `iw-cli-ci` container) explicitly acknowledged as Phase 2 — not in this PR.
- [x] Comment block in `iw-run` near `ensure_core_jar` documents the resolution order and the `out/` vs `build/` vs `.scala-build/` convention.
**Phase Status:** Complete
