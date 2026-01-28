# Phase 3: Write command for review state

**Issue:** IW-136
**Phase:** 3 of 3
**Story:** Workflow writes valid state via command

## Goals

Implement `iw write-review-state` command that constructs a valid review-state.json file from CLI arguments, validates it using Phase 2's validator, and writes it to the correct location. Auto-populates git_sha, last_updated, and issue_id.

## Scope

### In Scope
- `write-review-state.scala` command in `.iw/commands/`
- ReviewState building from CLI arguments
- Auto-inference of issue_id from git branch
- Auto-population of git_sha (from HEAD) and last_updated (current timestamp)
- Validation before writing (using ReviewStateValidator from Phase 2)
- Two input modes: CLI flags and `--from-stdin`
- Explicit output path via `--output`
- Unit tests for building logic
- E2E tests (BATS) for command behavior

### Out of Scope
- Dashboard integration
- Merge semantics (always replace)
- Schema migration

## Dependencies

- Phase 1: Schema at `schemas/review-state.schema.json`
- Phase 2: `ReviewStateValidator.validate()`, `ValidationError`, `ValidationResult`
- Existing: `IssueId.fromBranch()`, `GitAdapter.getCurrentBranch()`

## What Was Built in Previous Phases

- Phase 1: JSON Schema defining the contract
- Phase 2: Pure validation logic (`ReviewStateValidator.validate(json)`) and CLI validation command

## Technical Approach

### Building ReviewState from CLI Arguments

The command needs to accept structured data via flags:

```
iw write-review-state \
  --status implementing \
  --phase 2 \
  --step implementation \
  --message "Phase 2 in progress" \
  --artifact "Analysis:project-management/issues/IW-42/analysis.md" \
  --artifact "Context:project-management/issues/IW-42/phase-02-context.md" \
  --action "continue:Continue:ag-implement" \
  --branch IW-42 \
  --batch-mode \
  --output project-management/issues/IW-42/review-state.json
```

**Flag parsing:**
- `--status <value>` - Required (or from stdin)
- `--phase <value>` - Optional, accepts integer or string
- `--step <value>` - Optional
- `--message <value>` - Optional
- `--artifact <label:path>` - Repeatable, colon-separated
- `--action <id:label:skill>` - Repeatable, colon-separated
- `--branch <value>` - Optional (auto-detected from git if not provided)
- `--batch-mode` - Boolean flag (presence = true)
- `--pr-url <value>` - Optional
- `--output <path>` - Optional (default: auto-detect from issue_id)
- `--from-stdin` - Read full JSON from stdin instead of flags
- `--issue-id <value>` - Optional override (default: inferred from branch)
- `--version <value>` - Optional (default: 1)

### Auto-population

1. **issue_id**: Infer from git branch via `IssueId.fromBranch()`, or from `--issue-id` flag
2. **git_sha**: Get from `git rev-parse --short HEAD`
3. **last_updated**: Current UTC timestamp in ISO 8601 format
4. **version**: Default to 1 unless `--version` provided

### Output Path Detection

If `--output` not provided:
1. Derive from issue_id: `project-management/issues/{ISSUE_ID}/review-state.json`
2. Create parent directories if needed

### Validation Flow

1. Build JSON from flags or read from stdin
2. Validate using `ReviewStateValidator.validate(json)`
3. If valid: write to output path, exit 0
4. If invalid: show errors, don't write, exit 1

### Architecture

- Domain: `ReviewStateBuilder` in `.iw/core/model/` - pure function to construct JSON from typed inputs
- Command: `.iw/commands/write-review-state.scala` - imperative shell (arg parsing, git calls, file write)
- Reuse: `ReviewStateValidator` from Phase 2, `GitAdapter` and `IssueId` from existing code

### Existing Code to Use

- `GitAdapter.getCurrentBranch(dir)` - get current branch name
- `IssueId.fromBranch(branchName)` - extract issue ID from branch
- `ReviewStateValidator.validate(json)` - validate before writing
- `Output.info/error/success/warning` - consistent CLI output
- `os.read/os.write` - file I/O via os-lib
- `upickle ujson` - JSON construction

## Files to Create/Modify

1. `.iw/core/model/ReviewStateBuilder.scala` - Pure JSON construction from typed inputs
2. `.iw/core/adapters/Git.scala` - Add `getHeadSha()` method
3. `.iw/commands/write-review-state.scala` - CLI command
4. `.iw/core/test/ReviewStateBuilderTest.scala` - Unit tests for builder
5. `.iw/test/write-review-state.bats` - E2E tests
6. `.iw/core/test/GitAdapterTest.scala` - Test for getHeadSha (if not already tested)

## Testing Strategy

### Unit Tests (.iw/core/test/ReviewStateBuilderTest.scala)
- Build with required fields only → valid JSON with version, issue_id, status, artifacts, last_updated
- Build with all fields → includes optional fields
- Build with multiple artifacts → artifacts array correct
- Build with multiple actions → available_actions array correct
- Build with phase as integer → phase field is integer
- Build with phase as string → phase field is string
- Build with batch_mode flag → batch_mode is true
- Output passes ReviewStateValidator.validate()

### E2E Tests (.iw/test/write-review-state.bats)
- Write with required flags → creates file, exit 0
- Write with all flags → correct JSON in file
- Write with --from-stdin → reads and validates
- Invalid status in stdin → exit 1, no file written
- Auto-infer issue_id from branch → correct file path
- --output flag → writes to specified path
- Missing --status flag (no stdin) → exit 1

## Acceptance Criteria

- [ ] Can write state via flags or stdin
- [ ] Validation happens before file I/O
- [ ] Issue ID auto-inferred from git branch
- [ ] git_sha and last_updated auto-populated
- [ ] Clear validation errors prevent invalid writes
- [ ] Supports --output for explicit path
- [ ] Always replaces file (no merge semantics)
- [ ] Unit tests cover builder logic
- [ ] E2E tests verify command behavior
