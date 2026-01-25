# Implementation Log: Add issue creation command

Issue: IW-103

This log tracks the evolution of implementation across phases.

---

## Phase 1: Help display (2026-01-25)

**What was built:**
- Command: `.iw/commands/issue-create.scala` - Standalone issue create command with help display
- Routing: `.iw/commands/issue.scala` - Added subcommand routing for `create`
- Tests: `.iw/test/issue-create.bats` - E2E tests for help display

**Decisions made:**
- Use subcommand pattern (`iw issue create`) matching existing `server.scala` pattern
- Help text follows `feedback.scala` style for consistency
- Both `--help` and `-h` supported for convenience

**Patterns applied:**
- Subcommand routing: First arg checked for subcommand name, remaining args passed to handler
- Help-first design: Show help when no args provided (exit 1) or with `--help` flag (exit 0)

**Testing:**
- E2E tests: 6 tests added
- Unit tests: 0 (deferred - E2E coverage sufficient for Phase 1)

**Code review:**
- Iterations: 1
- Review file: review-phase-01-20260125-160700.md
- Major findings: No critical issues. Style warnings about visibility modifiers (acceptable).

**For next phases:**
- Available utilities: `handleCreateSubcommand()` ready for Phase 2 implementation
- Extension points: Placeholder in `handleCreateSubcommand()` for actual creation logic
- Notes: Argument parsing will be added in Phase 2 when implementing actual creation

**Files changed:**
```
A  .iw/commands/issue-create.scala
M  .iw/commands/issue.scala
A  .iw/test/issue-create.bats
```

---

## Phase 2: GitHub issue creation (2026-01-25)

**What was built:**
- Parser: `.iw/core/IssueCreateParser.scala` - Argument parser for --title and --description flags
- Model: `IssueCreateRequest` case class with title and optional description
- Implementation: Full `handleCreateSubcommand()` in `.iw/commands/issue.scala`
- Tests: 8 unit tests for parser + 4 new E2E tests (10 total)

**Decisions made:**
- Reuse GitHubClient infrastructure (validateGhPrerequisites, buildCreateIssueCommandWithoutLabel, parseCreateIssueResponse)
- Use separate IssueCreateParser in core module for testability
- Support multi-word flag values by consuming args until next flag
- Show help and exit 1 when --title is missing (consistent with help-first design)

**Patterns applied:**
- Either-based error handling: Parser returns Either[String, IssueCreateRequest]
- Flag extraction: takeWhile(!_.startsWith("--")) for multi-word values
- Infrastructure reuse: Leverage existing GitHubClient methods from feedback command

**Testing:**
- Unit tests: 8 tests for IssueCreateParser
- E2E tests: 4 new tests (10 total including Phase 1)
- Coverage: Title-only, title+description, missing title, non-GitHub tracker

**Code review:**
- Iterations: 1
- Review file: review-phase-02-20260125-163500.md
- Major findings: No critical issues. Minor style/testing suggestions (optional improvements).

**For next phases:**
- Available utilities: IssueCreateParser for argument parsing
- Extension points: Can add more flags (--assignee, --label) to parser
- Notes: Non-GitHub trackers show "not yet supported" message

**Files changed:**
```
M  .iw/commands/issue.scala
A  .iw/core/IssueCreateParser.scala
A  .iw/core/test/IssueCreateParserTest.scala
M  .iw/test/issue-create.bats
```

---

## Phase 3: Prerequisite validation (2026-01-25)

**What was built:**
- E2E tests: 2 new tests in `.iw/test/issue-create.bats` for prerequisite validation

**Decisions made:**
- Follow same mock pattern as feedback.bats (proven to work)
- Test both "gh not installed" and "gh not authenticated" scenarios
- Verify error messages contain actionable instructions (URLs, commands)

**Patterns applied:**
- Shell mocking via PATH manipulation (mock scripts in $TEST_DIR/bin)
- Consistent error message verification across commands

**Testing:**
- E2E tests: 2 new tests (12 total in issue-create.bats)
- Verification: All feedback.bats tests still pass (no regressions)

**Code review:**
- Iterations: 1
- Major findings: No critical issues.

