# Phase 1: LinearClient Extension for Issue Creation

**Issue:** IWLE-76
**Phase:** 1 of 2
**Objective:** Extend LinearClient with createIssue method and supporting infrastructure
**Estimated Time:** 3 hours
**Prerequisites:** None

## Phase Objective

This phase extends the existing LinearClient infrastructure to support creating Linear issues via GraphQL mutation. We'll add the createIssue method, GraphQL mutation builder, response parser, and necessary data types (CreatedIssue case class). We'll also add the IWLE team ID constant to Constants.scala. All parsing logic will be unit tested before integration with the command layer in Phase 2.

## Tasks

1. **Add IWLE Team ID Constant** (TDD Cycle)

   **RED - Write Failing Test:**
   - [ ] [impl] Create test file: `.iw/core/test/ConstantsTest.scala`
   - [ ] [impl] Write test case for `Constants.IwCliTeamId` that validates the constant exists and is a valid UUID format
   - [ ] [impl] Run test: `./iw test unit`
   - [ ] [impl] Verify test fails with "value IwCliTeamId is not a member of object Constants"
   - [ ] [reviewed] Test properly validates the expected constant exists

   **GREEN - Make Test Pass:**
   - [ ] [impl] Open `.iw/core/Constants.scala` and add `val IwCliTeamId: String = "cf2767bc-3458-44ca-87a8-f2a512ed2b7d"`
   - [ ] [impl] Add comment: `// Linear team ID for IWLE (iw-cli project)`
   - [ ] [impl] Run test: `./iw test unit`
   - [ ] [impl] Verify test passes
   - [ ] [reviewed] Implementation is correct and minimal

   **REFACTOR - Improve Quality:**
   - [ ] [impl] Review constant placement in file (alphabetical or logical grouping)
   - [ ] [impl] Ensure comment clearly explains purpose
   - [ ] [impl] Run all tests: `./iw test unit`
   - [ ] [impl] Verify all tests still pass
   - [ ] [reviewed] Code quality meets standards

   **Success Criteria:** Constants.IwCliTeamId exists with correct UUID value and is accessible from test code
   **Testing:** Unit test validates constant exists and is valid UUID format

2. **Add CreatedIssue Case Class and Response Parser** (TDD Cycle)

   **RED - Write Failing Test:**
   - [ ] [impl] Create test file: `.iw/core/test/LinearClientCreateIssueTest.scala`
   - [ ] [impl] Write test case `parseCreateIssueResponse_ValidResponse` with sample JSON: `{"data":{"issueCreate":{"success":true,"issue":{"id":"abc123","url":"https://linear.app/issue/abc123"}}}}`
   - [ ] [impl] Write test case `parseCreateIssueResponse_ApiError` with error JSON: `{"errors":[{"message":"Invalid team"}]}`
   - [ ] [impl] Write test case `parseCreateIssueResponse_MissingFields` with malformed JSON: `{"data":{"issueCreate":{"success":true,"issue":{"id":"abc123"}}}}`
   - [ ] [impl] Run test: `./iw test unit`
   - [ ] [impl] Verify tests fail with "value parseCreateIssueResponse is not a member of object LinearClient"
   - [ ] [reviewed] Tests properly validate all expected scenarios

   **GREEN - Make Test Pass:**
   - [ ] [impl] Open `.iw/core/LinearClient.scala`
   - [ ] [impl] Add case class `CreatedIssue(id: String, url: String)` near top of file
   - [ ] [impl] Implement method signature: `def parseCreateIssueResponse(json: String): Either[String, CreatedIssue]`
   - [ ] [impl] Parse JSON using ujson, extract data.issueCreate.issue.id and .url
   - [ ] [impl] Handle errors array if present, return Left with error message
   - [ ] [impl] Handle missing fields, return Left with descriptive error
   - [ ] [impl] Run test: `./iw test unit`
   - [ ] [impl] Verify all tests pass
   - [ ] [reviewed] Implementation handles all test cases correctly

   **REFACTOR - Improve Quality:**
   - [ ] [impl] Extract JSON path navigation into helper if repetitive
   - [ ] [impl] Ensure error messages are clear and actionable
   - [ ] [impl] Add inline comments for GraphQL response structure
   - [ ] [impl] Run all tests: `./iw test unit`
   - [ ] [impl] Verify all tests still pass
   - [ ] [reviewed] Code quality meets standards

   **Success Criteria:** parseCreateIssueResponse correctly parses valid responses and returns clear errors for invalid/error responses
   **Testing:** Unit tests cover valid JSON, API errors, and malformed responses

