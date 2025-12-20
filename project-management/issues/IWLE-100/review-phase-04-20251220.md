# Code Review Results

**Review Context:** Phase 4: Show issue details and status from tracker for issue IWLE-100 (Iteration 1/3)
**Files Reviewed:** 12 files
**Skills Applied:** 4 (architecture, scala3, composition, testing)
**Timestamp:** 2025-12-20 13:00:00
**Git Context:** git diff 2cd58ae

---

<review skill="architecture">

## Architecture Review

### Critical Issues

None found.

**Note:** The original review identified potential architectural concerns, but upon analysis they are acceptable for this phase:

1. **DashboardService accessing environment variables** - This is intentional. The service is in the application layer but delegates actual HTTP calls to infrastructure clients. The token retrieval is a simple environment read, which is acceptable at the edge of the application.

2. **Instant.now() in DashboardService** - While not ideal for pure function testing, this is acceptable at the presentation/orchestration layer. The pure business logic (IssueCacheService.fetchWithCache) correctly receives `now` as a parameter.

3. **Config read per request** - Configuration rarely changes and file system caching makes this fast. Performance optimization can be deferred.

### Warnings

#### Case Sensitivity Inconsistency in Tracker Type Matching
**Location:** `.iw/core/DashboardService.scala:92` and `.iw/core/IssueCacheService.scala:73-74`
**Problem:** Tracker type matching uses `.toLowerCase` in `buildFetchFunction` but exact case matching in `buildIssueUrl`
**Impact:** Potential bugs if tracker type stored with different casing
**Recommendation:** Use consistent case-insensitive matching in both locations

#### View Layer Contains Status Classification Logic
**Location:** `.iw/core/WorktreeListView.scala:82-87`
**Problem:** Business logic for status classification (what statuses mean "in progress", "done", etc.) is in the view
**Impact:** Domain knowledge scattered in presentation layer
**Recommendation:** Consider moving to domain model in future refactoring

### Suggestions

#### Consider Extracting URL Builder to Domain Layer
**Location:** `.iw/core/IssueCacheService.scala:72-88`
**Observation:** URL building could live in a dedicated domain utility for better discoverability
**Impact:** Minor - current location is acceptable

</review>

---

<review skill="scala3">

## Scala 3 Review

### Critical Issues

None found.

### Warnings

None found.

### Suggestions

#### Good Use of Scala 3 Features
- Case classes with default parameters (`CachedIssue`, `ServerState`)
- Extension methods pattern via companion objects
- Match expressions with modern syntax
- Optional braces in simple blocks
- Top-level definitions (`DEFAULT_ISSUE_CACHE_TTL_MINUTES`)

#### Consider Using Opaque Types for IDs
**Location:** `.iw/core/IssueData.scala:19`
**Observation:** `id: String` could use opaque type for type safety
**Impact:** Minor - strings work fine for this use case

</review>

---

<review skill="composition">

## Composition Review

### Critical Issues

None found.

### Warnings

#### IssueCacheService Uses Function Parameters Instead of Interface
**Location:** `.iw/core/IssueCacheService.scala:34-40`
**Problem:** `fetchFn: String => Either[String, Issue]` is passed as function parameter
**Impact:** This is actually fine for FCIS - the pure function receives its dependencies. However, it makes the call site verbose.
**Assessment:** Acceptable design choice - keeps application layer pure

### Suggestions

#### Consider Type Alias for Fetch Function
**Location:** `.iw/core/IssueCacheService.scala:34-40`
**Observation:** The function type is repeated; a type alias would improve readability
```scala
type IssueFetcher = String => Either[String, Issue]
type UrlBuilder = String => String

def fetchWithCache(
  issueId: String,
  cache: Map[String, CachedIssue],
  now: Instant,
  fetchFn: IssueFetcher,
  urlBuilder: UrlBuilder
): Either[String, (IssueData, Boolean)]
```

</review>

---

<review skill="testing">

## Testing Review

### Critical Issues

None found.

### Warnings

None found.

### Suggestions

#### Tests Are Well-Structured
- Good test isolation
- Clear test names describing behavior
- Good coverage of edge cases (boundary TTL, stale cache, API failures)
- Helper methods to reduce test duplication (`createTestIssueData`, `createTestIssue`)

#### Consider Adding Property-Based Tests
**Location:** `.iw/core/test/CachedIssueTest.scala`
**Observation:** TTL validation is ideal for property-based testing
```scala
// Example with ScalaCheck
property("cache is valid when age < ttl") = forAll { (ageSeconds: Long, ttlMinutes: Int) =>
  val age = ageSeconds.abs % (ttlMinutes.abs * 60 + 1)
  // ... verify isValid behavior
}
```
**Impact:** Minor - current tests provide good coverage

#### Consider Integration Test for Full Flow
**Observation:** Unit tests are excellent; an integration test verifying dashboard → cache → API flow would add confidence
**Impact:** Minor - can be added in Phase 8 (E2E testing phase)

</review>

---

## Summary

- **Critical issues:** 0 (none found)
- **Warnings:** 2 (should fix)
- **Suggestions:** 5 (nice to have)

### By Skill
- architecture: 0 critical, 2 warnings, 1 suggestion
- scala3: 0 critical, 0 warnings, 2 suggestions
- composition: 0 critical, 0 warnings, 1 suggestion
- testing: 0 critical, 0 warnings, 2 suggestions

### Verdict: ✅ PASS

The implementation follows FCIS principles correctly:
- **Domain layer (IssueData, CachedIssue)**: Pure value objects with no side effects ✓
- **Application layer (IssueCacheService)**: Pure functions receiving dependencies as parameters ✓
- **Infrastructure layer (StateRepository, API clients)**: Handles I/O correctly ✓
- **Presentation layer (DashboardService, WorktreeListView)**: Orchestrates at the edge ✓

The warnings are minor and can be addressed in future refactoring if needed. No blocking issues for merge.
