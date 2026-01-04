# Code Review Results

**Review Context:** Phase 3: Error Handling for issue IW-79 (Iteration 1/3)
**Files Reviewed:** 14 files
**Skills Applied:** 5 (architecture, scala3, style, testing, security)
**Timestamp:** 2026-01-03
**Git Context:** git diff 9f511f3

---

<review skill="architecture">

## Architecture Review

### Critical Issues

None found.

### Warnings

#### Inconsistent Package Organization - Mixed Layering Structure
**Location:** `.iw/core/` (root directory)
**Problem:** The codebase has a partial layered structure with `application/`, `domain/`, and `presentation/` subdirectories, but most files (50+ files including services, clients, repositories, and domain models) remain scattered in the root `.iw/core/` package instead of being organized into proper layers.
**Impact:** This mixed organization makes it difficult to understand architectural boundaries and violates the principle of clear layer separation.
**Recommendation:** Complete the migration to a fully layered structure (domain/, application/, infrastructure/, presentation/).

#### Application Service Contains URL Construction Logic
**Location:** `.iw/core/application/IssueSearchService.scala:69-119`
**Problem:** The `IssueSearchService` contains `buildIssueUrl()` and `extractGitHubIssueNumber()` functions that construct tracker-specific URLs. This is presentation/infrastructure concern, not application logic.
**Impact:** Application layer is polluted with formatting/presentation logic.
**Recommendation:** Move URL construction to either Domain layer (TrackerUrlBuilder value object) or Presentation layer (in the view).

### Suggestions

#### Error Mapping Function Could Be in Domain Service
**Location:** `.iw/core/domain/WorktreeCreationError.scala:21`
**Problem:** The `toUserFriendly` error mapping function contains presentation-oriented details like exact error message formatting and UI suggestions.
**Recommendation:** Consider splitting into semantic ErrorContext (domain) and UI-specific formatting (presentation). However, current approach is acceptable if UserFriendlyError is considered a domain concept.

#### Consider Creating Explicit Port Interfaces
**Location:** `.iw/core/application/WorktreeCreationService.scala:27-35`
**Problem:** The service accepts function parameters for all I/O operations, which is valid but doesn't explicitly define ports/adapters.
**Recommendation:** For more explicit hexagonal architecture, consider defining port traits. However, current functional approach is also architecturally sound.

</review>

---

<review skill="scala3">

## Scala 3 Idioms Review

### Critical Issues

#### Use Enum Instead of Sealed Trait for WorktreeCreationError
**Location:** `.iw/core/domain/WorktreeCreationError.scala:6-14`
**Problem:** `WorktreeCreationError` is implemented as a sealed trait with case classes in companion object, which is a Scala 2 pattern.
**Impact:** More verbose than necessary, doesn't leverage Scala 3's enum feature designed for this exact use case.
**Recommendation:** Convert to Scala 3 enum:

```scala
enum WorktreeCreationError:
  case DirectoryExists(path: String)
  case AlreadyHasWorktree(issueId: String, existingPath: String)
  case GitError(message: String)
  case TmuxError(message: String)
  case IssueNotFound(issueId: String)
  case ApiError(message: String)

  def toUserFriendly: UserFriendlyError = this match
    case DirectoryExists(path) => ...
    // etc.
```

### Warnings

#### Consider Extension Method for toUserFriendly
**Location:** `.iw/core/domain/WorktreeCreationError.scala:21`
**Problem:** The `toUserFriendly` function in companion object could be more idiomatic as an instance method (with enum) or extension method.
**Recommendation:** If converting to enum, make it an instance method. If keeping sealed trait, consider extension method pattern.

#### Pattern Match Could Use Import for Short Names
**Location:** `.iw/core/CaskServer.scala:417-423`
**Problem:** Pattern matching on enum cases uses fully qualified names.
**Recommendation:** Use `import WorktreeCreationError.*` for cleaner pattern matching.

### Suggestions

- Consider Opaque Type for Issue ID in Error Types (minor type safety improvement)
- Optional Chaining and conditional logic are used idiomatically - no changes needed

</review>

---

<review skill="style">

## Code Style & Documentation Review

### Critical Issues

None found.

### Warnings

#### Missing Scaladoc on New Public Methods in WorktreeCreationService
**Location:** `.iw/core/application/WorktreeCreationService.scala:30-36`
**Problem:** New parameters `checkDirectoryExists` and `checkWorktreeExists` use inline documentation instead of Scaladoc `@param` tags.
**Recommendation:** Convert to proper Scaladoc format for IDE integration and API documentation tools.

#### Missing Scaladoc on IssueSearchService.search Parameter
**Location:** `.iw/core/IssueSearchService.scala:18`
**Problem:** New `checkWorktreeExists` parameter has inline comment but should use Scaladoc.
**Recommendation:** Add proper `@param` documentation.

