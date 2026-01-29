# Phase 2 Tasks: Validation command for review state

**Issue:** IW-136
**Phase:** 2 of 3
**Phase Status:** Complete

## Domain Model

- [x] [impl] [x] [reviewed] Create `ValidationError` case class in `.iw/core/model/ValidationError.scala`
- [x] [impl] [x] [reviewed] Create `ValidationResult` case class in `.iw/core/model/ValidationResult.scala`

## Validation Logic (TDD)

- [x] [impl] [x] [reviewed] Write unit test: valid minimal JSON returns no errors
- [x] [impl] [x] [reviewed] Write unit test: valid full JSON returns no errors
- [x] [impl] [x] [reviewed] Write unit test: malformed JSON returns parse error
- [x] [impl] [x] [reviewed] Write unit test: missing required fields returns field-specific errors
- [x] [impl] [x] [reviewed] Write unit test: wrong field types returns type errors
- [x] [impl] [x] [reviewed] Write unit test: unknown status value returns warning (not error)
- [x] [impl] [x] [reviewed] Write unit test: unknown top-level property returns error
- [x] [impl] [x] [reviewed] Write unit test: phase accepts both integer and string
- [x] [impl] [x] [reviewed] Write unit test: pr_url accepts both string and null
- [x] [impl] [x] [reviewed] Write unit test: validates nested artifact structure
- [x] [impl] [x] [reviewed] Write unit test: validates nested available_actions structure
- [x] [impl] [x] [reviewed] Write unit test: validates nested phase_checkpoints structure
- [x] [impl] [x] [reviewed] Implement `ReviewStateValidator.validate()` in `.iw/core/model/ReviewStateValidator.scala`

## Command (CLI)

- [x] [impl] [x] [reviewed] Create `.iw/commands/validate-review-state.scala` with file path argument support
- [x] [impl] [x] [reviewed] Add `--stdin` support to read JSON from standard input
- [x] [impl] [x] [reviewed] Format validation output: success message, error list, warnings

## E2E Tests

- [x] [impl] [x] [reviewed] Create `.iw/test/validate-review-state.bats` with tests for:
  - Valid file → exit 0
  - Invalid file (missing required) → exit 1
  - Invalid file (wrong types) → exit 1
  - Malformed JSON → exit 1
  - Non-existent file → exit 1
  - Unknown status → exit 0 with warning
