# Phase 3: Git hooks check

## Goal

Add quality gate checks for git hooks to `iw doctor`. The checks verify that a project has a `.git-hooks/` directory with executable pre-commit and pre-push hooks, and that these hooks are installed (symlinked) into the git hooks directory.

## Scope

### In Scope
- Check for `.git-hooks/` directory existence
- Check for pre-commit and pre-push hook files
- Check that hook files are executable
- Check that hooks are installed in the git hooks directory
- Handle worktrees (use `git rev-parse --git-dir` to find the correct hooks dir)
- Unit tests and E2E tests

### Out of Scope
- Validating hook content
- Installing hooks automatically (that's Phase 7 fix)
- Check grouping/filtering (Phase 6)

## Dependencies

- Phase 0 (complete): Check types in `core/model/`
- Phase 1 (complete): Pattern established

## Approach

Follow the established pattern with dependency injection for testability.

1. **Pure check functions** in `core/model/GitHooksChecks.scala`:
   - `checkHooksDirExistsWith(config, fileExists)`: `.git-hooks/` directory exists
   - `checkHookFilesExistWith(config, fileExists)`: pre-commit and pre-push files exist in `.git-hooks/`
   - `checkHooksInstalledWith(config, getGitDir, isSymlinkedTo)`: hooks are symlinked into git hooks dir

2. **Hook-doctor file** at `.iw/commands/githooks.hook-doctor.scala`

3. **Unit tests** in `.iw/core/test/GitHooksChecksTest.scala`

4. **E2E test** additions to `.iw/test/doctor.bats`

## Files to Create

- `.iw/core/model/GitHooksChecks.scala`
- `.iw/commands/githooks.hook-doctor.scala`
- `.iw/core/test/GitHooksChecksTest.scala`

## Files to Modify

- `.iw/test/doctor.bats` - Add E2E tests

## Testing Strategy

### Unit Tests
- `.git-hooks/` dir exists → `Success`
- `.git-hooks/` dir missing → `Error` with hint
- pre-commit and pre-push exist → `Success`
- pre-commit missing → `Error` listing missing hooks
- Hooks installed (symlinked) → `Success`
- Hooks not installed → `WarningWithHint` with installation hint
- Git dir resolution for worktrees

### E2E Tests
- Doctor shows git hooks checks
- Checks handle missing `.git-hooks/` directory

## Acceptance Criteria

- [ ] `iw doctor` reports git hooks directory, hook files, and installation status
- [ ] Handles both normal repos and worktrees
- [ ] Checks are pure functions with injected dependencies
- [ ] Unit tests cover all scenarios
- [ ] E2E test validates output
