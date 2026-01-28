# Story-Driven Analysis: Define review-state.json schema and provide workflow integration skills

**Issue:** IW-136
**Created:** 2026-01-28
**Status:** Ready for Implementation
**Classification:** Feature

## Problem Statement

Currently, the `review-state.json` file schema is implicitly defined by what workflow tools (kanon) produce. This creates a fragile contract where:
- Workflows must guess at the correct structure
- iw-cli dashboard must adapt to whatever workflows produce
- Schema changes have no migration path
- Validation happens only when dashboard fails to parse the file

**User Need:** Workflow authors need clear, authoritative guidance on the review-state.json format so they can reliably communicate issue status to the iw-cli dashboard.

**Value:** By making iw-cli the schema owner, we establish clear ownership boundaries:
- **iw-cli**: Defines and owns the schema, provides validation/write commands
- **Workflows (kanon)**: Consume schema, create skills for their agents, call iw commands

This enables:
- Workflow authors to write valid state without studying iw-cli internals
- Dashboard to validate incoming state and provide clear error messages
- Schema evolution with explicit versioning and migration paths
- Confidence that state files are correct before workflows complete

## Scope

### In Scope (This Issue)

1. **JSON Schema definition** - Formal schema at `schemas/review-state.schema.json`
2. **Validation command** - `iw validate-review-state <path>`
3. **Write command** - `iw write-review-state --status ... --artifact ...`
4. **Schema documentation** - `schemas/README.md` with versioning policy

### Out of Scope (Deferred)

- **Dashboard validation UI** - Separate issue for dashboard changes
- **Dashboard action buttons** - Separate issue for dashboard changes
- **Skill documentation** - Belongs in workflows (kanon) repository, not iw-cli

### Ownership Boundaries

| Concern | Owner | Rationale |
|---------|-------|-----------|
| Schema definition | iw-cli | Contract owner - defines what's valid |
| Validation command | iw-cli | Tool provider - enforces the contract |
| Write command | iw-cli | Tool provider - produces valid output |
| Skill for agents | workflows (kanon) | Consumer - teaches agents how to use tools |
| Dashboard UI | iw-cli (separate issue) | Visual consumer of schema |

The skill belongs in the workflows repository because:
- Skills teach workflow agents how to use tools
- Creating skills in iw-cli would create dependency from workflows to iw-cli skill definitions
- Workflows should own their agent instruction space
- Workflows will reference the schema from iw-cli and build their own skill

---

## User Stories

### Story 1: JSON Schema formally defines contract

```gherkin
Feature: Formal JSON Schema for review state
  As a workflow author
  I want to read the formal schema definition
  So that I understand all fields, types, and constraints

Scenario: Schema defines all current fields
  Given I open "schemas/review-state.schema.json"
  Then I see JSON Schema with $schema property set to draft-07
  And I see all required fields: version, issue_id, status, artifacts
  And I see all optional fields: phase, step, branch, pr_url, git_sha, last_updated, message, batch_mode, phase_checkpoints, available_actions
  And status field has enum with valid values
  And version field is integer with minimum 1
  And available_actions is array of action objects with id, label, skill fields

Scenario: Schema provides field descriptions
  Given I read the schema
  Then each field has a "description" property
  And the description explains what the field is for
  And examples are provided for complex fields like artifacts and available_actions

Scenario: Schema documents versioning policy
  Given I read "schemas/README.md"
  Then I see section explaining version field semantics
  And I see that version bumps only for breaking changes
  And I see current schema version is 1
```

**Estimated Effort:** 4-6h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward because:
- JSON Schema is well-established format
- Schema structure clear from existing review-state.json examples
- No code generation needed (manual schema definition)

**Key Components:**
- `schemas/review-state.schema.json` - The formal schema file
- `schemas/README.md` - Versioning policy documentation

**Acceptance:**
- Schema file exists at schemas/review-state.schema.json
- All fields from current implicit schema are defined
- `available_actions` field included as optional array
- Field types and constraints are specified
- Descriptions explain purpose of each field
- Schema passes meta-validation (is itself valid JSON Schema)

