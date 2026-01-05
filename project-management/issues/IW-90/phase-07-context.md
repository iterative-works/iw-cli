# Phase 7 Context: Integration testing with real glab CLI

**Issue:** IW-90
**Phase:** 7 of 7
**Status:** Complete

## Goals

This phase ensures comprehensive test coverage for the GitLab integration and validates that all components work correctly with the real glab CLI. It's the final quality gate before release.

## Scope

### In Scope

1. **E2E Tests with BATS**
   - Test `iw issue` command with GitLab tracker configuration
   - Test `iw feedback` command for GitLab issue creation
   - Test error scenarios (glab not installed, not authenticated, issue not found)
   - Test `iw init` GitLab auto-detection and manual selection

2. **Integration Test Review**
   - Verify all GitLabClient unit tests are comprehensive
   - Ensure mock-based tests cover edge cases
   - Validate error detection functions

3. **Manual Verification**
   - Test with real GitLab repository
   - Verify `iw issue` displays correct issue details
   - Verify `iw feedback` creates issues successfully
   - Test self-hosted GitLab URL handling

4. **Documentation Updates**
   - Update README with GitLab support instructions
   - Document glab CLI installation requirements
   - Add GitLab configuration examples

### Out of Scope

- New feature development (all features completed in phases 1-6)
- Performance optimization
- CI pipeline setup for E2E tests (future work)

## Dependencies

### From Previous Phases

- **Phase 1**: GitLabClient.fetchIssue, validateGlabPrerequisites
- **Phase 2**: Error formatting and detection functions
- **Phase 3**: GitLab tracker configuration, TrackerDetector
- **Phase 4**: GitLab URL generation in services
- **Phase 5**: GitLabClient.createIssue for feedback command
- **Phase 6**: Tracker-aware IssueId parsing

### External Dependencies

- glab CLI installed and authenticated for E2E tests
- Test GitLab repository with sample issues
- Network access to gitlab.com (for E2E tests)

## Technical Approach

### E2E Test Structure

Follow existing BATS test patterns in `.iw/test/`:

```bash
# gitlab-issue.bats
@test "iw issue fetches GitLab issue successfully" {
  setup_gitlab_config
  run ./iw issue 1
  assert_success
  assert_output --partial "Issue #1"
}

@test "iw issue shows error when glab not installed" {
  setup_gitlab_config
  mock_glab_not_installed
  run ./iw issue 1
  assert_failure
  assert_output --partial "glab CLI is not installed"
}
```

### Test Categories

1. **Happy Path Tests**
   - Issue fetching with valid ID
   - Issue creation via feedback
   - Branch name issue inference

2. **Error Path Tests**
   - glab not installed
   - glab not authenticated
   - Issue not found
   - Network errors (optional)

3. **Configuration Tests**
   - GitLab auto-detection during init
   - Self-hosted GitLab baseUrl handling
   - Nested group repository paths

### Test Data Strategy

- Use existing test issues in public GitLab projects
- Mock glab CLI responses for fast unit tests
- Skip E2E tests if glab not available (with warning)

## Files to Modify

### New Files

- `.iw/test/gitlab-issue.bats` - E2E tests for GitLab issue commands
- `.iw/test/gitlab-feedback.bats` - E2E tests for GitLab issue creation

### Modified Files

- `.iw/test/init.bats` - Add GitLab-specific test cases (if not already covered)
- `README.md` - Add GitLab documentation
- `.iw/core/test/GitLabClientTest.scala` - Review and extend coverage

## Testing Strategy

### Unit Test Coverage Goals

- All GitLabClient public functions tested
- Error detection functions tested (positive and negative)
- Command building tested with various inputs
- JSON parsing tested with edge cases

### E2E Test Coverage Goals

- Issue fetch: valid issue, not found, auth error
- Issue create: bug type, feature type, label fallback
- Init: auto-detect gitlab.com, self-hosted, nested groups
- ID parsing: numeric ID, branch extraction

### Test Environment Requirements

```bash
# Required for full E2E testing:
glab --version          # Verify glab installed
glab auth status        # Verify authenticated
```

## Acceptance Criteria

1. **All existing unit tests pass** (346+ tests)
2. **E2E tests added** for GitLab commands
3. **Manual verification** with real GitLab repository documented
4. **README updated** with GitLab setup instructions
5. **No regressions** in GitHub/Linear/YouTrack functionality

## Notes

- E2E tests may be slower than unit tests due to glab CLI invocation
- Consider skipping E2E tests in CI if glab not configured
- Phase 6 left one manual verification task for Phase 7: `iw issue` on GitLab-configured project
- This is the final phase - quality and documentation are the priority

## References

- Story 7 from analysis.md: "Integration testing with real glab CLI"
- Existing test patterns: `.iw/test/init.bats`, `.iw/test/issue.bats`
- GitLabClient implementation: `.iw/core/GitLabClient.scala`
