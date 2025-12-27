# Story-Driven Analysis: Add 'iw register' command to manually register worktrees to dashboard

**Issue:** IW-63
**Created:** 2025-12-27
**Status:** Draft
**Classification:** Simple

## Problem Statement

Users who create worktrees outside of `iw start` (e.g., via direct `git worktree add`, from external tools, or in parallel project contexts) cannot register these worktrees with the dashboard for monitoring. This creates an inconsistency where only iw-created worktrees are visible in the dashboard, fragmenting the user's workflow visibility.

The core value is **convenience and completeness**: users should be able to add any worktree to the dashboard regardless of how it was created, enabling unified monitoring of all active issue work.

## User Stories

### Story 1: Auto-detect and register current worktree

```gherkin
Feature: Manual worktree registration from current directory
  As a developer working in an existing worktree
  I want to run 'iw register' without arguments
  So that the worktree is automatically detected and registered to the dashboard

Scenario: Successfully register current worktree with issue ID from branch
  Given I am in a worktree directory
  And the current branch is "IW-63-add-register-command"
  And the project has a valid .iw/config.conf
  When I run 'iw register'
  Then the worktree is registered to the dashboard with issue ID "IW-63"
  And I see success message "Registered worktree for IW-63 at [path]"
  And the worktree appears in the dashboard

Scenario: Fail gracefully when not in a git worktree
  Given I am in a directory that is not a git repository
  When I run 'iw register'
  Then I see error "Not in a git repository"
  And the command exits with code 1

Scenario: Fail gracefully when branch name has no issue ID
  Given I am in a worktree directory
  And the current branch is "main"
  When I run 'iw register'
  Then I see error "Cannot extract issue ID from branch 'main'"
  And I see info "Use 'iw register ISSUE-ID' to specify explicitly"
  And the command exits with code 1
```

**Estimated Effort:** 3-4h
**Complexity:** Straightforward

**Technical Feasibility:**
This story is straightforward because it reuses existing infrastructure:
- `GitAdapter.getCurrentBranch()` already exists
- `IssueId.fromBranch()` already handles parsing
- `ConfigFileRepository.read()` already loads project config
- `ServerClient.registerWorktree()` already handles the API call
- `os.pwd` provides current directory

The only new code is CLI argument parsing and orchestration of existing components.

**Acceptance:**
- Running `iw register` in a worktree with branch "TEAM-123-foo" registers to dashboard
- Clear error messages for missing config, invalid branch names, or non-git directories
- Success message includes issue ID and path for confirmation

---

### Story 2: Explicitly register worktree by issue ID

```gherkin
Feature: Manual worktree registration with explicit issue ID
  As a developer working in a worktree with a non-standard branch name
  I want to specify the issue ID explicitly
  So that I can register worktrees even when branch name doesn't contain the issue ID

Scenario: Successfully register current worktree with explicit issue ID
  Given I am in a worktree directory at "/home/user/project/worktrees/feature-work"
  And the project has a valid .iw/config.conf
  When I run 'iw register IW-42'
  Then the worktree is registered with issue ID "IW-42" at "/home/user/project/worktrees/feature-work"
  And I see success message "Registered worktree for IW-42 at /home/user/project/worktrees/feature-work"

Scenario: Successfully register external worktree with explicit path
  Given I am in the main repository
  And a worktree exists at "/home/user/.local/share/par/worktrees/MEDH-112-ag"
  And the project has a valid .iw/config.conf
  When I run 'iw register MEDH-112 --path /home/user/.local/share/par/worktrees/MEDH-112-ag'
  Then the worktree is registered with issue ID "MEDH-112" at the specified path
  And I see success message "Registered worktree for MEDH-112 at /home/user/.local/share/par/worktrees/MEDH-112-ag"

Scenario: Fail validation for invalid issue ID format
  Given I am in a worktree directory
  When I run 'iw register invalid-id'
  Then I see error "Invalid issue ID format: invalid-id (expected: TEAM-123)"
  And the command exits with code 1
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward extension of Story 1. Adds:
- Command-line argument parsing for issue ID and `--path` option
- Issue ID validation via existing `IssueId.parse()`
- Path defaulting logic (use `--path` if provided, else `os.pwd`)

All core logic already exists; this is just CLI plumbing.

**Acceptance:**
- `iw register TEAM-123` works from current directory
- `iw register TEAM-123 --path /external/path` works from anywhere
- Invalid issue ID formats rejected with clear error message
- Tracker-specific issue ID handling (GitHub numeric with team prefix) works correctly

---

### Story 3: Handle registration errors gracefully

```gherkin
Feature: Error handling for registration failures
  As a developer attempting to register a worktree
  I want clear feedback when registration fails
  So that I can diagnose and fix the problem

