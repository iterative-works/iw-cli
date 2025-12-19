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
   - [x] [impl] Create test file: `.iw/core/test/FeedbackParserTest.scala`
   - [x] [impl] Write test case `parseFeedbackArgs_TitleOnly` for args: `Seq("Bug", "in", "command")`
   - [x] [impl] Write test case `parseFeedbackArgs_WithDescription` for args: `Seq("Title", "--description", "Long description")`
   - [x] [impl] Write test case `parseFeedbackArgs_WithType` for args: `Seq("Title", "--type", "bug")`
   - [x] [impl] Write test case `parseFeedbackArgs_EmptyTitle` for args: `Seq("--description", "Only desc")`
   - [x] [impl] Write test case `parseFeedbackArgs_InvalidType` for args: `Seq("Title", "--type", "invalid")`
   - [x] [impl] Run test: `./iw test unit`
   - [x] [impl] Verify tests fail with "not found: value parseFeedbackArgs"
   - [ ] [reviewed] Tests cover all argument parsing scenarios

   **GREEN - Make Test Pass:**
   - [x] [impl] Create file: `.iw/core/FeedbackParser.scala`
   - [x] [impl] Define `enum IssueType { case Bug, Feature }` with `fromString` parser method
   - [x] [impl] Add case class `FeedbackRequest(title: String, description: String, issueType: IssueType)`
   - [x] [impl] Implement `def parseFeedbackArgs(args: Seq[String]): Either[String, FeedbackRequest]`
   - [x] [impl] Parse title from args before first flag, join with spaces if multiple words
   - [x] [impl] Parse --description flag value (everything after flag until next flag or end)
   - [x] [impl] Parse --type flag using `IssueType.fromString` (default to `IssueType.Feature`)
   - [x] [impl] Return Left with error if title is empty or type is invalid
   - [x] [impl] Run test: `./iw test unit`
   - [x] [impl] Verify all tests pass
   - [ ] [reviewed] Parser handles all test cases correctly

   **REFACTOR - Improve Quality:**
   - [x] [impl] Extract flag parsing into helper function if repetitive
   - [x] [impl] Ensure error messages are clear and guide user (e.g., "Title is required", "Type must be 'bug' or 'feature'")
   - [x] [impl] Add inline comments for parsing logic
   - [x] [impl] Run all tests: `./iw test unit`
   - [x] [impl] Verify all tests still pass
   - [ ] [reviewed] Code quality meets standards

   **Success Criteria:** parseFeedbackArgs correctly handles title, --description, --type flags and returns clear errors for invalid input
   **Testing:** Unit tests cover valid inputs, missing title, invalid type, and flag combinations

2. **Implement Feedback Command Entry Point** (TDD Cycle)

   **RED - Write Failing Test:**
   - [x] [impl] Command entry point will be tested via E2E tests (unit testing @main is difficult)
   - [ ] [reviewed] Test validates command integration

   **GREEN - Make Test Pass:**
   - [x] [impl] In `.iw/commands/feedback.scala`, implement `@main def feedback(args: String*): Unit`
   - [x] [impl] Get LINEAR_API_TOKEN from environment, return error if not set
   - [x] [impl] Call parseFeedbackArgs(args.toSeq), handle Left by printing error and exiting
   - [x] [impl] Call LinearClient.createIssue with parsed title, description, Constants.IwCliTeamId, and token
   - [x] [impl] Handle Left from createIssue by printing error and exiting with status 1
   - [x] [impl] Handle Right by printing success message with issue URL and ID
   - [x] [impl] Verify command compiles successfully
   - [ ] [reviewed] Command entry point correctly integrates all components

   **REFACTOR - Improve Quality:**
   - [x] [impl] Use Output.error(), Output.success(), and Output.info() for consistent output formatting
   - [x] [impl] Add descriptive error message for missing LINEAR_API_TOKEN
   - [x] [impl] Add command header comment with usage examples
   - [x] [impl] Verify command compiles without warnings
   - [ ] [reviewed] Code quality meets standards

   **Success Criteria:** feedback command integrates argument parsing, environment variables, LinearClient, and output formatting
   **Testing:** Unit test validates command calls LinearClient with correct parameters

