# Phase 7 Tasks: YouTrack issue creation

**Issue:** IW-103
**Phase:** 7 - YouTrack issue creation
**Story:** Story 4 - Create YouTrack issue with title and description

## Tasks

### Setup
- [x] [impl] [x] [reviewed] Review YouTrack REST API for issue creation
- [x] [impl] [x] [reviewed] Review LinearClient.createIssue pattern for consistency

### Tests (TDD - Write First)
- [x] [impl] [x] [reviewed] Create unit test: YouTrackClient.buildCreateIssueUrl returns correct URL
- [x] [impl] [x] [reviewed] Create unit test: YouTrackClient.buildCreateIssueBody generates valid JSON
- [x] [impl] [x] [reviewed] Create unit test: YouTrackClient.parseCreateIssueResponse parses success response
- [x] [impl] [x] [reviewed] Create unit test: YouTrackClient.parseCreateIssueResponse handles errors
- [x] [impl] [x] [reviewed] Create E2E test: Missing YOUTRACK_API_TOKEN shows error message
- [x] [impl] [x] [reviewed] Create E2E test: Missing baseUrl shows error message

### Implementation
- [x] [impl] [x] [reviewed] Add YouTrack branch to handleCreateSubcommand tracker type match
- [x] [impl] [x] [reviewed] Create createYouTrackIssue() helper function
- [x] [impl] [x] [reviewed] Implement YouTrackClient.buildCreateIssueUrl
- [x] [impl] [x] [reviewed] Implement YouTrackClient.buildCreateIssueBody
- [x] [impl] [x] [reviewed] Implement YouTrackClient.parseCreateIssueResponse
- [x] [impl] [x] [reviewed] Implement YouTrackClient.createIssue orchestrating above methods
- [x] [impl] [x] [reviewed] Validate YOUTRACK_API_TOKEN environment variable
- [x] [impl] [x] [reviewed] Get project ID from config.team field
- [x] [impl] [x] [reviewed] Get baseUrl from config.youtrackBaseUrl
- [x] [impl] [x] [reviewed] Build issue URL format: {baseUrl}/issue/{issueId}
- [x] [impl] [x] [reviewed] Format output: "Issue created: PROJ-234" and "URL: url"
- [x] [impl] [x] [reviewed] Handle error cases with appropriate error messages

### Integration
- [x] [impl] [x] [reviewed] Verify all E2E tests pass
- [x] [impl] [x] [reviewed] Verify GitHub, Linear, and GitLab tests still pass (no regressions)

## Success Criteria

- [x] All unit tests for YouTrackClient.createIssue pass
- [x] All BATS E2E tests pass
- [x] `iw issue create --title "X" --description "Y"` creates YouTrack issue
- [x] `iw issue create --title "X"` works without description
- [x] Output shows issue ID and URL after creation
- [x] Missing YOUTRACK_API_TOKEN shows clear error
- [x] Invalid token shows appropriate error
- [x] No regressions in GitHub/Linear/GitLab issue creation

**Phase Status:** Complete