Scenario: Fail gracefully when project is not initialized
  Given I am in a directory without .iw/config.conf
  When I run 'iw register IW-42'
  Then I see error "Cannot read configuration"
  And I see info "Run './iw init' to initialize the project"
  And the command exits with code 1

Scenario: Warn when dashboard server is unavailable
  Given I am in a valid worktree
  And the dashboard server is not running and cannot be started
  When I run 'iw register'
  Then the command attempts to parse issue ID and validate inputs first
  And I see warning "Failed to register worktree with dashboard: [server error]"
  And the command exits with code 0
  And the user can still use the worktree locally

Scenario: Handle server errors with meaningful messages
  Given I am in a valid worktree
  And the dashboard server returns a 500 error
  When I run 'iw register'
  Then I see warning "Failed to register worktree with dashboard: Server returned 500: [error message]"
  And the command exits with code 0
```

**Estimated Effort:** 1-2h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward because it follows the existing best-effort pattern from `start.scala`:
- Config validation happens first (hard failure with code 1)
- Server registration is best-effort (warning with code 0)
- `ServerClient.registerWorktree()` already returns `Either[String, Unit]`
- Error messages already extracted from server responses

No new infrastructure needed - just consistent error handling.

**Acceptance:**
- Missing config fails fast with helpful message
- Dashboard registration failures are warnings, not errors
- Server error messages are propagated to user
- Command succeeds locally even if dashboard unavailable

---

## Architectural Sketch

**Purpose:** List WHAT components each story needs, not HOW they're implemented.

### For Story 1: Auto-detect and register current worktree

**Domain Layer:**
- `IssueId` value object (already exists)
- `ProjectConfiguration` (already exists)

**Application Layer:**
- CLI orchestration logic in `register.scala`:
  - Load project config
  - Get current branch
  - Parse issue ID from branch
  - Call registration service

**Infrastructure Layer:**
- `ConfigFileRepository.read()` - read project config (already exists)
- `GitAdapter.getCurrentBranch()` - get current branch (already exists)
- `IssueId.fromBranch()` - parse issue ID (already exists)
- `ServerClient.registerWorktree()` - register to dashboard (already exists)
- `Output` - user feedback (already exists)

**Presentation Layer:**
- `register.scala` - new command script in `.iw/commands/`

---

### For Story 2: Explicitly register worktree by issue ID

**Domain Layer:**
- `IssueId` value object (already exists)
- `ProjectConfiguration` (already exists)

**Application Layer:**
- CLI argument parsing logic in `register.scala`:
  - Parse issue ID argument
  - Parse optional `--path` flag
  - Default path to `os.pwd` if not provided
  - Validate issue ID format

**Infrastructure Layer:**
- `IssueId.parse()` - validate explicit issue ID (already exists)
- All infrastructure from Story 1 (already exists)

**Presentation Layer:**
- Extend `register.scala` with argument parsing

---

### For Story 3: Handle registration errors gracefully

**Domain Layer:**
- No new domain objects needed

**Application Layer:**
- Error handling logic in `register.scala`:
  - Config validation with user-friendly messages
  - Best-effort dashboard registration
  - Warning output for non-critical failures

**Infrastructure Layer:**
- All infrastructure from Stories 1-2 (already exists)
- `Output.error()`, `Output.warning()`, `Output.success()` (already exists)

**Presentation Layer:**
- Extend `register.scala` with error handling

---

## Technical Risks & Uncertainties

### CLARIFY: Should --path accept relative or absolute paths only?

The `--path` option will allow users to specify external worktree paths. We need to decide on path handling.

**Questions to answer:**
1. Should relative paths be resolved relative to current directory or project root?
2. Should we validate that the path exists before registration?
3. Should we validate that the path is actually a git worktree?

**Options:**
- **Option A**: Accept only absolute paths, reject relative paths with error
  - Pros: No ambiguity, clear semantics
  - Cons: Less convenient for users
- **Option B**: Accept relative paths, resolve to absolute relative to `os.pwd`
  - Pros: More convenient
  - Cons: Could be confusing when run from different directories
- **Option C**: Accept both, normalize to absolute before registration
  - Pros: Most flexible
  - Cons: Path resolution might surprise users

**Impact:** Affects Story 2 implementation and error messages. Affects user experience when registering external worktrees.

---

### CLARIFY: Should we validate worktree existence before registration?

**Questions to answer:**
1. Should we check if the path exists on filesystem?
2. Should we verify it's a valid git worktree (via `git worktree list`)?
3. What should happen if path doesn't exist - hard error or proceed with registration?

**Options:**
- **Option A**: No validation, register any path (dashboard tracks it)
  - Pros: Simpler implementation, allows pre-registration
  - Cons: Could register non-existent or invalid paths
- **Option B**: Validate path exists, error if not
  - Pros: Catches obvious mistakes
  - Cons: Cannot pre-register worktrees
- **Option C**: Validate path exists AND is git worktree
  - Pros: Strongest validation, prevents garbage data
  - Cons: More complex, requires git worktree list parsing

**Impact:** Affects Story 2 and Story 3 implementation. Affects what gets stored in dashboard and whether registration can fail.

**Recommendation:** Probably Option B (validate existence) - matches user expectation that they're registering something that exists now.

---

### CLARIFY: Should we support --tracker and --team overrides?

The issue description mentions `--tracker TYPE` and `--team TEAM` as optional arguments.

**Questions to answer:**
1. Should these override project config or is project config always authoritative?
2. What's the use case for overriding tracker type for a single worktree?
3. Does team override make sense when issue ID already contains team (TEAM-123)?

**Options:**
- **Option A**: Do not support overrides, always use project config
  - Pros: Simpler, consistent with project-wide settings
  - Cons: Less flexible, cannot handle edge cases
- **Option B**: Support overrides for advanced users
  - Pros: Maximum flexibility
  - Cons: Confusing - why would one worktree have different tracker type?
- **Option C**: Support only team override (for cross-team work)
  - Pros: Addresses legitimate use case
  - Cons: Inconsistent with issue ID team component

**Impact:** Affects command API design and complexity of Stories 2-3.

**Recommendation:** Probably Option A (no overrides) - tracker type is project-wide, and team is already in issue ID. Keep it simple unless there's a concrete use case.

---

## Total Estimates

**Story Breakdown:**
- Story 1 (Auto-detect and register current worktree): 3-4 hours
- Story 2 (Explicitly register worktree by issue ID): 2-3 hours
- Story 3 (Handle registration errors gracefully): 1-2 hours

**Total Range:** 6-9 hours

**Confidence:** High

**Reasoning:**
- All core infrastructure already exists (ServerClient, IssueId, GitAdapter, ConfigFileRepository)
- This is primarily a CLI command layer task - orchestration of existing components
- Only new code is argument parsing and error handling
- Similar patterns already exist in `start.scala` and `open.scala` commands
- No new domain logic required
- No database migrations or complex integrations
- Classification matches: Simple issue, small scope, reuses infrastructure

---

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests**: Domain logic, value objects (mostly already tested)
2. **Integration Tests**: Command execution with mocked filesystem/git/server
3. **E2E Scenario Tests**: Automated verification of the Gherkin scenarios via BATS

**Story-Specific Testing Notes:**

**Story 1:**
- Unit: `IssueId.fromBranch()` already tested, no new logic
- Integration:
  - Mock GitAdapter to return various branch names
  - Mock ServerClient to verify registration called with correct params
  - Test config loading failure paths
- E2E:
  - Create test worktree with branch "TEST-123-foo"
  - Run `iw register` in that directory
  - Verify success output and dashboard registration (if server available)
  - Test in non-git directory and verify error

**Story 2:**
- Unit: `IssueId.parse()` already tested
- Integration:
  - Test argument parsing with various formats
  - Test path defaulting (no --path vs with --path)
  - Test invalid issue ID rejection
- E2E:
  - Create worktree at known path
  - Run `iw register ISSUE-123` from that path
  - Run `iw register ISSUE-456 --path /external/path` from elsewhere
  - Verify both registration calls succeed

**Story 3:**
- Unit: No new domain logic
- Integration:
  - Mock config read failures
  - Mock server errors (500, connection refused)
  - Verify warning messages, not errors
- E2E:
  - Run `iw register` in directory without .iw/config.conf
  - Verify error message mentions `iw init`
  - Stop dashboard server and run `iw register`
  - Verify warning but exit code 0

**Test Data Strategy:**
- Use temporary directories for E2E tests (BATS `setup` and `teardown`)
- Create minimal valid .iw/config.conf fixtures
- Mock server responses in integration tests
- Use real git worktrees in E2E tests (create via `git worktree add`)

**Regression Coverage:**
- Ensure existing `iw start` still registers worktrees
- Ensure existing `IssueId` parsing behavior unchanged
- Ensure config loading patterns consistent across commands

---

## Deployment Considerations

### Database Changes
None - registration API already exists, no schema changes needed.

### Configuration Changes
None - uses existing `.iw/config.conf` structure.

### Rollout Strategy
Single deployment - this is a new command that doesn't affect existing functionality.
- Add `.iw/commands/register.scala`
- No changes to existing commands
- No breaking changes to any APIs

### Rollback Plan
If issues found, simply remove `.iw/commands/register.scala`. No other changes to roll back.

---

## Dependencies

### Prerequisites
- Project must be initialized with `.iw/config.conf`
- Must be in a git repository (for auto-detection)
- Dashboard server API must be available (but failure is non-critical)

### Story Dependencies
- Story 1 must be complete before Story 2 (Story 2 extends Story 1)
- Story 3 can be implemented in parallel with Stories 1-2 (it's just error handling)
- All three stories can be in a single command implementation (natural progression)

### External Blockers
None - all infrastructure already exists in codebase.

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: Auto-detect and register current worktree** - Core functionality, establishes pattern, enables immediate value (register current worktree)
2. **Story 2: Explicitly register worktree by issue ID** - Natural extension, adds flexibility for external worktrees
3. **Story 3: Handle registration errors gracefully** - Polish and robustness, completes user experience

**Iteration Plan:**

- **Iteration 1** (Story 1): Core auto-detection - users can register current worktree with `iw register`
- **Iteration 2** (Stories 2-3): Add explicit arguments and error handling - complete feature with all edge cases

**Implementation Notes:**
Given the simple classification and tight scope, all three stories can likely be implemented in a single `register.scala` command file. The natural implementation flow is:

1. Implement basic auto-detection (Story 1)
2. Add argument parsing for explicit issue ID and --path (Story 2)
3. Add comprehensive error handling throughout (Story 3)

---

## Documentation Requirements

- [ ] Add `register` command to README or CLI help
- [ ] Document `--path` option behavior (once CLARIFY resolved)
- [ ] Add example usage in project documentation
- [ ] Update `.claude/skills/` if command skills are auto-generated

---

**Analysis Status:** Ready for Review - Pending CLARIFY Resolution

**Next Steps:**
1. Resolve CLARIFY markers (path handling, validation, override options)
2. Confirm estimated scope aligns with Simple classification
3. Implement Story 1 first to validate approach
4. Consider consolidating all stories into single implementation if patterns emerge cleanly
