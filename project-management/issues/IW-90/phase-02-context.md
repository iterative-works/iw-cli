# Phase 02 Context: Handle GitLab-specific error conditions gracefully

**Issue:** IW-90
**Phase:** 2 of 7
**Status:** Not Started

## Goals

This phase improves user experience by providing clear, actionable error messages when GitLab operations fail. Users should understand exactly what went wrong and how to fix it.

**Primary goal:** When glab CLI operations fail, users see helpful error messages with specific remediation steps.

## Scope

### In Scope

1. **User-friendly error messages** - Transform technical glab errors into actionable guidance
   - `formatGlabNotInstalledError()` - Instructions for installing glab CLI
   - `formatGlabNotAuthenticatedError()` - Instructions for running `glab auth login`
   - `formatIssueNotFoundError(issueId)` - Clear "issue not found" message

2. **Error detection utilities**
   - `isAuthenticationError(error: String)` - Detect auth-related failures
   - `isNotFoundError(error: String)` - Detect 404 responses
   - `isNetworkError(error: String)` - Detect connectivity issues

3. **Enhanced error presentation in issue.scala**
   - Match error types and display appropriate messages
   - Exit with appropriate status codes

### Out of Scope

- Retry logic for transient failures (future enhancement)
- Offline caching (not planned)
- Alternative authentication methods (glab handles this)

## Dependencies

### From Previous Phases

- **Phase 1:** `GitLabClient.scala` module with:
  - `GlabPrerequisiteError` enum (GlabNotInstalled, GlabNotAuthenticated, GlabError)
  - `validateGlabPrerequisites()` function
  - `fetchIssue()` function returning `Either[String, Issue]`

### External Dependencies

- glab CLI error output format (validated during Phase 1)

### Codebase Dependencies

- `modules/infrastructure/GitLabClient.scala` - Add error formatting functions
- `modules/presentation/issue.scala` - Enhance error handling in GitLab case

## Technical Approach

### 1. Error Message Functions

Add to `GitLabClient.scala`:

```scala
def formatGlabNotInstalledError(): String =
  """glab CLI is not installed.
    |
    |To install glab:
    |  macOS:   brew install glab
    |  Linux:   See https://gitlab.com/gitlab-org/cli#installation
    |  Windows: winget install glab.glab
    |
    |After installation, authenticate with: glab auth login""".stripMargin

def formatGlabNotAuthenticatedError(): String =
  """glab CLI is not authenticated.
    |
    |Run: glab auth login
    |
    |This will open a browser to authenticate with your GitLab instance.""".stripMargin

def formatIssueNotFoundError(issueId: String, repository: String): String =
  s"Issue $issueId not found in repository $repository."

def formatNetworkError(details: String): String =
  s"""Network error while connecting to GitLab.
     |
     |Details: $details
     |
     |Check your network connection and try again.""".stripMargin
```

### 2. Error Detection Functions

```scala
def isAuthenticationError(error: String): Boolean =
  error.contains("401") ||
  error.toLowerCase.contains("unauthorized") ||
  error.toLowerCase.contains("authentication")

def isNotFoundError(error: String): Boolean =
  error.contains("404") ||
  error.toLowerCase.contains("not found")

def isNetworkError(error: String): Boolean =
  error.toLowerCase.contains("network") ||
  error.toLowerCase.contains("connection") ||
  error.toLowerCase.contains("timeout") ||
  error.toLowerCase.contains("could not resolve")
```

### 3. Enhanced Error Handling in issue.scala

```scala
case IssueTrackerType.GitLab =>
  GitLabClient.validateGlabPrerequisites(repository) match
    case Left(GlabPrerequisiteError.GlabNotInstalled) =>
      Left(GitLabClient.formatGlabNotInstalledError())
    case Left(GlabPrerequisiteError.GlabNotAuthenticated) =>
      Left(GitLabClient.formatGlabNotAuthenticatedError())
    case Left(GlabPrerequisiteError.GlabError(msg)) =>
      Left(s"glab error: $msg")
    case Right(_) =>
      GitLabClient.fetchIssue(issueNumber, repository) match
        case Left(error) if GitLabClient.isNotFoundError(error) =>
          Left(GitLabClient.formatIssueNotFoundError(issueNumber, repository))
        case Left(error) if GitLabClient.isAuthenticationError(error) =>
          Left(GitLabClient.formatGlabNotAuthenticatedError())
        case Left(error) if GitLabClient.isNetworkError(error) =>
          Left(GitLabClient.formatNetworkError(error))
        case result => result
```

## Files to Modify

| File | Change |
|------|--------|
| `.iw/core/GitLabClient.scala` | Add error formatting and detection functions |
| `.iw/core/test/GitLabClientTest.scala` | Add tests for error functions |
| `.iw/commands/issue.scala` | Enhanced error handling for GitLab case |

## Testing Strategy

### Unit Tests (GitLabClientTest.scala)

1. **Error formatting tests:**
   - `formatGlabNotInstalledError` returns multi-line installation guide
   - `formatGlabNotAuthenticatedError` returns auth instructions
   - `formatIssueNotFoundError` includes issue ID and repository

2. **Error detection tests:**
   - `isAuthenticationError` detects "401", "unauthorized", "authentication"
   - `isNotFoundError` detects "404", "not found"
   - `isNetworkError` detects network-related strings
   - Negative cases: unrelated errors return false

### Integration Tests

1. Mock glab returning 404 → formatIssueNotFoundError displayed
2. Mock glab returning 401 → formatGlabNotAuthenticatedError displayed
3. Mock glab not found → formatGlabNotInstalledError displayed

### Manual Verification

1. Uninstall glab, run `iw issue 123` → see installation instructions
2. Run `glab auth logout`, then `iw issue 123` → see auth instructions
3. Run `iw issue 999999` (non-existent) → see "not found" message

## Acceptance Criteria

- [ ] `formatGlabNotInstalledError()` returns installation instructions for macOS, Linux, Windows
- [ ] `formatGlabNotAuthenticatedError()` returns `glab auth login` instructions
- [ ] `formatIssueNotFoundError()` includes issue ID and repository name
- [ ] `isAuthenticationError()` correctly detects 401 and auth-related errors
- [ ] `isNotFoundError()` correctly detects 404 and "not found" errors
- [ ] `isNetworkError()` correctly detects network-related errors
- [ ] `issue.scala` displays formatted errors instead of raw glab output
- [ ] Unit tests pass for all error formatting and detection functions
- [ ] Error messages are actionable and user-friendly

## Notes

- Follow existing error handling patterns from GitHubClient
- Error messages should be multi-line for readability
- Include specific commands users need to run
- Keep error detection simple - string matching is sufficient
- This phase focuses on read operations; write operation errors come in Phase 5
