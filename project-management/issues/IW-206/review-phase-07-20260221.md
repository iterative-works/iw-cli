# Code Review Results

**Review Context:** Phase 7: HTMX auto-refresh for project worktree list for issue IW-206
**Files Reviewed:** 3
**Skills Applied:** code-review-style, code-review-testing, code-review-scala3, code-review-architecture
**Timestamp:** 2026-02-21
**Git Context:** git diff 6518ec8..b760112

---

<review skill="code-review-style">

### Critical Issues
None found.

### Warnings
None found.

### Suggestions

1. **Variable name `html` for OOB fragment** — In `projectWorktreeChanges`, `val html = WorktreeListSync.generateChangesResponse(...)` holds OOB swap fragments, not a full HTML document. A name like `changesHtml` or `oobResponse` would be more precise.

2. **Massive test fixture duplication** — Four consecutive tests repeat identical 39-line setup (project, worktree, issueData, worktreesWithData). Each differs only in the final assertion. Extract to a shared helper method to reduce 164 lines to ~35.

</review>

---

<review skill="code-review-testing">

### Critical Issues

#### Missing tests for new HTTP endpoint
**Location:** `.iw/core/dashboard/CaskServer.scala:308` — new route `GET /api/projects/:projectName/worktrees/changes`
**Problem:** 42 lines of new endpoint logic with no integration tests. The endpoint performs project filtering, `have` parameter parsing, change detection, and OOB response generation — all untested.
**Impact:** Route behavior (correct filtering, parameter parsing, response format) is not verified. Only the view's HTMX attributes are tested.
**Recommendation:** Add integration tests verifying: (1) endpoint returns 200, (2) filters worktrees by project name, (3) parses `have` parameter correctly.

### Warnings

#### Test fixture duplication (164 lines)
**Location:** `.iw/core/test/ProjectDetailsViewTest.scala:211-374`
**Problem:** Four tests duplicate identical 39-line fixture setup. Each test creates the same project, worktree, issueData, worktreesWithData, renders the view, then checks one HTMX attribute.
**Impact:** Maintenance burden — any fixture change requires updating 4 places. Obscures what each test actually verifies.
**Recommendation:** Extract to a shared helper like `renderWithTestWorktree(): String` that returns the rendered HTML.

### Suggestions

1. **Consider consolidating HTMX attribute tests** — The four tests could be a single test with four assertions, or use a shared render and check each attribute. This is a judgment call — separate tests give better failure isolation, but the current duplication is excessive.

</review>

---

<review skill="code-review-scala3">

### Critical Issues
None found.

### Warnings

#### Test fixture duplication violates DRY
**Location:** `.iw/core/test/ProjectDetailsViewTest.scala:211-374`
**Problem:** Same as testing review — 4 tests with identical 39-line setup blocks.
**Recommendation:** Extract to companion object method or private helper using Scala 3 syntax.

### Suggestions

None beyond the duplication issue.

</review>

---

<review skill="code-review-architecture">

### Critical Issues
None found.

### Warnings
None found.

### Suggestions

1. **Consider extracting route logic to application service** — The `projectWorktreeChanges` route contains filtering + change detection + response generation inline. The existing root-level `/api/worktrees/changes` endpoint follows the same pattern, creating duplication between the two routes. Extracting the shared logic (filter → detect changes → generate response) to a service method would reduce duplication and make the routes thinner shells.

2. **HTMX attributes in view coupled to endpoint URL** — The view hardcodes `/api/projects/$projectName/worktrees/changes` and the JS expression for card ID extraction. This matches the existing pattern in the root dashboard, so it's consistent, but a `HtmxPollingConfig` parameter would decouple the view from the API structure. Lower priority since this is an internal dashboard.

### Notes

- Pure functions (`filterByProjectName`, `detectChanges`, `generateChangesResponse`) correctly called from the imperative shell
- View remains a pure rendering function with no side effects
- New endpoint follows the existing `/api/worktrees/changes` pattern, only adding project-scoped filtering
- FCIS compliance maintained

</review>

---

## Summary

- **Critical issues:** 1 (missing integration tests for new HTTP endpoint) — **RESOLVED**
- **Warnings:** 1 (massive test fixture duplication — 164 lines of repeated setup)
- **Suggestions:** 4 (variable naming, route extraction, view decoupling, test consolidation)

**Assessment:** The implementation is functionally correct and follows established patterns (mirrors the root-level `/api/worktrees/changes` endpoint). The test fixture duplication is the most actionable remaining code quality issue — extracting a shared helper would cut 130+ lines of repetition.

### Resolution

The critical issue (missing integration tests) was resolved by adding 3 integration tests to `CaskServerTest.scala`:
1. `GET /api/projects/:projectName/worktrees/changes returns 200 with HTML` — verifies endpoint responds with 200 and HTML content type
2. `GET /api/projects/:projectName/worktrees/changes filters by project name` — registers worktrees for two different projects, verifies only the matching project's worktree appears in the response
3. `GET /api/projects/:projectName/worktrees/changes with have param detects no changes` — verifies that when the client already has the current worktree IDs, no OOB swaps are returned
