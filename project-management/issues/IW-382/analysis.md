# Technical Analysis: Cleanup hook on worktree removal — stop project daemons before iw rm

**Issue:** IW-382
**Created:** 2026-06-12
**Status:** Draft

## Problem Statement

`./iw rm` removes a worktree directory but leaves project-spawned processes running. Two distinct classes of orphaned processes accumulate:

1. **Build-tool daemons** — Mill servers, Bloop (via scala-cli), and sbt BSP. These are long-lived and resource-hungry. A real observation logged 14 orphaned Mill/Bloop processes consuming ~2775% CPU and ~50% RAM, accumulated across worktrees that were removed without shutting their daemons down first (supersedes #225).

2. **Application-level processes** — docker-compose stacks, dev servers, long-running test runners: anything a project's `start` workflow brings up (supersedes #147).

Today the only workaround is destructive and circular: re-create the deleted worktree, run the project's shutdown by hand, then `./iw rm` again. There is no extension point where a project (or iw-cli itself) can run teardown before the directory disappears.

The value: `./iw rm` becomes a clean teardown that reclaims daemon CPU/RAM and lets projects shut down their own stacks, without the user remembering a manual shutdown ritual. This is valuable for anyone running multiple concurrent worktrees against a JVM build tool — the exact target audience of iw-cli.

## Proposed Solution

### High-Level Approach

Introduce a `CleanupAction` trait + `CleanupContext` case class in `core/model/`, mirroring the action-hook pattern landed in #331 (`SessionAction` / `FixAction` / `RecoveryAction`). Projects drop a `*.hook-rm.scala` file exposing a `CleanupAction`; `iw rm` discovers these via the existing reflection pass (`HookDiscovery.collectValues[CleanupAction]`) at a single point — after the force/session safety checks pass but **before** `env.worktree.remove`. Hooks run in declared order; their returned warnings aggregate and surface to the user while `rm` proceeds; the first hook that *throws* aborts `rm` and preserves the worktree.

Ship a built-in `BuildToolCleanup` so projects get sensible defaults without writing any hook: detect `out/mill-daemon/` under the worktree → shut down Mill; detect a Bloop server rooted in the worktree → `bloop exit`; detect `docker-compose.yml` at the worktree root → `docker compose down`. The built-in is best-effort and never blocks `rm`, and is opt-out via `cleanup.builtin = false` in `.iw/config.conf`.

A critical architectural distinction drives the design: **the built-in cannot be a reflectively-discovered `CleanupAction`**. Reflection (`HookDiscovery`) only loads project hook objects named in `IW_HOOK_CLASSES`, and a no-arg `cleanup(ctx)` method has no handle to the `CommandEnv` capabilities (`env.process`, `env.fs`) it needs to probe the filesystem and spawn subprocesses in a testable way. So the built-in is invoked **directly by the `Rm` command** through `CommandEnv`, kept separate from the reflective `cleanupActions` discovery path. Its detection logic is a pure decision function (filesystem state → list of teardown actions); execution lives at the edge via `env`.

### Why This Approach

The #331 reflection-discovery pattern is proven, and `iw-run` already auto-discovers `*.hook-rm.scala` from `commands/` and `.iw/commands/` because `rm` is a shared command (`commands/rm.scala`) — so **no `iw-run` change is required** for project-hook discovery. Reusing the pattern means project hooks need zero new infrastructure.

The built-in-as-direct-invocation split is the honest consequence of the FCIS boundary: hooks discovered by reflection are pure consumers of a context; anything needing live capabilities (subprocess, filesystem) belongs to the imperative shell and must be wired through `CommandEnv` to stay harness-testable. Forcing the built-in through the reflection path would either make it untestable (calling `os`/`ProcessAdapter` directly) or require smuggling a capability into `CleanupContext`, which would pollute the pure model contract that project hooks see. Keeping the two paths separate is the smaller, cleaner change.

## Architecture Design

**Purpose:** Define WHAT components each layer needs, not HOW they're implemented.

### Domain Layer (Model)

**Components:**
- `CleanupAction` trait + `CleanupContext` case class in `core/model/CleanupAction.scala` (two `// PURPOSE:` lines, matching `RecoveryAction.scala` style).
  - `CleanupContext(worktreePath: os.Path, issueId: String, config: ProjectConfiguration, force: Boolean)`
  - `def cleanup(ctx: CleanupContext): List[String]` — `Nil` on success, non-empty list = warnings (proceed), throw = abort.
- `cleanup.builtin` configuration field added to `ProjectConfiguration` (via a `CleanupConfig(builtin: Boolean = true)` sub-config or a flat accessor — see CLARIFY), parsed in `ConfigSerializer.fromHocon`, default `true` (opt-out).
- New config key string constant in `core/model/Constants.scala` (where `EnvVars.IwHookClasses` and path/key constants live).
- Pure decision type for the built-in, e.g. `BuildToolCleanup.detect(worktreePath, fsProbeResults): List[CleanupCommand]` returning the set of teardown commands to run given filesystem state. Pure — no I/O.

**Responsibilities:**
- Define the hook contract project hooks satisfy, with the abort-vs-warn semantics encoded purely in the return type / thrown-exception convention.
- Carry context to hooks without coupling to I/O (`os.Path` is a value here, not an effect).
- Encode the built-in's *decision* (which daemons to act on) as a pure function, keeping effects out of model.
- Default `cleanup.builtin = true`; preserve config round-trip semantics.

**Estimated Effort:** 1-1.5 hours
**Complexity:** Straightforward

---

### Application Layer (Built-in Cleanup Logic)

**Components:**
- `BuildToolCleanup` effectful runner — invoked directly by `Rm`, takes `(ctx: CleanupContext, env)` (exact shape is a CLARIFY). Probes filesystem via `env.fs` (`out/mill-daemon/`, `docker-compose.yml`, Bloop state file), feeds results to the pure `detect` function, then executes the resulting commands via `env.process` (`commandExists` guard + `run`). Best-effort: never throws to the `Rm` caller in a way that blocks removal; collects warnings instead.
- Mill teardown: `mill --no-server shutdown` (or TERM the recorded PIDs under `out/mill-daemon/`).
- Bloop teardown: parse the scala-cli Bloop state to determine whether a server's CWD is inside the worktree, then `bloop exit` (path/format portability is a CLARIFY).
- docker-compose teardown: `docker compose down` at worktree root, gated so it does not double-run when a project hook already handled it (gating mechanism is a CLARIFY).

**Responsibilities:**
- Translate filesystem state into concrete teardown subprocess invocations.
- Guard every subprocess behind `commandExists` so a missing tool degrades to a warning, not a failure.
- Honor `cleanup.builtin = false` (the gate lives in `Rm`, but the runner must be a no-op-friendly call).
- Never block `rm` — all failures become warnings.

**Estimated Effort:** 2.5-4 hours
**Complexity:** Moderate (Bloop state parsing and the docker-compose dedup are the variable cost)

---

### Infrastructure Layer (Imperative Shell — Command Wiring)

**Components:**
- `HookOps.cleanupActions: List[CleanupAction]` added to the `HookOps` trait on `CommandEnv` (`core/commands/CommandEnv.scala`), alongside the existing `recoveryActions` / `discoverFixActions` / `discoverChecks`.
- `LiveHookOps` impl (`core/commands/LiveCommandEnv.scala`, ~line 304): `def cleanupActions = HookDiscovery.collectValues[CleanupAction]`.
- `FakeHookOps` (`core/test/fixtures/FakeCommandEnv.scala`, ~line 642): backing `AtomicReference[List[CleanupAction]]` + `setCleanupActions(list)` setter + getter, matching `setRecoveryActions` precedent.
- `Rm` command invocation point (`core/commands/Rm.scala`): inside `removeWorktree`, **after** the force/session checks and **before** `env.worktree.remove`:
  1. Resolve `worktreePath.resolve(env.cwd)` → `os.Path` for `CleanupContext.worktreePath`.
  2. If `config.cleanup.builtin` is on, run `BuildToolCleanup` through `env` → collect warnings.
  3. Run discovered `env.hooks.cleanupActions` in order; aggregate warnings; first thrown error → print error, return `CommandResult.error`, **skip `env.worktree.remove`** (worktree preserved).
  4. Print aggregated warnings, then proceed to the existing removal flow.

**Responsibilities:**
- Bridge reflection discovery and the built-in into the existing `Rm` control flow without disturbing the unregister / branch-note / `CommandResult.ok` tail.
- Enforce abort-on-throw (preserve worktree) vs warn-and-proceed.
- Pass `--force` through into `CleanupContext.force`.

**Estimated Effort:** 1.5-2.5 hours
**Complexity:** Moderate

---

### Presentation Layer

**Components:**
- Output lines: a per-hook / per-built-in action line printed *before* "Removing worktree…" / "Worktree removed" (the smoke test asserts ordering), aggregated warning lines, and the abort error line.

**Responsibilities:**
- Make teardown visible and ordered relative to removal so users (and the BATS smoke test) can see cleanup ran first.
- Consistent warning vs error messaging.

**Estimated Effort:** 0.5 hours (folded into command wiring in practice)
**Complexity:** Straightforward

---

## Technical Decisions

### Patterns

- Reuse the #331 reflection-discovery pattern verbatim for project `CleanupAction` hooks (`HookDiscovery.collectValues[CleanupAction]`).
- Functional Core / Imperative Shell: built-in *detection* is a pure function over filesystem-probe results; *execution* is effectful and lives in the `Rm`/`BuildToolCleanup` shell via `CommandEnv` capabilities.
- Trait + context case class (Strategy), identical to `RecoveryAction` / `FixAction`.
- Abort-vs-warn encoded in the return/throw convention rather than a result ADT, matching the issue's stated contract (keep the model contract minimal that project authors implement).

### Technology Choices

- **Frameworks/Libraries**: No new dependencies. Java reflection (existing), `os-lib` (`os.Path`, already used), existing `env.process` / `env.fs` capabilities.
- **Data Storage**: None added. Reads (does not write) the Bloop state file (location TBD — CLARIFY) and the worktree filesystem.
- **External Systems**: Mill, Bloop, docker CLIs — all invoked best-effort behind `commandExists`.

### Integration Points

- `core/model/CleanupAction.scala` → consumed reflectively by command scripts and directly by `Rm`.
- `HookOps.cleanupActions` → `LiveHookOps` (reflection) and `FakeHookOps` (scripted) implementations.
- `BuildToolCleanup` → consumes `CommandEnv.{process, fs}`; invoked by `Rm`, not by reflection.
- `ConfigSerializer.fromHocon` → surfaces `cleanup.builtin` into `ProjectConfiguration` consumed by `Rm` for gating.
- `iw-run` → unchanged (`*.hook-rm.scala` already auto-discovered for the shared `rm` command).

## Technical Risks & Uncertainties

### Decision: BuildToolCleanup is a free function in the shell

**Status:** RESOLVED

The built-in is **Option A**. The pure decision (`detect`) lives in the model; an effectful `BuildToolCleanup.run(ctx: CleanupContext, env): List[String]` lives in `core/commands/` (the shell, which has `env` access) and is invoked **directly by `Rm`**, separate from the reflective `cleanupActions` discovery path. Project `CleanupAction` hooks keep the clean, pure `CleanupContext` with no capabilities — preserving the "pure context" symmetry with `SessionContext` / `RecoveryContext`. The cost — built-in and project hooks travel two code paths — is accepted; it keeps the model contract minimal and the built-in harness-testable through the fakes.

---

### Decision: Project hooks run first, then the built-in; teardown is idempotent

**Status:** RESOLVED

**Option B.** Project `CleanupAction` hooks run first (in declared order), then the built-in `BuildToolCleanup` runs unconditionally (when `cleanup.builtin` is on). The "handled" signaling from the issue is dropped entirely: `docker compose down` (and Mill/Bloop teardown scoped to the worktree) are idempotent, so a project hook that already shut a stack down makes the built-in's repeat a harmless no-op. This keeps the `CleanupAction` return type as `List[String]` (no structured result) and keeps `Rm`'s orchestration order simple and fixed: discovered hooks, then built-in.

---

### Decision: Marker-gated unconditional `bloop exit` (no state-file parsing)

**Status:** RESOLVED

**Option B.** No fragile state-file parsing. The built-in triggers Bloop teardown only when a **worktree-local marker** is present (e.g. `.bloop/` or `.scala-build/` under the worktree) and `bloop` (or scala-cli's bundled Bloop) is available, then issues an unconditional `bloop exit`. Accepted trade-off (per Michal): this may exit a Bloop server that another worktree was also using, but Bloop restarts on demand, so the cost is a one-time cold start — acceptable given the marker gate keeps it scoped to worktrees that actually used Bloop. This keeps the built-in simple and avoids coupling to scala-cli internals, and keeps Bloop **in scope** for this issue rather than deferred.

---

### Decision: `--force` is passed through to hooks only; built-in ignores it

**Status:** RESOLVED

**Option A.** `--force` is threaded into `CleanupContext.force` so *project* hooks can decide whether to escalate (e.g. SIGKILL vs SIGTERM), but the built-in `BuildToolCleanup` ignores `force` in this issue — it always does graceful teardown (`mill --no-server shutdown`, `bloop exit`, `docker compose down`). No PID-kill / SIGKILL branches in the built-in for now; escalation can be added later if a concrete need appears. Keeps the built-in scope and test matrix minimal.

---

### Decision: `CleanupConfig` sub-config; emit in toHocon only when non-default

**Status:** RESOLVED

**Option A.** Add a `CleanupConfig(builtin: Boolean = true)` sub-config on `ProjectConfiguration`, parsed under the `cleanup { }` block in `ConfigSerializer.fromHocon` (default `true` when absent). This is the extensible shape for when cleanup grows more knobs (e.g. per-tool toggles).

Round-trip (sub-question): `toHocon` emits a `cleanup { builtin = false }` block **only when the value is non-default** (`false`), matching how existing optional fields (`repository`, `teamPrefix`, `baseUrl`) are conditionally emitted — so default configs stay clean and unchanged. Read side always parses with the `true` default. (Flag for Michal if you'd prefer always writing the block.)

---

## Total Estimates

**Per-Layer Breakdown:**
- Domain Layer (Model): 1-1.5 hours
- Application Layer (Built-in cleanup): 2.5-4 hours
- Infrastructure Layer (Command wiring): 1.5-2.5 hours
- Presentation Layer: 0.5 hours (folded into wiring)

**Total Range:** 5.5 - 8.5 hours

**Confidence:** Medium

**Reasoning:**
- The hook trait + discovery + `Rm` wiring reuses a proven pattern (#331) — high confidence, tight range.
- The built-in `BuildToolCleanup` carries the uncertainty: Bloop state parsing and the docker-compose dedup semantics are the unresolved CLARIFYs and drive the wide end of the range.
- The harness-test matrix (7 scenarios) is well-specified, which de-risks the test effort.
- Resolving the Bloop CLARIFY toward "defer Bloop" (Option C) would pull the total toward the low end.

## Recommended Phase Plan

Total estimate 5.5-8.5h falls in the 4-12h band → 1-3 phases, merging any layer whose low-end is under the 3h floor. Domain (1-1.5h), Infrastructure/wiring (1.5-2.5h), and Presentation (0.5h) are all sub-floor and sit on the critical dependency path (model → wiring → output). The built-in (2.5-4h) is the one substantial, independently-reviewable concern (subprocess + filesystem detection, parsing fragility).

- **Phase 1: CleanupAction model + config + Rm wiring (project-hook path)**
  - Includes: Domain (trait/context, `cleanup.builtin` config + Constants) + Infrastructure (`HookOps.cleanupActions` live+fake, `Rm` invocation with abort/warn semantics) + Presentation (output lines). Delivers the full project-hook path end to end, plus the harness scenarios that don't need the built-in (no hooks / single success / single warnings / single abort / multiple) and the BATS smoke test.
  - Estimate: 3-4.5 hours
  - Rationale: Each layer here is below the 3h floor and they are tightly coupled on the dependency path; merging them yields one reviewable slice that already satisfies most acceptance criteria. The built-in is deliberately excluded because it carries the open CLARIFYs.
- **Phase 2: BuildToolCleanup built-in (Mill / Bloop / docker-compose)**
  - Includes: Application layer — pure `detect` decision function + effectful runner via `env`, gated on `cleanup.builtin`, invoked directly by `Rm`; plus the built-in-only and built-in-disabled harness scenarios and the pure decision tests.
  - Estimate: 2.5-4 hours
  - Rationale: Low-end dips just under the floor but it isolates a hard-to-review concern (external-tool subprocesses, fragile Bloop state parsing) that benefits from an independent PR; midpoint (~3.25h) is at the floor. It depends on Phase 1's `Rm` invocation point and config gate, so it must follow. Resolving the Bloop CLARIFY before this phase starts is advised.

**Total phases:** 2 (for total estimate 5.5-8.5 hours)

## Testing Strategy

### Per-Layer Testing (mapped to the three-tier pyramid)

**Tier 1 — Unit (Model + pure decision):**
- `CleanupContext` constructs correctly; trait compiles against a test implementation (trivial, may be implicit in harness tests).
- `ConfigSerializer.fromHocon` parses `cleanup.builtin` true/false and defaults to `true` when absent; round-trip if `toHocon` writes it (per CLARIFY).
- Pure `BuildToolCleanup.detect` over scripted filesystem-probe inputs → asserts the correct set of teardown commands (mill-daemon present/absent, docker-compose present/absent, Bloop-rooted-in-worktree true/false). FCIS: this is the testable core; no subprocesses.

**Tier 1 — Unit (Harness, `core/test/RmHarnessTest.scala`):** the seven required scenarios, driving `Rm.run` against `FakeCommandEnv`, asserting `FakeConsole` lines and that `env.worktree.remove` was/wasn't called:
1. No hooks installed → today's behavior, removal proceeds.
2. Single hook success (`Nil`) → removal proceeds, hook ran before removal.
3. Single hook warnings → warnings printed, removal proceeds.
4. Single hook abort (throws) → error printed, **`worktree.remove` NOT called** (worktree preserved), `CommandResult.error`.
5. Multiple hooks → run in declared order, warnings aggregate.
6. Built-in only (no project hook) → `FakeFileSystem` scripts `out/mill-daemon/` etc.; `FakeProcess` scripts `commandExists` + `run`; assert the right subprocess commands were issued.
7. Built-in disabled (`cleanup.builtin = false`) → built-in does not run; no teardown subprocess issued.

**Tier 2 — Tool contract (`test/contract/`):** Not required for this issue — no new assumption about an external tool's CLI surface is pinned (Mill/Bloop/docker are invoked best-effort behind `commandExists`, so behavior is graceful regardless). Skip unless a CLARIFY resolves toward depending on a specific Bloop CLI contract.

**Tier 3 — E2E smoke (`test/rm.bats`):** ONE scenario — install a trivial `*.hook-rm.scala` that prints a known line; assert that line appears **before** "Worktree removed". Exports `IW_SERVER_DISABLED=1`, uses `IW_TMUX_SOCKET`. BATS stays a wiring smoke test only.

**Test Data Strategy:**
- Harness: `FakeFileSystem` + `FakeProcess` scripted per scenario; `FakeHookOps.setCleanupActions(...)` injects test `CleanupAction` instances; no real subprocesses.
- BATS: a minimal real `*.hook-rm.scala` fixture in a throwaway temp worktree.

**Regression Coverage:**
- Existing `Rm` flow unchanged when no hooks/built-in act: force-prompt on uncommitted changes, current-session refusal, `killSessionIfPresent`, `unregisterBestEffort`, branch-not-deleted note, `CommandResult.ok`.
- Existing `*.hook-doctor.scala` / other action-hook discovery must keep working (shared `HookDiscovery`).
- `--force` semantics (ignore dirty tree/index) preserved, now additionally passed into `CleanupContext.force`.

## Deployment Considerations

### Database Changes
None.

### Configuration Changes
New optional `cleanup.builtin` key in `.iw/config.conf` (default `true`). Absent key = unchanged behavior; no migration needed.

### Rollout Strategy
Additive. With no project `*.hook-rm.scala` installed and the built-in on, `rm` gains best-effort daemon teardown; with `cleanup.builtin = false` it reverts to exactly today's behavior. Project hooks are opt-in by dropping a file.

### Rollback Plan
Revert the branch. No persisted state. Worst case at runtime: a flaky built-in surfaces warnings — by contract it never blocks `rm`, so removal still succeeds.

## Dependencies

### Prerequisites
- #331 action-hook pattern (`SessionAction`/`FixAction`/`RecoveryAction`, `HookDiscovery.collectValues`) — already landed.
- `CommandEnv` `Process` + filesystem capabilities — already present.

### Layer Dependencies
- Model (trait/context/config) before Infrastructure (wiring references the types).
- Infrastructure `Rm` invocation point + config gate before the Application built-in (the built-in is invoked from `Rm`).
- Presentation interleaves with Infrastructure.
- Phase 1 (project-hook path) and Phase 2 (built-in) are sequential by dependency; the built-in's pure `detect` could be written in parallel but is integrated only after Phase 1.

### External Blockers
- None hard. The Bloop CLARIFY may need a quick investigation of scala-cli's state file before Phase 2.

## Risks & Mitigations

### Risk 1: Bloop state file path/format is fragile or undocumented
**Likelihood:** Medium
**Impact:** Medium
**Mitigation:** Resolve the Bloop CLARIFY before Phase 2; if unverifiable cheaply, take Option C (defer Bloop, ship Mill + docker-compose) — acceptance criteria for the built-in can note Bloop as follow-up, or accept the conservative unconditional-exit variant guarded by a worktree-local marker.

### Risk 2: Built-in double-teardown or unwanted shutdown of shared daemons
**Likelihood:** Medium
**Impact:** Medium
**Mitigation:** Resolve the ordering/"handled" CLARIFY toward idempotent operations (docker `down` is idempotent); scope Bloop/Mill detection strictly to servers rooted *inside* the worktree so a daemon shared across worktrees is never killed.

### Risk 3: A hook that throws on an unexpected condition strands the worktree
**Likelihood:** Low
**Impact:** Low
**Mitigation:** This is the intended contract (throw = abort, preserve worktree). Document it clearly; the built-in by contract never throws to `Rm`. Users can re-run with the hook fixed or `cleanup.builtin = false`.

### Risk 4: Built-in calling `os`/`ProcessAdapter` directly, bypassing `env` (untestable)
**Likelihood:** Medium (easy mistake)
**Impact:** Medium
**Mitigation:** Enforce in review and tests that the built-in runs exclusively through `env.process` / `env.fs`; the harness built-in scenarios (6, 7) only pass if subprocesses are scripted through the fakes.

---

## Implementation Sequence

**Recommended Layer Order:**

1. **Domain Layer (Model)** — `CleanupAction`/`CleanupContext`, `cleanup.builtin` config + `Constants` key, pure `detect`. Pure types, no dependencies, foundation for everything.
2. **Infrastructure Layer (Command wiring)** — `HookOps.cleanupActions` (live + fake), `Rm` invocation point with abort/warn semantics + config gate. Enables the full project-hook path and most acceptance criteria.
3. **Application Layer (Built-in)** — `BuildToolCleanup` effectful runner over the pure `detect`, invoked from `Rm`, gated on config. Depends on the `Rm` invocation point and config gate from step 2.
4. **Presentation** — output ordering/messages, interleaved with steps 2-3.

**Ordering Rationale:**
- Model types must exist before the shell can reference them; the config gate must exist before the built-in can be conditionally invoked.
- The built-in is invoked *from* `Rm`, so the `Rm` invocation point lands first (Phase 1) and the built-in plugs into it (Phase 2).
- The pure `detect` function can be written and unit-tested independently of the wiring, offering a small parallelization window within Phase 2.
- This sequence matches the two-phase plan: Phase 1 = layers 1+2 (+4), Phase 2 = layer 3.

## Documentation Requirements

- [ ] Code documentation (`// PURPOSE:` headers on new files; inline contract docs on `CleanupAction.cleanup` covering Nil/warn/throw semantics)
- [ ] User-facing documentation (document `*.hook-rm.scala` authoring + `cleanup.builtin` config in the relevant command/config docs)
- [ ] Architecture decision record (the discovery-vs-direct-invocation split for env-dependent built-ins — likely worth recording, as it sets precedent for future built-in actions)
- [ ] Update CLAUDE.md hook-pattern notes if a new authoring convention emerges

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Resolve CLARIFY markers (especially built-in env access, ordering/dedup semantics, and Bloop state portability) with stakeholders
2. Run **wf-create-tasks** with the issue ID
3. Run **wf-implement** for layer-by-layer implementation
