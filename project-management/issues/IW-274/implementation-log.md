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

## Phase 3: Builder and Updater — new field support (2026-03-18)

**Layer:** Merge Logic

**What was built:**
- `.iw/core/model/ReviewStateBuilder.scala` — Added `activity` and `workflowType` to `BuildInput`; added JSON writing for both fields in `build()` using snake_case key `workflow_type`
- `.iw/core/model/ReviewStateUpdater.scala` — Added `activity`, `workflowType`, `clearActivity`, `clearWorkflowType` to `UpdateInput`; added merge logic following the existing `status`/`message` clear-wins pattern
- `.iw/core/test/ReviewStateBuilderTest.scala` — 6 new tests for individual field values + combined; updated 3 existing tests (all fields, validator pass, optional omit)
- `.iw/core/test/ReviewStateUpdaterTest.scala` — 9 new tests for set/replace/clear/clear-wins/preservation; updated validator pass test

**Dependencies on other layers:**
- Phase 1: Validator enforces enum constraints on built/merged JSON
- Phase 2: `ReviewState` carries the new fields

**Testing:**
- Unit tests: 15 new tests added (all passing, 1928 total)
- E2E tests: All passing (no regressions)

**Code review:**
- Iterations: 1
- No critical issues; warnings about pre-existing patterns (tuple types, duplicated clear logic) noted for future refactoring

**Files changed:**
```
M	.iw/core/model/ReviewStateBuilder.scala
M	.iw/core/model/ReviewStateUpdater.scala
M	.iw/core/test/ReviewStateBuilderTest.scala
M	.iw/core/test/ReviewStateUpdaterTest.scala
```

---

## Phase 4: CLI Commands — write/update flags (2026-03-18)

**Layer:** CLI/Adapter

**What was built:**
- `.iw/commands/review-state/write.scala` — Added `--activity` and `--workflow-type` flags to the write command, with help text and validation
- `.iw/commands/review-state/update.scala` — Added `--activity`, `--workflow-type`, `--clear-activity`, `--clear-workflow-type` flags to the update command
- `.iw/test/review-state.bats` — 13 new E2E tests covering write/update with new flags, validation, clear operations, preservation

**Dependencies on other layers:**
- Phase 3: `BuildInput` and `UpdateInput` accept the new fields

**Testing:**
- E2E tests: 13 tests added (all passing)

**Code review:**
- Iterations: 1
- No critical issues

**Files changed:**
```
M	.iw/commands/review-state/update.scala
M	.iw/commands/review-state/write.scala
M	.iw/test/review-state.bats
```

---

## Phase 5: WorktreeSummary redesign, worktrees command, and formatter (2026-03-18)

**Layer:** Domain Model + CLI/Adapter + Output

**What was built:**
- `.iw/core/model/WorktreeSummary.scala` — Redesigned with 10 new fields: `issueUrl`, `prUrl`, `activity`, `workflowType`, `workflowDisplay` (renamed from `reviewDisplay`), `currentPhase`, `totalPhases`, `completedTasks`, `totalTasks`, `registeredAt`, `lastActivityAt`
- `.iw/commands/worktrees.scala` — Populates all new fields from `reviewStateCache`, `issueCache`, `prCache`, `progressCache`, and `WorktreeRegistration` timestamps; hoisted repeated cache lookups into local variables
- `.iw/core/output/WorktreesFormatter.scala` — Displays `▶`/`⏸` activity indicators, `AG`/`WF`/`DX` workflow type abbreviations, and `Phase N/M` + `N/M tasks` progress
- `.iw/core/test/WorktreesFormatterTest.scala` — Updated 8 existing tests via `minimalWorktree` helper; added 9 new tests covering activity, workflow type, progress, combined fields, and graceful degradation
- `.iw/core/test/ServerStateCodecTest.scala` — Updated 2 `WorktreeSummary` roundtrip tests for new constructor
- `.iw/test/worktrees.bats` — 4 new E2E tests verifying `workflowDisplay`, `activity`/`workflowType`, progress fields, and URL/timestamp fields in JSON output

**Key decisions:**
- `workflowDisplay` is populated for JSON consumers but not rendered in human-readable formatter (activity + type abbreviation replaces it)
- Timestamps stored as `Option[String]` per analysis design for JSON serialization consistency
- Stringly-typed `activity`/`workflowType` matches `ReviewState` design; enum migration deferred to separate issue

**Dependencies on other layers:**
- Phase 2: `ReviewState` fields `activity` and `workflowType`
- Phase 4: CLI flags for E2E testing

**Testing:**
- Unit tests: 9 new tests added, 8 existing updated (all passing, 1937 total)
- E2E tests: 4 new tests added (all passing)

**Code review:**
- Iterations: 1
- Review file: review-phase-05-20260318-150834.md
- Fixed: Removed implementation source references from comments, fixed BATS test name with temporal framing, fixed fragile `=~` pattern, hoisted repeated cache lookups

**Files changed:**
```
M	.iw/commands/worktrees.scala
M	.iw/core/model/WorktreeSummary.scala
M	.iw/core/output/WorktreesFormatter.scala
M	.iw/core/test/ServerStateCodecTest.scala
M	.iw/core/test/WorktreesFormatterTest.scala
M	.iw/test/worktrees.bats
```

---