---

### Story 2: Workflow validates state before writing

```gherkin
Feature: Validate review state against JSON schema
  As a workflow author
  I want to validate my review-state.json before writing it
  So that I catch errors early and don't break the dashboard

Scenario: Valid state passes validation
  Given I have a review-state.json file with valid schema
  When I run "iw validate-review-state project-management/issues/IW-42/review-state.json"
  Then I see "Validation successful: review-state.json is valid"
  And the command exits with status 0

Scenario: Invalid state shows clear error
  Given I have a review-state.json with missing required field "issue_id"
  When I run "iw validate-review-state project-management/issues/IW-42/review-state.json"
  Then I see error message "Validation failed: missing required field 'issue_id'"
  And the command exits with status 1

Scenario: Invalid status enum shows valid options
  Given I have a review-state.json with status "invalid_status"
  When I run "iw validate-review-state project-management/issues/IW-42/review-state.json"
  Then I see error message listing valid status values
  And the command exits with status 1

Scenario: Malformed JSON shows parse error
  Given I have a file with invalid JSON syntax
  When I run "iw validate-review-state project-management/issues/IW-42/review-state.json"
  Then I see error message "JSON parse error" with location details
  And the command exits with status 1

Scenario: Validate from stdin
  Given I have JSON content piped to stdin
  When I run "iw validate-review-state --stdin"
  Then the content is validated against the schema
  And I see validation result on stdout
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**
Moderate complexity because:
- Manual validation with upickle (no external JSON Schema library)
- Must design clear error messages
- Command needs to handle both JSON parse errors and schema validation errors
- Should work with both relative paths, absolute paths, and stdin

**Key Components:**
- `validate-review-state.scala` command in .iw/commands/
- ValidationResult value object (success vs errors)
- ValidationError value object (field, message)
- Validation logic matching schema constraints

**Acceptance:**
- Command validates against formal schema
- Clear, actionable error messages for validation failures
- Both JSON syntax errors and schema violations handled
- Works with file paths and stdin
- Exit codes: 0 for valid, 1 for invalid

---

### Story 3: Workflow writes valid state via command

```gherkin
Feature: Write validated review state via CLI
  As a workflow (kanon agent)
  I want to write review-state.json through an iw command
  So that validation happens automatically and I don't duplicate logic

Scenario: Write state with all required fields
  Given I am in a worktree for issue IW-42
  When I run command with:
    """
    iw write-review-state \
      --status implementing \
      --phase 2 \
      --message "Phase 2 in progress" \
      --artifact "Analysis:project-management/issues/IW-42/analysis.md" \
      --artifact "Context:project-management/issues/IW-42/phase-02-context.md"
    """
  Then file "project-management/issues/IW-42/review-state.json" is created
  And the file passes schema validation
  And the file contains my provided values
  And issue_id is auto-inferred from branch name
  And git_sha is auto-populated from HEAD
  And last_updated is auto-populated with current timestamp

Scenario: Write state with available_actions
  Given I am in a worktree for issue IW-42
  When I run command with:
    """
    iw write-review-state \
      --status awaiting_review \
      --action "continue:Continue to next phase:ag-implement" \
      --action "review:Generate review packet:ag-review"
    """
  Then the file contains available_actions array with both actions
  And each action has id, label, and skill fields

Scenario: Write state from stdin for complex data
  Given I have JSON data with full state in stdin
  When I run "iw write-review-state --from-stdin"
  Then the data is validated before writing
  And if valid, file is written
  And if invalid, I see validation error without writing file

Scenario: Validation failure prevents write
  Given I provide invalid status value "invalid_status"
  When I run "iw write-review-state --status invalid_status"
  Then I see error "Invalid status 'invalid_status'. Valid values: ..."
  And no file is written
  And the command exits with status 1

Scenario: Explicit output path
  Given I want to write to a specific location
  When I run "iw write-review-state --status implementing --output /path/to/review-state.json"
  Then the file is written to the specified path
