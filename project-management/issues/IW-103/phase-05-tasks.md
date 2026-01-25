# Phase 5 Tasks: Linear issue creation

**Issue:** IW-103
**Phase:** 5 - Linear issue creation
**Story:** Story 2 - Create Linear issue with title and description

## Tasks

### Setup
- [x] [impl] [x] [reviewed] Review LinearClient.createIssue signature and patterns
- [x] [impl] [x] [reviewed] Review feedback.scala for LINEAR_API_TOKEN validation pattern

### Tests (TDD - Write First)
- [x] [impl] [x] [reviewed] Create E2E test: Missing LINEAR_API_TOKEN shows error message
- [x] [impl] [x] [reviewed] Note: Success tests deferred - require HTTP mocking or real API

### Implementation
- [x] [impl] [x] [reviewed] Add Linear branch to handleCreateSubcommand tracker type match
- [x] [impl] [x] [reviewed] Validate LINEAR_API_TOKEN environment variable is set
- [x] [impl] [x] [reviewed] Get teamId from config.team
- [x] [impl] [x] [reviewed] Call LinearClient.createIssue with title, description, teamId, token
- [x] [impl] [x] [reviewed] Format output: "Issue created: #ID" and "URL: url"
- [x] [impl] [x] [reviewed] Handle error cases with appropriate error messages

### Integration
- [x] [impl] [x] [reviewed] Verify all E2E tests pass (13 total)
- [x] [impl] [x] [reviewed] Verify GitHub tests still pass (no regressions)

## Success Criteria

- [ ] All BATS E2E tests pass
- [ ] `iw issue create --title "X" --description "Y"` creates Linear issue
- [ ] `iw issue create --title "X"` works without description
- [ ] Output shows issue ID and URL after creation
- [ ] Missing LINEAR_API_TOKEN shows clear error
- [ ] No regressions in GitHub issue creation

**Phase Status:** Complete
