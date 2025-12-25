# Story-Driven Analysis: Improve branch naming convention for GitHub issues

**Issue:** #51
**Created:** 2025-12-26
**Updated:** 2025-12-26
**Status:** Draft
**Classification:** Simple

## Problem Statement

Currently, GitHub issue branches are created using bare numeric names (e.g., `48`, `132`), which creates several usability and safety issues:

- **Namespace collision risk**: Numeric branches could conflict with other branch naming schemes (version numbers, sequential feature branches)
- **Poor discoverability**: When browsing branches, it's not immediately obvious that `48` refers to a GitHub issue
- **No semantic context**: Unlike Linear/YouTrack branches that include project prefix (e.g., `IWLE-123`), GitHub branches lack semantic meaning
- **Code complexity**: Special handling for numeric-only patterns (`NumericPattern`, `NumericBranchPattern`) adds complexity to `IssueId`

## Solution: Unified Team Prefix for All Trackers

Instead of adding path-based prefixes (like `issue/51`), we will use the same `TEAM-NNN` convention that Linear and YouTrack already use. GitHub projects will configure a team prefix, resulting in branches like `IWCLI-51` or `GH-51`.

**Benefits:**
- **Consistency**: All trackers use the same `TEAM-NNN` branch naming convention
- **Simplification**: Remove `NumericPattern` and `NumericBranchPattern` from `IssueId`
- **Existing code reuse**: `fromBranch` already handles `TEAM-NNN` pattern correctly
- **Clear semantics**: Branch names immediately indicate project and issue number

## User Stories

### Story 1: Configure team prefix for GitHub projects

```gherkin
Feature: Team prefix for GitHub issue branches
  As a developer using iw-cli with GitHub
  I want to configure a team prefix for my project
  So that my branches follow the same TEAM-NNN convention as Linear/YouTrack

Scenario: Initialize project with team prefix
  Given I run "iw init" in a GitHub project
  When I configure GitHub as the tracker
  Then I am prompted to enter a team prefix (e.g., "IWCLI")
  And the prefix is stored in .iw/config.conf

Scenario: Create new branch with team prefix
  Given I have GitHub tracker configured with team prefix "IWCLI"
  When I run "iw start 51"
  Then a new branch "IWCLI-51" is created
  And a worktree is created for branch "IWCLI-51"
  And I see success message mentioning "IWCLI-51"
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
- Add `teamPrefix` field to config for GitHub tracker type
- Update `iw init` to prompt for team prefix when GitHub is selected
- Modify `IssueId` to compose `TEAM-NNN` format for GitHub issues
- `toBranchName` already returns the issue ID value, which will now include prefix

**Acceptance:**
- Config file stores `teamPrefix` for GitHub projects
- `iw start <number>` creates branch with `TEAMPREFIX-<number>` format
- Team prefix is required for GitHub (prompted during init)

---

### Story 2: Parse and display GitHub issues with team prefix

```gherkin
Feature: Handle GitHub issues with team prefix format
  As a developer working in a team-prefixed branch
  I want iw-cli to correctly parse and display issue information
  So that all commands work seamlessly with the new format

Scenario: Parse issue ID from team-prefixed branch
  Given I am on branch "IWCLI-51"
  When I run "iw issue" (without issue ID argument)
  Then the issue ID is parsed as team="IWCLI", number="51"
  And GitHub issue #51 is fetched and displayed

Scenario: Explicit issue ID with team prefix
  Given I have GitHub tracker configured with team prefix "IWCLI"
  When I run "iw issue IWCLI-51"
  Then GitHub issue #51 is fetched and displayed

Scenario: Explicit issue ID with just number
  Given I have GitHub tracker configured with team prefix "IWCLI"
  When I run "iw issue 51"
  Then the team prefix is applied automatically
  And GitHub issue #51 is fetched and displayed
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
- `fromBranch` already handles `TEAM-NNN` pattern - no changes needed
- `IssueId.parse` needs to accept bare numbers for GitHub and apply team prefix
- `GitHubClient.fetchIssue` already extracts numeric part for API calls
- Existing test suite covers the pattern matching

