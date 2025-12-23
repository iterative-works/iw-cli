# Phase 5 Tasks: Display GitHub issue details

**Issue:** IWLE-132
**Phase:** 5 of 6
**Story:** Story 3 - Display GitHub issue details
**Estimated Effort:** 6-8 hours

---

## Overview

This phase extends the `iw issue` command to support GitHub tracker, enabling display of GitHub issue details via the `gh` CLI. The implementation follows TDD and is broken into bite-sized tasks (15-30 minutes each).

**Key deliverables:**
1. Numeric IssueId parsing (GitHub uses "132", not "TEAM-132")
2. GitHubClient.fetchIssue method (fetch + parse GitHub JSON)
3. GitHub case in issue.scala pattern match
4. Comprehensive test coverage (unit + E2E)

---

## Setup

- [ ] [setup] Review phase-05-context.md and analysis.md to understand requirements
- [ ] [setup] Confirm all Phase 4 prerequisite validation code is complete and tested
- [ ] [setup] Run existing tests to ensure clean baseline: `./iw test`

---

## Phase A: IssueId Enhancement for Numeric GitHub IDs

### Tests First

- [x] [test] Add test for parsing numeric IssueId "132" (GitHubClientTest or new IssueIdTest)
- [x] [test] Add test for parsing numeric IssueId "1" (single digit)
- [x] [test] Add test for parsing numeric IssueId "999" (multi-digit)
- [x] [test] Add test for parsing with whitespace "  132  " (should trim and succeed)
- [x] [test] Add regression test: "IWLE-132" still parses correctly (uppercase)
- [x] [test] Add regression test: "iwle-132" still parses correctly (normalizes to uppercase)
- [x] [test] Add test for invalid format "ABC" (should fail)
- [x] [test] Add test for fromBranch with numeric prefix "132-add-feature" (extract "132")
- [x] [test] Add test for fromBranch with numeric prefix "123_bugfix" (extract "123")
- [x] [test] Add regression test: fromBranch "IWLE-132-description" still works
- [x] [test] Add test for team extension on numeric ID "132" (should return "")
- [x] [test] Add regression test: team extension on "IWLE-132" (should return "IWLE")
- [x] [test] Run tests, confirm all new tests fail: `./iw test unit`

### Implementation

- [x] [impl] Add NumericPattern regex to IssueId.scala: `"""^[0-9]+$""".r`
- [x] [impl] Add NumericBranchPattern regex: `"""^([0-9]+)[-_].*""".r`
- [x] [impl] Update parse() to accept numeric strings (fallback after TEAM-NNN pattern)
- [x] [impl] Ensure numeric IDs are NOT uppercased (preserve "132" not "132")
- [x] [impl] Update fromBranch() to extract numeric prefixes (fallback after TEAM-NNN)
- [x] [impl] Update team extension to return "" for numeric IDs (check contains("-"))
- [x] [impl] Run tests, confirm all IssueId tests pass: `./iw test unit`
- [x] [impl] Manually test: `echo 'IssueId.parse("132")' | scala-cli -cp .iw/core`

---

## Phase B: GitHubClient.fetchIssue - Command Building

### Tests First

- [x] [test] Add test for buildFetchIssueCommand basic args (verify "gh", "issue", "view", "132")
- [x] [test] Add test for buildFetchIssueCommand includes --repo flag with correct repository
- [x] [test] Add test for buildFetchIssueCommand includes --json flag with correct fields
- [x] [test] Add test for buildFetchIssueCommand with different issue numbers ("1", "999")
- [x] [test] Run tests, confirm new tests fail: `./iw test unit`

### Implementation

- [x] [impl] Add buildFetchIssueCommand method to GitHubClient.scala
- [x] [impl] Method signature: `(issueNumber: String, repository: String): Array[String]`
- [x] [impl] Build array: `["issue", "view", issueNumber, "--repo", repository, "--json", "number,title,state,assignees,body"]`
- [x] [impl] Run tests, confirm buildFetchIssueCommand tests pass: `./iw test unit`

---

## Phase C: GitHubClient.fetchIssue - JSON Parsing

### Tests First

