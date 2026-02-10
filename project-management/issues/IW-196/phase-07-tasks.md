# Phase 7 Tasks: Fix remediation via Claude Code

## Tests First (TDD)

- [ ] [test] Create `BuildSystemTest.scala` with tests for detection logic
- [ ] [test] Create `FixPromptTest.scala` with tests for prompt generation
- [ ] [test] Run tests to confirm they fail

## Implementation - Model

- [ ] [impl] Create `BuildSystem.scala` in `core/model/`
- [ ] [impl] Create `FixPrompt.scala` in `core/model/`
- [ ] [impl] Run unit tests

## Implementation - Doctor Command

- [ ] [impl] Add `--fix` flag parsing to `doctor.scala`
- [ ] [impl] Run quality checks, collect failures
- [ ] [impl] Detect build system and CI platform
- [ ] [impl] Generate and launch Claude Code session
- [ ] [impl] Handle no-fix-needed case

## E2E Tests

- [ ] [test] Add E2E test: `--fix` with all checks passing

## Verification

- [ ] [verify] Run full test suite
- [ ] [verify] Manual test: run `iw doctor --fix` in project root
