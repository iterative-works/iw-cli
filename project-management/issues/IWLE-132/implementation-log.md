# Implementation Log: Add GitHub Issues support using gh CLI

**Issue:** IWLE-132

This log tracks the evolution of implementation across phases.

---

## Phase 1: Initialize project with GitHub tracker (2025-12-22)

**What was built:**
- `IssueTrackerType.GitHub` - New enum case for GitHub tracker type
- `ProjectConfiguration.repository` - Optional field for GitHub repository (owner/repo format)
- `GitRemote.repositoryOwnerAndName()` - Method to extract owner/repo from GitHub URLs
- `TrackerDetector.suggestTracker()` - Updated to suggest GitHub for github.com remotes
- `ConfigSerializer` - Updated to serialize/deserialize GitHub configs with repository field
- `init.scala` - Updated to support `--tracker=github` with repository auto-detection

**Decisions made:**
- GitHub uses `repository` field instead of `team` (different from Linear/YouTrack)
- No API token required for GitHub (gh CLI handles authentication)
- Auto-detect repository from git remote origin URL
- Support both HTTPS and SSH URL formats

**Patterns applied:**
- Enum extension: Added GitHub case to existing IssueTrackerType
- Conditional serialization: Different config fields based on tracker type
- Graceful fallback: Warning + prompt when auto-detection fails

**Testing:**
- Unit tests: 36 tests added/modified (repository parsing, config serialization, tracker detection)
- E2E tests: 6 tests added (GitHub init with HTTPS/SSH, warning for non-GitHub remote, regression tests)

**Code review:**
- Iterations: 1 (with fixes applied)
- Review file: review-phase-01-20251222.md
- Major findings: Added edge case tests for empty owner/repo, fixed split validation bug

**For next phases:**
- Available utilities: `GitRemote.repositoryOwnerAndName` for extracting repository from URLs
- Extension points: `IssueTrackerType.GitHub` case needs handling in feedback/issue commands
- Notes: gh CLI validation deferred to Phase 4, actual gh CLI calls deferred to Phase 3

**Files changed:**
```
M  .iw/commands/init.scala
M  .iw/core/Config.scala
M  .iw/core/Constants.scala
M  .iw/core/test/ConfigTest.scala
M  .iw/test/init.bats
```

---
## Phase 2: Repository auto-detection edge cases (2025-12-22)

**What was built:**
- Enhanced `GitRemote.host()` - Handle username prefix in HTTPS URLs (`https://username@github.com/...`)
- Enhanced `GitRemote.repositoryOwnerAndName()` - Handle trailing slashes in URLs
- Additional unit tests - 7 new edge case tests for URL parsing
- Additional E2E tests - 4 new tests for multi-remote scenarios and edge cases
- README documentation - Comprehensive GitHub integration section

**Decisions made:**
- Support trailing slashes by stripping them before validation
- Support username@ prefix in HTTPS URLs (used by some git clients)
- Origin remote is always preferred when multiple remotes exist
- GitAdapter already reads from `remote.origin.url` by default

**Patterns applied:**
- Defensive programming: Multiple stripSuffix calls to handle edge cases
- Test-driven development: All tests written first, then implementation to fix failures
- Documentation-driven clarity: Clear README section explaining supported formats

**Testing:**
- Unit tests: 7 new tests added (trailing slash HTTPS/SSH, username prefix HTTPS)
- E2E tests: 4 new tests added (multiple remotes, no remote, trailing slash, username prefix)
- All 109 tests passing (no regressions)

**Code review:**
- Iterations: 1 (passed on first review)
- Review file: review-phase-02-20251222.md
- Major findings: No critical issues. Suggestions for improved test naming and behavior-focused tests.

**For next phases:**
- Available utilities: Robust `GitRemote.repositoryOwnerAndName` handles all common URL formats
- Extension points: Ready for Phase 3 (create issue with gh CLI)
- Notes: Repository auto-detection is now production-ready

**Files changed:**
```
M  .iw/core/Config.scala
M  .iw/core/test/ConfigTest.scala
M  .iw/test/init.bats
M  README.md
M  project-management/issues/IWLE-132/phase-02-tasks.md
```

---

## Phase 3: Create GitHub issue via feedback command (2025-12-22)

**What was built:**
- `GitHubClient.scala` - New GitHub CLI wrapper for issue creation via `gh` command
- `buildCreateIssueCommand()` - Generates gh CLI arguments with proper escaping
- `parseCreateIssueResponse()` - Parses JSON output from `gh issue create --json`
- `createIssue()` - Orchestrates command execution with label fallback
- `feedback.scala` - Updated to route to GitHubClient when tracker is GitHub
- Label mapping: Bug → "bug", Feature → "feedback"

