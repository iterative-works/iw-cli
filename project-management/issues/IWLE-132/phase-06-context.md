# Phase 6 Context: Doctor validates GitHub setup

**Issue:** IWLE-132
**Phase:** 6 - Doctor validates GitHub setup
**Story:** Story 6 from analysis.md

---

## Goals

This phase adds GitHub-specific health checks to the `iw doctor` command. When a project is configured with the GitHub tracker, the doctor command should validate:

1. **gh CLI is installed** - The `gh` command is available in PATH
2. **gh CLI is authenticated** - User has logged in via `gh auth login`
3. **Repository is accessible** - User has access to the configured repository

These checks help users troubleshoot GitHub integration issues by providing clear diagnostic output and actionable instructions.

---

## Scope

### In Scope

- Create `github.hook-doctor.scala` following the hook pattern used by `issue.hook-doctor.scala` and `start.hook-doctor.scala`
- Implement three GitHub-specific checks:
  - `gh CLI installed` - Check if `gh` command is available
  - `gh CLI authenticated` - Check via `gh auth status`
  - `Repository accessible` - Check via `gh repo view` (optional, may be deferred)
- Skip all GitHub checks when tracker is not GitHub
- Provide user-friendly error messages with installation/authentication instructions
- Add E2E tests for doctor command with GitHub tracker

### Out of Scope

- Modifying the existing doctor.scala command (uses hook discovery mechanism)
- Adding checks for other tracker types
- Network connectivity checks
- GitHub rate limit checks
- Checking specific repository permissions

---

## Dependencies

### From Previous Phases

- **Phase 1**: `IssueTrackerType.GitHub` enum case
- **Phase 1**: `ProjectConfiguration.repository` field
- **Phase 4**: `GitHubClient.validateGhPrerequisites()` - Validates gh installation and authentication
- **Phase 4**: `GitHubClient.formatGhNotInstalledError()` - Error message for not installed
- **Phase 4**: `GitHubClient.formatGhNotAuthenticatedError()` - Error message for not authenticated

### External Dependencies

- Hook discovery mechanism in `doctor.scala` (reads `IW_HOOK_CLASSES` env var)
- `DoctorChecks.scala` - `Check` and `CheckResult` types
- `CommandRunner.isCommandAvailable()` - For checking gh CLI presence
- `CommandRunner.execute()` - For running `gh auth status`

---

## Technical Approach

### 1. Create GitHub Doctor Hook

Create `.iw/commands/github.hook-doctor.scala` following the established pattern:

```scala
// PURPOSE: Doctor checks for GitHub tracker - validates gh CLI prerequisites
// PURPOSE: Exposes checks to verify gh CLI installation and authentication

import iw.core.*

object GitHubHookDoctor:
  // Check gh CLI is installed
  def checkGhInstalled(config: ProjectConfiguration): CheckResult =
    if config.trackerType != IssueTrackerType.GitHub then
      CheckResult.Skip("Not using GitHub")
    else if CommandRunner.isCommandAvailable("gh") then
      CheckResult.Success("Installed")
    else
      CheckResult.Error("Not found", "Install: https://cli.github.com/")

  // Check gh CLI is authenticated
  def checkGhAuthenticated(config: ProjectConfiguration): CheckResult =
    if config.trackerType != IssueTrackerType.GitHub then
      CheckResult.Skip("Not using GitHub")
    else if !CommandRunner.isCommandAvailable("gh") then
      CheckResult.Skip("gh not installed")
    else
      // Use validateGhPrerequisites from Phase 4 to check auth
      GitHubClient.validateGhPrerequisites(config.repository.getOrElse("")) match
        case Right(_) => CheckResult.Success("Authenticated")
        case Left(GitHubClient.GhNotAuthenticated) =>
          CheckResult.Error("Not authenticated", "Run: gh auth login")
        case Left(_) => CheckResult.Success("Authenticated") // Already checked installation

  // Expose checks for discovery
  val check: Check = Check("gh CLI", checkGhInstalled)
  val authCheck: Check = Check("gh auth", checkGhAuthenticated)
```

### 2. Reuse Phase 4 Utilities

The `GitHubClient.validateGhPrerequisites()` function from Phase 4 already handles:
- Checking if `gh` command is available
- Running `gh auth status` to verify authentication
- Detecting exit code 4 for authentication failures

We can reuse this logic rather than duplicating it.

### 3. Hook Discovery

The doctor command uses the `IW_HOOK_CLASSES` environment variable to discover hooks. The hook file must:
- Be named `*.hook-doctor.scala`
- Export a `check` value of type `Check`
- Be compiled and available in the classpath

### 4. Handle Multiple Checks

Unlike the Linear hook (single check), GitHub needs multiple checks. Options:
- **Option A**: Single hook with combined check (simpler but less granular)
- **Option B**: Expose multiple check values (more granular output)

Recommend **Option A** for simplicity - combine both checks into one "gh CLI" check that first verifies installation, then authentication.

---

## Files to Modify

### New Files
- `.iw/commands/github.hook-doctor.scala` - GitHub doctor hook

### Modified Files
- `.iw/test/doctor.bats` - Add E2E tests for GitHub tracker doctor checks

### Files to Review (no changes expected)
- `.iw/commands/doctor.scala` - Verify hook discovery mechanism
- `.iw/core/GitHubClient.scala` - Review `validateGhPrerequisites()`

---

## Testing Strategy

### Unit Tests

1. **checkGhInstalled - skips for non-GitHub**
   - Input: config with `IssueTrackerType.Linear`
   - Expected: `CheckResult.Skip("Not using GitHub")`

2. **checkGhInstalled - succeeds when gh available**
   - Input: config with `IssueTrackerType.GitHub`, gh available
   - Expected: `CheckResult.Success("Installed")`