- [x] [test] Add test for parseFetchIssueResponse with complete valid JSON
- [x] [test] Add test for parseFetchIssueResponse with empty assignees array
- [x] [test] Add test for parseFetchIssueResponse with null body field
- [x] [test] Add test for parseFetchIssueResponse with multiple assignees (should use first)
- [x] [test] Add test for parseFetchIssueResponse with malformed JSON (should return Left)
- [x] [test] Add test for parseFetchIssueResponse with missing "title" field (should return Left)
- [x] [test] Add test for parseFetchIssueResponse with missing "state" field (should return Left)
- [x] [test] Add test for state mapping: "OPEN" → "open" (lowercase)
- [x] [test] Add test for state mapping: "CLOSED" → "closed" (lowercase)
- [x] [test] Add test for issue ID formatting: issueNumber "132" → id "#132"
- [x] [test] Run tests, confirm new tests fail: `./iw test unit`

### Implementation

- [x] [impl] Add parseFetchIssueResponse method to GitHubClient.scala
- [x] [impl] Method signature: `(jsonOutput: String, issueNumber: String): Either[String, Issue]`
- [x] [impl] Parse JSON using ujson (import ujson.*, read(jsonOutput))
- [x] [impl] Extract fields: number, title, state (lowercase), assignees, body
- [x] [impl] Handle empty assignees: `if json("assignees").arr.isEmpty then None`
- [x] [impl] Handle multiple assignees: use first `json("assignees").arr.head("login").str`
- [x] [impl] Handle null body: `if json("body").isNull then None`
- [x] [impl] Format issue ID as "#132" (not just "132")
- [x] [impl] Return Issue(id, title, status, assignee, description)
- [x] [impl] Wrap in try-catch, return Left on exception
- [x] [impl] Run tests, confirm parseFetchIssueResponse tests pass: `./iw test unit`

---

## Phase D: GitHubClient.fetchIssue - Integration

### Tests First

- [x] [test] Add test for fetchIssue validates prerequisites first (gh not installed)
- [x] [test] Add test for fetchIssue validates prerequisites (gh not authenticated)
- [x] [test] Add test for fetchIssue returns formatted error when gh not installed
- [x] [test] Add test for fetchIssue returns formatted error when gh not authenticated
- [x] [test] Add test for fetchIssue executes command with correct args (mock execCommand)
- [x] [test] Add test for fetchIssue parses successful response into Issue
- [x] [test] Add test for fetchIssue returns Left when command execution fails
- [x] [test] Add test for fetchIssue returns Left when JSON parsing fails
- [x] [test] Run tests, confirm new tests fail: `./iw test unit`

### Implementation

- [x] [impl] Add fetchIssue method to GitHubClient.scala
- [x] [impl] Method signature: `(issueNumber: String, repository: String, execCommand: ...): Either[String, Issue]`
- [x] [impl] Add function injection for execCommand (same pattern as createIssue)
- [x] [impl] Call validateGhPrerequisites first (reuse from Phase 4)
- [x] [impl] Handle GhNotInstalled → return Left(formatGhNotInstalledError())
- [x] [impl] Handle GhNotAuthenticated → return Left(formatGhNotAuthenticatedError())
- [x] [impl] Handle GhOtherError → return Left with error message
- [x] [impl] Build command args using buildFetchIssueCommand
- [x] [impl] Execute "gh" with args via execCommand
- [x] [impl] Handle Left from execCommand → return Left with error
- [x] [impl] Handle Right from execCommand → parse JSON via parseFetchIssueResponse
- [x] [impl] Return parsed Issue or error
- [x] [impl] Run tests, confirm fetchIssue tests pass: `./iw test unit`
- [x] [impl] Run full unit test suite: `./iw test unit`

---

## Phase E: Command Integration in issue.scala

### Tests First

- [ ] [test] Create .iw/test/issue.bats if it doesn't exist (check first)
- [ ] [test] Add E2E test: "iw issue 132" with mocked gh CLI (GitHub tracker)
- [ ] [test] Add E2E test: "iw issue" inferred from branch "132-feature" (GitHub tracker)
- [ ] [test] Add E2E test: Error when issue not found (gh returns error)
- [ ] [test] Add E2E test: Error when gh not installed (PATH without gh)
- [ ] [test] Add E2E test: Error when repository not configured
- [ ] [test] Add E2E regression test: Linear tracker "iw issue IWLE-132" still works
- [ ] [test] Run E2E tests, confirm new tests fail: `./iw test e2e`

### Implementation

