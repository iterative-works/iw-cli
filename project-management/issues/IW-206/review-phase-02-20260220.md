# Code Review Results

**Review Context:** Phase 2: Project details page with filtered worktree cards for issue IW-206 (Iteration 1/3)
**Files Reviewed:** 6
**Skills Applied:** code-review-style, code-review-testing, code-review-architecture
**Timestamp:** 2026-02-20T17:19:29Z
**Git Context:** git diff a26473a

---

<review skill="architecture">

## Architecture Review

### Critical Issues

None blocking. Two design observations noted as warnings below.

### Warnings

#### Pre-existing: loadConfig I/O in Application Layer
**Location:** `.iw/core/dashboard/application/MainProjectService.scala:120-134`
**Problem:** The `loadConfig` function performs direct filesystem I/O (`os.exists`, `os.isDir`, `ConfigFileRepository.read`) within the application layer. This is a pre-existing pattern not introduced by Phase 2.
**Impact:** Violates FCIS principle but is not a regression.
**Recommendation:** Future refactoring opportunity - move I/O to infrastructure layer.

#### Visibility Change for Cross-Component Reuse
**Location:** `.iw/core/dashboard/DashboardService.scala:116, 273, 309, 329`
**Problem:** Changed four private methods to `private[dashboard]` to allow CaskServer to reuse them.
**Impact:** Pragmatic trade-off to avoid code duplication. Creates tighter coupling within the dashboard package.
**Recommendation:** Consider extracting shared functions to a dedicated service in future phases.

#### Route Handler Contains Business Logic
**Location:** `.iw/core/dashboard/CaskServer.scala:66-132`
**Problem:** The `projectDetails` route handler contains ~67 lines of filtering, deriving, and data fetching logic.
**Recommendation:** Extract to application service when pattern stabilizes across more phases.

#### Duplicate Data Fetching Pattern
**Location:** `CaskServer.scala:103-111` and `DashboardService.scala:51-61`
**Problem:** Same pattern of fetching issue/progress/git/PR/review data duplicated.
**Recommendation:** Extract common pattern to shared function.

### Suggestions

- Consider type alias or case class for the complex worktree data tuple type
- Consider opaque type for ProjectName for better type safety

</review>

---

<review skill="style">

## Style Review

### Critical Issues

None.

### Warnings

- Minor: PURPOSE comment lines missing trailing periods (cosmetic)
- Minor: `capitalizeTrackerType` hardcodes brand-specific capitalization (maintenance burden)

### Suggestions

- Empty state message "No worktrees for this project yet" uses temporal "yet"
- Test assertion messages could be more consistent
- Documentation could include @example for filterByProjectName

</review>

---

<review skill="testing">

## Testing Review

### Critical Issues

None blocking. Two coverage gaps noted as warnings.

### Warnings

#### Missing Integration Tests for New Route
**Location:** Missing from `CaskServerTest.scala`
**Problem:** The `GET /projects/:projectName` route has no HTTP-level integration tests.
**Impact:** Route behavior (status codes, content type, filtering) only verified through unit tests.
**Note:** These are explicitly listed as remaining TODO items in phase-02-tasks.md.

#### No Tests for Cache-Fetching Functions
**Location:** `DashboardService.scala` - 4 functions changed to `private[dashboard]`
**Problem:** Functions only changed in visibility, not behavior. Tested indirectly through existing dashboard tests.

### Suggestions

- Consider HTML structure verification instead of simple string containment
- Add edge case tests (empty list, empty name) for filterByProjectName
- Centralize test data builders across test files

</review>

---

## Summary

**Critical:** 0
**Warnings:** 6 (2 architecture pre-existing, 2 architecture new, 2 testing coverage gaps)
**Suggestions:** 8

**Assessment:** Code review passes. No critical blocking issues found. Warnings are about pre-existing architectural patterns, pragmatic trade-offs, and test coverage gaps that are explicitly tracked as remaining TODOs. The Phase 2 implementation correctly adds filtering logic, view component, and route handler following existing patterns.
