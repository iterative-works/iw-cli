# Phase 6 Context: GitLab issue creation

**Issue:** IW-103
**Phase:** 6 - GitLab issue creation
**Story:** Story 3 - Create GitLab issue with title and description

## User Story

```gherkin
Feature: Create issue in GitLab repository
  As a developer working in a GitLab-tracked project
  I want to create issues via command line
  So that I can log work without opening browser

Scenario: Successfully create GitLab issue with title and description
  Given I am in a project configured for GitLab tracker
  And glab CLI is installed and authenticated
  And repository is set to "company/platform/api-service"
  When I run "./iw issue create --title 'API rate limiting' --description 'Implement rate limiting for public API'"
  Then a new issue is created in GitLab repository
  And the issue has title "API rate limiting"
  And the issue has description "Implement rate limiting for public API"
  And I see output "Issue created: #789"
  And I see output "URL: https://gitlab.com/company/platform/api-service/-/issues/789"
```

## Acceptance Criteria

- [ ] Command creates issue in configured GitLab repository
- [ ] Returns issue number and URL
- [ ] Works with nested repository paths (company/platform/api-service)
- [ ] Validates glab CLI installed and authenticated

## What Was Built in Previous Phases

### Phase 2: GitHub issue creation
- `IssueCreateParser` for parsing --title and --description
- `handleCreateSubcommand()` with tracker type dispatch
- Pattern: check tracker type, validate prerequisites, call client, format output
- Helper: `createGitHubIssue()` function

### Phase 5: Linear issue creation
- Added `IssueTrackerType.Linear` branch to tracker type match
- Helper: `createLinearIssue()` function
- Pattern for API token validation via `ApiToken.fromEnv()`

### Available Infrastructure
- `GitLabClient.validateGlabPrerequisites(repository)` - Validates glab CLI prerequisites
- `GitLabClient.buildCreateIssueCommandWithoutLabel(repository, title, description)` - Builds glab command
- `GitLabClient.parseCreateIssueResponse(output)` - Parses response to CreatedIssue
- `CreatedIssue` case class with `id` and `url` fields
- `CommandRunner.execute(cmd, args)` - Executes CLI commands

### GitLabClient Error Types
- `GlabPrerequisiteError.GlabNotInstalled` - glab CLI not found
- `GlabPrerequisiteError.GlabNotAuthenticated` - glab not logged in
- `GlabPrerequisiteError.GlabError(msg)` - Other glab errors

## Technical Approach

1. In `handleCreateSubcommand()`, add `case IssueTrackerType.GitLab` branch
2. Create `createGitLabIssue()` helper function following GitHub pattern
3. Validate glab CLI prerequisites using `GitLabClient.validateGlabPrerequisites()`
4. Get repository from `config.repository`
5. Call `GitLabClient.buildCreateIssueCommandWithoutLabel()` and execute via CommandRunner
6. Parse response with `GitLabClient.parseCreateIssueResponse()`
7. Format output: "Issue created: #ID" and "URL: url"

## Files to Modify

- `.iw/commands/issue.scala` - Add GitLab tracker support
- `.iw/test/issue-create.bats` - Add E2E tests for GitLab

## Phase Scope

- Add GitLab support to existing `handleCreateSubcommand()`
- Add E2E tests with mocked glab CLI
- Follow same error handling pattern as GitHub

## Success Criteria

- [ ] E2E test: `iw issue create --title "Test" --description "Body"` succeeds (GitLab tracker)
- [ ] E2E test: `iw issue create --title "Test"` works without description (GitLab)
- [ ] E2E test: Missing glab CLI shows error message
- [ ] E2E test: Unauthenticated glab shows error message
- [ ] Output shows issue number and URL after creation
