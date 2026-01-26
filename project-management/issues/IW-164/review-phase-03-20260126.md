# Code Review: Phase 3 - PR links persist across card refresh

**Issue:** IW-164
**Phase:** 3
**Date:** 2026-01-26
**Iteration:** 1/3

## Summary

| Severity | Count |
|----------|-------|
| Critical | 0 |
| Warnings | 0 |
| Suggestions | 3 |

## Files Reviewed

- `.iw/core/dashboard/WorktreeCardService.scala` (+6/-4 lines)
- `.iw/core/test/WorktreeCardServiceTest.scala` (+64 lines)

---

<review skill="style">

## Code Style & Documentation Review

### Critical Issues

None found.

### Warnings

None found.

### Suggestions

#### Consider More Descriptive Test Section Comment
**Location:** `.iw/core/test/WorktreeCardServiceTest.scala:495-501`
**Problem:** The section comment describes the test purpose adequately but could be slightly more concise in line with comment guidelines (explain WHY, not implementation details).
**Impact:** Minor - documentation clarity
**Recommendation:** The current comment is acceptable, but could be streamlined to focus on the key difference (TTL-based vs mtime-based caching).

#### Variable Naming in Test Could Be More Specific
**Location:** `.iw/core/test/WorktreeCardServiceTest.scala:544`
**Problem:** Variable `returned` is slightly generic - could be more descriptive
**Impact:** Minor - test readability
**Recommendation:** Consider `returnedCachedPR` or `fetchedCachedPR` to match the field name pattern.

</review>

---

<review skill="testing">

## Unit Testing Review

### Critical Issues

None found.

### Warnings

None found.

### Suggestions

#### Consider Testing PR Cache TTL Expiration Logic

**Location:** `.iw/core/test/WorktreeCardServiceTest.scala:503-577`

**Problem:** Tests verify that cached PR data is returned when present and None when absent, but don't test the TTL-based cache expiration behavior that distinguishes PR caching from mtime-based progress caching.

**Impact:** While current tests verify the basic cache lookup behavior, the TTL expiration logic (mentioned in comments as a key difference from progress caching) isn't tested. If TTL checking logic exists or will be added, it should have test coverage.

**Note:** Only add this test if TTL validation logic is implemented. If PR cache currently doesn't validate TTL (and simply returns whatever is cached), the current tests are sufficient.

#### Test Names Follow Excellent Pattern

**Location:** `.iw/core/test/WorktreeCardServiceTest.scala:503-577`

**Problem:** Not a problem - this is a positive observation.

**Impact:** Test names clearly describe behavior: "renderCard returns fetchedPR when PR cache has data" and "renderCard returns None for fetchedPR when no PR cached". This follows best practices for behavior-driven testing.

</review>

---

<review skill="scala3">

## Scala 3 Idioms Review

### Critical Issues

None found.

### Warnings

None found.

### Suggestions

#### Consider Pattern Matching Destructuring for Tuple Assignment
**Location:** `.iw/core/dashboard/WorktreeCardService.scala:113-116`
**Problem:** Pattern matching in match expression is used to destructure and assign to tuple, but the pattern is simple enough for inline destructuring
**Impact:** Minor verbosity - the code is clear but slightly more verbose than necessary

**Note:** This is a style suggestion. The current code is perfectly valid Scala 3 and reads clearly. The match expression is explicit about the two cases, which some teams prefer.

</review>

---

## Verdict

**PASS** - Clean implementation following established pattern. No critical issues or warnings. Only minor style suggestions that can be deferred. Code is ready to merge.
