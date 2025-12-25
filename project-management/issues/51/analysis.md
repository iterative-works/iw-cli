# Story-Driven Analysis: Improve branch naming convention for GitHub issues

**Issue:** #51
**Created:** 2025-12-26
**Status:** Draft
**Classification:** Simple

## Problem Statement

Currently, GitHub issue branches are created using bare numeric names (e.g., `48`, `132`), which creates several usability and safety issues:

- **Namespace collision risk**: Numeric branches could conflict with other branch naming schemes (version numbers, sequential feature branches)
- **Poor discoverability**: When browsing branches, it's not immediately obvious that `48` refers to a GitHub issue
- **No semantic context**: Unlike Linear/YouTrack branches that include project prefix (e.g., `IWLE-123`), GitHub branches lack semantic meaning

Users should be able to configure branch name prefixes (e.g., `issue/48`, `wip/48`, `feat/48`) to make branch purposes explicit and avoid naming collisions.

## User Stories

### Story 1: Configure branch prefix for new GitHub issue branches

```gherkin
Feature: Configurable branch prefixes for GitHub issues
  As a developer using iw-cli with GitHub
  I want to configure a prefix for issue branches
  So that my branches have clear, collision-free names

Scenario: Create new branch with configured prefix
  Given I have GitHub tracker configured in .iw/config.conf
  And I have set branch prefix to "issue/" in config
  When I run "iw start 51"
  Then a new branch "issue/51" is created
  And a worktree is created for branch "issue/51"
  And I see success message mentioning "issue/51"
```

**Estimated Effort:** 3-4h
**Complexity:** Straightforward

**Technical Feasibility:**
This is straightforward configuration enhancement. The existing `toBranchName` extension method already provides the hook point. Main work is:
- Add config field for branch prefix
- Update `toBranchName` to apply prefix for GitHub issues
- Existing tests provide good safety net

**Acceptance:**
- Config file accepts optional `branchPrefix` setting
- New branches created with prefix when configured
- Backward compatible (no prefix = current behavior)
- Tests verify prefix application

---

### Story 2: Parse issue ID from prefixed branches

```gherkin
Feature: Extract issue IDs from prefixed branch names
  As a developer working in a prefixed issue branch
  I want iw-cli to infer the issue ID from the branch name
  So that commands like "iw issue" and "iw open" work without explicit ID

Scenario: Infer issue ID from prefixed branch
  Given I am on branch "issue/51"
  When I run "iw issue" (without issue ID argument)
  Then the issue ID "51" is extracted from "issue/51"
  And issue #51 details are displayed

Scenario: Open existing worktree by prefixed branch
  Given a worktree exists for "issue/51"
  And I am on branch "issue/51"
  When I run "iw open" (without issue ID argument)
  Then the issue ID "51" is extracted
  And the worktree session is opened
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
The `fromBranch` method already handles pattern extraction for Linear/YouTrack. We need to:
- Update pattern matching to recognize prefix patterns
- Make pattern configurable (read from config during parsing)
- Existing test suite provides regression safety

Key challenge: `fromBranch` is currently static but needs config context. May need to pass config or use sensible prefix patterns.

**Acceptance:**
- Commands infer issue ID from prefixed branches
- Pattern matching supports common prefixes (`issue/`, `wip/`, `feat/`, etc.)
- Backward compatible with bare numeric branches
- Comprehensive test coverage for all prefix patterns

---

### Story 3: Handle mixed branch naming scenarios

```gherkin
Feature: Support mixed old and new branch naming
  As a developer transitioning to prefixed branches
  I want both old (bare numeric) and new (prefixed) branches to work
  So that existing worktrees remain functional

Scenario: Open existing bare numeric branch
  Given a worktree exists for bare branch "48"
  When I run "iw open 48"
  Then the worktree for branch "48" opens successfully

Scenario: Switch between different branch naming conventions
  Given I have worktree "48" with bare numeric branch
  And I have worktree "issue/51" with prefixed branch
  When I run "iw open 48"
  Then I switch to the "48" worktree
  When I run "iw open 51"
  Then I switch to the "issue/51" worktree
