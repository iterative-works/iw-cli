# Phase 5 Tasks: Display phase and task progress

**Issue:** IWLE-100
**Phase:** 5 of 7
**Estimated Effort:** 8-12 hours
**Created:** 2025-12-20

---

## Setup

- [ ] [setup] Verify munit test framework available in project
- [ ] [setup] Review existing upickle serialization patterns in StateRepository
- [ ] [setup] Create test fixtures directory for markdown task files

---

## Part 1: Domain Models (1-2h)

### PhaseInfo Model

- [x] [test] Write test: PhaseInfo.isComplete returns true when all tasks done
- [x] [test] Write test: PhaseInfo.isInProgress returns true for partial completion
- [x] [test] Write test: PhaseInfo.notStarted returns true when zero tasks done
- [x] [test] Write test: PhaseInfo.progressPercentage calculates correctly (8/15 = 53%)
- [x] [test] Write test: PhaseInfo.progressPercentage returns 0 for empty phase (0/0)
- [x] [impl] Create PhaseInfo.scala case class with computed properties
- [x] [impl] Verify all PhaseInfo tests pass

### WorkflowProgress Model

- [x] [test] Write test: WorkflowProgress.currentPhaseInfo returns current phase details
- [x] [test] Write test: WorkflowProgress.currentPhaseInfo returns None when no current phase
- [x] [test] Write test: WorkflowProgress.overallPercentage calculates across all phases
- [x] [test] Write test: WorkflowProgress.overallPercentage returns 0 when total is 0
- [x] [impl] Create WorkflowProgress.scala case class with navigation methods
- [x] [impl] Verify all WorkflowProgress tests pass

### CachedProgress Model

- [x] [test] Write test: CachedProgress.isValid returns true when all mtimes match
- [x] [test] Write test: CachedProgress.isValid returns false when any mtime changed
- [x] [test] Write test: CachedProgress.isValid returns false when new file added
- [x] [test] Write test: CachedProgress.isValid returns false when file removed
- [x] [impl] Create CachedProgress.scala case class with validation logic
- [x] [impl] Verify all CachedProgress tests pass

### Commit Domain Models

- [x] [impl] Run all domain model tests together and verify pass
- [x] [impl] Commit: "feat(IWLE-100): Add WorkflowProgress domain models"

---

## Part 2: Markdown Task Parser (2-3h)

### Checkbox Counting

- [x] [test] Write test: parseTasks counts incomplete checkbox `- [ ]`
- [x] [test] Write test: parseTasks counts completed checkbox `- [x]`
- [x] [test] Write test: parseTasks counts completed checkbox case-insensitive `- [X]`
- [x] [test] Write test: parseTasks counts mixed checkboxes correctly
- [x] [test] Write test: parseTasks ignores non-checkbox bullets (*, +, 1.)
- [x] [test] Write test: parseTasks handles indented checkboxes
- [x] [test] Write test: parseTasks handles empty input (returns 0/0)
- [x] [impl] Create MarkdownTaskParser.scala object with parseTasks method
- [x] [impl] Implement regex pattern for checkbox detection
- [x] [impl] Verify checkbox counting tests pass

### Phase Name Extraction

- [x] [test] Write test: extractPhaseName parses "# Phase 2 Tasks: Name" format
- [x] [test] Write test: extractPhaseName parses "# Phase 3: Name" format
- [x] [test] Write test: extractPhaseName returns None for missing header
- [x] [test] Write test: extractPhaseName handles multiple headers (picks first)
- [x] [impl] Implement extractPhaseName with regex pattern matching
- [x] [impl] Verify phase name extraction tests pass

### Commit Parser

- [x] [impl] Run all parser tests together and verify pass
- [x] [impl] Commit: "feat(IWLE-100): Add MarkdownTaskParser for checkbox counting"

---

## Part 3: WorkflowProgressService (2-3h)

### Progress Computation

- [ ] [test] Write test: computeProgress sums task counts across phases
- [ ] [test] Write test: computeProgress handles empty phase list
- [ ] [test] Write test: computeProgress calculates overall percentage correctly
- [ ] [impl] Implement computeProgress pure function
- [ ] [impl] Verify computeProgress tests pass

### Current Phase Detection

- [ ] [test] Write test: determineCurrentPhase returns first incomplete phase
- [ ] [test] Write test: determineCurrentPhase returns last phase if all complete
- [ ] [test] Write test: determineCurrentPhase returns None for empty list
- [ ] [test] Write test: determineCurrentPhase skips not-started phases after in-progress
- [ ] [impl] Implement determineCurrentPhase logic
- [ ] [impl] Verify determineCurrentPhase tests pass

