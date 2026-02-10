# Phase 4 Tasks: Contributor documentation check

## Tests First (TDD)

- [ ] [test] Create `ContributingChecksTest.scala` with tests for file existence and section coverage
- [ ] [test] Run tests to confirm they fail

## Implementation

- [ ] [impl] Create `ContributingChecks.scala` in `core/model/`
- [ ] [impl] Implement `checkFileExistsWith` and `checkSectionsCoveredWith`
- [ ] [impl] Add wrapper functions
- [ ] [impl] Run unit tests to confirm they pass

## Integration

- [ ] [impl] Create `contributing.hook-doctor.scala` in `.iw/commands/`
- [ ] [test] Add E2E tests to `doctor.bats`

## Verification

- [ ] [verify] Run full test suite: `./iw test unit`
- [ ] [verify] Run E2E tests: `bats .iw/test/doctor.bats`
