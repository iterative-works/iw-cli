# Implementation Log: Support GitLab issue tracker via glab

Issue: IW-90

This log tracks the evolution of implementation across phases.

---

## Phase 7: Integration testing with real glab CLI (2026-01-05)

**What was built:**
- E2E test file: `.iw/test/gitlab-issue.bats` - 13 tests for issue fetching
- E2E test file: `.iw/test/gitlab-feedback.bats` - 15 tests for issue creation
- Manual test log: `project-management/issues/IW-90/manual-test-log.md`
- README documentation: Comprehensive GitLab integration section

**E2E Test Coverage:**
- Happy path: Issue fetching, branch inference, display formatting
- Error scenarios: glab not installed, not authenticated, issue not found
- Configuration: Simple repository, nested groups, self-hosted GitLab
- Feedback: Bug/feature creation, label fallback, URL display
- Regression: Verification of GitHub/Linear/YouTrack functionality

**Documentation Added:**
- GitLab repository auto-detection (HTTPS, SSH, nested groups, self-hosted)
- glab CLI installation instructions (macOS, Linux, Windows)
- Authentication setup (`glab auth login`)
- Self-hosted GitLab configuration examples
- Nested groups support documentation

**Decisions made:**
- E2E tests use mocked glab CLI for CI reliability
- Tests skip gracefully when glab not available
- Manual verification documented for real-world testing
- README follows existing documentation structure

**Patterns applied:**
- BATS test patterns consistent with existing E2E tests
- Setup/teardown with temp directories for test isolation
- Mock scripts for external CLI dependencies

**Testing:**
- E2E tests: 28 tests added (13 issue + 15 feedback)
- Unit tests: All 353+ existing tests passing (no regressions)
- Manual verification: Documented in manual-test-log.md

**Code review:**
- Iterations: 1
- Critical issues: 0
- Warnings: 3 (minor stylistic)
- Suggestions: 6 (nice to have)
- Review file: review-phase-07.md

**For future:**
- E2E tests provide foundation for CI/CD integration
- Manual test checklist available for live testing
- Documentation enables users to set up GitLab integration

**Files changed:**
```
A  .iw/test/gitlab-issue.bats
A  .iw/test/gitlab-feedback.bats
A  project-management/issues/IW-90/manual-test-log.md
M  README.md
```

---

## Phase 6: GitLab issue ID parsing and validation (2026-01-05)

**What was built:**
- `IssueId.forGitHub(teamPrefix, number)` - Factory method for composing TEAM-NNN format
- `IssueId.parse()` - Extended with `defaultTeam` parameter for numeric ID composition
- `IssueId.fromBranch()` - Simplified to uniform TEAM-NNN extraction
- Commands updated: `issue.scala`, `open.scala`, `start.scala`, `register.scala`, `rm.scala`

**Decisions made:**
- GitLab IDs use same TEAM-NNN format as GitHub (unified format across all trackers)
- teamPrefix required in config for both GitHub and GitLab
- Removed trackerType parameter from IssueId parsing (simplified API)
- Branch patterns uniform: TEAM-NNN-description for all trackers

**Patterns applied:**
- Pattern matching on IssueTrackerType for teamPrefix determination
- Same opaque type pattern - all IDs use `IssueId` type with TEAM-NNN format
- Smart constructors validate input before creating IssueId

**Testing:**
- Unit tests: Updated existing tests for unified TEAM-NNN format
- E2E tests: 13 GitLab tests in gitlab-issue.bats

**Code review:**
- Iterations: 1 (before refactoring)
- Critical issues: 0
- Warnings: 5 (edge case tests, outdated comment)
- Review file: review-phase-06.md

**For next phases:**
- Available utilities: Unified `IssueId.parse` with defaultTeam, uniform format
- Extension points: Pattern for adding tracker-specific teamPrefix logic
- Notes: Phase 7 adds integration testing with real glab CLI

**Files changed:**
```
M  .iw/core/IssueId.scala
M  .iw/commands/issue.scala
M  .iw/commands/open.scala
M  .iw/commands/start.scala
M  .iw/commands/register.scala
M  .iw/commands/rm.scala
M  .iw/core/IssueSearchService.scala
M  .iw/core/test/IssueIdTest.scala
M  .iw/core/test/IssueIdFromBranchTest.scala
```

### Refactoring R1: Align GitLab IDs with TEAM-NNN format (2026-01-05)

