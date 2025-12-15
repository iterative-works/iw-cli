# Phase 7 Tasks: Fetch and display issue details

**Issue:** IWLE-72
**Phase:** 7 of 7
**Status:** Complete

---

## Setup

- [x] [setup] Review existing LinearClient.scala to understand current API patterns
- [x] [setup] Review IssueId.scala for issue ID parsing/validation

## Domain Layer

- [x] [test] [x] [reviewed] Write tests for Issue entity construction
- [x] [impl] [x] [reviewed] Create Issue.scala with Issue entity and IssueTracker trait

## Linear Integration

- [x] [test] [x] [reviewed] Write unit tests for Linear GraphQL query construction
- [x] [test] [x] [reviewed] Write unit tests for Linear response parsing
- [x] [impl] [x] [reviewed] Add fetchIssue method to LinearClient using GraphQL

## YouTrack Integration

- [x] [test] [x] [reviewed] Write unit tests for YouTrack REST request construction
- [x] [test] [x] [reviewed] Write unit tests for YouTrack response parsing (including customFields)
- [x] [impl] [x] [reviewed] Create YouTrackClient.scala with REST API client

## Issue Command

- [x] [impl] [x] [reviewed] Implement issue.scala command with full workflow

## Output Formatting

- [x] [test] [x] [reviewed] Write tests for issue display formatting
- [x] [impl] [x] [reviewed] Implement formatted issue output with Unicode rendering

## E2E Tests

- [x] [test] [x] [reviewed] Write E2E test for invalid issue ID format error
- [x] [test] [x] [reviewed] Write E2E test for missing config error
- [x] [test] [x] [reviewed] Write E2E test for missing API token error
- [x] [test] [x] [reviewed] Write E2E test for branch inference

## Integration Verification

- [x] [verify] Manual test with real Linear issue (IWLE-72)
- [x] [verify] Run full test suite
