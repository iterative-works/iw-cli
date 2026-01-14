# Phase 5 Tasks: Recent issues - YouTrack

**Issue:** IW-88
**Phase:** 5 of 7
**Story:** View recent issues on modal open (YouTrack)

---

## Setup

- [x] [setup] Create/extend YouTrackClientTest.scala test file

## Pure Functions (TDD)

### URL Building

- [x] [test] Write test: `buildListRecentIssuesUrl` returns correct URL with default limit (5)
- [x] [impl] Implement `buildListRecentIssuesUrl(baseUrl, limit)` in YouTrackClient
- [x] [test] Write test: `buildListRecentIssuesUrl` encodes spaces in orderBy parameter

### Response Parsing

- [x] [test] Write test: `parseListRecentIssuesResponse` parses valid JSON array with multiple issues
- [x] [impl] Implement `parseListRecentIssuesResponse(json)` in YouTrackClient
- [x] [test] Write test: `parseListRecentIssuesResponse` returns empty list for empty JSON array
- [x] [test] Write test: `parseListRecentIssuesResponse` extracts State from customFields
- [x] [test] Write test: `parseListRecentIssuesResponse` returns "Unknown" for missing State
- [x] [test] Write test: `parseListRecentIssuesResponse` returns error for malformed JSON

## Effectful Functions (TDD)

### HTTP Integration

- [x] [test] Write test: `listRecentIssues` returns issues on successful HTTP response
- [x] [impl] Implement `listRecentIssues(baseUrl, limit, token)` in YouTrackClient
- [x] [test] Write test: `listRecentIssues` returns error on 401 Unauthorized
- [x] [test] Write test: `listRecentIssues` returns error on network failure

## Integration

- [x] [impl] Update CaskServer `buildFetchRecentFunction` to route YouTrack to `listRecentIssues`

## Verification

- [x] [verify] Run all tests: `./iw test unit`
- [ ] [verify] Manual test with real YouTrack (if available)

---

## Task Count: 16 tasks

**TDD Pattern:**
1. Write failing test
2. Run test to confirm failure
3. Implement minimal code to pass
4. Run test to confirm success
5. Refactor if needed
