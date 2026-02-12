# Phase 5 Tasks: CI workflow check

## Tests First (TDD)

- [x] [test] Create `CIChecksTest.scala` with tests for CI workflow detection
- [x] [test] Run tests to confirm they fail

## Implementation

- [x] [impl] Create `CIChecks.scala` in `core/model/`
- [x] [impl] Implement `checkWorkflowExistsWith` with tracker-type-based platform detection
- [x] [impl] Add wrapper function
- [x] [impl] Run unit tests to confirm they pass

## Integration

- [x] [impl] Create `ci.hook-doctor.scala` in `.iw/commands/`
- [x] [test] Add E2E tests to `doctor.bats`

## Verification

- [x] [verify] Run full test suite: `./iw test unit`
- [x] [verify] Run E2E tests: `bats .iw/test/doctor.bats`
