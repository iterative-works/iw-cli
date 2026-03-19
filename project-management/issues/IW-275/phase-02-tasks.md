# Phase 2 Tasks: Batch implementation decision logic

**Issue:** IW-275
**Phase:** 2 of 3

## Setup

- [x] [setup] Create `.iw/core/model/BatchImplement.scala` with `package iw.core.model`, PURPOSE comments, `PhaseOutcome` enum (`MergePR`, `MarkDone`, `Recover(prompt: String)`, `Fail(reason: String)`), and empty `BatchImplement` object
- [x] [setup] Create `.iw/core/test/BatchImplementTest.scala` with `package iw.core.model`, PURPOSE comments, munit imports, and empty `BatchImplementTest` class extending `munit.FunSuite`

## Tests

### decideOutcome

- [x] [test] Test `decideOutcome("awaiting_review")` returns `MergePR`
- [x] [test] Test `decideOutcome("phase_merged")` returns `MarkDone`
- [x] [test] Test `decideOutcome("all_complete")` returns `MarkDone`
- [x] [test] Test `decideOutcome("context_ready")` returns `Recover` with prompt mentioning context
- [x] [test] Test `decideOutcome("tasks_ready")` returns `Recover` with prompt mentioning tasks
- [x] [test] Test `decideOutcome("implementing")` returns `Recover` with prompt mentioning implementation
- [x] [test] Test `decideOutcome("refactoring_complete")` returns `Recover` with prompt mentioning refactoring
- [x] [test] Test `decideOutcome("review_failed")` returns `Recover` with prompt mentioning review
- [x] [test] Test `decideOutcome("bogus_status")` returns `Fail` with descriptive reason

### isTerminal

- [x] [test] Test `isTerminal("all_complete")` returns `true`
- [x] [test] Test `isTerminal("phase_merged")` returns `true`
- [x] [test] Test `isTerminal("implementing")` returns `false`
- [x] [test] Test `isTerminal("awaiting_review")` returns `false`
- [x] [test] Test `isTerminal("review_failed")` returns `false`
- [x] [test] Test `isTerminal("unknown")` returns `false`

### nextPhase

- [x] [test] Test `nextPhase(Nil)` returns `None`
- [x] [test] Test `nextPhase` with all phases complete returns `None`
- [x] [test] Test `nextPhase` with first phase incomplete returns `Some(1)`
- [x] [test] Test `nextPhase` with phase 1 done and phase 2 not done returns `Some(2)`
- [x] [test] Test `nextPhase` with only last phase incomplete returns `Some(N)`
- [x] [test] Test `nextPhase` with a single incomplete phase returns its number

### resolveWorkflowCode

- [x] [test] Test `resolveWorkflowCode(Some("agile"))` returns `Right("ag")`
- [x] [test] Test `resolveWorkflowCode(Some("waterfall"))` returns `Right("wf")`
- [x] [test] Test `resolveWorkflowCode(Some("diagnostic"))` returns `Left` (not batch-implementable)
- [x] [test] Test `resolveWorkflowCode(None)` returns `Left`
- [x] [test] Test `resolveWorkflowCode(Some("unknown"))` returns `Left`

### markPhaseComplete

- [x] [test] Test `markPhaseComplete` with unchecked phase returns content with that phase checked
- [x] [test] Test `markPhaseComplete` with already-checked phase returns `Left`
- [x] [test] Test `markPhaseComplete` with non-existent phase number returns `Left`
- [x] [test] Test `markPhaseComplete` with multiple phases only checks the target phase
- [x] [test] Test `markPhaseComplete` preserves all other content unchanged

## Implementation

- [x] [impl] Implement `BatchImplement.decideOutcome(status: String): PhaseOutcome` — map each status string to the corresponding `PhaseOutcome` variant per the status table in phase context
- [x] [impl] Implement `BatchImplement.isTerminal(status: String): Boolean` — return `true` for `"all_complete"` and `"phase_merged"`, `false` otherwise
- [x] [impl] Implement `BatchImplement.nextPhase(phases: List[PhaseIndexEntry]): Option[Int]` — return first unchecked phase number or `None`
- [x] [impl] Implement `BatchImplement.resolveWorkflowCode(workflowType: Option[String]): Either[String, String]` — map `"agile"` to `"ag"`, `"waterfall"` to `"wf"`, reject others
- [x] [impl] Implement `BatchImplement.markPhaseComplete(tasksContent: String, phaseNumber: Int): Either[String, String]` — find line matching `- [ ] Phase N:` and replace `[ ]` with `[x]`, return `Left` if not found or already checked

## Integration

- [x] [verify] Run `scala-cli compile --scalac-option -Werror .iw/core/` and verify no errors or warnings
- [x] [verify] Run `./iw test unit` and verify all tests pass (including new `BatchImplementTest`)
- [ ] [verify] Run `./iw test e2e` and verify no downstream breakage
- [ ] [verify] Commit all changes
