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
