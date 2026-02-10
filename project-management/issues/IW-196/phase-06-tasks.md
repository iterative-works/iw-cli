# Phase 6 Tasks: Check grouping and filtering

## Tests First (TDD)

- [ ] [test] Add unit tests for category field default value
- [ ] [test] Add unit tests for `filterByCategory`
- [ ] [test] Add unit tests for `runAll` preserving category in results

## Implementation - Model

- [ ] [impl] Add `category: String = "Environment"` to `Check` case class
- [ ] [impl] Add `filterByCategory` to `DoctorChecks` object
- [ ] [impl] Update `runAll` to include category in results
- [ ] [impl] Run unit tests

## Implementation - Hook Doctors

- [ ] [impl] Update `scalafmt.hook-doctor.scala` - set category to "Quality"
- [ ] [impl] Update `scalafix.hook-doctor.scala` - set category to "Quality"
- [ ] [impl] Update `githooks.hook-doctor.scala` - set category to "Quality"
- [ ] [impl] Update `contributing.hook-doctor.scala` - set category to "Quality"
- [ ] [impl] Update `ci.hook-doctor.scala` - set category to "Quality"

## Implementation - Doctor Command

- [ ] [impl] Parse `--quality` and `--env` flags in `doctor.scala`
- [ ] [impl] Implement grouped display with section headers
- [ ] [impl] Exit code considers only displayed checks

## E2E Tests

- [ ] [test] Add E2E test: grouped output with section headers
- [ ] [test] Add E2E test: `--quality` filter
- [ ] [test] Add E2E test: `--env` filter

## Verification

- [ ] [verify] Run full test suite: `./iw test unit`
- [ ] [verify] Run E2E tests: `bats .iw/test/doctor.bats`
- [ ] [verify] Existing E2E tests still pass
