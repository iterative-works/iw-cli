---
generated_from: 4a18f26f78f3f6b2e4bbfdcae349eb7a9ff9dc7d
generated_at: 2026-04-19T08:18:00Z
branch: IW-344
issue_id: IW-344
phase: "1-3 (complete feature)"
files_analyzed:
  - iw-run
  - core/adapters/Process.scala
  - .iw/commands/test.scala
  - test/core-jar.bats
  - test/plugin-commands-describe.bats
  - test/plugin-commands-execute.bats
  - test/plugin-commands-list.bats
  - test/plugin-discovery.bats
  - test/project-commands-describe.bats
  - test/project-commands-execute.bats
  - test/project-commands-list.bats
---

# Review Packet: IW-344 — Pre-compile core into jar

## Goals

Every command invocation previously passed all `core/**/*.scala` files (132 source files) to `scala-cli` for compilation alongside the command file. While `scala-cli` caches aggressively, cold-start compilation was ~30 seconds per command, blocking fresh checkouts and branch switches. The pre-compiled artifact also prevents a future Mill-built dashboard from depending on core types.

This feature pre-compiles `core/` into a library jar (`build/iw-core.jar`) and injects it transparently into all command invocations via `--jar`, so command authors never need to add directives or know about the jar. A spike confirmed the recipe: ~27x cold-start speedup (29.6s to 1.1s) with no loss of dependency resolution, by pairing `--jar iw-core.jar` with `core/project.scala` as a source file (so `scala-cli` can still honour `//> using dep` directives).

Key objectives:
- Reduce cold-start command execution from ~30s to ~1s
- Detect stale core sources automatically and rebuild the jar without developer intervention
- Allow the test harness to pre-build the jar once per suite run and inject its path via `IW_CORE_JAR`, eliminating per-test 30s rebuild cost
- Leave command authoring unchanged: no new directives, no new tools

## Scenarios

- [ ] Running any command after a fresh checkout (`./iw version`, `./iw status`) builds the jar on first invocation and executes successfully
- [ ] When a core source file is newer than the jar, the next command run prints "Rebuilding core jar..." on stderr and rebuilds before executing
- [ ] When the jar is already fresh, commands execute silently (no rebuild message)
- [ ] `./iw --bootstrap` (or `./iw-run --bootstrap`) always rebuilds the jar unconditionally
- [ ] Setting `IW_CORE_JAR` to a custom path causes all jar operations (build and execute) to use that path
- [ ] Project commands (`.iw/commands/*.scala`) import `iw.core.adapters.*` and `iw.core.model.*` correctly via the jar
- [ ] Plugin commands execute with hooks, lib files, and `IW_HOOK_CLASSES` preserved correctly via jar mode
- [ ] Shared commands (e.g. `status`) that import core types execute correctly via jar mode
- [ ] `./iw ./test e2e` pre-builds the jar once and injects `IW_CORE_JAR` into all BATS child processes; no per-test rebuild
- [ ] BATS tests that copy `core/` into a temp install do not clobber the shared pre-built jar due to mtime drift

## Entry Points

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `iw-run` | `build_core_jar()` | Builds the jar via `scala-cli --power package --library`; the core of the feature |
| `iw-run` | `core_jar_stale()` | mtime check that drives auto-rebuild; uses `find -newer` with short-circuit |
| `iw-run` | `ensure_core_jar()` | Called once at the top of `execute_command()` before branch selection; the runtime hook |
| `iw-run` | `execute_command()` (lines 515-710) | Contains the three rewritten `exec scala-cli run` branches (project / plugin / shared) |
| `iw-run` | `bootstrap()` | Explicit pre-build entry point; calls `build_core_jar` unconditionally |
| `.iw/commands/test.scala` | `runE2ETests()` | Pre-builds jar via `--bootstrap`, injects `IW_CORE_JAR` into each BATS child via `runStreaming(env = ...)` |
| `core/adapters/Process.scala` | `runStreaming()` | Extended with `env: Map[String, String] = Map.empty`; forwards to `os.proc(...).call(env = env)` |
| `test/core-jar.bats` | all 6 tests | New test file covering the complete jar lifecycle; the canonical E2E test for this feature |

