# Implementation Log: Dashboard: Display workflow review artifacts from state file

**Issue:** #46

This log tracks the evolution of implementation across phases.

---

## Phase 1: Display artifacts from state file (2025-12-26)

**What was built:**
- Domain: `ReviewState.scala` - Domain models (ReviewState, ReviewArtifact)
- Domain: `CachedReviewState.scala` - Cache wrapper with mtime validation
- Service: `ReviewStateService.scala` - Application service with I/O injection pattern
- Integration: Extended `ServerState` with reviewStateCache field
- Integration: Extended `StateRepository` with JSON serialization for review types
- Integration: Extended `DashboardService` with fetchReviewStateForWorktree
- UI: Extended `WorktreeListView` with review artifacts section rendering
- Route: Updated `CaskServer` to pass reviewStateCache to dashboard

**Decisions made:**
- I/O Injection Pattern: File reading is injected as function parameters for testability (FCIS)
- Lenient JSON Parsing: Only `artifacts` is required; status, phase, message are optional
- Cache Strategy: mtime-based validation (same pattern as CachedProgress)
- Graceful Degradation: Missing/invalid files return None/Left without breaking dashboard

**Patterns applied:**
- Functional Core, Imperative Shell (FCIS): Pure parsing/validation logic with I/O at edges
- mtime-based Cache Validation: Follows existing CachedProgress/CachedIssue patterns
- Option-based Conditional Rendering: Review section only shown when state exists with artifacts

**Testing:**
- Unit tests: 27 tests added
  - ReviewStateTest (5 tests) - Domain model construction
  - CachedReviewStateTest (6 tests) - Cache validation logic
  - ReviewStateServiceTest (12 tests) - JSON parsing and service integration
  - ServerStateTest (2 tests) - reviewStateCache field
  - StateRepositoryTest (4 tests) - JSON serialization
- Integration tests: 5 tests added
  - DashboardServiceTest (5 tests) - Dashboard rendering with review state
- View tests: 6 tests added
  - WorktreeListViewTest (6 tests) - Review artifacts rendering

**Code review:**
- Iterations: 1
- Major findings: Missing tests for DashboardService and WorktreeListView (fixed)

**For next phases:**
- Available utilities: ReviewStateService for parsing review-state.json
- Extension points: ReviewState model supports status/phase/message for Phase 3
- Notes: Path validation needed before Phase 2 (artifact viewing)

**Files changed:**
```
A  .iw/core/ReviewState.scala
A  .iw/core/CachedReviewState.scala
A  .iw/core/ReviewStateService.scala
A  .iw/core/test/ReviewStateTest.scala
A  .iw/core/test/CachedReviewStateTest.scala
A  .iw/core/test/ReviewStateServiceTest.scala
A  .iw/core/test/DashboardServiceTest.scala
A  .iw/core/test/WorktreeListViewTest.scala
M  .iw/core/ServerState.scala
M  .iw/core/StateRepository.scala
M  .iw/core/DashboardService.scala
M  .iw/core/WorktreeListView.scala
M  .iw/core/CaskServer.scala
M  .iw/core/test/ServerStateTest.scala
M  .iw/core/test/StateRepositoryTest.scala
```

---

## Phase 2: Path validation security (2025-12-26)

**What was built:**
- Security: `PathValidator.scala` - Pure validation functions for artifact paths
- Security: `validateArtifactPath()` - Main entry point with I/O injection for symlink resolution
- Security: `isWithinBoundary()` - Boundary enforcement using Path.startsWith after normalize
- Security: `isAbsolute()` - Cross-platform absolute path detection (Unix + Windows)
- Security: `defaultSymlinkResolver()` - Symlink resolution with toRealPath()

**Decisions made:**
- I/O Injection Pattern: Symlink resolution injected as function parameter for testability (FCIS)
- Secure Error Messages: Generic messages ("Artifact not found") that don't leak filesystem structure
- Double Boundary Check: Check before and after symlink resolution to prevent symlink escapes
- Cross-Platform: Support both Unix (/) and Windows (C:\) absolute path detection

**Patterns applied:**
- Functional Core, Imperative Shell (FCIS): Pure path validation with I/O only in symlink resolution
- Dependency Injection: `resolveSymlinks` parameter allows pure unit tests with mock resolvers
- Defense in Depth: Multiple validation layers (empty, absolute, traversal, boundary, symlink)

**Testing:**
- Unit tests: 18 tests added
  - PathValidatorTest - Basic validation (5 tests): empty, whitespace, absolute paths
  - PathValidatorTest - Traversal detection (4 tests): simple, embedded, safe .., filename with ..
  - PathValidatorTest - Boundary enforcement (3 tests): under base, escapes, equal paths
  - PathValidatorTest - Symlink handling (3 tests): outside, inside, broken
  - PathValidatorTest - Edge cases (3 tests): e2e, Unicode, special chars

**Code review:**
- Iterations: 1
- Skills applied: scala3, style, testing, security
- Critical issues: 0
- Warnings: 5 (considered but not blocking)
- Suggestions: 10+ (documented for future consideration)
- Major findings: TOCTOU documentation suggested, Windows UNC paths noted

**For next phases:**
- Available utilities: PathValidator.validateArtifactPath for Phase 3 artifact serving
- Extension points: Can add caching if performance becomes an issue
- Notes: Call validateArtifactPath before any file read; use returned path directly

**Files changed:**
```
A  .iw/core/PathValidator.scala
A  .iw/core/test/PathValidatorTest.scala
```

---
