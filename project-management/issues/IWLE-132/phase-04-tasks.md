# Phase 4 Tasks: Handle gh CLI prerequisites

**Issue:** IWLE-132
**Phase:** 4 of 6
**Status:** Complete

---

## Setup

- [x] [setup] Read existing GitHubClient.scala to understand current patterns
- [x] [setup] Read existing CommandRunner.scala to understand isCommandAvailable

## Tests First (TDD)

### Unit Tests - Prerequisite Validation

- [x] [test] Test: validateGhPrerequisites returns GhNotInstalled when gh not found
- [x] [test] Test: validateGhPrerequisites returns GhNotAuthenticated when auth status fails
- [x] [test] Test: validateGhPrerequisites returns Right(()) when gh authenticated
- [x] [test] Test: createIssue fails with installation message when gh not installed
- [x] [test] Test: createIssue fails with auth message when gh not authenticated

### Unit Tests - Error Message Formatting

- [x] [test] Test: formatGhNotInstalledError contains installation URL
- [x] [test] Test: formatGhNotInstalledError contains gh auth login instruction
- [x] [test] Test: formatGhNotAuthenticatedError contains gh auth login instruction

## Implementation

### Core Validation Logic

- [x] [impl] Add GhPrerequisiteError sealed trait with GhNotInstalled, GhNotAuthenticated, GhOtherError cases
- [x] [impl] Implement validateGhPrerequisites method with command availability check
- [x] [impl] Add isAuthenticationError helper to detect exit code 4
- [x] [impl] Implement formatGhNotInstalledError with multi-line help message
- [x] [impl] Implement formatGhNotAuthenticatedError with auth instructions
- [x] [impl] Update createIssue to call validateGhPrerequisites before proceeding

### Verify Unit Tests Pass

- [x] [verify] Run unit tests and verify all pass

## E2E Tests

- [x] [test] E2E test: feedback fails with helpful message when gh CLI not installed
- [x] [test] E2E test: feedback fails with auth instructions when gh not authenticated
- [x] [test] E2E test: feedback fails with permission message when repository not accessible

### Verify All Tests Pass

- [x] [verify] Run all tests (unit + E2E) and verify no regressions

## Refinement

- [x] [refine] Review error messages are clear and actionable
- [x] [refine] Verify exit codes are non-zero on all errors
- [x] [refine] Update implementation-log.md with phase summary

---

## Task Count

- Setup: 2
- Tests (unit): 8
- Implementation: 6
- Verification: 2
- E2E tests: 3
- Refinement: 3

**Total: 24 tasks**
