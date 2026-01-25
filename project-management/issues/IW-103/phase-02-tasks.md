# Phase 2 Tasks: GitHub issue creation

**Issue:** IW-103
**Phase:** 2 - GitHub issue creation
**Story:** Story 1 - Create GitHub issue with title and description

## Tasks

### Setup
- [x] [impl] [x] [reviewed] Review GitHubClient.createIssue patterns and reusable components

### Tests (TDD - Write First)
- [x] [impl] [x] [reviewed] Create E2E test: `iw issue create --title "Test" --description "Body"` succeeds (GitHub tracker)
- [x] [impl] [x] [reviewed] Create E2E test: `iw issue create --title "Test"` works without description
- [x] [impl] [x] [reviewed] Create E2E test: `iw issue create` without --title shows help and exits 1
- [x] [impl] [x] [reviewed] Create E2E test: Success output contains "Issue created:" and URL
- [x] [impl] [x] [reviewed] Create unit test: IssueCreateParser parses --title correctly
- [x] [impl] [x] [reviewed] Create unit test: IssueCreateParser parses --title and --description
- [x] [impl] [x] [reviewed] Create unit test: IssueCreateParser returns error when --title missing

### Implementation
- [x] [impl] [x] [reviewed] Create IssueCreateParser with parse(args) method returning Either[String, IssueCreateRequest]
- [x] [impl] [x] [reviewed] Define IssueCreateRequest case class with title and description fields
- [x] [impl] [x] [reviewed] Implement handleCreateSubcommand: parse arguments using IssueCreateParser
- [x] [impl] [x] [reviewed] Implement handleCreateSubcommand: load configuration using loadConfig()
- [x] [impl] [x] [reviewed] Implement handleCreateSubcommand: validate tracker type is GitHub
- [x] [impl] [x] [reviewed] Implement handleCreateSubcommand: validate gh prerequisites
- [x] [impl] [x] [reviewed] Implement handleCreateSubcommand: call GitHubClient to create issue
- [x] [impl] [x] [reviewed] Implement handleCreateSubcommand: output success message with issue number and URL
- [x] [impl] [x] [reviewed] Handle error cases with appropriate error messages

### Integration
- [x] [impl] [x] [reviewed] Verify all E2E tests pass with real GitHub repository
- [x] [impl] [x] [reviewed] Manual smoke test: create real issue in test repository
- [x] [impl] [x] [reviewed] Verify existing `iw issue` fetch command still works

## Success Criteria

- [ ] All BATS E2E tests pass
- [ ] Unit tests for IssueCreateParser pass
- [ ] `iw issue create --title "X" --description "Y"` creates GitHub issue
- [ ] `iw issue create --title "X"` works without description
- [ ] Output shows issue number and URL after creation
- [ ] Missing --title shows help and exits 1
- [ ] Non-GitHub trackers show "not yet supported" message
- [ ] No regressions in existing commands

**Phase Status:** Complete ✓
