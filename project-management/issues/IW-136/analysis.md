# Story-Driven Analysis: Define review-state.json schema and provide workflow integration skills

**Issue:** IW-136
**Created:** 2026-01-28
**Status:** Draft
**Classification:** Feature

## Problem Statement

Currently, the `review-state.json` file schema is implicitly defined by what workflow tools (kanon) produce. This creates a fragile contract where:
- Workflows must guess at the correct structure
- iw-cli dashboard must adapt to whatever workflows produce
- Schema changes have no migration path
- Validation happens only when dashboard fails to parse the file

**User Need:** Workflow authors need clear, authoritative guidance on the review-state.json format so they can reliably communicate issue status to the iw-cli dashboard.

**Value:** By making iw-cli the schema owner, we establish clear ownership boundaries:
- **iw-cli**: Defines and validates the schema, provides skills/commands for workflows
- **Workflows (kanon)**: Consume skills to produce validated state, call iw commands

This enables:
- Workflow authors to write valid state without studying iw-cli internals
- Dashboard to validate incoming state and provide clear error messages
- Schema evolution with explicit versioning and migration paths
- Confidence that state files are correct before workflows complete

## User Stories

### Story 1: Workflow validates state before writing

```gherkin
Feature: Validate review state against JSON schema
  As a workflow author
  I want to validate my review-state.json before writing it
  So that I catch errors early and don't break the dashboard

Scenario: Valid state passes validation
  Given I have a review-state.json file with valid schema
  When I run "iw validate-state project-management/issues/IW-42/review-state.json"
  Then I see "Validation successful: review-state.json is valid"
  And the command exits with status 0

Scenario: Invalid state shows clear error
  Given I have a review-state.json with missing required field "issue_id"
  When I run "iw validate-state project-management/issues/IW-42/review-state.json"
  Then I see error message "Validation failed: missing required field 'issue_id'"
  And I see the schema location where the field is defined
  And the command exits with status 1

Scenario: Malformed JSON shows parse error
  Given I have a file with invalid JSON syntax
  When I run "iw validate-state project-management/issues/IW-42/review-state.json"
  Then I see error message "JSON parse error at line 5, column 12"
  And the command exits with status 1
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**
Moderate complexity because:
- Need to choose JSON Schema validation library for Scala
- Must design clear error messages that reference schema
- Command needs to handle both JSON parse errors and schema validation errors
- Should work with both relative and absolute paths

**Key Technical Challenges:**
- Finding appropriate JSON Schema library (jsonschema4s, json-schema-validator, or manual validation?)
- Error message clarity - must be actionable for workflow authors
- Schema location in error messages (line numbers in schema)

**Acceptance:**
- Command validates against formal schema
- Clear, actionable error messages for validation failures
- Both JSON syntax errors and schema violations handled
- Works with relative paths from any directory

---

### Story 2: Workflow skill writes valid state programmatically

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

Scenario: Write state from stdin for complex data
  Given I have JSON data with full state in stdin
  When I run "iw write-review-state --from-stdin"
  Then the data is validated before writing
  And if valid, file is written
  And if invalid, I see validation error without writing file

Scenario: Validation failure prevents write
  Given I provide invalid status value "invalid_status"
  When I run "iw write-review-state --status invalid_status"
  Then I see error "Invalid status 'invalid_status'. Valid values: implementing, awaiting_review, ..."
  And no file is written
  And the command exits with status 1
```

**Estimated Effort:** 8-12h
**Complexity:** Complex

**Technical Feasibility:**
Complex because:
- Must design ergonomic CLI interface for structured data
- Two input modes (flags vs stdin) need different UX
- Validation must happen before any I/O
- Need to infer issue_id from current worktree/branch
- Must handle partial updates vs full writes

**Key Technical Challenges:**
- CLI flag design for arrays (--artifact flag repeated multiple times)
- Stdin vs flags: how to make both intuitive
- Issue ID inference from git context
- Partial update semantics (merge with existing state or replace?)
- Timestamp generation (ISO 8601 format)

