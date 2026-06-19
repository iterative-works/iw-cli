---
generated_from: 2d8c8491064dba5dea2df689f03d0c5f66b59b47
generated_at: 2026-06-17T15:36:37Z
branch: IW-382
issue_id: IW-382
phase: "1+2 (complete feature)"
files_analyzed:
  - core/model/CleanupAction.scala
  - core/model/BuildToolCleanup.scala
  - core/model/Config.scala
  - core/model/Constants.scala
  - core/commands/Rm.scala
  - core/commands/BuildToolCleanupRunner.scala
  - core/commands/CommandEnv.scala
  - core/commands/LiveCommandEnv.scala
  - core/adapters/Process.scala
  - core/test/RmHarnessTest.scala
  - core/test/BuildToolCleanupTest.scala
  - core/test/ConfigTest.scala
  - core/test/fixtures/FakeCommandEnv.scala
  - test/rm.bats
---

# Review Packet: IW-382 — Cleanup hook on worktree removal

## Goals

`iw rm` previously removed a worktree directory while leaving project-spawned
processes running. This produced orphaned Mill servers, Bloop daemons, and
docker-compose stacks that accumulated across worktrees — observed in production
as 14 orphaned processes consuming ~2775% CPU and ~50% RAM.

IW-382 introduces a clean teardown contract so `iw rm` reclaims daemon
resources without requiring a manual shutdown ritual.

Key objectives:

