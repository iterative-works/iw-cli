# Implementation Log: IW-136

## 2026-01-28: Scope Refinement Session

**Participants:** Michal, Claude

### Context

Initial analysis generated 7 user stories covering full feature scope including dashboard changes, skill creation, and version migration. After discussion, scope was refined to focus on core contract and tooling.

### Decisions Made

#### 1. Scope Limited to Schema + Commands Only

**Decision:** Limit IW-136 scope to:
- JSON Schema definition
- Validation command (`iw validate-review-state`)
- Write command (`iw write-review-state`)

**Excluded:**
- Dashboard validation UI changes
- Dashboard action button rendering
- Skill documentation

**Rationale:**
- Dashboard changes are separate concern with separate effort
- Testing dashboard isn't needed to validate schema correctness
- Keeps issue focused and deliverable

#### 2. Skill Belongs in Workflows Repository

**Decision:** The skill teaching agents how to use the review-state commands should be created in the workflows (kanon) repository, not in iw-cli.

**Rationale:**
- Skills teach workflow agents how to use tools
- Creating skills in iw-cli would create undue dependency from workflows to iw-cli's skill definition space
- Workflows should own their agent instruction space
- Clean separation: iw-cli owns contract + tools, workflows own agent skills

**Action:** Workflows team will:
1. Reference the schema from iw-cli
2. Create their own skill for their agents
3. Use the skill in other workflow skills as needed

#### 3. available_actions Included in Schema

**Decision:** Include `available_actions` as an optional field in the schema.

**Rationale:**
- Schema should be complete - workflows may want to write actions
- We're formalizing the current implicit schema
- Testing schema correctness doesn't require testing dashboard rendering
- Dashboard UI for actions is separate issue

#### 4. Manual Validation with upickle

**Decision:** Use manual validation with upickle rather than an external JSON Schema validation library.

**Rationale:**
- Already using upickle in codebase
- Schema is relatively simple
- Full control over error messages
- No new dependencies
- Can add formal library later if needed

#### 5. Command Naming: Full Names

**Decision:** Use `validate-review-state` and `write-review-state` (full names).

**Rationale:**
- Self-documenting
- "state" alone is too vague
- Consistent with "review-state.json" filename

#### 6. Schema Versioning: Integer, Breaking Changes Only

**Decision:** Use integer version field, bump only for breaking changes.

**Rationale:**
- Simple and practical
- Adding optional fields doesn't require version bump
- Start at version 1
- Dashboard supports current version only (for now)

#### 7. Write Semantics: Always Replace

**Decision:** `write-review-state` always replaces the file (no merge mode).

**Rationale:**
- Simpler implementation and mental model
- Workflows typically generate full state at once
- Merge semantics for arrays are ambiguous
- Can add --merge later if needed

### Revised Estimates

**Before:** 39-56 hours (7 stories)
**After:** 18-24 hours (3 stories)

### Next Steps

1. ~~Resolve remaining CLARIFY markers~~ (resolved below)
2. Generate phase-based tasks
3. Begin implementation

---

## 2026-01-28: Status Enum and Required Fields Resolution

**Participants:** Michal, Claude

### Context

Searched kanon workflows repository (`~/ops/kanon`) to enumerate all status values currently in use. Also finalized required vs optional fields.

### Decisions Made

#### 8. Status Field: Open Enum Approach

**Decision:** Use open enum - document known values but allow custom statuses without breaking validation.

**Known status values discovered:**
- `analysis_ready` - Analysis document created
- `context_ready` - Phase context file created
- `tasks_ready` - Phase tasks file created
- `implementing` - Implementation in progress
- `awaiting_review` - Phase complete, waiting for human review
- `review_failed` - Code review failed after max iterations
- `phase_merged` - Phase PR merged (batch mode)
- `refactoring_complete` - Mid-phase refactoring done
- `all_complete` - All phases finished

**Rationale:**
- Dashboard already handles unknown statuses gracefully (converts to title case, default styling)
- New statuses can be added without schema/validator changes
- Known statuses get special behavior, unknown ones work neutrally
- Validation emits warning (not error) for unknown status

**Source:** `~/ops/kanon/skills/ag-implementation-workflow/SKILL.md` and `~/ops/kanon/commands/ag-*.md`

#### 9. Required Fields Finalized

**Decision:** Required fields are: `version`, `issue_id`, `status`, `artifacts`, `last_updated`

**Changes from initial proposal:**
- Added `last_updated` as required (for auditability and cache invalidation)
- Confirmed `artifacts` can be empty array `[]` (required but may have no items)

