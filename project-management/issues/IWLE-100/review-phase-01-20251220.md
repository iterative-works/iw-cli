# Code Review Results

**Review Context:** Phase 1: View basic dashboard with registered worktrees for issue IWLE-100
**Files Reviewed:** 12 files
**Skills Applied:** 4 (scala3, architecture, testing, composition)
**Timestamp:** 2025-12-20
**Git Context:** Uncommitted changes - new Scala files implementing dashboard foundation

---

<review skill="scala3">

## Scala 3 Idioms Review

### Critical Issues

None found.

### Warnings

#### Primitive Obsession - Domain Types Should Use Opaque Types
**Location:** `.iw/core/WorktreeRegistration.scala:8-15`
**Problem:** Domain model uses raw String types for IssueId, TrackerType, and Team, which are domain concepts with distinct semantics.
**Recommendation:** Use opaque types for domain identifiers to get compile-time type safety without runtime overhead.

#### Union Type Without Explicit Annotation
**Location:** `.iw/commands/dashboard.scala:69-79`
**Problem:** The `openBrowser` function returns Unit but could benefit from explicit Either[String, Unit] for better error signaling.
**Recommendation:** Consider making return type explicit for testability, though current implementation is acceptable for Phase 1.

### Suggestions

1. Consider Enum for OS Platform Detection (dashboard.scala)
2. Extension Method for Instant Formatting (WorktreeListView.scala)
3. Top-Level Definitions for Functions are fine for command scripts

</review>

---

<review skill="architecture">

## Architecture Review

### Critical Issues

#### Non-Pure Function in Presentation Layer
**Location:** `.iw/core/WorktreeListView.scala:32`
**Problem:** `formatRelativeTime` calls `Instant.now()` directly, introducing non-determinism into presentation layer.
**Impact:** Violates FCIS principle - makes view rendering non-deterministic and harder to test.
**Recommendation:** Pass current time as parameter from shell layer.

#### Application Layer Directly Depends on Infrastructure
**Location:** `.iw/core/ServerStateService.scala:7`
**Problem:** Application service imports concrete `StateRepository` from infrastructure package.
**Impact:** Violates hexagonal architecture - application layer should depend on port interfaces.
**Recommendation:** Define `StateRepositoryPort` trait in application layer.

#### Flat Package Structure Violates Layered Organization
**Location:** `.iw/core/`
**Problem:** All files in flat directory but use hierarchical package declarations.
**Impact:** Creates confusion between physical and logical organization.
**Recommendation:** Reorganize files into proper directory hierarchy.

### Warnings

1. Primitive Obsession in Domain Model (use opaque types)
2. Domain Logic Mixed with Infrastructure in DashboardService (CSS in application layer)
3. No Port Interface for Repository

### Suggestions

1. Consider Aggregate Root Pattern for ServerState
2. Consider Domain Events for Worktree Registration

</review>

---

<review skill="testing">

## Testing Review

### Critical Issues

#### StateRepositoryTest Uses Filesystem Instead of In-Memory Implementation
**Location:** `.iw/core/test/StateRepositoryTest.scala:11-99`
**Problem:** Tests directly interact with filesystem - these are integration tests, not unit tests.
**Recommendation:** Either rename to integration tests OR create abstraction for file operations.

#### Missing Tests for Validation Edge Cases in WorktreeRegistration
**Location:** `.iw/core/test/WorktreeRegistrationTest.scala:34-44`
**Problem:** Only tests empty issueId validation. Missing tests for empty path, trackerType, team, and whitespace-only strings.
**Recommendation:** Add comprehensive validation tests for all edge cases.

### Warnings

1. WorktreeRegistrationTest bypasses validation by using constructor directly
2. ServerStateTest missing Fixtures trait
3. Missing test for equal timestamps edge case

### Suggestions

1. Consider using SampleData for test fixtures
2. Test names could be more behavior-focused
3. StateRepositoryTest could test multiple worktrees in roundtrip

</review>

---

<review skill="composition">

## Composition Patterns Review

### Critical Issues

#### Manual Dependency Injection in CaskServer
**Location:** `.iw/core/CaskServer.scala:9-10`
**Problem:** Repository manually instantiated inside constructor, creating tight coupling.
**Recommendation:** Accept repository as constructor parameter.

#### Static Service Dependencies Break Composition
**Location:** `.iw/core/CaskServer.scala:14-17`
**Problem:** CaskServer calls static methods on services, creating hidden dependencies.
**Recommendation:** Inject services as dependencies.

### Warnings

1. Service Objects Should Be Traits with Implementations
2. DashboardService Has No Testable Dependencies
3. Command Script Tightly Couples to Infrastructure

### Suggestions

1. Extract HTML Styles to Separate Component
2. Pure Functions in ServerState are well-composed (positive example)

</review>

---

## Summary

- **Critical issues:** 7 (architectural improvements recommended)
- **Warnings:** 11 (should address)
- **Suggestions:** 11 (nice to have)

### By Skill
- scala3: 0 critical, 2 warnings, 3 suggestions
- architecture: 3 critical, 3 warnings, 2 suggestions
- testing: 2 critical, 3 warnings, 3 suggestions
- composition: 2 critical, 3 warnings, 2 suggestions

### Assessment

The critical issues identified are primarily **architectural improvements** rather than functional defects:
- Dependency injection patterns
- Package/directory structure organization
- Test categorization
- Passing current time for deterministic testing

**For Phase 1**, these are acceptable to defer as they represent refactoring opportunities rather than blocking issues. The core functionality works correctly.

**Recommended immediate fixes:**
1. Add missing validation tests for WorktreeRegistration
2. Pass `currentTime: Instant` parameter to `formatRelativeTime` for testability

**Recommended for future phases:**
1. Reorganize into proper directory structure
2. Convert services to traits with DI
3. Create port interfaces for repositories
