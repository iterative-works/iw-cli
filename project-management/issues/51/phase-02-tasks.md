# Phase 2 Tasks: Parse and display GitHub issues with team prefix

**Issue:** #51
**Phase:** 2 of 3
**Status:** Not Started

## Implementation Tasks

### Setup

- [x] [setup] Review current IssueId.parse implementation and tests

### Tests First (TDD)

- [x] [test] Write test: parse("51", Some("IWCLI")) returns Right("IWCLI-51")
- [x] [test] Write test: parse("51", None) returns Right("51") (backward compat)
- [x] [test] Write test: parse("IWCLI-51", Some("IWCLI")) returns Right("IWCLI-51")
- [x] [test] Write test: parse("IWCLI-51", None) returns Right("IWCLI-51")
- [x] [test] Write test: parse("IWCLI-51", Some("OTHER")) returns Right("IWCLI-51") (explicit wins)
- [x] [test] Write test: parse with invalid input returns Left error

### Implementation

- [x] [impl] Update IssueId.parse signature to accept optional defaultTeam parameter
- [x] [impl] Update parse logic to compose TEAM-NNN when numeric + team prefix provided
- [x] [impl] Verify all existing parse tests still pass

### Command Integration

- [x] [test] Write E2E test: iw issue 51 with team prefix config fetches issue
- [x] [test] Write E2E test: iw issue IWCLI-51 fetches issue correctly
- [x] [impl] Update issue command to load config and pass team prefix to parse
- [x] [impl] Review and update other commands if needed (start, comment, etc.)

### Verification

- [x] [verify] Run full test suite (unit + E2E)
- [x] [verify] Manual test: iw issue 51 with GitHub project + team prefix
- [x] [verify] Manual test: iw issue IWCLI-51 works correctly

## Task Notes

- Tests written BEFORE implementation (TDD)
- Each [test] task should be followed by its corresponding [impl] task
- Backward compatibility is critical - parse without team prefix must work as before
- Phase 3 will remove the backward compatibility for bare numeric

## Acceptance Checklist

- [ ] IssueId.parse accepts optional team prefix parameter
- [ ] Numeric input with team prefix composes TEAM-NNN format
- [ ] Full format input (IWCLI-51) works regardless of team prefix
- [ ] All existing tests pass (no regressions)
- [ ] E2E tests verify command behavior
- [ ] Backward compatibility maintained for projects without team prefix
