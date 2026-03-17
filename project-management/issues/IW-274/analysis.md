# Analysis: IW-274 — Add activity and workflow_type to review-state schema and redesign WorktreeSummary

## Problem Statement

The `./iw worktrees --json` output cannot distinguish worktrees with actively running agents from those waiting for human input. This blocks automated scheduling (e.g., "max 3 concurrent agents"). Additionally:

- `workflow_type` is read by external tooling (`batch-implement.sh`) but never written — the field doesn't exist in the schema.
- No review state is written between worktree creation and `phase-start`, making the triage/analysis/clarification stage invisible.
- `WorktreeSummary` is display-oriented and lacks machine-consumable fields.

## Architecture Design

### Affected Layers

```
1. Schema Layer         — review-state.schema.json (JSON Schema contract)
2. Domain Model Layer   — ReviewState.scala, WorktreeSummary.scala (pure types)
3. Validation Layer     — ReviewStateValidator.scala (pure logic)
4. Merge Layer          — ReviewStateUpdater.scala, ReviewStateBuilder.scala (pure logic)
5. CLI/Adapter Layer    — review-state/write.scala, review-state/update.scala, worktrees.scala
6. Output Layer         — WorktreesFormatter.scala (CLI formatting)
7. Documentation Layer  — llms.txt, review-state schema skill reference
```

Dependencies flow inward: CLI → Adapters → Model. Schema is the source of truth.

### Data Flow

```
review-state.json (file)
    ↓ read
ReviewStateValidator (validates, including new enums)
ReviewStateUpdater (merges new fields)
ReviewStateBuilder (creates new state with new fields)
    ↓
ReviewState (domain model — gains activity, workflowType)
    ↓
CachedReviewState → ServerState.reviewStateCache
    ↓
worktrees.scala reads ServerState
    ├─ extracts new fields from reviewStateCache + progressCache + WorktreeRegistration
    └─ builds redesigned WorktreeSummary
    ↓
WorktreesFormatter (human) or JSON output (machine)
```

---

## Layer 1: Schema — `schemas/review-state.schema.json`

### Changes

Add two new optional properties to the root object:

#### `activity`
- **Type:** `string`
- **Enum:** `["working", "waiting"]`
- **Description:** Binary signal for scheduling. `"working"` = agent actively processing. `"waiting"` = blocked on human action.
- **Optional** — absent means activity unknown (legacy/pre-triage state).

#### `workflow_type`
- **Type:** `string`
- **Enum:** `["agile", "waterfall", "diagnostic"]`
- **Description:** Which workflow is running. Already expected by `batch-implement.sh`.
- **Optional** — absent means workflow type unknown.

### Status Vocabulary

Document canonical `status` values in the schema description. Add description text listing known values:
- `triage` — initial classification
- `analyzing` — creating analysis
- `analysis_ready` — analysis complete, awaiting review/task generation
- `creating_tasks` — generating tasks
- `tasks_ready` — tasks complete, ready for implementation
- `context_ready` — phase context generated
- `implementing` — phase implementation in progress
- `awaiting_review` — PR created, awaiting code review
- `review_failed` — review rejected, needs rework
- `phase_merged` — phase PR merged
- `all_complete` — all phases done

These are informational (the field remains a free-form string to avoid breaking existing consumers).

### Validation Impact

- `AllowedRootProperties` in `ReviewStateValidator` must include `"activity"` and `"workflow_type"`.
- Enum validation for both fields.

### Estimate

- Optimistic: 0.5h
- Most likely: 1h
- Pessimistic: 1.5h

---

## Layer 2: Domain Model — Pure Types

### 2a. `ReviewState.scala`

Add two optional fields to `ReviewState`:

```scala
case class ReviewState(
  display: Option[Display],
  badges: Option[List[Badge]],
  taskLists: Option[List[TaskList]],
  needsAttention: Option[Boolean],
  message: Option[String],
  artifacts: List[ReviewArtifact],
  activity: Option[String],       // NEW: "working" | "waiting"
  workflowType: Option[String]    // NEW: "agile" | "waterfall" | "diagnostic"
)
```

Impact on `ServerStateCodec.scala`: The codec uses `macroRW` for `ReviewState` — needs update to handle snake_case `workflow_type` → camelCase `workflowType` mapping (check existing pattern with `Display` and `Badge` custom readers).

### 2b. `WorktreeSummary.scala` — Redesign

Replace current fields with richer structure:

