# Phase 7 Tasks: Integration testing with real glab CLI

**Issue:** IW-90
**Phase:** 7 of 7
**Estimated Time:** 4-6 hours
**Status:** Complete

## Overview

This phase completes IW-90 by adding comprehensive E2E tests for GitLab integration, reviewing unit test coverage, performing manual verification, and updating documentation. This is the final quality gate before release.

## Task Groups

### 1. E2E Test Setup (30 minutes)

- [x] Create `.iw/test/gitlab-issue.bats` test file with standard BATS structure
- [x] Create `.iw/test/gitlab-feedback.bats` test file with standard BATS structure
- [x] Add setup/teardown functions with temp directory and git initialization
- [x] Document test prerequisites (glab installed, authenticated) in test comments

### 2. E2E Tests: Issue Fetching (60-90 minutes)

#### Happy Path Tests
- [x] Test `iw issue 123` fetches GitLab issue successfully with gitlab.com config
- [x] Test `iw issue` infers issue ID from branch name (e.g., `123-feature-branch`)
- [x] Test issue display shows ID, title, status, assignee, and description
- [x] Test fetching issue with self-hosted GitLab baseUrl

#### Error Scenario Tests
- [x] Test error when glab CLI not installed (mock PATH to exclude glab)
- [x] Test error when glab not authenticated (requires glab auth logout setup)
- [x] Test error when issue not found (use high numeric ID like 999999)
- [x] Test error when network unavailable (optional, may be hard to mock)

#### Configuration Tests
- [x] Test with simple repository path `owner/project`
- [x] Test with nested groups repository path `company/team/project`
- [x] Test that error messages reference correct repository

### 3. E2E Tests: Issue Creation (60-90 minutes)

#### Happy Path Tests
- [x] Test `iw feedback` creates bug issue with "bug" label
- [x] Test `iw feedback` creates feature request with "feature" label
- [x] Test created issue URL is returned and displayed
- [x] Test issue creation with self-hosted GitLab baseUrl

#### Error Scenario Tests
- [x] Test error when glab not installed (consistent with issue tests)
- [x] Test error when glab not authenticated
- [x] Test label fallback behavior (optional - may require label deletion)

#### Integration Tests
- [x] Test feedback command with GitLab tracker type in config
- [x] Test issue title and description are correctly passed to glab
- [x] Verify no regression in GitHub/Linear/YouTrack feedback

### 4. E2E Tests: Init Command (30 minutes)

Note: Phase 3 already added some init tests to `init.bats`. Review and extend if needed.

- [x] Review existing GitLab init tests in `.iw/test/init.bats`
- [x] Verify test coverage for gitlab.com auto-detection
- [x] Verify test coverage for manual GitLab selection
- [x] Verify test coverage for self-hosted GitLab baseUrl prompts
- [x] Add missing tests if any gaps identified

### 5. Unit Test Coverage Review (45 minutes)

- [x] Run unit tests: `./iw test unit`
- [x] Review `GitLabClientTest.scala` for comprehensive coverage
- [x] Verify all public functions have positive and negative tests
- [x] Verify error detection functions (`isAuthenticationError`, etc.) have edge case tests
- [x] Verify command building functions handle all parameter variations
- [x] Verify JSON parsing handles malformed/missing fields
- [x] Document any test gaps discovered (if found, create tasks to address them)

### 6. Manual Verification with Real GitLab (60 minutes)

#### Setup Real GitLab Test Environment
- [x] Identify or create test GitLab repository (can use existing or create new)
- [x] Ensure glab CLI installed: `glab --version`
- [x] Ensure glab authenticated: `glab auth status`
- [x] Create test issues in GitLab repository (bug and feature types)

