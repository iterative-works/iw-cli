# Phase 5 Context: Validate development mode isolation

**Issue:** IW-82
**Phase:** 5 - Validate development mode isolation
**Created:** 2026-01-23

## Goals

This phase validates that development mode (`--dev` flag) provides complete isolation from production data. The goal is to ensure:

1. Production state file is never modified when using `--dev`
2. Production config file is never modified when using `--dev`
3. Operations in dev mode (create worktree, unregister, etc.) don't affect production

This is a **validation phase** - no new implementation, just E2E tests to verify the isolation guarantees from Phases 1-4.

## Scope

### In Scope
- E2E tests verifying state file isolation
- E2E tests verifying config file isolation
- Documentation of isolation guarantees in CLI help

### Out of Scope
- New features or functionality
- Unit tests (validation is at E2E level)
- Changes to existing implementation (unless bugs found)

## Dependencies

From previous phases:
- Phase 1: `--state-path` flag for custom state file
- Phase 2: `--sample-data` flag for loading fixtures
- Phase 4: `--dev` flag combining isolated state + sample data + temp directory

## Technical Approach

### E2E Test Strategy

Create BATS tests that:

1. **Set up baseline production state**
   - Create a production state file with known content
   - Create a production config file with known content
   - Record checksums/timestamps of both files

2. **Run dev mode and perform mutations**
   - Start server with `--dev` flag
   - Use API to create a worktree registration
   - Use API to unregister a sample worktree
   - Stop the server

3. **Verify production unchanged**
   - Compare state file checksum/content against baseline
   - Compare config file checksum/content against baseline
   - Assert byte-for-byte identical

### Test Location

E2E tests go in `tests/e2e/` directory using BATS framework (existing pattern).

### API Endpoints for Testing

From existing infrastructure:
- `POST /api/worktrees` - Register a worktree (will be blocked without real path, but exercise the route)
- State mutations happen via server endpoints, not direct file access

### Simplification

Since the server uses isolated paths when `--dev` is passed, the simplest validation is:
1. Check that `--dev` creates temp directory (not production paths)
2. Check that production paths are never touched
3. This can be done by monitoring file modification times

## Files to Modify

- `tests/e2e/dashboard-dev-mode.bats` (NEW) - E2E tests for dev mode isolation
- `.iw/commands/dashboard.scala` - Add isolation guarantees to help text (optional)

## Testing Strategy

### E2E Tests (Primary)

Test: "Production state unchanged after dev mode operations"
```bash
# 1. Create baseline production state
PROD_STATE="$HOME/.local/share/iw/server/state.json"
BASELINE_HASH=$(sha256sum "$PROD_STATE" | cut -d' ' -f1)

# 2. Run dev mode, perform operations
./iw dashboard --dev &
sleep 2
# ... hit API endpoints ...
kill %1

# 3. Verify unchanged
AFTER_HASH=$(sha256sum "$PROD_STATE" | cut -d' ' -f1)
assert_equal "$BASELINE_HASH" "$AFTER_HASH"
```

Test: "Dev mode uses temp directory"
```bash
# Capture dev mode output, verify temp paths are used
OUTPUT=$(./iw dashboard --dev 2>&1 &)
assert_output --partial "/tmp/iw-dev-"
```

Test: "Dev mode creates isolated config"
```bash
# Verify dev mode config is in temp dir, not production
# Production config remains untouched
```

### Manual Verification Checklist

- [ ] `./iw dashboard --dev` starts without touching production state
- [ ] Sample worktrees visible in dashboard
- [ ] DEV MODE banner visible
- [ ] After stopping server, production state file unchanged
- [ ] Dev temp directory contains state.json and config.json

## Acceptance Criteria

From Story 5 Gherkin scenario:
- [ ] E2E test verifies production state isolation
- [ ] Test creates baseline production state
- [ ] Test runs dev mode and performs mutations
- [ ] Test verifies production state byte-for-byte identical
- [ ] Documentation clearly states isolation guarantees

## Risks and Mitigations

**Risk:** Production state might not exist on fresh system
**Mitigation:** Test creates production state if missing, or skips gracefully

**Risk:** Server might fail to start, masking isolation issues
**Mitigation:** Test server startup success before proceeding

## Notes

- This is primarily a safety net / confidence builder
- If tests pass, the isolation guarantees from Phases 1-4 are validated
- If tests fail, we have a bug that needs fixing before release
