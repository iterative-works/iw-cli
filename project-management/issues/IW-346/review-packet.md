---
generated_from: cef2a7465e3ebc95ad3f912b0110fabb180dc291
generated_at: 2026-04-28T14:26:48Z
branch: IW-346
issue_id: IW-346
phase: "1+2"
files_analyzed:
  - scripts/package-release.sh
  - iw-run
  - .github/workflows/release.yml
  - test/bootstrap.bats
  - RELEASE.md
  - commands/review-state.scala
---

# Review Packet: IW-346 — Release packaging for pre-built artifacts

## Goals

This feature moves the iw-cli build step from install-time to release-time, shipping pre-built jars (`build/iw-core.jar`, `build/iw-dashboard.jar`) inside the release tarball so that extracted installs need only `scala-cli` and a JRE — no Mill, no Node, no Yarn, no Web Awesome registry token.

Key objectives:

- Produce a self-contained tarball containing `iw-run`, `iw-bootstrap`, `commands/`, `core/project.scala` (deps manifest only), and two pre-built jars at `build/iw-core.jar` and `build/iw-dashboard.jar`.
- Teach `iw-run` to resolve jars via a three-tier order: `$IW_CORE_JAR` env override → `$INSTALL_DIR/build/*.jar` file presence → Mill query (dev checkout only). Installed tarballs always use tier 2; dev checkouts fall through to tier 3.
- Make `iw-run --bootstrap` a verify-only step on installed tarballs (no Mill invocation) while preserving the Mill-driven compile path in dev checkouts.
- Update CI (`release.yml`) to build the jars on `v*` tag push using the `iw-cli-ci` container, which has Mill, Node, Yarn, and accepts `WEBAWESOME_NPM_TOKEN`.
- Lock in the "no Mill needed at install time" contract with BATS regression tests.
- Document the read-only-tarball contract in `RELEASE.md` so future maintainers cannot accidentally reintroduce a build-time dependency at install sites.

**Scope note:** a non-obvious blocker was discovered and resolved in Phase 1. The audit gate found that `commands/review-state.scala` was performing a runtime `os.walk(coreDir)` over `core/**/*.scala` — a stale pattern from before IW-344. Rather than widening the tarball to ship core sources, the three `commands/review-state/*.scala` sub-scripts were folded into a single consolidated `commands/review-state.scala`. This makes the command follow the same `--jar "$CORE_JAR"` path as every other command and eliminates the `os.walk` at runtime.

## Scenarios

- [ ] `scripts/package-release.sh <version>` completes and produces a tarball containing `build/iw-core.jar` and `build/iw-dashboard.jar`, both non-empty.
- [ ] The same tarball contains `core/project.scala` and does NOT contain any other `core/**/*.scala`, `core/**/*.css`, or `core/**/*.js`.
- [ ] The tarball does NOT contain `./mill`, `.mill-version`, or `build.mill`.
- [ ] An extracted tarball running `./iw-run --bootstrap` without Mill on PATH succeeds and prints `pre-built jars present`.
- [ ] An extracted tarball running `./iw-run --list` without Mill on PATH succeeds and lists available commands.
- [ ] `$IW_CORE_JAR=/path/to/explicit.jar` env override wins over `$INSTALL_DIR/build/iw-core.jar` (rung 1 priority preserved).
- [ ] In a dev checkout (no `build/` directory), `./iw --bootstrap` still drives Mill via rung 3 — no regression.
- [ ] `release.yml` runs on `self-hosted` with `ghcr.io/iterative-works/iw-cli-ci:latest` container and `WEBAWESOME_NPM_TOKEN` in env of the build step.
- [ ] BATS structure test asserts `build/iw-core.jar` and `build/iw-dashboard.jar` are present and non-empty; does not assert `core/Config.scala`.
- [ ] BATS bootstrap test asserts `pre-built jars present` (rung-2 path), not the legacy `Bootstrap complete` string.
- [ ] BATS regression test: a fail-loudly `mill` stub is installed at the head of PATH; after `./iw-run --list` and `./iw-run --bootstrap` on an extracted tarball, the mill stub marker file does not exist.
- [ ] `iw review-state validate|write|update` works correctly after the consolidation (no `os.walk` at runtime).

