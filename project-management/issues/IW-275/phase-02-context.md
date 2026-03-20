# Phase 2: Batch implementation decision logic

**Issue:** IW-275
**Phase:** 2 of 3
**Story:** Pure decision functions for batch implementation orchestration live in `model/BatchImplement`, fully testable without I/O

## Goals

Create `model/BatchImplement.scala` containing all pure decision logic that the `batch-implement` command (Phase 3) will call. This is the functional core of the batch implementation loop: given the current state (status string, tasks content, phase index), return the next action to take. No file reads, no process launches, no side effects.

## Scope

### In Scope
- `BatchImplement` object with pure functions for:
  - **Phase outcome decision:** given a status string, determine the action (merge PR, mark done, recover, fail)
  - **Terminal status check:** determine if a status requires no further action
  - **Next phase selection:** given `List[PhaseIndexEntry]`, return the next unchecked phase number or `None`
  - **Workflow type resolution:** given a `workflowType: Option[String]`, map to `ag`/`wf` short code
  - **Tasks.md phase completion:** given file content (String) and a phase number (Int), return updated content with the phase checkbox checked off
- `PhaseOutcome` ADT (enum) for phase outcomes
- Unit tests for every function with exhaustive coverage of branches

### Out of Scope
- Reading files from disk (Phase 3 does I/O)
- Running `claude -p` or any subprocess
- Creating the `batch-implement.scala` command script (Phase 3)
- Adding new adapter methods (none needed)
- Modifying `MarkdownTaskParser` (already moved in Phase 1)
- Modifying `ReviewState` or any other existing model types
- Recovery prompt selection (the command script in Phase 3 builds prompts from context)

## Dependencies

### From Prior Phases
- **Phase 1:** `MarkdownTaskParser` is now in `iw.core.model` with `PhaseIndexEntry(phaseNumber: Int, isComplete: Boolean, name: String)`

### Existing Types Used
- `PhaseIndexEntry` from `iw.core.model.MarkdownTaskParser` — has `phaseNumber: Int`, `isComplete: Boolean`, `name: String`

### Important: ReviewState `status` Field

The `ReviewState` Scala model does **not** include a `status` field — it exists only in the JSON. The `batch-implement` command (Phase 3) will extract the status string from raw JSON via `ujson`. Functions in this phase accept primitive types (`String`, `Option[String]`) rather than `ReviewState`.

Known status values from the workflow system:

| Status | Meaning | Outcome |
|--------|---------|---------|
| `analysis_ready` | Analysis created | Terminal (not a phase status) |
| `context_ready` | Phase context created | Recover — re-run implementation |
| `tasks_ready` | Phase tasks created | Recover — re-run implementation |
| `implementing` | Implementation running | Recover — re-run implementation |
| `awaiting_review` | PR created, waiting for review | MergePR |
| `review_failed` | Code review failed | Recover — re-run with review fix prompt |
| `phase_merged` | Phase PR merged | MarkDone |
| `refactoring_complete` | Mid-phase refactoring done | Recover — re-run implementation |
| `all_complete` | All phases finished | Terminal |

### No External Dependencies
- This phase adds no new library dependencies

## Approach

### Step 1: Define the PhaseOutcome ADT

Create an enum representing the possible outcomes after a phase completes:

```scala
enum PhaseOutcome:
  case MergePR           // Phase succeeded, PR should be merged
  case MarkDone          // Phase already merged/done, skip
  case Recover(prompt: String) // Phase needs retry with a specific prompt
  case Fail(reason: String)    // Unrecoverable failure
```

### Step 2: Write tests first (TDD)

Create `.iw/core/test/BatchImplementTest.scala` with tests for each function before implementing. Follow the pattern established in `MarkdownTaskParserTest.scala`:
- Import from `iw.core.model`
- Use `munit.FunSuite`
- Inline test data (no test resource files needed)

### Step 3: Implement `BatchImplement` object

Implement each function to make the tests pass:

1. **`decideOutcome(status: String): PhaseOutcome`**
   - Takes the raw `status` string from `review-state.json`
   - Maps status values to outcomes per the table above
   - `"awaiting_review"` → `MergePR`
   - `"phase_merged"` → `MarkDone`
   - `"all_complete"` → `MarkDone`
   - `"context_ready"`, `"tasks_ready"`, `"implementing"`, `"refactoring_complete"` → `Recover` with descriptive prompt
   - `"review_failed"` → `Recover` with review-fix prompt
   - Unknown status → `Fail` with descriptive reason

2. **`isTerminal(status: String): Boolean`**
   - Returns true for statuses that mean the phase loop should stop: `"all_complete"`, `"phase_merged"`
   - Returns false for all others

