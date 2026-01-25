# Phase 5 Context: Linear issue creation

**Issue:** IW-103
**Phase:** 5 - Linear issue creation
**Story:** Story 2 - Create Linear issue with title and description

## User Story

```gherkin
Feature: Create issue in Linear workspace
  As a developer working in a Linear-tracked project
  I want to create issues via command line
  So that I can capture tasks and bugs immediately

Scenario: Successfully create Linear issue with title and description
  Given I am in a project configured for Linear tracker
  And LINEAR_API_TOKEN environment variable is set to valid token
  And team ID is configured as "abc123-team-uuid"
  When I run "./iw issue create --title 'Implement search' --description 'Add full-text search'"
  Then a new issue is created in Linear team
  And the issue has title "Implement search"
  And the issue has description "Add full-text search"
  And I see output "Issue created: TEAM-456"
  And I see output "URL: https://linear.app/workspace/issue/TEAM-456"
```

## Acceptance Criteria

- [ ] Command creates issue in configured Linear team
- [ ] Returns Linear issue ID (e.g., TEAM-456) and URL
- [ ] Validates API token before attempting creation
- [ ] Clear error message if token missing or invalid

## What Was Built in Previous Phases

### Phase 2: GitHub issue creation
- `IssueCreateParser` for parsing --title and --description
- `handleCreateSubcommand()` with GitHub support
- Pattern: check tracker type, validate prerequisites, call client, format output

### Available Infrastructure
- `LinearClient.createIssue(title, description, teamId, token)` - Already exists
- `ApiToken.fromEnv(envVar)` - For reading LINEAR_API_TOKEN
- `ProjectConfiguration.team` - Team ID from config
- `CreatedIssue` case class with `id` and `url` fields

## Technical Approach

1. In `handleCreateSubcommand()`, add `case IssueTrackerType.Linear` branch
2. Validate LINEAR_API_TOKEN environment variable is set
3. Get teamId from config.team
4. Call LinearClient.createIssue with parsed title/description
5. Format output: "Issue created: #ID" and "URL: url"

## Files to Modify

- `.iw/commands/issue.scala` - Add Linear tracker support
- `.iw/test/issue-create.bats` - Add E2E tests for Linear

## Phase Scope

- Add Linear support to existing `handleCreateSubcommand()`
- Add E2E tests with mocked Linear API (HTTP mock or stubbed responses)
- Follow same error handling pattern as GitHub

## Success Criteria

- [ ] E2E test: `iw issue create --title "Test" --description "Body"` succeeds (Linear tracker)
- [ ] E2E test: `iw issue create --title "Test"` works without description (Linear)
- [ ] E2E test: Missing LINEAR_API_TOKEN shows error message
- [ ] Output shows issue ID and URL after creation
