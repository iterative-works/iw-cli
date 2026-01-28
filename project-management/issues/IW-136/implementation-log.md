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

### Open Questions Remaining

1. **Status enum values** - Need to enumerate all valid statuses from current usage
2. **Required vs optional fields** - Proposed: version, issue_id, status, artifacts required; rest optional

### Next Steps

1. Resolve remaining CLARIFY markers
2. Generate phase-based tasks
3. Begin implementation

---
