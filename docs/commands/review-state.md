# Review State Commands

Version: 1.0 (Public API)

The `review-state` command family manages review-state.json files that track workflow progress and provide information to the iw-cli dashboard.

## Overview

Review state files are JSON documents that workflows use to communicate their current state, progress, and available actions to users through the dashboard UI. The schema is defined in `schemas/review-state.schema.json`.

**Command structure:**
```bash
./iw review-state <subcommand> [options]
```

**Available subcommands:**
- `validate` - Validate a review-state.json file against the schema
- `write` - Create a new review-state.json from scratch
- `update` - Update an existing review-state.json with partial changes

## Schema Contract

Review state files follow JSON Schema v2 (field: `version: 2`). The schema defines:

- **Required fields:** `version`, `issue_id`, `artifacts`, `last_updated`
- **Optional fields:** `status`, `display`, `badges`, `task_lists`, `needs_attention`, `message`, `available_actions`, `pr_url`, `git_sha`, `phase_checkpoints`

See `schemas/review-state.schema.json` for complete field definitions and constraints.

## Subcommand: `validate`

Validates a review-state.json file against the formal schema.

### Usage

```bash
./iw review-state validate <file-path>
./iw review-state validate --stdin
```

### Arguments

| Argument | Description |
|----------|-------------|
| `file-path` | Path to the review-state.json file to validate |
| `--stdin` | Read JSON from standard input instead of a file |

### Exit Codes

- `0` - File is valid
- `1` - File is invalid or not found

### Examples

```bash
# Validate a file
./iw review-state validate project-management/issues/IW-42/review-state.json

# Validate from stdin
cat review-state.json | ./iw review-state validate --stdin
```

### Output

On success:
```
✓ Review state is valid
```

On validation errors:
```
✗ Review state validation failed
  display.type: must be one of: info, success, warning, error, progress
  artifacts[0]: missing required property 'label'
```

## Subcommand: `write`

Creates a new review-state.json file from scratch with specified fields. Auto-populates `issue_id` (from git branch), `git_sha` (from HEAD), and `last_updated` (current timestamp).

### Usage

```bash
./iw review-state write [options]
./iw review-state write --from-stdin --output <path>
```

### Arguments

| Flag | Type | Description |
|------|------|-------------|
| `--status <value>` | String | Optional machine-readable status identifier |
| `--display-text <value>` | String | Primary display text for status badge |
| `--display-subtext <value>` | String | Optional secondary display text |
| `--display-type <value>` | Enum | Display type: `info`, `success`, `warning`, `error`, `progress` |
| `--badge <label:type>` | Repeatable | Contextual badge (label:type) |
| `--task-list <label:path>` | Repeatable | Task list reference (label:path) |
| `--needs-attention` | Flag | Indicates workflow needs human input |
| `--message <value>` | String | Prominent notification message |
| `--artifact <label:path[=category]>` | Repeatable | Artifact with optional category |
| `--action <id:label:skill>` | Repeatable | Available action (id:label:skill) |
| `--pr-url <value>` | String | Pull request URL |
| `--checkpoint <phase:sha>` | Repeatable | Phase checkpoint (phase:sha) |
| `--git-sha <value>` | String | Override auto-detected git SHA |
| `--output <path>` | String | Output file path (default: auto-detect from issue_id) |
| `--from-stdin` | Flag | Read full JSON from stdin instead of flags |
| `--issue-id <value>` | String | Issue ID override (default: inferred from branch) |
| `--version <value>` | Integer | Schema version (default: 2) |

### Exit Codes

- `0` - File written successfully
- `1` - Validation failed or missing required arguments

### Examples

```bash
# Minimal write (auto-detects issue ID from branch)
./iw review-state write --display-text "Planning" --display-type info

# Write with explicit path and issue ID
./iw review-state write \
  --issue-id IW-42 \
  --display-text "Implementing" \
  --display-type progress \
  --artifact "Analysis:analysis.md=input" \
  --artifact "Tasks:phase-02-tasks.md" \
  --output project-management/issues/IW-42/review-state.json

# Write from stdin
cat state.json | ./iw review-state write --from-stdin --output review-state.json
```

### Behavior

- Validates constructed JSON before writing
- Creates parent directories if needed
- Auto-populates: `issue_id` (from git branch), `git_sha` (from HEAD), `last_updated` (current UTC timestamp)
- Exits with error if validation fails (no file written)

## Subcommand: `update`

Updates an existing review-state.json file with partial changes. Merges provided updates with existing state, auto-updates `last_updated`, and validates the result.

### Usage

```bash
./iw review-state update [options]
```

### Arguments

All flags from `write` command, plus:

#### Array Merge Modes

| Flag Pattern | Mode | Behavior |
|-------------|------|----------|
| `--artifact <value>` | Replace | Replaces entire array with provided values |
| `--append-artifact <value>` | Append | Adds provided values to existing array |
| `--clear-artifacts` | Clear | Removes all array items |

