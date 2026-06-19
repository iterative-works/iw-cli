# Implementation Log: Cleanup hook on worktree removal — stop project daemons before iw rm

Issue: IW-382

This log tracks the evolution of implementation across phases.

---

## Phase 1: CleanupAction model + config + Rm wiring (project-hook path) (2026-06-13)

**Layers:** Domain + Infrastructure + Presentation (merged — each below the phase-size floor, all on the model → wiring → output dependency path)

**What was built:**
- `core/model/CleanupAction.scala` (NEW) — `CleanupContext(worktreePath: os.Path, issueId: String, config: ProjectConfiguration, force: Boolean)` + `trait CleanupAction { def cleanup(ctx): List[String] }`. Mirrors the #331 `RecoveryAction` pattern. Return contract encoded by convention: `Nil` = proceed, non-empty list = warnings (proceed), throw = abort (preserve worktree, exit non-zero).
- `core/model/Config.scala` — `CleanupConfig(builtin: Boolean = true)` sub-config; `ProjectConfiguration` gains `cleanup: CleanupConfig = CleanupConfig()`; `ConfigSerializer.fromHocon` parses `cleanup.builtin` (default `true` via `hasPath`); `ConfigSerializer.toHocon` emits a `cleanup { builtin = false }` block only when non-default (default configs serialize byte-identically); `ReadWriter[CleanupConfig]` (`macroRW`) in `ProjectConfigurationJson`. `builtin` is parsed now but read by `BuildToolCleanup` in Phase 2 (documented on the type).
- `core/model/Constants.scala` — `ConfigKeys.CleanupBuiltin = "cleanup.builtin"`.
- `core/commands/CommandEnv.scala` — `HookOps.cleanupActions: List[CleanupAction]`.
- `core/commands/LiveCommandEnv.scala` — `LiveHookOps.cleanupActions = HookDiscovery.collectValues[CleanupAction]` (reflection discovery; no `iw-run` change needed since `rm` is a shared command).
- `core/test/fixtures/FakeCommandEnv.scala` — `FakeHookOps` gains `cleanupActionsRef` + `setCleanupActions` + getter (scripted injection for harness tests).
- `core/commands/Rm.scala` — `runCleanupHooks` private helper (short-circuiting `foldLeft`, catches `NonFatal`, null-guards the error message) invoked in `removeWorktree` after force/session checks and before `env.worktree.remove`. Hooks run in declared order; warnings aggregate and print before "Removing worktree…"; first thrown hook aborts (error line, `CommandResult.error`, worktree preserved, subsequent hooks skipped). `forceRemove` (post-confirm decision) threaded into `CleanupContext.force`.

**Dependencies on other layers:**
- Reuses #331 `HookDiscovery.collectValues` reflection and the `CommandEnv` / `HookOps` capability pattern.
- Phase 2 (`BuildToolCleanup` built-in) depends on this phase's `Rm` invocation point and the `cleanup.builtin` config gate.

