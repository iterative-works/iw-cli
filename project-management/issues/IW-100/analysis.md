# Story-Driven Analysis: Dashboard shows incorrect total phase count

**Issue:** IW-100
**Created:** 2026-01-26
**Status:** Ready for Implementation
**Classification:** Simple

## Problem Statement

The Dashboard displays phase progress as 'Phase X/Y' but the total number of phases (Y) is calculated incorrectly. When a tasks.md file lists 6 phases in its Phase Index but only 3 phase task files exist on disk, the dashboard shows "Phase 3/3" instead of "Phase 3/6".

**User Impact:** Developers cannot see the full scope of planned work at a glance. The phase counter suggests they're almost done (Phase 3/3) when in reality they're only halfway through (Phase 3/6). This creates incorrect expectations about remaining work and undermines trust in the dashboard's accuracy.

**Root Cause:** The WorkflowProgressService computes totalPhases from discovered phase files on disk (phases.size) rather than from the Phase Index in tasks.md which is the source of truth for planned phases.

## User Stories

### Story 1: Display correct total phase count from Phase Index

```gherkin
Feature: Total phase count reflects planned phases
  As a developer
  I want to see the total number of planned phases
  So that I understand the full scope of work remaining

Scenario: Phase Index with more phases than created files
  Given tasks.md Phase Index lists 6 phases
  And only phase-01-tasks.md, phase-02-tasks.md, phase-03-tasks.md exist
  And Phase 3 is marked incomplete in the Phase Index
  When I view the dashboard
  Then I see "Phase 3/6: [Phase 3 name]"
  And the total shows 6 phases, not 3

Scenario: All phase files created matching Phase Index
  Given tasks.md Phase Index lists 4 phases
  And all 4 phase task files exist (phase-01 through phase-04)
  And Phase 2 is marked incomplete in the Phase Index
  When I view the dashboard
  Then I see "Phase 2/4: [Phase 2 name]"
  And the total shows 4 phases
```

**Estimated Effort:** 2-3 hours
**Complexity:** Straightforward

**Technical Feasibility:**
The Phase Index is already being parsed by MarkdownTaskParser.parsePhaseIndex and used to determine currentPhase. We simply need to also use it for totalPhases. The data is available in computeProgress function - we just need to use phaseIndex.size as the source of truth instead of phases.size.

**Key Technical Challenge:**
- Handling edge case when Phase Index is empty (fallback to current behavior)
- Ensuring tests cover both Phase Index and non-Phase Index scenarios

**Acceptance:**
- Dashboard displays totalPhases from Phase Index count when available
- Falls back to discovered phase file count when Phase Index is empty (backward compatibility)
- All existing tests pass
- New tests verify Phase Index is source of truth

---

### Story 2: Handle missing Phase Index gracefully

```gherkin
Feature: Graceful fallback when Phase Index unavailable
  As a developer working on older issues
  I want the dashboard to still show phase count
  So that issues without Phase Index continue to work

Scenario: No Phase Index in tasks.md (legacy behavior)
  Given tasks.md exists but has no Phase Index section
  And 3 phase task files exist on disk
  When I view the dashboard
  Then I see "Phase X/3: [Phase name]"
  And the total is calculated from discovered files

Scenario: Empty Phase Index section
  Given tasks.md has "## Phase Index" header but no phase entries
  And 2 phase task files exist on disk
  When I view the dashboard
  Then I see "Phase X/2: [Phase name]"
  And the total is calculated from discovered files
```

**Estimated Effort:** 1 hour
**Complexity:** Straightforward

**Technical Feasibility:**
This is defensive programming - ensuring the fix doesn't break existing issues that may not have Phase Index sections. The fallback logic is simple: if phaseIndex.isEmpty, use phases.size (current behavior).

**Acceptance:**
- Issues without Phase Index continue to work as before
- Empty Phase Index falls back to file-based count
- Tests verify fallback behavior

## Architectural Sketch

### For Story 1: Use Phase Index for total count

**Domain Layer:**
- WorkflowProgress (model/WorkflowProgress.scala) - no changes needed, already has totalPhases field
- PhaseIndexEntry (dashboard/MarkdownTaskParser.scala) - already exists, represents parsed Phase Index

**Application Layer:**
- WorkflowProgressService.computeProgress (dashboard/WorkflowProgressService.scala)
  - Change: Use phaseIndex.size for totalPhases when phaseIndex.nonEmpty
  - Existing: phases parameter, phaseIndex parameter already available

**Presentation Layer:**
- WorktreeCardRenderer.renderCard (dashboard/presentation/views/WorktreeCardRenderer.scala)
  - No changes needed - already displays progress.totalPhases

