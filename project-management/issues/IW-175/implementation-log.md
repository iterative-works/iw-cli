# Implementation Log: Dashboard cards jump around during refresh causing misclicks

Issue: IW-175

This log tracks the evolution of implementation across phases.

---

## Phase 1: Stable card positions during auto-refresh (2026-01-28)

**What was built:**
- Model: `.iw/core/model/ServerState.scala` - Replaced `listByActivity` with `listByIssueId` to sort by Issue ID instead of lastSeenAt
- Service: `.iw/core/dashboard/DashboardService.scala` - Updated sorting logic, removed WorktreePriority dependency
- Infrastructure: `.iw/core/dashboard/CaskServer.scala` - Updated both endpoints to use stable Issue ID sorting
- Infrastructure: `.iw/core/dashboard/ServerStateService.scala` - Updated legacy compatibility method

**Decisions made:**
- Use simple alphabetical string sorting (IW-1 < IW-10 < IW-2) rather than natural numeric sorting for Phase 1. Simpler to implement, predictable behavior.
- Keep WorktreePriority class for potential future use (staggered loading), just removed from card ordering logic.
- Remove activity-based sorting entirely per analysis decision (lastSeenAt doesn't accurately reflect user activity).

**Patterns applied:**
- FCIS (Functional Core, Imperative Shell): Pure sorting logic in domain model, effects only at boundaries
- Single Responsibility: Sorting logic centralized in ServerState.listByIssueId

**Testing:**
- Unit tests: 9 tests updated/added in ServerStateTest.scala
- Integration tests: 0 (sorting is pure function, no I/O)

**Code review:**
- Iterations: 1
- Review file: review-phase-01-20260128-082854.md
- Major findings: 1 warning (misleading timestamps in test data), 6 suggestions (optional improvements)

**For next phases:**
- Available utilities: `ServerState.listByIssueId` provides stable Issue ID ordering
- Extension points: If natural numeric sorting needed, add custom Ordering
- Notes: Staggered loading still uses WorktreePriority for refresh priority

**Files changed:**
```
M	.iw/core/dashboard/CaskServer.scala
M	.iw/core/dashboard/DashboardService.scala
M	.iw/core/dashboard/ServerStateService.scala
M	.iw/core/model/ServerState.scala
M	.iw/core/test/ServerStateTest.scala
```

---

## Phase 2: New worktrees appear at predictable location (2026-01-28)

**What was built:**
- Service: `.iw/core/dashboard/WorktreeListSync.scala` - Added `findPredecessor` helper function and updated `generateAdditionOob` to support positional insertion with `predecessorId` parameter
- Infrastructure: `.iw/core/dashboard/CaskServer.scala` - Updated to pass `currentIds` to `generateChangesResponse`

**Decisions made:**
- Use `afterbegin:#worktree-list` for new cards that should be first, `afterend:#card-{id}` for cards after a predecessor
- Calculate predecessor using simple string comparison (alphabetical ordering), consistent with Phase 1's Issue ID sorting
- Keep `currentIds` parameter explicit rather than computing internally - caller already has this data

**Patterns applied:**
- FCIS: Pure `findPredecessor` function (no I/O, deterministic)
- HTMX OOB swap patterns: `afterbegin` for first position, `afterend` for positional insertion

**Testing:**
- Unit tests: 10 tests added in WorktreeListSyncTest.scala
  - 4 tests for `findPredecessor` edge cases
  - 2 tests for `generateAdditionOob` with/without predecessor
  - 4 tests for `generateChangesResponse` positional insertion

**Code review:**
- Iterations: 1
- Review file: review-phase-02-20260128-095025.md
- Major findings: 1 warning (mixed application/presentation logic - acceptable for now), 9 suggestions (minor improvements)

**For next phases:**
- Available utilities: `findPredecessor` for determining insertion position
- Extension points: If cards need repositioning after deletion, same pattern can be used
- Notes: Alphabetical sorting means IW-2 > IW-100 (string comparison)

**Files changed:**
```
M	.iw/core/dashboard/CaskServer.scala
M	.iw/core/dashboard/WorktreeListSync.scala
M	.iw/core/test/WorktreeListSyncTest.scala
```

---
