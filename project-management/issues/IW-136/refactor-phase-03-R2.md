# Refactoring R2: Add update command and restructure as subcommands

**Phase:** 3
**Created:** 2026-02-03
**Status:** Complete

## Decision Summary

**Problem 1 - Missing update functionality:**
After merging Phase 3 and workflows starting to use the commands in production, we discovered a critical usability issue: `write-review-state` requires all fields to be specified, forcing workflows to either:
1. Track the complete state and rewrite everything on every change
2. Build complex if-else branches to conditionally write different states
3. Resort to jq scripting to modify parts of the existing state

This is exactly what we wanted to avoid - workflows should be able to make simple, incremental updates to the state without managing the entire structure.

**Problem 2 - Command structure:**
We created top-level commands (`validate-review-state`, `write-review-state`) but they should be subcommands under a unified `review-state` command:
- `./iw review-state validate` (not `./iw validate-review-state`)
- `./iw review-state write` (not `./iw write-review-state`)
- `./iw review-state update` (new)

**Problem 3 - Public API contract:**
Since these commands are now public interface that workflows will depend on, we need to:
- Document the command interface (flags, behavior, exit codes)
- Define backward compatibility guarantees
- Version the command interface if needed

**Solution:**
1. Add `update` subcommand for partial state modification
2. Restructure existing commands as subcommands
3. Document public API contract for workflow authors

## Current State

**Existing commands (top-level):**

1. `.iw/commands/validate-review-state.scala`:
   - Validates a review-state.json file against schema
   - Usage: `iw validate-review-state <path>` or `iw validate-review-state --stdin`
   - Exit: 0 for valid, 1 for invalid

2. `.iw/commands/write-review-state.scala`:
   - Creates a complete review-state.json from scratch
   - Usage: `iw write-review-state [flags]` or `iw write-review-state --from-stdin`
   - Auto-populates: issue_id, git_sha, last_updated
   - Validates before writing

**Current usage pattern workflows are forced into:**
```bash
# Workflow must track whether state exists and build conditionally
if [ -f "review-state.json" ]; then
  # Use jq to modify existing state
  jq '.display.text = "Implementing" | .display.type = "progress"' review-state.json > tmp.json
  mv tmp.json review-state.json
else
  # Write complete state
  iw write-review-state --display-text "Implementing" --display-type progress --artifact "Analysis:..." --artifact "Tasks:..."
fi
```

**Current command structure issues:**
- Three separate top-level commands (`validate-review-state`, `write-review-state`, and planned `update-review-state`)
- No namespace grouping - pollutes top-level `iw` command space
- No public API documentation - workflows don't know what's stable vs internal

## Target State

**New command structure (subcommands under `review-state`):**

1. `./iw review-state validate <path>` or `./iw review-state validate --stdin`
   - Validates a review-state.json file against schema
   - Exit: 0 for valid, 1 for invalid
   - Clear error messages with field-level details

2. `./iw review-state write [flags]` or `./iw review-state write --from-stdin`
   - Creates a complete review-state.json from scratch
   - Auto-populates: issue_id, git_sha, last_updated
   - Validates before writing

3. `./iw review-state update [flags]` (NEW)
   - Reads existing review-state.json
   - Merges provided flags with existing JSON
   - Updates last_updated timestamp automatically
   - Validates merged result
   - Writes back to same location

**Desired usage pattern:**
```bash
# Simple update - just change what needs changing
./iw review-state update --display-text "Implementing" --display-type progress

# Append artifacts without replacing all
./iw review-state update --append-artifact "Phase2:path/to/phase-02-tasks.md"

# Clear a field
./iw review-state update --clear-message

# Multiple updates at once
./iw review-state update --display-text "Review Ready" --display-type success --needs-attention

# Write complete state from scratch
./iw review-state write --display-text "Planning" --display-type info --artifact "Analysis:path/to/analysis.md"

# Validate before committing
./iw review-state validate project-management/issues/IW-42/review-state.json
```

**Public API documentation:**
Create `docs/commands/review-state.md` documenting:
- Command interface (all flags, exit codes, behavior)
- JSON schema contract (link to schema)
- Usage examples for common workflows
- Backward compatibility guarantees
- Versioning policy (when breaking changes allowed)

## Constraints

