# Phase 2: BuildToolCleanup built-in (Mill / Bloop / docker-compose)

**Issue:** IW-382
**Phase:** 2 of 2
**Layer:** Application (built-in cleanup logic) + a small Infrastructure capability extension
**Estimate:** 2.5-4h
**Depends on:** Phase 1 (merged) — the `Rm` cleanup orchestration point, `CleanupContext`, and the `cleanup.builtin` config gate all exist.

## Goals

Ship a built-in `BuildToolCleanup` so projects get sensible daemon teardown on
`iw rm` without writing any `*.hook-rm.scala`. After the discovered project
hooks run, `Rm` invokes the built-in (gated on `config.cleanup.builtin`), which
probes the worktree filesystem and best-effort shuts down workspace-scoped build
daemons:

- **Mill** — `out/mill-daemon/` present under the worktree → `mill --no-server shutdown`.
- **Bloop** — `.bloop/` or `.scala-build/` marker present under the worktree → `bloop exit`.
- **docker-compose** — `docker-compose.yml` at the worktree root → `docker compose down`.

The built-in is **best-effort and never blocks `rm`**: a missing tool is skipped,
a non-zero exit / timeout / unexpected error becomes a warning, and it never
throws to `Rm`. It is opt-out via `cleanup.builtin = false`.

## Scope

### IN scope (Phase 2)

1. **Pure decision** — `BuildToolCleanup.detect` in `core/model/`: filesystem-probe
   booleans → ordered list of teardown commands. No I/O.
2. **Effectful runner** — `BuildToolCleanupRunner.run(ctx, env): List[String]` in
   `core/commands/`: build the probe via `env.fs.exists`, call `detect`, then for
   each teardown guard on `env.process.commandExists` and execute via the worktree
   working directory, collecting warnings. Never throws.