3. **checkGhInstalled - fails when gh not available**
   - Input: config with `IssueTrackerType.GitHub`, gh not available
   - Expected: `CheckResult.Error("Not found", "Install: https://cli.github.com/")`

4. **checkGhAuthenticated - skips for non-GitHub**
   - Input: config with `IssueTrackerType.Linear`
   - Expected: `CheckResult.Skip("Not using GitHub")`

5. **checkGhAuthenticated - skips when gh not installed**
   - Input: config with `IssueTrackerType.GitHub`, gh not available
   - Expected: `CheckResult.Skip("gh not installed")`

6. **checkGhAuthenticated - succeeds when authenticated**
   - Input: config with `IssueTrackerType.GitHub`, gh auth status passes
   - Expected: `CheckResult.Success("Authenticated")`

7. **checkGhAuthenticated - fails when not authenticated**
   - Input: config with `IssueTrackerType.GitHub`, gh auth status fails with exit 4
   - Expected: `CheckResult.Error("Not authenticated", "Run: gh auth login")`

### E2E Tests

1. **doctor passes for GitHub project when gh is installed and authenticated**
   - Setup: GitHub config, gh available and authenticated
   - Run: `iw doctor`
   - Assert: Exit 0, shows "gh CLI" ✓, shows "gh auth" ✓

2. **doctor fails for GitHub project when gh not installed**
   - Setup: GitHub config, mock gh not available
   - Run: `iw doctor`
   - Assert: Exit 1, shows "gh CLI" ✗, shows installation URL

3. **doctor fails for GitHub project when gh not authenticated**
   - Setup: GitHub config, mock gh available but auth fails
   - Run: `iw doctor`
   - Assert: Exit 1, shows "gh CLI" ✓, shows "gh auth" ✗

4. **doctor skips gh checks for Linear project**
   - Setup: Linear config
   - Run: `iw doctor`
   - Assert: Shows "gh CLI" - Skipped

---

## Acceptance Criteria

1. ✅ Doctor command checks gh CLI installation when tracker is GitHub
2. ✅ Doctor command checks gh CLI authentication when tracker is GitHub
3. ✅ Clear error message with installation URL when gh not installed
4. ✅ Clear error message with `gh auth login` instruction when not authenticated
5. ✅ Checks are skipped when tracker is not GitHub
6. ✅ Consistent with existing Linear/YouTrack doctor check patterns
7. ✅ E2E tests cover all GitHub doctor scenarios

---

## Implementation Notes

### Consideration: Repository Access Check

Story 6 mentions checking "Repository accessible" via `gh repo view`. This is valuable but adds complexity:
- Requires network connectivity
- May be slow
- Needs repository field from config

**Recommendation**: Start without repository access check. Add it later if users report confusion about repository permission issues.

### Consideration: Combined vs Separate Checks

The existing `IssueHookDoctor` exposes a single check. For consistency, we could:
- Combine installation + auth into one check (simpler output)
- Or expose separate checks for granular diagnosis

**Recommendation**: Use two checks (`gh CLI` and `gh auth`) for better diagnostics.

### Error Message Format

Follow the established pattern from Phase 4:
- Short error summary in the check result message
- Actionable hint with specific command or URL
- Consistent formatting with other doctor checks

---

## Reference: Gherkin Scenario from Analysis

```gherkin
Feature: Doctor validates GitHub tracker configuration
  As a developer troubleshooting iw-cli setup
  I want the doctor command to check GitHub prerequisites
  So that I know if everything is configured correctly

Scenario: All GitHub prerequisites met
  Given the project is configured with GitHub tracker
  And gh CLI is installed and authenticated
  And I have access to the configured repository
  When I run "iw doctor"
  Then I see a checkmark for "gh CLI installed"
  And I see a checkmark for "gh CLI authenticated"
  And I see a checkmark for "Repository accessible"

Scenario: gh CLI not installed
  Given the project is configured with GitHub tracker
  And gh CLI is not installed
  When I run "iw doctor"
  Then I see a failure for "gh CLI installed"
  And I see instructions to install gh CLI

Scenario: gh CLI not authenticated
  Given the project is configured with GitHub tracker
  And gh CLI is installed but not authenticated
  When I run "iw doctor"
  Then I see a failure for "gh CLI authenticated"
  And I see instructions to run "gh auth login"
```

---

## Estimated Effort

**Original estimate**: 3-4 hours
**Confidence**: High - straightforward extension of existing patterns

---

## Refactoring Decisions

### R1: Fix FCIS Architecture Violations (2025-12-23)

**Trigger:** Code review identified architecture violations:
1. `GitHubHookDoctor` is in `iw.core` but imports from `iw.core.infrastructure.CommandRunner` (core depending on infrastructure)
2. `GhPrerequisiteError` in `GitHubClient.scala` uses Scala 2 sealed trait pattern instead of Scala 3 enum

**Decision:** Move `GitHubHookDoctor.scala` to `iw.core.infrastructure` package and convert `GhPrerequisiteError` to Scala 3 enum.

**Scope:**
- Files affected:
  - `.iw/core/GitHubHookDoctor.scala` → move to `.iw/core/infrastructure/`
  - `.iw/core/GitHubClient.scala` → convert GhPrerequisiteError to enum
  - `.iw/core/test/GitHubHookDoctorTest.scala` → update package imports
- Boundaries: Do NOT touch doctor.scala hook discovery or E2E tests

**Approach:**
1. Move GitHubHookDoctor.scala to infrastructure package, update package declaration
2. Convert GhPrerequisiteError sealed trait to Scala 3 enum
3. Update all imports and references
4. Verify all tests still pass
