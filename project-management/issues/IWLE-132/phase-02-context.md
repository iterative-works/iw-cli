# Phase 2: Repository auto-detection from git remote

**Issue:** IWLE-132
**Phase:** 2 of 6
**Status:** Pending

## Goals

This phase refines and completes the repository auto-detection functionality started in Phase 1:
1. Ensure robust handling of both HTTPS and SSH GitHub URL formats
2. Add comprehensive validation and error messages for repository detection
3. Handle edge cases (multiple remotes, non-GitHub URLs, malformed URLs)
4. Improve user experience with clear guidance when auto-detection fails

**Note:** Phase 1 already implemented the basic `repositoryOwnerAndName` method and integrated it into `iw init`. Phase 2 focuses on edge cases, validation improvements, and ensuring the implementation fully satisfies Story 5 requirements.

## Scope

### In Scope
- Verify and test HTTPS GitHub URL parsing (with/without `.git` suffix)
- Verify and test SSH GitHub URL parsing (with/without `.git` suffix)
- Handle repositories with multiple remotes (prefer `origin`)
- Improve error messages for non-GitHub remotes when `--tracker github` is used
- Validate repository format more robustly (empty owner/repo, special characters)
- Add comprehensive unit and E2E tests for all URL format edge cases
- Document repository detection behavior

### Out of Scope
- Creating issues (Phase 3)
- Viewing issues (Phase 5)
- gh CLI validation (Phase 4)
- Doctor command updates (Phase 6)
- Support for non-GitHub git hosting (GitLab, Bitbucket) with GitHub tracker

## Dependencies

### Previous Phases
**Phase 1 (Complete):**
- `GitRemote.repositoryOwnerAndName()` method exists and is functional
- `init.scala` already calls repository auto-detection for GitHub tracker
- Config schema supports `repository` field
- Basic HTTPS and SSH parsing implemented

### External Dependencies
- Existing `GitAdapter.getRemoteUrl()` for reading git config
- Existing `TrackerDetector.suggestTracker()` for tracker detection

## Technical Approach

### 1. Repository Detection Enhancement

**Current Implementation Review:**
```scala
// Already exists in Config.scala (Phase 1)
def repositoryOwnerAndName: Either[String, String] =
  // Handles HTTPS: https://github.com/owner/repo.git
  // Handles SSH: git@github.com:owner/repo.git
  // Validates: owner/repo format, rejects empty components
```

**Phase 2 Enhancements:**
1. **Test Coverage:** Add comprehensive tests for edge cases
2. **Error Messages:** Improve clarity of error messages
3. **Multi-Remote Handling:** Ensure `origin` is always preferred
4. **Validation:** Add tests for special characters, URL encoding, subgroups

### 2. URL Format Handling

**Supported Formats (already implemented, needs testing):**
- `https://github.com/owner/repo.git`
- `https://github.com/owner/repo` (without .git)
- `git@github.com:owner/repo.git`
- `git@github.com:owner/repo` (without .git)

**Edge Cases to Handle:**
- Trailing slashes: `https://github.com/owner/repo/`
- URL with username: `https://username@github.com/owner/repo.git`
- Port numbers: `ssh://git@github.com:22/owner/repo.git` (rare)
- Non-GitHub URLs: Clear error message directing to manual input

### 3. Multi-Remote Handling

**Strategy:**
- `GitAdapter.getRemoteUrl()` already reads `origin` by default
- Verify this behavior works correctly
- Add test case with multiple remotes (origin, upstream)
- Document that `origin` is the preferred remote

### 4. Error Messages and User Guidance

**Scenarios to Handle:**

1. **Non-GitHub remote with `--tracker github`:**
   ```
   Warning: Remote URL 'https://gitlab.com/company/project.git' is not a GitHub URL.
   Please enter the GitHub repository manually.
   GitHub repository (owner/repo):
   ```

2. **No remote configured:**
   ```
   Warning: No git remote found.
   Please enter the GitHub repository manually.
   GitHub repository (owner/repo):
   ```

3. **Invalid repository format:**
   ```
   Error: Repository must be in owner/repo format (e.g., 'iterative-works/iw-cli')
   Please enter the GitHub repository:
   ```

## Files to Modify

### Core Files (likely no changes needed)
- `.iw/core/Config.scala` - `GitRemote.repositoryOwnerAndName` already implemented
- `.iw/commands/init.scala` - Already calls repository detection, may improve error handling

### Test Files (primary focus of Phase 2)
- `.iw/core/test/ConfigTest.scala` - Add comprehensive URL parsing tests
- `.iw/test/init.bats` - Add E2E tests for edge cases

### Documentation Files
- `README.md` - Document repository detection behavior
- Phase context file (this file)

## Testing Strategy

### Unit Tests (ConfigTest.scala)

**URL Format Tests:**
1. ✅ HTTPS with .git: `https://github.com/iterative-works/iw-cli.git`
2. ✅ HTTPS without .git: `https://github.com/iterative-works/iw-cli`
3. ✅ SSH with .git: `git@github.com:iterative-works/iw-cli.git`
4. ✅ SSH without .git: `git@github.com:iterative-works/iw-cli`
5. ⚠️ HTTPS with trailing slash: `https://github.com/iterative-works/iw-cli/`
6. ⚠️ HTTPS with username: `https://username@github.com/iterative-works/iw-cli.git`
7. ✅ GitLab URL (should reject): `https://gitlab.com/company/project.git`
8. ✅ Bitbucket URL (should reject): `https://bitbucket.org/company/project.git`

