# Phase 4: Contributor documentation check

## Goal

Add a quality gate check for contributor documentation to `iw doctor`. The check verifies that a project has a `CONTRIBUTING.md` file with key sections covering CI checks, git hook installation, and running checks locally.

## Scope

### In Scope
- Check for `CONTRIBUTING.md` file existence
- Check for key documentation sections (CI, hooks, local checks)
- Unit tests and E2E tests

### Out of Scope
- Validating section content quality
- Check grouping/filtering (Phase 6)

## Dependencies

- Phase 0 (complete): Check types in `core/model/`

## Approach

1. **Pure check functions** in `core/model/ContributingChecks.scala`:
   - `checkFileExistsWith(config, fileExists)`: CONTRIBUTING.md exists
   - `checkSectionsCoveredWith(config, readFile)`: Key topics are documented

2. **Hook-doctor file** at `.iw/commands/contributing.hook-doctor.scala`

3. **Section detection** uses case-insensitive keyword matching on section headings and content:
   - CI/continuous integration/pipeline/workflow
   - Hook/pre-commit/pre-push
   - Local/locally/running checks

Missing documentation is a warning (not error) since the project can function without it.

## Files to Create

- `.iw/core/model/ContributingChecks.scala`
- `.iw/commands/contributing.hook-doctor.scala`
- `.iw/core/test/ContributingChecksTest.scala`

## Files to Modify

- `.iw/test/doctor.bats` - Add E2E tests

## Testing Strategy

### Unit Tests
- File exists → Success
- File missing → Warning with hint
- File has all key sections → Success
- File missing some sections → WarningWithHint listing missing topics
- File unreadable → Error

### E2E Tests
- Doctor shows contributing checks

## Acceptance Criteria

- [ ] `iw doctor` reports CONTRIBUTING.md presence and section coverage
- [ ] Missing doc is a warning (not error)
- [ ] Section checks are keyword-based
- [ ] Unit tests cover all scenarios
- [ ] E2E test validates output
