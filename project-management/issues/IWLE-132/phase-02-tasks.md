# Phase 2 Tasks: Repository auto-detection from git remote

**Issue:** IWLE-132
**Phase:** 2 of 6
**Status:** Complete
**Estimated Time:** 4-6 hours

## Context

Phase 1 already implemented the core `GitRemote.repositoryOwnerAndName()` method and integrated it into `iw init`. Phase 2 focuses on:
- Testing edge cases identified in the context document
- Verifying multi-remote scenarios
- Improving error messages if gaps are found
- Ensuring comprehensive E2E coverage

## Task Breakdown

### Setup

- [x] [setup] Review Phase 1 implementation of `repositoryOwnerAndName` in Config.scala
- [x] [setup] Review existing unit tests in ConfigTest.scala to identify coverage gaps
- [x] [setup] Review existing E2E tests in init.bats to identify missing scenarios

### Unit Tests (ConfigTest.scala)

**Edge case URL format tests:**

- [x] [test] Add test for HTTPS URL with trailing slash (`https://github.com/owner/repo/`)
- [x] [test] Add test for HTTPS URL with username prefix (`https://username@github.com/owner/repo.git`)
- [x] [test] Add test for SSH URL with trailing slash (if applicable: `git@github.com:owner/repo/`)
- [x] [test] Add test for malformed SSH URL without colon (`git@github.com/owner/repo`)
- [x] [test] Run unit tests and verify all new tests pass (`./iw test unit`)

**Multi-remote validation:**

- [x] [test] Verify GitAdapter.getRemoteUrl() uses 'origin' by default (may need to check GitAdapter.scala)
- [x] [test] Document behavior when multiple remotes exist (origin vs upstream)

### E2E Tests (init.bats)

**Multi-remote scenario:**

- [x] [test] Add E2E test: Init with multiple remotes (origin + upstream) - verify origin is used
- [x] [test] Add E2E test: Init with no remote configured - verify graceful error/prompt

**Edge case URL scenarios:**

- [x] [test] Add E2E test: Init with HTTPS URL containing trailing slash
- [x] [test] Add E2E test: Init with HTTPS URL containing username prefix

**Validation scenarios:**

- [x] [test] Add E2E test: Verify repository stored correctly in config.conf after auto-detection
- [x] [test] Run E2E tests and verify all pass (`./iw test e2e`)

**Regression verification:**

- [x] [test] Run full test suite to ensure no regressions (`./iw test`)
- [x] [test] Verify Linear tracker initialization still works (existing test)
- [x] [test] Verify YouTrack tracker initialization still works (existing test)

### Implementation (if needed based on test findings)

**Error message improvements:**

- [x] [impl] Review error messages from repositoryOwnerAndName for clarity
- [x] [impl] If needed: Improve error message for non-GitHub remote
- [x] [impl] If needed: Improve error message for invalid repository format
- [x] [impl] If needed: Add helpful suggestions to error messages (e.g., "Expected format: owner/repo")

**Bug fixes (if edge cases reveal issues):**

- [x] [impl] Fix trailing slash handling if test reveals bug
- [x] [impl] Fix username prefix handling if test reveals bug
- [x] [impl] Handle any other edge cases discovered during testing

**Code refinement:**

- [x] [impl] If needed: Add code comments explaining URL parsing logic
- [x] [impl] If needed: Refactor duplicated URL parsing code

### Documentation

- [x] [doc] Add README section documenting repository auto-detection behavior
- [x] [doc] Document supported GitHub URL formats (HTTPS with/without .git, SSH with/without .git)
- [x] [doc] Add troubleshooting guide for common repository detection issues
- [x] [doc] Document that 'origin' remote is used when multiple remotes exist
- [x] [doc] Update init.scala help text if needed to clarify GitHub repository handling

### Phase Completion

- [x] [verify] All acceptance criteria from Story 5 met (check against Gherkin scenarios)
- [x] [verify] No regressions in existing functionality
- [x] [verify] All tests passing (unit + E2E)
- [x] [verify] Code review completed
- [x] [verify] Update implementation-log.md with Phase 2 summary

## Acceptance Criteria (from Story 5)

Phase 2 is complete when:

1. **Auto-detect from HTTPS remote works:**
   - URL with .git suffix: `https://github.com/owner/repo.git` ✓ (covered in Phase 1)
   - URL without .git suffix: `https://github.com/owner/repo` ✓ (covered in Phase 1)
   - URL with trailing slash: `https://github.com/owner/repo/` (new in Phase 2)
   - URL with username: `https://username@github.com/owner/repo.git` (new in Phase 2)

2. **Auto-detect from SSH remote works:**
   - URL with .git suffix: `git@github.com:owner/repo.git` ✓ (covered in Phase 1)
   - URL without .git suffix: `git@github.com:owner/repo` ✓ (covered in Phase 1)

3. **Multiple remotes - use origin:**
   - When both origin and upstream exist, origin is used (new in Phase 2)

4. **Non-GitHub remote with GitHub tracker:**
   - Warning shown: `https://gitlab.com/project.git` ✓ (covered in Phase 1)
   - User prompted for manual input ✓ (covered in Phase 1)
   - Initialization proceeds successfully ✓ (covered in Phase 1)

5. **Edge cases handled gracefully:**
   - No remote configured → clear error message
   - Malformed remote URL → clear error message
   - Invalid repository format → helpful validation error

6. **All existing functionality works:**
   - Linear tracker initialization unchanged
   - YouTrack tracker initialization unchanged
   - All existing E2E tests pass

## Notes

### Key Insight from Phase 1
The `repositoryOwnerAndName` method already handles:
- HTTPS and SSH URL parsing ✓
- .git suffix removal ✓
- owner/repo format validation ✓
- Non-GitHub URL rejection ✓

### Phase 2 Focus Areas
1. **Edge case testing** - ensure trailing slashes and username prefixes work
2. **Multi-remote scenarios** - verify origin is preferred
3. **Error message quality** - ensure users understand what went wrong
4. **Documentation** - help users understand auto-detection behavior

### Expected Outcome
- Minimal production code changes (mostly test code)
- 5-10 new unit tests for edge cases
- 3-5 new E2E tests for scenarios
- Clear documentation of repository detection
- High confidence in robustness

## Time Estimates

- Setup: 0.5h
- Unit Tests: 1.5h
- E2E Tests: 1.5h
- Implementation (if needed): 0-1h
- Documentation: 0.5-1h
- Verification: 0.5h

**Total: 4-6 hours**