## Entry Points

| File | Function/Section | Why Start Here |
|------|-----------------|----------------|
| `scripts/package-release.sh` | `strip_mill_ref`, `validate_jar`, main body | Produces the artifact; read this first to understand what goes into the tarball |
| `iw-run` | `ensure_core_jar`, `ensure_dashboard_jar` (lines 88-117) | Three-tier jar resolution — the load-bearing change for installed vs dev modes |
| `iw-run` | `mill_jar_path` (lines 39-69) | Error-path guard when neither pre-built jars nor Mill are available |
| `iw-run` | `bootstrap` (lines 122-130) | Verify-only on installed tarballs; confirm rung-2 message path |
| `.github/workflows/release.yml` | `Build release tarball` step | CI change — container switch and `WEBAWESOME_NPM_TOKEN` injection |
| `test/bootstrap.bats` | `"iw-run works without Mill on PATH"` test (lines 104-138) | The regression gate; stub-based design is worth reviewing |
| `RELEASE.md` | "Tarball Contract" section (lines 120-134) | Documents the read-only-artifact guarantee for future maintainers |
| `commands/review-state.scala` | Consolidated dispatch (replaces three sub-scripts) | Audit-gate finding; confirm `os.walk` is gone and `--jar "$CORE_JAR"` path is used |

## Diagrams

### Jar Resolution Order (installed tarball vs dev checkout)

```
iw-run invoke
      │
      ▼
ensure_core_jar()
      │
      ├─ [1] $IW_CORE_JAR set and points to readable .jar?
      │         YES → CORE_JAR=$IW_CORE_JAR  (env override wins)
      │
      ├─ [2] $INSTALL_DIR/build/iw-core.jar exists?
      │         YES → CORE_JAR=$INSTALL_DIR/build/iw-core.jar
      │               (release tarball path — always fires for installed copies)
      │
      └─ [3] mill_jar_path core.jar
                (dev checkout only; Mill must be present at $INSTALL_DIR/mill)
                  → builds/returns out/core/jar.dest/out.jar
```

### Tarball Production Flow (CI + local)

```
v* tag push
      │
      ▼
release.yml (self-hosted, iw-cli-ci container)
      │
      ├── Run all tests (./iw ./test)
      │
      ├── Extract version from tag
      │
      ├── Build release tarball
      │     scripts/package-release.sh $VERSION
      │           │
      │           ├── ./mill show core.jar        → CORE_JAR_PATH
      │           ├── ./mill show dashboard.assembly → DASHBOARD_JAR_PATH
      │           ├── validate_jar (fail fast on empty/missing)
      │           ├── Stage: iw-run, iw-bootstrap, VERSION
      │           ├── Stage: commands/**/*.scala
      │           ├── Stage: core/project.scala only (Decision 1)
      │           ├── Stage: build/iw-core.jar, build/iw-dashboard.jar
      │           └── tar -czf + verify (grep build/, grep core/project.scala)
      │
      ├── Upload to versioned release (gh release upload)
      └── Update vlatest release (force-move tag + upload iw-cli-latest.tar.gz)
```

### review-state Command Consolidation

```
Before (Phase 1 audit finding):
  iw review-state validate args
        │
        └── commands/review-state.scala: os.walk(coreDir) → all *.scala
              └── scala-cli run commands/review-state/validate.scala <all core sources>

After (consolidated):
  iw review-state validate args
        │
        └── iw-run: scala-cli run commands/review-state.scala project.scala --jar $CORE_JAR
              └── review-state.scala: direct Scala function call → runValidate(args)
```

## Test Summary

| # | Test Name | Type | Status |
|---|-----------|------|--------|
| 1 | `iw-run lists commands from installation directory` | BATS / integration | Pass |
| 2 | `iw-run --bootstrap pre-compiles successfully` | BATS / integration | Pass |
| 3 | `iw-run executes commands from installation directory` | BATS / integration | Pass |
| 4 | `release package contains required structure` | BATS / integration | Pass |
| 5 | `iw-run works without Mill on PATH (bundled jars only)` | BATS / regression | Pass |

