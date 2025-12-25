# Phase 1 Tasks: Display artifacts from state file

**Issue:** #46
**Phase:** 1 of 6
**Status:** Ready to implement
**Estimated Effort:** 6-8 hours

## Setup

- [ ] Review WorkflowProgressService pattern for I/O injection (5 min)
- [ ] Review CachedProgress implementation for cache validation pattern (5 min)
- [ ] Review StateRepository JSON serialization pattern (5 min)

## Domain Models (TDD)

### ReviewState and ReviewArtifact

- [ ] Write test: ReviewState requires artifacts list (10 min)
- [ ] Write test: ReviewState accepts optional status, phase, message (10 min)
- [ ] Write test: ReviewArtifact has label and path (10 min)
- [ ] Implement: Create ReviewState.scala with case classes (15 min)
- [ ] Run tests: Verify domain models pass (5 min)

### CachedReviewState

- [ ] Write test: CachedReviewState.isValid returns true when mtimes match (15 min)
- [ ] Write test: CachedReviewState.isValid returns false when mtime changed (10 min)
- [ ] Write test: CachedReviewState.isValid returns false when file added/removed (10 min)
- [ ] Implement: Create CachedReviewState.scala with validation logic (20 min)
- [ ] Run tests: Verify cache validation passes (5 min)

## JSON Parsing (TDD)

### ReviewStateService - parseReviewStateJson

- [ ] Write test: parseReviewStateJson parses valid JSON with all fields (15 min)
- [ ] Write test: parseReviewStateJson parses minimal JSON (only artifacts) (10 min)
- [ ] Write test: parseReviewStateJson returns error for missing artifacts (10 min)
- [ ] Write test: parseReviewStateJson returns error for invalid JSON syntax (10 min)
- [ ] Write test: parseReviewStateJson handles optional fields as None (10 min)
- [ ] Implement: Create ReviewStateService.scala with parseReviewStateJson (30 min)
- [ ] Implement: Add uJson ReadWriter instances for ReviewState and ReviewArtifact (15 min)
- [ ] Run tests: Verify JSON parsing passes (5 min)

## Service with I/O Injection (TDD)

### ReviewStateService - fetchReviewState

- [ ] Write test: fetchReviewState returns cached state when mtime unchanged (20 min)
- [ ] Write test: fetchReviewState re-reads file when mtime changed (20 min)
- [ ] Write test: fetchReviewState returns error for missing file (15 min)
- [ ] Write test: fetchReviewState returns error for invalid JSON (15 min)
- [ ] Write test: fetchReviewState handles cache miss (reads and parses file) (15 min)
- [ ] Implement: fetchReviewState with I/O injection pattern (30 min)
- [ ] Implement: File path construction (project-management/issues/{issueId}/review-state.json) (10 min)
- [ ] Run tests: Verify fetchReviewState integration passes (10 min)

## ServerState Integration

### Extend ServerState with reviewStateCache

- [ ] Write test: ServerState includes reviewStateCache field (10 min)
- [ ] Write test: ServerState.removeWorktree clears review state cache entry (10 min)
- [ ] Implement: Add reviewStateCache to ServerState case class (5 min)
- [ ] Implement: Update removeWorktree to clear reviewStateCache (5 min)
- [ ] Run tests: Verify ServerState changes pass (5 min)

### Extend StateRepository JSON serialization

- [ ] Write test: StateRepository serializes ReviewState (15 min)
- [ ] Write test: StateRepository serializes CachedReviewState (15 min)
- [ ] Write test: StateRepository deserializes reviewStateCache (15 min)
- [ ] Write test: StateRepository handles missing reviewStateCache gracefully (10 min)
- [ ] Implement: Add uJson ReadWriter for CachedReviewState in StateRepository (20 min)
- [ ] Implement: Update StateJson case class with reviewStateCache field (10 min)
- [ ] Implement: Update read/write methods to include reviewStateCache (15 min)
- [ ] Run tests: Verify StateRepository serialization passes (5 min)

## Dashboard Integration

