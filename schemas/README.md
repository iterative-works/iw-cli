# Review State Schema

Formal JSON Schema defining the contract for `review-state.json` files used
by iw-cli workflow tools and the dashboard.

## Schema Location

- **File:** `schemas/review-state.schema.json`
- **Format:** JSON Schema Draft-07

## Design Philosophy

The schema separates **structural display concerns** from **semantic workflow concerns**:

- **iw-cli (dashboard)** owns structure: "display a badge with this text and color"
- **Workflows** own semantics: "what text to show, what color to use, when"

The dashboard renders exactly what it's given - no interpretation of workflow states.

## Versioning Policy

The `version` field in each `review-state.json` file tracks the schema version.

- **Current version:** 1
- **Version bumps** are required only for **breaking changes**:
  - Removing a required field
  - Changing the type of an existing field
  - Renaming a field
- **Adding optional fields** does NOT require a version bump.

## Field Summary

### Required Fields

| Field          | Type    | Description                                  |
|----------------|---------|----------------------------------------------|
| `version`      | integer | Schema version (minimum 1)                   |
| `issue_id`     | string  | Issue tracker identifier (e.g., "IW-136")    |
| `artifacts`    | array   | Workflow artifacts (may be empty)            |
| `last_updated` | string  | ISO 8601 date-time of last modification      |

### Optional Fields

| Field               | Type             | Description                                        |
|---------------------|------------------|----------------------------------------------------|
| `status`            | string           | Machine-readable workflow state (for workflow use) |
| `display`           | object           | Presentation instructions for status badge         |
| `badges`            | array            | Additional contextual indicators                   |
| `task_lists`        | array            | Files containing task checkboxes for progress      |
| `needs_attention`   | boolean          | Flag for attention-grabbing visual indicator       |
| `message`           | string           | Prominent notification for the user                |
| `pr_url`            | string / null    | Pull request URL                                   |
| `git_sha`           | string           | Latest relevant commit SHA                         |
| `phase_checkpoints` | object           | Map of phase numbers to checkpoint data            |
| `available_actions` | array            | Actions the user can take from dashboard           |

### Display Object

Controls how the status badge is rendered:

| Field     | Type   | Required | Description                                           |
|-----------|--------|----------|-------------------------------------------------------|
| `text`    | string | Yes      | Primary label shown in the badge                      |
| `subtext` | string | No       | Secondary information shown beneath                   |
| `type`    | enum   | Yes      | Display category: info, success, warning, error, progress |

### Badge Object

Additional contextual indicators:

| Field   | Type   | Required | Description                                           |
|---------|--------|----------|-------------------------------------------------------|
| `label` | string | Yes      | Short text shown on the badge                         |
| `type`  | enum   | Yes      | Color category: info, success, warning, error, progress |

### Artifact Object

Workflow artifacts for user review:

| Field      | Type   | Required | Description                                        |
|------------|--------|----------|----------------------------------------------------|
| `label`    | string | Yes      | Human-readable name (e.g., "Analysis")             |
| `path`     | string | Yes      | Path relative to project root                      |
| `category` | string | No       | Optional grouping category (e.g., "input", "output") |

**Category conventions:**
- `input` - Planning artifacts (analysis, context, tasks)
- `output` - Execution results (log, reviews, verification)
- Workflows may define additional categories as needed

### Task List Object

References to markdown files with task checkboxes:

| Field   | Type   | Required | Description                              |
|---------|--------|----------|------------------------------------------|
| `label` | string | Yes      | Human-readable name (e.g., "Phase 2")    |
| `path`  | string | Yes      | Path to markdown file with checkboxes    |

### Action Object

Actions available from the dashboard:

| Field   | Type   | Required | Description                              |
|---------|--------|----------|------------------------------------------|
| `id`    | string | Yes      | Machine-readable identifier              |
| `label` | string | Yes      | Human-readable button label              |
| `skill` | string | Yes      | Skill to invoke when triggered           |

### Phase Checkpoint Object

Recovery data for revert-to-phase functionality:

| Field         | Type   | Required | Description                              |
|---------------|--------|----------|------------------------------------------|
| `context_sha` | string | Yes      | Git blob SHA of phase context file       |
