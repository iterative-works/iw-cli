# Phase 1: Cache-architecture investigation

**Issue:** IW-347
**Phase:** 1 of 2
**Type:** Discovery / write-up (no code changes)
**Estimate:** 0.75–1.25h

## Goals

- Characterise the dashboard-bound caching architecture (who writes the caches, when, what triggers refreshes) clearly enough that a follow-up parent issue can be opened with concrete evidence.
- Reproduce the three user-visible symptoms (CLI shows nothing for unvisited worktrees; first dashboard visit lags ~30 s; PR link flicker) and tie each to a specific code path.
- Produce a draft issue body for the cache-architecture rework (background warm-up, CLI-driven population, decoupling from dashboard visits).
- Stay out of the code: this phase only ships markdown.

## Scope

### In scope

- Reading the dashboard cache services and their callers to map state ownership and refresh triggers.
- Tracing read/write call sites with `rg` / `sg`.
- Reproducing the three symptoms against a real worktree to confirm the model derived from reading.
- Writing two markdown deliverables under `project-management/issues/IW-347/`.

### Out of scope

- Any change to `PullRequestCacheService`, `IssueCacheService`, `GitStatusService`, `RefreshThrottle`, `ServerStateService`, or any other cache-related code.
- Any fix for the PR-link flicker (the renderer-side mitigation lives in Phase 2).
- The cache-architecture rework itself — that is the follow-up parent issue, not Phase 1.
- Any test additions or modifications.

## Deliverables

1. `project-management/issues/IW-347/cache-investigation.md` — structured write-up. Suggested sections:
   - Cache services inventory — which services hold what state, where they live, what types they expose.
   - Write paths — who populates each cache and when (request-handler-driven, scheduled, throttled, etc.).
   - Read paths — who consumes each cache, with what staleness tolerance, and what happens on miss.
   - Refresh triggers — visit-driven, throttled (`RefreshThrottle` role), scheduled (if any), manual.
   - Reproduction notes for the three user-visible symptoms (CLI shows nothing for unvisited worktrees; first dashboard visit lags ~30 s; PR link flicker), each tied to the specific code path that explains it.
   - Open questions / things worth deeper investigation in the follow-up issue.

2. `project-management/issues/IW-347/cache-rework-issue-draft.md` — a draft markdown issue body suitable for opening as a new parent issue. Should motivate the work, summarise findings, and propose a high-level direction (background warm-up, CLI-driven population, dashboard-decoupling). IW-347 should be linked as a motivating case.

## Investigation approach

- Map the cache services first by reading source (no behavioural assumptions yet). The five services named in the analysis (`PullRequestCacheService`, `IssueCacheService`, `GitStatusService`, `RefreshThrottle`, `ServerStateService`) have all been confirmed to exist in `dashboard/jvm/src/` under those exact names — start there.
- Trace read/write call sites with `rg` / `sg` once the per-service surface area is understood. `WorktreeCardService` and `WorktreeListSync` are known consumers worth checking first.
- Reproduce each user-visible symptom against a real worktree to confirm the model derived from reading; note the observed timing for the ~30 s first-visit lag.
- Time-box: 1.25h hard cap. If the architecture is more entangled than expected, capture open questions in the write-up and let the follow-up issue's analysis explore them in depth — do not let the investigation expand into a redesign.

## Files to read (not modify)

All five cache-related class names referenced in the analysis are confirmed to exist exactly as written:

- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/PullRequestCacheService.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/IssueCacheService.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/GitStatusService.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/RefreshThrottle.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/ServerStateService.scala`

Likely callers / consumers worth tracing from:

- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/WorktreeCardService.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/WorktreeListSync.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/DashboardService.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/CaskServer.scala`

Use `rg` to find additional consumers; do not assume the list above is exhaustive.

## Files this phase WILL create

- `project-management/issues/IW-347/cache-investigation.md`
- `project-management/issues/IW-347/cache-rework-issue-draft.md`

## Files this phase will NOT modify

- Any `.scala` source file
- Any test file
- Any frontend asset

## Dependencies on prior phases

None — this is the first phase. Phase 1 does not block Phase 2 and may run independently; running Phase 1 first is recommended so any cache-architecture insight can inform Phase 2's tests, but the phases can land in either order (per analysis).

## Acceptance criteria

- [ ] `cache-investigation.md` exists and covers cache inventory, write paths, read paths, refresh triggers, reproductions, and open questions.
- [ ] `cache-rework-issue-draft.md` exists and is a coherent draft for opening as a follow-up parent issue.
- [ ] No `.scala`, test, or frontend file is modified.
- [ ] Time spent on investigation is within the 1.25h cap (or the cap reason for any overshoot is captured in the write-up's "open questions").

## Notes for the implementer

- This is a discovery phase. The bar is "characterise the problem clearly enough that a follow-up issue can be planned", not "solve it".
- The write-up is the product. Treat it like documentation work, not code work.
- If during investigation the user-visible symptoms turn out to have a different root cause than the analysis hypothesises, document that finding — do not try to defend the analysis.
