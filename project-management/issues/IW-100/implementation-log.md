# Implementation Log: Dashboard shows incorrect total phase count

**Issue:** IW-100

This log tracks the evolution of implementation across phases.

---

## Phase 1: Fix totalPhases to use Phase Index as source of truth (2026-01-27)

**What was built:**
- Fixed `WorkflowProgressService.computeProgress` to use Phase Index as source of truth for totalPhases
- Added fallback to file-based count when Phase Index is empty (backward compatibility)

**Decisions made:**
- Use `phaseIndex.size` when available, fall back to `phases.size` otherwise
- Single-line conditional mirrors existing pattern in `determineCurrentPhase`

**Patterns applied:**
- Conditional fallback pattern: `if phaseIndex.nonEmpty then phaseIndex.size else phases.size`
- Same pattern already used in `determineCurrentPhase` for consistency

**Testing:**
- Unit tests: 3 tests added
  - Phase Index provides totalPhases (6-entry index, 3 files → totalPhases = 6)
  - Phase Index matches file count (4-entry index, 4 files → totalPhases = 4)
  - Empty Phase Index falls back (empty index, 3 files → totalPhases = 3)
- All 15 existing tests continue to pass

**Code review:**
- Iterations: 1
- Review file: review-phase-01-20260127-085500.md
- Major findings: No critical issues or warnings. 4 suggestions (stylistic/future improvements)

**For next phases:**
- N/A (single phase issue)

**Files changed:**
```
M	.iw/core/dashboard/WorkflowProgressService.scala
M	.iw/core/test/WorkflowProgressServiceTest.scala
```

---
