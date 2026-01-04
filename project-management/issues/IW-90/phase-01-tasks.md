# Phase 01 Tasks: Fetch and display GitLab issue via glab CLI

**Issue:** IW-90
**Phase:** 1 of 7
**Status:** Not Started

## Setup Tasks

- [ ] [setup] Add GitLab variant to IssueTrackerType enum in Config.scala

## Test Tasks

- [ ] [test] Write GitLabClient.buildFetchIssueCommand unit test for simple repository
- [ ] [test] Write GitLabClient.buildFetchIssueCommand unit test for nested groups repository
- [ ] [test] Write GitLabClient.parseFetchIssueResponse unit test for valid opened issue JSON
- [ ] [test] Write GitLabClient.parseFetchIssueResponse unit test for closed issue JSON
- [ ] [test] Write GitLabClient.parseFetchIssueResponse unit test for missing optional fields
- [ ] [test] Write GitLabClient.parseFetchIssueResponse unit test for malformed JSON
- [ ] [test] Write GitLabClient.validateGlabPrerequisites unit test for glab not installed
- [ ] [test] Write GitLabClient.validateGlabPrerequisites unit test for glab authenticated

## Implementation Tasks

- [ ] [impl] Create GitLabClient.scala module skeleton with GlabPrerequisiteError enum
- [ ] [impl] Implement GitLabClient.buildFetchIssueCommand function
- [ ] [impl] Implement GitLabClient.parseFetchIssueResponse function with JSON parsing
- [ ] [impl] Implement GitLabClient.validateGlabPrerequisites function
- [ ] [impl] Implement GitLabClient.fetchIssue orchestration function
- [ ] [impl] Add GitLab case to issue.scala fetchIssue pattern match

## Integration Tasks

- [ ] [integration] Verify all unit tests pass
- [ ] [integration] Manual test: fetch GitLab issue with configured tracker

## Notes

- Follow TDD: write each test, verify it fails, implement to make it pass
- Use GitHubClient pattern as template for GitLabClient
- glab CLI command format: `glab issue view <id> --repo <owner/project> --output json`
- Use `iid` field from JSON (not `id`) - this is the project-scoped issue number
- State mapping: "opened" → Open, "closed" → Closed
