# Implementation Log: Add activity and workflow_type to review-state schema and redesign WorktreeSummary

Issue: IW-274

This log tracks the evolution of implementation across phases.

---

## Phase 1: Schema and Validator (2026-03-17)

**Layer:** Schema + Validation

**What was built:**
- `schemas/review-state.schema.json` — Added `activity` (enum: working/waiting) and `workflow_type` (enum: agile/waterfall/diagnostic) optional properties; updated `status` description with canonical vocabulary
- `.iw/core/model/ReviewStateValidator.scala` — Added both fields to `AllowedRootProperties`, added `ValidActivityValues` and `ValidWorkflowTypes` enum constants, added validation blocks in `validateOptionalFieldTypes`
- `.iw/core/test/ReviewStateValidatorTest.scala` — 10 new tests covering valid values, invalid enums, and wrong types for both fields; updated "valid full JSON" test

**Dependencies on other layers:**
- None (first phase, schema is the foundation)

**Testing:**
- Unit tests: 10 tests added (all passing)
- E2E tests: All passing (no regressions)

**Code review:**
- Iterations: 1
- Review file: review-phase-01-20260317-164333.md
- Fixed: Non-deterministic Set ordering in error messages (`.toSeq.sorted`)

**Files changed:**
```
M	.iw/core/model/ReviewStateValidator.scala
M	.iw/core/test/ReviewStateValidatorTest.scala
M	schemas/review-state.schema.json
```

---