**Acceptance:**
- Commands infer issue ID from `TEAM-NNN` branches (already works)
- Bare numeric input (`51`) is accepted and prefixed automatically
- Full format input (`IWCLI-51`) works directly
- GitHub API receives correct numeric issue number

---

### Story 3: Remove numeric-only branch handling

```gherkin
Feature: Simplify IssueId by removing numeric-only patterns
  As a maintainer of iw-cli
  I want to remove special-case numeric handling
  So that the codebase is simpler and more consistent

Scenario: Reject bare numeric branches
  Given I am on a branch named "48" (legacy bare numeric)
  When I run "iw issue"
  Then I see an error suggesting the new format
  And the error mentions configuring a team prefix

Scenario: Migration guidance for existing numeric branches
  Given I have existing bare numeric branches
  When I run "iw doctor"
  Then I see a warning about legacy branch naming
  And I see instructions to rename branches to TEAM-NNN format
```

**Estimated Effort:** 1-2h
**Complexity:** Simple (removal of code)

**Technical Feasibility:**
- Remove `NumericPattern` and `NumericBranchPattern` from `IssueId.scala`
- Update error messages to guide users to new format
- Add check in `iw doctor` for legacy numeric branches
- Update tests to remove numeric-only test cases

**Acceptance:**
- `NumericPattern` and `NumericBranchPattern` removed from codebase
- Clear error message when bare numeric branch detected
- `iw doctor` warns about legacy branches
- All existing `TEAM-NNN` functionality preserved

## Architectural Sketch

### For Story 1: Configure team prefix for GitHub projects

**Domain Layer:**
- `IssueId` value object (existing)
  - Add factory method: `IssueId.forGitHub(teamPrefix: String, number: Int)`
  - `toBranchName` remains unchanged (returns the value)
- `ProjectConfiguration` (existing)
  - Add `teamPrefix: Option[String]` field (required for GitHub)

**Application Layer:**
- `init.scala` command (existing)
  - Prompt for team prefix when GitHub tracker selected
  - Validate prefix format (uppercase letters only)
- `start.scala` command (existing)
  - Compose issue ID with team prefix before creating branch

**Infrastructure Layer:**
- `ConfigFileRepository` (existing)
  - Parse `teamPrefix` field from config.conf
- Config schema update for tracker section

---

### For Story 2: Parse and display GitHub issues with team prefix

**Domain Layer:**
- `IssueId.parse` (existing)
  - Accept bare numeric with context (team prefix from config)
  - Signature change: `parse(raw: String, defaultTeam: Option[String])`
- `IssueId.fromBranch` (existing)
  - No changes needed - already handles `TEAM-NNN`

**Application Layer:**
- All commands using `IssueId.parse`
  - Pass team prefix from config when parsing user input
- `GitHubClient.fetchIssue` (existing)
  - Already extracts numeric part - no changes needed

---

### For Story 3: Remove numeric-only branch handling

**Domain Layer:**
- `IssueId` (existing)
  - Remove `NumericPattern` regex
  - Remove `NumericBranchPattern` regex
  - Simplify pattern matching in `parse` and `fromBranch`

**Application Layer:**
- `doctor.scala` command (existing)
  - Add check for bare numeric branches in worktrees

**Presentation Layer:**
- Updated error messages with migration guidance

## Technical Risks & Uncertainties

### RESOLVED: Branch naming approach

**Decision:** Use `TEAM-NNN` format for GitHub, same as Linear/YouTrack.

This eliminates the previous CLARIFY markers about prefix patterns and config access. The solution is now straightforward:
- GitHub projects require a `teamPrefix` in config
- All trackers use unified `TEAM-NNN` format
- `fromBranch` already works without changes

