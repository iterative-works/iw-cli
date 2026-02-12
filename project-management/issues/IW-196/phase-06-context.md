# Phase 6: Check grouping and filtering

## Goal

Add check categories, grouped display with section headers, and filter flags to `iw doctor`. System checks (git, config) are grouped under "Environment" and quality gate checks (scalafmt, scalafix, hooks, ci, docs) under "Project Quality Gates".

## Scope

### In Scope
- Add `category` field to `Check` case class (defaulting to "Environment" for backward compat)
- Group doctor output by category with section headers
- Add filter flags: `--quality`, `--env`
- Update all quality gate hook-doctor files to set category
- Update display logic in doctor.scala
- Unit tests for filtering logic
- E2E tests for grouped output and filtering

### Out of Scope
- Per-check filter flags (e.g., `--scalafmt`)
- Fix remediation (Phase 7)

## Dependencies

- Phases 0-5 (complete): All checks exist

## Approach

1. **Extend `Check` model** in `core/model/DoctorChecks.scala`:
   - Add `category: String = "Environment"` to `Check` case class
   - This is backward compatible — existing checks default to "Environment"

2. **Add filtering logic** in `core/model/DoctorChecks.scala`:
   - `filterByCategory(checks: List[Check], category: Option[String]): List[Check]`
   - `groupByCategory(results: List[(String, CheckResult, String)]): Map[String, List[(String, CheckResult)]]`

3. **Update quality gate hook-doctor files** to set `category = "Quality"`:
   - `scalafmt.hook-doctor.scala`
   - `scalafix.hook-doctor.scala`
   - `githooks.hook-doctor.scala`
   - `contributing.hook-doctor.scala`
   - `ci.hook-doctor.scala`

4. **Update `doctor.scala`**:
   - Parse `--quality` and `--env` flags
   - Group results by category
   - Display section headers ("Environment", "Project Quality Gates")
   - Exit code considers only displayed checks

5. **Tests**

## Files to Modify

- `.iw/core/model/DoctorChecks.scala` - Add category field and filtering
- `.iw/commands/doctor.scala` - Grouped display and flag parsing
- `.iw/commands/scalafmt.hook-doctor.scala` - Set category
- `.iw/commands/scalafix.hook-doctor.scala` - Set category
- `.iw/commands/githooks.hook-doctor.scala` - Set category
- `.iw/commands/contributing.hook-doctor.scala` - Set category
- `.iw/commands/ci.hook-doctor.scala` - Set category
- `.iw/core/test/DoctorChecksTest.scala` - Add filtering tests
- `.iw/test/doctor.bats` - Add E2E tests for grouping and filtering

## Testing Strategy

### Unit Tests
- Filter by "Quality" returns only quality checks
- Filter by "Environment" returns only env checks
- No filter returns all checks
- Category field defaults to "Environment"

### E2E Tests
- `iw doctor` shows grouped output with section headers
- `iw doctor --quality` shows only quality gate checks
- `iw doctor --env` shows only environment checks

## Acceptance Criteria

- [ ] `iw doctor` shows checks grouped by category with section headers
- [ ] `--quality` flag filters to quality gate checks only
- [ ] `--env` flag filters to environment checks only
- [ ] Exit code considers only displayed checks
- [ ] Existing tests continue to pass
- [ ] E2E tests validate grouping and filtering
