# Phase 3 Tasks: Write command for review state

**Issue:** IW-136
**Phase:** 3 of 3
**Phase Status:** Complete

## Git Adapter Extension

- [x] [impl] [x] [reviewed] Add `getHeadSha(dir: os.Path): Either[String, String]` to `GitAdapter`

## Domain Model

- [x] [impl] [x] [reviewed] Create `ReviewStateBuilder` in `.iw/core/model/ReviewStateBuilder.scala` - pure JSON construction from typed inputs

## Builder Logic (TDD)

- [x] [impl] [x] [reviewed] Write unit test: build with required fields only produces valid JSON
- [x] [impl] [x] [reviewed] Write unit test: build with all fields includes optional fields
- [x] [impl] [x] [reviewed] Write unit test: build with multiple artifacts creates correct array
- [x] [impl] [x] [reviewed] Write unit test: build with multiple actions creates available_actions
- [x] [impl] [x] [reviewed] Write unit test: phase as integer produces integer in JSON
- [x] [impl] [x] [reviewed] Write unit test: phase as string produces string in JSON
- [x] [impl] [x] [reviewed] Write unit test: batch_mode flag sets boolean true
- [x] [impl] [x] [reviewed] Write unit test: built JSON passes ReviewStateValidator.validate()
- [x] [impl] [x] [reviewed] Implement `ReviewStateBuilder.build()` method

## Command (CLI)

- [x] [impl] [x] [reviewed] Create `.iw/commands/write-review-state.scala` with flag parsing
- [x] [impl] [x] [reviewed] Add `--from-stdin` support for full JSON input
- [x] [impl] [x] [reviewed] Add auto-inference: issue_id from branch, git_sha from HEAD, last_updated from clock
- [x] [impl] [x] [reviewed] Add `--output` flag for explicit path, default path from issue_id
- [x] [impl] [x] [reviewed] Add validation-before-write using ReviewStateValidator

## E2E Tests

- [x] [impl] [x] [reviewed] Create `.iw/test/write-review-state.bats` with tests for:
  - Write with required flags → creates file, exit 0
  - Write with all flags → correct JSON
  - Write with --from-stdin → validates and writes
  - Invalid status in stdin → exit 1, no file
  - --output flag → writes to specified path
  - Missing --status → exit 1

## Refactoring

- [x] [impl] [x] [reviewed] Refactoring R1: Separate display structure from workflow semantics
