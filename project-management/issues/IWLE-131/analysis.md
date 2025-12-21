# Story-Driven Analysis: E2E tests create real issues in Linear - need mock or sandbox

**Issue:** IWLE-131
**Created:** 2025-12-21
**Status:** Draft
**Classification:** Simple

## Problem Statement

**What's broken:** E2E tests for the `feedback` command create actual issues in the real Linear workspace, polluting the IWLE team's issue tracker with test data like "[TEST] E2E test issue from feedback command". While these use a `[TEST]` prefix for identification, they still clutter the backlog and require manual cleanup.

**User impact:** 
- Development team sees test pollution in Linear backlog
- Real issues mixed with test artifacts reduces clarity
- Manual cleanup overhead after test runs
- Risk of accidentally closing/working on test issues
- CI automation (IWLE-114) will amplify this problem

**Why this matters:** Tests should be safe to run repeatedly without side effects. Creating real issues violates test isolation principles and makes CI/CD problematic.

## User Stories

### Story 1: E2E tests skip real API calls by default

```gherkin
Feature: Safe E2E testing
  As a developer running tests
  I want E2E tests to skip real Linear API calls by default
  So that I don't pollute the issue tracker with test data

Scenario: Running E2E tests without LINEAR_API_TOKEN
  Given I have no LINEAR_API_TOKEN set in environment
  When I run "iw test e2e"
  Then all tests pass
  And tests that require real API are skipped with informative message
  And no real Linear issues are created

Scenario: Running E2E tests with LINEAR_API_TOKEN
  Given I have LINEAR_API_TOKEN set in environment
  When I run "iw test e2e"
  Then tests that create real issues are still skipped by default
  And I see a message "Run 'ENABLE_LIVE_API_TESTS=1 iw test e2e' for live API tests"
  And no real Linear issues are created
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
Tests already have `skip` mechanism when `LINEAR_API_TOKEN` is not set. We need to add an additional gate: require explicit `ENABLE_LIVE_API_TESTS=1` environment variable for tests that create real issues. This is a small change to test conditions.

**Acceptance:**
- E2E tests pass in clean environment (no env vars)
- E2E tests pass with only LINEAR_API_TOKEN set
- Tests skip with clear message about how to enable live API tests
- No real Linear issues created unless explicitly enabled

---

### Story 2: Developer can run live API tests when explicitly needed

```gherkin
Feature: Manual live API testing
  As a developer verifying Linear integration
  I want to run live API tests when I explicitly enable them
  So that I can test real API behavior when needed

Scenario: Running live API tests with explicit opt-in
  Given I have LINEAR_API_TOKEN set in environment
  And I set ENABLE_LIVE_API_TESTS=1
  When I run "iw test e2e"
  Then tests that create real issues run successfully
  And I see "[TEST]" prefixed issues created in Linear
  And output shows "WARNING: Live API tests enabled - real Linear issues will be created"

Scenario: Partial test run with live API tests only
  Given I have LINEAR_API_TOKEN and ENABLE_LIVE_API_TESTS=1 set
  When I run "bats .iw/test/feedback.bats --filter 'creates issue'"
  Then only live API tests run
  And I can verify specific API integration behavior
```

**Estimated Effort:** 1-2h
**Complexity:** Straightforward

**Technical Feasibility:**
Add check for `ENABLE_LIVE_API_TESTS` environment variable alongside existing `LINEAR_API_TOKEN` check in BATS tests. Add warning output when live tests are enabled. No changes to production code needed.

**Acceptance:**
- Live API tests only run when both LINEAR_API_TOKEN and ENABLE_LIVE_API_TESTS=1 are set
- Clear warning message shown before running live tests
- Existing skip messages updated to explain how to enable
- Documentation updated with manual testing procedure

---

### Story 3: Test coverage maintained with mock-based tests

```gherkin
Feature: Mock-based feedback testing
  As a developer
  I want unit tests that verify feedback command logic
  So that core functionality is tested without real API calls

Scenario: Feedback command validation tested in isolation
  Given unit tests for FeedbackParser exist
  When I run "iw test unit"
  Then argument parsing is verified (title, description, type)
  And error handling is verified (missing title, invalid type)
  And no external API calls are made

Scenario: LinearClient createIssue tested with mocked HTTP
  Given unit tests for LinearClient exist
  When I run "iw test unit"
  Then mutation building is verified
  And response parsing is verified (success, errors, malformed)
  And no real Linear API calls are made