3. **Implement GraphQL Mutation Builder** (TDD Cycle)

   **RED - Write Failing Test:**
   - [ ] [impl] Add test case to `.iw/core/test/LinearClientCreateIssueTest.scala`: `buildCreateIssueMutation_ValidInputs`
   - [ ] [impl] Test validates mutation includes correct fields: title, description, teamId in GraphQL format
   - [ ] [impl] Test validates GraphQL syntax (query keyword, mutation name, input structure)
   - [ ] [impl] Run test: `./iw test unit`
   - [ ] [impl] Verify test fails with "value buildCreateIssueMutation is not a member of object LinearClient"
   - [ ] [reviewed] Test validates mutation structure correctly

   **GREEN - Make Test Pass:**
   - [ ] [impl] Implement `def buildCreateIssueMutation(title: String, description: String, teamId: String): String` in LinearClient.scala
   - [ ] [impl] Return GraphQL mutation string: `mutation { issueCreate(input: {title: "...", description: "...", teamId: "..."}) { success issue { id url } } }`
   - [ ] [impl] Properly escape quotes in title and description
   - [ ] [impl] Run test: `./iw test unit`
   - [ ] [impl] Verify test passes
   - [ ] [reviewed] Mutation format is correct and strings are properly escaped

   **REFACTOR - Improve Quality:**
   - [ ] [impl] Add inline comment with Linear API mutation reference
   - [ ] [impl] Review string escaping logic for robustness
   - [ ] [impl] Consider using triple-quoted strings for mutation template
   - [ ] [impl] Run all tests: `./iw test unit`
   - [ ] [impl] Verify all tests still pass
   - [ ] [reviewed] Code quality meets standards

   **Success Criteria:** buildCreateIssueMutation produces valid GraphQL mutation string with properly escaped inputs
   **Testing:** Unit test validates mutation structure and string escaping

4. **Implement createIssue Method** (TDD Cycle)

   **RED - Write Failing Test:**
   - [ ] [impl] Add test case to `.iw/core/test/LinearClientCreateIssueTest.scala`: `createIssue_Integration`
   - [ ] [impl] Test validates method signature and return type (Either[String, CreatedIssue])
   - [ ] [impl] Mock or stub HTTP client to return sample response
   - [ ] [impl] Verify method calls buildCreateIssueMutation and parseCreateIssueResponse
   - [ ] [impl] Run test: `./iw test unit`
   - [ ] [impl] Verify test fails with "value createIssue is not a member of object LinearClient"
   - [ ] [reviewed] Test validates method integration correctly

   **GREEN - Make Test Pass:**
   - [ ] [impl] Implement method signature: `def createIssue(title: String, description: String, teamId: String, token: ApiToken): Either[String, CreatedIssue]`
   - [ ] [impl] Call buildCreateIssueMutation to construct GraphQL query
   - [ ] [impl] Use sttp client to POST to https://api.linear.app/graphql with Authorization header
   - [ ] [impl] Pass response body to parseCreateIssueResponse
   - [ ] [impl] Return Either[String, CreatedIssue] from parseCreateIssueResponse
   - [ ] [impl] Run test: `./iw test unit`
   - [ ] [impl] Verify test passes
   - [ ] [reviewed] Implementation correctly integrates all components

   **REFACTOR - Improve Quality:**
   - [ ] [impl] Review error handling for HTTP failures (network errors, non-200 status)
   - [ ] [impl] Ensure token is passed correctly in Authorization header
   - [ ] [impl] Add method documentation comment explaining parameters and return value
   - [ ] [impl] Run all tests: `./iw test unit`
   - [ ] [impl] Verify all tests still pass
   - [ ] [reviewed] Code quality meets standards

   **Success Criteria:** createIssue method successfully integrates mutation builder, HTTP client, and response parser
   **Testing:** Unit test validates method integration (mocked HTTP) and error handling

## Phase Success Criteria

- [ ] [impl] Constants.IwCliTeamId constant exists with correct IWLE team UUID
- [ ] [reviewed] Constant approved in code review
- [ ] [impl] CreatedIssue case class defined with id and url fields
- [ ] [reviewed] Case class approved in code review
- [ ] [impl] parseCreateIssueResponse handles valid responses, API errors, and malformed JSON
- [ ] [reviewed] Parser logic approved in code review
- [ ] [impl] buildCreateIssueMutation produces valid GraphQL mutation with escaped strings
- [ ] [reviewed] Mutation builder approved in code review
- [ ] [impl] createIssue method integrates all components and handles HTTP errors
- [ ] [reviewed] createIssue implementation approved in code review
- [ ] [impl] All unit tests pass (`.iw/test unit`)
- [ ] [reviewed] Test coverage and quality approved
- [ ] [impl] No compilation warnings in modified files
- [ ] [reviewed] Code quality and documentation approved

---

**Phase Status:** Not Started
**Next Phase:** [phase-02.md](./phase-02.md) - Feedback Command Implementation