#### Missing Scaladoc on New IssueSearchResult Field
**Location:** `.iw/core/IssueSearchResult.scala:12`
**Problem:** The new `hasWorktree` field should be documented in case class Scaladoc.
**Recommendation:** Update case class documentation to include all fields.

### Suggestions

- Consider more specific error title "Worktree Directory Already Exists"
- Consider adding example to renderWorktreeBadge documentation
- Test method names could use "should" pattern for consistency

</review>

---

<review skill="testing">

## Testing Review

### Critical Issues

None found.

### Warnings

#### Missing Edge Case: Empty/Whitespace Issue ID
**Location:** `.iw/core/test/WorktreeCreationServiceTest.scala`
**Problem:** No tests verify behavior when issueId is empty string or whitespace-only.
**Recommendation:** Add test cases for edge cases.

#### Test Data Contains Current Timestamp
**Location:** `.iw/core/test/WorktreeCreationServiceTest.scala:28`
**Problem:** Test data uses `Instant.now()` which makes tests non-deterministic.
**Recommendation:** Use fixed timestamp: `Instant.parse("2024-01-15T10:00:00Z")`

### Suggestions

- Consider parameterized tests for error mapping to reduce duplication
- Test names could be more behavior-focused
- Consider adding test for error precedence when multiple checks fail
- Domain model tests only check data holding (which Scala guarantees)
- HTML tests use string matching instead of DOM parsing (acceptable for this scale)

</review>

---

<review skill="security">

## Security Review

### Critical Issues

#### Missing Issue ID in Retry Button - CSRF/State Management Vulnerability
**Location:** `.iw/core/presentation/views/CreationErrorView.scala:74-82`
**Problem:** The retry button does not include the issue ID in its HTMX request. It posts to `/api/worktrees/create` without `hx-vals` containing the `issueId`, expecting it to be "preserved in the modal state" (line 70). However, the server expects `requestJson("issueId").str`, which will fail without the issue ID.
**Impact:** The retry functionality is broken - it will return a 400 error. More concerningly, relying on client-side "modal state" for security-sensitive operations is dangerous.
**Recommendation:** Include the issue ID explicitly in retry button's `hx-vals` attribute. Add `issueId: Option[String]` to `UserFriendlyError` so it can be rendered in the retry button.

#### Information Disclosure in Error Messages
**Location:** `.iw/core/domain/WorktreeCreationError.scala:24-72`
**Problem:** Error messages expose internal system details including:
- Full file system paths
- Exact error messages from Git
- Exact error messages from Tmux
- API error details
**Impact:** Attackers can learn about server's directory structure, git configuration, and internal system details.
**Recommendation:** Generic error messages for users, detailed logging server-side only.

#### Path Traversal Vulnerability in Directory Name Display
**Location:** `.iw/core/domain/WorktreeCreationError.scala:24,28,33`
**Problem:** Using `path.split("/").last` to extract directory names could be vulnerable if path contains malicious sequences.
**Recommendation:** Use proper path parsing library and validate/sanitize before display.

### Warnings

#### No Input Validation on Issue ID in Search Results
**Location:** `.iw/core/presentation/views/SearchResultsView.scala:70`
**Problem:** Issue ID is interpolated directly into JSON without escaping: `s"""{"issueId": "${result.id}"}"""`
**Recommendation:** Properly escape or use ujson library.

#### Logging Sensitive Information to System.err
**Location:** `.iw/core/CaskServer.scala:401`
**Problem:** Logs entire error object which may contain file paths and internal details.
**Recommendation:** Log only sanitized error information.

#### Command Injection Risk in Suggestion Text
**Location:** `.iw/core/domain/WorktreeCreationError.scala:28,38`
**Problem:** Suggestion text includes shell commands with user-influenced data.
**Recommendation:** Validate safe characters before including in shell commands.

### Suggestions

- Use Opaque Type for Issue ID
- Add Rate Limiting for Retry Attempts
- Consider Adding CSP Headers
- Verify CSRF Protection is Applied via Middleware

</review>

---

## Summary

- **Critical issues:** 3 (must fix before merge)
- **Warnings:** 12 (should fix)
- **Suggestions:** 15 (nice to have)

### By Skill
- **architecture:** 0 critical, 2 warnings, 2 suggestions
- **scala3:** 1 critical, 2 warnings, 1 suggestion
- **style:** 0 critical, 3 warnings, 3 suggestions
- **testing:** 0 critical, 2 warnings, 6 suggestions
- **security:** 2 critical, 3 warnings, 3 suggestions

### Critical Issues Summary

1. **[scala3]** Use Scala 3 `enum` instead of sealed trait for `WorktreeCreationError`
2. **[security]** Retry button missing issue ID - functionality broken and security risk
3. **[security]** Information disclosure in error messages exposes internal system details