**Validation Tests:**
1. ✅ Invalid format (no slash): `https://github.com/invalid`
2. ✅ Empty owner: `https://github.com//repo.git`
3. ✅ Empty repo: `https://github.com/owner/.git`
4. ✅ Too many slashes: `https://github.com/owner/repo/subpath`

**Legend:**
- ✅ Already tested in Phase 1
- ⚠️ Needs testing in Phase 2

### Integration Tests

1. **Multi-Remote Handling:**
   - Set up test repo with `origin` and `upstream` remotes
   - Verify `GitAdapter.getRemoteUrl()` returns `origin`

2. **Error Handling:**
   - Test behavior when git remote is not configured
   - Test behavior when remote is non-GitHub URL

### E2E Tests (init.bats)

**Existing Tests (Phase 1):**
- ✅ Init with GitHub tracker and HTTPS remote
- ✅ Init with GitHub tracker and SSH remote
- ✅ Warning shown for non-GitHub remote

**New Tests (Phase 2):**
1. Init with multiple remotes (origin + upstream)
2. Init with no remote configured → manual input prompt
3. Init with malformed remote URL → manual input prompt
4. Init with trailing slash in remote URL
5. Regression: Existing Linear/YouTrack tests still pass

## Acceptance Criteria

From Story 5 Gherkin scenarios:

```gherkin
Scenario: Auto-detect from HTTPS remote
  Given I am in a git repository
  And the remote origin URL is "https://github.com/iterative-works/iw-cli.git"
  When I run "iw init --tracker github"
  Then the repository is detected as "iterative-works/iw-cli"
  And it is stored in the configuration

Scenario: Auto-detect from SSH remote
  Given I am in a git repository
  And the remote origin URL is "git@github.com:iterative-works/iw-cli.git"
  When I run "iw init --tracker github"
  Then the repository is detected as "iterative-works/iw-cli"
  And it is stored in the configuration

Scenario: Multiple remotes - use origin
  Given I am in a git repository
  And I have remotes "origin" and "upstream"
  When I run "iw init --tracker github"
  Then the repository from "origin" remote is used

Scenario: Non-GitHub remote with GitHub tracker
  Given I am in a git repository
  And the remote origin is "https://gitlab.com/company/project.git"
  When I run "iw init --tracker github"
  Then I see a warning that remote is not GitHub
  But the initialization proceeds
  And I am prompted to enter repository manually as "owner/repo"
```

Additional criteria:
- All URL format edge cases handled gracefully
- Clear error messages guide users through manual input
- Repository validation catches common mistakes
- Existing Linear/YouTrack initialization remains unaffected

## Implementation Tasks

### Task 1: Review Phase 1 Implementation
- [x] Read `GitRemote.repositoryOwnerAndName` implementation
- [x] Verify it handles HTTPS and SSH correctly
- [x] Check error messages are user-friendly
- [x] Review init.scala integration

### Task 2: Add Missing Unit Tests
- [ ] Test URL with trailing slash
- [ ] Test URL with username prefix
- [ ] Test multi-remote scenario (if applicable to unit level)
- [ ] Test special characters in owner/repo names (if valid)

### Task 3: Add E2E Tests
- [ ] Test with multiple remotes (origin + upstream)
- [ ] Test with no remote configured
- [ ] Test with malformed remote URL
- [ ] Test repository validation in init command

### Task 4: Improve Error Messages (if needed)
- [ ] Review current error messages in init.scala
- [ ] Enhance clarity if needed
- [ ] Add helpful suggestions (e.g., "Use owner/repo format")

### Task 5: Documentation
- [ ] Document repository detection in README.md
- [ ] Add troubleshooting guide for common issues
- [ ] Update init command help text if needed

## Notes

**Key Insight from Phase 1:**
The `repositoryOwnerAndName` method in Phase 1 already handles the core functionality well:
- Parses HTTPS and SSH URLs
- Validates owner/repo format
- Rejects non-GitHub URLs
- Handles .git suffix correctly

**Phase 2 Focus:**
This phase is primarily about **verification, testing, and polish**:
1. Ensure all edge cases are covered by tests
2. Verify error messages are helpful and clear
3. Add missing E2E test scenarios
4. Document the behavior for users

**Risk Assessment:**
- **Low risk:** Most logic already implemented in Phase 1
- **Main work:** Writing comprehensive tests
- **Potential issues:** Edge cases in URL parsing (trailing slashes, usernames)

## Expected Deliverables

1. **Tests:**
   - 5-10 new unit tests for edge cases
   - 3-4 new E2E tests for init scenarios
   - All tests passing

2. **Code Changes:**
   - Minimal changes to production code (mostly test code)
   - Possible small improvements to error messages
   - Bug fixes if edge cases reveal issues

3. **Documentation:**
   - README section on repository detection
   - Troubleshooting guide
   - Code comments where helpful

## Success Metrics

- [ ] All Gherkin scenarios from Story 5 have corresponding automated tests
- [ ] Test coverage for `repositoryOwnerAndName` is comprehensive
- [ ] E2E tests cover all documented URL formats
- [ ] No regressions in existing functionality (Linear/YouTrack)
- [ ] User-facing error messages are clear and actionable
