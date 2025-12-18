# Task Review for IWLE-76

Generated: 2025-12-18
Status: 12/12 issues addressed

## Critical Issues

- [x] **[C1]** Use enum instead of String for issue type field
  - **Location:** Phase 2, Task 1 (Feedback Argument Parser)
  - **Issue:** The plan specifies "Parse --type flag value (default to 'feature', validate 'bug' or 'feature')" using String with manual validation. This is Scala 2 thinking and loses compile-time type safety.
  - **Impact:** String-based type handling allows invalid states, scatters validation logic, and prevents the compiler from catching errors. Magic strings like "feature" as default are error-prone.
  - **Recommendation:** Define `enum IssueType { case Bug, Feature }` and update FeedbackRequest to use it. Parser should return `Either[FeedbackError, IssueType]` from type parsing, with `IssueType.Feature` as the default.

- [x] **[C2]** Replace Either[String, ...] with typed error ADT (Decision: Keep String for consistency)
  - **Location:** Phase 1 & 2, all tasks
  - **Issue:** The plan uses `Either[String, Result]` throughout, but String errors lose structure - callers cannot distinguish error categories (validation, API, network, missing env var).
  - **Impact:** Cannot handle errors appropriately (some are user-fixable, others are technical). Testing error handling is difficult when all errors look identical. Retry logic impossible without error classification.
  - **Recommendation:** Define a sealed error type before implementation:
    ```scala
    enum FeedbackError:
      case MissingApiToken
      case InvalidTitle(provided: String)
      case InvalidType(provided: String, allowed: List[String])
      case NetworkError(reason: String)
      case ApiError(message: String, statusCode: Option[Int])
      case MalformedResponse(reason: String)
    ```
    Then use `Either[FeedbackError, Result]` throughout.

## Warnings

- [x] **[W1]** Missing network error handling strategy (Accepted: follow during impl)
  - **Location:** Phase 1, Task 4 (createIssue Method)
  - **Issue:** The plan assumes HTTP calls either succeed or return API error responses, but doesn't account for network failures: connection timeouts, DNS failures, connection refused, incomplete responses.
  - **Impact:** HTTP client exceptions could escape (violating functional core principle), users get confusing stack traces instead of clear messages, E2E tests won't cover network resilience.
  - **Recommendation:** Add NetworkError case to error ADT. Specify: "createIssue: Wrap HTTP call in try-catch. Map network exceptions to NetworkError with clear message."

- [x] **[W2]** Environment variable error handling not explicit in design (Accepted: follow during impl)
  - **Location:** Phase 2, Task 2 (Command Entry Point)
  - **Issue:** The plan lists "error when LINEAR_API_TOKEN not set" as E2E test but doesn't include this in the error handling design. This is a real failure mode needing an explicit error case.
  - **Impact:** Either env var reading uses unsafe `.get()` (runtime crash risk) or error handling becomes ad-hoc outside the plan.
  - **Recommendation:** Add MissingApiToken to FeedbackError ADT. Specify in command entry: "Attempt to read LINEAR_API_TOKEN from environment. If missing, return Left(MissingApiToken) with clear message about how to set it."

- [x] **[W3]** parseCreateIssueResponse edge cases underspecified (Accepted: follow during impl)
  - **Location:** Phase 1, Task 2 (Response Parser)
  - **Issue:** The plan says "Handle errors array if present" but doesn't specify: What does Linear API errors array contain? Which fields are required vs optional? How to distinguish malformed response from valid response with errors?
  - **Impact:** Implementation might miss edge cases, tests won't be thorough, different error cases get lumped together.
  - **Recommendation:** Before implementation, research Linear API error responses. Document the response schema. Add specific test cases for each error variant: success with issue, success=false with errors array, malformed response missing required fields.

- [x] **[W4]** Validation strategy not specified (Decision: fail-fast)
  - **Location:** Phase 2, Task 1 (Argument Parser)
  - **Issue:** Plan mentions "error with empty title" and "error with invalid --type" but doesn't specify: Should both be validated and reported at once, or fail on first error? Which error takes priority if both are invalid?
  - **Impact:** Affects UX and test expectations. Users may prefer seeing all validation failures at once rather than fixing one-by-one.
  - **Recommendation:** Explicitly specify validation strategy. For CLI, recommend fail-fast (simpler) - validate title first, then type. Document this choice in the plan.

- [x] **[W5]** TeamId parameter lacks type safety (Dismissed: unnecessary complexity)
  - **Location:** Phase 1, Task 4 (createIssue Method)
  - **Issue:** `createIssue(title: String, description: String, teamId: String, ...)` has all String parameters - no compile-time distinction between teamId and other strings.
  - **Impact:** Can accidentally swap parameters. No validation that teamId looks like a valid UUID.
  - **Recommendation:** Consider using opaque type: `opaque type TeamId = String` in Constants, then use `TeamId` type in method signature. Alternatively, at minimum validate teamId format before API call.

