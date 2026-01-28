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