### Cache Integration

- [ ] [test] Write test: fetchProgress uses cache when mtimes match (no file reads)
- [ ] [test] Write test: fetchProgress re-parses when mtime changed
- [ ] [test] Write test: fetchProgress re-parses when new phase file added
- [ ] [test] Write test: fetchProgress handles missing directory gracefully
- [ ] [test] Write test: fetchProgress handles read errors gracefully
- [ ] [impl] Create WorkflowProgressService.scala with fetchProgress method
- [ ] [impl] Implement file I/O injection pattern (readFile, getMtime functions)
- [ ] [impl] Implement cache validation and file discovery logic
- [ ] [impl] Verify all service tests pass

### Commit Service

- [ ] [impl] Run all WorkflowProgressService tests and verify pass
- [ ] [impl] Commit: "feat(IWLE-100): Add WorkflowProgressService with cache support"

---

## Part 4: State Repository Extension (0.5-1h)

### ServerState Extension

- [ ] [impl] Add progressCache field to ServerState case class
- [ ] [impl] Add default value Map.empty for backward compatibility
- [ ] [test] Write test: ServerState with progressCache serializes to JSON
- [ ] [test] Write test: ServerState with progressCache deserializes from JSON
- [ ] [test] Write test: Old state.json without progressCache loads successfully
- [ ] [impl] Add upickle ReadWriter instances for PhaseInfo
- [ ] [impl] Add upickle ReadWriter instances for WorkflowProgress
- [ ] [impl] Add upickle ReadWriter instances for CachedProgress
- [ ] [impl] Verify serialization tests pass

### Commit State Extension

- [ ] [impl] Run state repository tests and verify pass
- [ ] [impl] Commit: "feat(IWLE-100): Extend ServerState with progress cache"

---

## Part 5: Dashboard Integration (2-3h)

### DashboardService Modification

- [ ] [impl] Add progressCache parameter to DashboardService.renderDashboard
- [ ] [impl] Create fetchProgressForWorktree helper method
- [ ] [impl] Implement file I/O wrapper functions (readFile, getMtime with Try/Either)
- [ ] [impl] Call WorkflowProgressService.fetchProgress for each worktree
- [ ] [impl] Handle errors gracefully (return None on failure)
- [ ] [test] Write test: renderDashboard includes progress data when available
- [ ] [test] Write test: renderDashboard handles missing task files gracefully
- [ ] [test] Write test: renderDashboard handles file read errors gracefully
- [ ] [impl] Verify dashboard service tests pass

### CaskServer Integration

- [ ] [impl] Pass progressCache from ServerState to DashboardService
- [ ] [impl] Update progress cache after fetching (new mtimes)
- [ ] [impl] Save updated state with new progress cache
- [ ] [test] Manual test: Start server, verify progress fetching works
- [ ] [impl] Commit: "feat(IWLE-100): Integrate progress fetching in dashboard service"

---

## Part 6: View Layer Enhancement (1-2h)

### WorktreeListView Modification

- [ ] [impl] Add progress parameter to renderWorktreeCard method signature
- [ ] [impl] Add phase info display section in card template
- [ ] [impl] Render phase label: "Phase N/Total: Phase Name"
- [ ] [impl] Create progress bar HTML structure
- [ ] [impl] Add progress bar width calculation (percentage)
- [ ] [impl] Add task count label: "X/Y tasks"
- [ ] [impl] Add conditional rendering (only show if progress available)
- [ ] [impl] Handle edge case: no current phase (show nothing)
- [ ] [impl] Handle edge case: 0 total tasks (show "no tasks defined")

### Progress Bar Styling

- [ ] [impl] Add CSS for .phase-info container
- [ ] [impl] Add CSS for .phase-label styling
- [ ] [impl] Add CSS for .progress-container background
- [ ] [impl] Add CSS for .progress-bar gradient fill
- [ ] [impl] Add CSS for .progress-text overlay
- [ ] [impl] Add CSS for mobile responsive behavior
- [ ] [test] Manual test: Verify progress bar renders correctly
- [ ] [test] Manual test: Verify progress bar width matches percentage
- [ ] [test] Manual test: Verify styling looks good on mobile

### Commit View Changes

- [ ] [impl] Run manual tests and verify visual correctness
- [ ] [impl] Commit: "feat(IWLE-100): Enhance worktree cards with progress bar display"

---

## Part 7: File System Integration (1h)

### Phase File Detection

