# Phase 6 Context: GitLab issue ID parsing and validation

**Issue:** IW-90
**Phase:** 6 of 7
**Status:** Not Started

## Goals

This phase completes the domain layer for GitLab support by ensuring issue IDs are properly parsed and validated. This enables:
1. Parsing numeric GitLab issue IDs from command arguments
2. Inferring issue IDs from branch names (e.g., `123-add-dark-mode`)
3. Validating issue ID format before API calls

## Scope

### In Scope
- Ensure `IssueId.parse` handles numeric GitLab issue IDs correctly
- Ensure `IssueId.fromBranch` extracts numeric IDs from branch names
- Validate that GitLab tracker type doesn't require team prefix
- Add tests for GitLab-specific issue ID patterns
- Verify integration with `iw issue` command (branch inference)

### Out of Scope
- Changes to GitLabClient (already complete in Phases 1-5)
- Changes to configuration (complete in Phase 3)
- E2E testing with real glab CLI (Phase 7)

## Dependencies

### From Previous Phases
- **Phase 1:** `IssueTrackerType.GitLab` enum exists in Config.scala
- **Phase 3:** GitLab config format: `tracker.type = gitlab`, `tracker.repository`
- **Phase 5:** `GitLabClient.fetchIssue` works with numeric IDs

### Technical Dependencies
- `IssueId` type in domain layer
- Branch name parsing utilities
- Config loading for tracker type detection

## Technical Approach

### Current State Analysis

Based on the codebase patterns:
1. `IssueId` is likely a value object that wraps issue identifiers
2. `IssueId.parse` handles different formats based on tracker type
3. Branch name inference extracts issue ID from branch prefix (e.g., `IW-123-feature` → `IW-123`)

### GitLab-Specific Requirements

GitLab issues use simple numeric IDs (project-scoped):
- Valid formats: `123`, `456`, `1`
- Branch patterns: `123-feature-name`, `123_add_button`, `123/fix-bug`
- No team prefix required (unlike GitHub which uses teamPrefix for `TEAM-123`)

### Implementation Strategy

1. **Verify existing parsing**: Check if `IssueId.parse` already handles numeric IDs
2. **Add GitLab-specific tests**: Ensure parsing works for GitLab patterns
3. **Branch inference**: Verify numeric extraction from branch names works
4. **Integration**: Test with `iw issue` command using branch inference

## Files to Modify

Based on the codebase structure:

| File | Changes |
|------|---------|
| `.iw/core/IssueId.scala` (or similar) | Verify/add GitLab ID parsing |
| `.iw/core/test/IssueIdTest.scala` | Add GitLab-specific tests |
| `.iw/commands/issue.scala` | Verify GitLab branch inference works |

## Testing Strategy

### Unit Tests
- Parse numeric issue IDs: `123`, `1`, `99999`
- Parse with leading zeros: `0123` (should reject or normalize)
- Parse invalid formats: `abc`, `12.3`, `-1`, empty string
- Branch name extraction: `123-feature`, `123_feature`, `123/feature`
- No team prefix validation for GitLab (unlike GitHub)

### Integration Tests
- `iw issue` with explicit numeric ID
- `iw issue` with branch inference (on branch `123-feature`)
- Error case: invalid ID format

### Verification Tests
- Run existing GitHub/Linear/YouTrack tests to ensure no regression
- Test that GitLab tracker doesn't require teamPrefix in config

## Acceptance Criteria

1. **Numeric ID parsing**: `IssueId.parse("123")` returns valid ID for GitLab tracker
2. **Branch inference**: On branch `123-add-dark-mode`, `iw issue` fetches issue 123
3. **No team prefix**: GitLab doesn't require team prefix configuration
4. **Clear errors**: Invalid ID formats produce helpful error messages
5. **No regression**: Existing GitHub/Linear/YouTrack parsing continues to work

## Estimated Effort

**3-4 hours** (from analysis.md Story 6)

This is straightforward because:
- GitLab uses simple numeric IDs (like GitHub)
- `IssueId.parse` likely already handles numeric format for GitHub
- Branch name inference pattern exists for other trackers
- Mostly verification and test additions, minimal new code

## Notes

- GitLab issue IDs are project-scoped, so `123` in `project-a` is different from `123` in `project-b`
- The repository context comes from config, not the issue ID
- If existing parsing already works, this phase is mainly adding tests for completeness
