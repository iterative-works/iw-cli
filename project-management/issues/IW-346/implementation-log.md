# Implementation Log: IW-346 — Layer 0 release packaging

## Phase 1: Audit gate — BLOCKED, surfaced finding

**Date:** 2026-04-27

### Audit commands

```
rg -n 'core/' commands/ core/
rg -n 'IW_CORE_DIR|CORE_DIR' commands/ core/ iw-run
rg -n 'os\.walk|Source\.fromFile|os\.read|\.list\(' commands/ core/
```

### Classification of matches

**iw-run** (build-time, Decision 2 explicitly allows):
- `iw-run:11`  — `export IW_CORE_DIR="${IW_CORE_DIR:-$INSTALL_DIR/core}"`
- `iw-run:13`  — `CORE_DIR="$IW_CORE_DIR"`
- `iw-run:565, 644, 729` — three `scala-cli run … "$CORE_DIR/project.scala" --jar "$CORE_JAR"` invocations.
  These read **`core/project.scala` only** as the deps manifest. Decision 2 forbids touching them; the file we ship covers them.

**core/test/WorkflowStatePathsTest.scala:22** — string literal `"core/adapters/Git.scala"` used as test data in `WorkflowStatePaths.partition` assertions. Not a runtime read — purely a test-side string. `core/test/` is excluded from the tarball anyway. No issue.

**`os.read` matches in `commands/` and `core/`:** all read paths *outside* `core/**/*.{scala,css,js}` (config files, review-state.json, VERSION file, scalafmt/scalafix config files via `ScalafixChecks` / `ScalafmtChecks` / `ContributingChecks` — those read project files like `.scalafmt.conf` / `.scalafix.conf` / `CONTRIBUTING.md`, not `core/` sources). No issue.

### **Surfaced finding (BLOCKER) — `commands/review-state.scala:46-93`**

`runSubcommand` reads `IW_CORE_DIR` from env, then walks the core directory at runtime:

```scala
val coreDir = sys.env.get("IW_CORE_DIR") match
  case Some(dir) => os.Path(dir)
  ...

// Find all core files excluding test directory (same as iw-run does)
val coreFiles = os
  .walk(coreDir)
  .filter(p => p.ext == "scala" && !p.segments.contains("test"))
  .map(_.toString)

val command = List("scala-cli", "run", "-q", "--suppress-outdated-dependency-warning",
                   scriptPath.toString) ++ coreFiles ++ List("--") ++ args
```

Then it execs `scala-cli run <subcommand-script> <every-core-scala-file> -- <args>`.

**This is a literal runtime walk of `core/**/*.scala`.** It is the dispatcher behind `iw review-state validate|write|update`, which routes to `commands/review-state/{validate,write,update}.scala`.

**Why this matters for IW-346 / Decision 1:**
- If we ship only `core/project.scala` from `core/`, an extracted tarball running `iw review-state validate <path>` will:
  1. set `IW_CORE_DIR="$INSTALL_DIR/core"` (per `iw-run:11`),
  2. walk `$INSTALL_DIR/core` finding only `project.scala`,
  3. exec `scala-cli run subcommand.scala project.scala -- args`,
  4. compile fail because the `review-state` subcommands import `iw.core.adapters.*` / `iw.core.model.*` / `iw.core.output.*`, which `project.scala` alone doesn't supply (it's a deps manifest, not a source).

The comment "(same as iw-run does)" is **stale**: `iw-run` no longer walks `core/`; since IW-344 it uses `--jar "$CORE_JAR"`. `commands/review-state.scala` was not updated.

### Verification (subcommand imports)

