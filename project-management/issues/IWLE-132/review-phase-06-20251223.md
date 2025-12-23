# Code Review Results

**Review Context:** Phase 6: Doctor validates GitHub setup for issue IWLE-132 (Iteration 1/3)
**Files Reviewed:** 5 files
**Skills Applied:** 4 (scala3, testing, architecture, composition)
**Timestamp:** 2025-12-23 10:58:00
**Git Context:** git diff 2e676e5...HEAD

---

<review skill="scala3">

## Scala 3 Idioms Review

### Critical Issues

#### Sealed Trait Should Be Enum
**Location:** `/home/mph/Devel/projects/iw-cli-IWLE-132/.iw/core/GitHubClient.scala:10-13`
**Problem:** `GhPrerequisiteError` is defined as a sealed trait with simple case objects/classes, which is a Scala 2 pattern
**Impact:** Uses verbose Scala 2 syntax when Scala 3's enum feature would be more concise and idiomatic
**Recommendation:** Convert to Scala 3 enum for reduced boilerplate and clearer intent

```scala
// Current (Scala 2 pattern)
sealed trait GhPrerequisiteError
case object GhNotInstalled extends GhPrerequisiteError
case object GhNotAuthenticated extends GhPrerequisiteError
case class GhOtherError(message: String) extends GhPrerequisiteError

// Suggested (Scala 3 enum)
enum GhPrerequisiteError:
  case GhNotInstalled
  case GhNotAuthenticated
  case GhOtherError(message: String)
```

This is a simple ADT with all variants in one place and no complex inheritance requirements - an ideal candidate for enum. The pattern match usage in GitHubHookDoctor.scala lines 54-62 will work identically with the enum version.

### Warnings

None found.

### Suggestions

#### Consider Extension Method for CheckResult
**Location:** `/home/mph/Devel/projects/iw-cli-IWLE-132/.iw/core/DoctorChecks.scala:13-18`
**Problem:** The `hint` method is defined inside the enum, which works but doesn't leverage Scala 3's extension method capabilities
**Impact:** Minor - current approach is fine, but extension methods offer more flexibility for adding operations without modifying the enum
**Recommendation:** Consider using an extension method if you plan to add more operations to CheckResult in the future

</review>

---

<review skill="testing">

## Testing Review

### Critical Issues

None found.

### Warnings

#### Test Uses Mock Function Behavior Instead of Verifying Real Logic
**Location:** `.iw/core/test/GitHubHookDoctorTest.scala:70`
**Problem:** The test for authentication success mocks `execCommand` to return `Right("Logged in to github.com")`, but this doesn't test the actual logic in `GitHubClient.validateGhPrerequisites`. The test only verifies that when the mock returns success, the doctor check returns success - which is testing the mock's behavior, not the real implementation.
**Impact:** If the logic in `GitHubClient.validateGhPrerequisites` changes (e.g., the authentication check logic or error detection), this test will still pass because it bypasses that logic entirely.
**Recommendation:** Add test for GhOtherError case to verify the doctor's logic in interpreting different results from `validateGhPrerequisites`.

#### E2E Tests Have Environment-Dependent Assertions
**Location:** `.iw/test/doctor.bats:207-211`
**Problem:** Test has conditional assertions based on whether `gh` is installed in the CI environment. The test behaves differently on different machines, making it non-deterministic.
**Impact:** Test results vary by environment, making it harder to reproduce failures and understand what's being tested.
**Recommendation:** Structure tests to verify correct behavior explicitly in both cases.

### Suggestions

#### Consider Testing Edge Case: Empty Repository in Config
**Location:** `.iw/core/GitHubHookDoctor.scala:52`
**Problem:** The code uses `config.repository.getOrElse("")` which could pass an empty string to `validateGhPrerequisites`. There's no test verifying this edge case.
**Recommendation:** Add a test to verify behavior when repository is not set.

#### E2E Test Names Could Be More Behavior-Focused
**Location:** `.iw/test/doctor.bats:184-212`
**Problem:** Test names describe the scenario but not the expected behavior.
**Recommendation:** Use behavior-driven naming.

#### Unit Test Coverage Missing GhOtherError Path
**Location:** `.iw/core/test/GitHubHookDoctorTest.scala:64-83`
**Problem:** Tests cover `Skip`, `Success`, and `GhNotAuthenticated` error cases, but don't test the `GhOtherError` path.
**Recommendation:** Add test for the other error case.

</review>

---

<review skill="architecture">

## Architecture Review

### Critical Issues

#### Infrastructure Dependency in Core Package
**Location:** `/home/mph/Devel/projects/iw-cli-IWLE-132/.iw/core/GitHubHookDoctor.scala:5`
**Problem:** GitHubHookDoctor is in package `iw.core` but imports from `iw.core.infrastructure.CommandRunner`, creating a dependency from core to infrastructure layer
**Impact:** This violates FCIS architecture principle - functional core should not depend on infrastructure (imperative shell). The dependency direction is inverted.
**Recommendation:** Move GitHubHookDoctor to the infrastructure package or application layer, as it orchestrates infrastructure concerns.