```

**Estimated Effort:** 8-10h
**Complexity:** Moderate

**Technical Feasibility:**
Moderate because:
- Must design ergonomic CLI interface for structured data
- Two input modes (flags vs stdin) need different UX
- Validation must happen before any I/O
- Need to infer issue_id from current worktree/branch
- Auto-populate git_sha and last_updated

**Key Components:**
- `write-review-state.scala` command in .iw/commands/
- ReviewStateBuilder (construct ReviewState from CLI args)
- Uses validation from Story 2 before writing
- GitAdapter.getCurrentBranch for issue ID inference
- Uses existing IssueId normalization

**Acceptance:**
- Can write state via flags or stdin
- Validation happens before file I/O
- Issue ID auto-inferred from git branch
- git_sha and last_updated auto-populated
- Clear validation errors prevent invalid writes
- Supports --output for explicit path
- Always replaces file (no merge semantics)

---

## Architectural Sketch

**Purpose:** List WHAT components each story needs, not HOW they're implemented.

### For Story 1: JSON Schema formally defines contract

**Schema Layer:**
- `schemas/review-state.schema.json` - JSON Schema file
- `schemas/README.md` - Versioning policy documentation

**No code changes - pure schema definition**

---

### For Story 2: Workflow validates state before writing

**Domain Layer:**
- ValidationResult value object (success vs errors)
- ValidationError value object (field, message)

**Application Layer:**
- ReviewStateValidator.validate(json: String): ValidationResult

**Infrastructure Layer:**
- FileSystemAdapter.readFile(path: String) (existing)
- StdinReader for --stdin mode

**Presentation Layer:**
- `validate-review-state.scala` command in .iw/commands/
- Output formatting for validation errors

---

### For Story 3: Workflow writes valid state via command

**Domain Layer:**
- ReviewStateBuilder (construct ReviewState from CLI args)
- IssueId (existing - for inferring from branch)

**Application Layer:**
- ReviewStateValidator.validate (from Story 2)

**Infrastructure Layer:**
- FileSystemAdapter.writeFile(path, content)
- GitAdapter.getCurrentBranch (existing - for issue ID inference)
- GitAdapter.getHeadSha (for git_sha field)

**Presentation Layer:**
- `write-review-state.scala` command in .iw/commands/
- CLI argument parsing for flags (--status, --artifact, --action, etc.)
- Stdin reading for --from-stdin mode

---

## Resolved Decisions

These items were previously CLARIFY markers, now resolved through discussion:

### Decision 1: JSON Schema validation approach

**Decision:** Manual validation with upickle (no external JSON Schema library)

**Rationale:**
- We already use upickle in the codebase
- Schema is relatively simple (no complex constraints like conditionals)
- Full control over error messages
- No new dependencies to manage
- Can add formal JSON Schema library later if validation becomes complex

---

### Decision 2: Command naming

**Decision:** Use full names: `validate-review-state` and `write-review-state`

**Rationale:**
- Self-documenting - clear what's being validated/written
- "state" alone is too vague (what kind of state?)
- Consistent with "review-state.json" filename
- Tab completion makes longer names less of a burden

---

### Decision 3: available_actions scope

**Decision:** Include `available_actions` in schema, but defer dashboard UI to separate issue

**Rationale:**
- Schema should be complete - workflows may want to write actions
- We're formalizing the current implicit schema which may already include actions
- Testing that schema correctly represents actions doesn't require dashboard
- Dashboard changes are separate concern with separate effort

---

### Decision 4: Schema versioning strategy

**Decision:** Integer version, bump only for breaking changes

**Rationale:**
- Simple and practical
- Adding optional fields doesn't require version bump
- Current implicit schema will be version 1
- Breaking changes (removed field, changed type) require version bump
- Dashboard will support current version (can add backward compat later if needed)

---

### Decision 5: Write command semantics

**Decision:** Always replace (no merge mode)

**Rationale:**
- Simpler implementation and mental model
- Workflows typically generate full state at once
- Merge semantics for arrays (like artifacts) are ambiguous
- Can add --merge flag later if genuine need emerges

---

### Decision 6: Skill ownership

**Decision:** Skill belongs in workflows (kanon) repository, not iw-cli

**Rationale:**
- Skills teach workflow agents how to use tools
- Creating skills in iw-cli would create dependency from workflows to iw-cli skill definitions
- Workflows should own their agent instruction space
- Clean separation: iw-cli owns contract + tools, workflows own agent skills
- Workflows will reference schema from iw-cli and create their own skill

---

## Remaining Open Questions

### CLARIFY: Status enum values

**Question:** What are all valid values for the `status` field?

**Current known values from codebase:**
- `implementing`
- `awaiting_review`
- `analysis_ready`

**Need to confirm:**
- Are there other valid statuses?
- Should we allow custom statuses or strict enum?
- What status values does the dashboard currently recognize?

**Impact:** Affects schema definition and validation logic.

---

### CLARIFY: Required vs optional fields

**Question:** Which fields should be required vs optional?

**Proposed:**
- Required: `version`, `issue_id`, `status`, `artifacts`
- Optional: `phase`, `step`, `branch`, `pr_url`, `git_sha`, `last_updated`, `message`, `batch_mode`, `phase_checkpoints`, `available_actions`

**Need to confirm:**
- Is `artifacts` required or can it be empty array?
- Should `last_updated` be required for auditability?
- Any other fields that should be required?

**Impact:** Affects validation strictness.

---

## Total Estimates

**Story Breakdown:**
- Story 1 (JSON Schema formally defines contract): 4-6h
- Story 2 (Workflow validates state before writing): 6-8h
- Story 3 (Workflow writes valid state via command): 8-10h

**Total Range:** 18-24 hours

**Confidence:** Medium-High

**Reasoning:**
- **Known domain**: Review state structure already exists, we're formalizing it
- **Existing patterns**: Command creation follows established .iw/commands/ pattern
- **No UI work**: Dashboard changes deferred to separate issue
- **Simple validation**: Manual validation with upickle, no complex library integration

---

## Testing Approach

**Story 1 (JSON Schema):**
- Validate schema itself using JSON Schema meta-validator
- Create example valid and invalid JSON files as test fixtures
- Schema should accept all existing review-state.json files in codebase

**Story 2 (validate-review-state command):**
- Unit: Validation logic with sample JSON strings
- E2E (BATS): Run command with valid/invalid/malformed files, verify exit codes and output

**Story 3 (write-review-state command):**
- Unit: ReviewStateBuilder from CLI args, JSON serialization
- Integration: Git branch detection for issue ID inference
- E2E (BATS): Run command with various flag combinations, verify output files

**Test Data Strategy:**
- Use existing review-state.json files from project-management/issues/ as valid fixtures
- Create test fixtures in test directory with valid/invalid samples
- Test edge cases: empty arrays, missing optional fields, wrong types

---

## Implementation Sequence

**Recommended Order:**

1. **Story 1: JSON Schema** - Establishes the contract that validation depends on
2. **Story 2: validate-review-state** - Core validation command, needed by write command
3. **Story 3: write-review-state** - Uses validation from Story 2

**Dependencies:**
- Story 1 → Story 2 (validation needs schema definition)
- Story 2 → Story 3 (write command uses validation)

---

## Deferred Work (Future Issues)

These items are explicitly out of scope for IW-136:

1. **Dashboard validation UI** - Show validation warnings on worktree cards
2. **Dashboard action buttons** - Render and invoke available_actions
3. **Skill documentation** - Create skill in workflows (kanon) repository
4. **Schema version migration** - Runtime handling of multiple schema versions

---

**Analysis Status:** Ready for Implementation

**Next Steps:**
1. **Resolve remaining CLARIFY markers** (status enum, required fields)
2. **Generate tasks:** `/iterative-works:ag-create-tasks IW-136`
3. **Begin implementation:** `/iterative-works:ag-implement IW-136`

---
