# Implementation Log: E2E tests create real issues in Linear - need mock or sandbox

**Issue:** IWLE-131

This log tracks the evolution of implementation across phases.

---

## Phase 1: E2E tests skip real API calls by default (2025-12-21)

**What was built:**
- Modified `.iw/test/feedback.bats` to require `ENABLE_LIVE_API_TESTS` environment variable for live API tests

**Changes made:**
- Updated skip conditions in 3 test functions to check for both `LINEAR_API_TOKEN` AND `ENABLE_LIVE_API_TESTS`
- Updated skip messages to clearly explain both requirements

**Decisions made:**
- Used simple OR condition (`-z "$VAR1" || -z "$VAR2"`) for clarity
- Skip message explains how to enable: "Set LINEAR_API_TOKEN and ENABLE_LIVE_API_TESTS=1 to enable"

**Testing:**
- Verified all 8 feedback tests pass/skip correctly with no env vars set
- Live API tests (3) now skip by default
- Non-API tests (5) continue to pass unchanged

**Code review:**
- Iterations: 1
- Review file: review-phase-01-20251221-222014.md
- Status: PASSED (no critical issues)

**For next phases:**
- Phase 2 can add warning messages when live tests are enabled
- Phase 3 can add mock-based unit tests independently

**Files changed:**
```
M .iw/test/feedback.bats
```

---

## Phase 2: Explicit live API opt-in mechanism (2025-12-21)

**What was built:**
- Added warning message when live API tests are enabled
- Added comprehensive Testing section to README.md

**Changes made:**
- Added `setup_file()` function to `.iw/test/feedback.bats` with warning message
- Warning uses BATS `>&3` file descriptor for user-visible output
- Updated README.md with detailed Testing section covering all test types

**Decisions made:**
- Used `setup_file()` (runs once per file) instead of `setup()` (runs per test) for warning
- Warning outputs to fd 3 for BATS compatibility
- README documents three test types: Unit, E2E, Live API

**Testing:**
- Verified warning appears when both `LINEAR_API_TOKEN` and `ENABLE_LIVE_API_TESTS` are set
- Verified no warning when `ENABLE_LIVE_API_TESTS` is not set
- Verified all E2E tests pass with no regressions

**Code review:**
- Iterations: 1
- Review file: review-phase-02-20251221-235500.md
- Status: PASSED (no critical issues)

**For next phases:**
- Phase 3 can add mock-based unit tests for LinearClient
- Documentation foundation in place for CI (IWLE-114)

**Files changed:**
```
M .iw/test/feedback.bats
M README.md
```

---

## Phase 3: Mock-based unit tests with sttp backend injection (2025-12-22)

**What was built:**
- Refactored LinearClient to accept optional `SyncBackend` parameter for testability
- Added comprehensive mock-based unit tests using sttp's `SyncBackendStub`

**Changes made:**
- Added `backend: SyncBackend = defaultBackend` parameter to `validateToken`, `fetchIssue`, `createIssue`
- Changed from `quickRequest.send()` to `basicRequest.send(backend)`
- Created `LinearClientMockTest.scala` with 10 mock-based tests

**Decisions made:**
- Used default parameter to ensure backward compatibility
- Used sttp 4.x `SyncBackendStub` with `thenRespondAdjust` for mock responses
- Dependency injection pattern allows testing without real HTTP calls

**Patterns applied:**
- Dependency Injection: Backend parameter allows swapping real HTTP for mocks
- Test Doubles: BackendStub provides canned responses for testing

**Testing:**
- 10 new mock-based tests added
- Tests cover: validateToken (2), fetchIssue (3), createIssue (4), GraphQL error (1)
- All tests pass without LINEAR_API_TOKEN set
- No real API calls made in unit tests

**Code review:**
- Iterations: 1
- Review file: review-phase-03-20251222-001500.md
- Status: PASSED (no critical issues)

**Files changed:**
```
M .iw/core/LinearClient.scala
A .iw/core/test/LinearClientMockTest.scala
```

---