- **PRESERVE:** Existing schema validation logic - reuse `ReviewStateValidator`
- **PRESERVE:** Existing merge/build logic where applicable
- **PRESERVE:** File structure and naming conventions for state files
- **DO NOT TOUCH:** Dashboard code - this is a CLI-only change
- **DO NOT TOUCH:** Schema definition - no schema changes needed
- **CLEAN RESTRUCTURE:** No workflows use old command names yet, so no deprecation needed - we'll update workflows before publishing

## Architecture

Following FCIS (Functional Core, Imperative Shell):

**Domain layer (pure):**
- `ReviewStateUpdater.merge(existingJson: String, updates: UpdateInput): String` - pure merge logic
- `UpdateInput` case class - typed representation of updates to apply

**Command layer (imperative shell):**
- `.iw/commands/review-state.scala` - Main command dispatcher for subcommands
- `.iw/commands/review-state/validate.scala` - Subcommand for validation
- `.iw/commands/review-state/write.scala` - Subcommand for writing from scratch
- `.iw/commands/review-state/update.scala` - Subcommand for partial updates

**Merge semantics for different field types:**
- **Scalar fields** (status, display.text, message, needs_attention, pr_url):
  - Provided value replaces existing
  - Not provided = keep existing
- **Object fields** (display):
  - Merge individual properties (e.g., can update just display.text, keep display.type)
- **Array fields** (artifacts, badges, actions, task_lists):
  - Default mode (--artifact): REPLACE all artifacts with provided list
  - Append mode (--append-artifact): ADD to existing list
  - Clear mode (--clear-artifacts): REMOVE all
