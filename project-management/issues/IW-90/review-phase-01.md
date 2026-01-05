# Code Review Results

**Review Context:** Phase 1: Fetch and display GitLab issue via glab CLI for IW-90
**Files Reviewed:** 5 files
**Skills Applied:** 4 (architecture, scala3, testing, style)
**Timestamp:** 2026-01-04 15:45:00
**Git Context:** git diff IW-90...HEAD

---

<review skill="architecture">

## Architecture Review

### Critical Issues

None found.

### Warnings

#### Infrastructure Layer Organization - CommandRunner Package Mismatch
**Location:** `.iw/core/CommandRunner.scala:4`
**Problem:** `CommandRunner` is in package `iw.core.infrastructure` but the file is located at `.iw/core/CommandRunner.scala` instead of `.iw/core/infrastructure/CommandRunner.scala`
**Impact:** Package declaration doesn't match physical file location, causing confusion about module organization.
**Recommendation:** Move `CommandRunner.scala` to the correct directory to match its package declaration

#### Client Modules Placed in Core Instead of Infrastructure Layer
**Location:** `.iw/core/GitLabClient.scala:1`
**Problem:** `GitLabClient` (and `GitHubClient`, `LinearClient`, `YouTrackClient`) are infrastructure adapters but are placed in `.iw/core/` instead of `.iw/core/infrastructure/`
**Impact:** Blurs the FCIS boundary between functional core and imperative shell.
**Recommendation:** Consider moving client modules to infrastructure layer

### Suggestions

#### Consider Domain Package for Issue Domain Model
**Location:** `.iw/core/Config.scala:71-72`
**Problem:** `IssueTrackerType` enum is a domain concept but is defined in `Config.scala` alongside infrastructure concerns
**Recommendation:** Consider separating domain types from infrastructure configuration logic

#### Inconsistent Error Enum Naming Between Clients
**Location:** `.iw/core/GitLabClient.scala:11-14`
**Problem:** `GlabError` vs `GhOtherError` naming difference is confusing
**Recommendation:** Align error type names: use `GlabOtherError` for consistency

</review>

---

<review skill="scala3">

## Scala 3 Idioms Review

### Critical Issues

None found.

### Warnings

#### Enum Used Without Custom Methods - Consider Adding Extensions
**Location:** `.iw/core/GitLabClient.scala:11`
**Problem:** `GlabPrerequisiteError` enum has associated formatting functions defined outside the enum
**Recommendation:** Consider adding methods directly to enum for better encapsulation

### Suggestions

#### Consider Extension Methods for IssueTrackerType Pattern Matching
**Location:** `.iw/core/Config.scala:123`
**Problem:** Pattern matching on `IssueTrackerType.GitHub | IssueTrackerType.GitLab` appears in multiple locations
**Recommendation:** Consider adding `usesRepository: Boolean` method to enum

#### Pattern Matching in issue.scala Could Use Guard Clauses
**Location:** `.iw/commands/issue.scala:101-102`
**Problem:** Duplicated logic for extracting issue numbers from IssueId appears for both GitHub and GitLab
**Recommendation:** Extract common logic into helper function or extension method to IssueId

#### Issue ID Parsing Logic - TeamPrefix Missing for GitLab
**Location:** `.iw/commands/issue.scala:50-54`
**Problem:** TeamPrefix logic only considers GitHub but should also consider GitLab
**Recommendation:** Update conditional to include GitLab

#### Consider Opaque Type for Repository String
**Location:** `.iw/core/Config.scala:80`
**Problem:** `repository: Option[String]` is a plain String without type safety
**Recommendation:** Consider an opaque type for Repository (optional improvement)

</review>

---

<review skill="testing">

## Testing Review

### Critical Issues

None found.

### Warnings

#### Shared Mutable State in Integration Tests
**Location:** `.iw/core/test/GitLabClientTest.scala:294-328`
**Problem:** Tests use `var` for capturing command arguments, creating mutable state
**Impact:** Tests may fail non-deterministically in parallel execution
**Recommendation:** Refactor to use immutable capture patterns

### Suggestions

#### Consider Testing Edge Cases for State Normalization
**Location:** `.iw/core/test/GitLabClientTest.scala:45-206`
**Problem:** Tests don't verify unknown/unexpected state values behavior
**Recommendation:** Add test for unexpected state value to verify passthrough behavior

#### Consider Adding Test for GlabError Enum Variant
**Location:** `.iw/core/test/GitLabClientTest.scala:208-255`
**Problem:** `GlabPrerequisiteError.GlabError` enum variant is defined but never tested
**Recommendation:** Either remove unused variant or add test coverage

#### Test Coverage Missing for Issue.scala Integration
**Location:** `.iw/commands/issue.scala:94-106`
**Problem:** No unit tests for the GitLab case in `fetchIssue` function
**Recommendation:** Add focused tests for issue number extraction logic

#### Consider Test Organization with Nested Suites
**Location:** `.iw/core/test/GitLabClientTest.scala`
**Problem:** All 21 tests are flat in a single suite with comment separators
**Recommendation:** Consider using munit's nested suites for better organization (minor)

</review>

---

<review skill="style">

## Code Style Review

### Critical Issues

None found.

### Warnings

#### Inconsistent Comment Style in Test Names
**Location:** `.iw/core/test/GitLabClientTest.scala:10-257`
**Problem:** Test names use section headers with `// ========== Section Name ==========`
**Recommendation:** Consider using nested test suites instead (acceptable as-is)

#### Missing Scaladoc on Public Method Parameters
**Location:** `.iw/core/GitLabClient.scala:73-81`
**Problem:** Parameter `repository` mentions "can include nested groups" but doesn't specify format
**Recommendation:** Clarify with example in documentation

### Suggestions

#### Consider Using Inline Comments for State Mapping Explanation
**Location:** `.iw/core/GitLabClient.scala:106-111`
**Recommendation:** Make comment more concise

#### Comment in issue.scala Could Be More Precise
**Location:** `.iw/commands/issue.scala:49-50`
**Problem:** Comment mentions "for GitHub tracker" but code logic might also apply to GitLab
**Recommendation:** Update comment to reflect current reality

### Overall Assessment

**APPROVE with minor suggestions**

The code demonstrates excellent style consistency:
- ✅ All files have proper PURPOSE comments
- ✅ Naming conventions followed consistently
- ✅ Comprehensive Scaladoc on all public APIs
- ✅ Import organization is clean
- ✅ File organization follows one-primary-type-per-file pattern

</review>

---

## Summary

- **Critical issues:** 0 (no blockers for merge)
- **Warnings:** 5 (should consider addressing)
- **Suggestions:** 10 (nice to have)

### By Skill
- architecture: 0 critical, 2 warnings, 2 suggestions
- scala3: 0 critical, 1 warning, 4 suggestions
- testing: 0 critical, 1 warning, 4 suggestions
- style: 0 critical, 2 warnings, 2 suggestions

### Verdict: ✅ APPROVE

No critical issues found. The implementation follows established patterns (GitHubClient) and maintains good code quality. The warnings are primarily about organizational improvements that can be addressed in future refactoring.