**Acceptance:**
- Can write state via flags or stdin
- Validation happens before file I/O
- Issue ID auto-inferred from git branch
- Clear validation errors prevent invalid writes
- Both create and update scenarios work

---

### Story 3: JSON Schema formally defines contract

```gherkin
Feature: Formal JSON Schema for review state
  As a workflow author
  I want to read the formal schema definition
  So that I understand all fields, types, and constraints

Scenario: Schema defines all current fields
  Given I open "schemas/review-state.schema.json"
  Then I see JSON Schema with version property set to draft-07 or later
  And I see all required fields: version, issue_id, status, artifacts
  And I see all optional fields: phase, step, branch, pr_url, git_sha, last_updated, message, batch_mode, phase_checkpoints
  And status field has enum with valid values
  And version field is integer with minimum 1

Scenario: Schema provides field descriptions
  Given I read the schema
  Then each field has a "description" property
  And the description explains what the field is for
  And examples are provided for complex fields

Scenario: Schema is referenced in documentation
  Given I read ".claude/skills/review-state/SKILL.md"
  Then I see reference to "schemas/review-state.schema.json"
  And I see examples of valid review-state.json
  And I see links to validation command usage
```

**Estimated Effort:** 4-6h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward because:
- JSON Schema is well-established format
- Schema structure clear from existing review-state.json examples
- No code generation needed (manual schema definition)

**Key Technical Challenges:**
- Choosing JSON Schema version (draft-07 vs 2019-09 vs 2020-12)
- Deciding which fields are required vs optional
- Enum values for status field (need to enumerate all valid states)
- Whether to make schema strict (additionalProperties: false) or permissive

**Acceptance:**
- Schema file exists at schemas/review-state.schema.json
- All fields from IW-100 example are defined
- Field types and constraints are specified
- Descriptions explain purpose of each field
- Schema passes meta-validation (is itself valid JSON Schema)

---

### Story 4: Skill teaches workflows to use validation

```gherkin
Feature: Claude Code skill for review state workflow integration
  As a Claude Code agent running kanon workflow
  I want to load a skill that teaches me the review-state contract
  So that I can produce valid state files during workflow execution

Scenario: Skill provides validation command usage
  Given I load skill ".claude/skills/review-state/SKILL.md"
  Then I see section "Validating Review State"
  And I see example: "iw validate-state <path>"
  And I see explanation of validation errors

Scenario: Skill provides write command usage
  Given I load the review-state skill
  Then I see section "Writing Review State"
  And I see example using flags: "iw write-review-state --status ... --artifact ..."
  And I see example using stdin: "iw write-review-state --from-stdin"
  And I see when to use each approach

Scenario: Skill shows schema structure
  Given I load the review-state skill
  Then I see section "Schema Reference"
  And I see required vs optional fields
  And I see valid values for enum fields (status)
  And I see example of complete valid review-state.json
```

**Estimated Effort:** 3-4h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward because:
- SKILL.md format is established pattern
- Similar to existing iw-cli-ops and iw-command-creation skills
- Mostly documentation and examples

**Key Technical Challenges:**
- Making examples clear and copy-pastable
- Covering both common cases and edge cases
- Keeping skill concise but complete

**Acceptance:**
- Skill file at .claude/skills/review-state/SKILL.md
- Follows established skill format (frontmatter + sections)
- Contains working examples for validation and writing
- References schema location
- Covers error handling scenarios

---

### Story 5: Dashboard reads and validates on load

