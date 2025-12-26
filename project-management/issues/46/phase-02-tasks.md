# Phase 2 Tasks: Path validation security

**Issue:** #46
**Phase:** 2 of 6
**Status:** Ready to implement
**Estimated Effort:** 2-3 hours

## Setup

- [ ] Review java.nio.file.Path API for normalize() and toRealPath() (5 min)
- [ ] Review existing test patterns in codebase for munit tests (5 min)

## Domain Layer (TDD)

### PathValidator - Basic Path Validation

- [ ] Write test: validateArtifactPath accepts valid relative path within worktree (10 min)
- [ ] Write test: validateArtifactPath rejects empty path (5 min)
- [ ] Write test: validateArtifactPath rejects whitespace-only path (5 min)
- [ ] Write test: validateArtifactPath rejects absolute Unix path (5 min)
- [ ] Write test: validateArtifactPath rejects absolute Windows path (5 min)
- [ ] Implement: Create PathValidator.scala with basic validation (15 min)
- [ ] Run tests: Verify basic validation passes (5 min)

### PathValidator - Directory Traversal Detection

- [ ] Write test: validateArtifactPath rejects simple traversal "../../../etc/passwd" (5 min)
- [ ] Write test: validateArtifactPath rejects embedded traversal "a/../../../etc/passwd" (5 min)
- [ ] Write test: validateArtifactPath accepts path with ".." that stays within boundary (10 min)
- [ ] Write test: validateArtifactPath accepts filename containing ".." like "file..name.md" (5 min)
- [ ] Implement: Add traversal detection using Path.normalize() (15 min)
- [ ] Run tests: Verify traversal detection passes (5 min)

### PathValidator - Boundary Enforcement

- [ ] Write test: isWithinBoundary returns true when path is under base (5 min)
- [ ] Write test: isWithinBoundary returns false when path escapes base (5 min)
- [ ] Write test: isWithinBoundary handles equal paths (same directory) (5 min)
- [ ] Implement: isWithinBoundary using Path.startsWith after normalize (10 min)
- [ ] Run tests: Verify boundary enforcement passes (5 min)

### PathValidator - Symlink Handling

- [ ] Write test: validateArtifactPath rejects symlink pointing outside worktree (10 min)
- [ ] Write test: validateArtifactPath accepts symlink pointing within worktree (10 min)
- [ ] Write test: validateArtifactPath handles broken symlinks gracefully (10 min)
- [ ] Implement: Add optional symlink resolution with I/O injection (20 min)
- [ ] Run tests: Verify symlink handling passes (5 min)

## Error Messages

### Secure Error Generation

- [ ] Write test: error for absolute path says "Artifact path must be relative" (5 min)
- [ ] Write test: error for traversal says "Invalid artifact path" (no path details) (5 min)
- [ ] Write test: error for outside boundary says "Artifact not found" (5 min)
- [ ] Write test: error for empty path says "Invalid artifact path" (5 min)
- [ ] Implement: Ensure error messages don't leak filesystem structure (10 min)
- [ ] Run tests: Verify error messages are secure (5 min)

## Integration

### Full Validation Flow

- [ ] Write test: validateArtifactPath end-to-end with real worktree path (15 min)
- [ ] Write test: validateArtifactPath handles Unicode path components (10 min)
- [ ] Write test: validateArtifactPath handles special characters in filenames (10 min)
- [ ] Run all tests: Full test suite passes (5 min)

## Final Verification

- [ ] Run unit tests for PathValidator (5 min)
- [ ] Review code for security best practices (10 min)
- [ ] Verify no path information leaked in error messages (5 min)
- [ ] Manual test with real paths in worktree (10 min)

---

## Task Summary

**Total Tasks:** ~35 tasks
**Estimated Time:** 2-3 hours
**Test Coverage:** Unit tests for all validation scenarios

**Key Milestones:**
1. Basic validation (empty, absolute paths) - 30 min
2. Traversal detection - 30 min
3. Boundary enforcement - 30 min
4. Symlink handling - 45 min
5. Error message security - 30 min
6. Integration verification - 30 min

**TDD Approach:**
- Each validation rule starts with failing tests
- Tests written before implementation
- Tests run after each implementation step
- Security verified at each milestone

**Security Focus:**
- Error messages must not leak filesystem structure
- All edge cases tested (Unicode, special chars)
- Symlink resolution properly contained
