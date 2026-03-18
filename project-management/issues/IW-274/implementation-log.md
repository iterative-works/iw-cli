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

## Phase 2: Domain Model — ReviewState fields and codec (2026-03-18)

**Layer:** Domain Model

**What was built:**
- `.iw/core/model/ReviewState.scala` — Added `activity: Option[String] = None` and `workflowType: Option[String] = None` fields to `ReviewState` case class with ScalaDoc
- `.iw/core/dashboard/ReviewStateService.scala` — Updated custom `bimap` codec to read/write `activity` and `workflow_type` (snake_case) from review-state.json; added `writeReviewStateJson` method; extracted shared codec definitions into private `Codecs` object to eliminate duplication
- `.iw/core/test/ServerStateCodecTest.scala` — 2 new tests for `macroRW` roundtrip (fields populated and absent)
- `.iw/core/test/ReviewStateServiceTest.scala` — 9 new tests covering parse/write for both fields (presence, absence, non-string graceful handling, JSON shape verification)

**Key decisions:**
- Used default values (`= None`) on new fields so existing construction sites compile without changes
- `ServerStateCodec.macroRW[ReviewState]` needed no changes — macro derivation picks up new fields automatically
- Kept `Option[String]` (not enums) per analysis design; enums noted for future consideration

**Dependencies on other layers:**
- Phase 1: Schema and validator define the enum constraints

**Testing:**
- Unit tests: 11 new tests added (all passing, 1913 total)
- E2E tests: All passing (no regressions)

**Code review:**
- Iterations: 1 (with fixes)
- Findings fixed: Extracted duplicated codec `given` definitions into shared `Codecs` object; renamed misleading test names; removed temporal "backward compat" framing; removed unused import; added JSON shape assertions to write tests

**Files changed:**
```
M	.iw/core/dashboard/ReviewStateService.scala
M	.iw/core/model/ReviewState.scala
M	.iw/core/test/ReviewStateServiceTest.scala
M	.iw/core/test/ServerStateCodecTest.scala
```

---
