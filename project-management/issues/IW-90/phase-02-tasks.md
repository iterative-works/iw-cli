# Phase 02 Tasks: Handle GitLab-specific error conditions gracefully

**Issue:** IW-90
**Phase:** 2 of 7
**Status:** Complete

## Test Tasks

- [x] [test] Write test for formatGlabNotInstalledError returns installation instructions
- [x] [test] Write test for formatGlabNotAuthenticatedError returns auth login instructions
- [x] [test] Write test for formatIssueNotFoundError includes issue ID and repository
- [x] [test] Write test for formatNetworkError includes details and suggestions
- [x] [test] Write test for isAuthenticationError detects 401 and unauthorized strings
- [x] [test] Write test for isNotFoundError detects 404 and not found strings
- [x] [test] Write test for isNetworkError detects network-related strings
- [x] [test] Write negative tests for error detection functions (non-matching strings)

## Implementation Tasks

- [x] [impl] Add formatGlabNotInstalledError function to GitLabClient
- [x] [impl] Add formatGlabNotAuthenticatedError function to GitLabClient
- [x] [impl] Add formatIssueNotFoundError function to GitLabClient
- [x] [impl] Add formatNetworkError function to GitLabClient
- [x] [impl] Add isAuthenticationError function to GitLabClient
- [x] [impl] Add isNotFoundError function to GitLabClient
- [x] [impl] Add isNetworkError function to GitLabClient
- [x] [impl] Enhance issue.scala GitLab case to use error formatting functions

## Integration Tasks

- [x] [integration] Verify all unit tests pass
- [ ] [integration] Manual test: error message when glab not installed
- [ ] [integration] Manual test: error message when glab not authenticated
- [ ] [integration] Manual test: error message for non-existent issue

## Notes

- Follow TDD: write each test, verify it fails, implement to make it pass
- Error messages should be multi-line with clear instructions
- Use pattern matching in issue.scala for clean error handling
- Keep error detection simple - string matching is sufficient
