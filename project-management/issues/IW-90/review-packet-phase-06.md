# Review Packet: Phase 6 - GitLab Issue ID Parsing and Validation

**Issue:** IW-90
**Phase:** 6 of 7
**Branch:** IW-90-phase-06

## Goals

Enable GitLab-specific issue ID parsing:
1. Parse numeric GitLab issue IDs from command arguments (e.g., `123`)
2. Infer issue IDs from GitLab branch names (e.g., `123-add-dark-mode`)
3. Store GitLab IDs in `#123` format for display

## Key Changes

### 1. IssueId.scala - Core Parsing Logic

**New patterns added:**
```scala
private val NumericPattern = """^[0-9]+$""".r
private val NumericBranchPattern = """^([0-9]+)(?:[_\-/].*)?$""".r
```

**Modified methods:**
- `parse(raw, defaultTeam, trackerType)` - Added `trackerType` parameter
  - When `trackerType == GitLab`: accepts bare numeric IDs, stores as `#123`
  - Other trackers: unchanged behavior (TEAM-NNN pattern)

- `fromBranch(branchName, trackerType)` - Added `trackerType` parameter
  - When `trackerType == GitLab`: extracts numeric prefix from branches
  - Supports `123-feature`, `123_feature`, `123/feature` patterns

**New factory method:**
```scala
def forGitLab(number: Int): Either[String, IssueId]
```

### 2. issue.scala - Command Integration

Updated to pass tracker type to parsing methods:
```scala
issueId <- IssueId.fromBranch(branch, Some(config.trackerType))
IssueId.parse(args.head, teamPrefix, Some(config.trackerType))
```

### 3. Other Commands Updated

- `open.scala` - Pass tracker type for branch inference
- `start.scala` - Pass tracker type for issue ID parsing
- `register.scala` - Pass tracker type for validation
- `rm.scala` - Pass tracker type for issue ID resolution

### 4. IssueSearchService.scala

Updated to use new `IssueId.parse` signature with tracker type.

## Test Summary

### New Tests in IssueIdTest.scala (11 tests)

- Parse accepts bare numeric ID for GitLab
- Parse accepts single-digit/large IDs
- Parse rejects negative/decimal/alphabetic for GitLab
- Parse trims whitespace for numeric IDs
- forGitLab factory creates valid format

### New Tests in IssueIdFromBranchTest.scala (6 tests)

- Extract from `123-feature` pattern
- Extract from `123_feature` pattern
- Extract from exact numeric `123`
- Extract single-digit from branch
- Reject non-numeric branches for GitLab

### Updated Tests in IssueSearchServiceTest.scala (2 tests)

- Fixed GitLab search tests to use numeric IDs instead of TEAM-NNN format

## API Changes

| Method | Before | After |
|--------|--------|-------|
| `IssueId.parse` | `(String, Option[String])` | `(String, Option[String], Option[IssueTrackerType])` |
| `IssueId.fromBranch` | `(String)` | `(String, Option[IssueTrackerType])` |

Both have default values for backward compatibility.

## Acceptance Criteria

1. ✅ `IssueId.parse("123", trackerType=GitLab)` returns `Right("#123")`
2. ✅ `IssueId.fromBranch("123-feature", trackerType=GitLab)` returns `Right("#123")`
3. ✅ Existing GitHub/Linear/YouTrack parsing unchanged
4. ✅ All unit tests pass

## Files Changed

| File | Changes |
|------|---------|
| `.iw/core/IssueId.scala` | Added GitLab patterns and tracker-aware parsing |
| `.iw/commands/issue.scala` | Pass tracker type to IssueId methods |
| `.iw/commands/open.scala` | Pass tracker type for branch inference |
| `.iw/commands/start.scala` | Pass tracker type for issue ID parsing |
| `.iw/commands/register.scala` | Pass tracker type for validation |
| `.iw/commands/rm.scala` | Pass tracker type for issue ID resolution |
| `.iw/core/IssueSearchService.scala` | Updated parse call signature |
| `.iw/core/test/IssueIdTest.scala` | Added 11 GitLab-specific tests |
| `.iw/core/test/IssueIdFromBranchTest.scala` | Added 6 GitLab branch tests |
| `.iw/core/test/IssueSearchServiceTest.scala` | Fixed GitLab tests to use numeric IDs |

## Review Checklist

- [ ] GitLab ID format (`#123`) is appropriate for display
- [ ] Backward compatibility maintained via default parameter values
- [ ] Error messages are helpful for GitLab-specific failures
- [ ] Branch patterns cover common GitLab naming conventions
- [ ] No regression in other tracker types

---

🤖 Generated with Claude Code