3. **`Process` capability extension** — add `runIn(cwd, command)` so teardown runs
   in the worktree directory (see Design Decision D1 — the existing `run` has no
   cwd and would target iw's own directory).
4. **`Rm` wiring** — invoke the runner after the project hooks, gated on
   `config.cleanup.builtin`, aggregating its warnings with the project-hook warnings.
5. **Tests** — pure `detect` decision tests; harness scenarios 6 (built-in only)
   and 7 (built-in disabled) from the analysis.

### OUT of scope

- `--force` escalation in the built-in (analysis Decision: built-in ignores
  `force`, always graceful — `mill --no-server shutdown`, `bloop exit`,
  `docker compose down`). No PID-kill / SIGKILL branch.
- Bloop state-file parsing (analysis Decision: marker-gated unconditional
  `bloop exit`, no scala-cli internals).
- "Handled" signaling / dedup between project hooks and the built-in (analysis
  Decision: built-in runs unconditionally after hooks; the teardowns are
  idempotent so a repeat is a harmless no-op).
- scala-cli's *bundled* Bloop (not directly invokable as `bloop`). Only standalone
  `bloop` on PATH is targeted; see Design Decision D3.
- New BATS test — the built-in is covered by the harness (Tier 1). The analysis
  testing table maps the built-in scenarios to harness, not E2E; real Mill/docker
  are not available in CI and `commandExists` is faked. No new `*.bats`.

## Dependencies (what already exists)

### `Rm` cleanup orchestration point (`core/commands/Rm.scala`)

`removeWorktree` already builds the `CleanupContext` and runs project hooks. The
built-in plugs into the `Right(warnings)` branch (hooks succeeded), before the
worktree is removed:

```scala
// core/commands/Rm.scala — current Phase-1 shape (lines ~92-121)
val ctx = CleanupContext(
  worktreePath = targetPath,   // resolved os.Path; the worktree still exists here
  issueId = issueId.value,
  config = config,
  force = forceRemove
)
runCleanupHooks(ctx, env) match
  case Left(result) => result              // a hook aborted; worktree preserved
  case Right(warnings) =>
    warnings.foreach(w => env.console.out(s"Warning: $w"))
    killSessionIfPresent(sessionName, env)
    env.console.out(s"Removing worktree '${worktreePath.directoryName}'...")
    env.worktree.remove(targetPath, env.cwd, force = forceRemove) match ...
```

### `CleanupContext` (`core/model/CleanupAction.scala`)

Provides `worktreePath: os.Path` (the resolved, still-present worktree dir),
`config: ProjectConfiguration`, `force: Boolean`. The runner reads
`ctx.worktreePath` for probes + cwd; the built-in ignores `force`.

### Config gate (`core/model/Config.scala`)

`CleanupConfig(builtin: Boolean = true)` already parsed/serialized in Phase 1.
`config.cleanup.builtin` is the gate — read it in `Rm`, not in the runner.

### `CommandEnv` capabilities the runner uses

```scala
// core/commands/CommandEnv.scala
trait FileSystem:
  def exists(path: os.Path): Boolean      // probe for markers (exact-path in the fake)
trait Process:
  def commandExists(command: String): Boolean
  def run(command: Seq[String]): ProcessResult   // NO cwd — see D1
```

`ProcessResult(exitCode: Int, stdout: String, stderr: String, truncated: Boolean = false, timedOut: Boolean = false)`
(`core/adapters/Process.scala`).

### Fakes (`core/test/fixtures/FakeCommandEnv.scala`)

- `FakeFileSystem.put(path, content)` — `exists` is **exact-path** (no directory
  semantics): to simulate `out/mill-daemon/`, `put(worktree / "out" / "mill-daemon", "")`.
- `FakeProcess` — default `existingCommands = {git, gh, glab, scala-cli, tmux}`
  (**mill / bloop / docker are absent by default**); `setExistingCommands(Set)`,
  `markCommandMissing(name)`, `scriptResponse(prefix, ProcessResult)`,
  `invocationList: List[Seq[String]]`. The `runIn` extension (D1) adds a parallel
  recorder — see Component Spec 3.

## Design Decisions

### D1 — Teardown runs in the worktree cwd; add `Process.runIn`

The motivating defect is orphaned **Mill** daemons. `mill --no-server shutdown`
and `docker compose down` are **workspace-scoped**: they act on the daemon /
compose project rooted at the *current working directory*. The existing
`Process.run(command)` / `ProcessAdapter.run` run in **iw's own cwd**, not the
worktree — so running `mill shutdown` there would do nothing useful (or shut down
iw's own Mill). The analysis assumed `env.process.run` could target the worktree;
it cannot.

**Decision:** extend the `Process` boundary with
`runIn(cwd: os.Path, command: Seq[String]): ProcessResult`. The runner calls
`env.process.runIn(ctx.worktreePath, teardown.command)` for every teardown. This
is the minimal honest fix (trait + `LiveProcess` + `FakeProcess` = 3 sites) and
makes docker work without an explicit `-f` and Mill work at all. `runIn` is a
**new capability**, not a backward-compat shim — existing `run(command)` callers
are untouched.

### D2 — Missing tool → silent skip; non-zero / timeout / error → warning

`detect` decides teardowns from filesystem markers only. The runner guards each
on `commandExists(tool)`:
- **tool absent** → skip silently (no warning). Markers are heuristics; a missing
  CLI is not actionable noise (e.g. `.scala-build/` is ubiquitous but standalone
  `bloop` often is not installed). This refines the analysis's "missing tool →
  warning" — silent skip keeps the built-in quiet for the common case.
- **tool present, exit ≠ 0 (or `timedOut`)** → one concise warning, proceed.
- **unexpected throw** from the call → caught (`NonFatal`) → warning, proceed.

The built-in never returns `Left` / never aborts `rm`.

### D3 — Bloop: standalone `bloop` only, marker-gated unconditional `exit`

Per the analysis Decision, no state-file parsing. Trigger when `.bloop/` **or**
`.scala-build/` exists under the worktree and `commandExists("bloop")`, then issue
`bloop exit` (global — accepted trade-off: may exit a Bloop server shared with
another worktree; Bloop restarts on demand). scala-cli's bundled Bloop is not
invokable as `bloop`, so scala-cli-only setups fall through D2's silent skip.

### D4 — Naming: `BuildToolCleanup.detect` (model) vs `BuildToolCleanupRunner.run` (shell)

The analysis named both the pure decision and the effectful runner
`BuildToolCleanup`. Two objects of the same simple name in `iw.core.model` and
`iw.core.commands` collide when the shell file imports the model object. Resolve
by naming the shell runner `BuildToolCleanupRunner` — the model **decides**
(`BuildToolCleanup.detect`), the runner **executes** (`BuildToolCleanupRunner.run`).
Distinct, greppable, no import gymnastics.

## Component Specifications

### 1. `BuildToolCleanup` pure decision (`core/model/BuildToolCleanup.scala`, NEW)

```scala
// PURPOSE: Pure decision for the built-in build-tool teardown on worktree removal
// PURPOSE: Maps worktree filesystem markers to the ordered list of teardown commands

package iw.core.model

/** One build-tool teardown: the executable to require and the command to run. */
case class BuildToolTeardown(
    tool: String,            // commandExists guard target, e.g. "mill"
    command: Seq[String],    // argv, e.g. Seq("mill", "--no-server", "shutdown")
    description: String       // user-facing action line, e.g. "Shutting down Mill daemon"
)

/** Filesystem-marker probe results for a worktree. Pure inputs to `detect`. */
case class BuildToolProbe(
    millDaemon: Boolean,     // out/mill-daemon/ present
    bloopMarker: Boolean,    // .bloop/ or .scala-build/ present
    dockerCompose: Boolean   // docker-compose.yml present
)

object BuildToolCleanup:
  def detect(probe: BuildToolProbe): List[BuildToolTeardown] =
    List(
      Option.when(probe.millDaemon)(
        BuildToolTeardown("mill", Seq("mill", "--no-server", "shutdown"),
          "Shutting down Mill daemon")
      ),
      Option.when(probe.bloopMarker)(
        BuildToolTeardown("bloop", Seq("bloop", "exit"),
          "Stopping Bloop server")
      ),
      Option.when(probe.dockerCompose)(
        BuildToolTeardown("docker", Seq("docker", "compose", "down"),
          "Stopping docker compose stack")
      )
    ).flatten
```

Pure, no I/O. Ordering: Mill, Bloop, docker (declared order; deterministic).

### 2. `BuildToolCleanupRunner` effectful runner (`core/commands/BuildToolCleanupRunner.scala`, NEW)

```scala
// PURPOSE: Runs the built-in build-tool teardown during iw rm, best-effort
// PURPOSE: Probes the worktree, executes detected teardowns in its cwd, returns warnings

package iw.core.commands

import iw.core.model.{BuildToolCleanup, BuildToolProbe, BuildToolTeardown, CleanupContext}
import scala.util.control.NonFatal

object BuildToolCleanupRunner:
  /** Best-effort teardown. Returns warnings (never throws, never aborts rm). */
  def run(ctx: CleanupContext, env: CommandEnv): List[String] =
    val wt = ctx.worktreePath
    val probe = BuildToolProbe(
      millDaemon    = env.fs.exists(wt / "out" / "mill-daemon"),
      bloopMarker   = env.fs.exists(wt / ".bloop") || env.fs.exists(wt / ".scala-build"),
      dockerCompose = env.fs.exists(wt / "docker-compose.yml")
    )
    BuildToolCleanup.detect(probe).flatMap(t => runOne(t, wt, env))

  private def runOne(
      teardown: BuildToolTeardown,
      worktree: os.Path,
      env: CommandEnv
  ): Option[String] =
    if !env.process.commandExists(teardown.tool) then None  // D2: silent skip
    else
      try
        env.console.out(s"${teardown.description}...")
        val result = env.process.runIn(worktree, teardown.command)
        if result.exitCode != 0 || result.timedOut then
          val detail = Option(result.stderr).filter(_.nonEmpty)
            .orElse(Option(result.stdout).filter(_.nonEmpty))
            .map(s => s": ${s.linesIterator.next()}").getOrElse("")
          Some(s"${teardown.description} failed$detail")
        else None
      catch
        case NonFatal(e) =>
          val msg = Option(e.getMessage).getOrElse(e.getClass.getSimpleName)
          Some(s"${teardown.description} failed: $msg")
```

Notes:
- Action line printed **before** running, so it appears before the warnings and
  the "Removing worktree…" line.
- Returns `List[String]` warnings; `Rm` prints them (uniform with project hooks).

### 3. `Process.runIn` capability (`CommandEnv.scala`, `LiveCommandEnv.scala`, `FakeCommandEnv.scala`, MODIFY)

```scala
// core/commands/CommandEnv.scala — trait Process
def runIn(cwd: os.Path, command: Seq[String]): ProcessResult

// core/commands/LiveCommandEnv.scala — object LiveProcess
def runIn(cwd: os.Path, command: Seq[String]): ProcessResult =
  ProcessAdapter.run(command, cwd = cwd)   // add cwd param to ProcessAdapter.run

// core/adapters/Process.scala — ProcessAdapter.run gains:
//   cwd: os.Path = os.pwd   (passed to os.proc(command).call(cwd = cwd, ...))
//   default os.pwd preserves every existing run(command) call site unchanged.

// core/test/fixtures/FakeCommandEnv.scala — FakeProcess
private val runInInvocations = mutable.ArrayBuffer.empty[(os.Path, Seq[String])]
def runInList: List[(os.Path, Seq[String])] = runInInvocations.toList
def runIn(cwd: os.Path, command: Seq[String]): ProcessResult =
  runInInvocations += ((cwd, command))
  // reuse the same responseScript/default lookup as run(...)
  <lookup scripted response by command prefix, else defaultResult>
```

`FakeProcess.runIn` must honor `scriptResponse(prefix, result)` exactly like
`run`, so scenario 6 can script a non-zero exit. Factor the prefix-match lookup
into a shared private helper to avoid duplication.

### 4. `Rm` wiring (`core/commands/Rm.scala`, MODIFY)

In the `Right(hookWarnings)` branch of `runCleanupHooks`, run the built-in
(gated) and aggregate before printing:

```scala
case Right(hookWarnings) =>
  val builtinWarnings =
    if config.cleanup.builtin then BuildToolCleanupRunner.run(ctx, env)
    else Nil
  val warnings = hookWarnings ++ builtinWarnings
  warnings.foreach(w => env.console.out(s"Warning: $w"))
  killSessionIfPresent(sessionName, env)
  ... // unchanged removal tail
```

Order of output: built-in action lines → aggregated warnings → kill session →
"Removing worktree…" → "Worktree removed".

## API Contracts

```scala
// core/model/BuildToolCleanup.scala  (pure)
case class BuildToolTeardown(tool: String, command: Seq[String], description: String)
case class BuildToolProbe(millDaemon: Boolean, bloopMarker: Boolean, dockerCompose: Boolean)
object BuildToolCleanup:
  def detect(probe: BuildToolProbe): List[BuildToolTeardown]

// core/commands/BuildToolCleanupRunner.scala  (effectful, best-effort)
object BuildToolCleanupRunner:
  def run(ctx: CleanupContext, env: CommandEnv): List[String]   // warnings only, never throws

// core/commands/CommandEnv.scala  (trait Process — NEW method)
def runIn(cwd: os.Path, command: Seq[String]): ProcessResult

// Gate (in Rm): if config.cleanup.builtin then BuildToolCleanupRunner.run(ctx, env) else Nil
```

**Markers (all under `ctx.worktreePath`):**
| Tool   | Marker probed                    | Guard               | Command run (in worktree cwd)        |
|--------|----------------------------------|---------------------|--------------------------------------|
| Mill   | `out/mill-daemon/`               | `commandExists mill`| `mill --no-server shutdown`          |
| Bloop  | `.bloop/` or `.scala-build/`     | `commandExists bloop`| `bloop exit`                        |
| docker | `docker-compose.yml`             | `commandExists docker`| `docker compose down`              |

## Files to Create / Modify

**Create:**
- `core/model/BuildToolCleanup.scala` — `BuildToolTeardown`, `BuildToolProbe`,
  `BuildToolCleanup.detect`.
- `core/commands/BuildToolCleanupRunner.scala` — `BuildToolCleanupRunner.run`.
- `core/test/BuildToolCleanupTest.scala` — pure `detect` decision tests (munit).

**Modify:**
- `core/adapters/Process.scala` — `ProcessAdapter.run` gains `cwd: os.Path = os.pwd`.
- `core/commands/CommandEnv.scala` — `Process.runIn`.
- `core/commands/LiveCommandEnv.scala` — `LiveProcess.runIn`.
- `core/test/fixtures/FakeCommandEnv.scala` — `FakeProcess.runIn` + `runInList`
  recorder + shared prefix-match lookup.
- `core/commands/Rm.scala` — invoke `BuildToolCleanupRunner.run` gated on
  `config.cleanup.builtin`, aggregate warnings.
- `core/test/RmHarnessTest.scala` — add scenarios 6 (built-in only) and 7
  (built-in disabled).

## Testing Strategy

### Pure decision (`core/test/BuildToolCleanupTest.scala`, NEW)

`BuildToolCleanup.detect` over scripted `BuildToolProbe`:
1. all false → `Nil`.
2. millDaemon only → `[mill --no-server shutdown]`.
3. bloopMarker only → `[bloop exit]`.
4. dockerCompose only → `[docker compose down]`.
5. all true → all three, in Mill, Bloop, docker order.
Assert `tool`, `command`, and ordering.

### Harness (`core/test/RmHarnessTest.scala`) — analysis scenarios 6 & 7

Drive `Rm.run` against `FakeCommandEnv`. The worktree must `exist` for removal to
reach cleanup; set up the fake as the existing Phase-1 cleanup tests do.

- **Scenario 6 — built-in only (no project hook):**
  - `FakeFileSystem.put(worktree / "out" / "mill-daemon", "")` and
    `put(worktree / "docker-compose.yml", "")` (pick ≥1 marker; cover Mill at minimum).
  - `FakeProcess.setExistingCommands(Set("git", "tmux", "mill", "docker"))` (mill/docker
    must be added — absent by default).
  - Assert `FakeProcess.runInList` contains `(worktree, Seq("mill","--no-server","shutdown"))`
    (and docker) — **cwd is the worktree**.
  - Assert the action line ("Shutting down Mill daemon...") appears in console
    output **before** "Removing worktree…".
  - Assert `env.worktree.remove` was called (removal proceeds).
  - A second case: marker present but tool **absent** → `runInList` empty for that
    tool, **no warning** (D2 silent skip), removal still proceeds.
  - A third case: tool present, `scriptResponse(Seq("mill"), ProcessResult(1,"","boom"))`
    → a warning is surfaced, removal still proceeds.

- **Scenario 7 — built-in disabled (`cleanup.builtin = false`):**
  - Config with `CleanupConfig(builtin = false)`; markers present; tools present.
  - Assert `FakeProcess.runInList` has **no** teardown commands (built-in never ran).
  - Removal proceeds exactly as today.

### Regression

- Existing `Rm` flow unchanged when `cleanup.builtin` on but no markers present
  (probe all-false → no teardown, no warnings) and when off.
- Phase-1 project-hook scenarios still pass (built-in runs *after* hooks; a hook
  abort still short-circuits before the built-in).
- Existing `run(command)` call sites unaffected by the `ProcessAdapter.run` cwd
  default (`os.pwd`).

## Acceptance Criteria

- [ ] `BuildToolCleanup.detect` maps each marker to its teardown, pure, ordered, unit-tested.
- [ ] `BuildToolCleanupRunner.run` probes via `env.fs`, executes via
      `env.process.runIn(ctx.worktreePath, …)` behind `commandExists`, returns
      warnings, **never throws**, ignores `force`.
- [ ] `Process.runIn(cwd, command)` added to the trait, `LiveProcess`, and
      `FakeProcess` (with cwd recording); `ProcessAdapter.run` runs in the given cwd.
- [ ] `Rm` runs the built-in after project hooks, gated on `config.cleanup.builtin`,
      aggregating warnings; abort-on-hook-throw still preserves the worktree.
- [ ] Missing tool → silent skip; non-zero/timeout/error → warning; built-in never
      blocks `rm`.
- [ ] Harness scenarios 6 & 7 pass; pure `detect` tests pass.
- [ ] `scala-cli compile --scalac-option -Werror core/` clean; `core.test` and
      `dashboard.test` green.