- **Special fields:**
  - `last_updated`: Always set to current timestamp
  - `git_sha`: Keep existing unless explicitly overridden with --git-sha
  - `version`: Keep existing (schema version shouldn't change in updates)
  - `issue_id`: Keep existing (can't change issue ID)

## Tasks

### Analysis & Design

- [x] [impl] Review existing `write-review-state.scala` flag parsing patterns
- [x] [impl] Review existing `ReviewStateBuilder.BuildInput` structure
- [x] [impl] Design `UpdateInput` structure (what can be updated vs preserved)
- [x] [impl] Design merge semantics for each field type (scalar, object, array)
- [x] [impl] Design subcommand structure (dispatcher pattern vs separate entry points)

### Command Restructuring (Clean - No Backward Compatibility Needed)

- [x] [impl] Delete old commands: `.iw/commands/validate-review-state.scala` and `.iw/commands/write-review-state.scala`
- [x] [impl] Create `.iw/commands/review-state.scala` main dispatcher
- [x] [impl] Create `.iw/commands/review-state/validate.scala` subcommand
- [x] [impl] Create `.iw/commands/review-state/write.scala` subcommand
- [x] [impl] Test: `./iw review-state validate <path>` works
- [x] [impl] Test: `./iw review-state write [flags]` works
- [ ] [impl] Update all E2E tests to use new command structure (validate-review-state.bats, write-review-state.bats)

### Domain Logic (TDD)

- [x] [impl] Create `ReviewStateUpdater.scala` in `.iw/core/model/`
- [x] [impl] Create `UpdateInput` case class with all optional fields
- [x] [impl] Test: merge with no updates → returns existing JSON unchanged
- [x] [impl] Test: merge scalar field (display.text) → replaces value
- [x] [impl] Test: merge partial object (display.text only) → keeps display.type
- [x] [impl] Test: replace array (artifacts) → replaces entire array
- [x] [impl] Test: append to array (artifacts) → adds to existing
- [x] [impl] Test: clear array (artifacts) → removes all items
- [x] [impl] Test: last_updated always updated to current time
- [x] [impl] Test: git_sha preserved when not provided
- [x] [impl] Test: version and issue_id always preserved
- [x] [impl] Test: merged result passes ReviewStateValidator
- [x] [impl] Implement `ReviewStateUpdater.merge()` method

### Update Subcommand Implementation

- [x] [impl] Create `.iw/commands/review-state/update.scala` (or integrate into dispatcher)
- [x] [impl] Add input path detection (from issue_id or --input flag)
- [x] [impl] Add flag parsing matching write patterns
- [x] [impl] Add append/clear modes for array fields (--append-artifact, --clear-artifacts)
- [x] [impl] Add clear flags for optional fields (--clear-message, --clear-pr-url)
- [x] [impl] Read existing file, error if doesn't exist
- [x] [impl] Call ReviewStateUpdater.merge()
- [x] [impl] Validate merged result
- [x] [impl] Write back to same location
- [x] [impl] Error handling: file not found, validation failed, invalid flags

### E2E Tests

- [x] [impl] Delete old test files: `.iw/test/validate-review-state.bats` and `.iw/test/write-review-state.bats`
- [x] [impl] Create `.iw/test/review-state.bats` for all subcommands
- [x] [impl] Test: `./iw review-state validate <path>` → exit 0 for valid
- [x] [impl] Test: `./iw review-state validate <path>` → exit 1 for invalid with clear errors
- [x] [impl] Test: `./iw review-state validate --stdin` → validates from stdin
- [x] [impl] Test: `./iw review-state write [flags]` → creates file correctly
- [x] [impl] Test: `./iw review-state write --from-stdin` → reads and validates
- [x] [impl] Test: `./iw review-state update --display-text "X"` → updates scalar field
- [x] [impl] Test: update partial object (display.text) → display.type preserved
- [x] [impl] Test: replace array (--artifact) → all artifacts replaced
- [x] [impl] Test: append to array (--append-artifact) → added to existing
- [x] [impl] Test: clear array (--clear-artifacts) → array empty
- [x] [impl] Test: clear optional field (--clear-message) → field removed
- [x] [impl] Test: last_updated changed after update
- [x] [impl] Test: git_sha preserved when not provided
- [x] [impl] Test: version and issue_id preserved
- [x] [impl] Test: file not found for update → exit 1 with error
- [x] [impl] Test: validation failed → exit 1, file not modified
- [x] [impl] Test: auto-infer issue_id from branch → correct file path

### Public API Documentation

- [x] [impl] Create `docs/commands/review-state.md` with comprehensive documentation:
  - Overview and purpose (this is v1 of the public API)
  - All subcommands (validate, write, update)
  - All flags with types and descriptions
  - Exit codes and error handling
  - Usage examples for common workflows
  - JSON schema reference (link to schemas/review-state.schema.json)
- [x] [impl] Document backward compatibility policy (establishing for first public version):
  - What changes require major version bump
  - What changes are backward compatible
  - Deprecation policy for future breaking changes
  - Note: This refactoring IS v1.0, previous commands were internal/unpublished
- [x] [impl] Add examples showing the three command lifecycle:
  1. Create initial state: `review-state write`
  2. Update incrementally: `review-state update`
  3. Validate before commit: `review-state validate`
- [x] [impl] Document command header comments in all three subcommand files
- [x] [impl] Add note in main README about `./iw review-state` commands
- [x] [impl] Add note that this is the first public release of these commands

## Verification

After implementation:

- [x] Old command files deleted (validate-review-state.scala, write-review-state.scala)
- [x] Old test files deleted (validate-review-state.bats, write-review-state.bats)
- [x] All existing dashboard/domain tests pass (no regressions in core logic)
- [x] All three subcommands work: validate, write, update
- [x] New command structure: `./iw review-state <subcommand>` works correctly
- [x] Update command works for all field types and merge modes
- [x] Workflows can replace their if-else/jq logic with simple update commands
- [x] Merged state always passes validation
- [x] Error messages are clear and actionable
- [x] Public API documentation is complete and accurate
- [x] This is documented as v1.0 of the public command API

## Notes

**Key design decision:** Array merge semantics
- Default mode replaces (like write-review-state would)
- Append mode adds items (what workflows typically need)
- Clear mode removes all (for explicit cleanup)

This gives workflows full control without forcing them to track existing state.

**Command structure decision:**
- Clean restructure as subcommands under `review-state` - no workflows published yet
- Delete old command files (`validate-review-state.scala`, `write-review-state.scala`)
- Main command dispatches to subcommands: validate, write, update
- Update workflows before publishing

**Clean restructure (no backward compatibility):**
- No workflows currently use `validate-review-state` or `write-review-state` (confirmed by Michal)
- Can delete old commands and create new structure cleanly
- Will update workflows before publishing
- No schema changes needed

**Public API contract:**
- Document all command interfaces in `docs/commands/review-state.md`
- Define backward compatibility guarantees for future changes
- This IS the first public version, so establish the contract now
