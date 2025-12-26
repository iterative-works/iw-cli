# Phase 2 Context: Parse and display GitHub issues with team prefix

**Issue:** #51
**Phase:** 2 of 3
**Estimated Effort:** 2-3 hours
**Status:** Not Started

## Goals

Enable iw-cli to correctly parse and handle GitHub issue IDs with the new team prefix format. This phase ensures that:

1. **Bare numeric input** (e.g., `51`) is automatically prefixed with the team prefix from config
2. **Full format input** (e.g., `IWCLI-51`) is accepted directly
3. **Team-prefixed branches** are parsed correctly (already works via existing `BranchPattern`)
4. All commands work seamlessly with the new format

This phase bridges the gap between Phase 1 (config storage) and Phase 3 (removal of numeric patterns).

## Scope

### In Scope

1. **IssueId.parse Context Awareness**
   - Modify `parse` to optionally accept a default team prefix
   - When numeric input + team prefix provided → compose `TEAM-NNN` format
   - When full format input → accept directly (no change needed)
   - Signature: `parse(raw: String, defaultTeam: Option[String] = None)`

2. **Command Updates for Context-Aware Parsing**
   - Update commands that parse user input to pass team prefix from config
   - Commands affected: `issue`, `start`, `comment` (any that take issue ID as argument)
   - Read team prefix from config and pass to `IssueId.parse`

3. **Testing**
   - Unit tests for `IssueId.parse` with team prefix context
   - Unit tests for numeric → prefixed conversion
   - E2E tests for commands accepting numeric input
   - E2E tests for commands accepting full format input

### Out of Scope

- Removing `NumericPattern` and `NumericBranchPattern` (Phase 3)
- Changes to `fromBranch` (already handles `TEAM-NNN` format correctly)
- Migration of existing bare numeric branches (intentionally out of scope)
- Config file changes (completed in Phase 1)
- `iw init` changes (completed in Phase 1)

## Dependencies

### Prerequisites from Phase 1

- `ProjectConfiguration` has `teamPrefix: Option[String]` field ✓
- `TeamPrefixValidator.validate(prefix)` exists ✓
- `IssueId.forGitHub(prefix, number)` factory method exists ✓
- Config properly stores and retrieves team prefix ✓

### What Phase 1 Built

```scala
// Available in Config.scala
object TeamPrefixValidator:
  def validate(prefix: String): Either[String, String]
  def suggestFromRepository(repo: String): String

// Available in IssueId.scala
object IssueId:
  def forGitHub(teamPrefix: String, number: Int): Either[String, IssueId]
```

### External Dependencies

- Existing command infrastructure
- Existing config loading in commands
- Existing test framework

## Technical Approach

### 1. IssueId.parse Signature Change

**File:** `.iw/core/IssueId.scala`

**Current signature:**
```scala
def parse(raw: String): Either[String, IssueId]
```

**New signature:**
```scala
def parse(raw: String, defaultTeam: Option[String] = None): Either[String, IssueId]
```

**Logic:**
1. Try `Pattern` (TEAM-NNN) first → return as-is if matches
2. Try `NumericPattern` next:
   - If `defaultTeam.isDefined` → use `forGitHub(team, number)` to compose
   - If `defaultTeam.isEmpty` → return as-is (bare numeric, for backward compatibility during Phase 2)
3. Return error if no pattern matches

This approach:
- Maintains backward compatibility (no `defaultTeam` = current behavior)
- Enables commands to apply team prefix when available
- Prepares for Phase 3 by making numeric + no-team-prefix a distinct path

### 2. Command Updates

**Pattern for commands:**

```scala
// Read config
val config = ConfigFileRepository.load()

// Extract team prefix for GitHub
val teamPrefix = config.map(_.teamPrefix).getOrElse(None)

// Parse with context
IssueId.parse(userInput, teamPrefix)
```

**Commands to update:**

1. **`.iw/commands/issue.scala`**
   - Currently parses issue ID from argument or branch
   - Update argument parsing to use team prefix

2. **`.iw/commands/comment.scala`** (if exists)
   - Same pattern as issue command

3. **`.iw/commands/start.scala`**
   - Already updated in Phase 1 for creation
   - Verify parsing logic is consistent

### 3. Team Extraction for GitHub API

**Important consideration:** GitHub API needs just the issue number, not the full `TEAM-NNN`.

Current `GitHubClient.fetchIssue` already handles this correctly by extracting the numeric part:

```scala
// IssueId extension method
def number: String =
  if issueId.contains("-") then
    issueId.split("-").last
  else
    issueId // Bare numeric (to be deprecated in Phase 3)
```

This extension may need to be added if not already present, or we rely on the fact that GitHub API parsing already extracts the number.

## Files to Modify

### Core Domain Files

1. **`.iw/core/IssueId.scala`**
   - Change `parse` signature to accept optional team prefix
   - Update logic to compose `TEAM-NNN` when numeric + team prefix
   - Lines affected: ~18-29 (parse method)

### Command Files

2. **`.iw/commands/issue.scala`**
   - Load config to get team prefix
   - Pass team prefix to `IssueId.parse`
   - Lines affected: TBD (need to review current structure)

3. **`.iw/commands/start.scala`**
   - Verify consistent handling (may already be correct from Phase 1)
   - Lines affected: Minimal if any

