# Technical Analysis: Add `iw feedback` command to create issues for iw-cli itself

**Issue:** IWLE-76
**Created:** 2025-12-18
**Status:** Draft
**Classification:** Simple

## Problem Statement

Users and agents working with iw-cli currently have no convenient way to report bugs or request features for the tool itself without leaving their current project context. They would need to manually navigate to the iw-cli repository or Linear workspace, breaking their flow.

We need a dedicated command that allows anyone to quickly file feedback (bugs or feature requests) directly to the iw-cli team (IWLE) from within any project that uses iw-cli.

## Proposed Solution

### High-Level Approach

Add a new `feedback.scala` command file that creates Linear issues in the hardcoded IWLE team. The command will accept a title and description via command-line arguments (not interactive prompts) to enable both human and agent usage. The command will validate inputs, construct a GraphQL mutation, call the Linear API, and output the created issue URL/ID on success.

This will require extending LinearClient with a `createIssue` method that performs the GraphQL mutation to create issues in Linear. The command will bypass the project configuration system entirely, using hardcoded IWLE team details since feedback is always directed to iw-cli's own tracker.

### Why This Approach

This approach follows the established patterns in the codebase (command structure, error handling, LinearClient usage) while being completely self-contained. By hardcoding the IWLE team details, we avoid complexity around multi-project configuration and ensure feedback always reaches the right place. The argument-based interface (vs interactive) makes it accessible to both humans and LLM agents.

## Technical Decisions

### Architecture

- **Layers Affected**: Application (new command), Infrastructure (LinearClient extension)
- **Pattern to Use**: Direct command implementation with Either-based error handling
- **Integration Points**: LinearClient GraphQL API, console output

### Technology Choices

- **Frameworks/Libraries**:
  - sttp client (already in use for HTTP)
  - upickle/ujson (already in use for JSON)
  - Existing LinearClient infrastructure
- **Data Storage**: None required (stateless command)
- **External Systems**: Linear GraphQL API (https://api.linear.app/graphql)

### Design Patterns

- Either[String, Result] for error handling (existing pattern)
- Opaque types for ApiToken (existing pattern)
- Pure functions for argument parsing and validation
- Effect isolation: parsing/validation in pure functions, HTTP calls at edges

## Components to Modify/Create

### Domain Layer
No domain changes required - command operates on raw strings

### Application Layer
**Create:**
- `.iw/commands/feedback.scala` - New command entry point
  - `@main def feedback(args: String*): Unit` - Command entry point
  - `parseFeedbackArgs(args: Seq[String]): Either[String, FeedbackRequest]` - Pure argument parser
  - `FeedbackRequest` case class for validated inputs (title, description, type)

### Infrastructure Layer
**Modify:**
- `.iw/core/LinearClient.scala`
  - Add `createIssue(title: String, description: String, teamId: String, token: ApiToken): Either[String, CreatedIssue]`
  - Add `buildCreateIssueMutation(...)` helper for GraphQL mutation
  - Add `parseCreateIssueResponse(json: String)` for response parsing
  - Add `CreatedIssue` case class (id, url)

**Add constant:**
- `.iw/core/Constants.scala`
  - Add `IwCliTeamId = "..."` (Linear team ID for IWLE - needs to be looked up)

### Presentation Layer
- Console output using existing `Output.success()`, `Output.error()`
- Output format: Created issue URL/ID on success

## Testing Strategy

### Unit Tests
**Create:**
- `.iw/core/test/LinearClientCreateIssueTest.scala`
  - Test `parseCreateIssueResponse` with valid JSON
  - Test error handling for malformed responses
  - Test error handling for API errors (missing fields, null data)

**Create:**
- `.iw/commands/test/FeedbackArgsParserTest.scala`
  - Test parsing valid args: title only
  - Test parsing with --description flag
  - Test parsing with --type flag (bug, feature)
  - Test error cases: missing title, invalid type
  - Test multiple arguments forming title

### Integration Tests
Unit tests will cover JSON parsing; actual API calls will be tested in E2E with real token.

### End-to-End Tests
**Create:**
- `.iw/test/feedback.bats`
  - Test error when LINEAR_API_TOKEN not set
  - Test error with empty title
  - Test error with invalid --type value
  - Test successful creation (requires real LINEAR_API_TOKEN, creates actual issue)
  - Test output format includes issue URL/ID

## Documentation Requirements

- [x] Code documentation (inline comments for GraphQL mutation format)
- [ ] API documentation (N/A - command-line interface)
- [ ] Architecture decision record (N/A - follows existing patterns)
- [x] User-facing documentation (help text in command file header)
- [ ] Migration guide (N/A - new feature, no breaking changes)

## Deployment Considerations

### Database Changes
None - stateless command

### Configuration Changes
None - intentionally bypasses configuration system

### Deployment Strategy
Standard merge to main - command becomes available immediately

### Rollback Plan
Remove command file if issues occur - no data/state to clean up

## Complexity Assessment

**Estimated Effort:** 6 hours

**Reasoning:**
- New command file following existing pattern: ~1h
- LinearClient.createIssue implementation: ~2h
- Unit tests for parsing logic: ~1h
- E2E test in BATS: ~1h
- Documentation and testing iterations: ~1h

**Complexity Level:** Simple

- Single new command file
- One extension to existing LinearClient
- Clear solution path following established patterns
- No complex integration or state management
- Main complexity: GraphQL mutation syntax for Linear API

## Risks & Mitigations

### Risk 1: Linear team ID lookup required
**Likelihood:** High
**Impact:** Low
**Mitigation:** Use Linear API viewer query or web UI to look up IWLE team ID once during implementation. Hardcode in Constants.

### Risk 2: Linear API mutation differs from query patterns
**Likelihood:** Medium
**Impact:** Low
**Mitigation:** Refer to Linear API documentation for createIssue mutation. Test with real token in E2E tests. Pattern similar to existing query implementation.

### Risk 3: Created issues spam IWLE team
**Likelihood:** Low (only during testing)
**Impact:** Medium
**Mitigation:** Use descriptive titles in E2E tests like "[TEST] feedback command test". Consider adding optional --dry-run flag in future if abuse becomes an issue.

### Risk 4: Missing LINEAR_API_TOKEN breaks command
**Likelihood:** High (expected)
**Impact:** Low
**Mitigation:** Clear error message directing user to set environment variable. Same pattern as existing `iw issue` command.

## Dependencies

### Prerequisites
- Linear API token (LINEAR_API_TOKEN environment variable)
- IWLE team ID from Linear workspace
- Existing LinearClient infrastructure
- sttp client and upickle dependencies (already present)

### Blocked By
None

### Blocks
None

## Open Questions

- [x] What is the Linear team ID for IWLE? (To be looked up during implementation)
- [x] Should we support additional issue fields (labels, priority)? (No - keep simple, can add later)
- [x] Should description be optional? (Yes - use empty string if not provided)
- [x] Default issue type if --type not specified? (Feature - most common feedback type)

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Review this analysis for accuracy and completeness
2. Run `/create-tasks IWLE-76` to generate implementation plan
3. Run `/review-tasks IWLE-76` for implementation plan validation
