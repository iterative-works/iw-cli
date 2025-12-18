# Phase 2: Feedback Command Implementation

**Issue:** IWLE-76
**Phase:** 2 of 2
**Objective:** Implement user-facing feedback command with argument parsing and E2E tests
**Estimated Time:** 3 hours
**Prerequisites:** Completion of Phase 1

## Phase Objective

This phase implements the user-facing `feedback` command that allows users and agents to create Linear issues for iw-cli. We'll build the argument parser (title, --description, --type flags), implement the command entry point that integrates with LinearClient, and create comprehensive E2E tests that validate the full workflow against the real Linear API.

## Tasks

1. **Implement Feedback Argument Parser** (TDD Cycle)

   **RED - Write Failing Test:**
   - [ ] [impl] Create test file: `.iw/commands/test/FeedbackArgsParserTest.scala`
   - [ ] [impl] Write test case `parseFeedbackArgs_TitleOnly` for args: `Seq("Bug", "in", "command")`
   - [ ] [impl] Write test case `parseFeedbackArgs_WithDescription` for args: `Seq("Title", "--description", "Long description")`
   - [ ] [impl] Write test case `parseFeedbackArgs_WithType` for args: `Seq("Title", "--type", "bug")`
   - [ ] [impl] Write test case `parseFeedbackArgs_EmptyTitle` for args: `Seq("--description", "Only desc")`
   - [ ] [impl] Write test case `parseFeedbackArgs_InvalidType` for args: `Seq("Title", "--type", "invalid")`
   - [ ] [impl] Run test: `./iw test unit`
   - [ ] [impl] Verify tests fail with "not found: value parseFeedbackArgs"
   - [ ] [reviewed] Tests cover all argument parsing scenarios

   **GREEN - Make Test Pass:**
   - [ ] [impl] Create file: `.iw/commands/feedback.scala`
   - [ ] [impl] Define `enum IssueType { case Bug, Feature }` with `fromString` parser method
   - [ ] [impl] Add case class `FeedbackRequest(title: String, description: String, issueType: IssueType)`
   - [ ] [impl] Implement `def parseFeedbackArgs(args: Seq[String]): Either[String, FeedbackRequest]`
   - [ ] [impl] Parse title from args before first flag, join with spaces if multiple words
   - [ ] [impl] Parse --description flag value (everything after flag until next flag or end)
   - [ ] [impl] Parse --type flag using `IssueType.fromString` (default to `IssueType.Feature`)
   - [ ] [impl] Return Left with error if title is empty or type is invalid
   - [ ] [impl] Run test: `./iw test unit`
   - [ ] [impl] Verify all tests pass
   - [ ] [reviewed] Parser handles all test cases correctly

   **REFACTOR - Improve Quality:**
   - [ ] [impl] Extract flag parsing into helper function if repetitive
   - [ ] [impl] Ensure error messages are clear and guide user (e.g., "Title is required", "Type must be 'bug' or 'feature'")
   - [ ] [impl] Add inline comments for parsing logic
   - [ ] [impl] Run all tests: `./iw test unit`
   - [ ] [impl] Verify all tests still pass
   - [ ] [reviewed] Code quality meets standards

   **Success Criteria:** parseFeedbackArgs correctly handles title, --description, --type flags and returns clear errors for invalid input
   **Testing:** Unit tests cover valid inputs, missing title, invalid type, and flag combinations

2. **Implement Feedback Command Entry Point** (TDD Cycle)

   **RED - Write Failing Test:**
   - [ ] [impl] Add test case to `.iw/commands/test/FeedbackArgsParserTest.scala`: `feedback_ValidArgs_CallsLinearClient`
   - [ ] [impl] Test validates @main function exists and accepts String* args
   - [ ] [impl] Mock/stub LinearClient.createIssue to verify it's called with correct parameters
   - [ ] [impl] Run test: `./iw test unit`
   - [ ] [impl] Verify test fails with expected error
   - [ ] [reviewed] Test validates command integration

   **GREEN - Make Test Pass:**
   - [ ] [impl] In `.iw/commands/feedback.scala`, implement `@main def feedback(args: String*): Unit`
   - [ ] [impl] Get LINEAR_API_TOKEN from environment, return error if not set
   - [ ] [impl] Call parseFeedbackArgs(args.toSeq), handle Left by printing error and exiting
   - [ ] [impl] Call LinearClient.createIssue with parsed title, description, Constants.IwCliTeamId, and token
   - [ ] [impl] Handle Left from createIssue by printing error and exiting with status 1
   - [ ] [impl] Handle Right by printing success message with issue URL and ID
   - [ ] [impl] Run test: `./iw test unit`
   - [ ] [impl] Verify test passes
   - [ ] [reviewed] Command entry point correctly integrates all components

   **REFACTOR - Improve Quality:**
   - [ ] [impl] Extract environment variable access into helper function
   - [ ] [impl] Use Output.error() and Output.success() for consistent output formatting
   - [ ] [impl] Add descriptive error message for missing LINEAR_API_TOKEN
   - [ ] [impl] Add command header comment with usage examples
   - [ ] [impl] Run all tests: `./iw test unit`
   - [ ] [impl] Verify all tests still pass
   - [ ] [reviewed] Code quality meets standards

   **Success Criteria:** feedback command integrates argument parsing, environment variables, LinearClient, and output formatting
   **Testing:** Unit test validates command calls LinearClient with correct parameters

