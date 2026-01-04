# Implementation Log: Support GitLab issue tracker via glab

Issue: IW-90

This log tracks the evolution of implementation across phases.

---

## Phase 4: GitLab issue URL generation in search and dashboard (2026-01-04)

**What was built:**
- IssueSearchService: Added GitLab case to `buildIssueUrl` using `/-/issues/` path format
- IssueCacheService: Added GitLab case to `buildIssueUrl` with `repository|baseUrl` config format
- DashboardService: Added GitLab case to `buildUrlBuilder` to pass repository and optional baseUrl

**Decisions made:**
- GitLab URLs use `/-/issues/` path (different from GitHub's `/issues/`)
- Config value format: "repository" for gitlab.com, "repository|baseUrl" for self-hosted
- Reused `extractGitHubIssueNumber` for number extraction (same logic works for GitLab)
- Reused `youtrackBaseUrl` field for GitLab baseUrl (semantic naming debt carried forward)

**Patterns applied:**
- Pattern matching for tracker type routing
- Pipe-delimited string for compound config value (repository + optional baseUrl)
- Same number extraction logic as GitHub (handles IW-123, #123, 123 formats)

**Testing:**
- Unit tests: 8 tests added
  - IssueCacheServiceTest: 6 new tests for GitLab URL generation
  - IssueSearchServiceTest: 2 new tests for GitLab search with URL verification
- Test coverage: gitlab.com default, self-hosted baseUrl, nested groups, number extraction

**Code review:**
- Iterations: 0 (pending)
- All unit tests passing: 346 total tests

**For next phases:**
- Available utilities: GitLab URL generation in all services
- Extension points: Same pattern can be used for future trackers
- Notes: Phase 5 will add GitLab issue creation via glab CLI

**Files changed:**
```
M  .iw/core/IssueSearchService.scala
M  .iw/core/IssueCacheService.scala
M  .iw/core/DashboardService.scala
M  .iw/core/test/IssueCacheServiceTest.scala
M  .iw/core/test/IssueSearchServiceTest.scala
```

---

## Phase 3: Configure GitLab tracker during iw init (2026-01-04)

**What was built:**
- GitRemote: `extractGitLabRepository()` - Extracts repository path from GitLab URLs (supports nested groups)
- TrackerDetector: GitLab detection for gitlab.com and self-hosted instances
- ConfigSerializer: GitLab baseUrl serialization/deserialization
- init.scala: Full GitLab configuration flow with auto-detection, prompts, and next steps

**Decisions made:**
- Reused `youtrackBaseUrl` field for GitLab baseUrl (semantic naming debt noted for future)
- GitLab allows nested groups (group/subgroup/project) unlike GitHub's owner/repo
- Self-hosted GitLab detected by host != "gitlab.com" (prompts for baseUrl)
- Team prefix validation same as GitHub (2-10 uppercase letters)

**Patterns applied:**
- Pure functions for repository extraction and host detection
- Either-based error handling consistent with existing patterns
- Pattern matching for tracker-specific configuration branches

**Testing:**
- Unit tests: 33 tests added
  - GitRemote extraction: 17 tests (10 happy path + 7 error path)
  - TrackerDetector: 4 tests
  - ConfigSerializer: 12 tests (serialization, deserialization, round-trip)
- E2E tests: 11 tests in init.bats
  - HTTPS/SSH URL handling
  - Nested groups
  - Self-hosted with baseUrl
  - Team prefix validation
  - glab CLI hints

**Code review:**
- Iterations: 2
- Iteration 1: 2 critical issues (missing E2E tests, missing error path tests)
- Iteration 2: 0 critical, 7 warnings (acceptable technical debt)
- Review files: review-phase-03-20260104-205224.md, review-phase-03-20260104-210836.md

**For next phases:**
- Available utilities: `extractGitLabRepository`, GitLab detection in TrackerDetector
- Extension points: ConfigSerializer patterns for additional tracker types
- Notes: Phase 4 will add GitLab issue URL generation for search/dashboard

**Files changed:**
```
M  .iw/commands/init.scala
M  .iw/core/Config.scala
M  .iw/core/test/ConfigTest.scala
M  .iw/test/init.bats
```

---

## Phase 2: Handle GitLab-specific error conditions gracefully (2026-01-04)

**What was built:**
- Error formatting functions in `GitLabClient.scala`:
  - `formatGlabNotInstalledError()` - Installation instructions for glab CLI
  - `formatGlabNotAuthenticatedError()` - Authentication instructions
  - `formatIssueNotFoundError(issueId, repository)` - Clear "not found" message
  - `formatNetworkError(details)` - Network troubleshooting guidance
- Error detection functions:
  - `isAuthenticationError(error)` - Detects 401/unauthorized/authentication
  - `isNotFoundError(error)` - Detects 404/not found
  - `isNetworkError(error)` - Detects network/connection/timeout errors
- Enhanced error handling in `issue.scala` for GitLab case

**Decisions made:**
- Used simple string matching for error detection (case-insensitive where appropriate)
- Error messages are multi-line with actionable remediation steps
- Error classification happens at the presentation layer (issue.scala)
- Kept error detection as simple Boolean predicates for testability

**Patterns applied:**
- Pure functions for error formatting and detection
- Pattern matching with guards for error classification in issue.scala
- Positive and negative tests for error detection accuracy

**Testing:**
- Unit tests: 16 tests added covering:
  - Error formatting (4 tests)
  - Error detection positive cases (9 tests)
  - Error detection negative cases (3 tests)

**Code review:**
- Iterations: 1
- Major findings: None - passed with suggestions only
- Suggestions noted: Consider error classification enum, test data builders

**For next phases:**
- Available utilities: All error formatting and detection functions
- Extension points: Error detection patterns can be extended for new error types
- Notes: Phase 3 will add GitLab tracker configuration during `iw init`

**Files changed:**
```
M  .iw/core/GitLabClient.scala
M  .iw/core/test/GitLabClientTest.scala
M  .iw/commands/issue.scala
```

---

## Phase 1: Fetch and display GitLab issue via glab CLI (2026-01-04)

**What was built:**
- Module: `.iw/core/GitLabClient.scala` - GitLab CLI client for issue fetching via glab command
- Tests: `.iw/core/test/GitLabClientTest.scala` - Comprehensive unit tests (21 tests)
- Domain: `IssueTrackerType.GitLab` enum variant added to Config.scala
- Integration: `issue.scala` now handles GitLab tracker type

**Decisions made:**
- Followed GitHubClient pattern exactly for consistency and maintainability
- Used `iid` (project-scoped issue number) instead of `id` (global issue ID)
- GitLab uses same config structure as GitHub (repository + teamPrefix)
- Relaxed repository validation to allow nested groups (e.g., `company/team/project`)

**Patterns applied:**
- Dependency injection via function parameters for testability (isCommandAvailable, execCommand)
- Either-based error handling consistent with existing codebase
- Enum for prerequisite errors (GlabNotInstalled, GlabNotAuthenticated, GlabError)

**Testing:**
- Unit tests: 21 tests added covering:
  - Command building (3 tests)
  - JSON parsing (9 tests)
  - Prerequisite validation (3 tests)
  - Integration (6 tests)

**Code review:**
- Iterations: 1
- Major findings: None - passed on first iteration

**For next phases:**
- Available utilities: `GitLabClient.fetchIssue`, `validateGlabPrerequisites`
- Extension points: `buildFetchIssueCommand`, `parseFetchIssueResponse` can be reused
- Notes: Phase 2 will add detailed error handling and user-friendly messages

**Files changed:**
```
A  .iw/core/GitLabClient.scala
A  .iw/core/test/GitLabClientTest.scala
M  .iw/core/Config.scala
M  .iw/core/Constants.scala
M  .iw/commands/issue.scala
```

---