#### Core Layer Performing Side Effects
**Location:** `/home/mph/Devel/projects/iw-cli-IWLE-132/.iw/core/GitHubHookDoctor.scala:31-32`
**Problem:** `checkGhInstalled` function calls `CommandRunner.isCommandAvailable` directly, executing I/O from what appears to be core logic
**Impact:** Violates FCIS purity requirement - core should be pure functions with no side effects. Makes testing harder and couples core to infrastructure implementation.
**Recommendation:** The functions are designed correctly with dependency injection, but the placement in `iw.core` package suggests these are core functions when they're actually shell/infrastructure adapters.

### Warnings

#### Inconsistent Package Organization Between Related Files
**Location:** `.iw/core/GitHubHookDoctor.scala` vs `.iw/core/GitHubClient.scala`
**Problem:** Both are in `package iw.core` but both are doing I/O orchestration (shell functions), not pure domain logic. Compare with CommandRunner which is correctly in `iw.core.infrastructure`.
**Recommendation:** Review GitHubClient placement as well - it also performs I/O and should likely be in infrastructure package.

#### Hook Discovery Mechanism Uses Reflection in Command Layer
**Location:** `/home/mph/Devel/projects/iw-cli-IWLE-132/.iw/commands/doctor.scala:29-49`
**Problem:** Reflection-based hook discovery logic is embedded in the command script rather than abstracted into a reusable service.
**Recommendation:** Extract hook discovery into a dedicated service in the application or infrastructure layer.

### Suggestions

#### Consider Extracting Pure Validation Logic from GitHubHookDoctor
**Location:** `.iw/core/GitHubHookDoctor.scala:15-24, 41-62`
**Problem:** The check functions contain both pure conditional logic (checking tracker type) and I/O coordination.
**Recommendation:** Extract pure decision logic into separate functions for easier testing and clearer separation of concerns.

#### Hook Command Objects Follow Inconsistent Naming Pattern
**Location:** `.iw/commands/github.hook-doctor.scala:6` vs `.iw/commands/issue.hook-doctor.scala:6`
**Problem:** IssueHookDoctor exposes a single `check` value, while GitHubHookDoctor exposes multiple check values.
**Recommendation:** Standardize on one pattern - suggest using a `checks` list for all hooks.

</review>

---

<review skill="composition">

## Composition Patterns Review

### Critical Issues

None found.

### Warnings

#### Tight Coupling Between Core and Hook Layer via Reflection
**Location:** `.iw/commands/doctor.scala:39-46`
**Problem:** The hook discovery mechanism uses reflection to find methods returning `Check` type, creating tight coupling between the core doctor command and the hook implementation.
**Impact:** If hook implementations change their method signatures or return different types, failures will only be caught at runtime.
**Recommendation:** Consider introducing a more explicit composition mechanism using a trait-based hook protocol.

#### Function Composition Could Eliminate Duplication
**Location:** `.iw/core/GitHubHookDoctor.scala:15-32` and `.iw/core/GitHubHookDoctor.scala:41-74`
**Problem:** Both `checkGhInstalledWith` and `checkGhAuthenticatedWith` follow the same pattern: check if GitHub tracker → delegate to specific validation logic.
**Recommendation:** Extract the common pattern into a composable higher-order function.

### Suggestions

#### Consider Making Hook Checks Discoverable Through Type System
**Location:** `.iw/commands/github.hook-doctor.scala:6-9`
**Problem:** The hook exposes checks as individual `val` fields discovered via reflection.
**Recommendation:** If the project grows to have many hooks, consider using a type class or registry pattern for compile-time hook discovery.

#### List Composition in doctor.scala Is Well Done
**Location:** `.iw/commands/doctor.scala:61`
**Problem:** None - this is good use of horizontal composition
**Impact:** N/A
**Recommendation:** The pattern `val allChecks = baseChecks ++ collectHookChecks()` is a good example of horizontal composition. Well done!

</review>

---

## Summary

- **Critical issues:** 3 (must fix before merge)
- **Warnings:** 6 (should fix)
- **Suggestions:** 7 (nice to have)

### By Skill
- scala3: 1 critical, 0 warnings, 1 suggestion
- testing: 0 critical, 2 warnings, 3 suggestions
- architecture: 2 critical, 2 warnings, 2 suggestions
- composition: 0 critical, 2 warnings, 1 suggestion

### Key Actions Required

1. **Architecture (Critical):** GitHubHookDoctor is in `iw.core` but depends on infrastructure - consider moving to `iw.core.infrastructure`
2. **Scala 3 (Critical):** Convert `GhPrerequisiteError` sealed trait to Scala 3 enum (Note: this is in GitHubClient.scala from Phase 4)
3. **Testing:** Add test coverage for `GhOtherError` path
