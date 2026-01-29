# Refactoring R1: Separate display structure from workflow semantics

**Phase:** 3
**Created:** 2026-01-29
**Status:** Planned

## Decision Summary

The current schema conflates structural display concerns with workflow semantics. The dashboard interprets workflow-specific status values, and the schema contains fields that duplicate information available from other sources (git) or encode workflow-specific concepts (batch_mode).

**Separation of concerns:**
- **iw-cli (dashboard)** owns structure: "here are the affordances for displaying workflow state"
- **kanon (workflow)** owns semantics: "what to show, when, and why"

The dashboard should render what it's given without interpreting workflow-specific vocabulary.

## Field Intent Documentation

This section documents the intent of each field in the schema. All future development should align with these decisions.

### Identity & Metadata

#### `version` (integer, required)
**Intent:** Schema version for compatibility checking. Increment only for breaking changes.

#### `issue_id` (string, required)
**Intent:** Links the review state to the issue being worked on. Used for file paths and dashboard grouping.

#### `last_updated` (string, required)
**Intent:** ISO 8601 timestamp of last modification. Used for cache invalidation and staleness detection.

#### `git_sha` (string, optional)
**Intent:** Records the commit SHA when state was written. Useful for debugging and audit trails. Dashboard may display this for reference.

#### `pr_url` (string or null, optional)
**Intent:** Link to the pull request. Dashboard renders this as a clickable "View PR" button.

### Display Control

#### `display` (object, optional)
**Intent:** Workflow-controlled presentation instructions. The dashboard renders exactly what it's told without interpretation.

Structure:
```json
"display": {
  "text": "Implementing",           // required - badge label
  "subtext": "Phase 2 of 3",        // optional - secondary context
  "type": "progress"                // required - color category
}
```

- `text`: Primary status label shown in the badge. Workflow decides the wording.
- `subtext`: Secondary information shown smaller/muted beneath the badge. For context that supports the main status.
- `type`: Display category that maps to CSS styling. Closed enum:
  - `info` - neutral information (blue)
  - `success` - positive/complete state (green)
  - `warning` - needs attention (yellow/orange)
  - `error` - problem/blocked state (red)
  - `progress` - work in progress (blue/animated)

**Dashboard behavior:** If `display` is absent, no status badge is shown. Dashboard never interprets or transforms the text.

#### `message` (string, optional)
**Intent:** Prominent notification for the user. This is NOT status metadata - it's an important communication that should catch the user's eye.

Examples:
- "Please review the options in analysis.md and provide direction"
- "Build failed - check CI logs"
- "All phases complete - ready for final review"

**Dashboard behavior:** Rendered prominently, visually distinct from status badge. This is a call-to-action or critical information.

#### `badges` (array, optional)
**Intent:** Additional contextual indicators controlled by the workflow. Allows workflow to add arbitrary labels without dashboard needing to understand them.

Structure:
```json
"badges": [
  {"label": "Batch", "type": "info"},
  {"label": "TDD", "type": "success"},
  {"label": "Experimental", "type": "warning"}
]
```

- `label`: Short text shown on the badge
- `type`: Color category (same enum as `display.type`)

**Dashboard behavior:** Rendered as small secondary badges. No semantic interpretation - just label + color.

#### `needs_attention` (boolean, optional)
**Intent:** Flag indicating the workflow has stopped and needs human input. Can be set by a Claude hook when the agent stops to ask a question.

**Dashboard behavior:** Visual indicator (icon, border, highlight) to draw user's eye to this card.

### Progress Tracking

#### `task_lists` (array, optional)
**Intent:** Tells the dashboard which files contain task checkboxes for progress computation. The workflow controls which files matter; the dashboard computes progress from those files.

Structure:
```json
"task_lists": [
  {"label": "Phase 2 Tasks", "path": "project-management/issues/IW-136/phase-02-tasks.md"},
  {"label": "Refactoring R1", "path": "project-management/issues/IW-136/refactor-phase-03-R1.md"}
]
```

- `label`: Human-readable name for the task list
- `path`: Relative path from project root to the markdown file

**Dashboard behavior:** Reads each file, counts `- [x]` vs `- [ ]` checkboxes, computes and displays progress. If multiple files, may aggregate or show separately.

