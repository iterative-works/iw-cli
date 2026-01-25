# Phase 3 Context: Prerequisite validation

**Issue:** IW-103
**Phase:** 3 - Prerequisite validation
**Story:** Story 5 - Handle missing prerequisites gracefully

## User Story

```gherkin
Feature: Validate prerequisites before creating issue
  As a developer
  I want clear error messages when prerequisites are missing
  So that I know how to fix configuration issues

Scenario: GitHub tracker with gh CLI not installed
  Given I am in a project configured for GitHub tracker
  And gh CLI is not installed
  When I run "./iw issue create --title 'Test issue'"
  Then I see error message "gh CLI is not installed"
  And I see installation instructions for gh CLI
  And the command exits with code 1

Scenario: GitHub tracker with gh not authenticated
  Given I am in a project configured for GitHub tracker
  And gh CLI is installed but not authenticated
  When I run "./iw issue create --title 'Test issue'"
  Then I see error message "gh is not authenticated"
  And I see authentication instructions
  And the command exits with code 1
```

## Acceptance Criteria

- [ ] Clear, actionable error messages for each prerequisite failure
- [ ] Matches error message format from existing `iw issue` and `iw feedback` commands
- [ ] No generic "command failed" messages - specific to the missing prerequisite
- [ ] E2E tests verify all error scenarios with mocked gh CLI

## What Was Built in Previous Phases

### Phase 1: Help display
- `handleCreateSubcommand()` with help display
- Basic E2E tests for help scenarios

### Phase 2: GitHub issue creation
- `IssueCreateParser` for argument parsing
- Full `handleCreateSubcommand()` implementation including:
  - `GitHubClient.validateGhPrerequisites()` integration
  - Error handling for GhNotInstalled, GhNotAuthenticated, GhOtherError
  - User-friendly error messages from `GitHubClient.formatGhNotInstalledError()` and `GitHubClient.formatGhNotAuthenticatedError()`
- E2E test for non-GitHub tracker showing "not yet supported" message

## Available Infrastructure

### From Phase 2
- `GitHubClient.validateGhPrerequisites(repository)` - Returns `Either[GhPrerequisiteError, Unit]`
- `GitHubClient.GhPrerequisiteError` enum:
  - `GhNotInstalled`
  - `GhNotAuthenticated`
  - `GhOtherError(msg: String)`
- `GitHubClient.formatGhNotInstalledError()` - User-friendly error with install instructions
- `GitHubClient.formatGhNotAuthenticatedError()` - User-friendly error with auth instructions

### Existing in feedback.bats
- E2E tests for prerequisite validation (can use as pattern):
  - `feedback fails with helpful message when gh CLI not installed`
  - `feedback fails with auth instructions when gh not authenticated`
  - `feedback fails when repository not accessible`

## Technical Approach

Phase 2 already implemented the prerequisite validation LOGIC. Phase 3 adds comprehensive E2E tests to verify:

1. **gh CLI not installed scenario**: Mock `which` command to return false for gh
2. **gh not authenticated scenario**: Mock gh to return exit code 4 for `auth status`
3. **Verify error messages contain**: Installation URL, authentication instructions, specific error text

Tests should follow the patterns established in `feedback.bats` for consistency.

## Phase Scope

This phase is primarily about TESTING, not new implementation:
- Add E2E tests to `.iw/test/issue-create.bats`
- Verify existing prerequisite validation logic works correctly
- Ensure error messages are user-friendly and actionable

## Success Criteria

- [ ] E2E test: `issue create` fails with helpful message when gh CLI not installed
- [ ] E2E test: `issue create` fails with auth instructions when gh not authenticated
- [ ] Error messages contain installation/auth instructions (URLs, commands)
- [ ] All existing tests continue to pass
