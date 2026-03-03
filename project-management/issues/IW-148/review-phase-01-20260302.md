# Code Review Results

**Review Context:** Phase 1: Domain Layer for issue IW-148 (Iteration 1/3)
**Files Reviewed:** 9
**Skills Applied:** code-review-style, code-review-testing, code-review-scala3, code-review-architecture
**Timestamp:** 2026-03-02
**Git Context:** git diff fc7cc75

---

<review skill="code-review-style">

## Critical Issues

None.

## Warnings

1. **Fully-qualified type references in `ServerStateCodecTest`** — `iw.core.model.ProjectRegistration` is used throughout new codec tests despite `import iw.core.model.*` already in scope. All other types use the short name. Use `ProjectRegistration` to match existing style.

2. **Fragmented import in `ServerStateTest`** — `ProjectRegistration` was added to the brace-grouped import on line 6, while other model types use individual import lines (lines 8-17). Minor inconsistency.

## Suggestions

1. Consider adding `ServerState.empty` factory to avoid enumerating six `Map.empty` positional arguments across multiple call sites.
2. `SampleData.sampleProjects` was added but not used by any test in this phase.

</review>

---

<review skill="code-review-testing">

## Critical Issues

None.

## Warnings

1. **Missing whitespace-only tests for other fields** — Whitespace-only input is tested for `path` but not for `projectName`, `trackerType`, or `team`. Since all use the same `.trim.isEmpty` pattern, at least one additional whitespace test would improve coverage.

2. **Duplicate backward-compatibility test** — Both `"backward compatibility - JSON missing optional cache fields parses successfully"` and `"backward compatibility - JSON without projects key parses with empty projects map"` parse identical JSON `{"worktrees":{}}`. The first already implicitly covers `projects`. Consider merging.

3. **Redundant field assertions in `create()` happy-path test** — The happy-path `create()` test re-asserts all six fields already covered by the direct construction test. Could simplify to `assert(result.isRight)` plus equality check against a directly constructed instance.

## Suggestions

1. Add `test("ServerState.listProjects with empty projects returns empty list")` for symmetry with `listByIssueId` empty-state test.
2. Consider using `SampleData.sampleProjects` in at least one test to validate the fixtures.

</review>

---

<review skill="code-review-scala3">

## Critical Issues

None that should block merge. One design observation:

**`trackerType` uses `String` instead of `IssueTrackerType` enum** — The codebase has `enum IssueTrackerType` but `ProjectRegistration` (and `WorktreeRegistration`) store tracker type as a raw `String`. This is intentional per the analysis, which says to "Follow existing `WorktreeRegistration` pattern exactly." Changing both registration types to use the enum would be a separate refactoring.

## Warnings

1. **Positional `Map.empty` constructor calls** — `ServerState(Map.empty, Map.empty, Map.empty, Map.empty, Map.empty, Map.empty)` is fragile. Named parameters (`ServerState(worktrees = Map.empty)`) would leverage defaults and reduce churn. Pre-existing pattern, not introduced by this diff.

2. **`StateJson` duplicates `ServerState` field structure** — Each field addition requires updating both types plus read/write calls. Pre-existing architecture, not introduced by this diff.

## Suggestions

1. Extract shared validation helper for the `trim.isEmpty` pattern used in both `WorktreeRegistration.create()` and `ProjectRegistration.create()`.
2. Group `given` declarations in codec by semantic domain for readability.

</review>

---

<review skill="code-review-architecture">

## Critical Issues

None.

## Warnings

1. **`@deprecated` methods in `ServerStateService` companion** — Pre-existing methods with "Legacy compatibility" comments violate the project's temporal-language rule. Not introduced by this diff; flag for follow-up.

## Suggestions

1. Consider conversion helpers between `ServerState` and `StateJson` to reduce mechanical field-threading.
2. Consider opaque types for domain strings (`Path`, `TrackerType`, `Team`) for future type safety.

</review>

---

## Summary

| Severity | Count |
|----------|-------|
| Critical | 0 |
| Warnings | 7 (3 pre-existing/out-of-scope, 4 actionable) |
| Suggestions | 8 |

**Actionable items for this phase:**
- Fix fully-qualified `iw.core.model.ProjectRegistration` references in codec tests (style)
- Consider merging duplicate backward-compat tests (testing)
- Consider adding one whitespace-only test for `projectName` (testing)

**Out-of-scope items noted for future:**
- `ServerState.empty` factory
- Opaque types for domain strings
- `StateJson`/`ServerState` conversion helpers
- Remove deprecated methods in `ServerStateService`

**Verdict:** No critical issues. Code follows established patterns correctly. Minor style fixes recommended.
