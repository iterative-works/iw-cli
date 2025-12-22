# Code Review Results

**Review Context:** Phase 1: Initialize project with GitHub tracker for IWLE-132 (Iteration 1/3)
**Files Reviewed:** 5 files
**Skills Applied:** 4 (scala3, testing, architecture, composition)
**Timestamp:** 2025-12-22 14:45:00
**Git Context:** git diff 4253a6b

---

<review skill="scala3">

## Scala 3 Idioms Review

### Critical Issues

None found.

### Warnings

#### Pattern Matching on IssueTrackerType Not Exhaustive (Multiple Locations)

**Location:** `.iw/core/Config.scala:76-79`

**Problem:** The repeated pattern matching across multiple locations (lines 76-79, 113-124) creates maintenance burden when adding new tracker types.

**Recommendation:** Consider creating a helper extension method to reduce duplication.

---

#### String-Based Repository Identifier Could Be Opaque Type

**Location:** `.iw/core/Config.scala:64`

**Problem:** `repository: Option[String]` uses primitive String for GitHub repository identifiers. This allows invalid formats to compile.

**Recommendation:** Create an opaque type for repository identifiers to centralize validation and provide zero-cost type safety.

### Suggestions

1. Consider Extension Methods for GitRemote Operations (separate core data from derived operations)
2. Enum Could Have Companion Object Methods (centralize enum/string conversions)
3. Multiple Pattern Matches Could Use Conditional Expressions (use `if/else` for binary logic)
4. Test File Has Duplicate PURPOSE Comments (combine into single 2-line comment)

</review>

---

<review skill="testing">

## Testing Review

### Critical Issues

#### Missing Edge Case: Empty Path Components in Repository Validation
**Location:** `.iw/core/test/ConfigTest.scala:78-80`
**Problem:** Test only covers single-component path, doesn't test empty owner (`/repo`), empty repo (`owner/`), or multiple slashes.
**Recommendation:** Add comprehensive edge case tests for repository format validation.

#### Missing E2E Test: GitHub Tracker Without Git Remote
**Location:** `.iw/test/init.bats:172-275`
**Problem:** No test for `iw init --tracker=github` without a git remote configured.
**Recommendation:** Add test for GitHub tracker initialization without git remote.

### Warnings

1. Incomplete Assertion Coverage in Round-Trip Test - doesn't verify `team` field preservation
2. E2E Tests Don't Verify Config File Can Be Read Back - only grep-based verification
3. Missing Validation Test for Repository Format with Special Characters

### Suggestions

1. Consider Testing Behavior Rather Than Implementation Details
2. E2E Test Names Could Be More Descriptive (behavior-focused)
3. Consider Adding Test for URL Parsing Edge Cases

</review>

---

<review skill="architecture">

## Architecture Review

### Critical Issues

None found.

### Warnings

#### Domain Logic Mixed with String Parsing in GitRemote
**Location:** `.iw/core/Config.scala:31-53`
**Problem:** `repositoryOwnerAndName` contains GitHub-specific business logic mixed with string parsing.
**Recommendation:** Consider extracting repository parsing logic into a separate domain service.

#### Conditional Field in ProjectConfiguration Based on Tracker Type
**Location:** `.iw/core/Config.scala:58-65`
**Problem:** `team` vs `repository` are mutually exclusive fields with implicit state management.
**Recommendation:** Consider using ADT to make tracker-specific configuration explicit.

#### Infrastructure Adapter Not in Infrastructure Package
**Location:** `.iw/core/Git.scala:4`
**Problem:** `GitAdapter` performs I/O but is in `iw.core` instead of `iw.core.infrastructure`.

#### ConfigFileRepository Not in Infrastructure Package
**Location:** `.iw/core/ConfigRepository.scala:4`
**Problem:** Same issue - file I/O operations in core namespace.

### Suggestions

1. Consider Opaque Type for Repository Identifier
2. Validation Logic Duplicated Between Domain and Infrastructure
3. String Literals for Error Messages Could Use Constants

</review>

---

<review skill="composition">

## Composition Patterns Review

### Critical Issues

None found.

### Warnings

#### Mixed Data and Validation Logic in GitRemote
**Location:** `.iw/core/Config.scala:8-53`
**Problem:** `GitRemote` contains both data and complex parsing/validation logic.
**Recommendation:** Extract parsing and validation into composable functions.

#### Serialization Logic Tightly Coupled to Domain Model
**Location:** `.iw/core/Config.scala:74-142`
**Problem:** Manual string interpolation and tracker-type-specific branching in serialization.
**Recommendation:** Use composable serialization functions.

### Suggestions

1. Consider Smart Constructor Pattern for ProjectConfiguration
2. Extract Repository Format Validation to single composable function

</review>

---

## Summary

- **Critical issues:** 2 (test coverage gaps - should add before merge)
- **Warnings:** 11 (should consider for future iterations)
- **Suggestions:** 13 (nice to have improvements)

### By Skill
- scala3: 0 critical, 2 warnings, 4 suggestions
- testing: 2 critical, 3 warnings, 3 suggestions
- architecture: 0 critical, 4 warnings, 3 suggestions
- composition: 0 critical, 2 warnings, 2 suggestions

### Recommended Actions

**For this phase (must fix):**
1. ✅ FIXED: Added edge case tests for repository format validation (empty owner/repo, multiple slashes)
2. ✅ FIXED: Added E2E test for GitHub init with non-GitHub remote (shows warning)
3. ✅ FIXED: Bug in validation - `split` needed -1 limit to preserve trailing empty strings

**For future phases (nice to have):**
- Consider opaque type for GitHubRepository
- Consider ADT for TrackerConfiguration
- Move GitAdapter/ConfigFileRepository to infrastructure package
- Refactor duplicated pattern matching
