# Phase 1 Tasks: Domain Layer

**Issue:** IW-238
**Phase:** 1 of 3
**Context:** [phase-01-context.md](phase-01-context.md)

## Setup

- [ ] [setup] Verify all existing tests pass (`./iw test unit`) to establish a clean baseline
- [ ] [setup] Create `.iw/core/model/PhaseBranch.scala` with package declaration and PURPOSE header
- [ ] [setup] Create `.iw/core/model/CommitMessage.scala` with package declaration and PURPOSE header
- [ ] [setup] Create `.iw/core/model/PhaseTaskFile.scala` with package declaration and PURPOSE header
- [ ] [setup] Create `.iw/core/model/PhaseOutput.scala` with package declaration and PURPOSE header

## Tests First (TDD)

### PhaseNumber / PhaseBranch tests

- [ ] [test] [ ] [reviewed] Create `.iw/core/test/PhaseBranchTest.scala` with test: `PhaseNumber.parse("1")` returns `Right` with value "01"
- [ ] [test] [ ] [reviewed] Test: `PhaseNumber.parse("03")` returns `Right` with value "03" (already zero-padded)
- [ ] [test] [ ] [reviewed] Test: `PhaseNumber.parse("12")` returns `Right` with value "12"
- [ ] [test] [ ] [reviewed] Test: `PhaseNumber.parse("0")` returns `Left` (phases are 1-based)
- [ ] [test] [ ] [reviewed] Test: `PhaseNumber.parse("100")` returns `Left` (exceeds two-digit format)
- [ ] [test] [ ] [reviewed] Test: `PhaseNumber.parse("-1")` returns `Left`
- [ ] [test] [ ] [reviewed] Test: `PhaseNumber.parse("abc")` returns `Left`
- [ ] [test] [ ] [reviewed] Test: `PhaseNumber.parse("")` returns `Left`
- [ ] [test] [ ] [reviewed] Test: `PhaseNumber.toInt` returns integer value (e.g. "03" -> 3)
- [ ] [test] [ ] [reviewed] Test: `PhaseBranch("IW-238", phaseNum).branchName` returns "IW-238-phase-01"
- [ ] [test] [ ] [reviewed] Test: `PhaseBranch` with multi-digit phase number returns correct branch name
- [ ] [test] [ ] [reviewed] Test: `PhaseBranch` with feature branch containing "-phase-" produces concatenated name (no special handling)

### CommitMessage tests

- [ ] [test] [ ] [reviewed] Create `.iw/core/test/CommitMessageTest.scala` with test: title only produces single-line message
- [ ] [test] [ ] [reviewed] Test: title with items produces title, blank line, and bulleted list
- [ ] [test] [ ] [reviewed] Test: single item produces one bullet line
- [ ] [test] [ ] [reviewed] Test: empty items list produces title-only message (no trailing blank line)
- [ ] [test] [ ] [reviewed] Test: items with leading/trailing whitespace are trimmed
- [ ] [test] [ ] [reviewed] Test: title with trailing newline is trimmed

### PhaseTaskFile tests

- [ ] [test] [ ] [reviewed] Create `.iw/core/test/PhaseTaskFileTest.scala` with test: `markComplete` updates `**Phase Status:** Not Started` to `**Phase Status:** Complete`
- [ ] [test] [ ] [reviewed] Test: `markComplete` is idempotent on already-complete content
- [ ] [test] [ ] [reviewed] Test: `markComplete` appends status line when none exists
- [ ] [test] [ ] [reviewed] Test: `markComplete` handles `**Phase Status:** Ready for Implementation` (arbitrary existing value)
- [ ] [test] [ ] [reviewed] Test: `markComplete` leaves `## Phase Status:` heading format unchanged (only modifies bold format)
- [ ] [test] [ ] [reviewed] Test: `markComplete` preserves all other lines in the file
- [ ] [test] [ ] [reviewed] Test: `markReviewed` marks `- [x] [impl] [ ] [reviewed]` as `- [x] [impl] [x] [reviewed]`
- [ ] [test] [ ] [reviewed] Test: `markReviewed` marks `- [x] [test] [ ] [reviewed]` as `- [x] [test] [x] [reviewed]`
- [ ] [test] [ ] [reviewed] Test: `markReviewed` does not touch `- [ ] [impl] [ ] [reviewed]` (primary checkbox unchecked)
- [ ] [test] [ ] [reviewed] Test: `markReviewed` does not touch `- [x] [impl]` lines without `[reviewed]` marker
- [ ] [test] [ ] [reviewed] Test: `markReviewed` is idempotent on already-reviewed lines `- [x] [impl] [x] [reviewed]`
- [ ] [test] [ ] [reviewed] Test: `markReviewed` preserves non-checkbox lines exactly
- [ ] [test] [ ] [reviewed] Test: `markReviewed` handles mixed content (some lines with `[reviewed]`, some without)

### PhaseOutput tests

- [ ] [test] [ ] [reviewed] Create `.iw/core/test/PhaseOutputTest.scala` with test: `StartOutput.toJson` produces valid pretty-printed JSON with all fields
- [ ] [test] [ ] [reviewed] Test: `CommitOutput.toJson` produces valid JSON with correct field names and types
- [ ] [test] [ ] [reviewed] Test: `PrOutput.toJson` serializes `merged` as JSON boolean
- [ ] [test] [ ] [reviewed] Test: `PrOutput.toJson` with `merged = false` serializes as `false`
- [ ] [test] [ ] [reviewed] Test: empty string fields serialize as `""`, not omitted
- [ ] [test] [ ] [reviewed] Test: JSON output is indented (pretty-printed with indent = 2)

## Implementation

### PhaseNumber / PhaseBranch

- [ ] [impl] [ ] [reviewed] Implement `PhaseNumber` opaque type with `parse`, `value`, and `toInt`
- [ ] [impl] [ ] [reviewed] Implement `PhaseBranch` case class with `branchName` derivation
- [ ] [test] Run `PhaseBranchTest` and verify all tests pass

### CommitMessage

- [ ] [impl] [ ] [reviewed] Implement `CommitMessage.build` with title and optional items formatting
- [ ] [test] Run `CommitMessageTest` and verify all tests pass

### PhaseTaskFile

- [ ] [impl] [ ] [reviewed] Implement `PhaseTaskFile.markComplete` (find and replace Phase Status line)
- [ ] [impl] [ ] [reviewed] Implement `PhaseTaskFile.markReviewed` (update reviewed checkboxes on checked impl lines)
- [ ] [test] Run `PhaseTaskFileTest` and verify all tests pass

### PhaseOutput

- [ ] [impl] [ ] [reviewed] Implement `StartOutput` case class with `toJson` using `ujson`
- [ ] [impl] [ ] [reviewed] Implement `CommitOutput` case class with `toJson` using `ujson`
- [ ] [impl] [ ] [reviewed] Implement `PrOutput` case class with `toJson` using `ujson`
- [ ] [test] Run `PhaseOutputTest` and verify all tests pass

## Integration

- [ ] [int] Run full unit test suite (`./iw test unit`) -- all tests pass, no regressions
- [ ] [int] Run full test suite (`./iw test`) -- no regressions
- [ ] [int] Verify all new files have PURPOSE comments (two lines starting with `// PURPOSE:`)
- [ ] [int] Verify all new files are in `iw.core.model` package
- [ ] [int] Verify all code is pure (no I/O, no side effects, no mutable state)

**Phase Status:** Not Started
