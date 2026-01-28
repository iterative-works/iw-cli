// PURPOSE: Unit tests for ReviewStateBuilder pure JSON construction logic
// PURPOSE: Verifies correct JSON structure for various input combinations

package iw.core.test

import iw.core.model.*

class ReviewStateBuilderTest extends munit.FunSuite:

  // --- Required fields only ---

  test("build with required fields only produces valid JSON"):
    val input = ReviewStateBuilder.BuildInput(
      issueId = "IW-1",
      status = "implementing",
      lastUpdated = "2026-01-28T12:00:00Z"
    )
    val json = ReviewStateBuilder.build(input)
    val parsed = ujson.read(json)
    assertEquals(parsed("version").num.toInt, 1)
    assertEquals(parsed("issue_id").str, "IW-1")
    assertEquals(parsed("status").str, "implementing")
    assertEquals(parsed("last_updated").str, "2026-01-28T12:00:00Z")
    assertEquals(parsed("artifacts").arr.length, 0)

  // --- All fields ---

  test("build with all fields includes optional fields"):
    val input = ReviewStateBuilder.BuildInput(
      issueId = "IW-42",
      status = "awaiting_review",
      lastUpdated = "2026-01-28T17:30:00Z",
      artifacts = List(("Analysis", "project-management/issues/IW-42/analysis.md")),
      phase = Some(Left(3)),
      step = Some("review"),
      branch = Some("IW-42"),
      prUrl = Some("https://github.com/org/repo/pull/99"),
      gitSha = Some("abc1234"),
      message = Some("Phase 3 review complete"),
      batchMode = Some(true),
      phaseCheckpoints = Map("1" -> "sha1", "2" -> "sha2"),
      actions = List(("implement", "Start Implementation", "iterative-works:ag-implement"))
    )
    val json = ReviewStateBuilder.build(input)
    val parsed = ujson.read(json)
    assertEquals(parsed("phase").num.toInt, 3)
    assertEquals(parsed("step").str, "review")
    assertEquals(parsed("branch").str, "IW-42")
    assertEquals(parsed("pr_url").str, "https://github.com/org/repo/pull/99")
    assertEquals(parsed("git_sha").str, "abc1234")
    assertEquals(parsed("message").str, "Phase 3 review complete")
    assertEquals(parsed("batch_mode").bool, true)
    assertEquals(parsed("phase_checkpoints")("1")("context_sha").str, "sha1")
    assertEquals(parsed("phase_checkpoints")("2")("context_sha").str, "sha2")
    assertEquals(parsed("available_actions").arr.length, 1)
    assertEquals(parsed("available_actions")(0)("id").str, "implement")
    assertEquals(parsed("available_actions")(0)("label").str, "Start Implementation")
    assertEquals(parsed("available_actions")(0)("skill").str, "iterative-works:ag-implement")

  // --- Multiple artifacts ---

  test("build with multiple artifacts creates correct array"):
    val input = ReviewStateBuilder.BuildInput(
      issueId = "IW-1",
      status = "implementing",
      lastUpdated = "2026-01-28T12:00:00Z",
      artifacts = List(
        ("Analysis", "analysis.md"),
        ("Context", "phase-01-context.md"),
        ("Tasks", "phase-01-tasks.md")
      )
    )
    val json = ReviewStateBuilder.build(input)
    val parsed = ujson.read(json)
    val arts = parsed("artifacts").arr
    assertEquals(arts.length, 3)
    assertEquals(arts(0)("label").str, "Analysis")
    assertEquals(arts(0)("path").str, "analysis.md")
    assertEquals(arts(1)("label").str, "Context")
    assertEquals(arts(2)("label").str, "Tasks")

  // --- Multiple actions ---

  test("build with multiple actions creates available_actions"):
    val input = ReviewStateBuilder.BuildInput(
      issueId = "IW-1",
      status = "implementing",
      lastUpdated = "2026-01-28T12:00:00Z",
      actions = List(
        ("implement", "Implement", "ag-implement"),
        ("verify", "Verify", "ag-verify")
      )
    )
    val json = ReviewStateBuilder.build(input)
    val parsed = ujson.read(json)
    val actions = parsed("available_actions").arr
    assertEquals(actions.length, 2)
    assertEquals(actions(0)("id").str, "implement")
    assertEquals(actions(1)("id").str, "verify")

  // --- Phase as integer ---

  test("phase as integer produces integer in JSON"):
    val input = ReviewStateBuilder.BuildInput(
      issueId = "IW-1",
      status = "implementing",
      lastUpdated = "2026-01-28T12:00:00Z",
      phase = Some(Left(2))
    )
    val json = ReviewStateBuilder.build(input)
    val parsed = ujson.read(json)
    assert(parsed("phase").numOpt.isDefined, "Phase should be a number")
    assertEquals(parsed("phase").num.toInt, 2)

  // --- Phase as string ---

  test("phase as string produces string in JSON"):
    val input = ReviewStateBuilder.BuildInput(
      issueId = "IW-1",
      status = "implementing",
      lastUpdated = "2026-01-28T12:00:00Z",
      phase = Some(Right("1-R1"))
    )
    val json = ReviewStateBuilder.build(input)
    val parsed = ujson.read(json)
    assert(parsed("phase").strOpt.isDefined, "Phase should be a string")
    assertEquals(parsed("phase").str, "1-R1")

  // --- batch_mode flag ---

  test("batch_mode flag sets boolean true"):
    val input = ReviewStateBuilder.BuildInput(
      issueId = "IW-1",
      status = "implementing",
      lastUpdated = "2026-01-28T12:00:00Z",
      batchMode = Some(true)
    )
    val json = ReviewStateBuilder.build(input)
    val parsed = ujson.read(json)
    assertEquals(parsed("batch_mode").bool, true)

  // --- Validation pass-through ---

  test("built JSON passes ReviewStateValidator.validate()"):
    val input = ReviewStateBuilder.BuildInput(
      issueId = "IW-42",
      status = "implementing",
      lastUpdated = "2026-01-28T12:00:00Z",
      artifacts = List(("Analysis", "analysis.md")),
      phase = Some(Left(2)),
      step = Some("implementation"),
      branch = Some("IW-42"),
      gitSha = Some("abc1234"),
      message = Some("Working on phase 2"),
      batchMode = Some(true),
      phaseCheckpoints = Map("1" -> "sha123"),
      actions = List(("continue", "Continue", "ag-implement"))
    )
    val json = ReviewStateBuilder.build(input)
    val result = ReviewStateValidator.validate(json)
    assert(result.isValid, s"Built JSON should be valid but got errors: ${result.errors}")

  // --- Optional fields omitted when not provided ---

  test("optional fields are omitted when not provided"):
    val input = ReviewStateBuilder.BuildInput(
      issueId = "IW-1",
      status = "implementing",
      lastUpdated = "2026-01-28T12:00:00Z"
    )
    val json = ReviewStateBuilder.build(input)
    val parsed = ujson.read(json)
    assert(!parsed.obj.contains("phase"), "phase should be omitted")
    assert(!parsed.obj.contains("step"), "step should be omitted")
    assert(!parsed.obj.contains("branch"), "branch should be omitted")
    assert(!parsed.obj.contains("pr_url"), "pr_url should be omitted")
    assert(!parsed.obj.contains("git_sha"), "git_sha should be omitted")
    assert(!parsed.obj.contains("message"), "message should be omitted")
    assert(!parsed.obj.contains("batch_mode"), "batch_mode should be omitted")
    assert(!parsed.obj.contains("phase_checkpoints"), "phase_checkpoints should be omitted")
    assert(!parsed.obj.contains("available_actions"), "available_actions should be omitted")