I confirmed the three `commands/review-state/*.scala` subcommand scripts depend on `iw.core.*` types, so they cannot run with `project.scala` alone — they need either the full `core/**/*.scala` walk (today's behaviour) or `--jar "$CORE_JAR"` (the post-IW-344 launcher convention).

### Stop point

Per Phase 1 instructions: **"If anything else surfaces a runtime read of `core/**/*.{scala,css,js}`, STOP and report it back to me — do NOT silently expand the tarball."**

I have not edited `scripts/package-release.sh`, `iw-run`, or `iw-bootstrap` yet. Awaiting Michal's decision on resolution.

### Decision options for Michal

The analysis section of `phase-01-context.md` lists three remediation paths if the audit surfaces a runtime read:

(a) **Ship the file from `core/` like we ship `project.scala`** — would require widening `package-release.sh` to also include all `core/**/*.scala` (excluding `test/`), reverting most of Decision 1's narrowing. Defeats the point of Decision 1.

(b) **Bundle it into `build/iw-core.jar`** — already done; the jar contains compiled classes. The fix is to make `commands/review-state.scala` use `--jar "$IW_CORE_JAR"` and pass `$IW_CORE_DIR/project.scala` (the deps manifest), exactly like `iw-run:565` does for project commands. This is small (a ~10-line change in one command file) and brings `review-state.scala` in line with the post-IW-344 convention.

(c) **Refactor the runtime to not need it** — e.g. inline the dispatch into the `validate|write|update` subcommand scripts so `review-state.scala` doesn't need to relaunch `scala-cli`. Bigger change.

**My recommendation:** option (b). It's the smallest change, restores the original intent ("same as iw-run does"), and fits Phase 1's spirit (every command path consumes `build/iw-core.jar` after the change). It's also strictly necessary — without (b) (or some equivalent), the shipped tarball will silently break `iw review-state` for installed users, which is exactly Risk 3 from the analysis.

If we go with (b), the change is to `commands/review-state.scala` and is in scope of Phase 1's "files to modify" only by extension (the issue explicitly lists `package-release.sh`, `iw-run`, `iw-bootstrap` as the expected set, and says "If the audit gate surfaces an additional file, raise it before completing the phase"). I want explicit go-ahead before touching `commands/review-state.scala`.

**Awaiting decision before continuing Phase 1 implementation.**

### Resolution (2026-04-27, approved by Michal)

Chosen approach: **consolidation** — fold the dispatcher and three subscripts into a single `commands/review-state.scala`. This is strictly better than option (b) above for three reasons:

1. The three subscripts (`validate.scala`, `write.scala`, `update.scala`) are internal helpers — never invoked directly by users.
2. The previous dispatcher shelled out via `scala-cli run` per invocation, paying a full warm-up. After consolidation, dispatch becomes a direct Scala function call within the single launched process.
3. **Audit-gate consequence:** the runtime `os.walk(coreDir)` at `commands/review-state.scala:67-71` disappears entirely. The consolidated script runs through the normal `iw-run:565` path (`scala-cli run … "$CORE_DIR/project.scala" --jar "$CORE_JAR"`) like every other command — clean under Decision 1.

**Files removed (`git rm`):**
- `commands/review-state/validate.scala`
- `commands/review-state/write.scala`
- `commands/review-state/update.scala`
- The empty `commands/review-state/` directory was implicitly removed.

**File modified:**
- `commands/review-state.scala` — now contains the dispatcher plus three private `def`s (`validate`, `write`, `update`) taking `Seq[String]`, with shared helpers (`extractFlag`, `extractRepeatedFlag`, `parseArrayField`, `commitIfRequested`) defined once. Imports merged: `iw.core.model.*`, `iw.core.adapters.*`, `iw.core.output.*`. Removed: `IW_COMMANDS_DIR` / `IW_CORE_DIR` env reads, `os.walk(coreDir)` over `core/**/*.scala`, the per-subcommand `scala-cli run` invocation. Each subcommand keeps its own `--help` handling (e.g. `iw review-state validate --help`).

**Verification:**
- `scala-cli compile commands/review-state.scala core/project.scala --jar "$CORE_JAR"` — clean (no errors, no warnings).
- `./iw review-state --help` — prints dispatcher help.
- `./iw review-state validate project-management/issues/IW-346/review-state.json` — exits 0 with "Review state is valid".
- `./iw review-state validate --help` — prints validate-specific help (per-subcommand `--help` preserved).

**Audit gate now clear:** no remaining runtime read of `core/**/*.{scala,css,js}` beyond `core/project.scala` (the deps manifest read by the three `iw-run:565,644,729` `scala-cli run … --jar "$CORE_JAR"` invocations explicitly permitted by Decision 2). Side benefit: `iw review-state <sub>` now skips a fork+exec of `scala-cli` per call.

## Phase 1: Implementation summary

**Date:** 2026-04-27

### `scripts/package-release.sh`

- Added a `strip_mill_ref` helper (matches `iw-run:50` parsing) and a `validate_jar` helper that fails on empty/missing/non-jar/zero-byte paths.
- Resolves `core.jar` and `dashboard.assembly` via `./mill --ticker false show <task>` at the top of the script, before any staging — so the script fails fast if Mill / build inputs are missing.
- Creates `build/` staging dir and `cp`s the resolved jars to literal names `build/iw-core.jar` and `build/iw-dashboard.jar` (the launcher does a literal path test on those names).
- Replaces the previous `core/` rsync (which copied all `*.scala`, `*.css`, `*.js` under `core/`) with a single-file `cp "$PROJECT_ROOT/core/project.scala" "$RELEASE_PACKAGE_DIR/core/project.scala"`. (Decision 1.)
- Verification tail widened: `head -30` listing of contents plus an explicit `tar -tzvf … | grep -E "iw-cli-${VERSION}/build/"` that fails the script if jars are missing from the tarball.
- No `./mill`, `.mill-version`, `build.mill`, `.bsp/`, `out/`, `dashboard/jvm/`, or `dashboard/frontend/` is added — confirmed via tarball inspection. (Decision 5.)

### `iw-run`

- Added a load-bearing comment block above `ensure_core_jar` documenting the three-tier resolution order (env → `$INSTALL_DIR/build/*.jar` → Mill) and the build-output convention (`out/` for Mill, `.scala-build/` for scala-cli, `build/` for releases).
- `ensure_core_jar` now: (1) honour `$IW_CORE_JAR` if it points to a readable `.jar`, else (2) use `$INSTALL_DIR/build/iw-core.jar` if present, else (3) drive Mill via `mill_jar_path core.jar`. Always exports `IW_CORE_JAR` so child scala-cli processes inherit it.
- `ensure_dashboard_jar` mirrors the same three-tier pattern over `iw-dashboard.jar` / `dashboard.assembly`.
- `bootstrap` now prints `iw-cli is ready (pre-built jars present at $INSTALL_DIR/build/)` when rung 2 hit, else the existing "Bootstrap complete" message.
- `mill_jar_path` got an additive guard: if `$INSTALL_DIR/mill` is not executable, it prints a clear `ERROR: pre-built jars not found at $INSTALL_DIR/build/ and Mill is not available` and exits before bash's "No such file or directory" surfaces. Does not change resolution order — only replaces the failure-mode UX when an extracted tarball lacks `build/` AND lacks `./mill` (which shouldn't happen with the new packaging, but the guard hardens us against malformed tarballs).
- The three `scala-cli run … "$CORE_DIR/project.scala" --jar "$CORE_JAR" …` invocations are unchanged (now at lines 604/683/768 due to comment expansion above; line content identical). Decision 2 honoured.

