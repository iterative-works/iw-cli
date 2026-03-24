# Phase 2: E2E tests — add bare remote to setup, test push behavior

## Goals

Update `phase-start.bats` so all existing tests work with the new push-before-branch behavior, and add new tests verifying the push actually happens.

## Scope

- **In scope:** Update test setup to include a bare remote; add new push-related test cases
- **Out of scope:** Unit tests (no pure domain logic changed), changes to production code

## Dependencies

- Phase 1 merged: `phase-start.scala` now calls `GitAdapter.push` before `createAndCheckoutBranch`

## Approach

1. Update `setup()` to create a bare remote and configure `origin` so push works
2. Fix all existing tests that call `phase-start` (they now need a remote)
3. Add test: phase-start pushes feature branch commits to origin before creating sub-branch
4. Add test: phase-start fails gracefully when push fails (no remote)

## Files to Modify

- `.iw/test/phase-start.bats` — update setup, fix existing tests, add new tests

## Testing Strategy

- Run `./iw test e2e` to verify all BATS tests pass (including the modified ones)

## Acceptance Criteria

- [ ] All existing `phase-start.bats` tests pass with the bare remote setup
- [ ] New test verifies feature branch is pushed to origin before sub-branch creation
- [ ] New test verifies graceful failure when push fails
