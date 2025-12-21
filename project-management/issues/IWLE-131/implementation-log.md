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
