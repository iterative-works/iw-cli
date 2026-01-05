# Refactoring R1: Align GitLab IDs with TEAM-NNN format

**Phase:** 6
**Created:** 2026-01-05
**Status:** Planned

## Decision Summary

Phase 6 introduced GitLab-specific ID handling (`#123` format) that violates the design principle of uniform ID format across all trackers. GitHub was intentionally made to use `teamPrefix` so that `123` becomes `TEAM-123`, consistent with Linear and YouTrack. GitLab should follow the same pattern.

**Problems with current `#123` format:**
1. The `.team` extension method breaks (splits on `-` but `#123` has no dash)
2. Branch names are inconsistent (`123-desc` vs `TEAM-123-desc`)
3. Dual code paths complicate maintenance

## Current State

### IssueId.scala
```scala
// GitLab-specific patterns (to be REMOVED)
private val NumericPattern = """^[0-9]+$""".r
private val NumericBranchPattern = """^([0-9]+)(?:[_\-/].*)?$""".r

// GitLab-specific factory (to be REMOVED)
def forGitLab(number: Int): Either[String, IssueId]

// GitLab special case in parse() (to be REMOVED)
case Some(IssueTrackerType.GitLab) =>
  // For GitLab, accept bare numeric IDs
  trimmed match
    case NumericPattern() => Right(s"#$num")

// GitLab special case in fromBranch() (to be REMOVED)
case Some(IssueTrackerType.GitLab) =>
  branchName match
    case NumericBranchPattern(issueNum) => Right(s"#$num")
```

### issue.scala (and other commands)
```scala
// Current: Different handling for GitLab
val teamPrefix = if config.trackerType == IssueTrackerType.GitHub then
  config.teamPrefix
else
  None  // GitLab gets None, relies on special parsing
```

## Target State

### IssueId.scala
- **REMOVE** `NumericPattern` and `NumericBranchPattern`
- **REMOVE** `forGitLab()` factory method
- **REMOVE** `trackerType` parameter from `parse()` and `fromBranch()`
- All trackers use uniform `TEAM-NNN` format

### issue.scala (and other commands)
```scala
// Target: Same handling for GitHub and GitLab
val teamPrefix = config.trackerType match
  case IssueTrackerType.GitHub | IssueTrackerType.GitLab =>
    config.teamPrefix
  case _ => None
```

### Number extraction for API calls
```scala
// When calling GitLab API, extract number same as GitHub:
val issueNumber = issueId.value.split("-")(1)  // PROJ-123 -> 123
```

## Constraints

- **PRESERVE:** All existing GitHub/Linear/YouTrack functionality
- **PRESERVE:** GitLabClient API calls (they correctly use numbers)
- **PRESERVE:** Error messages for invalid formats
- **DO NOT TOUCH:** GitLabClient.scala (API layer is correct)
- **DO NOT TOUCH:** Configuration serialization (baseUrl, repository)

## Tasks

### Analysis
- [ ] [impl] Review all usages of `trackerType` parameter in IssueId
- [ ] [impl] List all commands that need teamPrefix handling update

### Tests First (TDD - RED)
- [ ] [impl] Update GitLab ID tests to expect `TEAM-NNN` format
- [ ] [impl] Add test: `IssueId.parse("123", Some("PROJ"))` returns `PROJ-123`
- [ ] [impl] Add test: `IssueId.fromBranch("PROJ-123-feature")` works for GitLab
- [ ] [impl] Verify `.team` extension returns correct team for GitLab IDs

### Implementation (GREEN)
- [ ] [impl] Remove `NumericPattern`, `NumericBranchPattern` from IssueId
- [ ] [impl] Remove `forGitLab()` factory method
- [ ] [impl] Remove `trackerType` parameter from `parse()` and `fromBranch()`
- [ ] [impl] Update `issue.scala` to pass teamPrefix for GitLab (like GitHub)
- [ ] [impl] Update `open.scala` to pass teamPrefix for GitLab
- [ ] [impl] Update `start.scala` to pass teamPrefix for GitLab
- [ ] [impl] Update `register.scala` to pass teamPrefix for GitLab
- [ ] [impl] Update `rm.scala` to pass teamPrefix for GitLab
- [ ] [impl] Update number extraction in fetchIssue for GitLab (same as GitHub)

### Cleanup
- [ ] [impl] Remove GitLab-specific tests that test `#123` format
- [ ] [impl] Update E2E tests in `gitlab-issue.bats` for TEAM-NNN format
- [ ] [impl] Run full test suite to verify no regressions

## Verification

- [ ] All 353+ existing tests pass
- [ ] `IssueId.parse("123", Some("PROJ"))` returns `Right("PROJ-123")`
- [ ] `IssueId.fromBranch("PROJ-123-feature")` returns `Right("PROJ-123")`
- [ ] `issueId.team` returns `"PROJ"` for GitLab IDs
- [ ] `iw issue 123` (with teamPrefix config) fetches GitLab issue correctly
- [ ] Branch `PROJ-123-feature` correctly infers issue ID

## Related Changes Already Made

1. **feedback.scala** - Reverted to always use GitHub for iw-cli feedback (not project tracker)
2. **gitlab-feedback.bats** - Deleted (tested incorrect behavior)

## Notes

- This refactoring simplifies the codebase by removing special cases
- GitLab config will now require `teamPrefix` (same as GitHub)
- Branch naming convention becomes uniform: `TEAM-NNN-description`
