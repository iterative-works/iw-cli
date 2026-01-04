# Phase 01 Tasks: Fetch and display GitLab issue via glab CLI

**Issue:** IW-90
**Phase:** 1 of 7
**Status:** Complete

## Setup Tasks

- [x] [setup] Add GitLab variant to IssueTrackerType enum in Config.scala

## Test Tasks

- [x] [test] Write GitLabClient.buildFetchIssueCommand unit test for simple repository
- [x] [test] Write GitLabClient.buildFetchIssueCommand unit test for nested groups repository
- [x] [test] Write GitLabClient.parseFetchIssueResponse unit test for valid opened issue JSON
- [x] [test] Write GitLabClient.parseFetchIssueResponse unit test for closed issue JSON
- [x] [test] Write GitLabClient.parseFetchIssueResponse unit test for missing optional fields
- [x] [test] Write GitLabClient.parseFetchIssueResponse unit test for malformed JSON
- [x] [test] Write GitLabClient.validateGlabPrerequisites unit test for glab not installed
- [x] [test] Write GitLabClient.validateGlabPrerequisites unit test for glab authenticated

## Implementation Tasks

- [x] [impl] Create GitLabClient.scala module skeleton with GlabPrerequisiteError enum
- [x] [impl] Implement GitLabClient.buildFetchIssueCommand function
- [x] [impl] Implement GitLabClient.parseFetchIssueResponse function with JSON parsing
- [x] [impl] Implement GitLabClient.validateGlabPrerequisites function
- [x] [impl] Implement GitLabClient.fetchIssue orchestration function
- [x] [impl] Add GitLab case to issue.scala fetchIssue pattern match

## Integration Tasks

- [x] [integration] Verify all unit tests pass
- [ ] [integration] Manual test: fetch GitLab issue with configured tracker

## Notes

- Follow TDD: write each test, verify it fails, implement to make it pass
- Use GitHubClient pattern as template for GitLabClient
- glab CLI command format: `glab issue view <id> --repo <owner/project> --output json`
- Use `iid` field from JSON (not `id`) - this is the project-scoped issue number
- State mapping: "opened" → Open, "closed" → Closed