### DashboardService - fetchReviewStateForWorktree

- [ ] Write test: fetchReviewStateForWorktree returns ReviewState when file exists (20 min)
- [ ] Write test: fetchReviewStateForWorktree returns None when file missing (15 min)
- [ ] Write test: fetchReviewStateForWorktree uses cache correctly (15 min)
- [ ] Implement: Add fetchReviewStateForWorktree private method to DashboardService (25 min)
- [ ] Implement: Inject readFile and getMtime functions following WorkflowProgressService pattern (15 min)
- [ ] Implement: Update renderDashboard to accept reviewStateCache parameter (10 min)
- [ ] Implement: Call fetchReviewStateForWorktree for each worktree in dashboard (15 min)
- [ ] Run tests: Verify DashboardService changes pass (5 min)

### CaskServer - pass reviewStateCache to dashboard

- [ ] Implement: Update dashboard route to pass state.reviewStateCache to DashboardService (10 min)
- [ ] Manual test: Verify server compiles and starts (5 min)

## UI Rendering

### WorktreeListView - display review section

- [ ] Write test: WorktreeListView renders review section when reviewState provided (20 min)
- [ ] Write test: WorktreeListView omits review section when reviewState is None (15 min)
- [ ] Write test: WorktreeListView displays artifact labels correctly (15 min)
- [ ] Implement: Update WorktreeListView.render signature to accept reviewState parameter (10 min)
- [ ] Implement: Update renderWorktreeCard to include review artifacts section (20 min)
- [ ] Implement: Add renderReviewSection helper method (15 min)
- [ ] Implement: Add CSS styles for review artifacts section (15 min)
- [ ] Run tests: Verify WorktreeListView changes pass (5 min)

## End-to-End Testing

### Manual E2E verification

- [ ] Create test worktree with review-state.json file (10 min)
- [ ] Create review-state.json with sample artifacts (5 min)
- [ ] Start dashboard server and verify review section displays (10 min)
- [ ] Verify artifact labels are shown correctly (5 min)
- [ ] Test missing file: create worktree without review-state.json (5 min)
- [ ] Verify dashboard doesn't crash with missing file (5 min)
- [ ] Test invalid JSON: create malformed review-state.json (5 min)
- [ ] Verify dashboard handles invalid JSON gracefully (5 min)
- [ ] Test cache: modify review-state.json and reload dashboard (5 min)
- [ ] Verify changes appear (cache invalidation working) (5 min)

### BATS E2E tests (optional)

- [ ] Write BATS test: Dashboard shows review section with state file (30 min)
- [ ] Write BATS test: Dashboard handles missing state file (20 min)
- [ ] Run BATS tests: Verify E2E tests pass (10 min)

## Final Integration

- [ ] Run all unit tests (5 min)
- [ ] Run all integration tests (5 min)
- [ ] Fix any failing tests (buffer: 30 min)
- [ ] Manual smoke test: full dashboard workflow (10 min)
- [ ] Review code for style consistency (15 min)
- [ ] Commit: Domain models and JSON parsing (5 min)
- [ ] Commit: Service with I/O injection (5 min)
- [ ] Commit: ServerState and StateRepository integration (5 min)
- [ ] Commit: Dashboard and UI rendering (5 min)
- [ ] Commit: Tests and documentation (5 min)

---

## Task Summary

**Total Tasks:** ~85 tasks
**Estimated Time:** 6-8 hours
**Test Coverage:** Unit + Integration + E2E

**Key Milestones:**
1. Domain models complete (30 min)
2. JSON parsing working (1 hour)
3. Service with caching (1.5 hours)
4. State integration (1.5 hours)
5. Dashboard rendering (1.5 hours)
6. E2E verification (1 hour)

**TDD Approach:**
- Each feature starts with failing tests
- Tests written before implementation
- Tests run after each implementation step
- Integration verified at each milestone

**Pattern Adherence:**
- Following WorkflowProgressService I/O injection pattern exactly
- Following CachedProgress cache validation pattern exactly
- Following StateRepository JSON serialization pattern exactly
