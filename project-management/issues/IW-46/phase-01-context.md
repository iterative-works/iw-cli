# Phase 1 Context: Display artifacts from state file

**Issue:** #46
**Phase:** 1 of 6
**Status:** Ready to implement
**Estimated Effort:** 6-8 hours

## Goals

This phase implements the **foundation** for displaying review artifacts in the dashboard:

1. **Review State Domain Model**: Define `ReviewState` and `ReviewArtifact` value objects to represent review state data
2. **JSON Parsing**: Parse `review-state.json` files from worktree directories using existing uJson library
3. **Review State Service**: Implement `ReviewStateService` with I/O injection pattern (following `WorkflowProgressService` pattern)
4. **Dashboard Integration**: Extend `WorktreeListView` to display review artifacts section on worktree cards
5. **Graceful Degradation**: Handle missing/invalid state files without breaking dashboard rendering

**Success Criteria:** Dashboard displays a "Review Artifacts" section on worktree cards when `project-management/issues/{issueId}/review-state.json` exists. Missing or invalid files are handled gracefully.

## Scope

### In Scope

**Domain Layer:**
- `ReviewState` case class (status: Option[String], phase: Option[Int], message: Option[String], artifacts: List[ReviewArtifact])
- `ReviewArtifact` case class (label: String, path: String)
- `CachedReviewState` case class (state: ReviewState, filesMtime: Map[String, Long])
- Cache validation logic (similar to `CachedProgress.isValid`)

**Application Layer:**
- `ReviewStateService.fetchReviewState(issueId, worktreePath, cache, readFile, getMtime): Either[String, ReviewState]`
- `ReviewStateService.parseReviewStateJson(content): Either[String, ReviewState]`
- Error handling for missing/malformed JSON

**Infrastructure Layer:**
- JSON parsing with uJson (already in dependencies)
- File I/O via injected functions (follows WorkflowProgressService pattern)
- No new dependencies needed

**Presentation Layer:**
- Extend `WorktreeListView.renderWorktreeCard` with review section
- Add CSS styles for review artifacts section
- Display artifact labels as list items

**Data Flow Integration:**
- Extend `DashboardService.renderDashboard` to fetch review state per worktree
- Add `reviewStateCache: Map[String, CachedReviewState]` to `ServerState`
- Update `StateRepository` JSON serialization to include review state cache

**Testing:**
- Unit tests for `ReviewState` parsing from JSON
- Unit tests for cache validation logic
- Integration tests for `ReviewStateService.fetchReviewState` with file I/O
- Test graceful handling of missing/invalid files
- E2E test (or BATS test) verifying dashboard displays review section

### Out of Scope

**NOT in this phase:**
- Clicking artifacts to view content (Phase 2)
- Artifact content rendering (Phase 2)
- Path validation/security (Phase 2 prerequisite)
- Visual status indicators for review state (Phase 3)
- Any HTTP endpoints beyond existing dashboard endpoint

**Manual Testing Approach:**
- Manually create `review-state.json` files in test worktrees
- Verify dashboard displays artifact list correctly
- Verify missing files don't break dashboard

## Dependencies

### Prerequisites

**From existing codebase:**
- `WorkflowProgressService` pattern (file I/O injection, caching)
- `ServerState` with cache infrastructure
- `StateRepository` JSON serialization
- `DashboardService` dashboard rendering flow
- `WorktreeListView` Scalatags templates
- `upickle` library (already in `project.scala`)

**File locations:**
- Review state file: `{worktreePath}/project-management/issues/{issueId}/review-state.json`
- Following same pattern as task files: `phase-XX-tasks.md`

### Blocked By

- None (extends existing infrastructure)

### Blocks

- Phase 2 (needs `ReviewState` and artifact list to display content)
- Phase 3 (needs `ReviewState` to show status/phase indicators)
- Phase 4 (caching optimization depends on basic loading working)

## Technical Approach

### Architecture Pattern

Following **Functional Core, Imperative Shell** (same as `WorkflowProgressService`):

1. **Functional Core**:
   - `ReviewState` and `ReviewArtifact` are immutable case classes
   - `parseReviewStateJson` is pure function (String => Either[String, ReviewState])
   - Cache validation logic is pure (compares mtimes)

2. **Imperative Shell**:
   - File reading injected via `readFile: String => Either[String, String]`
   - File mtime injected via `getMtime: String => Either[String, Long]`
   - I/O happens at edges (DashboardService)

### JSON Schema

**File location:** `{worktreePath}/project-management/issues/{issueId}/review-state.json`

**Schema:**
```json
{
  "status": "awaiting_review",
  "phase": 8,
  "message": "Phase 8 complete - Ready for review",
  "artifacts": [
    {"label": "Analysis", "path": "project-management/issues/46/analysis.md"},
    {"label": "Phase Context", "path": "project-management/issues/46/phase-08-context.md"}
  ]
}
```

