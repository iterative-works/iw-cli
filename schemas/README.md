# Review State Schema

Formal JSON Schema defining the contract for `review-state.json` files used
by iw-cli workflow tools and the dashboard.

## Schema Location

- **File:** `schemas/review-state.schema.json`
- **Format:** JSON Schema Draft-07

## Versioning Policy

The `version` field in each `review-state.json` file tracks the schema version.

- **Current version:** 1
- **Version bumps** are required only for **breaking changes**:
  - Removing a required field
  - Changing the type of an existing field
  - Renaming a field
- **Adding optional fields** does NOT require a version bump.
- **Adding new known status values** does NOT require a version bump (status
  is an open enum).

## Field Summary

### Required Fields

| Field          | Type    | Description                                  |
|----------------|---------|----------------------------------------------|
| `version`      | integer | Schema version (minimum 1)                   |
| `issue_id`     | string  | Issue tracker identifier (e.g., "IW-136")    |
| `status`       | string  | Current workflow status (open enum)           |
| `artifacts`    | array   | Workflow artifacts (may be empty)             |
| `last_updated` | string  | ISO 8601 date-time of last modification      |

### Optional Fields

| Field               | Type             | Description                              |
|---------------------|------------------|------------------------------------------|
| `phase`             | integer / string | Current phase number or label            |
| `step`              | string           | Current step within the phase            |
| `branch`            | string           | Git branch name                          |
| `pr_url`            | string / null    | Pull request URL, or null                |
| `git_sha`           | string           | Latest relevant commit SHA               |
| `message`           | string           | Human-readable state description         |
| `batch_mode`        | boolean          | Whether batch mode is active             |
| `phase_checkpoints` | object           | Map of phase numbers to checkpoint data  |
| `available_actions` | array            | Actions the user can take from this state|