3. **Add Command Documentation and Help Text** (TDD Cycle)

   **RED - Write Failing Test:**
   - [ ] [impl] Add test case to `.iw/commands/test/FeedbackArgsParserTest.scala`: `feedback_HelpFlag_PrintsUsage`
   - [ ] [impl] Test validates --help flag prints usage and exits successfully
   - [ ] [impl] Run test: `./iw test unit`
   - [ ] [impl] Verify test fails (no --help handling yet)
   - [ ] [reviewed] Test validates help text is shown

   **GREEN - Make Test Pass:**
   - [ ] [impl] In `.iw/commands/feedback.scala`, add header comment block with PURPOSE lines
   - [ ] [impl] Add usage examples: `./iw feedback "Bug in command"`, `./iw feedback "Feature request" --description "Details..."`
   - [ ] [impl] Add --help flag handling in feedback function that prints help and exits 0
   - [ ] [impl] Include flag documentation: --description, --type (bug|feature)
   - [ ] [impl] Run test: `./iw test unit`
   - [ ] [impl] Verify test passes
   - [ ] [reviewed] Help text is clear and comprehensive

   **REFACTOR - Improve Quality:**
   - [ ] [impl] Ensure help text follows format of other commands in .iw/commands/
   - [ ] [impl] Include example of LINEAR_API_TOKEN requirement in help
   - [ ] [impl] Review help text for clarity and completeness
   - [ ] [impl] Run all tests: `./iw test unit`
   - [ ] [impl] Verify all tests still pass
   - [ ] [reviewed] Documentation quality meets standards

   **Success Criteria:** Command has comprehensive help text with usage examples and flag documentation
   **Testing:** Unit test validates --help flag shows usage and exits cleanly

4. **Create End-to-End Tests** (TDD Cycle)

   **RED - Write Failing Test:**
   - [ ] [impl] Create test file: `.iw/test/feedback.bats`
   - [ ] [impl] Write test case `@test "feedback without LINEAR_API_TOKEN fails"` that unsets token and expects error
   - [ ] [impl] Write test case `@test "feedback without title fails"` that calls with only --description
   - [ ] [impl] Write test case `@test "feedback with invalid type fails"` that passes --type invalid
   - [ ] [impl] Write test case `@test "feedback creates issue successfully"` that creates real issue with "[TEST]" prefix
   - [ ] [impl] Write test case `@test "feedback with description creates issue"` that includes --description flag
   - [ ] [impl] Run test: `./iw test e2e`
   - [ ] [impl] Verify tests fail (command not yet functional)
   - [ ] [reviewed] Tests cover all critical scenarios

   **GREEN - Make Test Pass:**
   - [ ] [impl] Ensure LINEAR_API_TOKEN is available in test environment
   - [ ] [impl] Fix any issues preventing tests from passing
   - [ ] [impl] Verify test output includes issue URL/ID on success
   - [ ] [impl] Run test: `./iw test e2e`
   - [ ] [impl] Verify all tests pass
   - [ ] [reviewed] E2E tests pass and create real issues

   **REFACTOR - Improve Quality:**
   - [ ] [impl] Add cleanup comments noting that test issues can be archived in Linear
   - [ ] [impl] Ensure test titles use "[TEST]" prefix for easy identification
   - [ ] [impl] Verify error messages in tests match actual error output
   - [ ] [impl] Run all tests: `./iw test`
   - [ ] [impl] Verify all tests (unit + e2e) still pass
   - [ ] [reviewed] Test quality and coverage approved

   **Success Criteria:** E2E tests validate error cases and successful issue creation against real Linear API
   **Testing:** BATS tests cover missing token, invalid input, and successful creation with output validation

## Phase Success Criteria

- [ ] [impl] parseFeedbackArgs correctly parses title, --description, --type flags
- [ ] [reviewed] Argument parser approved in code review
- [ ] [impl] feedback command entry point integrates all components
- [ ] [reviewed] Command implementation approved in code review
- [ ] [impl] Command has comprehensive help text and usage examples
- [ ] [reviewed] Documentation approved in code review
- [ ] [impl] E2E tests pass for error cases (missing token, invalid input)
- [ ] [reviewed] Error handling tests approved
- [ ] [impl] E2E tests pass for success cases (issue creation)
- [ ] [reviewed] Success case tests approved
- [ ] [impl] All unit tests pass (`.iw/test unit`)
- [ ] [reviewed] Unit test coverage approved
- [ ] [impl] All E2E tests pass (`.iw/test e2e`)
- [ ] [reviewed] E2E test coverage approved
- [ ] [impl] Command successfully creates issues in Linear IWLE team
- [ ] [reviewed] End-to-end functionality validated
- [ ] [impl] No compilation warnings in new files
- [ ] [reviewed] Overall code quality and documentation approved

---

**Phase Status:** Not Started
**Next Phase:** Final Phase - Ready for Deployment
