# Phase 2 Context: Path validation security

**Issue:** #46
**Phase:** 2 of 6
**Status:** Ready to implement
**Estimated Effort:** 2-3 hours

## Goals

This phase implements **security validation** for artifact paths to prevent directory traversal attacks:

1. **PathValidator module**: Pure functions for validating artifact paths
2. **Canonical path resolution**: Normalize paths and detect traversal attempts
3. **Worktree boundary enforcement**: Ensure paths stay within worktree root
4. **Error messages**: Secure error messages that don't leak filesystem structure

**Success Criteria:** All artifact path validation rejects malicious paths (../, absolute, symlinks outside worktree) while allowing legitimate paths within worktree boundaries.

## Scope

### In Scope
- `PathValidator` module with pure validation functions
- Canonical path resolution using java.nio.file.Path
- Detection of "../" directory traversal attempts
- Rejection of absolute paths
- Rejection of symlinks pointing outside worktree
- Unit tests for all validation scenarios
- Integration with future artifact serving (Phase 3)

### Out of Scope
- HTTP endpoints (Phase 3)
- Actual file serving (Phase 3)
- UI changes (none needed)
- Markdown rendering (Phase 3)

## Dependencies

### From Previous Phases
- Phase 1: ReviewState with artifact paths (provides paths to validate)

### For Next Phases
- Phase 3 (View artifact content): PathValidator required before serving files

## Technical Approach

### Domain Layer

Create `PathValidator` module with pure functions:

```scala
object PathValidator:
  // Validate artifact path is safe to serve
  def validateArtifactPath(
    worktreePath: Path,
    artifactPath: String
  ): Either[String, Path]

  // Check if resolved path is within base path
  def isWithinBoundary(basePath: Path, targetPath: Path): Boolean

  // Normalize path (resolve ../ components)
  def normalizePath(path: String): Either[String, String]
```

### Validation Rules

1. **Reject absolute paths**: Path starting with `/` or drive letter (`C:\`)
2. **Reject empty paths**: Empty string or whitespace-only
3. **Resolve traversal**: Paths with `..` resolved to canonical form
4. **Boundary check**: Resolved path must be within worktree root
5. **Symlink check**: If path contains symlinks, final target must be within boundary

### Error Messages

Secure error messages that don't leak information:
- "Invalid artifact path" (generic, no path details)
- "Artifact path must be relative" (for absolute paths)
- "Artifact not found" (for paths outside boundary)

### Test Strategy

| Test Case | Input | Expected |
|-----------|-------|----------|
| Valid relative path | `project-management/issues/46/analysis.md` | Right(resolved path) |
| Simple traversal | `../../../etc/passwd` | Left("Invalid artifact path") |
| Embedded traversal | `project-management/../../../etc/passwd` | Left("Invalid artifact path") |
| Absolute path Unix | `/etc/passwd` | Left("Artifact path must be relative") |
| Empty path | `` | Left("Invalid artifact path") |
| Path within worktree | `README.md` | Right(resolved path) |
| Double dots in name | `file..name.md` | Right(resolved path) - valid filename |
| Symlink outside | `symlink-to-etc-passwd` | Left("Artifact not found") |

### I/O Pattern

PathValidator should be pure where possible:
- Path string manipulation: Pure
- Boundary checking with Path.normalize: Pure
- Symlink resolution: Requires I/O (inject function or use at edge)

For symlink checking, inject the I/O:
```scala
def validateArtifactPath(
  worktreePath: Path,
  artifactPath: String,
  resolveSymlinks: Path => Either[String, Path] = defaultResolver
): Either[String, Path]
```

### Files to Create/Modify

**New Files:**
- `.iw/core/PathValidator.scala` - Pure validation functions
- `.iw/core/test/PathValidatorTest.scala` - Comprehensive unit tests

**Modified Files:**
- None in this phase (integration in Phase 3)

## Acceptance Criteria

- [ ] Relative paths within worktree are accepted
- [ ] Absolute paths are rejected with clear message
- [ ] Paths with `..` components that escape boundary are rejected
- [ ] Paths with `..` that stay within boundary are accepted
- [ ] Symlinks pointing outside worktree are rejected
- [ ] Error messages don't leak filesystem structure
- [ ] All validation is covered by unit tests
- [ ] PathValidator follows pure functional design

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Platform differences (Windows vs Unix paths) | Use java.nio.file.Path which handles both |
| Symlink edge cases | Test with actual symlinks in test fixtures |
| Unicode path attacks | Use Path.normalize() which handles Unicode |
| Race conditions (TOCTOU) | Document that validation is point-in-time; re-validate at use |

## Notes for Next Phases

- Phase 3 will import PathValidator for artifact serving endpoint
- PathValidator.validateArtifactPath will be called before any file read
- Consider caching validation results if performance becomes an issue
