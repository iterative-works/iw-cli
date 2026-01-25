# Phase 5 Tasks: Linear issue creation

**Issue:** IW-103
**Phase:** 5 - Linear issue creation
**Story:** Story 2 - Create Linear issue with title and description

## Tasks

### Setup
- [ ] [impl] [ ] [reviewed] Review LinearClient.createIssue signature and patterns
- [ ] [impl] [ ] [reviewed] Review feedback.scala for LINEAR_API_TOKEN validation pattern

### Tests (TDD - Write First)
- [ ] [impl] [ ] [reviewed] Create E2E test: `iw issue create --title "Test" --description "Body"` succeeds (Linear tracker)
- [ ] [impl] [ ] [reviewed] Create E2E test: `iw issue create --title "Test"` works without description (Linear)
- [ ] [impl] [ ] [reviewed] Create E2E test: Missing LINEAR_API_TOKEN shows error message
- [ ] [impl] [ ] [reviewed] Create E2E test: Success output contains issue ID and URL

### Implementation
- [ ] [impl] [ ] [reviewed] Add Linear branch to handleCreateSubcommand tracker type match
- [ ] [impl] [ ] [reviewed] Validate LINEAR_API_TOKEN environment variable is set
- [ ] [impl] [ ] [reviewed] Get teamId from config.team
- [ ] [impl] [ ] [reviewed] Call LinearClient.createIssue with title, description, teamId, token
- [ ] [impl] [ ] [reviewed] Format output: "Issue created: #ID" and "URL: url"
- [ ] [impl] [ ] [reviewed] Handle error cases with appropriate error messages

### Integration
- [ ] [impl] [ ] [reviewed] Verify all E2E tests pass
- [ ] [impl] [ ] [reviewed] Verify GitHub tests still pass (no regressions)

## Success Criteria

- [ ] All BATS E2E tests pass
- [ ] `iw issue create --title "X" --description "Y"` creates Linear issue
- [ ] `iw issue create --title "X"` works without description
- [ ] Output shows issue ID and URL after creation
- [ ] Missing LINEAR_API_TOKEN shows clear error
- [ ] No regressions in GitHub issue creation

**Phase Status:** Not Started