#### Manual Test Scenarios
- [x] Initialize new project with GitLab tracker: `./iw init`
- [x] Verify config.conf has `type = gitlab` and correct repository
- [x] Fetch existing issue: `./iw issue 1` (use real issue from test repo)
- [x] Verify issue details displayed correctly (title, status, assignee, description)
- [x] Create new bug via feedback: `./iw feedback`
- [x] Verify bug created in GitLab with "bug" label
- [x] Create new feature request via feedback
- [x] Verify feature created in GitLab with "feature" label
- [x] Test branch inference: checkout `123-test-branch` and run `./iw issue`
- [x] Test self-hosted GitLab if available (or document as "not tested")

#### Document Results
- [x] Create manual test log documenting all scenarios and outcomes
- [x] Note any issues discovered during manual testing
- [x] Add test log to `project-management/issues/IW-90/manual-test-log.md`

### 7. Documentation Updates (45-60 minutes)

#### README Updates
- [x] Add GitLab to supported trackers list in README.md overview
- [x] Add glab CLI installation section under prerequisites
  - Installation instructions for macOS (brew), Linux (package managers), Windows
  - Link to official glab installation docs
- [x] Add GitLab authentication instructions: `glab auth login`
- [x] Add GitLab configuration example in tracker configuration section
- [x] Document self-hosted GitLab baseUrl configuration
- [x] Add example of nested groups repository format

#### Configuration Documentation
- [x] Document `tracker.type = gitlab` in configuration reference
- [x] Document `tracker.repository` format for GitLab (owner/project or group/subgroup/project)
- [x] Document optional `tracker.baseUrl` for self-hosted GitLab (defaults to gitlab.com)
- [x] Add example configuration for gitlab.com
- [x] Add example configuration for self-hosted GitLab

#### User Guide Updates
- [x] Add GitLab example to "Using iw issue" documentation
- [x] Add GitLab example to "Using iw feedback" documentation
- [x] Document GitLab-specific limitations if any discovered

### 8. Regression Testing (30 minutes)

- [x] Run full test suite: `./iw test`
- [x] Verify all existing tests pass (unit + E2E)
- [x] Verify no regressions in GitHub tracker functionality
- [x] Verify no regressions in Linear tracker functionality
- [x] Verify no regressions in YouTrack tracker functionality
- [x] Verify overall test count increased (new GitLab tests added)

### 9. Final Quality Checks (30 minutes)

- [x] Review all code changes in Phase 7 for consistency
- [x] Ensure all new test files follow project conventions
- [x] Ensure test descriptions are clear and maintainable
- [x] Verify test output is clean (no extraneous logs)
- [x] Run code formatting if needed
- [x] Commit all changes with descriptive messages

### 10. Phase Completion (15 minutes)

- [x] Update `phase-07-context.md` status to "Completed"
- [x] Update `tasks.md` to mark Phase 7 complete
- [x] Create summary of Phase 7 work in implementation log
- [x] Document test coverage statistics (number of tests added)
- [x] Prepare for final IW-90 review and PR

## Notes

- **Test Environment**: E2E tests may be slower due to glab CLI invocation
- **Test Isolation**: Use temp directories and clean git repos for each test
- **Skipping Tests**: Consider adding logic to skip E2E tests if glab not available
- **Manual Testing**: Critical for validating real-world usage patterns
- **Documentation**: Quality documentation is as important as code quality

## Acceptance Criteria

1. E2E tests added for GitLab issue fetching and creation
2. All existing unit tests pass (GitLabClientTest comprehensive)
3. Manual verification completed and documented
4. README updated with GitLab setup instructions
5. No regressions in other tracker types
6. Full test suite passes: `./iw test`

## Estimated Breakdown

- E2E test setup: 30 min
- E2E issue fetching tests: 60-90 min
- E2E issue creation tests: 60-90 min
- E2E init tests review: 30 min
- Unit test review: 45 min
- Manual verification: 60 min
- Documentation: 45-60 min
- Regression testing: 30 min
- Quality checks: 30 min
- Phase completion: 15 min

**Total: 4-6 hours**

## References

- Phase context: `phase-07-context.md`
- Existing test patterns: `.iw/test/issue.bats`, `.iw/test/feedback.bats`
- GitLabClient tests: `.iw/core/test/GitLabClientTest.scala`
- Story 7 from `analysis.md`
