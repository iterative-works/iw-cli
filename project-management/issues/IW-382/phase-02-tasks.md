# Phase 2 Tasks: BuildToolCleanup built-in (Mill / Bloop / docker-compose)

**Issue:** IW-382
**Phase:** 2 of 2
**Estimate:** 2.5-4 hours
**Status:** Not started

These tasks are ordered for TDD: within each behavior, the failing test task
precedes the implementation task that makes it pass. Work top to bottom. The one
exception is the `Process.runIn` plumbing — it is a prerequisite capability with
no standalone behavior, so it is implemented first so the harness scenarios can
compile and assert on `runInList` (this mirrors how Phase 1 wired `HookOps`
before the harness scenarios).

References:
- Spec: `project-management/issues/IW-382/phase-02-context.md`
- Integration point: `core/commands/Rm.scala` `removeWorktree` (the `Right(warnings)` branch)
- Touch points: `core/model/BuildToolCleanup.scala` (NEW),
  `core/commands/BuildToolCleanupRunner.scala` (NEW),
  `core/test/BuildToolCleanupTest.scala` (NEW), `core/adapters/Process.scala`,
  `core/commands/CommandEnv.scala`, `core/commands/LiveCommandEnv.scala`,
  `core/test/fixtures/FakeCommandEnv.scala`, `core/commands/Rm.scala`,
  `core/test/RmHarnessTest.scala`

---

## Setup

- [ ] [setup] Confirm baseline is green: run `scala-cli compile --scalac-option -Werror core/` and `./iw ./test unit`. Note the current `RmHarnessTest` count and `ConfigTest` count so regressions are visible later.
- [ ] [setup] Re-read the Phase-2 context Design Decisions (D1 cwd/`runIn`, D2 missing-tool silent skip, D3 Bloop standalone-only, D4 naming) and the `FakeProcess` block (`core/test/fixtures/FakeCommandEnv.scala` ~line 259) so the new code mirrors the existing `run`/`scriptResponse`/`invocationList` shape.

---

## Infrastructure: `Process.runIn` capability (prerequisite plumbing — D1)

The built-in must run teardown **in the worktree directory** (Mill daemon and
docker compose are workspace-scoped). The existing `Process.run` has no cwd, so
add `runIn`. This is a new capability, not a backward-compat shim — existing
`run(command)` call sites stay untouched.

- [ ] [impl] In `core/adapters/Process.scala`, add a `cwd: os.Path = os.pwd` parameter to `ProcessAdapter.run` and pass it to `os.proc(command).call(cwd = cwd, ...)`. The default `os.pwd` keeps every existing `run(command)` call byte-for-byte unchanged (os-lib already defaults cwd to the process dir).
- [ ] [impl] In `core/commands/CommandEnv.scala`, add `def runIn(cwd: os.Path, command: Seq[String]): ProcessResult` to `trait Process` (right after `run`).
- [ ] [impl] In `core/commands/LiveCommandEnv.scala`, `object LiveProcess`, add `def runIn(cwd: os.Path, command: Seq[String]): ProcessResult = ProcessAdapter.run(command, cwd = cwd)`.
- [ ] [impl] In `core/test/fixtures/FakeCommandEnv.scala`, `final class FakeProcess`: factor the existing `responseScript`/`defaultResult` prefix-match lookup in `run` into a private helper `private def lookup(command: Seq[String]): ProcessResult`, then have both `run` and the new `runIn` use it. Add `private val runInInvocations = mutable.ArrayBuffer.empty[(os.Path, Seq[String])]`, `def runInList: List[(os.Path, Seq[String])] = runInInvocations.toList`, and `def runIn(cwd: os.Path, command: Seq[String]): ProcessResult = { runInInvocations += ((cwd, command)); lookup(command) }`. (So `scriptResponse(prefix, result)` pins responses for `runIn` exactly as for `run`.)
- [ ] [verify] Run `scala-cli compile --scalac-option -Werror core/` — trait + both impls compile clean before writing any built-in code.

---

## Application: pure `BuildToolCleanup.detect` (tests first)

- [ ] [test] Create `core/test/BuildToolCleanupTest.scala` (munit, `package iw.core.test`, import `iw.core.model.*`). Add tests over `BuildToolCleanup.detect(BuildToolProbe(...))`:
  - all-false → `Nil`.
  - `millDaemon = true` only → one teardown with `tool == "mill"`, `command == Seq("mill", "--no-server", "shutdown")`.
  - `bloopMarker = true` only → `tool == "bloop"`, `command == Seq("bloop", "exit")`.
  - `dockerCompose = true` only → `tool == "docker"`, `command == Seq("docker", "compose", "down")`.
  - all-true → exactly three teardowns in Mill, Bloop, docker order (assert the `tool` sequence is `List("mill", "bloop", "docker")`).
- [ ] [test] Run the new test; confirm it fails to compile (no `BuildToolCleanup` / `BuildToolProbe` / `BuildToolTeardown` yet), not for unrelated reasons.
- [ ] [impl] Create `core/model/BuildToolCleanup.scala` with two `// PURPOSE:` header lines, `package iw.core.model`, the `BuildToolTeardown(tool: String, command: Seq[String], description: String)` and `BuildToolProbe(millDaemon: Boolean, bloopMarker: Boolean, dockerCompose: Boolean)` case classes, and `object BuildToolCleanup` with `def detect(probe: BuildToolProbe): List[BuildToolTeardown]` building the list via `Option.when(...)` in Mill, Bloop, docker order (see context Component Spec 1). Pure — no I/O, no imports beyond stdlib.
- [ ] [verify] Run `BuildToolCleanupTest` — all decision cases pass.

