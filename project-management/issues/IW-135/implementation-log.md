# Implementation Log: Add iw config command for querying project configuration

Issue: IW-135

This log tracks the evolution of implementation across phases.

---

## Phase 1: Query specific configuration value by field name (2026-01-28)

**What was built:**
- Command: `.iw/commands/config.scala` - New command for querying configuration values
- Model: `.iw/core/model/Config.scala` - Added `ProjectConfigurationJson` object with upickle serialization
- Tests: `.iw/test/config.bats` - 9 E2E tests covering all acceptance criteria

**Decisions made:**
- Use upickle/ujson for JSON serialization and field lookup (lightweight, already in dependencies)
- Distinguish between unknown fields and known-but-unset optional fields for better error messages
- Place ReadWriter derivations in separate `ProjectConfigurationJson` object to avoid namespace pollution

**Patterns applied:**
- Functional Core: JSON serialization derivations in model layer, no I/O
- Command pattern: Following existing iw-cli command structure with PURPOSE header

**Testing:**
- E2E tests: 9 tests added
- Coverage: GitHub, Linear trackers; required and optional fields; error conditions

**Code review:**
- Iterations: 1
- Major findings: None (clean implementation)

**For next phases:**
- Available utilities: `ProjectConfigurationJson` provides JSON serialization for `ProjectConfiguration`
- Extension points: Command structure ready for `--json` flag (Phase 2) and usage help (Phase 3)
- Notes: The `optionalFields` set should be kept in sync with `ProjectConfiguration` case class

**Files changed:**
```
A  .iw/commands/config.scala
M  .iw/core/model/Config.scala
A  .iw/test/config.bats
```

---

## Phase 2: Export full configuration as JSON (2026-01-28)

**What was built:**
- Command: `.iw/commands/config.scala` - Added `--json` flag and `handleJson()` function
- Tests: `.iw/test/config.bats` - 5 new E2E tests for JSON output

**Decisions made:**
- Reuse `ProjectConfigurationJson` derivations from Phase 1 (no new model code needed)
- Output compact JSON (single line) - users can pipe to `jq` for pretty-printing
- Follow same error handling pattern as `handleGet()` for consistency

**Patterns applied:**
- DRY: Reused existing JSON serialization infrastructure
- Consistency: Same config loading pattern as Phase 1

**Testing:**
- E2E tests: 5 tests added (14 total for config command)
- Coverage: JSON validity, field content, error handling, multiple tracker types

**Code review:**
- Iterations: 1
- Major findings: None (minimal, focused implementation)

**For next phases:**
- Available utilities: Both `get` and `--json` functionality complete
- Extension points: Command ready for usage help (Phase 3)
- Notes: None

**Files changed:**
```
M  .iw/commands/config.scala
M  .iw/test/config.bats
```

---