4. **Other commands that parse issue IDs**
   - Review `.iw/commands/` for any additional commands

### Test Files

5. **`.iw/core/test/IssueIdTest.scala`**
   - Add tests for `parse` with team prefix
   - Add tests for numeric → prefixed conversion
   - Add tests for full format with team prefix (should ignore prefix)

6. **E2E test files (BATS)**
   - Add tests for `iw issue 51` with team prefix configured
   - Add tests for `iw issue IWCLI-51` (should work directly)

## Testing Strategy

### Unit Tests (munit)

**IssueId.parse with team prefix:**

| Input | Team Prefix | Expected Output |
|-------|-------------|-----------------|
| `"51"` | `Some("IWCLI")` | `Right("IWCLI-51")` |
| `"51"` | `None` | `Right("51")` (backward compat) |
| `"IWCLI-51"` | `Some("IWCLI")` | `Right("IWCLI-51")` |
| `"IWCLI-51"` | `None` | `Right("IWCLI-51")` |
| `"IWCLI-51"` | `Some("OTHER")` | `Right("IWCLI-51")` (explicit wins) |
| `"abc"` | `Some("IWCLI")` | `Left(error)` |
| `"abc"` | `None` | `Left(error)` |

**Edge cases:**
- Empty string input
- Whitespace handling
- Very large numbers
- Invalid team prefix format (should still work if passed)

### E2E Tests (BATS)

**issue command:**
- `iw issue 51` with GitHub + team prefix → fetches issue #51
- `iw issue IWCLI-51` with GitHub → fetches issue #51
- `iw issue 51` without team prefix → still works (backward compat)

**start command:**
- Verify Phase 1 changes work with Phase 2 parsing

## Acceptance Criteria

### Functional Acceptance

- [ ] `iw issue 51` with GitHub + team prefix fetches correct issue
- [ ] `iw issue IWCLI-51` works correctly
- [ ] `iw issue` (no arg) on branch `IWCLI-51-desc` infers correct issue
- [ ] All commands accept both numeric and full format

### Technical Acceptance

- [ ] `IssueId.parse(raw, defaultTeam)` signature implemented
- [ ] Numeric input with team prefix composes `TEAM-NNN`
- [ ] Full format input ignores default team prefix
- [ ] Backward compatibility maintained (no team prefix = current behavior)

### Testing Acceptance

- [ ] All new unit tests pass
- [ ] All existing unit tests still pass
- [ ] E2E tests for issue command pass
- [ ] No regressions in start command

## Known Risks & Mitigations

### Risk 1: Breaking existing numeric workflows

**Risk:** Commands that currently work with `51` might break.

**Mitigation:**
- Backward compatibility: `parse("51", None)` still returns `"51"`
- Only when team prefix is provided does it compose `TEAM-NNN`
- Phase 3 will remove this fallback with clear migration path

### Risk 2: Command inconsistency

**Risk:** Some commands might not pass team prefix consistently.

**Mitigation:**
- Review ALL commands that parse issue IDs
- Create consistent pattern for loading config + passing team prefix
- E2E tests verify end-to-end behavior

### Risk 3: API call issues

**Risk:** GitHub API might receive `IWCLI-51` instead of `51`.

**Mitigation:**
- Verify `GitHubClient` extracts numeric part correctly
- Add/verify `number` extension method if needed
- Test with real GitHub API

## Implementation Notes

### Order of Implementation

1. **Update IssueId.parse signature** (core change)
   - Add optional parameter with default
   - Update logic for numeric + team prefix case
   - Write unit tests

2. **Update issue command** (main user-facing command)
   - Load config
   - Pass team prefix to parse
   - Test interactively

3. **Review and update other commands**
   - Check start.scala consistency
   - Check any other commands that parse IDs

4. **E2E tests**
   - Write BATS tests for all scenarios

### TDD Approach

1. Write failing test: `parse("51", Some("IWCLI"))` returns `Right("IWCLI-51")`
2. Implement logic
3. Write more tests for edge cases
4. Refactor if needed

### Commit Strategy

- "Add team prefix parameter to IssueId.parse"
- "Update issue command to use team prefix from config"
- "Update other commands for consistent team prefix handling"
- "Add E2E tests for team-prefixed issue parsing"

## Definition of Done

Phase 2 is complete when:

1. All acceptance criteria are met
2. All tests pass (unit + E2E)
3. Code is committed to branch `51`
4. Commands work seamlessly with both numeric and full format
5. Backward compatibility maintained for projects without team prefix
6. Ready to proceed to Phase 3 (removal of numeric patterns)

## Next Phase Preview

**Phase 3: Remove numeric-only branch handling**

Will:
- Remove `NumericPattern` from `IssueId`
- Remove `NumericBranchPattern` from `IssueId`
- Make team prefix required for GitHub (error if not configured)
- Update error messages with migration guidance
- Add `iw doctor` check for legacy numeric branches

Dependencies from Phase 2:
- Context-aware parsing is in place
- Commands pass team prefix consistently
- Users have migrated to `TEAM-NNN` format

---

**Phase Context Status:** Ready for Implementation

**Estimated Effort:** 2-3 hours
**Complexity:** Straightforward - signature change and command updates
**Confidence:** High - builds directly on Phase 1 foundation