---

## Application: `BuildToolCleanupRunner` + `Rm` wiring (harness tests first — scenarios 6 & 7)

Write these failing scenarios in `core/test/RmHarnessTest.scala` BEFORE creating
the runner or touching `Rm.scala`. Use the existing helpers: `seedConfig(env)`,
`env.worktree.addWorktree(wt)`, `env.fs.put(...)`, `env.process.setExistingCommands(...)`,
`env.process.scriptResponse(...)`, and assert on `env.process.runInList`,
`env.console.stdoutLines`, and `env.worktree.removeCallList`. Compute the worktree
path the same way the existing cleanup tests do and `put` markers under it
(`wt / "out" / "mill-daemon"`, `wt / "docker-compose.yml"`).

- [ ] [test] Scenario 6 — **built-in only (no project hook), markers + tools present → teardown runs in worktree cwd, removal proceeds.** `seedConfig(env)` (builtin defaults true); `env.hooks.setCleanupActions(Nil)`; `env.fs.put(wt / "out" / "mill-daemon", "")` and `env.fs.put(wt / "docker-compose.yml", "")`; `env.process.setExistingCommands(Set("mill", "docker"))`. Assert `env.process.runInList` contains `(wt, Seq("mill", "--no-server", "shutdown"))` and `(wt, Seq("docker", "compose", "down"))` — **cwd is the worktree**. Assert the `"Shutting down Mill daemon..."` action line precedes `"Removing worktree..."` in `env.console.stdoutLines`. Assert `exitCode == 0` and `env.worktree.removeCallList.size == 1`.
- [ ] [test] Scenario 6b — **marker present but tool absent → silent skip (D2).** Markers `put` as in 6 but do NOT add `mill`/`docker` to existing commands (defaults lack them). Assert `env.process.runInList` is empty, no `"Shutting down Mill daemon"` line appears, **no `Warning:` line** is emitted, and removal still proceeds (`exitCode == 0`, `removeCallList.size == 1`).
- [ ] [test] Scenario 6c — **tool present, non-zero exit → warning, removal proceeds.** Markers + `setExistingCommands(Set("mill"))`; `env.process.scriptResponse(Seq("mill"), ProcessResult(1, "", "boom"))`. Assert a `Warning:` line containing `"Shutting down Mill daemon failed"` appears, `exitCode == 0`, and `removeCallList.size == 1` (built-in never aborts).
- [ ] [test] Scenario 7 — **built-in disabled (`cleanup.builtin = false`) → never runs.** Seed a config string that includes a `cleanup {\n  builtin = false\n}` block (add a `seedConfigBuiltinDisabled(env)` helper or inline the HOCON — the gate reads the parsed config file, so a constructed `ProjectConfiguration` is not enough). Markers present, `setExistingCommands(Set("mill", "docker"))`. Assert `env.process.runInList` is empty (built-in skipped), `exitCode == 0`, `removeCallList.size == 1`.
- [ ] [test] Run `RmHarnessTest`; confirm scenarios 6/6b/6c/7 fail (no runner / no `Rm` gate yet) while the existing scenarios still pass.
- [ ] [impl] Create `core/commands/BuildToolCleanupRunner.scala` with two `// PURPOSE:` header lines, `package iw.core.commands`, `object BuildToolCleanupRunner` and `def run(ctx: CleanupContext, env: CommandEnv): List[String]`. Build `BuildToolProbe` from `env.fs.exists(ctx.worktreePath / ...)` (Mill `out/mill-daemon`; Bloop `.bloop` **or** `.scala-build`; docker `docker-compose.yml`), call `BuildToolCleanup.detect`, and `flatMap` each teardown through a private `runOne` that: returns `None` when `!env.process.commandExists(tool)` (silent skip); else prints `s"${description}..."` via `env.console.out`, runs `env.process.runIn(ctx.worktreePath, command)`, and returns `Some(warning)` when `exitCode != 0 || timedOut` (or on a caught `NonFatal`), else `None`. Never throws. See context Component Spec 2 for the exact body.
- [ ] [impl] In `core/commands/Rm.scala` `removeWorktree`, in the `runCleanupHooks(ctx, env)` → `Right(hookWarnings)` branch, compute `val builtinWarnings = if config.cleanup.builtin then BuildToolCleanupRunner.run(ctx, env) else Nil` and `val warnings = hookWarnings ++ builtinWarnings`, then keep the existing `warnings.foreach(...)` → `killSessionIfPresent` → `"Removing worktree..."` → `env.worktree.remove` tail exactly as-is. (The built-in runs **after** project hooks; a hook abort in the `Left` branch still preserves the worktree before the built-in is ever reached.)
- [ ] [verify] Run `RmHarnessTest`; scenarios 6/6b/6c/7 and all existing scenarios pass.

---

## Verification

- [ ] [verify] `scala-cli compile --scalac-option -Werror core/` is clean — no warnings.
- [ ] [verify] `./iw ./test unit` passes (core.test + dashboard.test): new `BuildToolCleanupTest` + `RmHarnessTest` scenarios green, existing tests unregressed.
- [ ] [verify] Walk the phase-02-context Acceptance Criteria checklist and confirm each item is satisfied — in particular: teardown executes via `env.process.runIn(ctx.worktreePath, …)` (never bare `run`, never `os`/`ProcessAdapter` directly), the runner never throws, `force` is ignored by the built-in, and the gate is `config.cleanup.builtin`.
- [ ] [verify] Confirm no new BATS test was added (the built-in is harness-covered; real Mill/docker are not available in CI).
