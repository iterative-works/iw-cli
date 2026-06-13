# Phase 1: CleanupAction model + config + Rm wiring (project-hook path)

**Issue:** IW-382
**Phase:** 1 of 2
**Estimate:** 3-4.5 hours
**Status:** Not started

## Goals

Deliver the full **project-hook cleanup path** for `iw rm` end to end. After this
phase, a project can drop a `*.hook-rm.scala` file exposing a `CleanupAction`,
and `iw rm` will:

1. Discover the hook reflectively (via the existing `HookDiscovery` pass — `rm`
   is a shared command so `iw-run` already auto-discovers `*.hook-rm.scala`; **no
   `iw-run` change is required**).
2. Run all discovered hooks in declared order **after** the force/session safety
   checks pass but **before** `env.worktree.remove`.
3. Aggregate hook warnings (non-empty return lists) and surface them to the user,
   then proceed with removal.
4. If a hook **throws**, print an error line, return `CommandResult.error`, and
   **skip removal** — the worktree is preserved.
5. Thread `--force` into `CleanupContext.force` so hooks can decide whether to
   escalate.

This phase also adds the `cleanup.builtin` config knob (default `true`) and a new
`Constants` key, but **does not** implement the built-in `BuildToolCleanup`
runner — that is Phase 2. The config field exists here so Phase 2 can gate on it.

## Scope

### IN scope (Phase 1)

- **Domain:** `CleanupAction` trait + `CleanupContext` case class in
  `core/model/CleanupAction.scala` (mirrors `RecoveryAction.scala`).
- **Domain/config:** `CleanupConfig(builtin: Boolean = true)` sub-config on
  `ProjectConfiguration`; parsed under a `cleanup { }` block in
  `ConfigSerializer.fromHocon` (default `true` when absent); `toHocon` emits a
  `cleanup { builtin = false }` block **only when non-default**; new config-key
  constant in `Constants.ConfigKeys`.
- **Infrastructure:** `HookOps.cleanupActions: List[CleanupAction]` on the
  `HookOps` trait; `LiveHookOps` reflection impl; `FakeHookOps.setCleanupActions`
  + backing `AtomicReference`.
- **Infrastructure:** `Rm` invocation point in `removeWorktree` — orchestration,
  abort-vs-warn semantics, `--force` passthrough.
- **Presentation:** per-hook action line + aggregated warnings printed before
  "Removing worktree…"; abort error line. Ordering is asserted by the smoke test.
- **Tests:** the 5 harness scenarios that **don't** need the built-in (no hooks /
  single success / single warnings / single abort / multiple) in
  `core/test/RmHarnessTest.scala`; ONE BATS smoke in `test/rm.bats`.

### OUT of scope (Phase 2)

- The built-in `BuildToolCleanup` — pure `detect` decision function + effectful
  `BuildToolCleanup.run(ctx, env)` runner invoked directly by `Rm`.
- Mill / Bloop / docker-compose teardown subprocess logic.
- The built-in-only and built-in-disabled harness scenarios.

**Do not** wire any built-in invocation into `Rm` in this phase. The `cleanup.builtin`
config field is added now, but nothing reads it yet — Phase 2 adds the gated call.

## Dependencies (what already exists)

The #331 action-hook pattern is landed and is the template to mirror **verbatim**.

### `RecoveryAction.scala` (the shape to mirror)

`core/model/RecoveryAction.scala` — exact current content:

```scala
// PURPOSE: Hook trait for phase-merge CI failure recovery actions
// PURPOSE: Plugins provide RecoveryAction implementations to fix CI failures automatically

package iw.core.model

/** Context passed to recovery action hooks when CI checks fail during
  * phase-merge.
  */
case class RecoveryContext(
    failedChecks: List[CICheckResult],
    prUrl: String,
    branch: String,
    attempt: Int,
    maxRetries: Int
)

/** Hook trait for CI failure recovery in phase-merge.
  *
  * Implementations run an external tool to fix CI failures. Returns exit code
  * (0 = success).
  */
trait RecoveryAction:
  def recover(ctx: RecoveryContext): Int
```