- [x] [impl] Open .iw/commands/issue.scala
- [x] [impl] Add IssueTrackerType.GitHub case to fetchIssue pattern match (after YouTrack case)
- [x] [impl] Check config.repository exists, return Left if missing
- [x] [impl] Extract numeric issue number from IssueId (handle both "132" and potential "TEAM-132")
- [x] [impl] Implement extraction: `if issueId.value.contains("-") then split("-")(1) else issueId.value`
- [x] [impl] Call GitHubClient.fetchIssue(issueNumber, repository)
- [x] [impl] Return result (error or Issue)
- [ ] [impl] Run E2E tests: `./iw test e2e`
- [ ] [impl] Fix any failing tests
- [ ] [impl] Run full test suite: `./iw test`

---

## Phase F: E2E Testing Refinement

- [ ] [test] Review all E2E test mocks for correctness (gh commands should match real gh CLI)
- [ ] [test] Add test for branch inference with underscore separator "123_bugfix"
- [ ] [test] Add test for issue with no assignees (verify display shows "None")
- [ ] [test] Add test for issue with no description (verify display handles null body)
- [ ] [test] Verify error messages are user-friendly (not technical jargon)
- [ ] [test] Run full test suite and confirm all pass: `./iw test`
- [ ] [test] Manually test with real GitHub repository (optional if gh available)

---

## Phase G: Manual Testing & Verification

- [ ] [impl] Create test config with GitHub tracker: `cd /tmp && mkdir test-gh && cd test-gh && git init`
- [ ] [impl] Add .iw/config.conf with github tracker and test repository
- [ ] [impl] Test: `iw issue 1` (if issue #1 exists in test repo)
- [ ] [impl] Create branch "132-test" and test: `iw issue` (should infer 132)
- [ ] [impl] Test error case: `iw issue 999999` (non-existent issue)
- [ ] [impl] Test with Linear config (regression): confirm Linear issues still work
- [ ] [impl] Verify issue display format matches Linear/YouTrack (consistent UX)
- [ ] [impl] Clean up test directory

---

## Phase H: Documentation & Cleanup

- [ ] [impl] Add inline comments to IssueId.scala explaining numeric pattern logic
- [ ] [impl] Add inline comments to GitHubClient.scala explaining JSON field mappings
- [ ] [impl] Review all test descriptions - ensure they clearly state what's being tested
- [ ] [impl] Run final test suite: `./iw test`
- [ ] [impl] Update implementation-log.md with Phase 5 summary (what was done, any deviations)
- [ ] [impl] Mark phase-05-context.md acceptance criteria as complete
- [ ] [impl] Commit changes: `git add -A && git commit -m "feat(IWLE-132): Phase 5 - Display GitHub issue details"`

---

## Success Criteria

**Phase is complete when:**
- [ ] All unit tests passing (existing + ~20 new IssueId tests + ~15 new GitHubClient tests)
- [ ] All E2E tests passing (existing + ~6 new issue command tests)
- [ ] `iw issue 132` displays GitHub issue #132 (manual test or E2E)
- [ ] `iw issue` infers from branch "132-feature" (manual test or E2E)
- [ ] Error messages are clear and actionable
- [ ] No regressions in Linear/YouTrack functionality
- [ ] Code follows existing patterns and style
- [ ] All acceptance criteria in phase-05-context.md met

---

## Notes

- **TDD approach**: Write failing tests first, then implement to make them pass
- **Bite-sized tasks**: Each task should take 15-30 minutes
- **Test organization**: Group tests by what they're testing (parsing, command building, integration)
- **Function injection**: All GitHubClient methods use function injection for testability
- **Reuse Phase 4**: Prerequisite validation is already implemented, just reuse it
- **Regression focus**: Every implementation change should have a corresponding regression test

---

**Estimated breakdown:**
- Phase A (IssueId): 1-2h (13 tests + 6 impl tasks)
- Phase B (Command building): 30min (5 tests + 4 impl tasks)
- Phase C (JSON parsing): 1-1.5h (11 tests + 10 impl tasks)
- Phase D (Integration): 1-1.5h (9 tests + 13 impl tasks)
- Phase E (issue.scala): 1-1.5h (7 tests + 9 impl tasks)
- Phase F (E2E refinement): 1h (6 test tasks)
- Phase G (Manual testing): 30min (7 impl tasks)
- Phase H (Documentation): 30min (8 impl tasks)

**Total: 6-8 hours** (matches estimate)
