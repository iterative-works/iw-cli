# Implementation Tasks: Cleanup hook on worktree removal — stop project daemons before iw rm

**Issue:** IW-382
**Created:** 2026-06-13
**Status:** 0/2 phases complete (0%)

## Phase Index

- [x] Phase 1: CleanupAction model + config + Rm wiring (project-hook path) (Est: 3-4.5h) → `phase-01-context.md`
- [ ] Phase 2: BuildToolCleanup built-in (Mill / Bloop / docker-compose) (Est: 2.5-4h) → `phase-02-context.md`

## Progress Tracker

**Completed:** 0/2 phases
**Estimated Total:** 5.5-8.5 hours
**Time Spent:** 0 hours

## Phase Summaries

### Phase 1: CleanupAction model + config + Rm wiring (project-hook path)

Merges Domain + Infrastructure + Presentation (each below the 3h floor, all on the critical dependency path model → wiring → output). Delivers the full project-hook path end to end:

- **Domain:** `CleanupAction` trait + `CleanupContext(worktreePath, issueId, config, force)` in `core/model/CleanupAction.scala` (mirrors `RecoveryAction`); `CleanupConfig(builtin: Boolean = true)` sub-config on `ProjectConfiguration`, parsed under `cleanup { }` in `ConfigSerializer.fromHocon` (default `true`), `toHocon` emits only when non-default; new config-key constant in `Constants.scala`.
- **Infrastructure:** `HookOps.cleanupActions: List[CleanupAction]` (live = `HookDiscovery.collectValues[CleanupAction]`; fake = `setCleanupActions`); `Rm` invocation point after force/session checks and before `env.worktree.remove` — run discovered hooks in declared order, aggregate warnings (proceed), first thrown error aborts and preserves the worktree; `--force` passed through into `CleanupContext.force`.
- **Presentation:** per-hook action line + aggregated warnings printed before "Removing worktree…"; abort error line.
- **Tests:** harness scenarios that don't need the built-in (no hooks / single success / single warnings / single abort / multiple); BATS smoke in `test/rm.bats` (trivial `*.hook-rm.scala` prints a known line before "Worktree removed"). No `iw-run` change needed (`rm` is a shared command, so `*.hook-rm.scala` is already auto-discovered).

### Phase 2: BuildToolCleanup built-in (Mill / Bloop / docker-compose)

Isolates the one substantial, independently-reviewable concern (external-tool subprocesses + filesystem detection). Depends on Phase 1's `Rm` invocation point and config gate, so it must follow.

- **Application:** pure `BuildToolCleanup.detect` (filesystem-probe results → list of teardown commands) in the model; effectful `BuildToolCleanup.run(ctx, env): List[String]` free function in `core/commands/`, invoked **directly by `Rm`** after the discovered project hooks, gated on `cleanup.builtin`.
- **Targets (all best-effort behind `commandExists`, graceful only — `--force` is ignored by the built-in):**
  - Mill: `out/mill-daemon/` under the worktree → `mill --no-server shutdown`.
  - Bloop: worktree-local marker (`.bloop/` or `.scala-build/`) present + `bloop`/scala-cli available → unconditional `bloop exit` (no state-file parsing; over-broad exit accepted since Bloop restarts on demand).
  - docker-compose: `docker-compose.yml` at worktree root → `docker compose down` (idempotent; runs unconditionally after project hooks, no "handled" signaling).
- **Tests:** pure `detect` decision tests (each target present/absent); harness scenarios built-in-only and built-in-disabled (`cleanup.builtin = false`) — `FakeFileSystem` scripts markers, `FakeProcess` scripts `commandExists` + `run` and asserts the right subprocess commands.

## Notes

- Phase context files generated just-in-time during implementation
- Use wf-implement to start next phase automatically
- Estimates are rough and will be refined during implementation
- Phases follow layer dependency order (domain → infrastructure → application → presentation); a single phase may merge multiple layers when each alone is below the phase-size floor
- All 5 CLARIFY markers in analysis.md are resolved (commit 3b80807) — the Bloop decision (marker-gated `bloop exit`, no state-file parsing) removes the parsing risk originally flagged for Phase 2
