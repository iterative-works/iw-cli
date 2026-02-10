# Phase 3 Tasks: Git hooks check

## Tests First (TDD)

- [ ] [test] Create `GitHooksChecksTest.scala` with test: `.git-hooks/` dir exists → Success
- [ ] [test] Add test: `.git-hooks/` dir missing → Error with hint
- [ ] [test] Add test: pre-commit and pre-push exist → Success
- [ ] [test] Add test: hook files missing → Error listing which ones
- [ ] [test] Add test: hooks installed (symlinked correctly) → Success
- [ ] [test] Add test: hooks not installed → WarningWithHint

## Implementation

- [ ] [impl] Create `GitHooksChecks.scala` in `core/model/`
- [ ] [impl] Implement `checkHooksDirExistsWith`
- [ ] [impl] Implement `checkHookFilesExistWith`
- [ ] [impl] Implement `checkHooksInstalledWith`
- [ ] [impl] Add wrapper functions using real filesystem operations
- [ ] [impl] Run unit tests to confirm they pass

## Integration

- [ ] [impl] Create `githooks.hook-doctor.scala` in `.iw/commands/`
- [ ] [test] Add E2E tests to `doctor.bats`

## Verification

- [ ] [verify] Run full unit test suite: `./iw test unit`
- [ ] [verify] Run E2E tests: `bats .iw/test/doctor.bats`
