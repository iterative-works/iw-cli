# Phase 3 Tasks: Git hooks check

## Tests First (TDD)

- [x] [test] Create `GitHooksChecksTest.scala` with test: `.git-hooks/` dir exists → Success
- [x] [test] Add test: `.git-hooks/` dir missing → Error with hint
- [x] [test] Add test: pre-commit and pre-push exist → Success
- [x] [test] Add test: hook files missing → Error listing which ones
- [x] [test] Add test: hooks installed (symlinked correctly) → Success
- [x] [test] Add test: hooks not installed → WarningWithHint

## Implementation

- [x] [impl] Create `GitHooksChecks.scala` in `core/model/`
- [x] [impl] Implement `checkHooksDirExistsWith`
- [x] [impl] Implement `checkHookFilesExistWith`
- [x] [impl] Implement `checkHooksInstalledWith`
- [x] [impl] Add wrapper functions using real filesystem operations
- [x] [impl] Run unit tests to confirm they pass

## Integration

- [x] [impl] Create `githooks.hook-doctor.scala` in `.iw/commands/`
- [x] [test] Add E2E tests to `doctor.bats`

## Verification

- [x] [verify] Run full unit test suite: `./iw test unit`
- [x] [verify] Run E2E tests: `bats .iw/test/doctor.bats`