```

**Estimated Effort:** 2-3h
**Complexity:** Moderate

**Technical Feasibility:**
This tests backward compatibility edge cases. Main considerations:
- Fallback logic in `fromBranch` (try prefixed patterns first, then bare)
- Branch lookup should handle both formats
- Config migration story (users changing prefix setting)

Some complexity around what happens if someone has both `48` and `issue/48` branches (unlikely but possible).

**Acceptance:**
- Existing bare numeric branches continue working
- New prefixed branches work alongside old branches
- No data migration required
- Clear error messages if ambiguous situations arise

## Architectural Sketch

### For Story 1: Configure branch prefix for new GitHub issue branches

**Domain Layer:**
- `IssueId` value object (existing)
  - Update `toBranchName` extension to apply prefix
  - May need `toBranchName(config: BranchConfig)` variant
- `BranchConfig` value object (new)
  - Encapsulates prefix configuration
  - Validation for valid prefix patterns

**Application Layer:**
- `start.scala` command (existing)
  - Pass config to `toBranchName` when creating branches
- Configuration loading (existing `ConfigFileRepository`)

**Infrastructure Layer:**
- `ConfigFileRepository` (existing)
  - Parse `branchPrefix` field from config.conf
- `ProjectConfiguration` (existing)
  - Add `branchPrefix: Option[String]` field

**Presentation Layer:**
- No UI changes needed
- CLI output shows created branch name (already exists)

---

### For Story 2: Parse issue ID from prefixed branches

**Domain Layer:**
- `IssueId.fromBranch` (existing method)
  - Update pattern matching to handle prefixes
  - New regex patterns for common prefixes
  - Fallback chain: prefixed patterns -> bare numeric

**Application Layer:**
- `issue.scala` command (existing)
  - Already calls `fromBranch`, no changes needed if domain handles it
- `open.scala` command (existing)
  - Already calls `fromBranch`, no changes needed

**Infrastructure Layer:**
- Potentially read config to determine valid prefixes
  - Alternative: Support common prefixes without config lookup

**Presentation Layer:**
- Error messages updated for prefix patterns

---

### For Story 3: Handle mixed branch naming scenarios

**Domain Layer:**
- `IssueId.fromBranch` (existing)
  - Fallback logic ensuring backward compatibility
  - Order of pattern matching matters (prefix first, then bare)

**Application Layer:**
- All commands using `fromBranch` (existing)
  - No changes needed if domain handles it correctly

**Infrastructure Layer:**
- Git branch lookup (existing `GitWorktreeAdapter`)
  - May need logic to find branch by issue ID regardless of prefix
  - `branchExists` might need enhancement

**Presentation Layer:**
- Help text / error messages mentioning both formats

## Technical Risks & Uncertainties

### CLARIFY: What prefix patterns should be supported?

The issue description mentions `issue/`, `wip/`, `feat/` as examples, but doesn't specify:

**Questions to answer:**
1. Should we support a single configurable prefix, or multiple preset patterns?
2. Should the prefix be freeform (user types anything), or validated against a list?
3. Do we want to support suffixes too (e.g., `51-feature-name`)?
4. Should prefix configuration be per-project or per-tracker-type?

**Options:**
- **Option A: Single configurable prefix** - User sets `branchPrefix = "issue/"` in config
  - Pros: Simple, clear, user has full control
  - Cons: Can't easily support multiple patterns for migration scenarios

- **Option B: Preset prefix list** - System recognizes common patterns (`issue/`, `wip/`, `feat/`, `gh/`)
  - Pros: Works without config, handles mixed naming automatically
  - Cons: Less flexible, might not match user's existing conventions

- **Option C: Configurable with smart defaults** - Config overrides default pattern list
  - Pros: Best of both worlds (works OOTB, customizable)
  - Cons: More complex implementation

**Impact:** Affects Story 1 (config schema) and Story 2 (pattern matching logic). All stories depend on this decision.

---

### CLARIFY: How should fromBranch access configuration?

Currently `IssueId.fromBranch` is a static method with no dependencies. To support configurable prefixes:

**Questions to answer:**
1. Should `fromBranch` read config internally (side effect)?
2. Should we pass config as a parameter?
3. Should we use smart defaults without config?

**Options:**
- **Option A: Pass config parameter** - `fromBranch(branch: String, config: BranchConfig)`
  - Pros: Pure function, testable, explicit dependencies
  - Cons: All call sites need config, breaking change

- **Option B: Internal config lookup** - `fromBranch` reads config from filesystem
  - Pros: No API changes, backward compatible
  - Cons: Side effect in domain layer, harder to test

- **Option C: Smart defaults (no config)** - Recognize common prefixes automatically
  - Pros: No config needed, works OOTB, pure function
  - Cons: Less customizable, users can't control patterns

**Impact:** Affects domain design philosophy (functional purity) and all of Story 2.

---

### CLARIFY: Migration strategy for existing worktrees

Users may have existing worktrees with bare numeric branches.

**Questions to answer:**
1. When they change config to add prefix, what happens to existing worktrees?
2. Do we support renaming branches in existing worktrees?
3. Should `iw open` handle both old and new naming simultaneously?

**Options:**
- **Option A: No migration needed** - Old branches keep working, new branches use prefix
  - Pros: Safe, no data loss, no forced migration
  - Cons: Mixed naming conventions in same project

- **Option B: Automatic branch rename** - Tool offers to rename existing branches
  - Pros: Clean, consistent naming
  - Cons: Risky (git rename can fail), might interfere with PRs

- **Option C: Hybrid lookup** - Commands search for both `issue/51` and `51`
  - Pros: Seamless, works during transition period
  - Cons: Ambiguity if both branches exist (rare but possible)

**Impact:** Affects Story 3 implementation complexity and user experience during adoption.

## Total Estimates

**Story Breakdown:**
- Story 1 (Configure branch prefix for new branches): 3-4 hours
- Story 2 (Parse issue ID from prefixed branches): 2-3 hours
- Story 3 (Handle mixed branch naming scenarios): 2-3 hours

**Total Range:** 7-10 hours

**Confidence:** Medium

**Reasoning:**
- Domain logic changes are localized to `IssueId.scala` (good encapsulation)
- Existing test suite provides safety net (20+ tests in `IssueIdFromBranchTest`)
- Config parsing already exists (`ConfigFileRepository`)
- Some unknowns around config access pattern (CLARIFY markers above)
- Backward compatibility adds moderate complexity but is well-scoped

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests**: Domain logic for pattern matching and prefix application
2. **Integration Tests**: Config loading and branch creation
3. **E2E Scenario Tests**: Full command workflows with real git operations

**Story 1: Configure branch prefix**
- Unit: `toBranchName` returns correct prefixed names
- Integration: Config parsing reads `branchPrefix` field
- E2E: `iw start 51` creates branch with configured prefix

**Story 2: Parse issue ID from prefixed branches**
- Unit: `fromBranch` extracts ID from various prefix patterns
- Integration: Commands infer issue ID correctly
- E2E: `iw issue` and `iw open` work from prefixed branches

**Story 3: Mixed branch naming**
- Unit: Fallback logic in `fromBranch`
- Integration: Branch lookup handles both formats
- E2E: Opening worktrees by ID works for both formats

## Dependencies

### Prerequisites
- None - this is pure enhancement to existing functionality

### Story Dependencies
- Story 2 depends on Story 1 (need config schema for prefix)
- Story 3 is parallel to Story 2 (both enhance `fromBranch`)

### External Blockers
- None identified

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: Configure branch prefix for new branches** - Establishes foundation
2. **Story 2: Parse issue ID from prefixed branches** - Enables commands to work with prefixed branches
3. **Story 3: Handle mixed naming scenarios** - Validates backward compatibility

**Recommendation:** Implement all three stories in a single focused session (7-10h total). Scope is small, stories are tightly coupled.

---

**Analysis Status:** Ready for Review - Pending CLARIFY Resolutions

**Next Steps:**
1. **Resolve CLARIFY markers** - Decide on prefix patterns, config access, and migration strategy
2. Run `/iterative-works:ag-create-tasks 51` to generate implementation phases
3. Run `/iterative-works:ag-implement 51` for story-by-story implementation

**Recommendation:** Favor **Option C (Smart defaults)** for both prefix patterns and config access:
- Recognize common prefixes without configuration (`issue/`, `wip/`, `feat/`, `gh/`)
- No changes to `fromBranch` signature (stays pure)
- Works OOTB for 90% of users
- Can add configuration later if users request custom prefixes