3. **Add Command Documentation and Help Text** (TDD Cycle)

   **RED - Write Failing Test:**
   - [x] [impl] Help text will be tested via E2E tests
   - [ ] [reviewed] Test validates help text is shown

   **GREEN - Make Test Pass:**
   - [x] [impl] In `.iw/commands/feedback.scala`, add header comment block with PURPOSE lines
   - [x] [impl] Add usage examples: `./iw feedback "Bug in command"`, `./iw feedback "Feature request" --description "Details..."`
   - [x] [impl] Add --help flag handling in feedback function that prints help and exits 0
   - [x] [impl] Include flag documentation: --description, --type (bug|feature)
   - [x] [impl] showHelp() function implemented with comprehensive documentation
   - [ ] [reviewed] Help text is clear and comprehensive

   **REFACTOR - Improve Quality:**
   - [x] [impl] Ensure help text follows format of other commands in .iw/commands/
   - [x] [impl] Include example of LINEAR_API_TOKEN requirement in help
   - [x] [impl] Review help text for clarity and completeness
   - [ ] [reviewed] Documentation quality meets standards

   **Success Criteria:** Command has comprehensive help text with usage examples and flag documentation
   **Testing:** Unit test validates --help flag shows usage and exits cleanly

4. **Create End-to-End Tests** (TDD Cycle)

   **RED - Write Failing Test:**
   - [x] [impl] Create test file: `.iw/test/feedback.bats`
   - [x] [impl] Write test case `@test "feedback without LINEAR_API_TOKEN fails"` that unsets token and expects error
   - [x] [impl] Write test case `@test "feedback without title fails"` that calls with only --description
   - [x] [impl] Write test case `@test "feedback with invalid type fails"` that passes --type invalid
   - [x] [impl] Write test case `@test "feedback creates issue successfully"` that creates real issue with "[TEST]" prefix
   - [x] [impl] Write test case `@test "feedback with description creates issue"` that includes --description flag
   - [x] [impl] Write test case `@test "feedback with bug type creates issue"` for bug issue type
   - [x] [impl] Write test case `@test "feedback --help shows usage"` for help text
   - [ ] [reviewed] Tests cover all critical scenarios

   **GREEN - Make Test Pass:**
   - [x] [impl] LINEAR_API_TOKEN is available in test environment
   - [x] [impl] All tests pass successfully
   - [x] [impl] Test output includes issue URL/ID on success
   - [x] [impl] Run test: `./iw test e2e`
   - [x] [impl] Verify all feedback tests pass
   - [ ] [reviewed] E2E tests pass and create real issues

   **REFACTOR - Improve Quality:**
   - [x] [impl] Test titles use "[TEST]" prefix for easy identification
   - [x] [impl] Tests skip gracefully if LINEAR_API_TOKEN is not set
   - [x] [impl] Error messages in tests match actual error output
   - [x] [impl] Run all tests: `./iw test`
   - [x] [impl] Verify all tests (unit + e2e) still pass
   - [ ] [reviewed] Test quality and coverage approved

   **Success Criteria:** E2E tests validate error cases and successful issue creation against real Linear API
   **Testing:** BATS tests cover missing token, invalid input, and successful creation with output validation

## Phase Success Criteria

- [x] [impl] parseFeedbackArgs correctly parses title, --description, --type flags
- [ ] [reviewed] Argument parser approved in code review
- [x] [impl] feedback command entry point integrates all components
- [ ] [reviewed] Command implementation approved in code review
- [x] [impl] Command has comprehensive help text and usage examples
- [ ] [reviewed] Documentation approved in code review
- [x] [impl] E2E tests pass for error cases (missing token, invalid input)
- [ ] [reviewed] Error handling tests approved
- [x] [impl] E2E tests pass for success cases (issue creation)
- [ ] [reviewed] Success case tests approved
- [x] [impl] All unit tests pass (`.iw/test unit`)
- [ ] [reviewed] Unit test coverage approved
- [x] [impl] All E2E tests pass (`.iw/test e2e`)
- [ ] [reviewed] E2E test coverage approved
- [x] [impl] Command successfully creates issues in Linear IWLE team
- [ ] [reviewed] End-to-end functionality validated
- [x] [impl] No compilation warnings in new files
- [ ] [reviewed] Overall code quality and documentation approved

---

**Phase Status:** Implementation Complete - Ready for Code Review
**Next Phase:** Final Phase - Ready for Deployment
