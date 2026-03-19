# Phase 2 Tasks: Batch implementation decision logic

**Issue:** IW-275
**Phase:** 2 of 3

## Setup

- [ ] [setup] Create `.iw/core/model/BatchImplement.scala` with `package iw.core.model`, PURPOSE comments, `PhaseOutcome` enum (`MergePR`, `MarkDone`, `Recover(prompt: String)`, `Fail(reason: String)`), and empty `BatchImplement` object
- [ ] [setup] Create `.iw/core/test/BatchImplementTest.scala` with `package iw.core.model`, PURPOSE comments, munit imports, and empty `BatchImplementTest` class extending `munit.FunSuite`

## Tests

### decideOutcome

- [ ] [test] Test `decideOutcome("awaiting_review")` returns `MergePR`
- [ ] [test] Test `decideOutcome("phase_merged")` returns `MarkDone`
- [ ] [test] Test `decideOutcome("all_complete")` returns `MarkDone`
- [ ] [test] Test `decideOutcome("context_ready")` returns `Recover` with prompt mentioning context
- [ ] [test] Test `decideOutcome("tasks_ready")` returns `Recover` with prompt mentioning tasks
- [ ] [test] Test `decideOutcome("implementing")` returns `Recover` with prompt mentioning implementation
- [ ] [test] Test `decideOutcome("refactoring_complete")` returns `Recover` with prompt mentioning refactoring
- [ ] [test] Test `decideOutcome("review_failed")` returns `Recover` with prompt mentioning review
- [ ] [test] Test `decideOutcome("bogus_status")` returns `Fail` with descriptive reason

### isTerminal

- [ ] [test] Test `isTerminal("all_complete")` returns `true`
- [ ] [test] Test `isTerminal("phase_merged")` returns `true`
- [ ] [test] Test `isTerminal("implementing")` returns `false`
- [ ] [test] Test `isTerminal("awaiting_review")` returns `false`
- [ ] [test] Test `isTerminal("review_failed")` returns `false`
- [ ] [test] Test `isTerminal("unknown")` returns `false`

### nextPhase

- [ ] [test] Test `nextPhase(Nil)` returns `None`
- [ ] [test] Test `nextPhase` with all phases complete returns `None`
- [ ] [test] Test `nextPhase` with first phase incomplete returns `Some(1)`
- [ ] [test] Test `nextPhase` with phase 1 done and phase 2 not done returns `Some(2)`
- [ ] [test] Test `nextPhase` with only last phase incomplete returns `Some(N)`
- [ ] [test] Test `nextPhase` with a single incomplete phase returns its number

### resolveWorkflowCode

- [ ] [test] Test `resolveWorkflowCode(Some("agile"))` returns `Right("ag")`
- [ ] [test] Test `resolveWorkflowCode(Some("waterfall"))` returns `Right("wf")`
- [ ] [test] Test `resolveWorkflowCode(Some("diagnostic"))` returns `Left` (not batch-implementable)
- [ ] [test] Test `resolveWorkflowCode(None)` returns `Left`
- [ ] [test] Test `resolveWorkflowCode(Some("unknown"))` returns `Left`

### markPhaseComplete

- [ ] [test] Test `markPhaseComplete` with unchecked phase returns content with that phase checked
- [ ] [test] Test `markPhaseComplete` with already-checked phase returns `Left`
- [ ] [test] Test `markPhaseComplete` with non-existent phase number returns `Left`
- [ ] [test] Test `markPhaseComplete` with multiple phases only checks the target phase
- [ ] [test] Test `markPhaseComplete` preserves all other content unchanged

## Implementation

- [ ] [impl] Implement `BatchImplement.decideOutcome(status: String): PhaseOutcome` — map each status string to the corresponding `PhaseOutcome` variant per the status table in phase context
- [ ] [impl] Implement `BatchImplement.isTerminal(status: String): Boolean` — return `true` for `"all_complete"` and `"phase_merged"`, `false` otherwise
- [ ] [impl] Implement `BatchImplement.nextPhase(phases: List[PhaseIndexEntry]): Option[Int]` — return first unchecked phase number or `None`
- [ ] [impl] Implement `BatchImplement.resolveWorkflowCode(workflowType: Option[String]): Either[String, String]` — map `"agile"` to `"ag"`, `"waterfall"` to `"wf"`, reject others
- [ ] [impl] Implement `BatchImplement.markPhaseComplete(tasksContent: String, phaseNumber: Int): Either[String, String]` — find line matching `- [ ] Phase N:` and replace `[ ]` with `[x]`, return `Left` if not found or already checked

## Integration

- [ ] [verify] Run `scala-cli compile --scalac-option -Werror .iw/core/` and verify no errors or warnings
- [ ] [verify] Run `./iw test unit` and verify all tests pass (including new `BatchImplementTest`)
- [ ] [verify] Run `./iw test e2e` and verify no downstream breakage
- [ ] [verify] Commit all changes
