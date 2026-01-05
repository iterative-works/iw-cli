# Phase 6 Tasks: GitLab issue ID parsing and validation

**Issue:** IW-90
**Phase:** 6 of 7
**Status:** In Progress

## Context

GitLab uses simple numeric issue IDs (e.g., `123`) unlike other trackers that use `TEAM-NNN` format.
Current `IssueId.parse` and `IssueId.fromBranch` only accept `TEAM-NNN` patterns.

## Implementation Tasks

### Setup

- [ ] [test] Create test file for GitLab-specific issue ID parsing
- [ ] [test] Create test file for GitLab-specific branch name extraction

### Tests - GitLab Numeric ID Parsing

- [ ] [test] Test IssueId.parse accepts bare numeric ID "123" for GitLab tracker
- [ ] [test] Test IssueId.parse accepts single-digit ID "1" for GitLab
- [ ] [test] Test IssueId.parse accepts large ID "99999" for GitLab
- [ ] [test] Test IssueId.parse rejects invalid formats for GitLab (negative, decimal, alpha)
- [ ] [test] Test IssueId.parse stores numeric ID as "#123" format for GitLab

### Tests - GitLab Branch Extraction

- [ ] [test] Test IssueId.fromBranch extracts from "123-feature" for GitLab
- [ ] [test] Test IssueId.fromBranch extracts from "123_feature" for GitLab (underscore)
- [ ] [test] Test IssueId.fromBranch extracts from "123" (exact match) for GitLab
- [ ] [test] Test IssueId.fromBranch rejects non-numeric prefix for GitLab ("main", "feature-x")

### Implementation - Tracker-Aware Parsing

- [ ] [impl] Add IssueId.forGitLab factory method that accepts numeric ID
- [ ] [impl] Modify IssueId.parse to accept tracker type parameter
- [ ] [impl] Modify IssueId.fromBranch to accept tracker type parameter
- [ ] [impl] Update issue.scala command to pass tracker type to parse/fromBranch

### Integration

- [ ] [impl] Update existing tests to pass tracker type where needed
- [ ] [test] Run full test suite to verify no regressions
- [ ] [impl] Manual verification with `iw issue` on GitLab-configured project

## Acceptance Criteria

1. `IssueId.parse("123", trackerType=GitLab)` returns valid ID
2. `IssueId.fromBranch("123-feature", trackerType=GitLab)` extracts "#123"
3. Existing GitHub/Linear/YouTrack parsing unchanged
4. Clear error messages for invalid formats

## Notes

- GitLab IDs are project-scoped, so we prefix with "#" for display (like `#123`)
- The `issue.scala` command already extracts numeric part for GitLab fetch, but parsing fails first
- We need tracker context to know whether to accept bare numeric IDs