**Local verification (2026-04-28):** all 5 tests pass. Test file: `test/bootstrap.bats`.

**Key design note on test 5:** an early version narrowed `PATH` to a curated allow-list, which was fragile (the launcher uses ~13 external tools; the PATH narrowing risked tautology if `mill` happened to live in `/usr/bin`). The final design installs a fail-loudly `mill` stub at the head of `PATH`. The stub writes a marker file and exits non-zero on any invocation. The test asserts `command -v mill` resolves to the stub (precondition), then runs the launcher and asserts the marker file does NOT exist. This directly tests "launcher must not invoke mill when `build/*.jar` is present" rather than relying on Mill's absence.

**BATS version-drift fix:** the entire test file previously hardcoded `iw-cli-0.1.0-dev` as version, tarball name, and package directory — stale against `VERSION=0.5.0`. Phase 2 replaced all literals with `VERSION`, `TARBALL_NAME`, and `PACKAGE_DIR` variables derived from `$(cat VERSION)`.

**Pending verification gate:** a release-candidate tag dry run (`v0.5.1-rc1` or `workflow_dispatch`) must run to confirm `release.yml` produces a working tarball end-to-end before the first production release with the new format.

**Code review summary (two iterations, one per phase):**

- Phase 1 review: 0 critical / 4 warnings / several suggestions. All 4 warnings addressed (commit-logic deduplication in `review-state.scala`, tarball verification for `core/project.scala`, comment hygiene, `Error:` capitalization consistency in `iw-run`). Remaining suggestions deferred as follow-up items.
- Phase 2 review: 0 critical / 3 warnings / 6 suggestions. All 3 warnings addressed (regression-test redesign to stub-based approach, removal of manual `PATH` restore, defensive comment on `WEBAWESOME_NPM_TOKEN` env block). 2 of 15 acceptance items explicitly pending (CI green run + RC dry-run — both are post-merge verification gates).

## Files Changed

| File | Change | Notes |
|------|--------|-------|
| `scripts/package-release.sh` | Modified | Adds Mill jar-build step, `build/` staging, narrows `core/` copy to `project.scala` only |
| `iw-run` | Modified | Adds three-tier jar resolution, `mill_jar_path` guard, `bootstrap` rung-2 message |
| `commands/review-state.scala` | Modified (consolidated) | Folds three sub-scripts into one; removes `os.walk` over core sources |
| `commands/review-state/validate.scala` | Deleted | Merged into `commands/review-state.scala` |
| `commands/review-state/update.scala` | Deleted | Merged into `commands/review-state.scala` |
| `commands/review-state/write.scala` | Deleted | Merged into `commands/review-state.scala` |
| `.github/workflows/release.yml` | Modified | Switches to `self-hosted` + `iw-cli-ci` container; removes manual toolchain install steps; adds `WEBAWESOME_NPM_TOKEN` |
| `test/bootstrap.bats` | Modified | Updates structural assertions; fixes version-drift; adds Mill-stub regression test |
| `RELEASE.md` | Modified | Updates tarball description, adds "Tarball Contract" section, updates troubleshooting |

<details>
<summary>Project-management files (not code-reviewed)</summary>

- `project-management/issues/IW-346/analysis.md`
- `project-management/issues/IW-346/tasks.md`
- `project-management/issues/IW-346/phase-01-context.md`
- `project-management/issues/IW-346/phase-02-context.md`
- `project-management/issues/IW-346/phase-01-tasks.md`
- `project-management/issues/IW-346/phase-02-tasks.md`
- `project-management/issues/IW-346/implementation-log.md`
- `project-management/issues/IW-346/review-phase-01-20260428-085454.md`
- `project-management/issues/IW-346/review-phase-02-20260428-145331.md`
- `project-management/issues/IW-346/review-state.json`

</details>