---

### CLARIFY: Team prefix validation rules

**Questions:**
1. Should team prefix be uppercase only (like Linear: `IWLE`) or allow mixed case?
2. Minimum/maximum length for prefix?
3. Should we suggest a default based on repository name?

**Recommendation:**
- Uppercase letters only, 2-10 characters
- Suggest default from repo name: `iterative-works/iw-cli` → `IWCLI`
- Validate during `iw init`

---

### CLARIFY: Migration for existing bare numeric branches

**Questions:**
1. Should we provide a migration command to rename existing branches?
2. How long should we support reading bare numeric branches (deprecation period)?

**Options:**
- **Option A: Hard cutoff** - Immediately reject bare numeric branches
  - Pros: Clean, simple, no legacy code
  - Cons: Breaks existing workflows immediately

- **Option B: Deprecation warning** - Warn but continue working for N releases
  - Pros: Gentle migration path
  - Cons: Keeps complexity temporarily

- **Option C: Migration command** - Provide `iw migrate-branches` to rename
  - Pros: Automated migration
  - Cons: Additional development, git rename is risky

**Recommendation:** Option A (hard cutoff) with clear error messages. This is a small project, and we're the primary users. Clean break is better than carrying technical debt.

## Total Estimates

**Story Breakdown:**
- Story 1 (Configure team prefix): 2-3 hours
- Story 2 (Parse with team prefix): 2-3 hours
- Story 3 (Remove numeric handling): 1-2 hours

**Total Range:** 5-8 hours

**Confidence:** High

**Reasoning:**
- Solution simplifies rather than adds complexity
- Reuses existing `TEAM-NNN` handling (proven, tested)
- Main work is config changes and code removal
- Existing test suite covers most scenarios

## Testing Approach

**Story 1: Configure team prefix**
- Unit: Config parsing with `teamPrefix` field
- Integration: `iw init` prompts for and stores prefix
- E2E: `iw start 51` with `teamPrefix=IWCLI` creates branch `IWCLI-51`

**Story 2: Parse with team prefix**
- Unit: `IssueId.parse("51", Some("IWCLI"))` returns `IWCLI-51`
- Unit: `IssueId.parse("IWCLI-51", None)` returns `IWCLI-51`
- Integration: Commands work with team-prefixed branches
- E2E: Full workflow with GitHub issue

**Story 3: Remove numeric handling**
- Unit: `IssueId.fromBranch("48")` returns error (not bare numeric anymore)
- Unit: Verify `NumericPattern` regex no longer exists
- E2E: Error message guides user to new format

## Dependencies

### Prerequisites
- None

### Story Dependencies
- Story 2 depends on Story 1 (needs config with team prefix)
- Story 3 can be done in parallel with Story 2

### External Blockers
- None

## Implementation Sequence

**Recommended Order:**

1. **Story 1: Configure team prefix** - Add config support, update init
2. **Story 2: Parse with team prefix** - Update IssueId.parse to apply prefix
3. **Story 3: Remove numeric handling** - Clean up old patterns

**Note:** Stories 2 and 3 can be combined since they both modify `IssueId.scala`.

---

**Analysis Status:** Ready for Implementation

**CLARIFY Resolutions Needed:**
1. Team prefix validation rules (recommend: uppercase, 2-10 chars)
2. Migration approach (recommend: hard cutoff with clear errors)

**Next Steps:**
1. Confirm CLARIFY resolutions above
2. Run `/iterative-works:ag-create-tasks 51` to generate phase-based tasks
3. Run `/iterative-works:ag-implement 51` to start implementation

---

**Key Simplification:**

This approach is significantly simpler than the original analysis because:
- We reuse existing `TEAM-NNN` pattern handling (no new regex patterns)
- `fromBranch` requires no changes (already handles the format)
- We remove code (NumericPattern, NumericBranchPattern) instead of adding it
- Unified convention across all trackers reduces cognitive load
