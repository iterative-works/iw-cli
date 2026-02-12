# Phase 1 Tasks: Scalafmt configuration check

## Tests First (TDD)

- [x] [test] Create `ScalafmtChecksTest.scala` with test: config file exists with version → Success
- [x] [test] Add test: config file missing → Error with hint
- [x] [test] Add test: config file exists but no version key → WarningWithHint
- [x] [test] Run tests to confirm they fail (no implementation yet)

## Implementation

- [x] [impl] Create `ScalafmtChecks.scala` in `core/model/` with pure check functions
- [x] [impl] Implement `checkConfigExistsWith` - file existence check with injected `fileExists`
- [x] [impl] Implement `checkVersionExistsWith` - version key check with injected `readFile`
- [x] [impl] Add wrapper functions `checkConfigExists` and `checkVersionExists` using real `os` operations
- [x] [impl] Run unit tests to confirm they pass

## Integration

- [x] [impl] Create `scalafmt.hook-doctor.scala` in `.iw/commands/` exposing Check values
- [x] [test] Add E2E test to `doctor.bats`: Scalafmt check appears when `.scalafmt.conf` exists
- [x] [test] Add E2E test: Scalafmt check shows error when `.scalafmt.conf` missing

## Verification

- [x] [verify] Run full unit test suite: `./iw test unit`
- [x] [verify] Run E2E tests: `./iw test e2e` (or just `bats .iw/test/doctor.bats`)
- [x] [verify] Manual test: run `iw doctor` in project root and verify Scalafmt checks appear