```gherkin
Feature: Dashboard validates state files on load
  As a dashboard user
  I want to see validation errors for invalid state files
  So that I know which worktrees have broken state and can fix them

Scenario: Valid state file displays normally
  Given worktree IW-42 has valid review-state.json
  When I open the dashboard
  Then I see the worktree card for IW-42
  And the review artifacts section displays correctly
  And no validation warnings appear

Scenario: Invalid state shows warning on card
  Given worktree IW-42 has review-state.json with invalid status value
  When I open the dashboard
  Then I see the worktree card for IW-42
  And I see warning badge "Invalid review state"
  And clicking the badge shows validation error details
  And the card still renders (graceful degradation)

Scenario: Missing state file shows nothing
  Given worktree IW-42 has no review-state.json
  When I open the dashboard
  Then I see the worktree card for IW-42
  And no review artifacts section appears
  And no error is shown (missing file is normal)
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**
Moderate because:
- Dashboard already loads review-state.json (via ReviewStateService)
- Need to add schema validation to existing load path
- Must design UI for validation errors
- Graceful degradation is important (don't break dashboard for one bad file)

**Key Technical Challenges:**
- Integrating validation into existing ReviewStateService
- Error message display in UI (badge, tooltip, modal?)
- Caching validation results (don't re-validate unchanged files)
- Distinguishing validation errors from missing files

**Acceptance:**
- Dashboard validates state files using schema
- Invalid files show clear warnings on cards
- Valid files display normally
- Missing files don't show errors (expected state)
- Validation errors don't crash dashboard

---

### Story 6: Schema version migration support

```gherkin
Feature: Schema version field enables migration
  As an iw-cli maintainer
  I want the schema to include a version field
  So that we can evolve the schema without breaking existing workflows

Scenario: Current files have version field
  Given review-state.json has "version": 2
  When dashboard loads the file
  Then it recognizes version 2 format
  And parses fields according to version 2 schema

Scenario: Future version 3 file with migration
  Given review-state.json has "version": 3
  And version 3 adds new required field "workflow_type"
  When dashboard loads the file with version 2 code
  Then it shows warning "Unknown schema version 3 (expected version 2)"
  And it attempts to parse with version 2 schema (best effort)
  And suggests "Update iw-cli to support schema version 3"

Scenario: Documentation explains migration
  Given I read "schemas/README.md"
  Then I see section "Schema Versioning"
  And I see migration policy: major version for breaking changes
  And I see compatibility promise: iw-cli version X supports schema versions Y-Z
```

**Estimated Effort:** 4-6h
**Complexity:** Moderate

**Technical Feasibility:**
Moderate because:
- Version field already exists in IW-100 example
- Need to design version detection logic
- Need to plan for future migrations (structure, not implementation)
- Documentation is key part of this story

**Key Technical Challenges:**
- Deciding version bump policy (semver-like? or just integers?)
- Forward compatibility strategy (fail gracefully vs attempt parse)
- Backward compatibility promise (how many old versions to support?)
- Schema evolution documentation

**Acceptance:**
- Schema includes version field as required integer
- Dashboard code checks version before parsing
- Unknown versions show clear warning with suggestion
- Documentation explains versioning policy
- Migration path documented for future schema changes

---

### Story 7: Dashboard invokes available_actions from state

```gherkin
Feature: Dashboard displays action buttons from state
  As a dashboard user
  I want to see action buttons defined in review-state.json
  So that I can trigger next steps without leaving the dashboard

Scenario: State defines available actions
  Given review-state.json contains:
    """
    {
      "available_actions": [
        {"id": "continue", "label": "Continue to next phase", "skill": "ag-implement"},
        {"id": "review", "label": "Generate review packet", "skill": "ag-review"}
      ]
    }
    """
  When I view the worktree card in dashboard
  Then I see button "Continue to next phase"
  And I see button "Generate review packet"

Scenario: Clicking action shows invocation instructions
  Given worktree has available_actions defined
  When I click "Continue to next phase" button
  Then I see modal with command to run:
    """
    /iterative-works:ag-implement IW-42 --continue
    """
  And I see "Copy command" button
  And I see explanation "Run this in Claude Code to continue"

