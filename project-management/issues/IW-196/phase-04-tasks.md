# Phase 4 Tasks: Contributor documentation check

## Tests First (TDD)

- [x] [test] Create `ContributingChecksTest.scala` with tests for file existence and section coverage
- [x] [test] Run tests to confirm they fail

## Implementation

- [x] [impl] Create `ContributingChecks.scala` in `core/model/`
- [x] [impl] Implement `checkFileExistsWith` and `checkSectionsCoveredWith`
- [x] [impl] Add wrapper functions
- [x] [impl] Run unit tests to confirm they pass

## Integration

- [x] [impl] Create `contributing.hook-doctor.scala` in `.iw/commands/`
- [x] [test] Add E2E tests to `doctor.bats`

## Verification

- [x] [verify] Run full test suite: `./iw test unit`
- [x] [verify] Run E2E tests: `bats .iw/test/doctor.bats`