Same pattern applies to: `--badge`/`--append-badge`/`--clear-badges`, `--task-list`/`--append-task-list`/`--clear-task-lists`, `--action`/`--append-action`/`--clear-actions`, `--checkpoint`/`--append-checkpoint`/`--clear-checkpoints`

#### Clear Flags

| Flag | Effect |
|------|--------|
| `--clear-status` | Removes `status` field |
| `--clear-message` | Removes `message` field |
| `--clear-pr-url` | Removes `pr_url` field |
| `--clear-display` | Removes entire `display` object |
| `--clear-display-subtext` | Removes `display.subtext` field |
| `--clear-needs-attention` | Removes `needs_attention` field |

### Exit Codes

- `0` - File updated successfully
- `1` - File not found, validation failed, or invalid arguments

### Examples

```bash
# Simple scalar update
./iw review-state update --display-text "Implementing"

# Append to existing artifacts without replacing
./iw review-state update --append-artifact "Phase2:phase-02-tasks.md"

# Clear a field
./iw review-state update --clear-message

# Multiple updates at once
./iw review-state update \
  --display-text "Review Ready" \
  --display-type success \
  --needs-attention \
  --message "Phase 3 complete, ready for review"

# Update with explicit input path
./iw review-state update \
  --input project-management/issues/IW-42/review-state.json \
  --display-text "Completed"
```

### Behavior

- Reads existing file, errors if not found
- Merges updates with existing JSON:
  - **Scalar fields:** Provided value replaces existing
  - **Display object:** Merges individual properties (can update just `display.text`, keeps `display.type`)
  - **Arrays:** Behavior depends on mode (replace/append/clear)
  - **Special fields:**
    - `last_updated`: Always updated to current timestamp
    - `git_sha`: Preserved unless explicitly overridden with `--git-sha`
    - `version`, `issue_id`: Always preserved
- Validates merged result before writing
- Writes back to same location (or `--input` path)

## Backward Compatibility

**Version:** 1.0 (First public release)

This is the first public release of the `review-state` commands. Previous commands (`validate-review-state`, `write-review-state`) were internal and not published to workflows.

### Compatibility Guarantees

- **Minor version changes** (1.x → 1.y): Backward compatible
  - New optional flags may be added
  - New optional fields may be added to schema
  - Existing flags and fields remain unchanged

- **Major version changes** (1.x → 2.x): May include breaking changes
  - Flag renames or removals
  - Schema breaking changes
  - Exit code changes

### Deprecation Policy

When breaking changes are required:
1. New behavior added alongside old (deprecated) behavior
2. Deprecation warnings shown for at least one minor version
3. Old behavior removed in next major version
4. Migration guide provided in release notes

## Common Workflows

### 1. Create initial state for new issue

```bash
./iw review-state write \
  --display-text "Planning" \
  --display-type info \
  --artifact "Analysis:project-management/issues/IW-42/analysis.md=input" \
  --artifact "Tasks:project-management/issues/IW-42/tasks.md" \
  --action "start:Start Implementation:ag-implement"
```

### 2. Update progress during implementation

```bash
./iw review-state update \
  --display-text "Implementing Phase 2" \
  --display-subtext "Step 3 of 5" \
  --append-artifact "Phase2:project-management/issues/IW-42/phase-02-tasks.md"
```

### 3. Mark ready for review

```bash
./iw review-state update \
  --display-text "Ready for Review" \
  --display-type success \
  --needs-attention \
  --message "Implementation complete, awaiting code review" \
  --pr-url "https://github.com/org/repo/pull/123"
```

### 4. Validate before committing

```bash
./iw review-state validate project-management/issues/IW-42/review-state.json
if [ $? -eq 0 ]; then
  git add project-management/issues/IW-42/review-state.json
  git commit -m "Update review state"
fi
```

### 5. Clear temporary fields after completion

```bash
./iw review-state update \
  --display-text "Completed" \
  --display-type success \
  --clear-message \
  --clear-needs-attention
```

## Troubleshooting

### Issue ID inference fails

**Error:** `Cannot infer issue ID: Cannot extract issue ID from branch 'main'`

**Solution:** Either:
1. Switch to an issue branch (e.g., `IW-42` or `IW-42-description`)
2. Use `--issue-id` flag explicitly: `./iw review-state write --issue-id IW-42 ...`

### Validation fails after update

**Error:** `Updated review state failed validation`

**Cause:** The merged result doesn't satisfy schema requirements (e.g., `display.text` without `display.type`)

**Solution:** Provide all required fields when updating partial objects:
```bash
./iw review-state update --display-text "New" --display-type "info"
```

### File not found for update

**Error:** `Review state file not found: project-management/issues/IW-42/review-state.json`

**Cause:** No existing state file to update

**Solution:** Use `write` instead of `update` for initial creation:
```bash
./iw review-state write --display-text "Initial" --display-type "info"
```

## See Also

- Schema definition: `schemas/review-state.schema.json`
- Dashboard rendering: `.iw/core/dashboard/`
- Example states: `.iw/core/test/resources/review-state/`
