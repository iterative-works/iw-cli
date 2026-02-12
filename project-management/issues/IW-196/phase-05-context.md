# Phase 5: CI workflow check

## Goal

Add a quality gate check for CI workflow configuration to `iw doctor`. The check verifies that a project has a CI workflow file. The CI platform is detected from the tracker type in the project config (GitHub → GitHub Actions, GitLab → GitLab CI).

## Scope

### In Scope
- Detect CI platform from tracker type
- Check for CI workflow file existence
- Support GitHub Actions (`.github/workflows/ci.yml`) and GitLab CI (`.gitlab-ci.yml`)
- Unit tests and E2E tests

### Out of Scope
- Validating CI file content (that's what `--fix` is for)
- Other CI platforms (Jenkins, etc.)

## Dependencies

- Phase 0 (complete): Check types in `core/model/`

## Approach

1. **Pure check functions** in `core/model/CIChecks.scala`:
   - `checkWorkflowExistsWith(config, fileExists)`: CI workflow file exists based on tracker type

2. **Hook-doctor file** at `.iw/commands/ci.hook-doctor.scala`

3. **CI platform detection logic:**
   - `IssueTrackerType.GitHub` → check `.github/workflows/ci.yml`
   - `IssueTrackerType.GitLab` → check `.gitlab-ci.yml`
   - Other tracker types → check both locations, succeed if either exists
   - If neither platform detected and no files found → `Warning` (not error)

## Files to Create

- `.iw/core/model/CIChecks.scala`
- `.iw/commands/ci.hook-doctor.scala`
- `.iw/core/test/CIChecksTest.scala`

## Files to Modify

- `.iw/test/doctor.bats` - Add E2E tests

## Testing Strategy

### Unit Tests
- GitHub tracker + `.github/workflows/ci.yml` exists → Success
- GitHub tracker + file missing → Error with hint
- GitLab tracker + `.gitlab-ci.yml` exists → Success
- GitLab tracker + file missing → Error with hint
- Other tracker + either CI file exists → Success
- Other tracker + no CI file → Warning

### E2E Tests
- Doctor shows CI workflow check

## Acceptance Criteria

- [ ] `iw doctor` reports CI workflow file presence
- [ ] Detects CI platform from tracker type
- [ ] Missing CI file is an error for known platforms
- [ ] Unit tests cover all scenarios
- [ ] E2E test validates output