```scala
case class WorktreeSummary(
  // Identity
  issueId: String,
  path: String,

  // Issue metadata (from issueCache)
  issueTitle: Option[String],
  issueStatus: Option[String],

  // URLs (from prCache + config)
  issueUrl: Option[String],
  prUrl: Option[String],

  // PR state (from prCache)
  prState: Option[String],

  // Workflow state (from reviewStateCache)
  activity: Option[String],          // NEW: "working" | "waiting"
  workflowType: Option[String],      // NEW: "agile" | "waterfall" | "diagnostic"
  workflowDisplay: Option[String],   // renamed from reviewDisplay
  needsAttention: Boolean,

  // Progress (from progressCache)
  currentPhase: Option[Int],
  totalPhases: Option[Int],
  completedTasks: Option[Int],
  totalTasks: Option[Int],

  // Timestamps (from WorktreeRegistration)
  registeredAt: Option[String],      // ISO 8601
  lastActivityAt: Option[String]     // ISO 8601
) derives ReadWriter
```

**Fields kept:** `issueId`, `path`, `issueTitle`, `issueStatus`, `prState`, `needsAttention`
**Renamed:** `reviewDisplay` → `workflowDisplay`
**Added:** `activity`, `workflowType`, `currentPhase`, `totalPhases`, `completedTasks`, `totalTasks`, `issueUrl`, `prUrl`, `registeredAt`, `lastActivityAt`

### Issue URL Source

`issueUrl` is read from `issueCache` → `IssueData.url`. This field is already populated by all tracker clients (GitHub, Linear, GitLab, YouTrack) when fetching issues. The same pattern is used by `CaskServer.scala` to populate `WorktreeStatus.issueUrl`. No new derivation logic needed.

Similarly, `prUrl` is read from `prCache` → `PullRequestData.url` (or from `reviewStateCache` → `pr_url` field).

### Estimate

- Optimistic: 1h
- Most likely: 2h
- Pessimistic: 3h

---

## Layer 3: Validation — `ReviewStateValidator.scala`

### Changes

1. Add `"activity"` and `"workflow_type"` to `AllowedRootProperties`.
2. Add enum validation for `activity`: must be one of `["working", "waiting"]`.
3. Add enum validation for `workflow_type`: must be one of `["agile", "waterfall", "diagnostic"]`.

Pattern matches existing `ValidDisplayTypes` validation.

### Estimate

- Optimistic: 0.5h
- Most likely: 0.75h
- Pessimistic: 1h

---

## Layer 4: Builder/Updater — Pure Merge Logic

### 4a. `ReviewStateBuilder.scala`

Add to `BuildInput`:
- `activity: Option[String]`
- `workflowType: Option[String]`

Add to JSON construction: write `activity` and `workflow_type` fields when present.

### 4b. `ReviewStateUpdater.scala`

Add to `UpdateInput`:
- `activity: Option[String]`
- `workflowType: Option[String]`
- `clearActivity: Boolean`
- `clearWorkflowType: Boolean`

Add merge logic following existing scalar field pattern (e.g., `status`, `message`).

### Estimate

- Optimistic: 0.5h
- Most likely: 1h
- Pessimistic: 1.5h

---

## Layer 5: CLI/Adapter — Commands

### 5a. `review-state/write.scala`

Add CLI flags:
- `--activity <working|waiting>`
- `--workflow-type <agile|waterfall|diagnostic>`

Pass to `ReviewStateBuilder.BuildInput`.

### 5b. `review-state/update.scala`

Add CLI flags:
- `--activity <working|waiting>`
- `--workflow-type <agile|waterfall|diagnostic>`
- `--clear-activity`
- `--clear-workflow-type`

Pass to `ReviewStateUpdater.UpdateInput`.

### 5c. `worktrees.scala`

Update `WorktreeSummary` construction to populate new fields from `ServerState` caches:
- `activity` and `workflowType` from `reviewStateCache`
- Progress fields from `progressCache`
- `prUrl` from `reviewStateCache` (already stored there)
- `issueUrl` from `issueCache` (`IssueData.url`)
- Timestamps from `WorktreeRegistration`

### Estimate

- Optimistic: 1h
- Most likely: 2h
- Pessimistic: 3h

---

## Layer 6: Output — `WorktreesFormatter.scala`

### Changes

Update human-readable output to show:
- Activity indicator (e.g., spinner/icon for "working", pause icon for "waiting")
- Workflow type abbreviation (AG/WF/DX)
- Progress (e.g., "Phase 2/4, 5/12 tasks")

