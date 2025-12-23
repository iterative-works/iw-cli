# Phase 3 Context: Create GitHub issue via feedback command

**Issue:** IWLE-132
**Phase:** 3 of 6
**Story:** Create GitHub issue via feedback command

---

## Goals

1. **Make feedback command work with GitHub tracker** - When `tracker.type = github`, the feedback command should create a GitHub issue via `gh issue create`
2. **Create GitHubClient service** - Follow the same pattern as LinearClient/YouTrackClient for consistency
3. **Map issue types to GitHub labels** - Bug → "bug", Feature → "feedback" (with graceful fallback if labels don't exist)
4. **Show issue URL on success** - Display the created issue number and URL

## Scope

### In Scope
- Create `GitHubClient.scala` following existing client patterns
- Modify `feedback.scala` to detect GitHub tracker and route to GitHubClient
- Parse `gh issue create --json` output to extract issue number and URL
- Read repository from config for GitHub tracker
- Handle basic error cases (gh command failure, network errors)
- Unit tests for GitHubClient methods
- E2E tests for feedback with GitHub tracker

### Out of Scope
- Detailed gh CLI error detection (not installed, not authenticated) - Phase 4
- Reading/displaying GitHub issues - Phase 5
- Doctor checks for GitHub - Phase 6
- Complex error handling with helpful user messages - Phase 4

## Dependencies

### From Previous Phases
- **Phase 1**: `IssueTrackerType.GitHub` enum, `ProjectConfiguration.repository` field
- **Phase 2**: `GitRemote.repositoryOwnerAndName()` for URL parsing (not directly needed here, already in config)

### External Dependencies
- `gh` CLI must be installed and authenticated
- Repository must exist and be accessible

## Technical Approach

### 1. GitHubClient Pattern

Follow the existing `LinearClient.scala` pattern:
- Object with static methods
- `createIssue()` method that returns `Either[String, CreatedIssue]`
- Use ProcessAdapter or direct shell execution for `gh` CLI calls
- Parse JSON output from gh CLI

### 2. gh CLI Command

```bash
gh issue create --repo owner/repo --title "Title" --body "Description" --label bug --json number,url
```

**JSON output format:**
```json
{
  "number": 132,
  "url": "https://github.com/owner/repo/issues/132"
}
```

### 3. Feedback Command Changes

Current flow (Linear only):
```
feedback.scala
  → Check LINEAR_API_TOKEN
  → Parse args
  → LinearClient.createIssue()
  → Show result
```

New flow (tracker-aware):
```
feedback.scala
  → Load config (detect tracker type)
  → Parse args
  → Match on tracker type:
      GitHub → GitHubClient.createIssue(repository, ...)
      Linear → (existing) LinearClient.createIssue(token, teamId, ...)
      YouTrack → (TBD, not in this phase)
  → Show result
```

### 4. Config Reading

The feedback command currently doesn't read config - it only uses env vars. For GitHub:
- Read `.iw/config.conf` to get tracker type and repository
- Only require LINEAR_API_TOKEN when tracker is Linear
- For GitHub, no token needed (gh handles auth)

### 5. Label Mapping

| Issue Type | GitHub Label |
|------------|--------------|
| Bug | "bug" |
| Feature | "feedback" |

If labels don't exist in repo, `gh issue create` will fail. Options:
1. **Graceful fallback**: Create issue without labels (chosen approach)
2. Try with labels, retry without on failure

## Files to Modify

### New Files
- `.iw/core/GitHubClient.scala` - GitHub CLI wrapper
- `.iw/core/test/GitHubClientTest.scala` - Unit tests

### Modified Files
- `.iw/commands/feedback.scala` - Add GitHub routing and config reading
- `.iw/test/feedback.bats` - Add E2E tests for GitHub tracker
- `.iw/core/Constants.scala` - Add GitHub label names (optional, could hardcode)

## Testing Strategy

### Unit Tests (GitHubClientTest.scala)
1. `buildCreateIssueCommand` - Verify correct gh CLI args generated
2. `parseCreateIssueResponse` - Parse valid JSON output
3. `parseCreateIssueResponse` - Handle missing fields
4. `parseCreateIssueResponse` - Handle malformed JSON
5. Label handling - With and without labels

### E2E Tests (feedback.bats)
1. `feedback with GitHub tracker creates issue` - Full flow with real gh
2. `feedback with GitHub tracker shows issue URL` - Verify output format
3. `feedback with GitHub tracker and bug type applies label` - Label mapping
4. `feedback without gh CLI fails with helpful message` - (Basic version, details in Phase 4)
5. `feedback works for Linear when config is Linear` - Regression test

### Test Environment
- Default: Skip GitHub tests if `gh` not authenticated or `IW_TEST_GITHUB_REPO` not set
- With `IW_TEST_REAL_GITHUB=1`: Run against real test repository

## Acceptance Criteria

1. **AC1: GitHub tracker routes to GitHubClient**
   - When config has `tracker.type = github`, feedback uses GitHubClient instead of LinearClient
   - LINEAR_API_TOKEN is not required for GitHub tracker

2. **AC2: Issue created successfully**
   - Running `iw feedback "Title" --description "Body" --type bug` creates a GitHub issue
   - Issue has title "Title" and body "Body"
   - Issue has label "bug"

3. **AC3: Success output shows issue info**
   - Output shows "Feedback submitted successfully!"
   - Output shows issue number and URL (e.g., "Issue: #132", "URL: https://...")

4. **AC4: Basic error handling**
   - If gh command fails, show error message (details in Phase 4)
   - If config missing repository, show error message

5. **AC5: Existing Linear flow unchanged**
   - When tracker is Linear, existing behavior is preserved
   - LINEAR_API_TOKEN still required for Linear

## Gherkin Scenarios (from analysis.md)

```gherkin
Scenario: Create bug report via feedback
  Given the project is configured with GitHub tracker
  And the repository is "iterative-works/iw-cli"
  And gh CLI is authenticated
  When I run 'iw feedback "Bug in start command" --type bug --description "Command crashes on invalid input"'
  Then a GitHub issue is created via "gh issue create"
  And the issue has title "Bug in start command"
  And the issue has label "bug"
  And the issue body contains "Command crashes on invalid input"
  And I see the issue number and URL in the output

Scenario: Create feature request via feedback
  Given the project is configured with GitHub tracker
  And the repository is "iterative-works/iw-cli"
  And gh CLI is authenticated
  When I run 'iw feedback "Add completion support" --description "Would be nice to have shell completion"'
  Then a GitHub issue is created with label "feedback"
  And the issue title is "Add completion support"
  And I see a success message with the issue URL
```

## Implementation Notes

### gh CLI JSON Output

Use `--json` flag to get structured output:
```bash
$ gh issue create --repo owner/repo --title "Test" --body "Body" --json number,url
{
  "number": 123,
  "url": "https://github.com/owner/repo/issues/123"
}
```

### Label Handling Strategy

The `--label` flag adds labels. If the label doesn't exist:
- gh CLI will create the label automatically (with default color)
- OR fail with "label not found" error

**Decision:** Try with labels. If gh fails mentioning "label", retry without labels and warn user.

### Process Execution Pattern

Follow PullRequestCacheService pattern:
- Use function injection for testability: `execCommand: (String, Array[String]) => Either[String, String]`
- For production, use `scala.sys.process._`

Example from existing code:
```scala
import scala.sys.process._
val result = Process(Seq("gh", "issue", "create", ...)).!!
```

### Config Loading

Need to read config in feedback command. Pattern from other commands:

```scala
val config = ConfigSerializer.fromHocon(configContent) match
  case Left(error) =>
    Output.error(s"Failed to read config: $error")
    sys.exit(1)
  case Right(c) => c
```

---

## Refactoring Decisions

### R1: Hardcoded feedback target (2025-12-22)

**Trigger:** During review, clarified that the feedback command is for reporting issues **about iw-cli itself**, not for the user's current project. The command should always target the iw-cli repository regardless of what project the user is working in.

**Decision:** Remove config-dependent routing from feedback.scala. The feedback command should:
1. Always target the hardcoded `iterative-works/iw-cli` repository on GitHub
2. NOT depend on local `.iw/config.conf`
3. Work from any directory, whether or not it's an iw-cli project

**Scope:**
- Files affected: `.iw/commands/feedback.scala`, `.iw/test/feedback.bats`
- Components: Feedback command routing logic
- Boundaries: Keep GitHubClient.scala as-is (it's reusable for other purposes)

**Approach:**
- Remove config loading from feedback.scala
- Add `FEEDBACK_REPOSITORY` constant to Constants.scala
- Always use GitHubClient with the hardcoded repository
- Keep LinearClient code for backward compatibility (controlled by env var or remove entirely)
- Update E2E tests to not depend on local config

---

## Next Steps After Phase 3

- **Phase 4**: Better error messages for gh not installed / not authenticated
- **Phase 5**: `iw issue` command for GitHub
- **Phase 6**: Doctor checks for GitHub setup
