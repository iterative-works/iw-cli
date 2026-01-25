# Phase 2 Context: GitHub issue creation

**Issue:** IW-103
**Phase:** 2 of 7
**Story:** Story 1 - Create GitHub issue with title and description

## User Story

```gherkin
Feature: Create issue in GitHub repository
  As a developer working in a GitHub-tracked project
  I want to create issues via command line with title and description
  So that I can quickly log work without leaving my terminal

Scenario: Successfully create GitHub issue with title and description
  Given I am in a project configured for GitHub tracker
  And gh CLI is installed and authenticated
  And repository is set to "iterative-works/test-project"
  When I run "./iw issue create --title 'Fix login bug' --description 'Users cannot login with SSO'"
  Then a new issue is created in GitHub repository
  And the issue has title "Fix login bug"
  And the issue has body "Users cannot login with SSO"
  And I see output "Issue created: #123"
  And I see output "URL: https://github.com/iterative-works/test-project/issues/123"
```

## Acceptance Criteria

- [ ] Command creates issue in configured GitHub repository
- [ ] Returns issue number and URL
- [ ] Works with both --title + --description and --title only formats
- [ ] Follows existing error handling patterns (missing gh, not authenticated, etc.)
- [ ] Missing --title shows help and exits with code 1

## What Phase 1 Built

**Available components:**
- `.iw/commands/issue.scala` - Has `handleCreateSubcommand()` with routing for `create`
- `.iw/commands/issue-create.scala` - Standalone command entry point
- `showCreateHelp()` - Help text display function

**Extension point:**
- `handleCreateSubcommand()` has placeholder for actual creation logic (lines 45-53)
- Currently shows help and exits, needs real implementation

**From issue.scala (Phase 1):**
```scala
def handleCreateSubcommand(args: Seq[String]): Unit =
  // Handle --help flag
  if args.contains("--help") || args.contains("-h") then
    showCreateHelp()
    sys.exit(0)

  // Placeholder for actual implementation (Phase 2+)
  showCreateHelp()
  sys.exit(1)
```

## Available Infrastructure

**GitHubClient** (`.iw/core/GitHubClient.scala`):
- `createIssue(repository, title, description, issueType, ...)` - Exists but requires IssueType
- `buildCreateIssueCommandWithoutLabel(repository, title, description)` - Creates gh command without labels
- `parseCreateIssueResponse(output)` - Parses gh CLI response to CreatedIssue
- `validateGhPrerequisites(repository, ...)` - Validates gh installed and authenticated
- `formatGhNotInstalledError()` / `formatGhNotAuthenticatedError()` - Error messages

**Issue.scala patterns:**
- `loadConfig()` - Loads ProjectConfiguration from .iw/config.conf
- `config.repository` - Gets configured repository (e.g., "iterative-works/iw-cli")
- `config.trackerType` - Gets IssueTrackerType enum

**FeedbackParser** (example pattern):
- `parseFeedbackArgs(args)` - Parses title, --description, --type flags
- Returns `Either[String, FeedbackRequest]`

**Output utilities:**
- `Output.error(msg)` - Print error message
- `Output.success(msg)` - Print success message
- `Output.info(msg)` - Print info message

## Technical Approach

### 1. Argument Parser

Create `IssueCreateParser` to parse `--title` and `--description` flags:

```scala
case class IssueCreateRequest(title: String, description: Option[String])

object IssueCreateParser:
  def parse(args: Seq[String]): Either[String, IssueCreateRequest]
```

**Parse rules:**
- `--title <value>` is required
- `--description <value>` is optional (default to None/empty)
- Return Left("error message") if --title is missing

### 2. GitHub Issue Creation

Extend or create a simplified createIssue function:

**Option A:** Create `GitHubClient.createIssueSimple` without issueType parameter
**Option B:** Use existing `buildCreateIssueCommandWithoutLabel` + `parseCreateIssueResponse`

Recommended: Option B - reuse existing building blocks, compose in handleCreateSubcommand

### 3. Integration Flow

```scala
def handleCreateSubcommand(args: Seq[String]): Unit =
  // 1. Handle --help flag (already exists)

  // 2. Parse arguments
  IssueCreateParser.parse(args) match
    case Left(error) => Output.error(error); showCreateHelp(); sys.exit(1)
    case Right(request) =>

  // 3. Load configuration
  loadConfig() match
    case Left(error) => Output.error(error); sys.exit(1)
    case Right(config) =>

  // 4. Validate tracker type is GitHub
  if config.trackerType != IssueTrackerType.GitHub then
    // Phase 2 only supports GitHub - other trackers in later phases
    Output.error("Issue creation for this tracker not yet supported")
    sys.exit(1)

  // 5. Create issue using GitHubClient
  // 6. Output result
```

## Constraints

- **GitHub only in Phase 2** - Other trackers added in Phases 5-7
- **No labels** - MVP decision: title + description only
- **Non-interactive** - All args must be on command line
- **Reuse existing code** - Use GitHubClient methods, don't duplicate

## Testing Strategy

**E2E Tests (BATS):**
- Test: `iw issue create --title "Test" --description "Body"` creates issue
- Test: `iw issue create --title "Test"` works without description
- Test: `iw issue create` without --title shows help, exits 1
- Test: Output shows "Issue created: #N" and URL

**Unit Tests (optional):**
- IssueCreateParser.parse with various argument combinations
- Can stub gh CLI for integration tests

**Note:** For real E2E tests, need GitHub test repository. Can also test with stubbed/mocked gh CLI.

## Files to Create/Modify

**Create:**
- `.iw/core/IssueCreateParser.scala` - Argument parser (or add to existing file)

**Modify:**
- `.iw/commands/issue.scala` - Implement handleCreateSubcommand
- `.iw/test/issue-create.bats` - Add E2E tests for creation

**Optionally modify:**
- `.iw/core/GitHubClient.scala` - Add simplified createIssue if needed

## Dependencies

- Phase 1 complete (routing and help display)
- gh CLI installed for testing
- Test GitHub repository (iterative-works/iw-cli can be used)

## Out of Scope for Phase 2

- Linear/GitLab/YouTrack trackers (Phases 5-7)
- Prerequisite validation error messages (Phase 3)
- Labels or issue types
- Interactive mode