- [ ] [impl] Implement phase file listing logic (java.nio.file.Files)
- [ ] [impl] Filter for phase-NN-tasks.md pattern (regex: phase-\d{2}-tasks\.md)
- [ ] [impl] Extract phase number from filename (phase-02-tasks.md → 2)
- [ ] [impl] Sort phase files by phase number
- [ ] [test] Write test: Phase file detection finds all valid files
- [ ] [test] Write test: Phase file detection ignores non-matching files
- [ ] [test] Write test: Phase number extraction works for all valid names
- [ ] [impl] Verify file detection tests pass

### Phase Name Fallback

- [ ] [impl] Implement fallback phase name from filename
- [ ] [test] Write test: Fallback name works when header missing (phase-02 → "Phase 2")
- [ ] [impl] Verify fallback logic works end-to-end

### Commit File System Integration

- [ ] [impl] Run filesystem integration tests and verify pass
- [ ] [impl] Commit: "feat(IWLE-100): Add phase file detection and parsing"

---

## Part 8: Error Handling & Edge Cases (1h)

### Missing Files

- [ ] [test] Manual test: Worktree with no task directory shows no progress
- [ ] [test] Manual test: Worktree with task directory but no phase files shows nothing
- [ ] [impl] Verify graceful fallback (no error, just missing progress)

### Empty Task Files

- [ ] [test] Create fixture: empty phase-01-tasks.md file
- [ ] [test] Manual test: Empty task file shows 0/0 tasks or no progress bar
- [ ] [impl] Verify 0 total tasks handled correctly

### Malformed Markdown

- [ ] [test] Create fixture: task file with mixed markdown styles
- [ ] [test] Manual test: Best-effort parsing counts valid checkboxes
- [ ] [impl] Verify parser doesn't crash on unexpected input

### File I/O Errors

- [ ] [test] Manual test: Unreadable task file (permissions) shows no progress
- [ ] [test] Manual test: File deleted between detection and read handled gracefully
- [ ] [impl] Verify all error paths return Left() with message

### Commit Error Handling

- [ ] [impl] Fix any issues found during error testing
- [ ] [impl] Commit: "fix(IWLE-100): Handle edge cases in progress parsing"

---

## Part 9: Manual E2E Verification (0.5-1h)

### Scenario Testing

- [ ] [test] Manual test: Show current phase and task completion (Scenario 1)
- [ ] [test] Manual test: Progress derived from task files (Scenario 2)
- [ ] [test] Manual test: Cache invalidation on file change (Scenario 3)
- [ ] [test] Manual test: Missing task files handled gracefully (Scenario 4)
- [ ] [test] Manual test: Empty task file (0/0 tasks) (Scenario 5)

### Real Workflow Testing

- [ ] [test] Manual test: Use actual IWLE-100 task files for testing
- [ ] [test] Manual test: Edit task file, reload dashboard, verify update
- [ ] [test] Manual test: Verify cache mtime updated in state.json
- [ ] [test] Manual test: Check dashboard on mobile browser

### Fix Issues

- [ ] [impl] Fix any issues found during E2E testing
- [ ] [impl] Commit fixes if needed

---

## Part 10: Documentation (0.5h)

### Code Documentation

- [ ] [impl] Add PURPOSE comments to PhaseInfo.scala
- [ ] [impl] Add PURPOSE comments to WorkflowProgress.scala
- [ ] [impl] Add PURPOSE comments to CachedProgress.scala
- [ ] [impl] Add PURPOSE comments to MarkdownTaskParser.scala
- [ ] [impl] Add PURPOSE comments to WorkflowProgressService.scala
- [ ] [impl] Add inline comments for regex patterns
- [ ] [impl] Add inline comments for current phase detection logic

### Implementation Log

- [ ] [impl] Update implementation-log.md with Phase 5 summary
- [ ] [impl] Document markdown parsing format assumptions
- [ ] [impl] Document cache invalidation strategy (mtime-based)
- [ ] [impl] Document edge cases handled
- [ ] [impl] Commit: "docs(IWLE-100): Document Phase 5 implementation"

---

## Completion Checklist

- [ ] All unit tests passing (PhaseInfo, WorkflowProgress, CachedProgress, MarkdownTaskParser, WorkflowProgressService)
- [ ] All integration tests passing (StateRepository serialization)
- [ ] All manual E2E scenarios verified
- [ ] No compilation warnings
- [ ] Code follows FCIS pattern (pure functions, effects at edges)
- [ ] Git commits follow TDD pattern (test → impl → refactor)
- [ ] implementation-log.md updated with Phase 5 completion
- [ ] tasks.md updated to mark Phase 5 complete

---

**Next Steps After Completion:**
1. Update tasks.md to mark Phase 5 complete
2. Run full test suite: `./iw test`
3. Create PR for Phase 5
4. Begin Phase 6 context generation
