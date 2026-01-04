# Phase 4 Tasks: GitLab issue URL generation in search and dashboard

**Issue:** IW-90
**Phase:** 4 of 7
**Estimated Effort:** 2-3 hours

---

## Setup

- [ ] [setup] Read phase context and understand GitLab URL format (`/-/issues/`)
- [ ] [setup] Review existing buildIssueUrl implementations in codebase

## Tests

- [ ] [test] Add unit test for gitlab.com URL generation (default baseUrl)
- [ ] [test] Add unit test for self-hosted GitLab URL with custom baseUrl
- [ ] [test] Add unit test for nested groups (company/team/project)
- [ ] [test] Add unit test for issue number extraction from various formats (123, IW-123, #123)

## Implementation

- [ ] [impl] Add GitLab case to IssueSearchService.buildIssueUrl
- [ ] [impl] Add GitLab case to IssueCacheService.buildIssueUrl
- [ ] [impl] Add GitLab case to DashboardService.buildUrlBuilder
- [ ] [impl] Ensure correct URL format: `{baseUrl}/{repository}/-/issues/{number}`

## Integration

- [ ] [integration] Run all tests to verify no regressions
- [ ] [integration] Verify compilation has no warnings

---

## Notes

- GitLab URL format: `https://gitlab.com/{group}/{project}/-/issues/{number}`
- Key difference from GitHub: the `/-/` path segment
- Reuse `extractGitHubIssueNumber` for number extraction (same logic)
- `youtrackBaseUrl` config field is reused for GitLab baseUrl