**Required fields:** `artifacts` (array of {label, path} objects)
**Optional fields:** `status`, `phase`, `message` (all can be null/missing)

**Lenient Parsing:**
- Missing optional fields => None
- Unknown fields => ignored
- Invalid JSON => Left("Parse error: ...")
- Missing artifacts array => Left("Missing required field: artifacts")

### Implementation Sequence

**Step 1: Domain Models** (1-2h)
- Create `ReviewState.scala` with case classes
- Create `CachedReviewState.scala` with validation logic
- Unit tests for domain models

**Step 2: JSON Parsing** (1-2h)
- Implement `ReviewStateService.parseReviewStateJson`
- Use uJson ReadWriter macros (similar to StateRepository)
- Handle optional fields gracefully
- Unit tests for parsing (valid, invalid, missing fields)

**Step 3: Service with I/O Injection** (2-3h)
- Implement `ReviewStateService.fetchReviewState`
- Follow WorkflowProgressService pattern exactly:
  - Check cache validity first
  - Read file only if cache invalid/missing
  - Parse JSON content
  - Return Either[String, ReviewState]
- Integration tests with mocked file I/O

**Step 4: Dashboard Integration** (1-2h)
- Extend `ServerState` with `reviewStateCache`
- Update `StateRepository` serialization
- Add `fetchReviewStateForWorktree` to `DashboardService`
- Pass review state to `WorktreeListView`

**Step 5: UI Rendering** (1h)
- Extend `WorktreeListView.renderWorktreeCard` with review section
- Add CSS styles (simple list with labels)
- Conditional rendering (only show if reviewState.isDefined)

**Step 6: Testing & Validation** (1h)
- E2E test with real review-state.json
- Test missing file (should not crash)
- Test invalid JSON (should show error or hide section)
- Verify cache works (file not re-read on second load)

### File I/O Pattern

Following `WorkflowProgressService` exactly:

```scala
// In DashboardService
private def fetchReviewStateForWorktree(
  wt: WorktreeRegistration,
  cache: Map[String, CachedReviewState]
): Option[ReviewState] =
  // Inject file reading
  val readFile = (path: String) => Try {
    val source = scala.io.Source.fromFile(path)
    try source.mkString
    finally source.close()
  }.toEither.left.map(_.getMessage)

  // Inject mtime reading
  val getMtime = (path: String) => Try {
    java.nio.file.Files.getLastModifiedTime(
      java.nio.file.Paths.get(path)
    ).toMillis
  }.toEither.left.map(_.getMessage)

  // Call service
  ReviewStateService.fetchReviewState(
    wt.issueId,
    wt.path,
    cache,
    readFile,
    getMtime
  ).toOption
```

### Cache Strategy

**Exactly like `CachedProgress`:**

1. On dashboard load:
   - Get mtime of `review-state.json`
   - Check cache: `CachedReviewState.isValid(cached, currentMtime)`
   - If valid: return cached `ReviewState`
   - If invalid/missing: read file, parse, update cache

2. Cache invalidation:
   - File modified (mtime changed) => re-read
   - File deleted => return None, clear cache
   - Server restart => cache persists in `state.json`

3. Cache storage:
   - Add to `ServerState.reviewStateCache: Map[String, CachedReviewState]`
   - Serialize in `StateRepository` JSON (extend existing serialization)

## Files to Modify

### New Files (Create)

1. `/home/mph/Devel/projects/iw-cli-46/.iw/core/ReviewState.scala`
   - Domain models: `ReviewState`, `ReviewArtifact`

2. `/home/mph/Devel/projects/iw-cli-46/.iw/core/CachedReviewState.scala`
   - Cache wrapper with validation logic

3. `/home/mph/Devel/projects/iw-cli-46/.iw/core/ReviewStateService.scala`
   - Application service with I/O injection

4. `/home/mph/Devel/projects/iw-cli-46/.iw/core/test/ReviewStateServiceTest.scala`
   - Unit and integration tests

### Existing Files (Modify)

1. `/home/mph/Devel/projects/iw-cli-46/.iw/core/ServerState.scala`
   - Add `reviewStateCache: Map[String, CachedReviewState] = Map.empty`

2. `/home/mph/Devel/projects/iw-cli-46/.iw/core/StateRepository.scala`
   - Add ReadWriter for `ReviewState`, `ReviewArtifact`, `CachedReviewState`
   - Update `StateJson` case class with reviewStateCache field
   - Update read/write methods to include review cache

3. `/home/mph/Devel/projects/iw-cli-46/.iw/core/DashboardService.scala`
   - Add `reviewStateCache` parameter to `renderDashboard`
   - Add `fetchReviewStateForWorktree` method
   - Pass review state to WorktreeListView

4. `/home/mph/Devel/projects/iw-cli-46/.iw/core/WorktreeListView.scala`
   - Update `render` signature to include review state
   - Update `renderWorktreeCard` to show review section
   - Add review artifacts list rendering