### `iw-bootstrap`

- Skipped the optional defensive presence check after `tar -xzf`. Rationale: the new `mill_jar_path` guard in `iw-run` already produces a clear error if an extracted tarball lacks both `build/` and `./mill`, which covers the same failure mode at the layer where it manifests. Adding a duplicate check in `iw-bootstrap` would be redundant.

### Verification (manual, four flows)

1. **Build a tarball locally.** `./scripts/package-release.sh 0.5.0-test` ran cleanly. `tar -tzf release/iw-cli-0.5.0-test.tar.gz | grep -E 'build/'` listed exactly: `iw-cli-0.5.0-test/build/`, `iw-cli-0.5.0-test/build/iw-core.jar` (1,118,844 bytes), `iw-cli-0.5.0-test/build/iw-dashboard.jar` (42,336,827 bytes). PASS.
2. **Tarball contains only `core/project.scala` from `core/`.** `tar -tzf … | grep core/` returned exactly two entries: `iw-cli-0.5.0-test/core/` and `iw-cli-0.5.0-test/core/project.scala`. No other `core/**/*.scala`, `core/**/*.css`, or `core/**/*.js`. PASS.
3. **Extract and run without Mill on PATH.** Extracted to `/tmp/tmp.fhOrSU0wMK/`, ran with `PATH=/usr/bin:/bin:$scala-cli:$java:$jq` (Mill hidden):
   - `./iw-run --bootstrap` → `iw-cli is ready (pre-built jars present at …/build/).` (no Mill invocation). PASS.
   - `./iw-run --list` → printed all commands. PASS.
   - `IW_PROJECT_DIR=/tmp ./iw-run version` → `iw-cli version 0.5.0`, exit 0. PASS.
