# Phase 5 Tasks: CI workflow check

## Tests First (TDD)

- [ ] [test] Create `CIChecksTest.scala` with tests for CI workflow detection
- [ ] [test] Run tests to confirm they fail

## Implementation

- [ ] [impl] Create `CIChecks.scala` in `core/model/`
- [ ] [impl] Implement `checkWorkflowExistsWith` with tracker-type-based platform detection
- [ ] [impl] Add wrapper function
- [ ] [impl] Run unit tests to confirm they pass

## Integration

- [ ] [impl] Create `ci.hook-doctor.scala` in `.iw/commands/`
- [ ] [test] Add E2E tests to `doctor.bats`

## Verification

- [ ] [verify] Run full test suite: `./iw test unit`
- [ ] [verify] Run E2E tests: `bats .iw/test/doctor.bats`
