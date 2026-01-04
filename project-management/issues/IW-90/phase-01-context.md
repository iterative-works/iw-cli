# Phase 01 Context: Fetch and display GitLab issue via glab CLI

**Issue:** IW-90
**Phase:** 1 of 7
**Status:** Not Started

## Goals

This phase implements the core GitLabClient infrastructure to fetch and display GitLab issues using the glab CLI. This establishes the foundation for all subsequent GitLab integration work.

**Primary goal:** Users can run `iw issue 123` with GitLab tracker configured and see the issue details.

## Scope

### In Scope

1. **GitLabClient module** - New infrastructure component following GitHubClient pattern
   - `validateGlabPrerequisites` - Check glab installed and authenticated
   - `buildFetchIssueCommand` - Construct glab CLI command array
   - `parseFetchIssueResponse` - Parse JSON output to Issue domain model
   - `fetchIssue` - Orchestrate fetch operation

2. **IssueTrackerType.GitLab** - Add new enum variant to domain model

3. **issue.scala integration** - Handle GitLab case in issue command

4. **Basic prerequisite validation** - Check glab is installed (detailed error messages in Phase 2)

### Out of Scope

- Detailed error handling and user-friendly error messages (Phase 2)
- Configuration via `iw init` (Phase 3)
- URL generation for search/dashboard (Phase 4)
- Issue creation (Phase 5)
- ID parsing from branch names (Phase 6)
- Comprehensive testing (Phase 7)

## Dependencies

### From Previous Phases
None - this is the first phase.

### External Dependencies
- glab CLI must be installed for testing
- Access to test GitLab repository for manual verification

### Codebase Dependencies
- `domain/Config.scala` - IssueTrackerType enum
- `infrastructure/GitHubClient.scala` - Pattern to follow
- `presentation/issue.scala` - Integration point

## Technical Approach

### 1. Domain Layer Changes

Add GitLab to IssueTrackerType enum:
```scala
enum IssueTrackerType:
  case GitHub, Linear, YouTrack, GitLab  // Add GitLab
```

### 2. Infrastructure Layer - GitLabClient

Create `infrastructure/GitLabClient.scala` following GitHubClient pattern:

**Key functions:**

```scala
object GitLabClient:
  enum GlabPrerequisiteError:
    case GlabNotInstalled
    case GlabNotAuthenticated
    case GlabError(message: String)

  def validateGlabPrerequisites(
    repository: String,
    execCommand: Array[String] => Either[String, String] = defaultExecCommand
  ): Either[GlabPrerequisiteError, Unit]

  def buildFetchIssueCommand(issueNumber: String, repository: String): Array[String]

  def parseFetchIssueResponse(jsonOutput: String, issueNumber: String): Either[String, Issue]

  def fetchIssue(
    issueNumber: String,
    repository: String,
    execCommand: Array[String] => Either[String, String] = defaultExecCommand
  ): Either[String, Issue]
```

**glab CLI command format:**
```bash
glab issue view 123 --repo owner/project --output json
```

**Expected JSON response:**
```json
{
  "iid": 123,
  "state": "opened",
  "title": "Issue title",
  "description": "Issue body",
  "author": {"username": "user", "name": "Full Name"},
  "assignees": [{"username": "dev", "name": "Developer"}],
  "labels": ["bug"],
  "web_url": "https://gitlab.com/org/repo/-/issues/123"
}
```

**State mapping:**
- `"opened"` → `IssueState.Open`
- `"closed"` → `IssueState.Closed`
- Other → `IssueState.Unknown`

### 3. Presentation Layer - issue.scala

Add GitLab case to fetchIssue function pattern match:
```scala
case IssueTrackerType.GitLab =>
  val repository = config.repository.getOrElse(...)
  GitLabClient.fetchIssue(issueNumber, repository)
```

## Files to Modify

| File | Change |
|------|--------|
| `modules/domain/Config.scala` | Add `GitLab` to IssueTrackerType enum |
| `modules/infrastructure/GitLabClient.scala` | **NEW** - GitLab client implementation |
| `modules/presentation/issue.scala` | Add GitLab case to fetchIssue |

## Testing Strategy

### Unit Tests (GitLabClientTest.scala)

1. **Command building tests:**
   - `buildFetchIssueCommand("123", "my-org/my-project")` returns correct array
   - Handles nested groups: `"company/team/project"`

2. **JSON parsing tests:**
   - Parse valid issue JSON → Issue domain object
   - Handle opened/closed states
   - Handle missing optional fields (description, assignees)
   - Handle malformed JSON → error

3. **Prerequisite validation tests:**
   - glab not installed → GlabNotInstalled error
   - glab not authenticated → GlabNotAuthenticated error
   - glab available and authenticated → success

### Integration Tests (with mocked execCommand)

1. Fetch issue with mocked glab output → parsed Issue
2. Fetch issue with glab error → appropriate error message

### Manual Verification

1. Configure GitLab tracker manually in config.conf
2. Run `iw issue 123` with real glab CLI
3. Verify issue displays correctly

## Acceptance Criteria

- [ ] `IssueTrackerType.GitLab` enum variant exists
- [ ] `GitLabClient.scala` module created with all functions
- [ ] `GitLabClient.buildFetchIssueCommand` constructs correct glab command
- [ ] `GitLabClient.parseFetchIssueResponse` parses JSON to Issue
- [ ] `GitLabClient.validateGlabPrerequisites` checks glab is installed
- [ ] `issue.scala` handles GitLab tracker type
- [ ] Unit tests pass for command building and parsing
- [ ] Manual test: `iw issue` works with GitLab tracker (requires manual config)

## Notes

- This phase focuses on the happy path. Detailed error handling comes in Phase 2.
- Configuration support comes in Phase 3, so testing requires manual config file editing.
- The glab CLI JSON format has been verified during analysis (see analysis.md Technical Decisions).
- Follow TDD: write tests first, then implementation.
