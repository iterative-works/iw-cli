# Technical Analysis: Layer 0 -- Pre-compile core into jar and update iw-run launcher

**Issue:** IW-344
**Created:** 2026-04-15
**Status:** Draft

## Problem Statement

Every command invocation passes all `core/**/*.scala` files to scala-cli for compilation alongside the command file. While scala-cli caches aggressively, the initial compile per command is slow, and this model prevents a future Mill-built dashboard from depending on core types as a compiled artifact.

The fix is to pre-compile `core/` into a jar and have `iw-run` inject it via `--jar` transparently, so command authors never need to add directives.

## Proposed Solution

### High-Level Approach

Add a `build_core_jar()` function to `iw-run` that runs `scala-cli package` on the core sources (excluding tests) to produce `build/iw-core.jar`. This jar is a library jar (not an assembly/fat jar) -- it contains only the compiled core classes. The `--jar` flag in scala-cli tells it to add the jar to the classpath, and scala-cli still handles dependency resolution from the `//> using dep` directives in the command files (or transitively from the jar's directives if embedded).

The three invocation paths in `execute_command()` (project commands, plugin commands, shared commands) all switch from passing `$core_files` as source files to passing `--jar build/iw-core.jar`. The `bootstrap()` function is updated to build the jar as its primary action. A dev-mode mtime check warns or auto-rebuilds when core sources are newer than the jar.

### Why This Approach

Using `scala-cli package --library` is the simplest path -- it stays within the existing scala-cli toolchain, requires no new build tools, and produces a jar that scala-cli can consume via `--jar`. The alternative of adding Mill for core would be premature and adds complexity that belongs in a later issue.

## Architecture Design

This issue is entirely within the **Build Infrastructure** layer -- there are no domain model, application, or presentation layer changes. The work is concentrated in bash scripts.

### Infrastructure Layer (Build Scripts)

**Components:**
- `build_core_jar()` function in `iw-run` -- builds `build/iw-core.jar` from core sources
- Mtime check logic in `iw-run` -- detects stale jar and triggers rebuild or warning
- Updated `execute_command()` -- three invocation sites switch from `$core_files` to `--jar`
- Updated `bootstrap()` -- calls `build_core_jar()` instead of compiling version.scala with core files
- Updated `scripts/package-release.sh` (noted as separate issue, but may need a stub/comment)

**Responsibilities:**
- Building the core jar from source, excluding test files
- Detecting when the jar is stale relative to source files
- Injecting the jar into all command invocations transparently
- Ensuring the jar includes dependency metadata so scala-cli resolves deps correctly

**Estimated Effort:** 4-6 hours
**Complexity:** Moderate

### Test Layer

**Components:**
- New or updated BATS tests for jar build/rebuild behavior
- Updated E2E test setup functions that currently copy core sources
- Bootstrap test updates

**Responsibilities:**
- Verifying jar is built correctly and commands execute against it
- Verifying mtime check triggers rebuild
- Verifying existing command execution still works (regression)

**Estimated Effort:** 3-5 hours
**Complexity:** Moderate

## Technical Decisions

### Patterns

- `scala-cli package --library` to produce a library jar (not assembly)
- `find` with `-newer` flag for mtime comparison (simple, no extra tooling)
- Auto-rebuild in dev mode (vs. warn-only) -- needs decision (see CLARIFY below)

### Technology Choices

- **Build tool**: `scala-cli package` (already available, no new dependencies)
- **Jar location**: `build/iw-core.jar` (to be added to `.gitignore`)
- **Mtime check**: bash `find` with `-newer` flag or `stat` comparison

### Integration Points

- `iw-run` `execute_command()` -- three sites that build `core_files` and pass to scala-cli
- `iw-run` `bootstrap()` -- entry point for pre-compilation
- `scripts/package-release.sh` -- currently copies core source; will eventually need to include the jar instead (separate issue per description)
- E2E test setup functions -- many tests copy core sources into temp dirs

## Technical Risks & Uncertainties

### RESOLVED: scala-cli --jar and dependency resolution

**Spike conducted on 2026-04-16.** Resolution: **Option B** — pass `core/project.scala` alongside `--jar`.

**Findings:**
- `scala-cli --power package --library -o build/iw-core.jar <core files>` produces a 1.6MB library jar (compiled `.class` + `.tasty` files, no embedded dependency metadata — MANIFEST.MF only contains `Manifest-Version: 1.0`).
- `--jar build/iw-core.jar` alone causes commands to fail compilation: `Not found: os`, `Not found: config`, etc. scala-cli does not resolve transitive dependencies from the jar.
- `--jar build/iw-core.jar core/project.scala` works end-to-end. scala-cli picks up the `//> using dep` directives from `project.scala` (which contains only directives, no code), and the compiled classes come from the jar.

**Performance (measured on `commands/version.scala`):**

| Approach              | Cold compile | Warm (BSP) |
|-----------------------|--------------|------------|
| Old (132 source files) | 29.6s        | 1.2s       |
| New (--jar + project.scala) | **1.1s** | **0.8s**   |

Cold runs are **~27x faster**. This is the most significant user-facing win since branch switches, fresh clones, and `.scala-build/` clears all trigger cold rebuilds. The core-heavy `commands/status.scala` confirmed end-to-end output correctness (JSON output matches the source-file mode).

**Implication for `iw-run`:** All three invocation sites in `execute_command()` must include `"$CORE_DIR/project.scala"` as a source alongside `--jar "$CORE_JAR"`. Command authors still never touch `project.scala` — `iw-run` injects it transparently, preserving the "no directives for command authors" constraint.

### RESOLVED: Dev-mode stale jar behavior

**Decision (2026-04-17): Option C — auto-rebuild with visible message, no source-file fallback.**

- When the jar is missing or stale (any `core/**/*.scala` newer than the jar), `iw-run` prints "Rebuilding core jar..." to stderr and rebuilds before executing the command.
- No fallback to source-file mode. The jar is always required; if it doesn't exist, it gets built on first run.
- `./iw --bootstrap` still works as an explicit pre-build entry point.

**Rationale:** Keeping source-file fallback would make the slow path the default for anyone who doesn't know about `--bootstrap`. Auto-rebuild with a message gives transparency without requiring manual steps.

### RESOLVED: E2E test strategy for jar-based execution

**Decision (2026-04-17): Option C — `IW_CORE_JAR` env var override, pre-built once per test run.**

- `iw-run` checks `IW_CORE_JAR` env var first; if unset, defaults to `$INSTALL_DIR/build/iw-core.jar`.
- `test.scala` builds the jar once at the start of the test run, sets `IW_CORE_JAR` to point at it.
- Individual BATS tests inherit the env var — no per-test jar building needed.
- Tests that copy `iw-run` into temp dirs can point `IW_CORE_JAR` at the pre-built jar location.

**Rationale:** Since there's no source-file fallback, tests must use the jar path. The env var gives the test harness control over jar location without hardcoding paths, and the one-time build cost (~30s) is amortized across all test files.

## Total Estimates

**Per-Layer Breakdown:**
- Infrastructure (Build Scripts): 4-6 hours
- Tests: 3-5 hours

**Total Range:** 6-9 hours (revised down from 7-11 after spike)

**Confidence:** Medium-High (spike resolved the main technical unknown)

**Reasoning:**
- Spike confirmed the jar + `project.scala` recipe works and delivers ~27x cold-start speedup
- The `iw-run` changes are mechanically straightforward (three invocation sites plus `build_core_jar()` function)
- Test migration is still the bulk of remaining uncertainty -- many BATS tests copy core sources, and need to either pre-build the jar or keep source-file support as a fallback
- Risk 1 (dep resolution) is eliminated; Risk 3 (fat jar) is also eliminated -- the library jar is 1.6MB with no bundled deps

## Testing Strategy

### Per-Layer Testing

**Infrastructure (Build Scripts):**
- E2E: jar is built by `iw --bootstrap`
- E2E: commands execute correctly against the jar (version, doctor, project commands)
- E2E: mtime check detects stale jar and triggers rebuild/warning
- E2E: missing jar triggers build (or error with helpful message)
- Regression: all existing BATS tests pass with jar-based execution

**Test Data Strategy:**
- Pre-build jar once per test run (in test.scala or a shared setup)
- Individual BATS tests reference the pre-built jar via env var

**Regression Coverage:**
- All existing BATS test files must pass -- they currently exercise command execution with source files
- The `project-commands-execute.bats` test that imports core library types is the key regression indicator

## Deployment Considerations

### Database Changes
None.

### Configuration Changes
- `build/` directory to be added to `.gitignore`
- No new env vars required for normal operation (possibly `IW_CORE_JAR` for test override)

### Rollout Strategy
- This is a dev-tooling change; merged to main, all developers get it on next pull
- `iw --bootstrap` must be run once after pulling the change

### Rollback Plan
- Revert the `iw-run` changes; source-file mode is the current behavior
- No data migration concerns

## Dependencies

### Prerequisites
- Verify `scala-cli package --library` produces a jar consumable by `scala-cli run --jar` (quick manual test, ~15 min)

### Layer Dependencies
- Build script changes must come before test updates
- Test updates depend on knowing whether fallback mode exists

### External Blockers
- None

## Risks & Mitigations

### ~~Risk 1: scala-cli --jar doesn't resolve transitive dependencies from library jar~~ RESOLVED
Confirmed via spike (2026-04-16). Adopted Option B: pass `core/project.scala` alongside `--jar`.

### Risk 2: E2E test migration takes longer than expected
**Likelihood:** Medium
**Impact:** Low (tests still work in source-file fallback mode)
**Mitigation:** Implement dual mode in iw-run so tests can be migrated incrementally.

### ~~Risk 3: scala-cli package --library produces a fat jar with all dependencies bundled~~ RESOLVED
Spike confirmed `--library` produces a thin 1.6MB jar with only compiled core classes.

### Risk 4 (new): Jar rebuild cost blocks developers editing core/
**Likelihood:** High
**Impact:** Low-Medium
**Mitigation:** Initial `scala-cli package` of core takes ~30s cold. The mtime-check/auto-rebuild decision (CLARIFY) directly determines how often developers pay this cost. Auto-rebuild with a visible message is the leading option.

## Implementation Sequence

**Recommended Layer Order:**

1. ~~**Spike**~~ ✅ Done 2026-04-16 -- confirmed Option B (jar + project.scala) works with ~27x cold-start speedup
2. **Infrastructure: build_core_jar() and mtime check** -- add the jar build function to iw-run
3. **Infrastructure: update execute_command()** -- switch three invocation sites from `$core_files` to `--jar "$CORE_JAR" "$CORE_DIR/project.scala"`
4. **Infrastructure: update bootstrap()** -- integrate jar build into bootstrap flow; replace existing `scala-cli compile version.scala` with `build_core_jar`
5. **Tests: update E2E tests** -- adapt test setup to jar-based execution

**Ordering Rationale:**
- The build function must exist before `execute_command()` can reference it
- Tests come last because they validate the final behavior

**Command recipe for `execute_command()`:**
```bash
scala-cli run -q --suppress-outdated-dependency-warning \
    "$cmd_file" $hook_files "$CORE_DIR/project.scala" \
    --jar "$CORE_JAR" -- "$@"
```
