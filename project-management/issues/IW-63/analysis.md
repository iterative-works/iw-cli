# Story-Driven Analysis: Add 'iw register' command to manually register worktrees to dashboard

**Issue:** IW-63
**Created:** 2025-12-27
**Status:** Draft
**Classification:** Simple

## Problem Statement

Users who create worktrees outside of `iw start` (e.g., via direct `git worktree add`, from external tools, or in parallel project contexts) cannot register these worktrees with the dashboard for monitoring. This creates an inconsistency where only iw-created worktrees are visible in the dashboard.

The core value is **convenience and completeness**: users should be able to register any worktree to the dashboard regardless of how it was created.

## User Stories

### Story 1: Register current worktree to dashboard

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

Scenario: Fail gracefully when not in a git repository
  Given I am in a directory that is not a git repository
  When I run 'iw register'
  Then I see error "Not in a git repository"
  And the command exits with code 1

Scenario: Fail gracefully when branch name has no issue ID
  Given I am in a worktree directory
  And the current branch is "main"
  When I run 'iw register'
  Then I see error "Cannot extract issue ID from branch 'main' (expected: TEAM-123 or TEAM-123-description)"
  And the command exits with code 1

Scenario: Fail gracefully when project is not initialized
  Given I am in a directory without .iw/config.conf
  When I run 'iw register'
  Then I see error "Cannot read configuration"
  And I see info "Run './iw init' to initialize the project"
  And the command exits with code 1

Scenario: Warn when dashboard server is unavailable
  Given I am in a valid worktree with issue branch "IW-42-feature"
  And the dashboard server is not running and cannot be started
  When I run 'iw register'
  Then I see warning "Failed to register worktree with dashboard: [server error]"
  And the command exits with code 0
```

**Estimated Effort:** 2-3 hours
**Complexity:** Straightforward

**Technical Feasibility:**
This story is straightforward because it reuses existing infrastructure:
- `GitAdapter.getCurrentBranch()` already exists
- `IssueId.fromBranch()` already handles parsing
- `ConfigFileRepository.read()` already loads project config
- `ServerClient.registerWorktree()` already handles the API call
- `os.pwd` provides current directory

The only new code is orchestration of existing components.

**Acceptance:**
- Running `iw register` in a worktree with branch "TEAM-123-foo" registers to dashboard
- Clear error messages for missing config, invalid branch names, or non-git directories
- Success message includes issue ID and path for confirmation
- Dashboard failures are warnings (best-effort), not errors

---

## Architectural Sketch

**Purpose:** List WHAT components the story needs, not HOW they're implemented.

**Domain Layer:**
- `IssueId` value object (already exists)
- `ProjectConfiguration` (already exists)

**Application Layer:**
- CLI orchestration logic in `register.scala`:
  - Load project config
  - Get current branch
  - Parse issue ID from branch
  - Call registration service
  - Handle errors appropriately

**Infrastructure Layer:**
- `ConfigFileRepository.read()` - read project config (already exists)
- `GitAdapter.getCurrentBranch()` - get current branch (already exists)
- `IssueId.fromBranch()` - parse issue ID (already exists)
- `ServerClient.registerWorktree()` - register to dashboard (already exists)
- `Output` - user feedback (already exists)

**Presentation Layer:**
- `register.scala` - new command script in `.iw/commands/`

---

## Total Estimates

**Story Breakdown:**
- Story 1 (Register current worktree to dashboard): 2-3 hours

**Total Range:** 2-3 hours

**Confidence:** High

**Reasoning:**
- All core infrastructure already exists (ServerClient, IssueId, GitAdapter, ConfigFileRepository)
- This is primarily a CLI command layer task - orchestration of existing components
- Similar patterns already exist in `start.scala` and `open.scala` commands
- No new domain logic required
- No argument parsing complexity (no arguments)

---

## Testing Approach

**Unit Tests:**
- `IssueId.fromBranch()` already tested, no new logic needed

**E2E Tests (BATS):**
- Create test worktree with branch "TEST-123-foo", run `iw register`, verify success output
- Run `iw register` in non-git directory, verify error
- Run `iw register` on branch "main", verify error about missing issue ID
- Run `iw register` in directory without .iw/config.conf, verify error

**Test Data Strategy:**
- Use temporary directories for E2E tests (BATS `setup` and `teardown`)
- Create minimal valid .iw/config.conf fixtures
- Use real git worktrees in E2E tests (create via `git worktree add`)

---

## Deployment Considerations

### Database Changes
None - registration API already exists.

### Configuration Changes
None - uses existing `.iw/config.conf` structure.

### Rollout Strategy
Single deployment - add `.iw/commands/register.scala`. No breaking changes.

### Rollback Plan
Remove `.iw/commands/register.scala`. No other changes to roll back.

---

## Dependencies

### Prerequisites
- Project must be initialized with `.iw/config.conf`
- Must be in a git repository
- Branch name must contain issue ID (TEAM-123 format)
- Dashboard server API must be available (but failure is non-critical)

### External Blockers
None - all infrastructure already exists in codebase.

---

## Implementation Notes

The command follows the same pattern as `open.scala`:
1. Load config (fail if missing)
2. Get current branch via `GitAdapter.getCurrentBranch()`
3. Parse issue ID via `IssueId.fromBranch()` (fail if cannot parse)
4. Call `ServerClient.registerWorktree()` (warn if fails, don't error)
5. Output success message

No command-line arguments needed - everything is auto-detected from current directory and branch.

---

**Analysis Status:** Ready for Implementation

**Next Steps:**
1. `/iterative-works:ag-create-tasks IW-63` - Generate task index
2. `/iterative-works:ag-implement IW-63` - Start implementation
