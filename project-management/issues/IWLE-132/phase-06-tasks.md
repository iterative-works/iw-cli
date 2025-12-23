# Phase 6 Tasks: Doctor validates GitHub setup

**Issue:** IWLE-132
**Phase:** 6 - Doctor validates GitHub setup
**Status:** Complete

---

## Task List

### Setup

- [x] [impl] [x] [reviewed] Review doctor hook pattern from existing hooks

### Unit Tests - GitHub Doctor Hook

- [x] [test] [x] [reviewed] Test: checkGhInstalled skips for non-GitHub tracker
- [x] [test] [x] [reviewed] Test: checkGhInstalled succeeds when gh CLI available
- [x] [test] [x] [reviewed] Test: checkGhInstalled fails when gh CLI not available
- [x] [test] [x] [reviewed] Test: checkGhAuthenticated skips for non-GitHub tracker
- [x] [test] [x] [reviewed] Test: checkGhAuthenticated skips when gh not installed
- [x] [test] [x] [reviewed] Test: checkGhAuthenticated succeeds when authenticated
- [x] [test] [x] [reviewed] Test: checkGhAuthenticated fails when not authenticated

### Implementation - GitHub Doctor Hook

- [x] [impl] [x] [reviewed] Create github.hook-doctor.scala with checkGhInstalled function
- [x] [impl] [x] [reviewed] Implement checkGhAuthenticated using GitHubClient.validateGhPrerequisites
- [x] [impl] [x] [reviewed] Expose both checks as immutable values for hook discovery

### E2E Tests

- [x] [test] [x] [reviewed] E2E: doctor shows gh CLI check passed for GitHub project (when gh installed)
- [x] [test] [x] [reviewed] E2E: doctor skips gh checks for non-GitHub project (Linear)
- [x] [test] [x] [reviewed] E2E: doctor shows gh auth check when gh is installed

### Refactoring

- [x] [impl] [x] [reviewed] Refactoring R1: Fix FCIS Architecture Violations

---

## Progress Tracking

- **Tests written:** 10/10
- **Tests passing:** 10/10
- **Implementation tasks:** 4/4

## Notes

- Follow TDD: write failing test → implement → verify pass
- Reuse `GitHubClient.validateGhPrerequisites()` from Phase 4
- Match existing hook pattern from `issue.hook-doctor.scala`
