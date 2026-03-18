# Phase 5: WorktreeSummary redesign, worktrees command, and formatter

## Goals

Redesign `WorktreeSummary` to include machine-consumable fields (`activity`, `workflowType`, progress, URLs, timestamps) alongside the existing display-oriented fields. Update `worktrees.scala` to populate these from `ServerState` caches. Enhance `WorktreesFormatter` to display activity, workflow type, and progress information.

## Scope

### In Scope

1. **WorktreeSummary model redesign** — Add new fields, rename `reviewDisplay` → `workflowDisplay`
2. **worktrees.scala command** — Extract new fields from `ServerState` caches (`reviewStateCache`, `progressCache`, `issueCache`, `prCache`, worktree registration)
3. **WorktreesFormatter** — Show activity indicator, workflow type abbreviation, and phase progress in human-readable output
4. **JSON output** — New fields automatically serialized via `derives ReadWriter` (snake_case via upickle)
5. **Unit tests** — Update existing `WorktreesFormatterTest` and add tests for new formatting
6. **E2E tests** — Test `iw worktrees --json` output includes new fields

### Out of Scope

- Writing `activity`/`workflowType` at workflow transition points (separate issue)
- Dashboard server changes (reads WorktreeSummary independently)
- Changes to `ServerStateCodec` (caches already carry all needed data)

## Dependencies on Prior Phases

- **Phase 2**: `ReviewState` has `activity: Option[String]` and `workflowType: Option[String]` fields
- **Phase 4**: CLI commands can write/update these fields (enables E2E testing)
- `WorkflowProgress` (already exists in `progressCache`) provides `currentPhase`, `totalPhases`, `overallCompleted`, `overallTotal`
- `IssueData.url` provides issue URL
- `PullRequestData.url` provides PR URL
- `WorktreeRegistration.registeredAt` and `lastSeenAt` provide timestamps

## Approach

### Step 1: Redesign WorktreeSummary model

Current (`WorktreeSummary.scala`):
```scala
case class WorktreeSummary(
  issueId: String,
  path: String,
  issueTitle: Option[String],
  issueStatus: Option[String],
  prState: Option[String],
  reviewDisplay: Option[String],
  needsAttention: Boolean
) derives ReadWriter
```

Target:
```scala
case class WorktreeSummary(
  // Identity
  issueId: String,
  path: String,
  // Issue metadata (from issueCache)
  issueTitle: Option[String],
  issueStatus: Option[String],
  // URLs
  issueUrl: Option[String],
  prUrl: Option[String],
  // PR state (from prCache)
  prState: Option[String],
  // Workflow state (from reviewStateCache)
  activity: Option[String],
  workflowType: Option[String],
  workflowDisplay: Option[String],   // renamed from reviewDisplay
  needsAttention: Boolean,
  // Progress (from progressCache)
  currentPhase: Option[Int],
  totalPhases: Option[Int],
  completedTasks: Option[Int],
  totalTasks: Option[Int],
  // Timestamps (from WorktreeRegistration)
  registeredAt: Option[String],
  lastActivityAt: Option[String]
) derives ReadWriter
```

Key changes:
- **Renamed**: `reviewDisplay` → `workflowDisplay`
- **Added**: `activity`, `workflowType`, `issueUrl`, `prUrl`, `currentPhase`, `totalPhases`, `completedTasks`, `totalTasks`, `registeredAt`, `lastActivityAt`

### Step 2: Update worktrees.scala

Populate new fields from caches:
- `activity` / `workflowType` from `state.reviewStateCache.get(issueId).map(_.state.activity)` etc.
- `issueUrl` from `state.issueCache.get(issueId).map(_.data.url)`
- `prUrl` from `state.prCache.get(issueId).map(_.pr.url)`  OR `state.reviewStateCache` pr_url field
- `currentPhase` / `totalPhases` from `state.progressCache.get(issueId).map(_.progress.currentPhase)` etc.
- `completedTasks` / `totalTasks` from `state.progressCache.get(issueId).map(_.progress.overallCompleted)` etc.
- `registeredAt` from `wt.registeredAt.toString`
- `lastActivityAt` from `wt.lastSeenAt.toString`

### Step 3: Update WorktreesFormatter

Add to human-readable output:
- Activity indicator: `▶` for "working", `⏸` for "waiting", nothing if absent
- Workflow type abbreviation: `AG` / `WF` / `DX`, nothing if absent
- Progress: `Phase 2/4` and/or `5/12 tasks` if available
- Keep compact — this is CLI table output

### Step 4: Update JSON serialization

upickle `derives ReadWriter` with macro derivation handles snake_case field names automatically. The renamed field `workflowDisplay` will serialize as `workflowDisplay` in JSON (camelCase, matching upickle default). Verify this is acceptable (the JSON output is for machine consumption by scripts).

Note: upickle macro derivation uses field names as-is (camelCase). This is consistent with how `WorktreeSummary` has always serialized (`issueId`, `issueTitle`, etc.).

## Files to Modify

| File | Change |
|------|--------|
| `.iw/core/model/WorktreeSummary.scala` | Add new fields, rename `reviewDisplay` → `workflowDisplay` |
| `.iw/commands/worktrees.scala` | Populate new fields from caches |
| `.iw/core/output/WorktreesFormatter.scala` | Display activity, workflow type, progress |
| `.iw/core/test/WorktreesFormatterTest.scala` | Update existing tests, add new tests for new fields |

## Testing Strategy

### Unit Tests (WorktreesFormatterTest)
- Update all existing tests to use new `WorktreeSummary` constructor (add new fields as `None`/defaults)
- Test activity indicator display (`▶` for working, `⏸` for waiting)
- Test workflow type abbreviation display (AG/WF/DX)
- Test progress display (phase and task counts)
- Test combined display with all new fields
- Test display when new fields are absent (graceful degradation)

### E2E Tests (BATS)
- `iw worktrees --json` with review-state containing `activity` and `workflow_type` → verify fields in JSON output
- Verify renamed field (`workflowDisplay` instead of `reviewDisplay`) in JSON output

## Acceptance Criteria

- [ ] `WorktreeSummary` has all new fields per analysis design
- [ ] `reviewDisplay` renamed to `workflowDisplay` everywhere
- [ ] `worktrees.scala` populates all new fields from caches
- [ ] `WorktreesFormatter` shows activity, workflow type, and progress
- [ ] `iw worktrees --json` includes new fields in output
- [ ] All existing tests updated and passing
- [ ] New tests cover all new formatting paths
- [ ] No compilation warnings
- [ ] All E2E tests pass
