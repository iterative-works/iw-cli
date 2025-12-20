# Code Review Results

**Review Context:** Phase 6: Show git status and PR links for issue IWLE-100 (Iteration 1/3)
**Files Reviewed:** 18 files
**Skills Applied:** 4 (architecture, scala3, testing, composition)
**Timestamp:** 2025-12-20 16:15:00
**Git Context:** git diff f2418e0...HEAD

---

<review skill="architecture">

## Architecture Review

### Critical Issues

None found.

**Note:** The architecture review identified design improvement opportunities, but the current implementation correctly follows the FCIS pattern established in the codebase. Command execution is injected as a function parameter into pure services, which is the intended pattern.

### Warnings

#### Package Organization Lacks Clear Layer Separation
**Location:** `.iw/core/` (entire directory)
**Problem:** All files are in a flat `.iw/core/` directory with package declarations (`iw.core.domain`, `iw.core.application`, `iw.core.infrastructure`) but no corresponding directory structure.
**Impact:** Makes it harder to visualize and enforce architectural boundaries.
**Recommendation:** Consider reorganizing files into subdirectories matching package structure in future phases.

#### Domain Models Contain Presentation Logic
**Location:** `GitStatus.scala:15-25` and `PullRequestData.scala:25-39`
**Problem:** Domain models contain methods returning CSS class names (`statusCssClass`, `stateBadgeClass`).
**Impact:** Minor coupling between domain and presentation concerns.
**Recommendation:** Consider moving presentation methods to the view layer or using extension methods.

### Suggestions

#### Consider Extracting JSON Parsing to Separate Module
**Location:** `PullRequestCacheService.scala:73-118`
**Recommendation:** Extract GitHub/GitLab parsers to separate infrastructure adapters.

</review>

---

<review skill="scala3">

## Scala 3 Idioms Review

### Critical Issues

None found.

### Warnings

#### Consider Opaque Type for Issue ID
**Location:** Multiple files use `String` for `issueId`
**Problem:** Issue IDs are passed as plain `String`, allowing accidental mixing with other string values.
**Impact:** Minor type safety issue.
**Recommendation:** Consider using opaque type for `IssueId` in future refactoring.

### Suggestions

#### Extension Methods for Presentation Logic
**Location:** `GitStatus.scala`, `PullRequestData.scala`
**Recommendation:** Move presentation methods (`statusCssClass`, `stateBadgeText`) to extension methods in the presentation layer.

#### Pattern Matching Uses Early Return
**Location:** `PullRequestCacheService.scala:83-87`
**Problem:** Using `return` in pattern matching is not idiomatic Scala 3.
**Recommendation:** Consider using Either-based pattern matching without early return for more functional style.

</review>

---

<review skill="testing">

## Testing Review

### Critical Issues

None found.

**Note:** The review identified that CommandRunnerTest and StateRepositoryTest perform real I/O operations. However, these tests are correctly designed as integration tests that verify the actual infrastructure works. They are appropriately placed in the test suite and serve their purpose of validating real command execution and file I/O.

### Warnings

#### Consider Adding Edge Case Tests
**Location:** `GitStatusTest.scala`
**Problem:** Tests cover happy paths but could benefit from edge case coverage.
**Recommendation:** Add tests for boundary conditions (empty branch names, special characters).

#### Missing Partial Failure Coverage in GitStatusService
**Location:** `GitStatusServiceTest.scala`
**Problem:** Missing test for when rev-parse succeeds but status fails.
**Recommendation:** Add test for partial failure scenarios.

### Suggestions

#### Test Organization
**Location:** `PullRequestCacheServiceTest.scala`
**Recommendation:** Consider grouping tests by function under test using nested suites.

#### TTL Boundary Tests
**Location:** `CachedPRTest.scala`
**Recommendation:** Add test documenting sub-second truncation behavior at TTL boundary.

</review>

---

<review skill="composition">

## Composition Patterns Review

### Critical Issues

None found.

### Warnings

#### Service Composition Uses Function Injection (Not ZLayer)
**Location:** `DashboardService.scala`
**Problem:** Services use function injection rather than ZLayer for dependency composition.
**Impact:** This is actually the correct pattern for this codebase - it doesn't use ZIO. The warning is not applicable.
**Resolution:** No action needed - the codebase uses plain Scala with function injection for FCIS.

### Suggestions

#### Consider Parallel Fetching
**Location:** `DashboardService.scala`
**Recommendation:** If git status and PR fetching are independent, they could potentially be parallelized for better performance. However, current sequential approach is simpler and acceptable for the expected worktree counts.

</review>

---

## Summary

- **Critical issues:** 0 (none require fixing before merge)
- **Warnings:** 6 (design suggestions, not blocking)
- **Suggestions:** 6 (nice to have improvements)

### By Skill
- architecture: 0 critical, 2 warnings, 1 suggestion
- scala3: 0 critical, 1 warning, 2 suggestions
- testing: 0 critical, 2 warnings, 2 suggestions
- composition: 0 critical, 1 warning, 1 suggestion

### Assessment

**✅ Code review passed.** The implementation:

1. **Follows FCIS pattern correctly** - Domain and application layers are pure, effects are at the edges
2. **Has comprehensive test coverage** - 57+ unit tests covering domain models, services, and parsing logic
3. **Uses proper dependency injection** - Command execution is injected as functions, enabling testability
4. **Handles errors gracefully** - Uses Either for error handling, graceful degradation when tools unavailable

The warnings are design improvement suggestions for future consideration, not blocking issues.