**Benefits:**
- Workflow can evolve without breaking progress tracking
- Supports multiple concurrent task lists (phases, refactorings)
- Dashboard doesn't need to know naming conventions

### Artifacts & Actions

#### `artifacts` (array, required)
**Intent:** Documents produced during the workflow that the user may want to review.

Structure:
```json
"artifacts": [
  {"label": "Analysis", "path": "project-management/issues/IW-136/analysis.md"},
  {"label": "Implementation Log", "path": "project-management/issues/IW-136/implementation-log.md"}
]
```

**Dashboard behavior:** Rendered as clickable links. Artifact content served via artifact endpoint.

#### `available_actions` (array, optional)
**Intent:** Actions the user can take from the dashboard. Workflow tells dashboard what buttons to show.

Structure:
```json
"available_actions": [
  {"id": "verify", "label": "Verify Phase", "skill": "iterative-works:ag-verify"},
  {"id": "continue", "label": "Continue Implementation", "skill": "iterative-works:ag-implement"}
]
```

- `id`: Machine identifier for the action
- `label`: Button text shown to user
- `skill`: Skill identifier to invoke when clicked

**Dashboard behavior:** Render as action buttons. When clicked, invoke the specified skill (exact mechanism TBD - may copy command to clipboard, open terminal, etc.)

### Machine State (Workflow Internal)

#### `status` (string, optional)
**Intent:** Machine-readable workflow state identifier for workflow tools. NOT used by dashboard for display - that's what `display` is for.

Examples: `implementing`, `awaiting_review`, `phase_merged`, `all_complete`

**Dashboard behavior:** Ignored for display purposes. May be logged for debugging.

#### `phase_checkpoints` (object, optional)
**Intent:** Internal workflow data for revert-to-phase functionality. Maps phase numbers to context file SHAs.

**Dashboard behavior:** Not displayed. Internal workflow bookkeeping.

## Fields to Remove

### `branch`
**Reason:** Redundant with git. Dashboard gets branch from git status, not review-state.json.

### `batch_mode`
**Reason:** Workflow-specific concept. Replaced by `badges` - workflow can add `{"label": "Batch", "type": "info"}` if it wants to indicate batch mode.

### `step`
**Reason:** Workflow-specific semantic. Workflow can put step info in `display.subtext` if needed.

### `phase`
**Reason:** Redundant with `task_lists`. Dashboard computes current phase from task file analysis. Workflow can put phase info in `display.subtext`.

### `knownStatuses` (in validator)
**Reason:** Semantic knowledge that belongs in workflow, not iw-cli. Remove entirely - no warnings for unknown status values.

## Schema Changes Summary

**Before (v1):**
```json
{
  "version": 1,
  "issue_id": "IW-136",
  "status": "implementing",        // required, interpreted by dashboard
  "phase": 2,                      // redundant
  "step": "implementation",        // semantic
  "branch": "IW-136",              // redundant with git
  "batch_mode": true,              // workflow-specific
  "message": "...",
  "artifacts": [...],
  "last_updated": "..."
}
```

**After (v2):**
```json
{
  "version": 2,
  "issue_id": "IW-136",
  "status": "implementing",        // optional, machine use only
  "display": {
    "text": "Implementing",
    "subtext": "Phase 2 of 3",
    "type": "progress"
  },
  "badges": [
    {"label": "Batch", "type": "info"}
  ],
  "task_lists": [
    {"label": "Phase 2", "path": "project-management/issues/IW-136/phase-02-tasks.md"}
  ],
  "needs_attention": false,
  "message": "...",
  "artifacts": [...],
  "available_actions": [...],
  "pr_url": "https://...",
  "git_sha": "abc123",
  "phase_checkpoints": {...},
  "last_updated": "..."
}
```

## Tasks

### Schema Update
- [ ] [impl] Bump version to 2
- [ ] [impl] Make `status` field optional
- [ ] [impl] Remove `branch`, `batch_mode`, `step`, `phase` fields
- [ ] [impl] Add `display` object definition with `text`, `subtext`, `type`
- [ ] [impl] Add `badges` array definition
- [ ] [impl] Add `task_lists` array definition
- [ ] [impl] Add `needs_attention` boolean definition
- [ ] [impl] Define `type` enum: info, success, warning, error, progress
- [ ] [impl] Add comprehensive field descriptions documenting intent
- [ ] [impl] Update/add test fixtures for v2 schema

