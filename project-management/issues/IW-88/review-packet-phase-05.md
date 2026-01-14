# Review Packet: Phase 5 - Recent issues - YouTrack

**Issue:** IW-88
**Phase:** 5 of 7
**Branch:** IW-88-phase-05
**Date:** 2026-01-14

---

## Goals

Add `listRecentIssues` functionality to YouTrackClient following the established pattern from GitHub (Phase 1) and Linear (Phase 3). When the Create Worktree modal opens in a project configured with YouTrack, it should show the 5 most recent open issues.

---

## Scenarios

- [x] YouTrack issues are fetched when modal opens for YouTrack-configured project
- [x] URL is built correctly with proper field selection and ordering
- [x] JSON array response is parsed correctly
- [x] State is extracted from customFields
- [x] Missing State defaults to "Unknown"
- [x] Empty list returned on errors (graceful degradation)
- [x] 401 Unauthorized returns appropriate error message

---

## Entry Points

| File | Function | Description |
|------|----------|-------------|
| `.iw/core/YouTrackClient.scala:81` | `buildListRecentIssuesUrl` | Pure URL builder |
| `.iw/core/YouTrackClient.scala:85` | `parseListRecentIssuesResponse` | Pure JSON parser |
| `.iw/core/YouTrackClient.scala:122` | `listRecentIssues` | Effectful HTTP call |
| `.iw/core/CaskServer.scala:553` | `buildFetchRecentFunction` | YouTrack routing |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    CaskServer                               │
│  GET /api/issues/recent                                     │
│                                                             │
│  buildFetchRecentFunction:                                  │
│    case YouTrack => YouTrackClient.listRecentIssues(...)   │
└────────────────────────────┬────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                  YouTrackClient                             │
│                                                             │
│  listRecentIssues(baseUrl, limit, token)                   │
│    │                                                        │
│    ├─► buildListRecentIssuesUrl()  [pure]                  │
│    │     Returns: {baseUrl}/api/issues?fields=...&$top=N   │
│    │                                                        │
│    ├─► HTTP GET with Bearer token                           │
│    │                                                        │
│    └─► parseListRecentIssuesResponse()  [pure]             │
│          Parses JSON array, extracts State from customFields│
└─────────────────────────────────────────────────────────────┘
```

---

## Test Summary

| Test File | Tests | Coverage |
|-----------|-------|----------|
| `YouTrackClientTest.scala` | 7 | URL building, JSON parsing, error handling |

**Tests:**
1. `buildListRecentIssuesUrl returns correct URL with default limit (5)`
2. `buildListRecentIssuesUrl encodes spaces in orderBy parameter`
3. `parseListRecentIssuesResponse parses valid JSON array with multiple issues`
4. `parseListRecentIssuesResponse returns empty list for empty JSON array`
5. `parseListRecentIssuesResponse extracts State from customFields`
6. `parseListRecentIssuesResponse returns Unknown for missing State`
7. `parseListRecentIssuesResponse returns error for malformed JSON`

---

## Files Changed

| Status | File | Description |
|--------|------|-------------|
| M | `.iw/core/YouTrackClient.scala` | Added listRecentIssues, buildListRecentIssuesUrl, parseListRecentIssuesResponse |
| M | `.iw/core/CaskServer.scala` | Updated buildFetchRecentFunction to route YouTrack |
| A | `.iw/core/test/YouTrackClientTest.scala` | New test file with 7 unit tests |

---

## Technical Notes

1. **URL Pattern**: `{baseUrl}/api/issues?fields=idReadable,summary,customFields(name,value(name))&$top={limit}&$orderBy=created%20desc`
2. **Response Format**: Direct JSON array (unlike Linear which nests in `data.team.issues`)
3. **State Extraction**: Parses `State` from `customFields` array, defaults to "Unknown" if missing
4. **HTTP Client**: Uses `quickRequest` from sttp4 with Bearer token auth (consistent with existing `fetchIssue`)
5. **Error Handling**: Returns empty list on errors for graceful degradation (same as GitHub/Linear)

---

## Review Checklist

- [ ] Pure functions are separated from effectful functions
- [ ] Error handling is consistent with existing patterns
- [ ] Tests cover happy path and error cases
- [ ] No security issues (token handling is correct)
- [ ] Code style matches existing codebase
