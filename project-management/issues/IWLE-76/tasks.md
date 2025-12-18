# Implementation Tasks: Add `iw feedback` command to create issues for iw-cli itself

**Issue:** IWLE-76
**Total Phases:** 2
**Completed Phases:** 0/2
**Complexity:** Simple
**Estimated Total Time:** 6 hours
**Generated:** 2025-12-18

## Overview

Add a new `feedback` command that allows users and agents to create Linear issues for iw-cli itself (team IWLE) directly from any project, without breaking their workflow or navigating to the repository/Linear workspace.

## Implementation Strategy

This implementation extends the existing LinearClient infrastructure with issue creation capabilities, then builds a new command on top of it. Phase 1 focuses on the infrastructure layer (LinearClient.createIssue method with unit tests). Phase 2 implements the command layer (argument parsing, command entry point, E2E tests). This separation allows us to validate the API integration independently before building the user-facing command.

## Phases

### Phase 1: LinearClient Extension for Issue Creation
- [ ] [phase-complete] Phase implementation and review complete
- **Objective:** Extend LinearClient with createIssue method and supporting infrastructure
- **Estimated Time:** 3 hours
- **Tasks:** 4 tasks - See [phase-01.md](./phase-01.md)
- **Prerequisites:** None

### Phase 2: Feedback Command Implementation
- [ ] [phase-complete] Phase implementation and review complete
- **Objective:** Implement user-facing feedback command with argument parsing and E2E tests
- **Estimated Time:** 3 hours
- **Tasks:** 4 tasks - See [phase-02.md](./phase-02.md)
- **Prerequisites:** Completion of Phase 1

---

## Testing Strategy

### Unit Tests

**What to Test:**
- LinearClient.parseCreateIssueResponse with valid JSON responses
- LinearClient.parseCreateIssueResponse error handling for malformed/error responses
- Feedback argument parser with various valid and invalid inputs
- Edge cases: empty strings, missing flags, invalid types

**Testing Approach:**
- Use munit (existing test framework in project)
- Focus on pure parsing functions (parseCreateIssueResponse, parseFeedbackArgs)
- Test error messages are clear and actionable
- Property-based testing not required (simple string parsing)

### Integration Tests

**What to Test:**
- No separate integration tests needed
- API integration validated in E2E tests with real Linear API

**Testing Approach:**
- Unit tests cover JSON parsing in isolation
- E2E tests cover full HTTP request/response cycle

### End-to-End Tests

**Scenarios to Validate:**
- Error when LINEAR_API_TOKEN not set (clear message)
- Error with empty/missing title
- Error with invalid --type value
- Successful issue creation with title only
- Successful issue creation with title and description
- Output format includes issue URL and ID

**Testing Approach:**
- BATS test framework (existing in project at .iw/test/)
- Tests run against real Linear API (requires LINEAR_API_TOKEN)
- Tests create actual issues in IWLE team with "[TEST]" prefix
- Verify both success and error paths
- Validate output format matches expected pattern

## Documentation Requirements

### Code Documentation
- [ ] Inline comments in LinearClient for GraphQL mutation format
- [ ] Function documentation for createIssue method
- [ ] Help text in feedback.scala command header explaining usage
- [ ] Constants.scala documentation for IwCliTeamId value

### API Documentation
Not applicable - command-line interface with help text

### Architecture Documentation
Not required - follows existing patterns (command structure, LinearClient usage, Either-based error handling)

### User Documentation
- [ ] Command help text in feedback.scala header
- [ ] Usage examples in help text (with and without --description)
- [ ] Error message guidance (how to set LINEAR_API_TOKEN)

## Deployment Checklist

### Pre-Deployment
- [ ] All tests passing (unit, integration, e2e)
- [ ] Code reviewed and approved
- [ ] Documentation complete and reviewed
- [ ] Performance validated (N/A - simple command)
- [ ] Security reviewed (N/A - no sensitive data handling beyond API token)

### Database Changes
Not applicable - stateless command, no database

### Configuration Changes
- [ ] IWLE team ID constant added to Constants.scala
- [ ] No project configuration changes (intentionally bypasses config system)
- [ ] No environment variable changes (uses existing LINEAR_API_TOKEN)

### Deployment Steps
- [ ] Merge to main branch
- [ ] Command becomes available immediately in all projects using iw-cli
- [ ] No deployment artifacts or releases required
- [ ] Verify command works in test project: `./iw feedback "Test issue"`

### Post-Deployment
- [ ] Monitor for issues created via feedback command
- [ ] Verify issue URLs/IDs are correct
- [ ] Check for spam or abuse (unlikely, but monitor)
- [ ] Ready to rollback if issues detected

## Rollback Plan

**If deployment issues occur:**

1. Remove `.iw/commands/feedback.scala` file
2. Remove `createIssue` method and related code from LinearClient.scala
3. Remove `IwCliTeamId` constant from Constants.scala
4. Remove test files (unit and E2E)
5. Verify iw-cli still functions normally
6. Investigate issue before re-attempting deployment

**Note:** No state or data to clean up - command is stateless. Issues created during testing will remain in Linear but can be archived manually if needed.

## Notes

**Linear Team ID:** The IWLE team ID is `cf2767bc-3458-44ca-87a8-f2a512ed2b7d` (looked up via Linear API during task generation). This will be hardcoded in Constants.scala.

**API Token Requirement:** Command requires LINEAR_API_TOKEN environment variable. This is the same token used by existing `iw issue` command, so no additional setup required for users.

**Test Issues:** E2E tests will create real issues in Linear with "[TEST]" prefix in title. These should be archived after test runs, but are harmless if left in the system.

**Future Enhancements:** Command intentionally kept simple (title, description, type only). Future iterations could add labels, priority, assignee if needed. The --dry-run flag mentioned in risks is deferred to future work.

**GraphQL Mutation Reference:** Linear API documentation for createIssue mutation: https://studio.apollographql.com/public/Linear-API/variant/current/home

---

**Next Steps:** Begin with Phase 1 - see [phase-01.md](./phase-01.md)