Keep compact — this is a CLI table.

### Estimate

- Optimistic: 0.5h
- Most likely: 1h
- Pessimistic: 1.5h

---

## Layer 7: Documentation — `llms.txt` and Schema Skill

### Changes

#### 7a. `.iw/llms.txt`

Update the llms.txt to document the new `activity` and `workflow_type` fields on `ReviewState`, the redesigned `WorktreeSummary` fields, and any new doc references.

Affected doc entries:
- `WorkflowTypes` section (ReviewState gains `activity`, `workflowType`)
- `WorktreeSummary` section (new fields: activity, workflowType, progress, URLs, timestamps; renamed: reviewDisplay → workflowDisplay)

#### 7b. `.claude/skills/iterative-works/review-state-schema/` (if exists)

Update the review-state schema skill reference to include the new fields.

### Estimate

- Optimistic: 0.25h
- Most likely: 0.5h
- Pessimistic: 0.75h

---

## Out of Scope: Skill Updates

Workflow skill updates (setting `activity` and `workflow_type` at transition points in triage, analysis, phase commands) will be done separately. The CLI/schema work in this issue is prerequisite.

---

## Technical Decisions

1. **`activity` as string enum (not boolean):** Binary "working"/"waiting" is sufficient for scheduling. Using a string enum instead of boolean allows future extension without schema break.

2. **`workflow_type` as string enum:** Matches existing consumer expectations (`batch-implement.sh`). Three values cover current workflows.

3. **Schema remains optional-heavy:** Both new fields are optional. Existing review-state.json files remain valid. No version bump needed (adding optional fields is non-breaking per schema docs).

4. **WorktreeSummary is a derived view:** Not persisted — built fresh from ServerState caches each time `iw worktrees` runs. Adding fields here has zero storage cost.

5. **Codec snake_case mapping:** `workflow_type` (JSON) → `workflowType` (Scala). Must handle in `ServerStateCodec` custom reader, matching existing `Display`/`Badge` pattern.

---

## Testing Strategy

### Unit Tests (per layer)

| Layer | Test File | Coverage |
|-------|-----------|----------|
| Schema | Manual validation against schema | activity/workflow_type field presence and enum values |
| Validator | `ReviewStateValidatorTest.scala` | Valid/invalid activity values, valid/invalid workflow_type values, unknown properties still rejected |
| Builder | `ReviewStateBuilderTest.scala` | Build with activity, build with workflow_type, both absent |
| Updater | `ReviewStateUpdaterTest.scala` | Merge activity, merge workflow_type, clear operations |
| WorktreeSummary | New or existing test | Verify new fields populated correctly from mock ServerState |
| Formatter | `StatusFormatterTest.scala` or new | Activity and progress display formatting |

### E2E Tests (BATS)

- `iw review-state write --activity working --workflow-type agile` → validates output JSON
- `iw review-state update --activity waiting` → validates merged JSON
- `iw worktrees --json` → verify new fields present in output

---

## Implementation Sequence

1. **Schema** (Layer 1) — add fields to JSON Schema, no code dependencies
2. **Validator** (Layer 3) — add enum validation, depends on schema design
3. **Domain Model** (Layer 2) — add fields to ReviewState, update codec
4. **Builder/Updater** (Layer 4) — support new fields in construction and merge
5. **CLI Commands** (Layer 5a, 5b) — add flags to write/update commands
6. **WorktreeSummary Redesign** (Layer 2b) — redesign the model
7. **Worktrees Command** (Layer 5c) — populate new WorktreeSummary from caches
8. **Formatter** (Layer 6) — update human output
9. **Documentation** (Layer 7) — update llms.txt and schema skill reference

Layers 1-5b can be done incrementally with tests at each step. Layer 2b (WorktreeSummary redesign) is the largest change and should be done after the schema/model foundation is stable.

---

## Estimates Summary

| Layer | Optimistic | Most Likely | Pessimistic |
|-------|-----------|-------------|-------------|
| 1. Schema | 0.5h | 1h | 1.5h |
| 2. Domain Model | 1h | 2h | 3h |
| 3. Validator | 0.5h | 0.75h | 1h |
| 4. Builder/Updater | 0.5h | 1h | 1.5h |
| 5. CLI Commands | 1h | 2h | 3h |
| 6. Formatter | 0.5h | 1h | 1.5h |
| 7. Documentation | 0.25h | 0.5h | 0.75h |
| **Total** | **4.25h** | **8.25h** | **12.25h** |
