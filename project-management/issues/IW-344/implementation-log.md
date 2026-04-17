# Implementation Log: Pre-compile core into jar (IW-344)

Issue: IW-344

This log tracks the evolution of implementation across phases.

---

## Phase 1: Core jar build and mtime check (2026-04-17)

**Layer:** Build infrastructure (bash launcher)

**What was built:**
- `iw-run` — added `CORE_JAR` variable with `IW_CORE_JAR` env override, `build_core_jar()`, `core_jar_stale()`, `ensure_core_jar()` functions; rewrote `bootstrap()` to call `build_core_jar`.

**Dependencies on other layers:**
- None — this phase stands alone. Phase 2 will wire `ensure_core_jar()` into `execute_command()`.

**Testing:**
- No automated tests added (deferred to Phase 3 per phase plan).
- Manual smoke tests all passed: clean bootstrap produces jar, missing/stale jar triggers rebuild, `IW_CORE_JAR` override respected, `./iw version` still works.

**Code review:**
- Iterations: 1
- Review file: `review-phase-01-20260417-142314.md`
- 0 critical issues, 3 warnings (all minor), 6 suggestions (mostly deferred to Phase 3).

**Notable decisions:**
- `ensure_core_jar()` is defined in Phase 1 but unused until Phase 2 — ships together for a coherent diff. Flagged in review as intentional scaffolding.
- `bootstrap()` calls `build_core_jar` directly (unconditional rebuild) rather than `ensure_core_jar` because bootstrap is the explicit pre-build entry point.
- `build/` is already in `.gitignore` — no change needed.

**For next phases:**
- Phase 2 should switch `execute_command()`'s three invocation sites from `$core_files` to `--jar "$CORE_JAR" "$CORE_DIR/project.scala"`, calling `ensure_core_jar` before each.
- Phase 3 should add BATS coverage for missing-jar, stale-jar, `IW_CORE_JAR` override, and `ensure_core_jar` (via its Phase 2 call site); also cover the missing `$CORE_DIR` edge case flagged in testing review.

**Files changed:**
```
M	iw-run
```

---
