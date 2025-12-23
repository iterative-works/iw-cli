# Phase 4 Context: Handle gh CLI prerequisites

**Issue:** IWLE-132
**Phase:** 4 of 6
**Story:** Story 4 - GitHub tracker prerequisites validation
**Status:** Ready for Implementation
**Estimated Effort:** 4-6 hours

---

## Goals

This phase adds comprehensive error detection and user-friendly messaging for GitHub CLI prerequisite failures. The goal is to validate gh CLI availability and authentication BEFORE attempting to create issues, providing clear actionable guidance when prerequisites are not met.

**What this phase accomplishes:**
1. Detect when `gh` CLI is not installed
2. Detect when `gh` is installed but not authenticated
3. Detect when repository is not accessible (permissions)
4. Provide clear error messages with installation/authentication instructions
5. Use proper exit codes for all error scenarios
6. Add prerequisite validation to GitHubClient

**User value:**
- Clear guidance when setup is incomplete (not cryptic error messages)
- Saves time by identifying exact problem (vs debugging unclear failures)
- Lowers barrier to entry for new users (tells them what to do next)

---

## Scope

### In Scope

**Error Detection:**
- Command not found (gh CLI not installed)
- Exit code 4 from gh CLI (not authenticated)
- Permission denied errors (no repository access)
- Generic gh CLI failures (network errors, etc.)

