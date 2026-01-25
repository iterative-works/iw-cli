# Phase 6 Tasks: GitLab issue creation

**Issue:** IW-103
**Phase:** 6 - GitLab issue creation
**Story:** Story 3 - Create GitLab issue with title and description

## Tasks

### Setup
- [x] [impl] [x] [reviewed] Review GitLabClient methods (validateGlabPrerequisites, buildCreateIssueCommandWithoutLabel, parseCreateIssueResponse)
- [x] [impl] [x] [reviewed] Review createGitHubIssue() pattern for consistency

### Tests (TDD - Write First)
- [x] [impl] [x] [reviewed] Create E2E test: Missing glab CLI shows error message
- [x] [impl] [x] [reviewed] Create E2E test: Unauthenticated glab shows error message
- [x] [impl] [x] [reviewed] Create E2E test: `iw issue create --title "Test"` succeeds (GitLab tracker)

### Implementation
- [x] [impl] [x] [reviewed] Add GitLab branch to handleCreateSubcommand tracker type match
- [x] [impl] [x] [reviewed] Create createGitLabIssue() helper function
- [x] [impl] [x] [reviewed] Validate glab CLI prerequisites (installed, authenticated)
- [x] [impl] [x] [reviewed] Get repository from config.repository
- [x] [impl] [x] [reviewed] Build and execute glab command via CommandRunner
- [x] [impl] [x] [reviewed] Parse response with GitLabClient.parseCreateIssueResponse
- [x] [impl] [x] [reviewed] Format output: "Issue created: #ID" and "URL: url"
- [x] [impl] [x] [reviewed] Handle error cases with appropriate error messages

### Integration
- [x] [impl] [x] [reviewed] Verify all E2E tests pass
- [x] [impl] [x] [reviewed] Verify GitHub and Linear tests still pass (no regressions)

## Success Criteria

- [ ] All BATS E2E tests pass
- [ ] `iw issue create --title "X" --description "Y"` creates GitLab issue
- [ ] `iw issue create --title "X"` works without description
- [ ] Output shows issue number and URL after creation
- [ ] Missing glab CLI shows clear error
- [ ] Unauthenticated glab shows clear error
- [ ] No regressions in GitHub/Linear issue creation

**Phase Status:** Complete
