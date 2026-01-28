# Phase 2: Validation command for review state

**Issue:** IW-136
**Phase:** 2 of 3
**Story:** Workflow validates state before writing

## Goals

Implement `iw validate-review-state` command that validates a review-state.json file against the formal schema defined in Phase 1. Provide clear, actionable error messages for validation failures.

## Scope

### In Scope
- `validate-review-state.scala` command in `.iw/commands/`
- Pure validation logic (domain model + application service)
- Validation of: JSON syntax, required fields, field types, nested object structure
- Warning (not error) for unknown status values
- Support file path argument and `--stdin` mode
- Exit code 0 for valid, 1 for invalid
- Unit tests for validation logic
- E2E tests (BATS) for command behavior

### Out of Scope
- Write command (Phase 3)
- Dashboard integration
- Formal JSON Schema library (manual validation with upickle per Decision 4)

## Dependencies

- Phase 1 artifacts:
  - `schemas/review-state.schema.json` - defines the contract
  - `.iw/core/test/resources/review-state/` - test fixtures

## What Was Built in Phase 1

- JSON Schema at `schemas/review-state.schema.json` with:
  - Required: version (integer ≥1), issue_id (string), status (string), artifacts (array), last_updated (string, date-time)
  - Optional: phase (integer|string), step (string), branch (string), pr_url (string|null), git_sha (string), message (string), batch_mode (boolean), phase_checkpoints (object), available_actions (array)
  - Nested definitions for artifact, action, phase_checkpoint objects
  - `additionalProperties: false` at all levels
- Test fixtures for valid and invalid cases

## Technical Approach

### Domain Layer (pure, no I/O)

Create in `.iw/core/model/`:

**ValidationError** - a single field-level error:
```scala
case class ValidationError(field: String, message: String)
```

**ValidationResult** - aggregate result with warnings:
```scala
case class ValidationResult(
  errors: List[ValidationError],
  warnings: List[String]
):
  def isValid: Boolean = errors.isEmpty
```

### Application Layer (pure logic)

Create in `.iw/core/model/` (since it's pure logic, not dashboard-specific):

**ReviewStateValidator** - validates JSON against schema rules:
```scala
object ReviewStateValidator:
  def validate(json: String): ValidationResult
```

Validation checks (in order):
1. JSON parse: Can the string be parsed as JSON?
2. Root type: Is it a JSON object?
3. Required fields: version, issue_id, status, artifacts, last_updated
4. Field types:
   - version: integer, minimum 1
   - issue_id: string
   - status: string (warn if not in known values)
   - artifacts: array of objects with required label (string) and path (string)
   - last_updated: string
5. Optional field types (if present):
   - phase: integer or string
   - step: string
   - branch: string
   - pr_url: string or null
   - git_sha: string
   - message: string
   - batch_mode: boolean
   - phase_checkpoints: object with values containing context_sha (string)
   - available_actions: array of objects with required id, label, skill (all strings)
6. No additional properties at root level

### Presentation Layer (command)

Create `.iw/commands/validate-review-state.scala`:
- Parse args: file path or `--stdin`
- Read JSON (from file or stdin)
- Call `ReviewStateValidator.validate(json)`
- Format and print results
- Exit 0 if valid, 1 if invalid

### Existing Code to Reuse

- `ReviewStateService.parseReviewStateJson()` in `.iw/core/dashboard/` - existing parser (but it's dashboard-internal, don't import from commands)
- `Output.info/error/success/warning` for consistent output formatting
- Pattern: use `os.read(path)` for file reading
- Pattern: `@main def \`validate-review-state\`(args: String*)` for command entry

### Important Architecture Rules

- Commands MUST NOT import from `iw.core.dashboard.*` (those are server internals)
- Validation logic goes in `model/` (pure, no I/O)
- Command file is the imperative shell (reads file, calls validator, formats output)
- Use `Either[String, T]` pattern for error handling

## Files to Create/Modify

1. `.iw/core/model/ValidationError.scala` - ValidationError case class
2. `.iw/core/model/ValidationResult.scala` - ValidationResult case class
3. `.iw/core/model/ReviewStateValidator.scala` - Pure validation logic
4. `.iw/commands/validate-review-state.scala` - CLI command
5. `.iw/core/test/ReviewStateValidatorTest.scala` - Unit tests
6. `.iw/test/validate-review-state.bats` - E2E tests

## Testing Strategy

### Unit Tests (.iw/core/test/ReviewStateValidatorTest.scala)
- Valid minimal JSON → no errors
- Valid full JSON → no errors
- Missing required field → error listing field name
- Wrong type for version → error
- Wrong type for artifacts → error
- Invalid artifact (missing label) → error
- Unknown status value → warning (not error)
- Unknown top-level property → error (additionalProperties: false)
- Malformed JSON → parse error
- phase as integer → valid
- phase as string → valid
- pr_url as null → valid
- phase_checkpoints with valid structure → valid
- available_actions with valid structure → valid
- Use test fixtures from Phase 1

### E2E Tests (.iw/test/validate-review-state.bats)
- Valid file → exit 0, success message
- Invalid file (missing required) → exit 1, error message
- Invalid file (wrong types) → exit 1, error message
- Malformed JSON → exit 1, parse error message
- Non-existent file → exit 1, error message
- --stdin mode with valid JSON → exit 0
- --stdin mode with invalid JSON → exit 1

## Acceptance Criteria

- [ ] Command validates against formal schema rules
- [ ] Clear, actionable error messages for validation failures
- [ ] Both JSON syntax errors and schema violations handled
- [ ] Works with file paths and stdin
- [ ] Exit codes: 0 for valid, 1 for invalid
- [ ] Unknown status values produce warnings, not errors
- [ ] Unknown top-level properties produce errors (strict mode)
- [ ] Unit tests cover all validation scenarios
- [ ] E2E tests verify command behavior
