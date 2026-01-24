# Phase 2 Context: Load sample data for UI testing

**Issue:** IW-82
**Phase:** 2 of 5
**Story:** Load sample data for UI testing

## Goals

Enable developers to populate the dashboard with realistic sample data across multiple tracker types, PR states, and workflow progress scenarios. This provides comprehensive test coverage for UI layouts and visual states without requiring access to real issue trackers.

## Scope

### In Scope
- Expand `SampleData` object in `TestFixtures.scala` to include realistic fixtures for all cache types
- Create `SampleDataGenerator` utility to compose diverse worktree scenarios with cached data
- Add `--sample-data` CLI flag to `dashboard.scala` command
- Modify `StateRepository.initialize()` or add initialization hook to load sample data
- Ensure sample data includes:
  - 5 sample worktrees with different tracker types (Linear, GitHub, YouTrack)
  - Diverse worktree statuses and activity timestamps
  - Cached issue data for each worktree (various statuses, assignees, timestamps)
  - Cached PR data with all PR states (Open, Merged, Closed)
  - Cached workflow progress with various phase completion states
  - Cached review states with different artifact collections
  - Edge cases (missing assignees, old timestamps, different priorities)

### Out of Scope
- Custom project directory support (Phase 3)
- Combined `--dev` flag (Phase 4)
- Full isolation validation (Phase 5)
- Changes to server daemon commands (separate scope)
- Production state initialization (only sample data)

## Dependencies

### From Previous Phases
- Phase 1 provided: `--state-path` CLI flag infrastructure for custom state isolation
- StateRepository and ServerStateService already support arbitrary state paths
- Dashboard.scala command pattern established with optional parameters

### Existing Infrastructure
- `TestFixtures.scala` already has `SampleData` object with basic Issue and configuration examples
- `ServerState` domain model already supports all cache types (issueCache, progressCache, prCache, reviewStateCache)
- `ServerStateService` already has thread-safe update methods for all cache types
- `StateRepository` already handles serialization/deserialization of all cache types
- `CaskServer.start()` already uses initialized state from StateRepository

## Technical Approach

### 1. Expand SampleData in TestFixtures.scala

Add factory methods to `SampleData` object for comprehensive fixtures:

**Domain Models to Add:**
- Multiple `WorktreeRegistration` samples with various timestamps and tracker types
- Multiple `IssueData` samples with diverse statuses, assignees, and descriptions
- `PullRequestData` samples covering all PRState values (Open, Merged, Closed)
- `CachedPR` samples with different fetch timestamps and TTL states
- `WorkflowProgress` samples with various phase completion percentages
- `CachedProgress` samples with different file modification times
- `ReviewState` and `CachedReviewState` samples with various artifacts
- `CachedIssue` samples combining IssueData with cache TTL state

**Sample Data Strategy:**
```
SampleData object should provide:
- Worktrees for: Linear team ("IWLE"), GitHub project, YouTrack project
- Issue statuses: "In Progress", "Todo", "Done", "Backlog", "Under Review"
- PR states: Open (in progress), Merged (completed), Closed (rejected)
- Progress: 0% (not started), 25%, 50%, 75%, 100% complete
- Review states: awaiting_review, in_review, ready_to_merge
- Timestamps: Current, 1 hour ago, 1 day ago, 1 week ago (exercise cache aging)
```

### 2. Create SampleDataGenerator Utility

New file: `.iw/core/domain/SampleDataGenerator.scala`

Composes diverse realistic scenarios:
```scala
object SampleDataGenerator:
  def generateSampleState(): ServerState
  // Creates a ServerState with:
  // - 5 worktrees across 3 tracker types
  // - Each has cached issue data
  // - Mix of PR states and workflow progress
  // - Various timestamps for cache TTL testing
```

**Design Principles:**
- Pure function (no effects, deterministic)
- Returns complete `ServerState` ready for persistence
- Includes edge cases naturally (missing assignees, old timestamps)
- Uses timestamps from Phase 1's activity timestamps

### 3. CLI Flag Parsing in dashboard.scala

Add `--sample-data` parameter:
```scala
@main def dashboard(
  statePath: Option[String] = None,
  sampleData: Boolean = false  // --sample-data flag
): Unit =
```

### 4. State Initialization with Sample Data

When `--sample-data` flag is set:
1. Generate sample state using `SampleDataGenerator.generateSampleState()`
2. Pass to StateRepository for persistence
3. ServerStateService loads normally (gets sample data)

### 5. State Path Resolution with Sample Data

When both `--state-path` and `--sample-data` are provided:
- Use custom state path for sample data persistence
- This enables: `./iw dashboard --state-path=/tmp/test --sample-data`
- Sample data loaded into custom path

When only `--sample-data` (no custom path):
- **Current decision:** Require `--state-path` for safety, or auto-generate temp path
- Recommendation: Auto-generate temp path to protect production state

## Files to Modify

| File | Change Type | Description |
|------|-------------|-------------|
| `.iw/core/test/TestFixtures.scala` | Modify | Expand SampleData object with comprehensive fixtures for all cache types |
| `.iw/core/domain/SampleDataGenerator.scala` | Create | New utility to generate realistic diverse ServerState with sample worktrees and caches |
| `.iw/commands/dashboard.scala` | Modify | Add `--sample-data` parameter, call SampleDataGenerator when flag set |

## Testing Strategy

### Unit Tests (in test suite)