---

### For Story 2: Fallback logic

**Application Layer:**
- WorkflowProgressService.computeProgress (dashboard/WorkflowProgressService.scala)
  - Change: Add fallback logic - if phaseIndex.isEmpty then use phases.size else use phaseIndex.size
  - This preserves backward compatibility

## Design Decisions

### RESOLVED: Source of truth for totalPhases

**Decision:** Use Phase Index from tasks.md as source of truth when available, fall back to discovered file count otherwise.

**Rationale:**
- Phase Index represents the planned workflow scope
- Phase task files are created incrementally as work progresses
- Current behavior (file count) is accurate only when all files exist

**Implementation:**
```
totalPhases = if phaseIndex.nonEmpty then phaseIndex.size else phases.size
```

### RESOLVED: Handling phase number gaps

**Decision:** Use phaseIndex.size (count of entries), not max phase number.

**Rationale:**
- Phase Index lists actual planned phases regardless of numbering
- Non-sequential numbering (1, 2, 5) should show 3 phases, not 5
- Simpler logic with clearer semantics

## Total Estimates

**Story Breakdown:**
- Story 1 (Display correct total from Phase Index): 2-3 hours
- Story 2 (Graceful fallback): 1 hour

**Total Range:** 3-4 hours

**Confidence:** High

**Reasoning:**
- Well-understood codebase - Phase Index parsing already exists
- Minimal code changes - single function modification in computeProgress
- Clear requirements - source of truth is defined (Phase Index)
- Good test coverage exists - just need to add new test cases
- No external dependencies or integrations

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests**: Pure logic in computeProgress function
2. **Integration Tests**: End-to-end workflow progress computation with file I/O

**Story-Specific Testing Notes:**

**Story 1:**
- Unit: Test computeProgress with phaseIndex containing 6 entries, phases containing 3 files
  - Assert totalPhases = 6
- Unit: Test computeProgress with phaseIndex containing 4 entries, phases containing 4 files
  - Assert totalPhases = 4
- Integration: Create test directory with tasks.md (6 phases in index) and 3 phase files
  - Call fetchProgress
  - Assert returned WorkflowProgress.totalPhases = 6

**Story 2:**
- Unit: Test computeProgress with empty phaseIndex, 3 phase files
  - Assert totalPhases = 3 (fallback to phases.size)
- Integration: Create test directory with tasks.md (no Phase Index) and 2 phase files
  - Call fetchProgress
  - Assert returned WorkflowProgress.totalPhases = 2

**Test Data Strategy:**
- Use test fixtures with synthetic tasks.md files
- Create minimal phase task files (just headers, no tasks needed)
- Leverage existing file I/O injection pattern for unit tests (mock file reads)

**Regression Coverage:**
- Existing WorkflowProgressServiceTests should continue to pass
- Verify current phase detection still works correctly
- Ensure dashboard rendering doesn't break

## Deployment Considerations

### Database Changes
None - this is a pure computation change.

### Configuration Changes
None required.

### Rollout Strategy
This can be deployed immediately as a single commit:
- Fix is transparent to users
- No feature flags needed
- Backward compatible (fallback for missing Phase Index)

### Rollback Plan
Simple git revert if issues arise. No data migration or state changes to undo.

## Dependencies

### Prerequisites
- None - all required code and data structures already exist

### Story Dependencies
- Story 2 should be implemented together with Story 1 (simple if/else branch)
- Stories form a single logical fix

### External Blockers
None

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1 + Story 2 together**: Core fix + fallback logic in one commit (3-4h total)

**Rationale:** These stories are tightly coupled and tiny. Implementing them together:
- Ensures backward compatibility from the start
- Avoids intermediate state that might break legacy issues
- Keeps the changeset focused and reviewable
- Reduces overall implementation time

**Single Iteration Plan:**
1. Add failing test for Phase Index-based totalPhases
2. Modify computeProgress to use phaseIndex.size
3. Add fallback test for empty Phase Index
4. Add fallback logic
5. Run full test suite
6. Manual verification on dashboard (optional)

## Documentation Requirements

- [ ] Update code comments in WorkflowProgressService.computeProgress to explain Phase Index as source of truth
- [ ] Add test documentation explaining fallback behavior
- [ ] No user-facing docs needed (transparent bug fix)

---

**Analysis Status:** Ready for Implementation

**Next Steps:**
1. `/iterative-works:ag-create-tasks IW-100` - Generate phase-based task index
2. `/iterative-works:ag-implement IW-100` - Begin implementation
