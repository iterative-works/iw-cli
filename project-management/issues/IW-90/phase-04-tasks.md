# Phase 4 Tasks: GitLab issue URL generation in search and dashboard

**Issue:** IW-90
**Phase:** 4 of 7
**Estimated Effort:** 2-3 hours

---

## Setup

- [x] [setup] Read phase context and understand GitLab URL format (`/-/issues/`)
- [x] [setup] Review existing buildIssueUrl implementations in codebase

## Tests

- [x] [test] Add unit test for gitlab.com URL generation (default baseUrl)
- [x] [test] Add unit test for self-hosted GitLab URL with custom baseUrl
- [x] [test] Add unit test for nested groups (company/team/project)
- [x] [test] Add unit test for issue number extraction from various formats (123, IW-123, #123)

## Implementation

- [x] [impl] Add GitLab case to IssueSearchService.buildIssueUrl
- [x] [impl] Add GitLab case to IssueCacheService.buildIssueUrl
- [x] [impl] Add GitLab case to DashboardService.buildUrlBuilder
- [x] [impl] Ensure correct URL format: `{baseUrl}/{repository}/-/issues/{number}`

## Integration

- [x] [integration] Run all tests to verify no regressions
- [x] [integration] Verify compilation has no warnings

---

## Notes

- GitLab URL format: `https://gitlab.com/{group}/{project}/-/issues/{number}`
- Key difference from GitHub: the `/-/` path segment
- Reuse `extractGitHubIssueNumber` for number extraction (same logic)
- `youtrackBaseUrl` config field is reused for GitLab baseUrl