**For next phases:**
- Prerequisite validation pattern established for other trackers
- Same mocking approach can be used for Linear/GitLab/YouTrack tests

**Files changed:**
```
M  .iw/test/issue-create.bats
```

---

## Phase 4: Title-only creation (2026-01-25)

**Status:** Already implemented in Phase 2

**What was verified:**
- IssueCreateParser designed with optional `--description` from the start
- E2E test "issue create with title only succeeds" already exists and passes
- No additional implementation required

**Notes:**
- Phase 2's design anticipated this requirement
- `description: Option[String]` in `IssueCreateRequest` makes `--description` optional
- Test coverage already included in Phase 2

**Files changed:**
```
(No changes - functionality already complete)
```

---

## Phase 5: Linear issue creation (2026-01-25)

**What was built:**
- Feature: `.iw/commands/issue.scala` - Added Linear tracker support to handleCreateSubcommand
- Helper: `createLinearIssue()` function for Linear-specific issue creation flow
- Refactor: Replaced hardcoded GitHub check with tracker type pattern match
- Tests: `.iw/test/issue-create.bats` - Added Linear E2E test, updated YouTrack test

**Decisions made:**
- Reuse existing `LinearClient.createIssue()` infrastructure from feedback command
- Follow same pattern as `createGitHubIssue()` for consistency
- Use `ApiToken.fromEnv(Constants.EnvVars.LinearApiToken)` for token validation
- Get teamId from `config.team` field
- Defer success tests requiring HTTP mocking or real API

**Patterns applied:**
- Tracker type branching: Added case for `IssueTrackerType.Linear` alongside GitHub
- Token validation pattern: Check env var before API call with clear error message
- Helper function extraction: Separate functions for each tracker type

**Testing:**
- E2E tests: 1 new test (13 total in issue-create.bats)
- Updated test: Changed "non-GitHub" test to use YouTrack (Linear now supported)
- Coverage: Token validation tested; success path deferred (needs mocking)

**Code review:**
- Iterations: 1
- Major findings: No critical issues. Suggestions for Scala 3 features (opaque types) - optional improvements.

**For next phases:**
- Available utilities: `createLinearIssue()` pattern can be followed for GitLab/YouTrack
- Extension points: Tracker type match in handleCreateSubcommand ready for new cases
- Notes: Linear success tests require HTTP mocking infrastructure

**Files changed:**
```
M  .iw/commands/issue.scala
M  .iw/test/issue-create.bats
```

---

## Phase 6: GitLab issue creation (2026-01-25)

**What was built:**
- Feature: `.iw/commands/issue.scala` - Added GitLab tracker support to handleCreateSubcommand
- Helper: `createGitLabIssue()` function for GitLab-specific issue creation flow
- Tests: `.iw/test/issue-create.bats` - 3 new GitLab E2E tests

**Decisions made:**
- Reuse existing `GitLabClient.validateGlabPrerequisites()` for CLI validation
- Use `GitLabClient.buildCreateIssueCommandWithoutLabel()` to build glab command
- Pass command as array to `CommandRunner.execute("glab", args)` (not split like GitHub)
- Follow same error handling pattern as GitHub for consistency

**Patterns applied:**
- CLI prerequisite validation: Check glab installed and authenticated before API call
- Tracker type branching: Added `case IssueTrackerType.GitLab` alongside GitHub/Linear
- Helper function extraction: `createGitLabIssue()` follows `createGitHubIssue()` pattern

**Testing:**
- E2E tests: 3 new tests (16 total in issue-create.bats)
- Coverage: glab not installed, glab not authenticated, successful creation
- Mocking: PATH manipulation for glab CLI (same pattern as gh)

**Code review:**
- Iterations: 1
- Major findings: No critical issues. Code follows Scala 3 idioms correctly.

**For next phases:**
- Available utilities: `createGitLabIssue()` pattern for YouTrack
- Extension points: Tracker type match ready for YouTrack
- Notes: YouTrack requires implementing YouTrackClient.createIssue (new method)

**Files changed:**
```
M  .iw/commands/issue.scala
M  .iw/test/issue-create.bats
A  project-management/issues/IW-103/phase-06-context.md
A  project-management/issues/IW-103/phase-06-tasks.md
```

---
