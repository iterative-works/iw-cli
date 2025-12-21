# Phase 2 Context: Explicit live API opt-in mechanism

**Issue:** IWLE-131
**Phase:** 2 of 3
**Estimated:** 1-2 hours

## Goals

Enhance the live API test opt-in mechanism with clear warning messages and documentation updates. When developers enable live API tests, they should see a clear warning that real Linear issues will be created.

## Scope

### In Scope
- Add warning message when `ENABLE_LIVE_API_TESTS=1` is set (visible before tests run)
- Update test file header comments with clearer documentation
- Add README section explaining test modes and how to run them
- Update skip messages to be more actionable

### Out of Scope
- Mock-based unit tests (Phase 3)
- CI configuration (IWLE-114)
- Changing the skip gate logic (already done in Phase 1)

## Dependencies

- **Phase 1 (Complete):** Skip gate mechanism already in place
- Phase 2 builds on Phase 1's foundation

## Technical Approach

### Warning Message Implementation

When both `LINEAR_API_TOKEN` and `ENABLE_LIVE_API_TESTS=1` are set, BATS `setup_file()` should output a warning:

```bash
setup_file() {
    if [ -n "$LINEAR_API_TOKEN" ] && [ -n "$ENABLE_LIVE_API_TESTS" ]; then
        echo "⚠️  WARNING: Live API tests enabled - real Linear issues will be created" >&2
    fi
}
```

This runs once at the start of the test file, making the warning visible before any tests execute.

### Documentation Updates

1. **Test file header comments** - Already updated in Phase 1, may need enhancement
2. **README.md** - Add new "Testing" section explaining:
   - How to run unit tests: `./iw test unit`
   - How to run E2E tests: `./iw test e2e`
   - How to run live API tests: `ENABLE_LIVE_API_TESTS=1 LINEAR_API_TOKEN=xxx ./iw test e2e`
   - Environment variables explained

### Files to Modify

| File | Changes |
|------|---------|
| `.iw/test/feedback.bats` | Add `setup_file()` with warning message |
| `README.md` | Add Testing section with commands and environment variables |

## Testing Strategy

### Verification Steps

1. **Warning message appears:**
   ```bash
   export LINEAR_API_TOKEN=<real> ENABLE_LIVE_API_TESTS=1
   ./iw test e2e 2>&1 | grep -i warning
   # Should see: "⚠️  WARNING: Live API tests enabled..."
   ```

2. **No warning when disabled:**
   ```bash
   export LINEAR_API_TOKEN=<real>
   unset ENABLE_LIVE_API_TESTS
   ./iw test e2e 2>&1 | grep -i warning
   # Should NOT see warning (tests skip instead)
   ```

3. **README is accurate:**
   - Verify commands in README actually work
   - Verify environment variable explanations are correct

### Regression Check
- All existing tests continue to pass
- Phase 1 skip behavior unchanged
- Help text tests unaffected

## Acceptance Criteria

- [ ] Warning message displayed when ENABLE_LIVE_API_TESTS=1 is set
- [ ] Warning is visible before tests run (uses setup_file, outputs to stderr)
- [ ] No warning when ENABLE_LIVE_API_TESTS is not set
- [ ] README has Testing section explaining all test modes
- [ ] README documents both environment variables
- [ ] Skip messages remain clear and actionable

## Notes

- This is a test-only change with documentation - no production code modifications
- Warning goes to stderr so it doesn't interfere with BATS test output parsing
- `setup_file()` runs once per file, not per test, so warning appears only once
- README section should be concise and practical
