# Phase 2 Tasks: GitHub issue creation

**Issue:** IW-103
**Phase:** 2 - GitHub issue creation
**Story:** Story 1 - Create GitHub issue with title and description

## Tasks

### Setup
- [ ] [impl] [ ] [reviewed] Review GitHubClient.createIssue patterns and reusable components

### Tests (TDD - Write First)
- [ ] [impl] [ ] [reviewed] Create E2E test: `iw issue create --title "Test" --description "Body"` succeeds (GitHub tracker)
- [ ] [impl] [ ] [reviewed] Create E2E test: `iw issue create --title "Test"` works without description
- [ ] [impl] [ ] [reviewed] Create E2E test: `iw issue create` without --title shows help and exits 1
- [ ] [impl] [ ] [reviewed] Create E2E test: Success output contains "Issue created:" and URL
- [ ] [impl] [ ] [reviewed] Create unit test: IssueCreateParser parses --title correctly
- [ ] [impl] [ ] [reviewed] Create unit test: IssueCreateParser parses --title and --description
- [ ] [impl] [ ] [reviewed] Create unit test: IssueCreateParser returns error when --title missing

### Implementation
- [ ] [impl] [ ] [reviewed] Create IssueCreateParser with parse(args) method returning Either[String, IssueCreateRequest]
- [ ] [impl] [ ] [reviewed] Define IssueCreateRequest case class with title and description fields
- [ ] [impl] [ ] [reviewed] Implement handleCreateSubcommand: parse arguments using IssueCreateParser
- [ ] [impl] [ ] [reviewed] Implement handleCreateSubcommand: load configuration using loadConfig()
- [ ] [impl] [ ] [reviewed] Implement handleCreateSubcommand: validate tracker type is GitHub
- [ ] [impl] [ ] [reviewed] Implement handleCreateSubcommand: validate gh prerequisites
- [ ] [impl] [ ] [reviewed] Implement handleCreateSubcommand: call GitHubClient to create issue
- [ ] [impl] [ ] [reviewed] Implement handleCreateSubcommand: output success message with issue number and URL
- [ ] [impl] [ ] [reviewed] Handle error cases with appropriate error messages

### Integration
- [ ] [impl] [ ] [reviewed] Verify all E2E tests pass with real GitHub repository
- [ ] [impl] [ ] [reviewed] Manual smoke test: create real issue in test repository
- [ ] [impl] [ ] [reviewed] Verify existing `iw issue` fetch command still works

## Success Criteria

- [ ] All BATS E2E tests pass
- [ ] Unit tests for IssueCreateParser pass
- [ ] `iw issue create --title "X" --description "Y"` creates GitHub issue
- [ ] `iw issue create --title "X"` works without description
- [ ] Output shows issue number and URL after creation
- [ ] Missing --title shows help and exits 1
- [ ] Non-GitHub trackers show "not yet supported" message
- [ ] No regressions in existing commands

**Phase Status:** Not Started