**TestFixtures.scala tests:**
- SampleData fixtures create valid domain objects
- All fixtures can be serialized to JSON (test persistence round-trip)
- Sample timestamps are valid (in past or present)
- Sample URLs are properly formatted

**SampleDataGenerator tests:**
- `generateSampleState()` returns ServerState with correct counts (5 worktrees)
- Generated worktrees have all required fields populated
- Generated caches have matching issue IDs
- Sample data includes edge cases (missing assignees)
- Generated state is deterministic (same output each run)
- Serialization round-trip: JSON -> ServerState -> JSON matches

### Integration Tests

**State persistence with sample data:**
- Generate sample state
- Persist to temp path via StateRepository
- Read back and verify all caches present
- Verify counts (5 worktrees, caches for each)

### Manual Verification

1. Run: `./iw dashboard --state-path=/tmp/iw-samples --sample-data`
2. Server starts and browser opens to dashboard
3. Verify 5 sample worktrees display in dashboard
4. Click each worktree to verify:
   - Issue data loads (title, status, assignee, description)
   - PR data displays if present
   - Progress bar shows workflow state
   - Review state shows artifacts if present
5. Verify `/tmp/iw-samples/state.json` contains full structure with all caches
6. Verify file is readable JSON and can be edited/reloaded

## Acceptance Criteria

- [ ] `./iw dashboard --state-path=/tmp/test --sample-data` populates state with sample fixtures
- [ ] Sample includes 5 worktrees across Linear, GitHub, YouTrack tracker types
- [ ] Each worktree has cached issue data (title, status, assignee, description, URL)
- [ ] Sample includes cached PR data with Open, Merged, and Closed states
- [ ] Sample includes workflow progress with various completion percentages
- [ ] Sample includes review states with artifact collections
- [ ] Edge cases included: missing assignees, old timestamps, various statuses
- [ ] Dashboard renders all sample worktrees correctly
- [ ] UI states are visually testable (different statuses, PR states, progress levels)
- [ ] State file is valid JSON and can be reloaded in subsequent dashboard runs
- [ ] Sample data is deterministic (reproducible for testing)
- [ ] No `--sample-data` flag = uses production path (backward compatible)

## Sample Data Design Decisions

### 1. Worktree Count: 5 worktrees
- Enough to exercise list rendering and scrolling
- Covers 3 tracker types (Linear, GitHub, YouTrack)
- Provides diverse scenarios

### 2. Tracker Types
- Linear: IWLE-123, IWLE-456 (2 worktrees)
- GitHub: GH-100 (1 worktree)
- YouTrack: YT-111, YT-222 (2 worktrees)

### 3. Timestamps Strategy
```
Current time (Instant.now()):
- worktree.registeredAt: 7 days ago to 1 day ago (varying)
- worktree.lastSeenAt: 1 hour ago to current (active worktrees)

Issue cache:
- Some fetchedAt: current time (fresh)
- Some fetchedAt: 10 minutes ago (valid)
- Some fetchedAt: 2 hours ago (stale but readable)

PR cache:
- Similar TTL patterns (2 min default vs 5 min for issues)

Progress cache:
- filesMtime: various timestamps to test invalidation logic

Review cache:
- filesMtime: various timestamps to test artifact staleness
```

### 4. PR State Coverage
```
- IWLE-123: PR#42 Open (in progress)
- IWLE-456: PR#45 Merged (completed work)
- GH-100: No PR (edge case)
- YT-111: PR#1 Closed (rejected)
- YT-222: PR#5 Open (under review)
```

### 5. Progress States
```
- IWLE-123: currentPhase=2/5, 40% complete
- IWLE-456: currentPhase=5/5, 100% complete (merged)
- GH-100: currentPhase=1/3, 10% complete (just started)
- YT-111: No workflow (edge case)
- YT-222: currentPhase=3/4, 60% complete
```

### 6. Review States
```
- IWLE-123: status="in_review", phase=2, artifacts=["analysis.md", "phase-02-context.md"]
- IWLE-456: status="ready_to_merge", phase=5, artifacts=["implementation-log.md"]
- GH-100: status="awaiting_review", phase=1, artifacts=[]
- YT-111: No review state (edge case)
- YT-222: status="in_review", phase=3, artifacts=["tasks.md", "review.md"]
```

## Estimated Effort

6-8 hours

**Breakdown:**
- Expand SampleData fixtures: 2-3 hours (creating diverse but realistic samples)
- Create SampleDataGenerator: 1-2 hours (composing samples into coherent scenarios)
- CLI integration in dashboard.scala: 30 minutes (simple flag addition)
- Testing and verification: 2-3 hours (ensuring all UI states render, state round-trips)

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Sample data stale timestamps confuse tests | Use descriptive comments in SampleDataGenerator explaining timestamp strategy |
| UI doesn't handle all edge cases in samples | That's the point! Sample data should expose UI issues |
| Sample data too simplistic for comprehensive testing | Design deliberately includes edge cases and state transitions |
| Sample worktree paths don't exist | Sample paths are fake ("/tmp/sample-worktree-X") - not intended to be real git repos |
| Persistent sample data conflicts with production | Use `--state-path` to isolate (Phase 1 foundation) |

## For Next Phases

**For Phase 3 (custom project directory):**
- Sample data generation can be reused
- May need to parameterize project paths in samples

**For Phase 4 (--dev flag):**
- Sample data becomes automatic when `--dev` used
- May combine with temporary state path auto-generation

**For Phase 5 (isolation validation):**
- Sample data generation useful for creating baseline states
- Can create multiple divergent states to test merge/conflict scenarios
