# Phase 6 Tasks: GitLab issue ID parsing and validation

**Issue:** IW-90
**Phase:** 6 of 7
**Status:** Complete

## Context

GitLab uses simple numeric issue IDs (e.g., `123`) unlike other trackers that use `TEAM-NNN` format.
Current `IssueId.parse` and `IssueId.fromBranch` only accept `TEAM-NNN` patterns.

## Implementation Tasks

### Setup

- [x] [impl] [reviewed] Tests added to existing IssueIdTest.scala and IssueIdFromBranchTest.scala

### Tests - GitLab Numeric ID Parsing

- [x] [impl] [reviewed] Test IssueId.parse accepts bare numeric ID "123" for GitLab tracker
- [x] [impl] [reviewed] Test IssueId.parse accepts single-digit ID "1" for GitLab
- [x] [impl] [reviewed] Test IssueId.parse accepts large ID "99999" for GitLab
- [x] [impl] [reviewed] Test IssueId.parse rejects invalid formats for GitLab (negative, decimal, alpha)
- [x] [impl] [reviewed] Test IssueId.parse stores numeric ID as "#123" format for GitLab

### Tests - GitLab Branch Extraction

- [x] [impl] [reviewed] Test IssueId.fromBranch extracts from "123-feature" for GitLab
- [x] [impl] [reviewed] Test IssueId.fromBranch extracts from "123_feature" for GitLab (underscore)
- [x] [impl] [reviewed] Test IssueId.fromBranch extracts from "123" (exact match) for GitLab
- [x] [impl] [reviewed] Test IssueId.fromBranch rejects non-numeric prefix for GitLab ("main", "feature-x")

### Implementation - Tracker-Aware Parsing

- [x] [impl] [reviewed] Add IssueId.forGitLab factory method that accepts numeric ID
- [x] [impl] [reviewed] Modify IssueId.parse to accept tracker type parameter
- [x] [impl] [reviewed] Modify IssueId.fromBranch to accept tracker type parameter
- [x] [impl] [reviewed] Update issue.scala command to pass tracker type to parse/fromBranch

### Integration

- [x] [impl] [reviewed] Update existing tests to pass tracker type where needed
- [x] [impl] [reviewed] Run full test suite to verify no regressions
- [ ] [impl] Manual verification with `iw issue` on GitLab-configured project (Phase 7)
- [x] [impl] [reviewed] Refactoring R1: Align GitLab IDs with TEAM-NNN format

## Acceptance Criteria

1. ✅ `IssueId.parse("123", trackerType=GitLab)` returns valid ID
2. ✅ `IssueId.fromBranch("123-feature", trackerType=GitLab)` extracts "#123"
3. ✅ Existing GitHub/Linear/YouTrack parsing unchanged
4. ✅ Clear error messages for invalid formats

## Notes

- GitLab IDs are project-scoped, so we prefix with "#" for display (like `#123`)
- The `issue.scala` command already extracts numeric part for GitLab fetch, but parsing fails first
- We need tracker context to know whether to accept bare numeric IDs

**Phase Status:** Complete