```

**Estimated Effort:** 3-4h
**Complexity:** Moderate

**Technical Feasibility:**
Current unit tests exist for LinearClient parsing logic. Need to add:
1. Unit tests for FeedbackParser (argument validation)
2. Unit tests for LinearClient.createIssue that mock sttp HTTP client
3. Tests for mutation building with various inputs

Moderate complexity due to need to mock sttp client in tests.

**Acceptance:**
- Unit tests cover all feedback command logic (parsing, validation, error handling)
- Unit tests cover LinearClient.createIssue mutation building and response parsing
- All unit tests pass without LINEAR_API_TOKEN
- No real API calls in unit test suite

---

## Architectural Sketch

**Purpose:** List WHAT components each story needs, not HOW they're implemented.

### For Story 1: E2E tests skip real API calls by default

**Test Layer:**
- BATS test condition checks for ENABLE_LIVE_API_TESTS env var
- Skip message templates explaining how to enable live tests
- Test documentation in comments

**No production code changes needed.**

---

### For Story 2: Developer can run live API tests when explicitly needed

**Test Layer:**
- BATS test setup checks for both env vars (LINEAR_API_TOKEN + ENABLE_LIVE_API_TESTS)
- Warning message output before running live tests
- BATS filter support for running subset of tests

**Documentation:**
- README section on running live API tests
- Comment documentation in test files

**No production code changes needed.**

---

### For Story 3: Test coverage maintained with mock-based tests

**Domain Layer:**
- FeedbackRequest value object (already exists)
- IssueType enum (already exists)

**Application Layer:**
- FeedbackParser unit tests (argument parsing, validation)

**Infrastructure Layer:**
- LinearClient.createIssue unit tests with mocked HTTP
- sttp mock backend or test doubles for HTTP responses

**No production code changes needed - only test additions.**

---

## Technical Risks & Uncertainties

### RESOLVED: How to mock sttp HTTP client in unit tests

**Decision:** Use sttp's built-in `BackendStub` with backend injection.

**Approach:**
1. Refactor `LinearClient` methods to accept a `Backend[Identity]` parameter
2. Default to `DefaultSyncBackend()` in production code
3. In tests, use `DefaultSyncBackend.stub` with `whenRequestMatches()` to define canned responses

**Example test pattern:**
```scala
val testBackend = DefaultSyncBackend.stub
  .whenRequestMatches(_.uri.path.startsWith(List("graphql")))
  .thenRespond("""{"data":{"issueCreate":{"success":true,"issue":{"id":"123","url":"https://..."}}}}""")

