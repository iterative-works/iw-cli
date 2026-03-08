# Phase 1 Tasks: Domain Layer

**Issue:** IW-238
**Phase:** 1 of 3
**Context:** [phase-01-context.md](phase-01-context.md)

## Setup

- [x] [setup] Verify all existing tests pass (`./iw test unit`) to establish a clean baseline
- [x] [setup] Create `.iw/core/model/PhaseBranch.scala` with package declaration and PURPOSE header
- [x] [setup] Create `.iw/core/model/CommitMessage.scala` with package declaration and PURPOSE header
- [x] [setup] Create `.iw/core/model/PhaseTaskFile.scala` with package declaration and PURPOSE header
- [x] [setup] Create `.iw/core/model/PhaseOutput.scala` with package declaration and PURPOSE header

## Tests First (TDD)

### PhaseNumber / PhaseBranch tests

- [x] [test] [x] [reviewed] Create `.iw/core/test/PhaseBranchTest.scala` with test: `PhaseNumber.parse("1")` returns `Right` with value "01"
- [x] [test] [x] [reviewed] Test: `PhaseNumber.parse("03")` returns `Right` with value "03" (already zero-padded)
- [x] [test] [x] [reviewed] Test: `PhaseNumber.parse("12")` returns `Right` with value "12"
- [x] [test] [x] [reviewed] Test: `PhaseNumber.parse("0")` returns `Left` (phases are 1-based)
- [x] [test] [x] [reviewed] Test: `PhaseNumber.parse("100")` returns `Left` (exceeds two-digit format)
- [x] [test] [x] [reviewed] Test: `PhaseNumber.parse("-1")` returns `Left`
- [x] [test] [x] [reviewed] Test: `PhaseNumber.parse("abc")` returns `Left`
- [x] [test] [x] [reviewed] Test: `PhaseNumber.parse("")` returns `Left`
- [x] [test] [x] [reviewed] Test: `PhaseNumber.toInt` returns integer value (e.g. "03" -> 3)
- [x] [test] [x] [reviewed] Test: `PhaseBranch("IW-238", phaseNum).branchName` returns "IW-238-phase-01"
- [x] [test] [x] [reviewed] Test: `PhaseBranch` with multi-digit phase number returns correct branch name
- [x] [test] [x] [reviewed] Test: `PhaseBranch` with feature branch containing "-phase-" produces concatenated name (no special handling)

### CommitMessage tests

- [x] [test] [x] [reviewed] Create `.iw/core/test/CommitMessageTest.scala` with test: title only produces single-line message
- [x] [test] [x] [reviewed] Test: title with items produces title, blank line, and bulleted list
- [x] [test] [x] [reviewed] Test: single item produces one bullet line
- [x] [test] [x] [reviewed] Test: empty items list produces title-only message (no trailing blank line)
- [x] [test] [x] [reviewed] Test: items with leading/trailing whitespace are trimmed
- [x] [test] [x] [reviewed] Test: title with trailing newline is trimmed

### PhaseTaskFile tests

- [x] [test] [x] [reviewed] Create `.iw/core/test/PhaseTaskFileTest.scala` with test: `markComplete` updates `**Phase Status:** Not Started` to `**Phase Status:** Complete`
- [x] [test] [x] [reviewed] Test: `markComplete` is idempotent on already-complete content
- [x] [test] [x] [reviewed] Test: `markComplete` appends status line when none exists
- [x] [test] [x] [reviewed] Test: `markComplete` handles `**Phase Status:** Ready for Implementation` (arbitrary existing value)
- [x] [test] [x] [reviewed] Test: `markComplete` leaves `## Phase Status:` heading format unchanged (only modifies bold format)
- [x] [test] [x] [reviewed] Test: `markComplete` preserves all other lines in the file
- [x] [test] [x] [reviewed] Test: `markReviewed` marks `- [x] [impl] [x] [reviewed]` as `- [x] [impl] [x] [reviewed]`
- [x] [test] [x] [reviewed] Test: `markReviewed` marks `- [x] [test] [x] [reviewed]` as `- [x] [test] [x] [reviewed]`
- [x] [test] [x] [reviewed] Test: `markReviewed` does not touch `- [ ] [impl] [x] [reviewed]` (primary checkbox unchecked)
- [x] [test] [x] [reviewed] Test: `markReviewed` does not touch `- [x] [impl]` lines without `[reviewed]` marker
- [x] [test] [x] [reviewed] Test: `markReviewed` is idempotent on already-reviewed lines `- [x] [impl] [x] [reviewed]`
- [x] [test] [x] [reviewed] Test: `markReviewed` preserves non-checkbox lines exactly
- [x] [test] [x] [reviewed] Test: `markReviewed` handles mixed content (some lines with `[reviewed]`, some without)

### PhaseOutput tests

- [x] [test] [x] [reviewed] Create `.iw/core/test/PhaseOutputTest.scala` with test: `StartOutput.toJson` produces valid pretty-printed JSON with all fields
- [x] [test] [x] [reviewed] Test: `CommitOutput.toJson` produces valid JSON with correct field names and types
- [x] [test] [x] [reviewed] Test: `PrOutput.toJson` serializes `merged` as JSON boolean
- [x] [test] [x] [reviewed] Test: `PrOutput.toJson` with `merged = false` serializes as `false`
- [x] [test] [x] [reviewed] Test: empty string fields serialize as `""`, not omitted
- [x] [test] [x] [reviewed] Test: JSON output is indented (pretty-printed with indent = 2)

## Implementation

### PhaseNumber / PhaseBranch

- [x] [impl] [x] [reviewed] Implement `PhaseNumber` opaque type with `parse`, `value`, and `toInt`
- [x] [impl] [x] [reviewed] Implement `PhaseBranch` case class with `branchName` derivation
- [x] [test] Run `PhaseBranchTest` and verify all tests pass

### CommitMessage

- [x] [impl] [x] [reviewed] Implement `CommitMessage.build` with title and optional items formatting
- [x] [test] Run `CommitMessageTest` and verify all tests pass

### PhaseTaskFile

- [x] [impl] [x] [reviewed] Implement `PhaseTaskFile.markComplete` (find and replace Phase Status line)
- [x] [impl] [x] [reviewed] Implement `PhaseTaskFile.markReviewed` (update reviewed checkboxes on checked impl lines)
- [x] [test] Run `PhaseTaskFileTest` and verify all tests pass

### PhaseOutput

- [x] [impl] [x] [reviewed] Implement `StartOutput` case class with `toJson` using `ujson`
- [x] [impl] [x] [reviewed] Implement `CommitOutput` case class with `toJson` using `ujson`
- [x] [impl] [x] [reviewed] Implement `PrOutput` case class with `toJson` using `ujson`
- [x] [test] Run `PhaseOutputTest` and verify all tests pass

## Integration

- [x] [int] Run full unit test suite (`./iw test unit`) -- all tests pass, no regressions
- [x] [int] Run full test suite (`./iw test`) -- no regressions
- [x] [int] Verify all new files have PURPOSE comments (two lines starting with `// PURPOSE:`)
- [x] [int] Verify all new files are in `iw.core.model` package
- [x] [int] Verify all code is pure (no I/O, no side effects, no mutable state)

**Phase Status:** Complete
