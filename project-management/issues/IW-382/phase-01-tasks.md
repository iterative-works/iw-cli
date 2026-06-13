# Phase 1 Tasks: CleanupAction model + config + Rm wiring (project-hook path)

**Issue:** IW-382
**Phase:** 1 of 2
**Estimate:** 3-4.5 hours
**Status:** Not started

These tasks are ordered for TDD: within each behavior, the failing test task
precedes the implementation task that makes it pass. Work top to bottom. Do NOT
introduce any `BuildToolCleanup` code — that is Phase 2.

References:
- Spec: `project-management/issues/IW-382/phase-01-context.md`
- Pattern to mirror: `core/model/RecoveryAction.scala`
- Touch points: `core/model/Config.scala`, `core/model/Constants.scala`,
  `core/commands/Rm.scala`, `core/commands/CommandEnv.scala`,
  `core/commands/LiveCommandEnv.scala`, `core/test/fixtures/FakeCommandEnv.scala`,
  `core/test/RmHarnessTest.scala`, `core/test/ConfigTest.scala`, `test/rm.bats`

---

## Setup

- [ ] [setup] Confirm baseline is green: run `scala-cli compile --scalac-option -Werror core/` and `./iw ./test unit`, note the current `RmHarnessTest` count (13) and `ConfigTest` count so regressions are visible later.
- [ ] [setup] Re-read `core/model/RecoveryAction.scala` and the existing `FakeHookOps` block (`core/test/fixtures/FakeCommandEnv.scala` ~line 642) so the new code mirrors them verbatim in shape and header style.

---

## Domain: `CleanupAction` model (create first; tests reference it)

The domain types have no behavior to test on their own (a pure trait + case
class); they are exercised by the config, harness, and Rm tests below. Create
the file first so those tests compile.

- [ ] [impl] Create `core/model/CleanupAction.scala` with two `// PURPOSE:` header lines (hook trait for worktree-removal cleanup / plugins provide CleanupAction implementations), `package iw.core.model`, the `CleanupContext(worktreePath: os.Path, issueId: String, config: ProjectConfiguration, force: Boolean)` case class, and `trait CleanupAction: def cleanup(ctx: CleanupContext): List[String]`. Mirror `RecoveryAction.scala` exactly.
- [ ] [impl] Add the scaladoc on `CleanupContext` (passed during `iw rm`, after safety checks, before directory removal) and on `CleanupAction.cleanup` documenting the Nil/warn/throw contract (`Nil` => success proceed; non-empty => warnings surfaced, proceed; throw => abort, print error, preserve worktree, exit non-zero).

---

## Config: tests first

- [ ] [test] In `core/test/ConfigTest.scala`, add a test: `fromHocon` on a config with **no** `cleanup` block yields `config.cleanup.builtin == true` (default-when-absent). Run it; it must fail to compile/pass (no `CleanupConfig` / `cleanup` field yet).
- [ ] [test] Add a test: `fromHocon` on a config containing `cleanup { builtin = false }` yields `config.cleanup.builtin == false`.
- [ ] [test] Add a test: `toHocon` of a default config (`builtin = true`) produces output containing **no** `cleanup` block (assert the string does not contain `"cleanup"`), proving default serialization is byte-stable vs. today.
- [ ] [test] Add a test: `toHocon` of a config with `builtin = false` produces output containing `cleanup {` and `builtin = false`, and round-trips back to `false` through `fromHocon` (mirror the existing "ConfigSerializer round-trip" test style at lines ~150-165).
- [ ] [test] Run the new config tests and confirm they fail for the right reason (missing `CleanupConfig` / serializer support), not for unrelated compile errors elsewhere.

## Config: implementation

