# Code Review Results

**Review Context:** Phase 3: Presentation Layer — new commands (projects, worktrees, status) with --json for issue IW-222 (Iteration 1/3)
**Files Reviewed:** 9
**Skills Applied:** code-review-style, code-review-testing (partial), code-review-scala3, code-review-architecture
**Timestamp:** 2026-02-25
**Git Context:** `git diff 0da861b`

---

<review skill="code-review-style">

## Critical Issues

None found.

## Warnings

- **Missing documentation on private helpers** (StatusFormatter.scala:24-99): Private section methods lack Scaladoc. While private, the conditional visibility logic is non-trivial.
- **Inconsistent string interpolation** (ProjectsFormatter.scala:16): Uses printf-style `f"..."` while other formatters use simple `s"..."` interpolation. Printf style is justified here for column alignment.

## Suggestions

- Extract magic numbers for padding widths in WorktreesFormatter (40, 42, 12, 15) to named constants
- Extract pluralization helper for "worktree"/"worktrees" pattern
- Standardize test naming patterns across test files
- Consider consistent phrasing for empty collection messages ("registered" vs "found")

</review>

---

<review skill="code-review-testing">

Note: Reviewer failed to load the code-review-testing skill. Manual assessment: All 3 formatter test files exist with comprehensive test cases (6 + 8 + 10 = 24 tests), plus 15 E2E BATS tests. All tests pass.

</review>

---

<review skill="code-review-scala3">

## Critical Issues

None found.

## Warnings

- **Inconsistent padding approach** (WorktreesFormatter.scala:21): Direct `.padTo` usage could use extension methods for consistency across formatters.

## Suggestions

- Use `for`-comprehension instead of tuple pattern matching (StatusFormatter.scala:91-94) for clearer intent
- Consider `enum OutputMode` for flag parsing across commands (eliminates repeated boolean parsing)
- Extract common `StateReader.read()` error handling pattern shared by all 3 commands
- Consider opaque types for `IssueId` (pre-existing design, not introduced in this phase)
- Trait-based formatter design for future composability (premature at this stage)

</review>

---

<review skill="code-review-architecture">

## Critical Issues

None found.

## Warnings

- **Output object performs I/O** (Output.scala): Pre-existing pattern — `Output.error()`/`Output.info()` perform `System.err.println` directly in the `output/` package. Not introduced in this phase.
- **Command scripts contain transformation logic** (projects.scala:27-51, worktrees.scala:38-55, status.scala:77-94): Building domain objects (ProjectSummary, WorktreeSummary, WorktreeStatus) from raw state happens in the command scripts rather than in pure domain functions. This follows the existing command pattern but could be extracted for better testability.

## Suggestions

- Extract issue ID resolution logic to a reusable pure function
- Consider domain service objects for the mapping logic (state → summary value objects) to enable pure unit tests

</review>

---

## Summary

| Severity | Count |
|----------|-------|
| Critical | 0 |
| Warnings | 5 (2 pre-existing patterns, 3 in new code) |
| Suggestions | 12 |

**Verdict:** PASS — No critical issues. Warnings are minor and mostly relate to pre-existing patterns or style preferences. The code follows established project conventions, has comprehensive tests, and correct architectural layering.