## Suggestions

- [x] **[S1]** Add explicit chaining strategy for Either operations (Dismissed: impl detail)
  - **Location:** Phase 2, Task 2 (Command Entry Point)
  - **Issue:** Plan doesn't clarify if the main function will chain Either operations with for-comprehension or pattern match on each separately.
  - **Impact:** Affects code structure and readability.
  - **Recommendation:** Specify in plan: "The feedback command will chain parsing and API call using Either's for-comprehension" and show example structure.

- [x] **[S2]** Document success output format explicitly (Dismissed: impl detail)
  - **Location:** Phase 2, Task 2 (Command Entry Point)
  - **Issue:** Plan mentions "print success message with issue URL and ID" but doesn't specify exact format or whether it should match other iw-cli commands.
  - **Impact:** Inconsistent output formats across commands.
  - **Recommendation:** Specify: "On success, print: 'Created issue IWLE-XXX: https://linear.app/...'" using Output.success() for consistency with other commands.

- [x] **[S3]** Consider rate limiting handling (Dismissed: YAGNI)
  - **Location:** Phase 1, Task 4 (createIssue Method)
  - **Issue:** Linear API has rate limits (typically returns 429). Plan doesn't address detection or handling.
  - **Impact:** Users hitting rate limits get confusing errors.
  - **Recommendation:** Add to error handling: detect 429 status code, return clear "Rate limited, please wait and retry" message. Consider adding to ApiError case with statusCode for rate limit detection.

- [x] **[S4]** GraphQL string escaping needs specification (Accepted: follow during impl)
  - **Location:** Phase 1, Task 3 (Mutation Builder)
  - **Issue:** Plan mentions "properly escape quotes in title and description" but doesn't specify escaping strategy for GraphQL strings.
  - **Impact:** Titles or descriptions with quotes/special characters could break the mutation.
  - **Recommendation:** Specify escaping approach: escape quotes as `\"`, handle newlines as `\\n`, consider using JSON encoding for the entire mutation body.

- [x] **[S5]** Add extension method specification for ApiToken (Dismissed: impl detail)
  - **Location:** Phase 1, Task 4 (createIssue Method)
  - **Issue:** Plan uses existing ApiToken opaque type but doesn't show how HTTP layer extracts the value.
  - **Impact:** Implementation detail left unclear.
  - **Recommendation:** Confirm ApiToken has `.value` extension method or add one if needed for Authorization header construction.

## Process Compliance

### Included in Plan
- Test-Driven Development: Plan includes RED-GREEN-REFACTOR cycle for every task
- Unit tests: Comprehensive tests for parsing functions
- E2E tests: Cover error and success paths
- Documentation: Help text and code comments planned
- Error handling: Either-based approach specified (though needs typed errors)

### Missing from Plan
- Network resilience: No mention of timeout handling or retry strategy
- Rate limiting: No handling for API rate limits
- Validation rules: Specific constraints (title max length?) not specified

## Positive Aspects

- **TDD workflow properly integrated**: Every task follows RED-GREEN-REFACTOR cycle
- **Phase structure logical**: Infrastructure first (Phase 1), then command (Phase 2)
- **FCIS architecture respected**: Pure functions for parsing, effects at edges
- **Existing patterns followed**: Uses established Either pattern, Output helpers, LinearClient structure
- **Appropriate phase sizing**: 4 tasks, 3 hours per phase - well within limits
- **Success criteria clear**: Each task has measurable done criteria
- **Dual checkboxes**: [impl] and [reviewed] for tracking progress

## Recommended Plan Updates

### Priority 1 (Must Address)
1. Define `enum IssueType` instead of String-based type handling [C1]
2. Define `enum FeedbackError` with all error cases [C2]
3. Add network error handling to createIssue design [W1]
4. Explicit MissingApiToken error handling [W2]

### Priority 2 (Should Address)
5. Research and document Linear API error response format [W3]
6. Specify validation strategy (recommend fail-fast) [W4]
7. Consider TeamId opaque type or validation [W5]

### Priority 3 (Nice to Have)
8. Specify Either chaining strategy [S1]
9. Document exact success output format [S2]
10. Add rate limit handling consideration [S3]

## Next Steps

1. Address Critical Issues [C1] and [C2] - these affect the entire implementation
2. Update Phase 1 tasks to include typed error ADT
3. Update Phase 2 tasks to use enum for IssueType
4. Review updated plan before starting implementation

---

**Review Status:** ✅ All Issues Addressed - Ready for Implementation