**Decisions:**
- `--force` passthrough: threaded `forceRemove` (post-confirm) into `CleanupContext.force`, matching what `env.worktree.remove` receives (per phase-context recommendation; the analysis's "pass `--force` through" left raw-flag vs post-confirm ambiguous).
- The built-in is deliberately NOT in this phase. `cleanup.builtin` exists but nothing reads it yet.

**Testing:**
- Unit (harness, `RmHarnessTest`): 6 new scenarios — no hooks; single success (asserts hook ran + context fields + ordering); single warnings (ordering before removal); single abort (error, `worktree.remove` NOT called, exit 1); multiple hooks (declared order, warnings aggregate, all before removal); first-hook-throws-second-never-runs.
- Unit (config, `ConfigTest`): 5 new — default-absent → true; explicit false; toHocon omits block when default; toHocon emits + round-trips when false; default idempotent round-trip.
- E2E smoke (`test/rm.bats`): one scenario — inline `*.hook-rm.scala` prints a sentinel that must appear before "Worktree removed".
- Results: core.test 186/186, dashboard.test 241/241, `scala-cli compile -Werror core/` clean, bats parses (2 tests).

**Code review:**
- Iterations: 2 (iteration 1 found 4 criticals: `Throwable` catch, null `getMessage`, comment-only ordering assertion, bats sentinel robustness — all fixed plus the 5-reviewer `var`/`while`→`foldLeft` consensus warning; iteration 2 verification: 0 critical, 0 warnings).
- Review file: review-phase-01-20260613-153233.md

**For next phases:**
- Phase 2 plugs `BuildToolCleanup` directly into the `Rm` cleanup section (after the discovered project hooks), gated on `config.cleanup.builtin`, using `env.fs` / `env.process` — pure `detect` decision + effectful runner.

**Files changed:**
```
A	core/model/CleanupAction.scala
M	core/model/Config.scala
M	core/model/Constants.scala
M	core/commands/CommandEnv.scala
M	core/commands/LiveCommandEnv.scala
M	core/commands/Rm.scala
M	core/test/RmHarnessTest.scala
M	core/test/ConfigTest.scala
M	core/test/fixtures/FakeCommandEnv.scala
M	test/rm.bats
```

---

## Phase 2: BuildToolCleanup built-in (Mill / Bloop / docker-compose) (2026-06-14)

**Layer:** Application (built-in cleanup logic) + a small Infrastructure capability extension

**What was built:**
- `core/model/BuildToolCleanup.scala` (NEW) — pure decision. `BuildToolProbe(millDaemon, bloopMarker, dockerCompose)` (filesystem-marker booleans) → `BuildToolCleanup.detect`: ordered `List[BuildToolTeardown(tool, command, description)]` (Mill → Bloop → docker). No I/O.
- `core/commands/BuildToolCleanupRunner.scala` (NEW) — effectful runner. Probes the worktree via `env.fs.exists` (`out/mill-daemon`, `.bloop` || `.scala-build`, `docker-compose.yml`), calls `detect`, and runs each teardown in the **worktree cwd** via `env.process.runIn`, guarded by `commandExists`. Best-effort: missing tool → silent skip; non-zero/timeout/`NonFatal` → warning; never throws, never aborts `rm`. Bounded per-tool timeout (`TeardownTimeoutMs = 60s`); subprocess detail truncated to 200 chars in warnings.
- `core/adapters/Process.scala` — `ProcessAdapter.run` gains `cwd: os.Path = os.pwd` (default preserves all existing call sites).
- `core/commands/CommandEnv.scala` / `LiveCommandEnv.scala` / `core/test/fixtures/FakeCommandEnv.scala` — new `Process.runIn(cwd, command, timeoutMs)` capability (live delegates to `ProcessAdapter.run(cwd=, timeoutMs=)`; fake records `(cwd, command)` pairs via `runInList`, shares the `run` response-script `lookup`). This is the crux of Phase 2: the existing `run` has no cwd, so workspace-scoped daemons (Mill, docker compose) would otherwise be targeted at iw's own directory.
- `core/commands/Rm.scala` — in the `Right(hookWarnings)` branch, runs `BuildToolCleanupRunner.run` after the project hooks (gated on `config.cleanup.builtin`), aggregating built-in warnings with hook warnings before removal. Hooks-before-built-in order documented inline.

**Dependencies on other layers:**
- Phase 1's `Rm` cleanup invocation point, `CleanupContext`, and `cleanup.builtin` config gate.
- `CommandEnv` `fs` / `process` / `console` capabilities (FCIS: all built-in I/O goes through `env`; no direct `os` / `ProcessAdapter`).

**Decisions:**
- **D1 (cwd):** the analysis assumed `env.process.run` could target the worktree; it cannot. Added `Process.runIn(cwd, command, timeoutMs)`. The bounded 60s timeout (added in review) implements the analysis "never block rm" contract — the unbounded 5-min default could freeze `rm` on a wedged daemon.
- **D2:** missing tool → silent skip (no warning); only real failures warn. Refines the analysis's "missing → warning" to avoid noise (e.g. ubiquitous `.scala-build` without standalone `bloop`).
- **D3:** Bloop is marker-gated unconditional `bloop exit` (no state-file parsing), gated on `commandExists("bloop")`; scala-cli-only setups silently skip.
- **D4:** naming — model decision `BuildToolCleanup` vs shell runner `BuildToolCleanupRunner` (avoids the same-name import collision the analysis would have produced).
- Declined an enum/`Set[BuildTool]` remodel of `BuildToolProbe` (YAGNI for 3 fixed tools; pure tests are exhaustive).

**Testing:**
- Unit (pure, `BuildToolCleanupTest`): 5 — all-false, each marker alone, all-true ordering.
- Unit (harness, `RmHarnessTest`): 7 new — built-in teardown in worktree cwd (ordered `runInList`, asserts no `run` fallback); tool-absent silent skip; non-zero exit warning; built-in disabled gate; `.bloop` → `bloop exit`; `.scala-build` OR-branch; timed-out → warning.
- Results: `core.test` SUCCESS, `dashboard.test` 241/241 SUCCESS, `scala-cli compile -Werror core/` clean, scalafmt clean. No new BATS (built-in is harness-covered; real Mill/docker unavailable in CI).

**Code review:**
- Iterations: 1 review round (6 reviewers: architecture, scala3, composition, testing, security, style) → 0 architecture/security/style criticals; 1 valid testing critical (Bloop harness coverage) + high-value warnings (unbounded timeout, untested timed-out path, missing Scaladoc) fixed; re-verified via compile + full unit run.
- Review file: review-phase-02-20260614-193110.md

**Files changed:**
```
A	core/model/BuildToolCleanup.scala
A	core/commands/BuildToolCleanupRunner.scala
A	core/test/BuildToolCleanupTest.scala
M	core/adapters/Process.scala
M	core/commands/CommandEnv.scala
M	core/commands/LiveCommandEnv.scala
M	core/commands/Rm.scala
M	core/test/RmHarnessTest.scala
M	core/test/fixtures/FakeCommandEnv.scala
```

---
