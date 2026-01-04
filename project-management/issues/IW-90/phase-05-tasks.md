# Phase 5 Tasks: Create GitLab issues via glab CLI

**Issue:** IW-90
**Phase:** 5 of 7
**Story:** Create GitLab issues via glab CLI

---

## Setup

- [ ] [setup] Verify existing `GitLabClient` has `validateGlabPrerequisites`, `formatGlabNotInstalledError`, and `formatGlabNotAuthenticatedError` functions from Phase 1-2
- [ ] [setup] Verify `FeedbackParser` module exists and has `IssueType` enum (Bug, Feature)
- [ ] [setup] Verify `CreatedIssue` domain model exists with `id` and `url` fields

## Tests - Command Building (Unit)

- [ ] [test] Write test in `GitLabClientTest.scala` for `buildCreateIssueCommand` with Bug type
  - Input: repository="my-org/my-project", title="Login broken", description="Details", issueType=Bug
  - Expected: Array("issue", "create", "--repo", "my-org/my-project", "--title", "Login broken", "--description", "Details", "--label", "bug")

- [ ] [test] Write test in `GitLabClientTest.scala` for `buildCreateIssueCommand` with Feature type
  - Input: repository="owner/repo", title="Add dark mode", description="Please add dark mode", issueType=Feature
  - Expected: Array("issue", "create", "--repo", "owner/repo", "--title", "Add dark mode", "--description", "Please add dark mode", "--label", "feature")

- [ ] [test] Write test in `GitLabClientTest.scala` for `buildCreateIssueCommand` with empty description
  - Input: description="" should produce "--description", "" (not omitted)
  - Ensures non-interactive mode works correctly

- [ ] [test] Write test in `GitLabClientTest.scala` for `buildCreateIssueCommandWithoutLabel` (fallback)
  - Input: repository="my-org/project", title="Issue", description="Details"
  - Expected: Array without --label flag but with all other flags

## Tests - Response Parsing (Unit)

- [ ] [test] Write test in `GitLabClientTest.scala` for `parseCreateIssueResponse` with gitlab.com URL
  - Input: "https://gitlab.com/owner/project/-/issues/123\n"
  - Expected: Right(CreatedIssue("123", "https://gitlab.com/owner/project/-/issues/123"))

- [ ] [test] Write test in `GitLabClientTest.scala` for `parseCreateIssueResponse` with self-hosted GitLab URL
  - Input: "https://gitlab.company.com/team/app/-/issues/456"
  - Expected: Right(CreatedIssue("456", "https://gitlab.company.com/team/app/-/issues/456"))

- [ ] [test] Write test in `GitLabClientTest.scala` for `parseCreateIssueResponse` with empty response
  - Input: ""
  - Expected: Left("Empty response from glab CLI")

- [ ] [test] Write test in `GitLabClientTest.scala` for `parseCreateIssueResponse` with invalid URL format
  - Input: "https://gitlab.com/not-an-issue-url"
  - Expected: Left("Unexpected response format: ...")

## Tests - Label Error Detection (Unit)

- [ ] [test] Write test in `GitLabClientTest.scala` for `isLabelError` detecting "label not found"
  - Input: "Error: label 'bug' not found in project"
  - Expected: true

- [ ] [test] Write test in `GitLabClientTest.scala` for `isLabelError` detecting "does not exist"
  - Input: "label does not exist"
  - Expected: true

- [ ] [test] Write test in `GitLabClientTest.scala` for `isLabelError` returning false for other errors
  - Input: "Network timeout"
  - Expected: false

## Tests - Integration (Mocked)

- [ ] [test] Write test in `GitLabClientTest.scala` for `createIssue` success path with label
  - Mock `execCommand` to return success with URL
  - Verify `validateGlabPrerequisites` called
  - Verify `buildCreateIssueCommand` result used
  - Verify `parseCreateIssueResponse` returns CreatedIssue

- [ ] [test] Write test in `GitLabClientTest.scala` for `createIssue` label error fallback
  - Mock first `execCommand` call to return label error
  - Mock second `execCommand` call to return success (without label)
  - Verify retry happens and succeeds

- [ ] [test] Write test in `GitLabClientTest.scala` for `createIssue` prerequisite failure (glab not installed)
  - Mock `isCommandAvailable("glab")` to return false
  - Expected: Left(formatGlabNotInstalledError())

- [ ] [test] Write test in `GitLabClientTest.scala` for `createIssue` prerequisite failure (glab not authenticated)
  - Mock `execCommand("glab", Array("auth", "status"))` to return Left with auth error
  - Expected: Left(formatGlabNotAuthenticatedError())

- [ ] [test] Write test in `GitLabClientTest.scala` for `createIssue` non-label error (should not retry)
  - Mock `execCommand` to return network error
  - Verify only one attempt (no retry without label)
  - Expected: Left with error message

## Implementation - Command Building

- [ ] [impl] Add `buildCreateIssueCommand` function to `GitLabClient.scala`
  - Map IssueType.Bug → "bug" label, IssueType.Feature → "feature" label
  - Build array: ["issue", "create", "--repo", repository, "--title", title, "--description", description, "--label", label]
  - Note: glab uses `--description` (not `--body` like gh)

