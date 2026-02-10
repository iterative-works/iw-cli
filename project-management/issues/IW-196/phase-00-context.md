# Phase 0: Move Check types from dashboard/ to model/

## Goal

Move `Check`, `CheckResult`, and `DoctorChecks` from `core/dashboard/DoctorChecks.scala` to `core/model/DoctorChecks.scala`. Move `GitHubHookDoctor` from `core/dashboard/GitHubHookDoctor.scala` to `core/model/GitHubHookDoctor.scala`. Update all imports across the codebase.

This fixes an architectural violation: commands should not import from `dashboard/` (per `core/CLAUDE.md`), but `doctor.scala` and all hook-doctor commands currently do. These types are pure domain types with no dashboard dependencies and belong in `model/`.

## Scope

### In Scope
- Move `DoctorChecks.scala` (containing `Check`, `CheckResult`, `DoctorChecks`) from `core/dashboard/` to `core/model/`
- Move `GitHubHookDoctor.scala` from `core/dashboard/` to `core/model/`
- Update package declarations in moved files: `iw.core.dashboard` → `iw.core.model`
- Update all import statements across commands and tests

### Out of Scope
- Adding new check types or quality gate checks (Phase 1+)
- Changing any logic or API of the moved types
- Moving any other dashboard types

## Dependencies

None. This is the foundational refactoring that subsequent phases build on.

## Approach

1. Copy `DoctorChecks.scala` to `core/model/`, change package to `iw.core.model`
2. Copy `GitHubHookDoctor.scala` to `core/model/`, change package to `iw.core.model`
3. Update all imports in commands and tests
4. Delete the original files from `core/dashboard/`
5. Run tests to verify no breakage

## Files to Modify

### Move (create in new location, delete old):
- `.iw/core/dashboard/DoctorChecks.scala` → `.iw/core/model/DoctorChecks.scala`
- `.iw/core/dashboard/GitHubHookDoctor.scala` → `.iw/core/model/GitHubHookDoctor.scala`

### Update imports (dashboard → model for Check/CheckResult/DoctorChecks):
- `.iw/commands/doctor.scala` (line 10)
- `.iw/commands/github.hook-doctor.scala` (line 7)
- `.iw/commands/start.hook-doctor.scala` (line 7)
- `.iw/commands/issue.hook-doctor.scala` (line 7)
- `.iw/commands/legacy-branches.hook-doctor.scala` (line 7)

### Update imports (dashboard → model for Check only):
- `.iw/core/test/CaskServerTest.scala` (line 13)
- `.iw/core/test/SampleDataTest.scala` (line 13)
- `.iw/core/test/WorktreeListViewTest.scala` (line 9)
- `.iw/core/test/SearchResultsViewTest.scala` (line 9)
- `.iw/core/test/ProcessManagerTest.scala` (line 8)
- `.iw/core/test/CreateWorktreeModalTest.scala` (line 7)
- `.iw/core/test/CreationErrorViewTest.scala` (line 8)
- `.iw/core/test/CreationSuccessViewTest.scala` (line 8)

### Update imports (dashboard → model for CheckResult/GitHubHookDoctor/DoctorChecks):
- `.iw/core/test/DoctorChecksTest.scala` (lines 5-6)
- `.iw/core/test/GitHubHookDoctorTest.scala` (lines 5-6, 8)

### Dashboard-internal files that reference Check (re-export or update):
- Any dashboard files that use `Check` type will need to import from `iw.core.model` instead

## Testing Strategy

- All existing unit tests (`DoctorChecksTest.scala`, `GitHubHookDoctorTest.scala`) must pass unchanged (only imports change)
- All existing E2E tests (`doctor.bats`) must pass unchanged
- Run full test suite to catch any missed import updates

## Acceptance Criteria

- [ ] `Check`, `CheckResult`, `DoctorChecks` live in `iw.core.model` package
- [ ] `GitHubHookDoctor` lives in `iw.core.model` package
- [ ] No file imports `Check`, `CheckResult`, or `DoctorChecks` from `iw.core.dashboard`
- [ ] No file imports `GitHubHookDoctor` from `iw.core.dashboard`
- [ ] All unit tests pass
- [ ] All E2E tests pass
- [ ] No compilation warnings introduced