- [ ] [impl] Add `Constants.ConfigKeys.CleanupBuiltin = "cleanup.builtin"` in `core/model/Constants.scala`, alongside the existing tracker keys in `object ConfigKeys`.
- [ ] [impl] In `core/model/Config.scala`, add `case class CleanupConfig(builtin: Boolean = true)` and the field `cleanup: CleanupConfig = CleanupConfig()` to `ProjectConfiguration` (keep the default so all existing construction sites and `ProjectConfiguration.create(...)` stay source-compatible).
- [ ] [impl] In `ProjectConfigurationJson`, add `given ReadWriter[CleanupConfig] = macroRW` next to the existing `given`s so `ProjectConfiguration`'s derived `ReadWriter` resolves.
- [ ] [impl] In `ConfigSerializer.fromHocon` (`core/model/Config.scala`), read `cleanup.builtin` using the existing `hasPath`-with-default idiom (default `true` when absent) and thread `cleanup = CleanupConfig(builtin = cleanupBuiltin)` into the `ProjectConfiguration(...)` built in the `for`-yield.
- [ ] [impl] In `ConfigSerializer.toHocon`, emit a `cleanup { builtin = false }` block **only when** `config.cleanup.builtin == false` (mirror the conditional emit of `repository`/`teamPrefix`/`baseUrl`); append nothing when `builtin == true` so default configs stay byte-for-byte unchanged.
- [ ] [verify] Run the config tests — all four new ones plus the existing `ConfigTest` suite must pass (default byte-stability test guards no regression).

---

## Hook wiring: `HookOps.cleanupActions`

The fake is what the harness tests need; wire the trait + fake first so the
harness scenarios can inject actions, then the live impl.

- [ ] [impl] Add `def cleanupActions: List[CleanupAction]` to `trait HookOps` in `core/commands/CommandEnv.scala` (alongside `recoveryActions` / `discoverFixActions`). Add the `CleanupAction` import if needed.
- [ ] [impl] In `core/commands/LiveCommandEnv.scala`, `object LiveHookOps`, add `def cleanupActions: List[CleanupAction] = HookDiscovery.collectValues[CleanupAction]`.
- [ ] [impl] In `core/test/fixtures/FakeCommandEnv.scala`, `final class FakeHookOps`, add `private val cleanupActionsRef: AtomicReference[List[CleanupAction]] = AtomicReference(Nil)`, `def setCleanupActions(list: List[CleanupAction]): Unit = cleanupActionsRef.set(list)`, and `def cleanupActions: List[CleanupAction] = cleanupActionsRef.get()`, mirroring the `recoveryActions` precedent exactly. Add `CleanupAction`/`CleanupContext` imports if not already present.
- [ ] [verify] Run `scala-cli compile --scalac-option -Werror core/` to confirm the trait + both impls compile clean before writing the Rm harness scenarios.

---

## Rm orchestration: harness tests first

Write these 5 failing scenarios in `core/test/RmHarnessTest.scala` BEFORE
touching `Rm.scala`. Use `seedConfig(env)` + `env.worktree.addWorktree(wt)` to
reach the removal path (as the existing "clean worktree" test does), inject hooks
via `env.hooks.setCleanupActions(...)`, and assert on `env.console.stdoutLines` /
`env.console.stderr` / `env.worktree.removeCallList`.

- [ ] [test] Scenario 1 — **no hooks → removal proceeds.** `setCleanupActions(Nil)` (or omit); assert `exitCode == 0` and `env.worktree.removeCallList.size == 1`. Explicitly asserts absence of hooks does not change behavior.
- [ ] [test] Scenario 2 — **single success (`Nil`) → proceed, hook ran before removal.** Inject a `CleanupAction` returning `Nil` that records its invocation (flip an `AtomicBoolean` / append to a buffer). Assert removal happened AND the recorded invocation preceded it; optionally assert any hook-emitted console line precedes `"Worktree removed"` in `stdoutLines`.
- [ ] [test] Scenario 3 — **single warnings → printed, proceed.** Hook returns `List("daemon X still running")`. Assert the warning text is in `env.console.stdout`, `exitCode == 0`, `removeCallList.size == 1`, and the warning line index precedes the `"Removing worktree..."` line index in `stdoutLines`.
- [ ] [test] Scenario 4 — **single abort (throws) → error, `worktree.remove` NOT called, `CommandResult.error`.** Hook throws `new RuntimeException("boom")`. Assert `exitCode == 1`, `env.console.stderr` contains the error line, and `env.worktree.removeCallList == Nil` (worktree preserved).
- [ ] [test] Scenario 5 — **multiple hooks → declared order, warnings aggregate.** Inject hook A returning `List("warnA")` and hook B returning `List("warnB")`, both recording order. Assert both warnings appear in declared order (A before B) and removal proceeds.
- [ ] [test] Scenario 5b (variant) — **first hook throws, second never runs, removal skipped.** Inject a throwing hook A and a recording hook B; assert B's recorded-invocation flag stayed false, `removeCallList == Nil`, `exitCode == 1`.
- [ ] [test] Run `RmHarnessTest`; confirm the new scenarios fail (no cleanup wiring in `Rm` yet) while the existing 13 tests still pass.

