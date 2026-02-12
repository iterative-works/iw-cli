# Phase 7 Tasks: Fix remediation via Claude Code

## Tests First (TDD)

- [x] [test] Create `BuildSystemTest.scala` with tests for detection logic
- [x] [test] Create `FixPromptTest.scala` with tests for prompt generation
- [x] [test] Run tests to confirm they fail

## Implementation - Model

- [x] [impl] Create `BuildSystem.scala` in `core/model/`
- [x] [impl] Create `FixPrompt.scala` in `core/model/`
- [x] [impl] Run unit tests

## Implementation - Doctor Command

- [x] [impl] Add `--fix` flag parsing to `doctor.scala`
- [x] [impl] Run quality checks, collect failures
- [x] [impl] Detect build system and CI platform
- [x] [impl] Generate and launch Claude Code session
- [x] [impl] Handle no-fix-needed case

## E2E Tests

- [x] [test] Add E2E test: `--fix` with all checks passing

## Verification

- [x] [verify] Run full test suite
- [x] [verify] Manual test: run `iw doctor --fix` in project root
