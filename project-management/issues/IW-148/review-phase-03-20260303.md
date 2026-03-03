# Code Review Results

**Review Context:** Phase 3: Infrastructure Layer for issue IW-148 (Iteration 1/3)
**Files Reviewed:** 5
**Skills Applied:** code-review-style, code-review-testing, code-review-scala3, code-review-architecture
**Timestamp:** 2026-03-03
**Git Context:** git diff 84df3eb

---

## Style Review

### Critical Issues
None.

### Warnings
- Duplicate body parsing logic between `registerProject` and `registerWorktree` in CaskServer
- Duplicate error catch blocks in inner try of `registerProject`
- Missing Scaladoc on `registerProject` route
- `withEnv` helper lacks portability caveat in docs

### Suggestions
- Unused `prunedProjectPaths` variable — use `val _ = ...`
- Section header comments reference temporal context (phase number)
- Extract `sendRegistrationRequest` helper in ServerClient

---

## Testing Review

### Critical Issues
- Reflection-based env mutation in `withEnv` is fragile (pre-existing pattern concern)
- CaskServer tests are integration tests without unit-level isolation (pre-existing pattern)

### Warnings
- Duplicate server startup boilerplate in every test
- Missing negative coverage for `ServerClient.registerProject` error paths
- Auto-prune test assertion is overly permissive

### Suggestions
- Race condition in `findAvailablePort()` (pre-existing)

---

## Scala 3 Review

### Critical Issues
None.

### Warnings
- String-typed domain parameters (pre-existing pattern)
- Reflection-based env mutation (same as testing review)

### Suggestions
- Extract `parseRequestBody` helper to deduplicate
- Fully-qualified MainProject reference — add import
- Extract upickle exception matching to named extractor

---

## Architecture Review

### Critical Issues
None.

### Warnings
- `WorktreeRegistrationService.register` drops caches via `ServerState(...)` constructor instead of `state.copy()` — pre-existing bug, not introduced by this diff
- `pruneProjects` doesn't clean associated caches (none exist yet, but asymmetric with `pruneWorktrees`)
- Deprecated companion methods on `ServerStateService`

### Suggestions
- Extract JSON parsing guard as shared helper
- `withEnv` reflection hack — injectable alternative
- Unused `prunedProjectPaths` binding

---

## Summary

- **Critical issues:** 0
- **Warnings:** ~10 (mostly pre-existing patterns, not introduced by this diff)
- **Suggestions:** ~8

The implementation correctly follows established patterns in the codebase. The main architectural concern (duplicate JSON parsing/error handling) is a pre-existing pattern that was replicated consistently. No regressions detected. All 1665 tests pass.