## Diagrams

### Component relationships

```
iw-run
├── Variables (top of script)
│   ├── CORE_JAR = ${IW_CORE_JAR:-$INSTALL_DIR/build/iw-core.jar}
│   └── export IW_CORE_JAR=$CORE_JAR   (propagated to children)
│
├── build_core_jar()
│   ├── find $CORE_DIR -name "*.scala" -not -path "*/test/*"
│   └── scala-cli --power package --library -o $CORE_JAR -f $core_files
│
├── core_jar_stale()
│   ├── [ ! -f $CORE_JAR ]  → true (stale)
│   └── find $CORE_DIR -name "*.scala" -newer $CORE_JAR  → true if any found
│
├── ensure_core_jar()          ← called at top of execute_command()
│   └── core_jar_stale() → build_core_jar()
│
├── bootstrap()                ← entry: iw-run --bootstrap
│   └── build_core_jar()  (unconditional)
│
└── execute_command()
    ├── ensure_core_jar()      ← single call, before branch selection
    ├── project branch  → exec scala-cli run $cmd_file $CORE_DIR/project.scala --jar $CORE_JAR
    ├── plugin branch   → exec scala-cli run $cmd_file $hook_files $lib_files $CORE_DIR/project.scala --jar $CORE_JAR
    └── shared branch   → exec scala-cli run $cmd_file $hook_files $CORE_DIR/project.scala --jar $CORE_JAR
```

### Why `project.scala` must be a source file

`scala-cli --power package --library` produces a thin jar (compiled `.class`+`.tasty`, no embedded dep metadata). When `scala-cli run --jar iw-core.jar` is used alone, dependency resolution fails (`Not found: os`, `Not found: config`, etc.). Passing `core/project.scala` alongside `--jar` lets `scala-cli` read its `//> using dep` directives and resolve transitive dependencies correctly. This is why all three invocation branches include both `--jar "$CORE_JAR"` and `"$CORE_DIR/project.scala"`.

### Test infrastructure flow

```
./iw ./test e2e
  └── test.scala: runE2ETests()
      ├── ./iw-run --bootstrap        (builds build/iw-core.jar once; 10-min timeout)
      └── for each .bats file:
          └── bats <file>  env={"IW_CORE_JAR": "<repo>/build/iw-core.jar"}
              └── BATS setup():
                  ├── cp -r core/ .iw-install/core/         (sources for ensure_core_jar to rebuild from)
                  ├── export IW_CORE_JAR=<repo>/build/iw-core.jar   (shared pre-built jar)
                  └── touch -r $IW_CORE_JAR .iw-install/core/**/*.scala  (prevent stale-check trigger)
```

The `touch -r` step is the key correctness detail: copying sources into a temp dir updates their mtimes to "now", which would make them newer than the pre-built jar and trigger `core_jar_stale` → `build_core_jar`, overwriting the shared jar mid-suite. The mtime sync prevents this.

`test/core-jar.bats` deliberately uses a per-test `$TEST_DIR/...` jar path instead of the shared one, because scenarios 2 and 6 mutate the jar and must not contaminate the suite.

## Test Summary

### Unit tests
No Scala unit tests added (the changed code is bash and the test harness wiring). The `ProcessAdapter.runStreaming` signature extension is covered implicitly by the compile check.

### Integration / compile checks
- `./iw ./test compile` — all 35 commands compiled successfully against jar mode
- `./iw ./test unit` — green (no regressions in existing Scala tests)

### E2E tests (BATS)