3. **`nextPhase(phases: List[PhaseIndexEntry]): Option[Int]`**
   - Returns the phase number of the first unchecked phase, or `None` if all complete
   - Simple: `phases.find(!_.isComplete).map(_.phaseNumber)`

4. **`resolveWorkflowCode(workflowType: Option[String]): Either[String, String]`**
   - Maps: `Some("agile")` → `Right("ag")`, `Some("waterfall")` → `Right("wf")`
   - Returns `Left` for `None`, `Some("diagnostic")`, or unrecognized values
   - `"diagnostic"` is valid but NOT batch-implementable

5. **`markPhaseComplete(tasksContent: String, phaseNumber: Int): Either[String, String]`**
   - Finds the line matching `- [ ] Phase N:` and replaces `[ ]` with `[x]`
   - Returns updated content as `Right`, or `Left` if the phase line is not found or already checked
   - Uses regex similar to `MarkdownTaskParser.parsePhaseIndex` pattern

### Step 4: Verify compilation and tests

- `scala-cli compile --scalac-option -Werror .iw/core/`
- `./iw test unit`

## Files to Modify

### Create New
- **`.iw/core/model/BatchImplement.scala`**
  - Package: `iw.core.model`
  - Contains: `PhaseOutcome` enum + `BatchImplement` object with pure functions
  - Imports: only `iw.core.model.PhaseIndexEntry` (same package, may not need explicit import)
  - PURPOSE comments as required by project rules

- **`.iw/core/test/BatchImplementTest.scala`**
  - Package: `iw.core.model` (test for model code)
  - Tests all functions in `BatchImplement` with munit
  - Inline test data using `PhaseIndexEntry(...)` constructors and status strings

### No Existing Files Modified
- This phase creates new files only; no modifications to existing code

## Testing Strategy

### Unit Tests (`.iw/core/test/BatchImplementTest.scala`)

**`decideOutcome` tests:**
- `"awaiting_review"` → `MergePR`
- `"phase_merged"` → `MarkDone`
- `"all_complete"` → `MarkDone`
- `"context_ready"` → `Recover(...)` with prompt mentioning context
- `"tasks_ready"` → `Recover(...)` with prompt mentioning tasks
- `"implementing"` → `Recover(...)` with prompt mentioning implementation
- `"review_failed"` → `Recover(...)` with prompt mentioning review fixes
- `"refactoring_complete"` → `Recover(...)` with prompt mentioning refactoring
- Unknown status string → `Fail` with descriptive reason

**`isTerminal` tests:**
- `"all_complete"` → true
- `"phase_merged"` → true
- `"implementing"` → false
- `"awaiting_review"` → false
- `"review_failed"` → false
- Unknown status → false

**`nextPhase` tests:**
- Empty list → None
- All phases complete → None
- First phase incomplete → Some(1)
- Middle phase incomplete (1 done, 2 not, 3 not) → Some(2)
- Only last phase incomplete → Some(N)
- Single incomplete phase → Some(that phase number)

**`resolveWorkflowCode` tests:**
- `Some("agile")` → `Right("ag")`
- `Some("waterfall")` → `Right("wf")`
- `Some("diagnostic")` → `Left(...)` (not batch-implementable)
- `None` → `Left(...)`
- `Some("unknown")` → `Left(...)`

**`markPhaseComplete` tests:**
- Content with unchecked phase → content with that phase checked
- Content with already-checked phase → `Left` (idempotency guard)
- Content with no matching phase number → `Left`
- Content with multiple phases, only target gets checked
- Preserves all other content unchanged (other phases, notes, etc.)

### Test Data Strategy
- All test data is inline: status strings, `PhaseIndexEntry(...)` constructors, tasks.md content strings
- No test resource files needed
- No mocking needed (everything is pure)

### Verification Commands
```bash
scala-cli compile --scalac-option -Werror .iw/core/
./iw test unit
```

## Acceptance Criteria

- [ ] `.iw/core/model/BatchImplement.scala` exists with `package iw.core.model`
- [ ] `PhaseOutcome` enum defined with `MergePR`, `MarkDone`, `Recover(prompt)`, `Fail(reason)` variants
- [ ] `BatchImplement.decideOutcome` implemented and tested with all known status values
- [ ] `BatchImplement.isTerminal` implemented and tested
- [ ] `BatchImplement.nextPhase` implemented and tested
- [ ] `BatchImplement.resolveWorkflowCode` implemented and tested
- [ ] `BatchImplement.markPhaseComplete` implemented and tested
- [ ] All functions are pure (no I/O, no side effects)
- [ ] No imports from `adapters/`, `output/`, or `dashboard/`
- [ ] `scala-cli compile --scalac-option -Werror .iw/core/` passes
- [ ] All unit tests pass (`./iw test unit`)
- [ ] All existing E2E tests still pass (`./iw test e2e`)
- [ ] Changes committed