Scenario: No available_actions shows no buttons
  Given review-state.json has no available_actions field
  When I view the worktree card
  Then no action buttons appear
  And the card displays normally
```

**Estimated Effort:** 8-12h
**Complexity:** Complex

**Technical Feasibility:**
Complex because:
- Requires UI changes to dashboard (new button component)
- Need modal/tooltip for showing command to copy
- Must handle missing available_actions gracefully
- Copy-to-clipboard functionality needs JavaScript
- Schema needs to define available_actions structure

**Key Technical Challenges:**
- UI design: where to place action buttons on card
- How to communicate "copy this command" UX
- JavaScript for clipboard (or use HTMX pattern?)
- Action command templating (substitute issue ID dynamically?)
- Security: prevent arbitrary command injection

**Acceptance:**
- Dashboard parses available_actions from state
- Action buttons appear on worktree cards
- Clicking button shows command to run
- Copy-to-clipboard works in browser
- Missing available_actions doesn't break rendering

---

## Architectural Sketch

**Purpose:** List WHAT components each story needs, not HOW they're implemented.

### For Story 1: Workflow validates state before writing

**Domain Layer:**
- ReviewState (existing)
- ValidationResult value object (success vs errors)
- ValidationError value object (field, message, schema location)

**Application Layer:**
- ValidationService.validateReviewState(json: String): ValidationResult
- SchemaLoader.loadSchema(): JsonSchema

**Infrastructure Layer:**
- JsonSchemaValidator (adapter wrapping chosen library)
- FileSystemAdapter.readFile(path: String) (existing)

**Presentation Layer:**
- validate-state.scala command in .iw/commands/
- Output formatting for validation errors

---

### For Story 2: Workflow skill writes valid state programmatically

**Domain Layer:**
- ReviewStateBuilder (construct ReviewState from CLI args)
- IssueId (existing - for inferring from branch)

**Application Layer:**
- ReviewStateService.writeReviewState(state: ReviewState, path: String)
- ValidationService.validateReviewState (from Story 1)

**Infrastructure Layer:**
- FileSystemAdapter.writeFile(path, content)
- GitAdapter.getCurrentBranch (existing - for issue ID inference)

**Presentation Layer:**
- write-review-state.scala command in .iw/commands/
- CLI argument parsing for flags (--status, --artifact, etc.)
- Stdin reading for --from-stdin mode

---

### For Story 3: JSON Schema formally defines contract

**Schema Layer:**
- schemas/review-state.schema.json file
- schemas/README.md documentation

**No code changes - pure schema definition**

---

### For Story 4: Skill teaches workflows to use validation

**Documentation Layer:**
- .claude/skills/review-state/SKILL.md

**No code changes - pure documentation**

---

### For Story 5: Dashboard reads and validates on load

**Domain Layer:**
- ValidationResult (from Story 1)
- CachedReviewState extended with validation state

**Application Layer:**
- ReviewStateService.fetchReviewState - add validation step
- DashboardService - handle validation errors

**Infrastructure Layer:**
- JsonSchemaValidator (from Story 1)

**Presentation Layer:**
- WorktreeCard view - add validation warning badge
- ValidationErrorModal view - show error details

---

### For Story 6: Schema version migration support

**Domain Layer:**
- SchemaVersion value object (version number + compatibility)
- MigrationPolicy (rules for version compatibility)

**Application Layer:**
- VersionDetector.detectVersion(json: String): SchemaVersion
- CompatibilityChecker.isSupported(version: SchemaVersion): Boolean

**Infrastructure Layer:**
- No new adapters needed

**Presentation Layer:**
- Dashboard warning display for unsupported versions

**Documentation Layer:**
- schemas/README.md with versioning policy

---

### For Story 7: Dashboard invokes available_actions from state

**Domain Layer:**
- AvailableAction value object (id, label, skill)
- ReviewState extended with optional available_actions field

**Application Layer:**
- ReviewStateService.parseReviewStateJson - handle available_actions
- ActionCommandBuilder.buildCommand(action: AvailableAction, issueId: String): String

**Infrastructure Layer:**
- No new adapters needed

**Presentation Layer:**
- ActionButton component
- CommandModal view (show command to copy)
- JavaScript/HTMX for copy-to-clipboard

---

## Technical Risks & Uncertainties

### CLARIFY: JSON Schema validation library choice

We need to choose a JSON Schema validation library for Scala that works with scala-cli.

**Questions to answer:**
1. Which JSON Schema library should we use for validation?
2. What JSON Schema version should the schema use (draft-07, 2019-09, 2020-12)?
3. Should we embed the schema in code or read it from file at runtime?

**Options:**
- **Option A: json-schema-validator (Java library)** 
  - Pros: Mature, feature-complete, supports multiple draft versions
  - Cons: Java interop overhead, heavyweight dependency
  
- **Option B: jsonschema4s (Scala native)**
  - Pros: Scala-native, lighter weight, good error messages
  - Cons: May have limited draft support, less battle-tested
  
- **Option C: Manual validation with upickle**
  - Pros: No external dependency, full control, already using upickle
  - Cons: Must maintain validation logic ourselves, no schema reuse

**Impact:** Affects Stories 1, 2, 5 - all validation logic depends on this choice. Also affects complexity of error messages and schema features we can use.

**Recommendation:** Start with Option C (manual validation) for MVP since we already have upickle and the schema is relatively simple. Can add formal JSON Schema library later if validation logic becomes complex.

---

### CLARIFY: Command naming conventions

We're adding new commands `validate-state` and `write-review-state`. Need to confirm naming pattern.

**Questions to answer:**
1. Should commands be `validate-state` or `validate-review-state` (full name)?
2. Should write command be `write-review-state` or `update-review-state` or `set-review-state`?
3. Should these be subcommands (e.g., `iw review-state validate`) or top-level?

**Options:**
- **Option A: Top-level with abbreviated names**
  - Commands: `iw validate-state`, `iw write-review-state`
  - Pros: Shorter to type, follows existing pattern (iw issue, iw start)
  - Cons: "state" is vague (what kind of state?)
  
- **Option B: Top-level with full names**
  - Commands: `iw validate-review-state`, `iw write-review-state`
  - Pros: Self-documenting, clear what's being validated
  - Cons: Longer to type
  
- **Option C: Subcommand group**
  - Commands: `iw review-state validate`, `iw review-state write`
  - Pros: Organized namespace, room for future commands
  - Cons: More typing, subcommand pattern not used elsewhere in iw-cli

**Impact:** Affects Stories 1, 2, 4 - command names appear in skill documentation and workflow code. Consistency with existing iw-cli commands is important for UX.

**Recommendation:** Option B (top-level with full names) for clarity, since "review-state" is the domain concept we're formalizing.

---

### CLARIFY: Scope of available_actions implementation

Story 7 adds `available_actions` to enable workflow-dashboard interaction, but scope is unclear.

**Questions to answer:**
1. Is Story 7 in MVP scope or deferred to future?
2. Should dashboard execute actions (dangerous) or just display command to copy?
3. What security model prevents malicious actions in state files?
4. Should actions support templating (substitute issue ID, worktree path, etc.)?

**Options:**
- **Option A: MVP includes Story 7 with copy-only**
  - Dashboard shows buttons, clicking copies command
  - User pastes command into Claude Code manually
  - Pros: Safe (no execution), provides value (reduces friction)
  - Cons: Not truly automated, copy-paste step remains
  
- **Option B: Defer Story 7 to future iteration**
  - MVP focuses on schema + validation only
  - available_actions added to schema but dashboard ignores it
  - Pros: Smaller MVP, lower risk
  - Cons: Incomplete feature, workflows can't use available_actions yet
  
- **Option C: MVP includes Story 7 with dashboard execution**
  - Dashboard executes commands via iw CLI
  - Requires security model and sandboxing
  - Pros: True automation, best UX
  - Cons: Security risk, complex implementation, high effort

**Impact:** Major scope change. Story 7 is 8-12h effort. If deferred, total estimate drops from 47-66h to 39-54h. Also affects schema design (whether to include available_actions now or later).

**Recommendation:** Option B (defer Story 7). Focus MVP on schema + validation. Add available_actions field to schema as optional but don't implement dashboard UI. Can add in follow-up iteration once validation is stable.

---

### CLARIFY: Schema evolution strategy

Need to decide versioning policy and compatibility promises.

**Questions to answer:**
1. What version number is the current schema (start with 1, 2, or 3)?
2. What triggers a version bump (any change? breaking change only? new field?)?
3. How long must dashboard support old schema versions?
4. Should schema support multiple versions simultaneously or only latest?

**Options:**
- **Option A: Integer version, breaking changes only**
  - Version bump only for breaking changes (removed field, changed type)
  - Adding optional fields doesn't bump version
  - Dashboard supports last 2 major versions
  - Pros: Infrequent version bumps, wide compatibility
  - Cons: "Same version" files may have different fields
  
- **Option B: Integer version, any schema change**
  - Version bump for any change (new field, removed field, description change)
  - Strict versioning
  - Dashboard supports last 3 versions
  - Pros: Explicit compatibility, clear what schema version means
  - Cons: Version churn, frequent migrations
  
- **Option C: Semantic versioning (major.minor.patch)**
  - Major: breaking changes
  - Minor: new optional fields
  - Patch: description/documentation changes
  - Pros: Industry standard, clear semantics
  - Cons: More complex, overkill for simple schema?

**Impact:** Affects Story 6 implementation and long-term maintenance burden. Also affects how we document schema in schemas/README.md.

**Recommendation:** Option A (integer version, breaking changes only). Keep it simple. Current files use version 2, so schema definition should be version 2. Add field descriptions and mark all existing fields.

---

### CLARIFY: Partial update semantics for write-review-state

Story 2 command needs to handle both full writes and partial updates.

**Questions to answer:**
1. If review-state.json exists, does write-review-state replace or merge?
2. Should there be separate commands (write vs update) or flag (--merge)?
3. How to handle removing a field (set to null? omit flag? explicit --remove-field)?

**Options:**
- **Option A: Always replace (full write only)**
  - write-review-state always writes complete state
  - Must provide all required fields every time
  - To update one field, must re-specify all fields
  - Pros: Simple semantics, no merge logic
  - Cons: Tedious for small updates, error-prone
  
- **Option B: Always merge (update existing)**
  - write-review-state reads existing file, merges changes, writes back
  - Provided fields override, missing fields preserved
  - Pros: Convenient for updates, intuitive
  - Cons: More complex, merge strategy for arrays (replace or append artifacts?)
  
- **Option C: Explicit mode flag**
  - --merge flag determines behavior
  - Default is replace (write), --merge updates
  - Pros: Clear intent, supports both use cases
  - Cons: More flags to remember

**Impact:** Affects Story 2 implementation complexity and workflow usage patterns. Merge logic is tricky for array fields like artifacts.

**Recommendation:** Option A (always replace) for MVP. Keep it simple. Workflows typically generate full state at once. Can add merge mode later if needed.

---

## Total Estimates

**Story Breakdown:**
- Story 1 (Workflow validates state before writing): 6-8h
- Story 2 (Workflow skill writes valid state programmatically): 8-12h
- Story 3 (JSON Schema formally defines contract): 4-6h
- Story 4 (Skill teaches workflows to use validation): 3-4h
- Story 5 (Dashboard reads and validates on load): 6-8h
- Story 6 (Schema version migration support): 4-6h
- Story 7 (Dashboard invokes available_actions from state): 8-12h

**Total Range:** 39-56 hours

**Note:** If Story 7 is deferred per CLARIFY recommendation, MVP total is 31-44 hours.

**Confidence:** Medium

**Reasoning:**
- **Known domain**: Review state structure already exists, we're formalizing it
- **New patterns**: JSON Schema validation is new, library choice adds uncertainty
- **UI complexity**: Dashboard changes (Story 5, 7) have unknown effort for UI design
- **Scope risk**: Story 7 (available_actions) may reveal complexity not captured in estimate
- **Existing patterns**: Command creation, skill writing, schema definition follow established patterns

**Estimate assumes:**
- Manual validation with upickle (no external JSON Schema library)
- Story 7 deferred to future iteration
- Simple CLI interface (flags, not interactive prompts)
- Minimal UI changes to dashboard (warning badge, no complex modals)

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests**: Pure domain logic, validation functions, builders
2. **Integration Tests**: File I/O, command execution, schema loading
3. **E2E Scenario Tests**: Full command execution matching Gherkin scenarios

**Story-Specific Testing Notes:**

**Story 1 (validate-state command):**
- Unit: ValidationService with sample JSON strings, ValidationError formatting
- Integration: SchemaLoader reading schema file, FileSystemAdapter with real files
- E2E: Run `iw validate-state` with valid/invalid/malformed JSON files, verify exit codes and output

**Story 2 (write-review-state command):**
- Unit: ReviewStateBuilder from CLI args, JSON serialization, issue ID inference
- Integration: Git branch detection, file writing, validation before write
- E2E: Run `iw write-review-state` with various flag combinations and stdin, verify output files

**Story 3 (JSON Schema):**
- Unit: N/A (schema is data, not code)
- Integration: Schema file parses as valid JSON, passes JSON Schema meta-validation
- E2E: Validate schema itself using online JSON Schema validator

**Story 4 (Skill documentation):**
- Unit: N/A (documentation)
- Integration: Verify examples in skill actually work (run commands from examples)
- E2E: Load skill in Claude Code, verify it provides helpful guidance

**Story 5 (Dashboard validation):**
- Unit: ReviewStateService validation logic, CachedReviewState with validation results
- Integration: Dashboard load with valid/invalid state files, error display
- E2E: Start dashboard with various state files, verify warnings appear correctly

**Story 6 (Version migration):**
- Unit: VersionDetector parsing version field, CompatibilityChecker logic
- Integration: Dashboard loading different schema versions
- E2E: Create state files with version 1, 2, 3, verify dashboard behavior for each

**Story 7 (Available actions - if included):**
- Unit: ActionCommandBuilder templating, AvailableAction parsing
- Integration: Dashboard rendering action buttons, modal display
- E2E: Click action button in browser, verify command appears and copy works

**Test Data Strategy:**
- Use existing review-state.json files from project-management/issues/ as fixtures
- Create fixture directory: .iw/core/test/fixtures/review-state/ with valid/invalid samples
- Generate edge cases: empty arrays, missing optional fields, extra fields, wrong types

**Regression Coverage:**
- Existing ReviewStateService.parseReviewStateJson must still work
- Dashboard must still load state files without validation errors (backward compat)
- No changes to worktree card rendering for valid state files
- Cache invalidation logic unchanged (mtime-based)

## Deployment Considerations

### Database Changes
No database - all state is in JSON files.

**Story 3 migrations:**
- Create schemas/ directory
- Add schemas/review-state.schema.json
- Add schemas/README.md

**Story 4 migrations:**
- Create .claude/skills/review-state/ directory
- Add .claude/skills/review-state/SKILL.md

### Configuration Changes
No configuration changes needed. Commands use existing .iw/config.conf for project context.

### Rollout Strategy
Can deploy per story since each provides incremental value:

**After Story 1:** Workflows can validate state manually before writing
**After Story 2:** Workflows can use write-review-state command (auto-validates)
**After Story 3+4:** Schema and skill available for workflow authors to reference
**After Story 5:** Dashboard shows validation warnings for invalid state
**After Story 6:** Schema versioning documented for future evolution
**After Story 7:** Dashboard provides action buttons (if in scope)

No feature flags needed - commands are additive (don't break existing behavior).

### Rollback Plan
If validation is too strict and breaks existing workflows:
1. Identify which state files fail validation
2. Update schema to accept existing patterns (loosen constraints)
3. Re-release schema and validation command
4. Workflows re-validate and proceed

If write-review-state command has bugs:
- Workflows can still write files manually (as before)
- Fix command and re-release
- No data loss since validation happens before write

## Dependencies

### Prerequisites
- Existing ReviewState and ReviewStateService in .iw/core/
- Dashboard already loads review-state.json files
- upickle library available (existing dependency)
- Scala 3 and scala-cli for commands

### Story Dependencies
Sequential:
- Story 3 must complete before Story 1 (need schema to validate against)
- Story 1 must complete before Story 2 (write command uses validation)
- Story 3 must complete before Story 4 (skill references schema location)
- Story 1 must complete before Story 5 (dashboard uses validation)

Can parallelize:
- Story 4 can happen anytime after Story 3 (independent documentation)
- Story 6 can happen anytime (documentation + version detection logic)
- Story 7 can happen anytime after Story 5 (builds on dashboard changes)

### External Blockers
None. All work is internal to iw-cli.

Workflow authors (kanon) are consumers but not blockers - they'll adopt commands/skills once available.

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 3: JSON Schema formally defines contract** - Establishes the contract that all other stories depend on. Pure schema definition, no code.

2. **Story 1: Workflow validates state before writing** - Core validation logic. Once this works, we can build other features on top.

3. **Story 4: Skill teaches workflows to use validation** - Document validation command so workflows can start using it. Low effort, high value.

4. **Story 2: Workflow skill writes valid state programmatically** - Convenience layer on top of validation. Workflows can adopt incrementally.

5. **Story 5: Dashboard reads and validates on load** - Surface validation in dashboard. Provides visibility into state quality.

6. **Story 6: Schema version migration support** - Future-proofing. Can be done later but good to have policy documented early.

7. **Story 7: Dashboard invokes available_actions from state** - Optional/deferred. Most complex, least critical for MVP.

**Iteration Plan:**

- **Iteration 1** (Stories 3, 1): Schema definition + validation command (10-14h) - Core contract established
- **Iteration 2** (Stories 4, 2): Skill + write command (11-16h) - Workflow integration complete
- **Iteration 3** (Stories 5, 6): Dashboard validation + versioning (10-14h) - Polish + future-proofing
- **Future** (Story 7): Action buttons (8-12h) - Deferred per CLARIFY recommendation

## Documentation Requirements

- [x] JSON Schema serves as machine-readable contract (Story 3)
- [x] SKILL.md documents workflow integration (Story 4)
- [x] schemas/README.md explains versioning policy (Story 6)
- [ ] Update main CLAUDE.md if new patterns emerge
- [ ] Update .iw/docs/ if command usage needs detailed examples
- [ ] No API documentation needed (commands are CLI, not library)

---

**Analysis Status:** Ready for Review - CLARIFY markers need resolution

**Next Steps:**
1. **RESOLVE CLARIFY MARKERS:**
   - Validation library choice (manual vs external)
   - Command naming (`validate-state` vs `validate-review-state`)
   - Story 7 scope (include in MVP or defer?)
   - Schema versioning strategy (breaking changes only vs all changes)
   - Write command semantics (replace vs merge)

2. **After CLARIFY resolution:** Run `/iterative-works:ag-create-tasks IW-136` to map stories to implementation phases

3. **Implementation:** Run `/iterative-works:ag-implement IW-136` for iterative story-by-story development

---
