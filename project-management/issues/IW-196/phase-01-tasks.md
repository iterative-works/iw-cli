# Phase 1 Tasks: Scalafmt configuration check

## Tests First (TDD)

- [ ] [test] Create `ScalafmtChecksTest.scala` with test: config file exists with version → Success
- [ ] [test] Add test: config file missing → Error with hint
- [ ] [test] Add test: config file exists but no version key → WarningWithHint
- [ ] [test] Run tests to confirm they fail (no implementation yet)

## Implementation

- [ ] [impl] Create `ScalafmtChecks.scala` in `core/model/` with pure check functions
- [ ] [impl] Implement `checkConfigExistsWith` - file existence check with injected `fileExists`
- [ ] [impl] Implement `checkVersionExistsWith` - version key check with injected `readFile`
- [ ] [impl] Add wrapper functions `checkConfigExists` and `checkVersionExists` using real `os` operations
- [ ] [impl] Run unit tests to confirm they pass

## Integration

- [ ] [impl] Create `scalafmt.hook-doctor.scala` in `.iw/commands/` exposing Check values
- [ ] [test] Add E2E test to `doctor.bats`: Scalafmt check appears when `.scalafmt.conf` exists
- [ ] [test] Add E2E test: Scalafmt check shows error when `.scalafmt.conf` missing

## Verification

- [ ] [verify] Run full unit test suite: `./iw test unit`
- [ ] [verify] Run E2E tests: `./iw test e2e` (or just `bats .iw/test/doctor.bats`)
- [ ] [verify] Manual test: run `iw doctor` in project root and verify Scalafmt checks appear