| File | Type | Tests | Status |
|------|------|-------|--------|
| `test/core-jar.bats` | E2E (new) | 6 | Pass |
| `test/project-commands-execute.bats` | E2E (regression sentinel) | 10 | Pass |
| `test/plugin-commands-execute.bats` | E2E (regression) | 4 | Pass |
| `test/project-commands-describe.bats` | E2E | unchanged count | Pass |
| `test/project-commands-list.bats` | E2E | unchanged count | Pass |
| `test/plugin-commands-describe.bats` | E2E | unchanged count | Pass |
| `test/plugin-commands-list.bats` | E2E | unchanged count | Pass |
| `test/plugin-discovery.bats` | E2E | unchanged count | Pass |

6 pre-existing, unrelated failures remain in `phase-merge.bats`, `start-prompt.bats`, and `dashboard.bats` (not caused by this feature).

### What `test/core-jar.bats` verifies

1. Missing jar triggers auto-rebuild on first command execution
2. Stale jar (core source newer than jar) triggers rebuild; jar mtime advances
3. Fresh jar is silent on second invocation
4. `IW_CORE_JAR` override causes `--bootstrap` to write to the custom path; default path untouched
5. `./iw-run --bootstrap` produces the jar at the configured default location
6. `build_core_jar` with `-f` overwrites an existing jar cleanly (regression guard for the Phase-2 fix)

## Files Changed

### Core changes

**`iw-run`** — The primary implementation file. Changes span three areas:

1. Variables (lines 14-15): adds `CORE_JAR` with `IW_CORE_JAR` env override and re-exports for child processes.
2. New functions (lines 27-76): `build_core_jar()`, `core_jar_stale()`, `ensure_core_jar()`, rewritten `bootstrap()`.
3. `execute_command()` (lines 514-710): `ensure_core_jar` call at top; three `exec scala-cli run` branches rewritten from `$core_files` source-list to `--jar "$CORE_JAR" "$CORE_DIR/project.scala"`; three `core_files=$(find ...)` blocks removed.

Notable Phase-2 bug fix applied: `build_core_jar` passes `-f` to `scala-cli package` to allow overwriting an existing jar. Without this, mtime-triggered rebuilds fail silently with an "already exists" error.

**`core/adapters/Process.scala`** — Minimal extension: `runStreaming` gains a trailing `env: Map[String, String] = Map.empty` parameter forwarded to `os.proc(...).call(env = env)`. All existing call sites remain source-compatible. PURPOSE header updated to list all four public operations.

**`.iw/commands/test.scala`** — `runE2ETests()` extended with a pre-build step before the BATS loop (10-minute timeout, aborts on failure) and passes `IW_CORE_JAR` to each `bats` invocation via `runStreaming(env = ...)`.

### New test file

**`test/core-jar.bats`** — 6 E2E scenarios for the jar lifecycle. Uses per-test `IW_CORE_JAR` inside `$TEST_DIR` to isolate mutation tests from the shared suite jar.

### Modified test setup (7 files)

Each of the following BATS files gained an `IW_CORE_JAR` + `touch -r` block immediately after `export IW_CORE_DIR=...`:

- `test/plugin-commands-describe.bats`
- `test/plugin-commands-execute.bats`
- `test/plugin-commands-list.bats`
- `test/plugin-discovery.bats`
- `test/project-commands-describe.bats`
- `test/project-commands-execute.bats`
- `test/project-commands-list.bats`

The block is identical across all seven files (candidate for a future BATS helper extraction; deliberately left as-is to stay within phase scope).

### Project management files (not production code)

`project-management/issues/IW-344/` — analysis, tasks, phase context files, implementation log, review state, and per-phase review files. No review needed.

## Known Follow-ups (not blocking)

- Extract the duplicated 10-line `IW_CORE_JAR` + `touch -r` setup block from the seven BATS files into `test/helpers/core-jar-setup.bash`
- Add a guard in `ProcessAdapter.runStreaming` validating env keys against injection (low priority; current callers all use `os.Path`-derived values)
- The 6 pre-existing unrelated BATS failures in `phase-merge.bats`, `start-prompt.bats`, `dashboard.bats` are tracked separately