### Validator Update
- [ ] [impl] Remove `knownStatuses` list entirely
- [ ] [impl] Remove status value interpretation/warnings
- [ ] [impl] Make `status` optional
- [ ] [impl] Remove validation for `branch`, `batch_mode`, `step`, `phase`
- [ ] [impl] Add `display` object validation
- [ ] [impl] Add `display.type` enum validation
- [ ] [impl] Add `badges` array validation
- [ ] [impl] Add `task_lists` array validation
- [ ] [impl] Add `needs_attention` boolean validation
- [ ] [impl] Add unit tests for new field validation
- [ ] [impl] Update existing tests for v2 schema

### Builder Update
- [ ] [impl] Remove `branch`, `batchMode`, `step`, `phase` from BuildInput
- [ ] [impl] Add `display: Option[Display]` where Display(text, subtext?, type)
- [ ] [impl] Add `badges: List[Badge]` where Badge(label, type)
- [ ] [impl] Add `taskLists: List[TaskList]` where TaskList(label, path)
- [ ] [impl] Add `needsAttention: Option[Boolean]`
- [ ] [impl] Update JSON construction for new fields
- [ ] [impl] Add unit tests for building with new fields
- [ ] [impl] Update existing builder tests

### Command Update
- [ ] [impl] Remove `--branch`, `--batch-mode`, `--step`, `--phase` flags
- [ ] [impl] Add `--display-text`, `--display-subtext`, `--display-type` flags
- [ ] [impl] Add `--badge <label:type>` repeatable flag
- [ ] [impl] Add `--task-list <label:path>` repeatable flag
- [ ] [impl] Add `--needs-attention` boolean flag
- [ ] [impl] Update usage comments with intent documentation
- [ ] [impl] Add E2E tests for new flags

### Dashboard Update
- [ ] [impl] Remove `statusBadgeClass()` from WorktreeListView
- [ ] [impl] Remove `formatStatusLabel()` from WorktreeListView
- [ ] [impl] Add `displayTypeClass(type: String): String` for type → CSS mapping
- [ ] [impl] Update WorktreeCardRenderer to render `display` object
- [ ] [impl] Add rendering for `display.subtext`
- [ ] [impl] Add rendering for `badges` array
- [ ] [impl] Add rendering for `needs_attention` indicator
- [ ] [impl] Implement `task_lists` → progress computation
- [ ] [impl] Remove hardcoded phase file discovery patterns
- [ ] [impl] Add fallback for v1 files (no display = no badge)
- [ ] [impl] Ensure `message` renders prominently

### ReviewState Model Update
- [ ] [impl] Add `Display` case class: `Display(text: String, subtext: Option[String], displayType: String)`
- [ ] [impl] Add `Badge` case class: `Badge(label: String, badgeType: String)`
- [ ] [impl] Add `TaskList` case class: `TaskList(label: String, path: String)`
- [ ] [impl] Update `ReviewState` case class with new fields
- [ ] [impl] Remove `status`, `phase` from ReviewState (dashboard doesn't need them)
- [ ] [impl] Update ReviewStateService to parse new structure

### Progress Service Update
- [ ] [impl] Add method to compute progress from `task_lists`
- [ ] [impl] Refactor to accept file paths instead of discovering them
- [ ] [impl] Keep existing discovery as fallback for v1/legacy
- [ ] [impl] Add tests for task_lists-based progress

## Verification

- [ ] All existing tests pass (with appropriate updates)
- [ ] v1 schema files still validate (backward compatible parsing)
- [ ] v2 schema files validate correctly
- [ ] Dashboard renders v1 files gracefully (no badge if no display)
- [ ] Dashboard renders v2 files with full feature set
- [ ] `write-review-state` supports all new flags
- [ ] No workflow-specific vocabulary in iw-cli code
- [ ] `knownStatuses` completely removed
- [ ] Progress computed from `task_lists` when provided
- [ ] `needs_attention` indicator visible when set
- [ ] `badges` render correctly
- [ ] `message` renders prominently
- [ ] Field intent documentation in schema descriptions