Siblings confirming the convention (two `// PURPOSE:` lines, context case class +
trait in one file, pure `os.Path` field in context):
- `core/model/SessionAction.scala` — `SessionContext(sessionName, worktreePath: os.Path, issueId, prompt)`, `trait SessionAction: def run(ctx): Option[String]`.
- `core/model/FixAction.scala` — `DoctorFixContext(failedChecks, buildSystem, ciPlatform, config: ProjectConfiguration)`, `trait FixAction: def fix(ctx): Int`. **Note:** `FixAction` already proves a context carrying `config: ProjectConfiguration` — that is the precedent for putting `config` in `CleanupContext`.

### `HookDiscovery.collectValues[T]`

`core/adapters/HookDiscovery.scala`:

```scala
def collectValues[T: ClassTag]: List[T]
```

Reads `IW_HOOK_CLASSES`, reflectively loads each named Scala object, invokes all
no-arg methods whose return type is assignable to `T`, and collects results.
The new `cleanupActions` live impl is one line: `HookDiscovery.collectValues[CleanupAction]`.

### `HookOps` trait (current)

`core/commands/CommandEnv.scala` (~line 211):

```scala
trait HookOps:
  def recoveryActions: List[RecoveryAction]
  def runSessionHooks(ctx: SessionContext): SessionHookResult
  def discoverChecks: List[Check]
  def discoverFixActions: List[FixAction]
```

### `LiveHookOps` (current)

`core/commands/LiveCommandEnv.scala` (~line 304):

```scala
object LiveHookOps extends HookOps:
  def recoveryActions: List[RecoveryAction] =
    HookDiscovery.collectValues[RecoveryAction]
  def runSessionHooks(ctx: SessionContext): SessionHookResult =
    SessionHooks.run(ctx)
  def discoverChecks: List[Check] =
    HookDiscovery.collectValues[Check]
  def discoverFixActions: List[FixAction] =
    HookDiscovery.collectValues[FixAction]
```

### `FakeHookOps` (current)

`core/test/fixtures/FakeCommandEnv.scala` (~line 642):

```scala
final class FakeHookOps extends HookOps:
  private val actionsRef: AtomicReference[List[RecoveryAction]] =
    AtomicReference(Nil)
  // ...
  private val checksRef: AtomicReference[List[Check]] = AtomicReference(Nil)
  private val fixActionsRef: AtomicReference[List[FixAction]] =
    AtomicReference(Nil)
  def setRecoveryActions(list: List[RecoveryAction]): Unit =
    actionsRef.set(list)
  def setDiscoveredChecks(list: List[Check]): Unit = checksRef.set(list)
  def setDiscoveredFixActions(list: List[FixAction]): Unit =
    fixActionsRef.set(list)
  def recoveryActions: List[RecoveryAction] = actionsRef.get()
  def discoverChecks: List[Check] = checksRef.get()
  def discoverFixActions: List[FixAction] = fixActionsRef.get()
```

### `CommandEnv` capabilities the wiring uses

- `env.hooks: HookOps` (where `cleanupActions` lives).
- `env.worktree.remove(path, workDir, force): Either[String, Unit]` (the call to skip on abort).
- `env.console.out / env.console.err` (presentation).
- `env.cwd: os.Path`; `WorktreePath(...).resolve(env.cwd): os.Path` (the `CleanupContext.worktreePath`).
- `CommandResult.ok` (`CommandResult(0)`) / `CommandResult.error` (`CommandResult(1)`).

### Config model (current)

`core/model/Config.scala`:
- `case class ProjectConfiguration(tracker: TrackerConfig, project: ProjectConfig, version: Option[String] = Some("latest"))` plus backward-compat accessors.
- `object ConfigSerializer` with `toHocon` (emits `tracker { … }`, `project { … }`, optional `version`) and `fromHocon` (uses `config.hasPath(...)` to read optional fields, supplying defaults when absent). This `hasPath`-with-default idiom is exactly what `cleanup.builtin` follows.

## Component Specifications

### 1. `CleanupAction` trait + `CleanupContext` (`core/model/CleanupAction.scala`, NEW)

Mirror `RecoveryAction.scala` exactly in shape and header style. The context
carries `config: ProjectConfiguration` (precedent: `DoctorFixContext`) and
`force: Boolean`.

