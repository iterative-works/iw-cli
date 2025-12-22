# Phase 1 Context: E2E tests skip real API calls by default

**Issue:** IWLE-131
**Phase:** 1 of 3
**Estimated:** 2-3 hours

## Goals

Make E2E tests safe to run repeatedly without creating real Linear issues. By default, tests that would create real issues in Linear should be skipped, requiring explicit opt-in via `ENABLE_LIVE_API_TESTS=1`.

## Scope

### In Scope
- Add `ENABLE_LIVE_API_TESTS` environment variable gate to live API tests
- Update skip conditions in `.iw/test/feedback.bats`
- Update skip messages to explain how to enable live tests
- Ensure tests pass in all scenarios:
  - No env vars set
  - Only `LINEAR_API_TOKEN` set
  - Both `LINEAR_API_TOKEN` and `ENABLE_LIVE_API_TESTS=1` set

### Out of Scope
- Warning messages when live tests enabled (Phase 2)
- Documentation updates beyond test comments (Phase 2)
- Mock-based unit tests (Phase 3)
- CI configuration (IWLE-114)

## Dependencies

- No dependencies on other phases
- Foundation for Phase 2

## Technical Approach

### Current State
Tests in `feedback.bats` currently skip when `LINEAR_API_TOKEN` is not set:
```bash
if [ -z "$LINEAR_API_TOKEN" ]; then
    skip "LINEAR_API_TOKEN not set, skipping live API test"
fi
```

### Target State
Tests that create real issues should require BOTH environment variables:
```bash
if [ -z "$LINEAR_API_TOKEN" ] || [ -z "$ENABLE_LIVE_API_TESTS" ]; then
    skip "Live API tests disabled. Set LINEAR_API_TOKEN and ENABLE_LIVE_API_TESTS=1 to enable."
fi
```

### Tests Affected
These tests create real Linear issues and need the new gate:
1. `feedback creates issue successfully`
2. `feedback with description creates issue`
3. `feedback with bug type creates issue`

### Tests NOT Affected
These tests don't create real issues and should remain unchanged:
1. `feedback without LINEAR_API_TOKEN fails` - Uses dummy token
2. `feedback without title fails` - Uses dummy token
3. `feedback with invalid type fails` - Uses dummy token
4. `feedback --help shows usage` - No API calls
5. `feedback -h shows usage` - No API calls

## Files to Modify

| File | Changes |
|------|---------|
| `.iw/test/feedback.bats` | Add ENABLE_LIVE_API_TESTS gate to 3 tests, update skip messages |

## Testing Strategy

### Verification Steps
1. `unset LINEAR_API_TOKEN ENABLE_LIVE_API_TESTS && ./iw test e2e` → All tests pass/skip correctly
2. `export LINEAR_API_TOKEN=<real> && unset ENABLE_LIVE_API_TESTS && ./iw test e2e` → Live tests skip with message
3. `export LINEAR_API_TOKEN=<real> ENABLE_LIVE_API_TESTS=1 && ./iw test e2e` → Live tests run and create issues

### Regression Check
- All existing non-API tests continue to pass
- Help text tests unaffected
- Validation error tests unaffected

## Acceptance Criteria

- [ ] E2E tests pass when no environment variables are set
- [ ] E2E tests pass when only LINEAR_API_TOKEN is set (live tests skip)
- [ ] Skip messages clearly explain ENABLE_LIVE_API_TESTS requirement
- [ ] Live tests only run when BOTH LINEAR_API_TOKEN and ENABLE_LIVE_API_TESTS=1 are set
- [ ] No changes to non-API tests

## Notes

- This is a test-only change - no production code modifications
- Simple environment variable gate - minimal implementation complexity
- Skip message should be clear and actionable