5. `/home/mph/Devel/projects/iw-cli-46/.iw/core/CaskServer.scala`
   - Update dashboard route to pass reviewStateCache

## Testing Strategy

### Unit Tests

**ReviewStateService:**
- `parseReviewStateJson` with valid JSON => Right(ReviewState)
- `parseReviewStateJson` with missing artifacts => Left(error)
- `parseReviewStateJson` with optional fields missing => ReviewState with None values
- `parseReviewStateJson` with invalid JSON => Left(parse error)
- Cache validation: same mtime => cache valid
- Cache validation: changed mtime => cache invalid

**Test fixtures:**
```scala
// Valid review state
val validJson = """
{
  "status": "awaiting_review",
  "phase": 8,
  "message": "Ready for review",
  "artifacts": [
    {"label": "Analysis", "path": "project-management/issues/46/analysis.md"}
  ]
}
"""

// Minimal valid (only artifacts required)
val minimalJson = """
{
  "artifacts": [
    {"label": "Doc", "path": "path/to/doc.md"}
  ]
}
"""

// Invalid (missing artifacts)
val invalidJson = """
{
  "status": "awaiting_review"
}
"""
```

### Integration Tests

**File I/O Integration:**
- `fetchReviewState` with real file => Right(ReviewState)
- `fetchReviewState` with missing file => Left(error)
- `fetchReviewState` with cache hit => no file read
- `fetchReviewState` with cache miss => file read + parse

### E2E Tests (Manual or BATS)

**Scenario 1: Dashboard shows review section**
1. Create worktree with review-state.json
2. Load dashboard
3. Verify worktree card shows "Review Artifacts" section
4. Verify artifact labels displayed

**Scenario 2: Missing file handled gracefully**
1. Create worktree without review-state.json
2. Load dashboard
3. Verify worktree card shows (no review section)
4. Verify dashboard doesn't crash

**Scenario 3: Invalid JSON handled gracefully**
1. Create worktree with malformed review-state.json
2. Load dashboard
3. Verify worktree card shows (possibly error message)
4. Verify dashboard doesn't crash

## Acceptance Criteria

**Must Have:**
- [ ] `ReviewState` and `ReviewArtifact` domain models created
- [ ] `ReviewStateService.fetchReviewState` implemented with I/O injection
- [ ] `ReviewStateService.parseReviewStateJson` parses valid JSON correctly
- [ ] Cache validation logic works (follows CachedProgress pattern)
- [ ] `ServerState` includes `reviewStateCache`
- [ ] `StateRepository` serializes/deserializes review cache
- [ ] Dashboard displays "Review Artifacts" section when state file exists
- [ ] Artifact labels shown as list items
- [ ] Missing state files don't break dashboard rendering
- [ ] Invalid JSON doesn't crash server
- [ ] Unit tests pass (parsing, cache validation)
- [ ] Integration tests pass (file I/O)

**Nice to Have:**
- [ ] Error message shown when JSON parse fails (vs silent hide)
- [ ] Loading indicator while parsing (not critical for Phase 1)

## Risk Assessment

**Low Risk:**
- Follows proven WorkflowProgressService pattern exactly
- Uses existing uJson library (no new dependencies)
- Pure functional core (easy to test)
- Graceful degradation (failures are isolated)

**Potential Issues:**
1. **JSON schema mismatch**: Workflows write different format than expected
   - Mitigation: Lenient parsing (ignore unknown fields, optional fields)
   
2. **Performance**: Reading many review-state.json files on dashboard load
   - Mitigation: Cache with mtime validation (only read when changed)
   
3. **File encoding issues**: UTF-8 vs other encodings
   - Mitigation: Scala Source handles UTF-8 by default

## Next Steps

After Phase 1 completion:

1. **Phase 2**: Implement artifact viewing (click to see content)
   - Requires path validation (security)
   - Requires markdown rendering
   - Requires new HTTP endpoint

2. **Phase 3**: Display review status and phase indicators
   - Visual badges for different statuses
   - Phase number display

3. **Phase 4**: Review state caching optimization
   - May already be sufficient from Phase 1 cache
   - Could add cache preloading

---

**Implementation Ready:** Yes

**Blockers:** None

**Coordination Needed:** 
- Workflows should write `review-state.json` in the documented schema
- Can implement dashboard first, workflows can adopt later (backward compatible)

---

**Key Design Decisions:**

- **I/O Injection Pattern**: Enables pure functional core, easy testing
- **Lenient Parsing**: All fields optional except `artifacts` for forward/backward compatibility
- **Cache Strategy**: mtime-based validation (proven with WorkflowProgress)
- **Graceful Degradation**: Missing/invalid states don't break dashboard (return None/Left)
- **No New Dependencies**: Reuse existing uJson library
- **Schema Location**: Co-located with artifacts in `project-management/issues/{issueId}/`