**Rationale:**
- `last_updated` enables dashboard cache management and audit trails
- Empty artifacts array is explicit "no artifacts" vs missing field

### All CLARIFY Markers Resolved

Analysis is now **Ready for Implementation** with no remaining open questions.

### Next Steps

1. Generate phase-based tasks: `/iterative-works:ag-create-tasks IW-136`
2. Begin implementation: `/iterative-works:ag-implement IW-136`

---

## Phase 1: JSON Schema formally defines contract (2026-01-28)

**What was built:**
- Schema: `schemas/review-state.schema.json` - JSON Schema Draft-07 defining the review-state.json contract
- Documentation: `schemas/README.md` - Versioning policy and field summary
- Test fixtures: `.iw/core/test/resources/review-state/` - 4 fixture files (2 valid, 2 invalid)
- E2E test: `.iw/test/schema.bats` - 6 BATS tests for schema existence and correctness

**Decisions made:**
- Used `additionalProperties: false` at all levels for strict validation
- `phase` field uses `oneOf` to accept both integer and string types
- `pr_url` uses `oneOf` for string/null support
- Status is documented as open enum (any string accepted, known values listed in description)
- Schema version starts at 1 (the `version` field in the schema tracks breaking changes)

**Patterns applied:**
- JSON Schema Draft-07 with `$ref` definitions for nested objects (artifact, action, phase_checkpoint)
- Test fixtures designed to exercise both valid and invalid cases for use by Phase 2 validation

**Testing:**
- E2E tests: 6 BATS tests (schema existence, valid JSON, Draft-07, required fields, all properties, fixtures)
- Compatibility: Verified all existing v2 review-state.json files have required fields

**Code review:**
- Iterations: 1
- No critical issues found
- Schema passes meta-validation and compatibility checks

**For next phases:**
- Schema at `schemas/review-state.schema.json` defines the contract Phase 2 validation must enforce
- Test fixtures in `.iw/core/test/resources/review-state/` available for Phase 2 unit tests
- Required fields: version, issue_id, status, artifacts, last_updated
- Open enum for status means validation should warn (not error) on unknown values

**Files created:**
```
A schemas/review-state.schema.json
A schemas/README.md
A .iw/core/test/resources/review-state/valid-minimal.json
A .iw/core/test/resources/review-state/valid-full.json
A .iw/core/test/resources/review-state/invalid-missing-required.json
A .iw/core/test/resources/review-state/invalid-wrong-types.json
A .iw/test/schema.bats
```

---

## Phase 2: Validation command for review state (2026-01-28)

**What was built:**
- Domain: `ValidationError` and `ValidationResult` case classes in `.iw/core/model/`
- Application: `ReviewStateValidator.validate()` in `.iw/core/model/ReviewStateValidator.scala` - pure validation logic
- Command: `.iw/commands/validate-review-state.scala` - CLI with file and stdin support
- Unit tests: 35 tests in `.iw/core/test/ReviewStateValidatorTest.scala`
- E2E tests: 10 tests in `.iw/test/validate-review-state.bats`

**Decisions made:**
- Used mutable ListBuffer internally within validate() for accumulating errors/warnings (local scope only, function returns immutable result)
- Status validation uses warning (not error) for unknown values, matching the open enum decision
- additionalProperties: false enforced at root and all nested object levels
- Command imports from `iw.core.model.*` and `iw.core.output.*` only (not dashboard)

**Patterns applied:**
- FCIS: Pure validation logic in model/ (no I/O), command is imperative shell
- upickle ujson API for JSON parsing without schema library dependency
- Error accumulation pattern (collect all errors, don't fail on first)

**Testing:**
- Unit tests: 35 tests covering valid inputs, parse errors, missing fields, wrong types, status warnings, unknown properties, nested structure validation
- E2E tests: 10 BATS tests covering file mode, stdin mode, error formatting, exit codes

**Code review:**
- Iterations: 1
- No critical issues found

**For next phases:**
- `ReviewStateValidator.validate()` available for Phase 3 write command (validate before writing)
- `ValidationResult.isValid` for quick pass/fail check
- `ValidationError(field, message)` provides structured error reporting

**Files created:**
```
A .iw/core/model/ValidationError.scala
A .iw/core/model/ValidationResult.scala
A .iw/core/model/ReviewStateValidator.scala
A .iw/commands/validate-review-state.scala
A .iw/core/test/ReviewStateValidatorTest.scala
A .iw/test/validate-review-state.bats
```

---
