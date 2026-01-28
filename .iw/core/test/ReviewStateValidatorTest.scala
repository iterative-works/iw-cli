// PURPOSE: Unit tests for ReviewStateValidator pure validation logic
// PURPOSE: Covers valid inputs, missing fields, wrong types, nested structures, and warnings

package iw.core.test

import iw.core.model.*

class ReviewStateValidatorTest extends munit.FunSuite:

  // --- Valid inputs ---

  test("valid minimal JSON returns no errors"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": [],
      "last_updated": "2026-01-28T12:00:00Z"
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(result.isValid, s"Expected valid but got errors: ${result.errors}")
    assertEquals(result.warnings, Nil)

  test("valid full JSON returns no errors"):
    val json = """{
      "version": 1,
      "issue_id": "IW-42",
      "status": "awaiting_review",
      "artifacts": [
        {"label": "Analysis", "path": "project-management/issues/IW-42/analysis.md"},
        {"label": "Implementation Log", "path": "project-management/issues/IW-42/implementation-log.md"}
      ],
      "last_updated": "2026-01-28T17:30:00Z",
      "phase": 3,
      "step": "review",
      "branch": "IW-42",
      "pr_url": "https://github.com/iterative-works/iw-cli/pull/99",
      "git_sha": "abc1234",
      "message": "Phase 3 review complete",
      "batch_mode": true,
      "phase_checkpoints": {
        "1": {"context_sha": "7bd547909953d9414b4cd6049a411beb6258ba2b"},
        "2": {"context_sha": "d485a987e378d8193e6a211955e1709725178f00"},
        "3": {"context_sha": "e2ec5f731fb1230e03783600e83325ac70072100"}
      },
      "available_actions": [
        {"id": "implement", "label": "Start Implementation", "skill": "iterative-works:ag-implement"},
        {"id": "verify", "label": "Verify Phase", "skill": "iterative-works:ag-verify"}
      ]
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(result.isValid, s"Expected valid but got errors: ${result.errors}")
    assertEquals(result.warnings, Nil)

  // --- Parse errors ---

  test("malformed JSON returns parse error"):
    val json = "not json at all {"
    val result = ReviewStateValidator.validate(json)
    assert(!result.isValid)
    assertEquals(result.errors.size, 1)
    assert(result.errors.head.field == "root", s"Expected field 'root' but got '${result.errors.head.field}'")
    assert(result.errors.head.message.contains("parse"), s"Expected parse error but got: ${result.errors.head.message}")

  // --- Missing required fields ---

  test("missing required fields returns field-specific errors"):
    val json = """{"version": 1, "artifacts": [], "last_updated": "2026-01-28T12:00:00Z"}"""
    val result = ReviewStateValidator.validate(json)
    assert(!result.isValid)
    val errorFields = result.errors.map(_.field).toSet
    assert(errorFields.contains("issue_id"), s"Expected error for issue_id, got: $errorFields")
    assert(errorFields.contains("status"), s"Expected error for status, got: $errorFields")

  test("missing all required fields returns errors for each"):
    val json = """{}"""
    val result = ReviewStateValidator.validate(json)
    assert(!result.isValid)
    val errorFields = result.errors.map(_.field).toSet
    assert(errorFields.contains("version"), s"Missing error for version")
    assert(errorFields.contains("issue_id"), s"Missing error for issue_id")
    assert(errorFields.contains("status"), s"Missing error for status")
    assert(errorFields.contains("artifacts"), s"Missing error for artifacts")
    assert(errorFields.contains("last_updated"), s"Missing error for last_updated")

  // --- Wrong field types ---

  test("wrong type for version returns type error"):
    val json = """{
      "version": "not-a-number",
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": [],
      "last_updated": "2026-01-28T12:00:00Z"
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(!result.isValid)
    val versionErrors = result.errors.filter(_.field == "version")
    assert(versionErrors.nonEmpty, s"Expected error for version, got: ${result.errors}")

  test("version less than 1 returns error"):
    val json = """{
      "version": 0,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": [],
      "last_updated": "2026-01-28T12:00:00Z"
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(!result.isValid)
    val versionErrors = result.errors.filter(_.field == "version")
    assert(versionErrors.nonEmpty, s"Expected error for version minimum, got: ${result.errors}")

  test("wrong type for issue_id returns type error"):
    val json = """{
      "version": 1,
      "issue_id": 123,
      "status": "implementing",
      "artifacts": [],
      "last_updated": "2026-01-28T12:00:00Z"
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(!result.isValid)
    val errors = result.errors.filter(_.field == "issue_id")
    assert(errors.nonEmpty, s"Expected error for issue_id type")

  test("wrong type for artifacts returns type error"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": "should-be-array",
      "last_updated": "2026-01-28T12:00:00Z"
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(!result.isValid)
    val errors = result.errors.filter(_.field == "artifacts")
    assert(errors.nonEmpty, s"Expected error for artifacts type")

  test("multiple wrong types returns multiple errors"):
    val json = """{
      "version": "not-a-number",
      "issue_id": 123,
      "status": false,
      "artifacts": "should-be-array",
      "last_updated": "2026-01-28T12:00:00Z"
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(!result.isValid)
    assert(result.errors.size >= 4, s"Expected at least 4 errors, got ${result.errors.size}: ${result.errors}")

  // --- Status warnings ---

  test("unknown status value returns warning not error"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "unknown_status_xyz",
      "artifacts": [],
      "last_updated": "2026-01-28T12:00:00Z"
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(result.isValid, s"Unknown status should not cause errors: ${result.errors}")
    assert(result.warnings.nonEmpty, "Expected a warning for unknown status")
    assert(result.warnings.exists(_.contains("unknown_status_xyz")), s"Warning should mention the unknown status value")

  test("known status value produces no warnings"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": [],
      "last_updated": "2026-01-28T12:00:00Z"
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(result.isValid)
    assertEquals(result.warnings, Nil)

  // --- Unknown properties ---

  test("unknown top-level property returns error"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": [],
      "last_updated": "2026-01-28T12:00:00Z",
      "unknown_field": "extra"
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(!result.isValid)
    val errors = result.errors.filter(_.message.contains("unknown_field"))
    assert(errors.nonEmpty, s"Expected error for unknown property, got: ${result.errors}")

  // --- Phase accepts integer and string ---

  test("phase as integer is valid"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": [],
      "last_updated": "2026-01-28T12:00:00Z",
      "phase": 3
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(result.isValid, s"Phase as integer should be valid: ${result.errors}")

  test("phase as string is valid"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": [],
      "last_updated": "2026-01-28T12:00:00Z",
      "phase": "1-R1"
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(result.isValid, s"Phase as string should be valid: ${result.errors}")

  test("phase as boolean returns error"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": [],
      "last_updated": "2026-01-28T12:00:00Z",
      "phase": true
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(!result.isValid)
    val errors = result.errors.filter(_.field == "phase")
    assert(errors.nonEmpty, s"Expected error for phase type")

  // --- pr_url accepts string and null ---

  test("pr_url as string is valid"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": [],
      "last_updated": "2026-01-28T12:00:00Z",
      "pr_url": "https://github.com/org/repo/pull/1"
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(result.isValid, s"pr_url as string should be valid: ${result.errors}")

  test("pr_url as null is valid"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": [],
      "last_updated": "2026-01-28T12:00:00Z",
      "pr_url": null
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(result.isValid, s"pr_url as null should be valid: ${result.errors}")

  test("pr_url as integer returns error"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": [],
      "last_updated": "2026-01-28T12:00:00Z",
      "pr_url": 42
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(!result.isValid)
    val errors = result.errors.filter(_.field == "pr_url")
    assert(errors.nonEmpty, s"Expected error for pr_url type")

  // --- Nested artifact structure ---

  test("validates nested artifact structure - missing label"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": [{"path": "some/path.md"}],
      "last_updated": "2026-01-28T12:00:00Z"
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(!result.isValid)
    val errors = result.errors.filter(_.field.startsWith("artifacts"))
    assert(errors.nonEmpty, s"Expected error for missing artifact label: ${result.errors}")

  test("validates nested artifact structure - missing path"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": [{"label": "Analysis"}],
      "last_updated": "2026-01-28T12:00:00Z"
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(!result.isValid)
    val errors = result.errors.filter(_.field.startsWith("artifacts"))
    assert(errors.nonEmpty, s"Expected error for missing artifact path: ${result.errors}")

  test("validates nested artifact structure - wrong type for label"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": [{"label": 123, "path": "some/path.md"}],
      "last_updated": "2026-01-28T12:00:00Z"
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(!result.isValid)

  test("validates nested artifact structure - artifact not an object"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": ["not-an-object"],
      "last_updated": "2026-01-28T12:00:00Z"
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(!result.isValid)

  test("validates nested artifact structure - unknown property in artifact"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": [{"label": "Analysis", "path": "x.md", "extra": true}],
      "last_updated": "2026-01-28T12:00:00Z"
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(!result.isValid)
    val errors = result.errors.filter(_.field.startsWith("artifacts"))
    assert(errors.nonEmpty, s"Expected error for unknown artifact property")

  // --- Nested available_actions structure ---

  test("validates nested available_actions structure - valid"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": [],
      "last_updated": "2026-01-28T12:00:00Z",
      "available_actions": [
        {"id": "impl", "label": "Implement", "skill": "iterative-works:ag-implement"}
      ]
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(result.isValid, s"Valid actions should pass: ${result.errors}")

  test("validates nested available_actions structure - missing required field"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": [],
      "last_updated": "2026-01-28T12:00:00Z",
      "available_actions": [
        {"id": "impl", "label": "Implement"}
      ]
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(!result.isValid)
    val errors = result.errors.filter(_.field.startsWith("available_actions"))
    assert(errors.nonEmpty, s"Expected error for missing action skill: ${result.errors}")

  test("validates nested available_actions structure - unknown property"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": [],
      "last_updated": "2026-01-28T12:00:00Z",
      "available_actions": [
        {"id": "impl", "label": "Implement", "skill": "x", "extra": true}
      ]
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(!result.isValid)

  // --- Nested phase_checkpoints structure ---

  test("validates nested phase_checkpoints structure - valid"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": [],
      "last_updated": "2026-01-28T12:00:00Z",
      "phase_checkpoints": {
        "1": {"context_sha": "abc123"},
        "2": {"context_sha": "def456"}
      }
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(result.isValid, s"Valid checkpoints should pass: ${result.errors}")

  test("validates nested phase_checkpoints structure - missing context_sha"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": [],
      "last_updated": "2026-01-28T12:00:00Z",
      "phase_checkpoints": {
        "1": {}
      }
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(!result.isValid)
    val errors = result.errors.filter(_.field.startsWith("phase_checkpoints"))
    assert(errors.nonEmpty, s"Expected error for missing context_sha: ${result.errors}")

  test("validates nested phase_checkpoints structure - wrong type for context_sha"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": [],
      "last_updated": "2026-01-28T12:00:00Z",
      "phase_checkpoints": {
        "1": {"context_sha": 123}
      }
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(!result.isValid)

  test("validates nested phase_checkpoints structure - checkpoint not an object"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": [],
      "last_updated": "2026-01-28T12:00:00Z",
      "phase_checkpoints": {
        "1": "not-an-object"
      }
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(!result.isValid)

  test("validates nested phase_checkpoints structure - unknown property in checkpoint"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": [],
      "last_updated": "2026-01-28T12:00:00Z",
      "phase_checkpoints": {
        "1": {"context_sha": "abc", "extra": true}
      }
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(!result.isValid)

  // --- Non-JSON root ---

  test("JSON array at root returns error"):
    val json = """[1, 2, 3]"""
    val result = ReviewStateValidator.validate(json)
    assert(!result.isValid)
    assertEquals(result.errors.size, 1)
    assert(result.errors.head.field == "root")

  // --- Optional field type checks ---

  test("step as non-string returns error"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": [],
      "last_updated": "2026-01-28T12:00:00Z",
      "step": 42
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(!result.isValid)
    val errors = result.errors.filter(_.field == "step")
    assert(errors.nonEmpty)

  test("batch_mode as non-boolean returns error"):
    val json = """{
      "version": 1,
      "issue_id": "IW-1",
      "status": "implementing",
      "artifacts": [],
      "last_updated": "2026-01-28T12:00:00Z",
      "batch_mode": "yes"
    }"""
    val result = ReviewStateValidator.validate(json)
    assert(!result.isValid)
    val errors = result.errors.filter(_.field == "batch_mode")
    assert(errors.nonEmpty)