- **Project-hook path (Phase 1):** Projects can drop a `*.hook-rm.scala` file
  exposing a `CleanupAction`. `iw rm` discovers the hook reflectively (reusing
  the existing `HookDiscovery` pass from #331), runs it after safety checks but
  before directory removal, surfaces warnings, and aborts (preserves the
  worktree) if a hook throws.
- **Built-in teardown (Phase 2):** A `BuildToolCleanup` built-in provides
  sensible defaults without any project hook: detect `out/mill-daemon/` →
  `mill --no-server shutdown`; detect `.bloop/` or `.scala-build/` →
  `bloop exit`; detect `docker-compose.yml` → `docker compose down`. All
  best-effort, gated on `commandExists`, running in the worktree's own cwd.
- **Opt-out:** `cleanup.builtin = false` in `.iw/config.conf` disables the
  built-in. Default configs are byte-identically unchanged.
- **FCIS boundary maintained:** The pure decision (`BuildToolCleanup.detect`)
  lives in the model; effectful execution goes through `CommandEnv` capabilities
  so everything is harness-testable without spawning real subprocesses.


## Scenarios

Phase 1 — project-hook path:

- [ ] No cleanup hooks installed: removal proceeds exactly as before
- [ ] Single hook returning `Nil`: removal proceeds; hook ran before removal
- [ ] Single hook returning warnings: warnings printed before "Removing worktree…"; removal proceeds
- [ ] Single hook throwing: error printed, `worktree.remove` NOT called, `CommandResult.error`
- [ ] Multiple hooks: run in declared order; all warnings aggregate and print before removal
- [ ] First hook throws: second hook never runs; removal skipped

Phase 2 — built-in teardown:

- [ ] Markers present and tools installed: teardowns run in worktree cwd (not iw's cwd), in Mill → Bloop → docker order, before removal
- [ ] Tool absent (marker present): silent skip, no warning, removal proceeds
- [ ] Tool present, non-zero exit: warning surfaced, removal proceeds
- [ ] Tool present, subprocess timed out (60 s): warning surfaced, removal proceeds
- [ ] `.bloop/` marker triggers `bloop exit`
- [ ] `.scala-build/` marker alone also triggers `bloop exit` (OR branch)
- [ ] `cleanup.builtin = false`: built-in never runs even with markers and tools present

Config:

- [ ] `fromHocon` with no `cleanup` block defaults `builtin` to `true`
- [ ] `fromHocon` with `cleanup { builtin = false }` reads `false`
- [ ] `toHocon` of default config contains no `cleanup` block (byte-stable)
- [ ] `toHocon` with `builtin = false` emits the block and round-trips


## Entry Points

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `core/commands/Rm.scala` | `removeWorktree()` | Orchestration hub: runs hooks, then built-in, then removes the worktree |
| `core/commands/Rm.scala` | `runCleanupHooks()` | Project-hook fold: abort-vs-warn semantics, `NonFatal` guard, short-circuit |
| `core/commands/BuildToolCleanupRunner.scala` | `BuildToolCleanupRunner.run()` | Effectful built-in entry; probes filesystem, calls `detect`, executes teardowns |
| `core/model/BuildToolCleanup.scala` | `BuildToolCleanup.detect()` | Pure decision: marker booleans → ordered `List[BuildToolTeardown]`; start here to understand the logic without I/O |
| `core/model/CleanupAction.scala` | `CleanupAction` / `CleanupContext` | Contract project hooks must satisfy; defines the Nil/warn/throw return semantics |
| `core/model/Config.scala` | `CleanupConfig` / `ConfigSerializer` | Config gate and serialization; `toHocon` emits block only when non-default |
| `core/commands/CommandEnv.scala` | `HookOps.cleanupActions` / `Process.runIn` | Two capability additions: hook discovery + cwd-aware subprocess execution |
| `core/test/RmHarnessTest.scala` | cleanup + built-in test suites | 13 new scenarios driven against `FakeCommandEnv`; covers all acceptance criteria |


## Diagrams

### Component relationships

```
iw rm (Rm.scala)
  │
  ├── decideForce()             ← unchanged: force/dirty-tree prompt
  │
  ├── runCleanupHooks()         ← Phase 1: foldLeft over discovered hooks
  │     │
  │     └── env.hooks.cleanupActions   ← HookDiscovery.collectValues[CleanupAction]
  │           (project *.hook-rm.scala files, auto-discovered by iw-run)
  │
  ├── BuildToolCleanupRunner.run()   ← Phase 2 (gated: config.cleanup.builtin)
  │     │
  │     ├── env.fs.exists()        probe 3 markers under worktreePath
  │     │
  │     ├── BuildToolCleanup.detect()  ← PURE: BuildToolProbe → List[BuildToolTeardown]
  │     │     (core/model/ — no I/O)
  │     │
  │     └── env.process.runIn(worktreePath, command, 60s)   ← NEW capability
  │           (guards on commandExists first; missing tool = silent skip)
  │
  ├── killSessionIfPresent()    ← unchanged
  └── env.worktree.remove()     ← unchanged (skipped if any hook aborted)
```

### Output ordering guarantee

```
[hook action lines]
[hook/built-in warnings, prefixed "Warning: "]
Killing tmux session '...'   (if session exists)
Removing worktree '...'
Worktree removed
```

All cleanup output precedes "Removing worktree…". The BATS smoke test pins
this ordering in a live round-trip.

### Abort vs warn semantics

```
CleanupAction.cleanup(ctx) returns:
  Nil            → proceed silently
  List(w1, w2)   → print each "Warning: <w>" before removal; proceed
  throws NonFatal → print "Error: Cleanup hook failed: <msg>"; skip remaining
                    hooks; skip built-in; skip env.worktree.remove; exit 1
```


## Test Summary

### Unit — pure decision (`core/test/BuildToolCleanupTest.scala`)

| Test | Type | Status |
|------|------|--------|
| All false markers → Nil | Unit | New |
| millDaemon only → mill teardown | Unit | New |
| bloopMarker only → bloop teardown | Unit | New |
| dockerCompose only → docker teardown | Unit | New |
| All true → 3 teardowns in Mill/Bloop/docker order | Unit | New |

### Unit — config (`core/test/ConfigTest.scala`, cleanup section)

| Test | Type | Status |
|------|------|--------|
| `fromHocon` no cleanup block → `builtin = true` | Unit | New |
| `fromHocon` `cleanup { builtin = false }` → `false` | Unit | New |
| `toHocon` default → no cleanup block emitted | Unit | New |
| `toHocon` `builtin = false` → emits block + round-trips | Unit | New |
| Default idempotent round-trip | Unit | New |

### Unit — harness (`core/test/RmHarnessTest.scala`)

Phase-1 cleanup scenarios (13 new, extending the existing 13 pre-existing tests):

| Test | Type | Status |
|------|------|--------|
| No cleanup hooks: removal proceeds | Harness | New |
| Single hook Nil: proceeds, hook ran before removal, context correct | Harness | New |
| Single hook warnings: printed before removal | Harness | New |
| Single hook throws: error, remove NOT called, exit 1 | Harness | New |
| Multiple hooks: declared order, warnings aggregate before removal | Harness | New |
| First hook throws: second never runs, removal skipped | Harness | New |

Phase-2 built-in scenarios:

| Test | Type | Status |
|------|------|--------|
| Markers + tools present: teardown in worktree cwd, ordered, not via `run` | Harness | New |
| Marker present, tool absent: silent skip, no warning | Harness | New |
| Tool present, non-zero exit: warning, removal proceeds | Harness | New |
| Built-in disabled: no runIn calls | Harness | New |
| `.bloop` marker + bloop: `bloop exit` in worktree cwd | Harness | New |
| `.scala-build` alone: also triggers `bloop exit` (OR branch) | Harness | New |
| `timedOut = true, exitCode = 0`: warning, removal proceeds | Harness | New |

**Test run results (from implementation log):**
- `core.test`: 186/186 (Phase 1) → passes (Phase 2 adds 7 more scenarios)
- `dashboard.test`: 241/241
- `scala-cli compile --scalac-option -Werror core/` — clean
- `scala-cli fmt --check core/` — clean

### E2E smoke (`test/rm.bats`)

| Test | Type | Status |
|------|------|--------|
| rm: removes worktree and tmux session | E2E | Pre-existing |
| rm: cleanup hook runs before worktree is removed | E2E | New |

The new BATS test writes a real `*.hook-rm.scala` at runtime, runs `iw rm`
through the full scala-cli stack, and asserts the `HOOK_RAN` sentinel appears
before "Worktree removed" by comparing line numbers in the output.


## Files Changed

### New files

| File | Purpose |
|------|---------|
| `core/model/CleanupAction.scala` | `CleanupContext` case class + `CleanupAction` trait with Nil/warn/throw contract |
| `core/model/BuildToolCleanup.scala` | Pure decision: `BuildToolProbe` + `BuildToolTeardown` + `BuildToolCleanup.detect` |
| `core/commands/BuildToolCleanupRunner.scala` | Effectful built-in runner: probes, executes teardowns in worktree cwd, returns warnings |
| `core/test/BuildToolCleanupTest.scala` | 5 unit tests for the pure decision function |

### Modified files

| File | Change |
|------|--------|
| `core/model/Config.scala` | `CleanupConfig(builtin: Boolean = true)` sub-config; `ProjectConfiguration.cleanup` field; `fromHocon`/`toHocon` parse and conditional emit |
| `core/model/Constants.scala` | `ConfigKeys.CleanupBuiltin = "cleanup.builtin"` |
| `core/commands/CommandEnv.scala` | `HookOps.cleanupActions: List[CleanupAction]`; `Process.runIn(cwd, command, timeoutMs)` |
| `core/commands/LiveCommandEnv.scala` | `LiveHookOps.cleanupActions` (reflection); `LiveProcess.runIn` |
| `core/adapters/Process.scala` | `ProcessAdapter.run` gains `cwd: os.Path = os.pwd` (default preserves all call sites) |
| `core/commands/Rm.scala` | `runCleanupHooks` helper; built-in invocation gated on `config.cleanup.builtin`; warnings aggregation and ordering |
| `core/test/RmHarnessTest.scala` | 13 new scenarios (6 hook + 7 built-in) |
| `core/test/ConfigTest.scala` | 5 new cleanup config tests |
| `core/test/fixtures/FakeCommandEnv.scala` | `FakeHookOps`: `cleanupActionsRef` + `setCleanupActions`; `FakeProcess`: `runIn` + `runInList` recorder + shared prefix-match lookup |
| `test/rm.bats` | One new E2E smoke scenario |

<details>
<summary>Diff stat (24 files, +2786 / -18 lines)</summary>

```
core/adapters/Process.scala                        |   4 +-
core/commands/BuildToolCleanupRunner.scala         |  71 +++
core/commands/CommandEnv.scala                     |  10 +
core/commands/LiveCommandEnv.scala                 |   6 +
core/commands/Rm.scala                             |  67 ++-
core/model/BuildToolCleanup.scala                  |  63 +++
core/model/CleanupAction.scala                     |  29 ++
core/model/Config.scala                            |  28 +-
core/model/Constants.scala                         |   1 +
core/test/BuildToolCleanupTest.scala               |  67 +++
core/test/ConfigTest.scala                         |  80 +++
core/test/RmHarnessTest.scala                      | 401 +++++++++++++++
core/test/fixtures/FakeCommandEnv.scala            |  22 +-
```

The remaining +lines are project-management docs (analysis, tasks, phase
contexts, review files, implementation log).
</details>
