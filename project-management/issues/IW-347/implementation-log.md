# Implementation Log: Dashboard — Add git repo web link and ensure PR link is always visible

Issue: IW-347

This log tracks the evolution of implementation across phases.

---

## Phase 1: Cache-architecture investigation (2026-04-29)

**Layer:** Discovery / Documentation (no code changes)

**What was built:**
- `project-management/issues/IW-347/cache-investigation.md` — structured write-up of the dashboard cache architecture (services inventory, write paths, read paths, refresh triggers, reproduction notes for symptoms A/B/C, open questions).
- `project-management/issues/IW-347/cache-rework-issue-draft.md` — draft issue body for opening a follow-up parent issue covering the cache-architecture rework.

**Key findings (diverged from analysis hypothesis):**
- "Caches are dashboard-only" is true for **writes** but not **reads**. The CLI silently consumes cache state via `iw status` (through `/api/v1/worktrees/:issueId/status`) and `iw worktrees` (through `StateReader` reading `state.json` directly). Neither has a fallback when the cache is empty.
- Symptom B is HTMX trigger-schedule overhead, not a synchronous fetch. Initial render serves a skeleton with `every 30s` (no `load` trigger), so the first poll fires +30 s after page load.
- Symptom C's deeper cause: `RefreshThrottle.recordRefresh` is gated only on a successful issue fetch (`WorktreeCardService.scala:108`). When tracker auth fails, the throttle never closes; transient `gh pr view` failures then produce a render with `prData = None` → PR section disappears.
- `RefreshThrottle` (30 s) and `CacheConfig` TTLs (15–30 min) are overlapping gates; the throttle dominates in practice and the TTL rarely fires.
- `ServerDaemon` does no background work (no scheduler, no warm-up). `ServerStateService`'s shape supports it cleanly.

**Dependencies on other layers:**
- None (first phase, discovery only).

**Testing:**
- Unit tests: 0 (none required for documentation-only phase).
- Integration tests: 0 (none required).
- Reproduction approach: code-path analysis with file/line citations, not live reproduction (per phase context guidance — dashboard is heavyweight to spin up).

**Code review:**
- Iterations: 0 (formal code-review skills do not apply to markdown documentation; sanity-checked manually).
- Review file: none.

**Files changed:**
```
A  project-management/issues/IW-347/cache-investigation.md
A  project-management/issues/IW-347/cache-rework-issue-draft.md
M  project-management/issues/IW-347/phase-01-tasks.md (checkboxes)
```

**For next phases:**
- Phase 2 (renderer-side mitigation for symptom C) can proceed independently. Phase 1's investigation may inform Phase 2's tests but is not blocking.
- The cache-rework-issue-draft.md is ready to be opened as a follow-up parent issue when desired; that work is explicitly out of scope for IW-347.

---