```scala
// PURPOSE: Hook trait for worktree-removal cleanup actions in iw rm
// PURPOSE: Plugins provide CleanupAction implementations to tear down project daemons before removal

package iw.core.model

/** Context passed to cleanup hooks during `iw rm`, after safety checks pass and
  * before the worktree directory is removed.
  */
case class CleanupContext(
    worktreePath: os.Path,
    issueId: String,
    config: ProjectConfiguration,
    force: Boolean
)

/** Hook trait for teardown before worktree removal.
  *
  * Implementations shut down project-spawned processes (build daemons, dev
  * servers, docker stacks) rooted in the worktree before it is removed.
  *
  * Return contract:
  *   - `Nil`         => success; `rm` proceeds.
  *   - non-empty list => warnings; each string is surfaced to the user and
  *                       `rm` still proceeds.
  *   - throwing      => abort; `rm` prints the error, preserves the worktree,
  *                       and exits non-zero.
  */
trait CleanupAction:
  def cleanup(ctx: CleanupContext): List[String]
```

Notes:
- `worktreePath` is an `os.Path` **value** (not an effect) — consistent with
  `SessionContext.worktreePath`. No `CommandEnv`/capabilities leak into the
  context; project hooks see only pure data. (This is why the built-in cannot be
  a `CleanupAction` — it needs `env` — and is Phase 2's direct-invocation path.)
- `issueId` is the raw `String` value (matches `SessionContext.issueId: String`).

### 2. `CleanupConfig` + config key + serializer (`core/model/Config.scala`, `Constants.scala`, MODIFY)

**a. `CleanupConfig` sub-config and field on `ProjectConfiguration`** (`core/model/Config.scala`):

```scala
case class CleanupConfig(
    builtin: Boolean = true
)
```

Add to `ProjectConfiguration`:

```scala
case class ProjectConfiguration(
    tracker: TrackerConfig,
    project: ProjectConfig,
    version: Option[String] = Some("latest"),
    cleanup: CleanupConfig = CleanupConfig()
):
```

Keep the default so every existing construction site and `ProjectConfiguration.create(...)`
remains source-compatible. Add a `ReadWriter[CleanupConfig]` (`macroRW`) in
`ProjectConfigurationJson` next to the existing `given`s, since
`ProjectConfiguration`'s derived `ReadWriter` will now need it.

**b. `Constants.ConfigKeys` new key** (`core/model/Constants.scala`):

Add alongside the existing tracker keys:

```scala
val CleanupBuiltin = "cleanup.builtin"
```

**c. `ConfigSerializer.fromHocon`** (`core/model/Config.scala`):

Parse with the existing `hasPath`-with-default idiom; default `true`:

```scala
val cleanupBuiltin =
  if config.hasPath(Constants.ConfigKeys.CleanupBuiltin) then
    config.getBoolean(Constants.ConfigKeys.CleanupBuiltin)
  else true
```

Then thread `cleanup = CleanupConfig(builtin = cleanupBuiltin)` into the
`ProjectConfiguration(...)` constructed in the `for`-yield.

**d. `ConfigSerializer.toHocon`** (`core/model/Config.scala`):

Emit a `cleanup` block **only when non-default** (`builtin == false`), mirroring
how `repository`/`teamPrefix`/`baseUrl` are conditionally emitted (they use
`Option.map(...)` flattened into a `Seq`). Append after the `project { }` /
`versionLine` segment:

```scala
val cleanupBlock =
  if config.cleanup.builtin then ""
  else "\n\ncleanup {\n  builtin = false\n}"
```

…and append `$cleanupBlock` to the returned string. Default configs stay byte-for-byte unchanged.

### 3. `HookOps.cleanupActions` (`CommandEnv.scala`, `LiveCommandEnv.scala`, `FakeCommandEnv.scala`, MODIFY)

**Trait** (`core/commands/CommandEnv.scala`, in `trait HookOps`):

```scala
def cleanupActions: List[CleanupAction]
```

**Live** (`core/commands/LiveCommandEnv.scala`, in `object LiveHookOps`):

```scala
def cleanupActions: List[CleanupAction] =
  HookDiscovery.collectValues[CleanupAction]
```

**Fake** (`core/test/fixtures/FakeCommandEnv.scala`, in `final class FakeHookOps`),
mirroring the `recoveryActions` precedent exactly:

```scala
private val cleanupActionsRef: AtomicReference[List[CleanupAction]] =
  AtomicReference(Nil)
def setCleanupActions(list: List[CleanupAction]): Unit =
  cleanupActionsRef.set(list)
def cleanupActions: List[CleanupAction] = cleanupActionsRef.get()
```

(Add the `CleanupAction` / `CleanupContext` imports to the relevant `imports`
blocks; `FakeCommandEnv.scala` already imports model types.)

### 4. `Rm` invocation point (`core/commands/Rm.scala`, MODIFY)

**Exact location:** inside `removeWorktree`, in the `decideForce(...) match` →
`Right(forceRemove)` branch. Current code (lines ~89-104):

```scala
case Right(forceRemove) =>
  killSessionIfPresent(sessionName, env)
  env.console.out(
    s"Removing worktree '${worktreePath.directoryName}'..."
  )
  env.worktree.remove(targetPath, env.cwd, force = forceRemove) match
    case Left(err) => ...
    case Right(_)  => ... CommandResult.ok
```

The cleanup orchestration goes **after** `decideForce` returns `Right`, and
**before** `killSessionIfPresent` / the "Removing worktree…" line / `env.worktree.remove`.
(Running it before `killSessionIfPresent` is the right order: hooks may want the
session alive to message it; the analysis only mandates "before `env.worktree.remove`".)

Orchestration (extract to a private helper, e.g. `runCleanupHooks(...)`, returning
`Either[CommandResult, List[String]]` — `Left` = abort, `Right(warnings)` = proceed):

1. Build the context once:
   ```scala
   val ctx = CleanupContext(
     worktreePath = targetPath,   // already env.cwd-resolved at line 72
     issueId = issueId.value,
     config = config,
     force = forceRemove
   )
   ```
   Use `forceRemove` (the post-confirm decision), not the raw `force` flag, so a
   user who confirmed removal of a dirty tree gets `force = true` in the context —
   consistent with what gets passed to `env.worktree.remove`. (If the analysis's
   "pass `--force` through" is read strictly as the raw flag, flag this for Michal;
   `forceRemove` is the more coherent choice and matches `env.worktree.remove`.)

2. Fold over `env.hooks.cleanupActions` **in declared order**, aggregating
   warnings; on the **first thrown exception**, print the error line and abort:
   ```scala
   val hooks = env.hooks.cleanupActions
   // foldLeft, short-circuiting on throw:
   //   try action.cleanup(ctx) -> append to warnings
   //   catch e => env.console.err(s"Error: Cleanup hook failed: ${e.getMessage}")
   //              return Left(CommandResult.error)  // worktree NOT removed
   ```
   Use a `scala.util.Try`/`try…catch` around each `action.cleanup(ctx)`; the first
   `Failure`/caught `Throwable` stops the fold and returns `Left(CommandResult.error)`.

3. On success, print each aggregated warning (`env.console.out(...)` per warning,
   or a `Warning:`-prefixed line — see Presentation) and return `Right(warnings)`.

4. Back in `removeWorktree`, on `Left(result)` short-circuit (return it,
   **`env.worktree.remove` is never called**); on `Right(_)` fall through to the
   existing `killSessionIfPresent` → "Removing worktree…" → `env.worktree.remove`
   → `CommandResult.ok` tail unchanged.

The unregister / branch-note / `CommandResult.ok` tail stays exactly as-is.

### 5. Presentation lines

- **Per-hook action line:** optional but recommended — a line indicating cleanup
  is running, printed before "Removing worktree…". At minimum, hook **warnings**
  must be printed before "Removing worktree…" so ordering is observable.
- **Aggregated warnings:** one `env.console.out` line per warning string returned
  by hooks (consider a `Warning:` prefix to match `killSessionIfPresent`'s
  `"Warning: Failed to kill session: …"` style), printed before "Removing
  worktree…".
- **Abort error line:** `env.console.err(s"Error: Cleanup hook failed: …")` (the
  `"Error: …"` prefix matches every other error line in `Rm`).
- **Ordering guarantee:** all cleanup output (hook lines + warnings, or the abort
  error) is emitted **before** "Removing worktree…" / "Worktree removed". The BATS
  smoke asserts the hook's known line appears before "Worktree removed".

## API Contracts

```scala
// core/model/CleanupAction.scala
case class CleanupContext(
    worktreePath: os.Path,
    issueId: String,
    config: ProjectConfiguration,
    force: Boolean
)
trait CleanupAction:
  def cleanup(ctx: CleanupContext): List[String]
//   Nil          => success, proceed
//   non-empty    => warnings, proceed (each surfaced to user)
//   throw        => abort, print error, preserve worktree, CommandResult.error

// core/model/Config.scala
case class CleanupConfig(builtin: Boolean = true)
// ProjectConfiguration gains: cleanup: CleanupConfig = CleanupConfig()
// ConfigSerializer.fromHocon: reads cleanup.builtin (default true)
// ConfigSerializer.toHocon: emits `cleanup { builtin = false }` only when builtin == false

// core/model/Constants.scala
Constants.ConfigKeys.CleanupBuiltin: String = "cleanup.builtin"

// core/commands/CommandEnv.scala (trait HookOps)
def cleanupActions: List[CleanupAction]

// core/commands/LiveCommandEnv.scala (object LiveHookOps)
def cleanupActions: List[CleanupAction] = HookDiscovery.collectValues[CleanupAction]

// core/test/fixtures/FakeCommandEnv.scala (final class FakeHookOps)
def setCleanupActions(list: List[CleanupAction]): Unit
def cleanupActions: List[CleanupAction]

// core/commands/Rm.scala (private helper, suggested signature)
private def runCleanupHooks(
    ctx: CleanupContext,
    env: CommandEnv
): Either[CommandResult, List[String]]
//   Left(CommandResult.error) => a hook threw; worktree must NOT be removed
//   Right(warnings)           => proceed; warnings already printed (or to be printed by caller)
```

**Abort-vs-warn semantics (authoritative):**
- Hook returns `Nil` → no output for that hook, proceed.
- Hook returns `List("w1", "w2")` → print `w1`, `w2`, proceed.
- Hook throws → print error, return `CommandResult.error`, **do not** call
  `env.worktree.remove`, **do not** run subsequent hooks.
- Multiple hooks → run in `env.hooks.cleanupActions` order; warnings from all
  hooks that ran before any abort are aggregated.

## Files to Create / Modify

**Create:**
- `core/model/CleanupAction.scala` — `CleanupContext` + `CleanupAction` trait.

**Modify:**
- `core/model/Config.scala` — `CleanupConfig`, `ProjectConfiguration.cleanup`
  field, `ConfigSerializer.fromHocon` parse, `ConfigSerializer.toHocon` emit,
  `ProjectConfigurationJson` `ReadWriter[CleanupConfig]`.
- `core/model/Constants.scala` — `ConfigKeys.CleanupBuiltin`.
- `core/commands/CommandEnv.scala` — `HookOps.cleanupActions`.
- `core/commands/LiveCommandEnv.scala` — `LiveHookOps.cleanupActions`.
- `core/test/fixtures/FakeCommandEnv.scala` — `FakeHookOps` ref + setter + getter.
- `core/commands/Rm.scala` — cleanup orchestration in `removeWorktree`.
- `core/test/RmHarnessTest.scala` — **already exists** (13 tests today); add the 5
  cleanup scenarios.
- `test/rm.bats` — **already exists** (one test today); add the cleanup smoke test.
- `core/test/ConfigTest.scala` — add `cleanup.builtin` parse + round-trip tests.

**Possibly create (BATS fixture):**
- A throwaway `*.hook-rm.scala` written inline by the BATS test (no checked-in
  fixture file needed; mirror how `plugin-hooks.bats` writes hook files into a
  temp dir at runtime).

## Testing Strategy

### Harness (`core/test/RmHarnessTest.scala`) — 5 Phase-1 scenarios

These extend the existing suite. Use `env.hooks.setCleanupActions(...)` to inject
test `CleanupAction` instances (anonymous classes/lambdas), `seedConfig(env)` +
`env.worktree.addWorktree(wt)` to reach the removal path (as the existing "clean
worktree" test does), and assert on `env.console.stdoutLines` /
`env.worktree.removeCallList`.

1. **No hooks → removal proceeds.** `setCleanupActions(Nil)` (or omit). Assert
   `exitCode == 0`, `env.worktree.removeCallList.size == 1`. (Largely covered by
   the existing "clean worktree" test; add an explicit assertion that absence of
   hooks does not change behavior.)
2. **Single hook success (`Nil`) → removal proceeds, hook ran before removal.**
   Inject a hook returning `Nil` that records it was invoked (e.g. flips an
   `AtomicBoolean` or appends to a buffer). Assert removal happened **and** the
   hook's recorded invocation preceded it (the hook ran during orchestration,
   which is before `env.worktree.remove`). Optionally assert any hook-emitted
   console line precedes `"Worktree removed"` in `stdoutLines`.
3. **Single hook warnings → warnings printed, removal proceeds.** Hook returns
   `List("daemon X still running")`. Assert that warning text is in
   `env.console.stdout`, `exitCode == 0`, `removeCallList.size == 1`, and the
   warning line index precedes the `"Removing worktree…"` line index.
4. **Single hook abort (throws) → error printed, `worktree.remove` NOT called,
   `CommandResult.error`.** Hook throws `new RuntimeException("boom")`. Assert
   `exitCode == 1`, `env.console.stderr` contains the error, and
   `env.worktree.removeCallList == Nil` (worktree preserved).
5. **Multiple hooks → declared order, warnings aggregate.** Inject two hooks; hook
   A returns `List("warnA")`, hook B returns `List("warnB")`; both record order.
   Assert both warnings appear, in declared order (A before B), and removal
   proceeds. (Optionally add a variant where the **first** hook throws and assert
   the second hook never ran and removal was skipped.)

### Config tests (`core/test/ConfigTest.scala`)

- `fromHocon` with no `cleanup` block → `config.cleanup.builtin == true`.
- `fromHocon` with `cleanup { builtin = false }` → `false`.
- `toHocon` of a default config (`builtin = true`) → output contains **no**
  `cleanup` block (byte-stable vs. today).
- `toHocon` of `builtin = false` → output contains `cleanup { builtin = false }`,
  and round-trips back to `false` through `fromHocon` (mirror the existing
  "ConfigSerializer round-trip" test style at lines 150-165).

### E2E smoke (`test/rm.bats`) — ONE scenario

Add a second `@test` that:
- Writes a trivial `*.hook-rm.scala` into a discoverable location (mirror
  `plugin-hooks.bats`' runtime hook-file creation; since `rm` is a shared command,
  a project hook in `.iw/commands/*.hook-rm.scala` is auto-discovered by `iw-run`).
  The hook is an `object` exposing a `CleanupAction` `val` whose `cleanup` prints a
  known sentinel line (e.g. `println("HOOK_RAN")`) and returns `Nil`.
- Runs `"$PROJECT_ROOT/iw" rm IWLE-xxx`.
- Asserts the sentinel line appears in output **before** `"Worktree removed"`
  (capture `output`, compare line indices, or `grep -n` ordering).
- Keeps the existing `setup()` exports: `IW_SERVER_DISABLED=1`, `IW_TMUX_SOCKET`.

BATS stays a wiring smoke test only — detailed behavior is in the harness.

## Acceptance Criteria

- [ ] `core/model/CleanupAction.scala` exists with two `// PURPOSE:` header lines,
      `CleanupContext(worktreePath: os.Path, issueId: String, config: ProjectConfiguration, force: Boolean)`,
      and `trait CleanupAction: def cleanup(ctx: CleanupContext): List[String]` with the
      Nil/warn/throw contract documented in scaladoc.
- [ ] `CleanupConfig(builtin: Boolean = true)` exists; `ProjectConfiguration` has a
      `cleanup: CleanupConfig = CleanupConfig()` field; all existing construction
      sites still compile.
- [ ] `Constants.ConfigKeys.CleanupBuiltin == "cleanup.builtin"`.
- [ ] `ConfigSerializer.fromHocon` defaults `cleanup.builtin` to `true` when absent
      and reads `false` when present; `toHocon` emits the `cleanup` block **only**
      when `builtin == false`; default configs serialize byte-identically to today.
- [ ] `HookOps.cleanupActions` declared; `LiveHookOps.cleanupActions =
      HookDiscovery.collectValues[CleanupAction]`; `FakeHookOps` has
      `setCleanupActions` + backing `AtomicReference` + getter.
- [ ] `Rm.removeWorktree` runs discovered hooks in declared order after the
      force/session checks and before `env.worktree.remove`, threading
      `forceRemove` into `CleanupContext.force`.
- [ ] A throwing hook aborts: error printed, `CommandResult.error`,
      `env.worktree.remove` not called, subsequent hooks not run.
- [ ] Hook warnings aggregate and print before "Removing worktree…"; removal proceeds.
- [ ] The 5 harness scenarios pass; the existing 13 `RmHarnessTest` tests still pass
      (no regression to force-prompt / session-refusal / unregister / branch-note).
- [ ] Config parse + round-trip tests pass.
- [ ] The `test/rm.bats` smoke asserts the hook's sentinel line precedes
      "Worktree removed".
- [ ] `scala-cli compile --scalac-option -Werror core/` is clean (no warnings).
- [ ] No built-in `BuildToolCleanup` code is introduced (deferred to Phase 2).
