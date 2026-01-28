# Phase 3 Tasks: Write command for review state

**Issue:** IW-136
**Phase:** 3 of 3
**Phase Status:** Not Started

## Git Adapter Extension

- [ ] [impl] [ ] [reviewed] Add `getHeadSha(dir: os.Path): Either[String, String]` to `GitAdapter`

## Domain Model

- [ ] [impl] [ ] [reviewed] Create `ReviewStateBuilder` in `.iw/core/model/ReviewStateBuilder.scala` - pure JSON construction from typed inputs

## Builder Logic (TDD)

- [ ] [impl] [ ] [reviewed] Write unit test: build with required fields only produces valid JSON
- [ ] [impl] [ ] [reviewed] Write unit test: build with all fields includes optional fields
- [ ] [impl] [ ] [reviewed] Write unit test: build with multiple artifacts creates correct array
- [ ] [impl] [ ] [reviewed] Write unit test: build with multiple actions creates available_actions
- [ ] [impl] [ ] [reviewed] Write unit test: phase as integer produces integer in JSON
- [ ] [impl] [ ] [reviewed] Write unit test: phase as string produces string in JSON
- [ ] [impl] [ ] [reviewed] Write unit test: batch_mode flag sets boolean true
- [ ] [impl] [ ] [reviewed] Write unit test: built JSON passes ReviewStateValidator.validate()
- [ ] [impl] [ ] [reviewed] Implement `ReviewStateBuilder.build()` method

## Command (CLI)

- [ ] [impl] [ ] [reviewed] Create `.iw/commands/write-review-state.scala` with flag parsing
- [ ] [impl] [ ] [reviewed] Add `--from-stdin` support for full JSON input
- [ ] [impl] [ ] [reviewed] Add auto-inference: issue_id from branch, git_sha from HEAD, last_updated from clock
- [ ] [impl] [ ] [reviewed] Add `--output` flag for explicit path, default path from issue_id
- [ ] [impl] [ ] [reviewed] Add validation-before-write using ReviewStateValidator

## E2E Tests

- [ ] [impl] [ ] [reviewed] Create `.iw/test/write-review-state.bats` with tests for:
  - Write with required flags → creates file, exit 0
  - Write with all flags → correct JSON
  - Write with --from-stdin → validates and writes
  - Invalid status in stdin → exit 1, no file
  - --output flag → writes to specified path
  - Missing --status → exit 1