- [ ] [impl] Add `buildCreateIssueCommandWithoutLabel` function to `GitLabClient.scala`
  - Same as `buildCreateIssueCommand` but without `--label` flag
  - Used for fallback when labels don't exist in project

## Implementation - Response Parsing

- [ ] [impl] Add `parseCreateIssueResponse` function to `GitLabClient.scala`
  - Extract issue number from GitLab URL pattern: `.*/-/issues/(\d+)$`
  - Handle both gitlab.com and self-hosted URLs
  - Return Right(CreatedIssue(number, url)) on success
  - Return Left("Empty response from glab CLI") if empty
  - Return Left("Unexpected response format: ...") if URL doesn't match

## Implementation - Label Error Detection

- [ ] [impl] Add `isLabelError` private function to `GitLabClient.scala`
  - Check if error.toLowerCase contains "label" AND ("not found" OR "does not exist" OR "invalid")
  - Used to trigger retry without label

## Implementation - Main Entry Point

- [ ] [impl] Add `createIssue` function to `GitLabClient.scala`
  - Signature: `def createIssue(repository: String, title: String, description: String, issueType: FeedbackParser.IssueType, isCommandAvailable: String => Boolean = ..., execCommand: (String, Array[String]) => Either[String, String] = ...): Either[String, CreatedIssue]`
  - Step 1: Call `validateGlabPrerequisites` and return formatted errors if fails
  - Step 2: Build command with `buildCreateIssueCommand`
  - Step 3: Execute `glab issue create` via `execCommand`
  - Step 4: If label error, retry with `buildCreateIssueCommandWithoutLabel`
  - Step 5: Parse response with `parseCreateIssueResponse`

## Integration - feedback.scala Command

- [ ] [impl] Update `feedback.scala` to read `.iw/config.conf` and check tracker type
  - Import `Config` module
  - Call `Config.load(".iw/config.conf")` before creating issue
  - If Right(config) and config.trackerType == Some("gitlab"), use GitLabClient
  - Otherwise, use existing GitHubClient behavior (backward compatible)

- [ ] [impl] Add GitLab issue creation logic to `feedback.scala`
  - Extract repository from config.gitlabRepository (or config.repository if unified)
  - Call `GitLabClient.createIssue(repository, request.title, request.description, request.issueType)`
  - Handle result same as GitHub (success/error output)

## Tests - E2E

- [ ] [test] Add E2E test in `.iw/test/feedback.bats` for GitLab issue creation
  - Setup: Create temp directory with `.iw/config.conf` configured for GitLab
  - Config: tracker.type = gitlab, tracker.repository = "test-org/test-project"
  - Run: `iw feedback "E2E test issue" --type bug`
  - Verify: Output contains GitLab issue URL (https://gitlab.com/.../issues/...)
  - Skip: Test skipped if `glab` not available in PATH

- [ ] [test] Add E2E test in `.iw/test/feedback.bats` for GitLab prerequisite errors
  - Test glab not authenticated error message
  - Verify error contains "glab auth login" instructions

## Verification and Review

- [ ] [verify] Run all unit tests: `scala-cli test .iw/core/test/GitLabClientTest.scala`
  - All tests must pass
  - No compilation warnings

- [ ] [verify] Run E2E tests: `bats .iw/test/feedback.bats`
  - GitLab tests pass (or skip if glab unavailable)
  - Existing GitHub feedback tests still pass (regression check)

- [ ] [verify] Manual smoke test with real GitLab repository
  - Configure GitLab tracker in test project
  - Run `iw feedback "Manual test" --type bug`
  - Verify issue created in GitLab with correct label
  - Verify URL returned correctly

- [ ] [review] Code review checklist
  - All functions follow GitHubClient pattern exactly
  - Error messages are user-friendly with clear remediation steps
  - Tests cover happy path, error cases, and edge cases
  - No code duplication between GitLabClient and GitHubClient
  - Documentation comments updated (if needed)

---

## Implementation Notes

**Key Differences from GitHub (`gh` vs `glab`):**
- Description flag: `gh` uses `--body`, `glab` uses `--description`
- URL format: GitHub uses `/issues/123`, GitLab uses `/-/issues/123`

**Reuse from Previous Phases:**
- `validateGlabPrerequisites` (Phase 1)
- `formatGlabNotInstalledError` (Phase 2)
- `formatGlabNotAuthenticatedError` (Phase 2)

**Label Fallback Strategy:**
Same as GitHubClient - if label assignment fails, retry without labels to ensure issue creation succeeds.

**Config Reading in feedback.scala:**
```scala
Config.load(".iw/config.conf") match
  case Right(config) if config.trackerType == Some("gitlab") =>
    GitLabClient.createIssue(config.repository, ...)
  case _ =>
    GitHubClient.createIssue(Constants.Feedback.Repository, ...)
```

---

**Total Tasks:** 35
**Estimated Time:** 4-6 hours

**Next Action:** Start with Setup tasks, then proceed with TDD cycle (Tests → Implementation → Verification)
