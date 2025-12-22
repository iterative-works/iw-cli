# Phase 3 Tasks: Create GitHub issue via feedback command

**Issue:** IWLE-132
**Phase:** 3 of 6
**Status:** Complete

---

## Setup

- [x] [impl] Read existing feedback.scala to understand current implementation
- [x] [impl] Read existing LinearClient.scala to understand client pattern
- [x] [impl] Read existing ProcessAdapter.scala for shell command execution pattern

## Tests - GitHubClient Unit Tests

- [x] [test] Test buildCreateIssueCommand generates correct gh CLI args for title only
- [x] [test] Test buildCreateIssueCommand generates correct args with description
- [x] [test] Test buildCreateIssueCommand generates correct args with bug label
- [x] [test] Test buildCreateIssueCommand generates correct args with feedback label
- [x] [test] Test buildCreateIssueCommand uses correct repository format
- [x] [test] Test parseCreateIssueResponse parses valid JSON with number and URL
- [x] [test] Test parseCreateIssueResponse returns error for missing number field
- [x] [test] Test parseCreateIssueResponse returns error for malformed JSON
- [x] [test] Test parseCreateIssueResponse returns error for empty response

## Implementation - GitHubClient.scala

- [x] [impl] Create GitHubClient.scala with basic structure
- [x] [impl] Implement buildCreateIssueCommand method
- [x] [impl] Implement parseCreateIssueResponse method
- [x] [impl] Implement createIssue method with shell command execution
- [x] [impl] Add label mapping (bug -> "bug", feature -> "feedback")
- [x] [impl] Handle label graceful fallback (retry without labels on failure)

## Tests - Feedback Command Integration Tests

- [x] [test] Test feedback with GitHub tracker routes to GitHubClient
- [x] [test] Test feedback with GitHub tracker reads repository from config
- [x] [test] Test feedback with GitHub tracker does not require LINEAR_API_TOKEN
- [x] [test] Test feedback with Linear tracker still uses LinearClient (regression)
- [x] [test] Test feedback with GitHub tracker shows issue number and URL on success
- [x] [test] Test feedback with GitHub tracker shows error when gh command fails

## Implementation - Feedback Command Changes

- [x] [impl] Add config loading to feedback.scala
- [x] [impl] Add tracker type detection from config
- [x] [impl] Route to GitHubClient when tracker is GitHub
- [x] [impl] Display issue number and URL for GitHub issues
- [x] [impl] Handle config missing repository error

## E2E Tests

- [x] [test] E2E: feedback with GitHub tracker creates issue (mock gh)
- [x] [test] E2E: feedback with GitHub tracker shows issue URL
- [x] [test] E2E: feedback with bug type applies bug label
- [x] [test] E2E: feedback with Linear tracker still works (regression)
- [x] [test] E2E: feedback with missing repository shows helpful error

## Integration

- [x] [impl] Run all unit tests and fix any failures
- [x] [impl] Run all E2E tests and fix any failures
- [x] [impl] Update phase-03-tasks.md with completion status

## Refactoring

- [x] [impl] Refactoring R1: Hardcoded feedback target

---

## Notes

- GitHubClient pattern should match LinearClient for consistency
- Use function injection for testability (execCommand parameter)
- gh CLI handles authentication - no API token needed
- Labels may not exist in repository - handle gracefully
