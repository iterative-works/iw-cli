# Phase 1 Tasks: Cache-architecture investigation

**Issue:** IW-347
**Phase:** 1 of 2
**Phase Name:** Cache-architecture investigation (discovery / write-up)
**Estimate:** 0.75–1.25h (hard cap 1.25h)

## Deliverables recap

1. `project-management/issues/IW-347/cache-investigation.md` — structured write-up covering cache services inventory, write paths, read paths, refresh triggers, reproductions of the three user-visible symptoms, and open questions.
2. `project-management/issues/IW-347/cache-rework-issue-draft.md` — draft markdown body for opening a follow-up parent issue covering the cache-architecture rework (background warm-up, CLI-driven population, dashboard-decoupling).

No `.scala`, test, or frontend files are modified in this phase.

---

## Investigation: map cache services

- [x] [investigate] Read `dashboard/jvm/src/PullRequestCacheService.scala` and capture surface area (state held, public methods, write/read entry points).
- [x] [investigate] Read `dashboard/jvm/src/IssueCacheService.scala` and capture the same surface-area summary.
- [x] [investigate] Read `dashboard/jvm/src/GitStatusService.scala` and capture the same surface-area summary.
- [x] [investigate] Read `dashboard/jvm/src/RefreshThrottle.scala` and characterise its role (what it throttles, how triggers are gated, configurable cadence).
- [x] [investigate] Read `dashboard/jvm/src/ServerStateService.scala` and note whether it owns or coordinates cache state vs. delegating to the per-resource services.

## Investigation: trace call sites

- [x] [investigate] Trace consumers of each cache service with `rg` / `sg` (start from `WorktreeCardService.scala`, `WorktreeListSync.scala`, `DashboardService.scala`, `CaskServer.scala`); record write-path call sites (who populates) and read-path call sites (who consumes, what they do on miss).
- [x] [investigate] For each cache, identify what triggers a refresh (HTTP request handler, throttled, scheduled, manual) and whether anything outside the dashboard ever writes to it.
- [x] [investigate] Check whether any CLI command path reads cache state directly (looking for `IW_INSTALL_DIR`-rooted commands or `core/` consumers); confirm or refute the analysis claim that caches are dashboard-only.

## Reproduction: three user-visible symptoms

- [x] [reproduce] Symptom A — CLI shows nothing for unvisited worktrees: pick a worktree the dashboard has not visited, run the relevant CLI surface, capture observed behaviour, and tie it to the specific code path identified above.
- [x] [reproduce] Symptom B — first dashboard visit lags ~30 s: load a not-yet-cached worktree on the dashboard, observe and record the timing, and pinpoint the synchronous fetch that explains the lag.
- [x] [reproduce] Symptom C — PR link flicker: load a worktree whose PR cache is unpopulated/stale, watch the card render, note when the PR link appears, and link the flicker to the cache write timing in the relevant service.

## Write-up: cache-investigation.md

- [x] [write-up] Create `project-management/issues/IW-347/cache-investigation.md` skeleton with the section headings listed in `phase-01-context.md` (inventory, write paths, read paths, refresh triggers, reproductions, open questions).
- [x] [write-up] Fill the "Cache services inventory" section from the surface-area notes (one short paragraph per service with state held, key types, location).
- [x] [write-up] Fill the "Write paths" section: for each cache, who populates it and when (request-handler-driven, scheduled, throttled).
- [x] [write-up] Fill the "Read paths" section: who consumes each cache, with what staleness tolerance, and what happens on miss.
- [x] [write-up] Fill the "Refresh triggers" section, including `RefreshThrottle`'s role and any scheduled or manual triggers found.
- [x] [write-up] Fill the "Reproduction notes" section with the three symptoms, each tied to the specific code path that explains it (cite file/line where helpful).
- [x] [write-up] Fill the "Open questions" section with anything left ambiguous after the time-box; note any places where the model derived from reading diverged from the analysis hypothesis.

## Follow-up issue draft: cache-rework-issue-draft.md

- [x] [draft] Create `project-management/issues/IW-347/cache-rework-issue-draft.md` with a coherent issue body: motivation, summary of findings (linking back to the investigation write-up), and proposed high-level direction (background warm-up, CLI-driven cache population, decoupling caches from dashboard visits).
- [x] [draft] Reference IW-347 as a motivating case in the draft body and note the three symptoms it surfaces.

## Wrap-up / acceptance

- [x] [wrap-up] Re-read both deliverables end-to-end and confirm the write-up covers cache inventory, write paths, read paths, refresh triggers, reproductions, and open questions, and that the issue draft is suitable for opening directly.
- [x] [wrap-up] Verify no `.scala`, test, or frontend file was modified during this phase (`git status` should show only the two new markdown files in `project-management/issues/IW-347/`).
- [x] [wrap-up] Confirm the time-box: if the 1.25h cap was exceeded, capture the reason in the write-up's "Open questions" section.

---

See `phase-01-context.md` for the full acceptance criteria.
**Phase Status:** Complete
