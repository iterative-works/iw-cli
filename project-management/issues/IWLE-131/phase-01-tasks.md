# Phase 1 Tasks: E2E tests skip real API calls by default

**Issue:** IWLE-131
**Phase:** 1 of 3
**Status:** Complete

## Implementation Tasks

### [impl] Core Implementation

- [x] [impl] Update skip condition in "feedback creates issue successfully" test to require ENABLE_LIVE_API_TESTS
- [x] [impl] Update skip condition in "feedback with description creates issue" test to require ENABLE_LIVE_API_TESTS
- [x] [impl] Update skip condition in "feedback with bug type creates issue" test to require ENABLE_LIVE_API_TESTS

### [test] Verification

- [x] [test] Verify all tests pass when no env vars set (unset LINEAR_API_TOKEN ENABLE_LIVE_API_TESTS)

## Detailed Implementation

### Task 1-3: Update Skip Conditions

For each of the three live API tests, change:
```bash
if [ -z "$LINEAR_API_TOKEN" ]; then
    skip "LINEAR_API_TOKEN not set, skipping live API test"
fi
```

To:
```bash
if [ -z "$LINEAR_API_TOKEN" ] || [ -z "$ENABLE_LIVE_API_TESTS" ]; then
    skip "Live API tests disabled. Set LINEAR_API_TOKEN and ENABLE_LIVE_API_TESTS=1 to enable."
fi
```

### Task 4: Verification

Run tests in clean environment to verify they pass/skip correctly:
```bash
unset LINEAR_API_TOKEN ENABLE_LIVE_API_TESTS
./iw test e2e
```

Expected: All 8 tests run, 3 live API tests skip with new message.

## Notes

- This is a simple refactoring task - changing skip logic in 3 tests
- No structural changes needed
- Skip message should be informative and actionable
