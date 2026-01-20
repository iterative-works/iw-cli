# Phase 2 Implementation Tasks: Load sample data for UI testing

**Issue:** IW-82
**Phase:** 2 of 5
**Story:** Load sample data for UI testing

## TDD Implementation Tasks

### Setup

- [ ] [setup] Review existing TestFixtures.scala SampleData patterns
- [ ] [setup] Review phase-02-context.md acceptance criteria and sample data design decisions

### SampleData Fixtures (TestFixtures.scala)

**WorktreeRegistration samples:**
- [x] [test] [x] [reviewed] Test SampleData.sampleWorktrees returns 5 valid WorktreeRegistrations (Linear, GitHub, YouTrack)
- [x] [impl] [ ] [reviewed] Add 5 WorktreeRegistration samples (IWLE-123, IWLE-456, GH-100, YT-111, YT-222)

**IssueData samples:**
- [x] [test] [x] [reviewed] Test SampleData.sampleIssues returns IssueData with various statuses (In Progress, Done, Backlog, Under Review, Todo)
- [x] [test] [x] [reviewed] Test at least one issue has assignee=None (edge case)
- [x] [impl] [ ] [reviewed] Add 5 IssueData samples with diverse statuses, including one without assignee

**CachedIssue samples:**
- [x] [test] [x] [reviewed] Test CachedIssue samples include fresh and stale timestamps
- [x] [test] [x] [reviewed] Test CachedIssue.isStale() returns true for stale samples
- [x] [impl] [ ] [reviewed] Add CachedIssue samples (fresh, valid, stale)

**PullRequestData samples:**
- [x] [test] [x] [reviewed] Test SampleData.samplePRs returns PRs with all states (Open, Merged, Closed)
- [x] [impl] [ ] [reviewed] Add 3 PullRequestData samples (Open, Merged, Closed)

**CachedPR samples:**
- [x] [test] [x] [reviewed] Test CachedPR samples include fresh and stale timestamps
- [x] [impl] [ ] [reviewed] Add CachedPR samples for worktrees with PRs (4 worktrees have PRs, GH-100 has none)

**WorkflowProgress samples:**
- [x] [test] [x] [reviewed] Test WorkflowProgress samples cover all completion levels (0%, 25%, 50%, 75%, 100%)
- [x] [impl] [ ] [reviewed] Add 5 WorkflowProgress samples with PhaseInfo helpers

**CachedProgress samples:**
- [x] [test] [x] [reviewed] Test CachedProgress samples include filesMtime validation
- [x] [impl] [ ] [reviewed] Add CachedProgress samples (fresh, stale)

**ReviewState samples:**
- [x] [test] [x] [reviewed] Test ReviewState samples cover statuses: awaiting_review, in_review, ready_to_merge
- [x] [test] [x] [reviewed] Test ReviewState samples include both with and without artifacts
- [x] [impl] [ ] [reviewed] Add 4 ReviewState samples with varying artifacts

**CachedReviewState samples:**
- [x] [test] [x] [reviewed] Test CachedReviewState samples include fresh and stale
- [x] [impl] [ ] [reviewed] Add CachedReviewState samples

**JSON serialization:**
- [ ] [test] Test all SampleData fixtures serialize to JSON and deserialize correctly (round-trip)
- [ ] [impl] Verify all sample types have proper macroRW serialization

### SampleDataGenerator Utility

**File: `.iw/core/domain/SampleDataGenerator.scala`**

**Core generation:**
- [x] [test] [x] [reviewed] Test generateSampleState() returns ServerState with 5 worktrees
- [x] [test] [x] [reviewed] Test generated worktrees use 3 tracker types (Linear, GitHub, YouTrack)
- [x] [test] [x] [reviewed] Test generateSampleState() is deterministic (same output each run)
- [x] [impl] [ ] [reviewed] Create SampleDataGenerator.scala with generateSampleState() function

**Cache population:**
- [x] [test] [x] [reviewed] Test issueCache contains entries for all 5 worktrees
- [x] [test] [x] [reviewed] Test progressCache contains entries for all 5 worktrees
- [x] [test] [x] [reviewed] Test prCache contains entries for 4 worktrees (GH-100 has no PR)
- [x] [test] [x] [reviewed] Test reviewStateCache contains entries for 4 worktrees (YT-111 has no review)
- [x] [impl] [ ] [reviewed] Populate all cache maps in generateSampleState()

**Edge cases:**
- [x] [test] [x] [reviewed] Test generated data includes missing assignee in at least one issue
- [x] [test] [x] [reviewed] Test generated timestamps span multiple time periods (fresh to stale)
- [x] [test] [x] [reviewed] Test generated PRs cover all PRState values
- [x] [impl] [ ] [reviewed] Ensure edge cases are properly represented in generated state

**Persistence:**
- [ ] [test] Test generateSampleState() output serializes correctly via StateRepository
- [ ] [test] Test round-trip: generate -> serialize -> deserialize preserves all data
- [ ] [impl] Verify StateRepository can handle generated state

### CLI Integration (dashboard.scala)

**Parameter handling:**
- [ ] [test] Test dashboard command accepts --sample-data flag
- [ ] [test] Test sampleData parameter defaults to false
- [ ] [test] Test --sample-data can combine with --state-path
- [x] [impl] [ ] [reviewed] Add sampleData: Boolean = false parameter to dashboard @main function

**State initialization:**
- [ ] [test] Test sample data initializes state at custom path when both flags provided
- [ ] [test] Test sample state persists correctly and can be reread
- [x] [impl] [ ] [reviewed] When sampleData=true, call SampleDataGenerator and persist to effectiveStatePath

**Isolation (Phase 1 integration):**
- [ ] [test] Test production path unchanged when --state-path provided with --sample-data
- [x] [impl] [ ] [reviewed] Verify state isolation works with sample data

### Integration and Verification

- [ ] [test] Test all 5 worktrees appear in ServerState API response
- [ ] [test] Test dashboard can start with sample data at custom path
- [ ] [verify] Manual: Run `./iw dashboard --state-path=/tmp/test --sample-data`
- [ ] [verify] Manual: Confirm 5 worktrees display in dashboard UI
- [ ] [verify] Manual: Confirm issue data shows for each worktree
- [ ] [verify] Manual: Confirm PR badges display correctly (Open/Merged/Closed/None)
- [ ] [verify] Manual: Confirm progress bars show completion percentages
- [ ] [verify] Manual: Confirm state file at custom path is valid JSON

---

## Acceptance Criteria Checklist

- [ ] `./iw dashboard --state-path=/tmp/test --sample-data` populates state with fixtures
- [ ] Sample includes 5 worktrees across Linear, GitHub, YouTrack tracker types
- [ ] Each worktree has cached issue data (title, status, assignee, description, URL)
- [ ] Sample includes cached PR data with Open, Merged, and Closed states
- [ ] Sample includes workflow progress with various completion percentages
- [ ] Sample includes review states with artifact collections
- [ ] Edge cases included: missing assignees, old timestamps, various statuses
- [ ] Dashboard renders all sample worktrees correctly
- [ ] State file is valid JSON and can be reloaded
- [ ] Sample data is deterministic (reproducible)
- [ ] No `--sample-data` flag = uses production path (backward compatible)

---

## Summary

**Total Tasks:** 47
- Setup: 2
- Tests: 29
- Implementation: 12
- Verification: 4

**Estimated Effort:** 6-8 hours