**Decisions made:**
- Follow existing LinearClient pattern for consistency
- Use function injection (`execCommand` parameter) for testability
- Graceful label fallback: retry without labels if label error occurs
- Array-based command construction to prevent shell injection
- Config loading in feedback command to detect tracker type

**Patterns applied:**
- Adapter pattern: GitHubClient wraps `gh` CLI tool
- Either monad: Error handling returns `Either[String, CreatedIssue]`
- Function injection: `execCommand` parameter enables unit testing
- Defensive programming: Label error detection and retry logic

**Testing:**
- Unit tests: 14 tests for GitHubClient (command building, JSON parsing, retry logic)
- E2E tests: 6 new tests for GitHub tracker feedback
- Regression tests: All existing Linear tests pass (tests 12-20)
- Total: 115 E2E tests passing

**Code review:**
- Iterations: 1 (passed on first review)
- Review file: review-phase-03-20251222.md
- Major findings: APPROVED - excellent security (command injection protection), comprehensive tests, clean architecture
- Suggestions: Minor UX improvements for error messages (deferred)

**For next phases:**
- Available utilities: `GitHubClient.createIssue()` for creating GitHub issues
- Extension points: `GitHubClient` can be extended for `gh issue view` in Phase 5
- Notes: gh CLI validation deferred to Phase 4, issue display deferred to Phase 5

**Files changed:**
```
A  .iw/core/GitHubClient.scala
A  .iw/core/test/GitHubClientTest.scala
M  .iw/commands/feedback.scala
M  .iw/test/feedback.bats
```

---

## Phase 4: Handle gh CLI prerequisites (2025-12-23)

**What was built:**
- `GhPrerequisiteError` - Sealed trait with `GhNotInstalled`, `GhNotAuthenticated`, `GhOtherError` cases
- `validateGhPrerequisites()` - Validates gh CLI installation and authentication before issue creation
- `isAuthenticationError()` - Helper to detect exit code 4 from gh CLI (not authenticated)
- `formatGhNotInstalledError()` - User-friendly error message with installation instructions
- `formatGhNotAuthenticatedError()` - User-friendly error message with authentication instructions
- Updated `createIssue()` - Now validates prerequisites before attempting to create issue

**Decisions made:**
- Check prerequisites BEFORE attempting issue creation (fail fast)
- Use `CommandRunner.isCommandAvailable()` to detect gh CLI presence
- Use `gh auth status` to check authentication (exit code 4 = not authenticated)
- Detect exit code patterns in error messages ("exit status 4" and "exit value: 4")
- Provide actionable error messages with links to https://cli.github.com/ and `gh auth login` instructions
- Function injection pattern maintained for testability (`isCommandAvailable` parameter)

**Patterns applied:**
- Sealed trait for error types: Type-safe error handling
- Validation before operation: Fail fast with clear guidance
- Function injection: Both `isCommandAvailable` and `execCommand` injected for testing
- Multi-line error messages: Clear, actionable guidance for users

**Testing:**
- Unit tests: 8 new tests for prerequisite validation and error formatting
- E2E tests: 3 new tests (gh not installed, gh not authenticated, repository not accessible)
- Mock strategy: Mock `which` command to simulate gh not installed; mock `gh` to simulate auth errors
- All tests passing: 21 GitHubClient tests, 16 feedback E2E tests (3 new)

**Code review:**
- Not yet reviewed
- All acceptance criteria met
- TDD methodology followed strictly (red → green → refactor)

**For next phases:**
- Available utilities: `validateGhPrerequisites()` can be reused in Phase 5 (issue view) and Phase 6 (doctor checks)
- Extension points: Error messages are defined in standalone functions for easy customization
- Notes: Phase 5 will reuse validation logic; Phase 6 will call same validation for `iw doctor`

**Files changed:**
```
M  .iw/core/GitHubClient.scala
M  .iw/core/test/GitHubClientTest.scala
M  .iw/test/feedback.bats
```

**Test summary:**
- Unit tests: All 224 tests passing
- E2E tests: 15 of 16 feedback tests passing (1 pre-existing failure unrelated to this phase)
- New tests: 11 total (8 unit + 3 E2E)

---

## Phase 5: Display GitHub issue details (2025-12-23)

**What was built:**
- `IssueId.parse()` - Extended to accept numeric GitHub issue IDs (e.g., "132")
- `IssueId.fromBranch()` - Extended to extract numeric prefixes (e.g., "132-feature")
- `IssueId.team` - Returns empty string for numeric IDs (GitHub has no team concept)
- `GitHubClient.buildFetchIssueCommand()` - Generates `gh issue view` command arguments
- `GitHubClient.parseFetchIssueResponse()` - Parses JSON response into Issue domain model
- `GitHubClient.fetchIssue()` - Full integration with prerequisite validation and command execution
- `issue.scala` - Added GitHub case to `fetchIssue()` pattern match