## Rm orchestration: implementation

- [ ] [impl] In `core/commands/Rm.scala`, add a private helper `runCleanupHooks(ctx: CleanupContext, env: CommandEnv): Either[CommandResult, List[String]]` that folds over `env.hooks.cleanupActions` in declared order, wrapping each `action.cleanup(ctx)` in a `try`/`catch` (or `scala.util.Try`); on the first caught `Throwable` it prints `env.console.err(s"Error: Cleanup hook failed: ${e.getMessage}")`, stops the fold, and returns `Left(CommandResult.error)`; otherwise it aggregates the returned warning lists and returns `Right(warnings)`.
- [ ] [impl] In `removeWorktree`, in the `decideForce(...) match` → `Right(forceRemove)` branch, BEFORE `killSessionIfPresent` / `"Removing worktree..."` / `env.worktree.remove`, build `val ctx = CleanupContext(worktreePath = targetPath, issueId = issueId.value, config = config, force = forceRemove)` (use `forceRemove`, the post-confirm decision, for `force`).
- [ ] [impl] Call `runCleanupHooks(ctx, env)`: on `Left(result)` return `result` immediately (worktree NOT removed); on `Right(warnings)` print each warning line via `env.console.out` (consider a `Warning:` prefix matching `killSessionIfPresent`'s style) BEFORE falling through to the existing `killSessionIfPresent` → `"Removing worktree..."` → `env.worktree.remove` → unregister/branch-note → `CommandResult.ok` tail (which stays exactly as-is).
- [ ] [impl] Add the `CleanupContext` import to `Rm.scala`'s `iw.core.model` import block.
- [ ] [verify] Run `RmHarnessTest`; all 5 (+5b) new scenarios and the existing 13 must pass.

---

## Integration: E2E smoke

- [ ] [integration] In `test/rm.bats`, add a second `@test` that writes an inline `*.hook-rm.scala` into a discoverable location (`.iw/commands/` in the temp project, mirroring how `plugin-hooks.bats` creates hook files at runtime). The hook is an `object` exposing a `CleanupAction` `val` whose `cleanup` prints a sentinel line (e.g. `println("HOOK_RAN")`) and returns `Nil`. Keep the existing `setup()` exports `IW_SERVER_DISABLED=1` and `IW_TMUX_SOCKET`.
- [ ] [integration] In that `@test`, create a worktree, run `"$PROJECT_ROOT/iw" rm IWLE-xxx`, assert `status -eq 0`, and assert the sentinel line appears in `output` **before** `"Worktree removed"` (compare line indices or `grep -n` ordering).

---

## Verification

- [ ] [verify] `scala-cli compile --scalac-option -Werror core/` is clean — no warnings.
- [ ] [verify] `./iw ./test unit` passes (core.test + dashboard.test): new `ConfigTest` + `RmHarnessTest` scenarios green, existing tests unregressed.
- [ ] [verify] Run the rm bats smoke (`./iw ./test e2e` or the single `test/rm.bats`); the cleanup-hook ordering assertion passes.
- [ ] [verify] Walk the phase-01-context Acceptance Criteria checklist and confirm each item is satisfied; in particular confirm NO `BuildToolCleanup` code was introduced and nothing yet reads `cleanup.builtin` (Phase 2 adds the gated call).