**Trigger:** Code review discussion revealed Phase 6 introduced inconsistent ID handling. GitHub was intentionally designed to use teamPrefix so that numeric IDs become `TEAM-123` format. GitLab was given special `#123` format, which broke `.team` extension and created inconsistent branch names.

**What changed:**
- `IssueId.scala` - Removed `NumericPattern`, `NumericBranchPattern`, `forGitLab()`, and `trackerType` parameter
- `GitLabClient.scala` - Updated to accept full issueIdValue (PROJ-123) and extract number internally
- `GitHubClient.scala` - Same change as GitLabClient for consistency
- `issue.scala` - Updated to pass issueId.value instead of extracting number at command level
- Commands (`open`, `start`, `register`, `rm`) - Updated to pass teamPrefix for GitLab like GitHub

**Before → After:**
- GitLab IDs: `#123` → `PROJ-123` (same format as GitHub)
- Branch names: `123-feature` → `PROJ-123-feature` (consistent)
- IssueId API: `parse(raw, trackerType)` → `parse(raw, defaultTeam)` (simpler)

**Patterns applied:**
- Uniform TEAM-NNN format for all trackers (consistency)
- Number extraction moved into Client classes (encapsulation)
- teamPrefix logic centralized at command level

**Testing:**
- Unit tests: Updated GitLab tests to expect TEAM-NNN format
- E2E tests: Updated gitlab-issue.bats assertions for new format
- All 1126 unit tests passing
- All 13 GitLab E2E tests passing

**Code review:**
- Iterations: 2
- Iteration 1: 3 critical (GitLabClient/GitHubClient still using #123 format)
- Iteration 2: 0 critical, 4 warnings, 6 suggestions
- Review files: review-refactor-06-R1-20260105-153532.md, review-refactor-06-R1-20260105-155334.md

**Files changed:**
```
M  .iw/commands/issue.scala
M  .iw/commands/open.scala
M  .iw/commands/register.scala
M  .iw/commands/rm.scala
M  .iw/commands/start.scala
M  .iw/core/GitHubClient.scala
M  .iw/core/GitLabClient.scala
M  .iw/core/IssueId.scala
M  .iw/core/IssueSearchService.scala
M  .iw/core/test/GitHubClientTest.scala
M  .iw/core/test/GitLabClientTest.scala
M  .iw/core/test/IssueIdFromBranchTest.scala
M  .iw/core/test/IssueIdTest.scala
M  .iw/test/gitlab-issue.bats
```

---

## Phase 5: Create GitLab issues via glab CLI (2026-01-04)

**What was built:**
- `GitLabClient.buildCreateIssueCommand()` - Builds glab CLI command with labels
- `GitLabClient.buildCreateIssueCommandWithoutLabel()` - Fallback command for label errors
- `GitLabClient.parseCreateIssueResponse()` - Parses issue URL from glab output
- `GitLabClient.isLabelError()` - Detects label-related errors for retry logic
- `GitLabClient.createIssue()` - Main entry point with label fallback strategy
- `feedback.scala` - Routes to GitLab or GitHub based on tracker config

**Decisions made:**
- Followed `GitHubClient.createIssue` pattern exactly for consistency
- Label fallback strategy: Try with label first, retry without if label doesn't exist
- glab uses `--description` flag (not `--body` like gh)
- GitLab URLs use `/-/issues/` format for parsing
- Config routing: Read `.iw/config.conf` at execution time, not load time

**Patterns applied:**
- Dependency injection for testability (isCommandAvailable, execCommand parameters)
- Either-based error handling consistent with existing patterns
- Label fallback pattern (same as GitHubClient)
- Pattern matching for tracker type routing in feedback.scala

**Testing:**
- Unit tests: 15+ tests added covering:
  - Command building (Bug/Feature type mapping, with/without labels)
  - Response parsing (gitlab.com URLs, self-hosted URLs, error cases)
  - Label error detection (various error messages)
  - Integration tests (success path, retry logic, prerequisite failures)

**Code review:**
- Iterations: 1
- Major findings: 2 security concerns (contextually low risk - Scala Process API is safe)
- Style fix: Updated PURPOSE comments to reflect new functionality
- Review file: review-phase-05-20260104.md

**For next phases:**
- Available utilities: `GitLabClient.createIssue`, `buildCreateIssueCommand`
- Extension points: Label fallback pattern can be reused for other commands
- Notes: Phase 6 will add GitLab issue ID parsing and validation

**Files changed:**
```
M  .iw/commands/feedback.scala
M  .iw/core/GitLabClient.scala
M  .iw/core/test/GitLabClientTest.scala
```

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