4. **Dev-checkout regression.** From the iw-cli repo root with no `build/` dir present:
   - `./iw --list` → succeeds. `./iw version` → `iw-cli version 0.5.0`. No regression.
   - `IW_TRACE=1 ./iw --bootstrap` → printed `[iw-run] mill_jar_path core.jar` then `Bootstrap complete.` confirming rung-3 (Mill) was invoked. PASS.
5. **Env override wins over `build/iw-core.jar` (rung 1 > rung 2).** Created `/tmp/fake.jar` (empty), then `IW_CORE_JAR=/tmp/fake.jar ./iw-run version` from inside the extracted tarball → fell through scala-cli compilation with `Compilation failed`, exit 1, confirming the empty fake jar was preferred over the real `build/iw-core.jar`. (Note: the original spec phrasing "fails clean because it's not a real jar" assumed pre-rung-2 fall-through; the env override here only takes effect when the file exists, matching the original `[[ -f "$IW_CORE_JAR" ]]` guard. Behaviour is correct.) PASS.

All four flows pass. Audit gate clear. Acceptance Checklist all checked.

### Code review

- Iterations: 1 (warnings only; no critical issues)
- Review file: `review-phase-01-20260428-085454.md`
- Skills applied: code-review-style, code-review-scala3, code-review-security, code-review-architecture
- Iteration 1 fixes (4 warnings addressed):
  - Deduplicated commit logic in `update`: replaced inline block with `commitIfRequested(argList, issueId, inputPath)`.
  - Added tarball verification: `package-release.sh` now asserts `core/project.scala` is in the tarball alongside the existing `build/*.jar` checks.
  - Comment hygiene: lifted the "Some(Nil) signals …" comment above the `if clearMode` branch in `commands/review-state.scala`; dropped the fragile `iw-run:50` cross-reference in `scripts/package-release.sh:17`.
  - CLI consistency: changed all `ERROR:` to `Error:` in `mill_jar_path`'s new guard (4 lines) to match the surrounding `iw-run` convention.
- Deferred to follow-up issues / Phase 2 (recorded in review file):
  - `strip_mill_ref` deduplication into `scripts/lib/mill-helpers.sh` (shared between `iw-run` and `package-release.sh`).
  - Scala 3 idiom polish in `commands/review-state.scala` (`Option.when`, `sliding` → `zip`, `ArrayMergeMode` alias, args.toList consolidation, issue-ID resolution helper, rename `validate`/`write`/`update` → `runValidate`/etc.).
  - `--issue-id` validation via `IssueId.parse` (defense in depth).
  - `IW_CORE_JAR` trust comment.
  - Bash arrays for `$hook_files`/`$lib_files` in `iw-run` (pre-existing pattern, broader refactor).
  - `validate_jar` path-boundary check.
  - `Instant.now()` in `core/model/ReviewStateUpdater.scala` (pre-existing FCIS gap; reviewer's "update doesn't refresh last_updated" claim was disputed and verified incorrect at `ReviewStateUpdater.scala:69-72`).

### Files changed (git diff --name-status from baseline `d7b0da4`)

```
M	commands/review-state.scala
D	commands/review-state/update.scala
D	commands/review-state/validate.scala
D	commands/review-state/write.scala
M	iw-run
M	scripts/package-release.sh
```

(The three deletions plus the consolidated rewrite of `commands/review-state.scala` net to roughly a −70 line reduction across the four `commands/review-state*` files.)