val result = LinearClient.createIssue(title, desc, teamId, token, backend = testBackend)
```

**Reference:** https://sttp.softwaremill.com/en/latest/testing/stub.html

**Impact:** Story 3 requires minor refactoring of LinearClient to accept backend parameter. Clean approach, well-supported by sttp.

---

### RESOLVED: Should we add CI configuration in this issue?

**Decision:** Option B - Leave CI setup for IWLE-114, focus on making tests CI-safe here.

**Rationale:**
- Issue acceptance criteria focus on preventing test pollution, not CI setup
- IWLE-114 is explicitly about CI setup - avoid duplication
- This issue makes tests safe to run in CI; IWLE-114 will configure CI to run them

**Scope for this issue:**
- Make E2E tests skip real API calls by default
- Add ENABLE_LIVE_API_TESTS opt-in mechanism
- Add unit tests with mocked backends

**Out of scope (deferred to IWLE-114):**
- .github/workflows/ configuration
- CI environment variable setup

---

## Total Estimates

**Story Breakdown:**
- Story 1 (E2E tests skip by default): 2-3 hours
- Story 2 (Explicit live API opt-in): 1-2 hours  
- Story 3 (Mock-based unit tests): 3-4 hours

**Total Range:** 6-9 hours

**Confidence:** High

**Reasoning:**
- Well-understood problem domain (test isolation)
- Existing skip mechanism to build upon (Story 1-2)
- Clear test requirements (prevent real API calls)
- Only uncertainty is sttp mocking approach (Story 3)
- No production code changes required
- Small, focused scope (Simple classification)

---

## Testing Approach

**Per Story Testing:**

Each story should have verification approach before marking complete.

### Story 1: E2E tests skip by default

**Verification:**
1. Unset all env vars: `unset LINEAR_API_TOKEN ENABLE_LIVE_API_TESTS`
2. Run `iw test e2e`
3. Verify all tests pass or skip appropriately
4. Verify skip messages mention ENABLE_LIVE_API_TESTS
5. Check Linear workspace - no new issues created

**Regression:**
- Run existing E2E tests that don't require API (help, validation) still pass

---

### Story 2: Explicit live API opt-in

**Verification:**
1. Set `export LINEAR_API_TOKEN=<real-token>`
2. Set `export ENABLE_LIVE_API_TESTS=1`
3. Run `iw test e2e`
4. Verify warning message appears
5. Verify test issues created in Linear with [TEST] prefix
6. Clean up test issues in Linear workspace

**Regression:**
- Without ENABLE_LIVE_API_TESTS, tests still skip correctly (Story 1)

---

### Story 3: Mock-based unit tests

**Verification:**
1. Unset all env vars
2. Run `iw test unit`
3. Verify new FeedbackParser tests pass
4. Verify new LinearClient.createIssue tests pass
5. Check no network calls made (can verify with network monitoring)

**Test Coverage Goals:**
- FeedbackParser: All argument combinations (title only, with description, with type, invalid type, missing title)
- LinearClient.createIssue: Success response, error response, malformed response, network error
- Mutation building: Proper escaping of special characters, labelIds formatting

**Regression:**
- Existing LinearClient parsing tests still pass
- Live API integration tests (when enabled) still pass

---

## Deployment Considerations

### Configuration Changes

**Environment Variables:**
- New: `ENABLE_LIVE_API_TESTS` - controls whether E2E tests create real Linear issues
- Existing: `LINEAR_API_TOKEN` - still required for live API tests

**Documentation Updates:**
- README.md: Add section on running tests
- .iw/test/feedback.bats: Update comments explaining test modes
- Contributing guide (if exists): Explain test philosophy

### Rollout Strategy

No deployment needed - test-only changes.

**Testing in development:**
1. Verify Story 1 (default skip behavior)
2. Verify Story 2 (explicit enable works)
3. Verify Story 3 (unit tests pass)
4. Run complete test suite end-to-end

### Rollback Plan

If changes break existing tests, revert commits. No production impact possible.

---

## Dependencies

### Prerequisites

**Development environment:**
- BATS installed (already required for E2E tests)
- scala-cli installed (already required for unit tests)
- LINEAR_API_TOKEN available for manual live API testing

**Knowledge:**
- How current skip mechanism works in BATS
- sttp HTTP client testing approaches

### Story Dependencies

**Sequential dependencies:**
- Story 1 must complete before Story 2 (establishes skip-by-default baseline)
- Story 3 can be done in parallel with Story 1-2 (separate test suite)

**Recommended order:**
1. Story 1 (establish skip-by-default)
2. Story 2 (add explicit opt-in) - depends on Story 1
3. Story 3 (add unit tests) - can start anytime, independent of 1-2

### External Blockers

**None identified.** This is purely internal testing infrastructure.

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: E2E tests skip by default** - Foundation. Prevents pollution immediately. Highest value.
2. **Story 2: Explicit live API opt-in** - Builds on Story 1. Preserves ability to test real integration.
3. **Story 3: Mock-based unit tests** - Enhances coverage. Can be done last or in parallel.

**Iteration Plan:**

- **Iteration 1** (Stories 1-2, 3-5h): Safe E2E testing with explicit opt-in for live API
  - After this: Tests won't pollute Linear by default, CI-safe
- **Iteration 2** (Story 3, 3-4h): Enhanced unit test coverage
  - After this: Full coverage without needing live API at all

**Value delivery:**
- After Story 1: Problem is 80% solved (no accidental pollution)
- After Story 2: Problem is 100% solved (can still test live when needed)
- After Story 3: Bonus improvement (better test coverage, faster tests)

---

## Documentation Requirements

- [x] Update .iw/test/feedback.bats header comments
  - Explain ENABLE_LIVE_API_TESTS requirement
  - Explain how to run live API tests manually
  - Update cleanup instructions
- [ ] Add README section on testing
  - How to run unit tests
  - How to run E2E tests
  - How to run live API tests
  - Environment variables explained
- [ ] Update skip messages in tests
  - Clear explanation of how to enable live tests
  - Warning message when live tests are enabled

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Resolve CLARIFY marker: sttp mocking approach (check sttp documentation)
2. Resolve CLARIFY marker: CI configuration scope (confirm IWLE-114 handles this)
3. Once CLARIFY markers resolved, ready to implement Story 1
4. Estimated completion: 6-9 hours total

---

## Notes

**Why this approach:**
- Follows existing pattern (tests already skip when LINEAR_API_TOKEN missing)
- Minimal code changes (only test files)
- No production code risk
- Solves immediate problem (Story 1) quickly
- Preserves integration testing capability (Story 2)
- Improves long-term maintainability (Story 3)

**Alternative approaches considered:**
- Separate test workspace: Requires Linear setup, still creates pollution, doesn't solve root issue
- Delete after creation: Fragile, can fail mid-test, leaves orphans
- Mock server: Over-engineering for Simple issue, adds complexity

**Chosen approach advantages:**
- Simple: Environment variable gate
- Safe: Default behavior is safe
- Flexible: Can still test live integration when needed
- Fast: No mock server infrastructure needed
