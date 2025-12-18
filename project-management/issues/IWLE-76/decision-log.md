# Decision Log for IWLE-76

## Session 1 - 2025-12-18

### Issues Addressed
- [C1] Use enum instead of String for issue type field
- [C2] Replace Either[String, ...] with typed error ADT

### Discussion Summary
Discussed type safety improvements for the feedback command. Two approaches were considered for error handling: (A) keep `Either[String, Result]` consistent with existing codebase, or (B) introduce a typed `FeedbackError` ADT. Michal chose Option A for simplicity and consistency. For issue types, using an enum is a clear win with no downside.

### Decisions Made

1. **[C1]:** Use `enum IssueType { case Bug, Feature }` with a `fromString` parser method
   - Rationale: Type-safe representation of issue types prevents invalid states at compile time. Clear win with no complexity cost.

2. **[C2]:** Keep `Either[String, Result]` pattern - do not introduce typed error ADT
   - Rationale: Consistency with existing codebase (e.g., `iw issue` command uses same pattern). Command is simple enough that error classification for retry logic isn't needed. Keeps implementation simple.

### Tasks Updated
- Phase 2, Task 1: Added `enum IssueType` definition step, updated FeedbackRequest to use `IssueType` instead of String

---

## Session 2 - 2025-12-18

### Issues Addressed
- [W1]-[W5] All warnings
- [S1]-[S5] All suggestions

### Discussion Summary
Batch resolution of remaining issues. Given the decision to keep implementation simple and consistent with existing codebase, warnings were accepted as guidance to follow during implementation (not requiring plan changes). Suggestions were dismissed as implementation details or YAGNI.

### Decisions Made

1. **[W1]-[W4]:** Accepted as implementation guidance - wrap HTTP in try-catch, handle missing env var gracefully, test edge cases, use fail-fast validation
   - Rationale: Good practices to follow during implementation without requiring plan updates

2. **[W5]:** Dismissed - TeamId type safety unnecessary
   - Rationale: It's a hardcoded constant; adding opaque type is over-engineering

3. **[S1]-[S5]:** Dismissed as implementation details or YAGNI
   - Rationale: Keep it simple, decide these during implementation

### Tasks Updated
- No additional task updates required

---
