# Phase 6 Tasks: Check grouping and filtering

## Tests First (TDD)

- [x] [test] Add unit tests for category field default value
- [x] [test] Add unit tests for `filterByCategory`
- [x] [test] Add unit tests for `runAll` preserving category in results

## Implementation - Model

- [x] [impl] Add `category: String = "Environment"` to `Check` case class
- [x] [impl] Add `filterByCategory` to `DoctorChecks` object
- [x] [impl] Update `runAll` to include category in results
- [x] [impl] Run unit tests

## Implementation - Hook Doctors

- [x] [impl] Update `scalafmt.hook-doctor.scala` - set category to "Quality"
- [x] [impl] Update `scalafix.hook-doctor.scala` - set category to "Quality"
- [x] [impl] Update `githooks.hook-doctor.scala` - set category to "Quality"
- [x] [impl] Update `contributing.hook-doctor.scala` - set category to "Quality"
- [x] [impl] Update `ci.hook-doctor.scala` - set category to "Quality"

## Implementation - Doctor Command

- [x] [impl] Parse `--quality` and `--env` flags in `doctor.scala`
- [x] [impl] Implement grouped display with section headers
- [x] [impl] Exit code considers only displayed checks

## E2E Tests

- [x] [test] Add E2E test: grouped output with section headers
- [x] [test] Add E2E test: `--quality` filter
- [x] [test] Add E2E test: `--env` filter

## Verification

- [x] [verify] Run full test suite: `./iw test unit`
- [x] [verify] Run E2E tests: `bats .iw/test/doctor.bats`
- [x] [verify] Existing E2E tests still pass