**User Messaging:**
- Installation instructions for gh CLI (link to https://cli.github.com/)
- Authentication instructions (`gh auth login`)
- Permission error explanation (repository access required)
- Helpful context in all error messages

**Implementation Changes:**
- Add `validateGhPrerequisites()` to GitHubClient
- Call validation before `createIssue()` operations
- Enhanced error detection in CommandRunner (optional)
- Update error messages in feedback command

### Out of Scope

**Not in this phase:**
- Automatic gh installation/authentication (user must do this)
- Doctor command checks (Phase 6)
- Issue viewing prerequisite checks (Phase 5)
- Repository accessibility proactive checks (only on error)
- Offline mode or graceful degradation

**Deferred decisions:**
- Whether to cache gh CLI availability check results
- Whether to add --skip-validation flag for power users
- Integration with `iw doctor` (Phase 6 handles this)

---

## Dependencies

### Prerequisites from Previous Phases

**From Phase 1:**
- `IssueTrackerType.GitHub` enum case exists
- Config schema supports GitHub tracker
- `iw init --tracker github` creates valid configuration

**From Phase 2:**
- Repository auto-detection from git remote works
- Config stores repository in `owner/repo` format

**From Phase 3 (Critical Dependencies):**
- `GitHubClient.createIssue()` exists and is functional
- `CommandRunner.execute()` returns `Either[String, String]`
- `feedback.scala` calls GitHubClient for GitHub tracker
- Error handling pattern: Left(error message) on failure
- Function injection pattern (`execCommand` parameter) enables testing

**From Infrastructure:**
- `CommandRunner.execute()` wraps shell command execution
- `CommandRunner.isCommandAvailable()` exists for command detection
- Error detection via exit codes and stderr (Scala process API)

### External Dependencies

**Required tools:**
- `gh` CLI (GitHub's official CLI tool)
- `which` command (for availability detection)

**Expected `gh` CLI behavior:**
- Exit code 0 on success
- Exit code 4 when not authenticated
- Exit code 1 for general errors
- Stderr contains error details
- "Cannot run program" exception when not installed

---

## Technical Approach

### High-Level Strategy

**Detection-First Approach:**
1. Check prerequisites BEFORE attempting operation
2. Provide specific error messages for each failure mode
3. Include actionable next steps in every error message
4. Fail fast with clear guidance (don't retry blindly)

**Error Categories:**

```scala
sealed trait GhPrerequisiteError
case object GhNotInstalled extends GhPrerequisiteError
case object GhNotAuthenticated extends GhPrerequisiteError
case class GhOtherError(message: String) extends GhPrerequisiteError
```

**Validation Flow:**

```
validateGhPrerequisites(repository)
  ├─> Check gh CLI installed
  │     └─> if not found → GhNotInstalled
  ├─> Check gh authentication
  │     └─> run `gh auth status`
  │     └─> exit code 4 → GhNotAuthenticated
  └─> if all pass → Right(Unit)
```

### Implementation Pattern

**Add validation method to GitHubClient:**

```scala
def validateGhPrerequisites(
  repository: String,
  execCommand: (String, Array[String]) => Either[String, String] = ...
): Either[GhPrerequisiteError, Unit] =
  // 1. Check gh CLI installed
  if !CommandRunner.isCommandAvailable("gh") then
    return Left(GhNotInstalled)
  
  // 2. Check gh authentication
  val authCheck = execCommand("gh", Array("auth", "status"))
  authCheck match
    case Left(error) if isAuthenticationError(error) =>
      Left(GhNotAuthenticated)
    case Left(error) =>
      Left(GhOtherError(error))
    case Right(_) =>
      Right(())
```

**Update createIssue to validate first:**

```scala
def createIssue(...): Either[String, CreatedIssue] =
  // Validate prerequisites before attempting creation
  validateGhPrerequisites(repository, execCommand) match
    case Left(GhNotInstalled) =>
      Left(formatGhNotInstalledError())
    case Left(GhNotAuthenticated) =>
      Left(formatGhNotAuthenticatedError())
    case Left(GhOtherError(msg)) =>
      Left(s"gh CLI error: $msg")
    case Right(_) =>
      // Proceed with issue creation (existing logic)
      val args = buildCreateIssueCommand(...)
      ...
```

### Error Detection Strategy

**From analysis.md Technical Decisions:**

1. **Command not found:**
   - Use `CommandRunner.isCommandAvailable("gh")`
   - Check before any gh CLI calls
   - Error message: "gh CLI is not installed"

2. **Not authenticated (exit code 4):**
   - Run `gh auth status` 
   - Parse exit code (4 = not authenticated)
   - Error message: "gh is not authenticated"

3. **Permission denied:**
   - Attempt operation, catch permission error in stderr
   - Look for "permission denied" or "not found" in error
   - Error message: "You don't have access to repository {owner/repo}"

4. **Other errors:**
   - Show actual stderr from gh CLI
   - Generic error: "gh CLI error: {stderr}"

### Error Message Format

**GhNotInstalled:**
```
gh CLI is not installed

The GitHub tracker requires the gh CLI tool.

Install gh CLI:
  https://cli.github.com/

After installation, authenticate with:
  gh auth login
```

**GhNotAuthenticated:**
```
gh is not authenticated

You need to authenticate with GitHub before creating issues.

Run this command to authenticate:
  gh auth login

Follow the prompts to sign in with your GitHub account.
```

**Permission Denied:**
```
You don't have access to repository {owner/repo}

This could mean:
- The repository is private and you don't have access
- The repository doesn't exist
- Your authentication doesn't include this organization

Verify you can access the repository:
  gh repo view {owner/repo}
```

---

## Files to Modify

### Core Implementation

**`.iw/core/GitHubClient.scala`** (Major changes)
- Add `GhPrerequisiteError` sealed trait and cases
- Add `validateGhPrerequisites()` method
- Add `isAuthenticationError()` helper
- Add `formatGhNotInstalledError()` helper
- Add `formatGhNotAuthenticatedError()` helper
- Update `createIssue()` to call validation first

**Estimated lines:** ~80-100 new lines, ~10 modified

### Command Integration

**`.iw/commands/feedback.scala`** (Minor changes)
- Error handling already present (Left/Right pattern)
- Error messages already displayed to user
- No changes needed (GitHubClient errors propagate correctly)

**Estimated lines:** 0 changes (error handling already sufficient)

### Testing

**`.iw/core/test/GitHubClientTest.scala`** (Major additions)
- Test `validateGhPrerequisites()` with gh not installed
- Test `validateGhPrerequisites()` with gh not authenticated
- Test `validateGhPrerequisites()` with valid setup
- Test `createIssue()` fails when gh not installed
- Test `createIssue()` fails when gh not authenticated
- Test error message formatting

**Estimated lines:** ~100-120 new test lines

**`.iw/test/feedback.bats`** (Major additions)
- E2E test: gh not installed scenario
- E2E test: gh not authenticated scenario
- E2E test: permission denied scenario
- Mock gh CLI to simulate error conditions

**Estimated lines:** ~60-80 new test lines

### Optional Enhancement

**`.iw/core/CommandRunner.scala`** (Optional changes)
- Enhanced error detection for exit code 4
- Better error message extraction from stderr
- Not required (can handle in GitHubClient)

**Estimated lines:** ~20 lines if modified

---

## Testing Strategy

### Unit Tests (munit)

**Test File:** `.iw/core/test/GitHubClientTest.scala`

**Test Cases:**

1. **Prerequisite Validation:**
   ```scala
   test("validateGhPrerequisites - gh not installed") {
     // Mock isCommandAvailable to return false
     val result = GitHubClient.validateGhPrerequisites(
       "owner/repo",
       execCommand = mockExecNotFound
     )
     assert(result == Left(GhNotInstalled))
   }
   
   test("validateGhPrerequisites - gh not authenticated") {
     // Mock gh auth status to return exit code 4
     val result = GitHubClient.validateGhPrerequisites(
       "owner/repo",
       execCommand = mockExecAuthError
     )
     assert(result == Left(GhNotAuthenticated))
   }
   
   test("validateGhPrerequisites - gh authenticated") {
     val result = GitHubClient.validateGhPrerequisites(
       "owner/repo",
       execCommand = mockExecSuccess
     )
     assert(result == Right(()))
   }
   ```

2. **Error Message Formatting:**
   ```scala
   test("formatGhNotInstalledError contains installation instructions") {
     val error = GitHubClient.formatGhNotInstalledError()
     assert(error.contains("gh CLI is not installed"))
     assert(error.contains("https://cli.github.com/"))
     assert(error.contains("gh auth login"))
   }
   
   test("formatGhNotAuthenticatedError contains auth instructions") {
     val error = GitHubClient.formatGhNotAuthenticatedError()
     assert(error.contains("gh is not authenticated"))
     assert(error.contains("gh auth login"))
   }
   ```

3. **Integration with createIssue:**
   ```scala
   test("createIssue fails when gh not installed") {
     val result = GitHubClient.createIssue(
       repository = "owner/repo",
       title = "Test",
       description = "",
       issueType = FeedbackParser.IssueType.Bug,
       execCommand = mockExecNotFound
     )
     assert(result.isLeft)
     assert(result.left.get.contains("gh CLI is not installed"))
   }
   ```

**Test Utilities:**

```scala
// Mock functions for testing
def mockExecNotFound(cmd: String, args: Array[String]): Either[String, String] =
  Left("Command not found: gh")

def mockExecAuthError(cmd: String, args: Array[String]): Either[String, String] =
  if args.contains("auth") && args.contains("status") then
    Left("Command failed: gh auth status: exit status 4")
  else
    Right("{\"number\": 1, \"url\": \"https://...\"}")

def mockExecSuccess(cmd: String, args: Array[String]): Either[String, String] =
  Right(if args.contains("auth") then "Logged in" else "{\"number\": 1, \"url\": \"https://...\"}")
```

### E2E Tests (BATS)

**Test File:** `.iw/test/feedback.bats`

**Test Scenarios from analysis.md:**

1. **Scenario: gh CLI not installed**
   ```bash
   @test "feedback: error when gh CLI not installed (GitHub)" {
     cd "$BATS_TEST_TMPDIR"
     git init
     git remote add origin https://github.com/test/repo.git
     
     # Create GitHub config
     mkdir -p .iw
     cat > .iw/config.conf <<EOF
   tracker {
     type = github
     repository = "test/repo"
   }
   EOF
     
     # Temporarily hide gh from PATH
     export PATH="/usr/bin:/bin"  # Exclude typical gh locations
     
     run iw feedback "Test issue"
     
     assert_failure
     assert_output --partial "gh CLI is not installed"
     assert_output --partial "https://cli.github.com/"
   }
   ```

2. **Scenario: gh CLI not authenticated**
   ```bash
   @test "feedback: error when gh not authenticated (GitHub)" {
     cd "$BATS_TEST_TMPDIR"
     git init
     git remote add origin https://github.com/test/repo.git
     
     mkdir -p .iw
     cat > .iw/config.conf <<EOF
   tracker {
     type = github
     repository = "test/repo"
   }
   EOF
     
     # Mock gh CLI that returns exit code 4 for auth status
     cat > "$BATS_TEST_TMPDIR/gh" <<'GHSCRIPT'
   #!/bin/bash
   if [[ "$1" == "auth" && "$2" == "status" ]]; then
     echo "You are not logged in" >&2
     exit 4
   fi
   GHSCRIPT
     chmod +x "$BATS_TEST_TMPDIR/gh"
     export PATH="$BATS_TEST_TMPDIR:$PATH"
     
     run iw feedback "Test issue"
     
     assert_failure
     assert_output --partial "gh is not authenticated"
     assert_output --partial "gh auth login"
   }
   ```

3. **Scenario: Repository not accessible**
   ```bash
   @test "feedback: error when repository not accessible (GitHub)" {
     cd "$BATS_TEST_TMPDIR"
     git init
     git remote add origin https://github.com/private/repo.git
     
     mkdir -p .iw
     cat > .iw/config.conf <<EOF
   tracker {
     type = github
     repository = "private/repo"
   }
   EOF
     
     # Mock gh CLI that returns permission error
     cat > "$BATS_TEST_TMPDIR/gh" <<'GHSCRIPT'
   #!/bin/bash
   if [[ "$1" == "auth" ]]; then
     exit 0
   elif [[ "$1" == "issue" && "$2" == "create" ]]; then
     echo "Could not resolve to a Repository" >&2
     exit 1
   fi
   GHSCRIPT
     chmod +x "$BATS_TEST_TMPDIR/gh"
     export PATH="$BATS_TEST_TMPDIR:$PATH"
     
     run iw feedback "Test issue"
     
     assert_failure
     assert_output --partial "permission denied" || \
     assert_output --partial "not found" || \
     assert_output --partial "Could not resolve"
   }
   ```

**Test Coverage:**
- ✅ gh CLI not installed detection
- ✅ gh CLI not authenticated detection  
- ✅ Repository permission errors
- ✅ Error message quality (contains instructions)
- ✅ Proper exit codes (non-zero on failure)
- ✅ No regression to existing functionality

### Mock Strategy

**For unit tests:**
- Use function injection (`execCommand` parameter)
- Mock returns specific error patterns
- Test all error detection branches

**For E2E tests:**
- Create fake `gh` script in temp PATH
- Script returns appropriate exit codes
- Verify actual command output to user

**No real GitHub calls:**
- All tests run in isolation
- No network dependencies
- Fast and repeatable

---

## Acceptance Criteria

### Functional Requirements

- [ ] When gh CLI is not installed, feedback command fails with clear error
- [ ] Error message includes link to https://cli.github.com/
- [ ] Error message mentions `gh auth login` for next step
- [ ] When gh is installed but not authenticated, feedback command fails with clear error
- [ ] Authentication error message instructs user to run `gh auth login`
- [ ] When repository is not accessible, feedback command fails with clear error
- [ ] All error scenarios result in non-zero exit code
- [ ] Validation happens BEFORE attempting to create issue
- [ ] Existing Linear/YouTrack feedback is not affected

### Technical Requirements

- [ ] `GitHubClient.validateGhPrerequisites()` method exists
- [ ] Validation detects gh not installed (via CommandRunner.isCommandAvailable)
- [ ] Validation detects gh not authenticated (via `gh auth status` exit code 4)
- [ ] Error messages formatted as multi-line with clear instructions
- [ ] Function injection pattern maintained for testability
- [ ] Unit tests cover all prerequisite validation cases
- [ ] E2E tests verify error messages in real command execution
- [ ] No new dependencies introduced

### Quality Requirements

- [ ] Error messages are user-friendly (not technical jargon)
- [ ] Each error message includes actionable next step
- [ ] Error detection is specific (not generic "something failed")
- [ ] Tests pass consistently (no flaky PATH-dependent tests)
- [ ] Code follows existing patterns (consistent with LinearClient error handling)
- [ ] No regressions in existing test suite (all 115+ tests still pass)

### Documentation Requirements

- [ ] Inline comments explain error detection strategy
- [ ] Test descriptions clearly state what scenario is being tested
- [ ] Error messages are self-documenting (no separate docs needed)

---

## Implementation Checklist

### Phase A: Core Validation Logic (2-3h)

- [ ] Add `GhPrerequisiteError` sealed trait to GitHubClient.scala
- [ ] Implement `validateGhPrerequisites()` method
- [ ] Add `isAuthenticationError()` helper to detect exit code 4
- [ ] Implement error message formatters:
  - [ ] `formatGhNotInstalledError()`
  - [ ] `formatGhNotAuthenticatedError()`
- [ ] Update `createIssue()` to call validation before proceeding
- [ ] Write unit tests for validation logic
- [ ] Run unit tests and fix any failures

### Phase B: E2E Testing (1-2h)

- [ ] Add E2E test for gh not installed scenario
- [ ] Add E2E test for gh not authenticated scenario
- [ ] Add E2E test for repository permission error
- [ ] Create mock gh CLI scripts for testing
- [ ] Run E2E tests and fix any failures
- [ ] Verify no regressions in existing tests

### Phase C: Refinement (1h)

- [ ] Review error message clarity (read them aloud)
- [ ] Test error messages in real terminal (verify formatting)
- [ ] Check exit codes are correct (non-zero on all errors)
- [ ] Verify function injection still works (testability maintained)
- [ ] Final test run (unit + E2E)
- [ ] Update implementation-log.md with phase summary

---

## Risks and Mitigations

### Risk 1: gh CLI exit codes vary by version

**Likelihood:** Low
**Impact:** Medium

**Mitigation:**
- Don't rely solely on exit code 4 for authentication
- Also check for "not authenticated" or "not logged in" in stderr
- Document minimum gh CLI version if needed

### Risk 2: PATH manipulation in E2E tests is fragile

**Likelihood:** Medium
**Impact:** Low

**Mitigation:**
- Use explicit temp directory for mock gh script
- Restore PATH after test (use BATS teardown)
- Test on multiple environments (CI and local)

### Risk 3: Error messages become outdated if gh CLI changes

**Likelihood:** Low
**Impact:** Low

**Mitigation:**
- Keep error detection simple (exit codes are stable)
- Fallback to showing actual gh stderr if detection fails
- Error messages are general enough to stay relevant

### Risk 4: isCommandAvailable uses which (not always present)

**Likelihood:** Low
**Impact:** Low

**Mitigation:**
- `which` is standard on Linux/macOS (our target platforms)
- Alternative: try running `gh --version` and catch error
- Document platform requirements if needed

---

## Open Questions

None - all decisions were resolved during analysis phase.

**Previously resolved:**
- ✅ Error detection strategy (exit codes + stderr)
- ✅ Error message format (multi-line with instructions)
- ✅ Validation timing (before issue creation, not after)
- ✅ Testing approach (mock gh CLI for E2E)

---

## Success Metrics

**This phase is complete when:**

1. ✅ All acceptance criteria met
2. ✅ All unit tests passing (14 existing + ~8 new = 22 tests)
3. ✅ All E2E tests passing (115 existing + ~3 new = 118 tests)
4. ✅ Error messages verified in real terminal
5. ✅ No regressions in Linear/YouTrack functionality
6. ✅ Code review checklist completed
7. ✅ Implementation log updated

**User experience validation:**
- Run `iw feedback "Test"` without gh installed → See helpful error
- Run with gh installed but not authenticated → See auth instructions
- Run with invalid repository → See permission error

---

## Next Steps After This Phase

**Phase 5: Display GitHub issue details**
- Implement `gh issue view` for fetching issue data
- Parse numeric issue IDs (GitHub uses #132, not TEAM-132)
- Display issue information in terminal

**Phase 6: Doctor validates GitHub setup**
- Add GitHub checks to `iw doctor` command
- Reuse validation logic from this phase
- Show checkmarks for each prerequisite

**Integration:**
- Phase 5 will reuse `validateGhPrerequisites()` from this phase
- Phase 6 will call same validation for doctor checks
- Error messages established here become standard for all GitHub commands

---

## References

- **Story Definition:** analysis.md Story 4
- **Technical Decisions:** analysis.md sections on error handling
- **Existing Patterns:** 
  - GitHubClient.scala (Phase 3)
  - CommandRunner.scala (infrastructure)
  - feedback.scala (command integration)
- **Test Patterns:**
  - GitHubClientTest.scala (unit test examples)
  - feedback.bats (E2E test examples)

---

**Phase Status:** Ready for Implementation

**Confidence:** High - Clear requirements, existing patterns to follow, comprehensive testing strategy

**Estimated Effort:** 4-6 hours
- Core logic: 2-3h
- Testing: 1-2h  
- Refinement: 1h