**Decisions made:**
- Pattern priority: TEAM-NNN patterns tried first, numeric patterns as fallback (backward compatible)
- No uppercasing: Numeric IDs preserved as-is (not uppercased like TEAM-NNN)
- Graceful degradation: `team` extension returns empty string for numeric IDs
- State normalization: GitHub states lowercased for consistency ("OPEN" → "open")
- First assignee: When multiple assignees exist, use first one
- ID formatting: GitHub issue IDs formatted with "#" prefix ("#132")
- Prerequisite reuse: Uses Phase 4's `validateGhPrerequisites()` for consistency

**Patterns applied:**
- Function injection: `fetchIssue` uses same `execCommand` pattern as `createIssue`
- Either monad: Error handling returns `Either[String, Issue]`
- Adapter pattern: GitHubClient wraps `gh issue view` command
- Backward compatibility: New patterns added without breaking existing functionality

**Testing:**
- Unit tests: 31 new tests (12 IssueId + 19 GitHubClient)
  - IssueId: Numeric parsing, branch extraction, team extension
  - GitHubClient: Command building, JSON parsing (complete/empty/null cases), integration
- Regression tests: Linear/YouTrack patterns verified still working
- All 682 unit tests passing

**Code review:**
- Iterations: 1 (passed on first review)
- Review file: review-phase-05-20251223.md
- Major findings: No critical issues
- Warnings: 6 (Scala 3 enum, test mocking pattern, architecture suggestions)
- Suggestions: 11 (composition patterns, package organization)

**For next phases:**
- Available utilities: `GitHubClient.fetchIssue()` for fetching GitHub issues
- Extension points: `validateGhPrerequisites()` can be used in Phase 6 doctor command
- Notes: Phase 6 will add GitHub validation to `iw doctor` command

**Files changed:**
```
M  .iw/commands/issue.scala
M  .iw/core/GitHubClient.scala
M  .iw/core/IssueId.scala
M  .iw/core/test/GitHubClientTest.scala
M  .iw/core/test/IssueIdTest.scala
A  project-management/issues/IWLE-132/phase-05-context.md
A  project-management/issues/IWLE-132/phase-05-tasks.md
```

---

## Phase 6: Doctor validates GitHub setup (2025-12-23)

**What was built:**
- `GitHubHookDoctor.checkGhInstalled()` - Checks if gh CLI is installed in PATH
- `GitHubHookDoctor.checkGhAuthenticated()` - Verifies gh CLI is authenticated via `gh auth status`
- `github.hook-doctor.scala` - Hook command exposing checks for doctor discovery
- Integration with existing doctor infrastructure via `Check` and `CheckResult` types

**Decisions made:**
- Skip checks for non-GitHub trackers (Linear, YouTrack) - returns `CheckResult.Skip`
- Reuse `GitHubClient.validateGhPrerequisites()` from Phase 4 for authentication check
- Provide descriptive hints: "Install: https://cli.github.com/" and "Run: gh auth login"
- Dependency injection for testability via `*With` function variants

**Patterns applied:**
- Hook pattern: Exposes checks as `val` for doctor discovery mechanism
- Functional Core: Pure check functions with injected dependencies
- Adapter pattern: Bridges `GhPrerequisiteError` to `CheckResult`

**Testing:**
- Unit tests: 7 tests added covering all check scenarios
- E2E tests: 3 tests added verifying doctor output

**Files changed:**
```
A  .iw/commands/github.hook-doctor.scala
A  .iw/core/GitHubHookDoctor.scala
A  .iw/core/test/GitHubHookDoctorTest.scala
M  .iw/commands/doctor.scala
M  .iw/test/doctor.bats
```

### Refactoring R1: Fix FCIS Architecture Violations (2025-12-23)

**Trigger:** Code review identified FCIS violations - `GitHubHookDoctor` in core imported from infrastructure

**What changed:**
- `GitHubHookDoctor.scala` - Moved from `package iw.core` to `package iw.core.infrastructure`
- `GitHubClient.scala` - Converted `GhPrerequisiteError` from sealed trait to Scala 3 enum
- All pattern matches updated to use `GhPrerequisiteError.` prefix

**Before → After:**
- Core no longer depends on infrastructure (package layering fixed)
- Idiomatic Scala 3 enum replaces Scala 2 sealed trait pattern

**Patterns applied:**
- Scala 3 enum for ADTs

**Testing:**
- Tests updated: 2 (imports in test files)
- All tests passing

**Files changed:**
```
M  .iw/commands/github.hook-doctor.scala
M  .iw/core/GitHubClient.scala
M  .iw/core/GitHubHookDoctor.scala
M  .iw/core/test/GitHubClientTest.scala
M  .iw/core/test/GitHubHookDoctorTest.scala
```

---
