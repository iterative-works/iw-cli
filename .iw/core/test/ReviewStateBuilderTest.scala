// PURPOSE: Unit tests for ReviewStateBuilder pure JSON construction logic
// PURPOSE: Verifies correct JSON structure for various input combinations

package iw.core.test

import iw.core.model.*

class ReviewStateBuilderTest extends munit.FunSuite:

  // --- Required fields only ---

  test("build with required fields only produces valid JSON"):
    val input = ReviewStateBuilder.BuildInput(
      issueId = "IW-1",
      lastUpdated = "2026-01-28T12:00:00Z"
    )
    val json = ReviewStateBuilder.build(input)
    val parsed = ujson.read(json)
    assertEquals(parsed("version").num.toInt, 2)
    assertEquals(parsed("issue_id").str, "IW-1")
    assertEquals(parsed("last_updated").str, "2026-01-28T12:00:00Z")
    assertEquals(parsed("artifacts").arr.length, 0)

  // --- All fields ---

  test("build with all fields includes optional fields"):
    val input = ReviewStateBuilder.BuildInput(
      issueId = "IW-42",
      lastUpdated = "2026-01-28T17:30:00Z",
      artifacts = List(("Analysis", "project-management/issues/IW-42/analysis.md", None)),
      status = Some("awaiting_review"),
      display = Some(("Awaiting Review", Some("Phase 3 of 5"), "warning")),
      badges = List(("TDD", "info")),
      taskLists = List(("Phase 3", "project-management/issues/IW-42/phase-03-tasks.md")),
      needsAttention = Some(true),
      prUrl = Some("https://github.com/org/repo/pull/99"),
      gitSha = Some("abc1234"),
      message = Some("Phase 3 review complete"),
      phaseCheckpoints = Map("1" -> "sha1", "2" -> "sha2"),
      actions = List(("implement", "Start Implementation", "iterative-works:ag-implement"))
    )
    val json = ReviewStateBuilder.build(input)
    val parsed = ujson.read(json)
    assertEquals(parsed("status").str, "awaiting_review")
    assertEquals(parsed("display")("text").str, "Awaiting Review")
    assertEquals(parsed("display")("subtext").str, "Phase 3 of 5")
    assertEquals(parsed("display")("type").str, "warning")
    assertEquals(parsed("badges").arr.length, 1)
    assertEquals(parsed("badges")(0)("label").str, "TDD")
    assertEquals(parsed("badges")(0)("type").str, "info")
    assertEquals(parsed("task_lists").arr.length, 1)
    assertEquals(parsed("task_lists")(0)("label").str, "Phase 3")
    assertEquals(parsed("task_lists")(0)("path").str, "project-management/issues/IW-42/phase-03-tasks.md")
    assertEquals(parsed("needs_attention").bool, true)
    assertEquals(parsed("pr_url").str, "https://github.com/org/repo/pull/99")
    assertEquals(parsed("git_sha").str, "abc1234")
    assertEquals(parsed("message").str, "Phase 3 review complete")
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
      lastUpdated = "2026-01-28T12:00:00Z",
      artifacts = List(
        ("Analysis", "analysis.md", None),
        ("Context", "phase-01-context.md", None),
        ("Tasks", "phase-01-tasks.md", None)
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

  // --- Artifacts with category ---

  test("build with artifact categories includes category field"):
    val input = ReviewStateBuilder.BuildInput(
      issueId = "IW-1",
      lastUpdated = "2026-01-28T12:00:00Z",
      artifacts = List(
        ("Analysis", "analysis.md", Some("input")),
        ("Log", "implementation-log.md", Some("output")),
        ("Tasks", "phase-01-tasks.md", None)
      )
    )
    val json = ReviewStateBuilder.build(input)
    val parsed = ujson.read(json)
    val arts = parsed("artifacts").arr
    assertEquals(arts.length, 3)
    assertEquals(arts(0)("category").str, "input")
    assertEquals(arts(1)("category").str, "output")
    assert(!arts(2).obj.contains("category"), "artifact without category should not have category field")

  // --- Multiple actions ---

  test("build with multiple actions creates available_actions"):
    val input = ReviewStateBuilder.BuildInput(
      issueId = "IW-1",
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

  // --- Validation pass-through ---

  test("built JSON passes ReviewStateValidator.validate()"):
    val input = ReviewStateBuilder.BuildInput(
      issueId = "IW-42",
      lastUpdated = "2026-01-28T12:00:00Z",
      artifacts = List(("Analysis", "analysis.md", None)),
      status = Some("implementing"),
      display = Some(("Implementing", Some("Phase 2"), "progress")),
      gitSha = Some("abc1234"),
      message = Some("Working on phase 2"),
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
      lastUpdated = "2026-01-28T12:00:00Z"
    )
    val json = ReviewStateBuilder.build(input)
    val parsed = ujson.read(json)
    assert(!parsed.obj.contains("status"), "status should be omitted")
    assert(!parsed.obj.contains("display"), "display should be omitted")
    assert(!parsed.obj.contains("badges"), "badges should be omitted")
    assert(!parsed.obj.contains("task_lists"), "task_lists should be omitted")
    assert(!parsed.obj.contains("needs_attention"), "needs_attention should be omitted")
    assert(!parsed.obj.contains("pr_url"), "pr_url should be omitted")
    assert(!parsed.obj.contains("git_sha"), "git_sha should be omitted")
    assert(!parsed.obj.contains("message"), "message should be omitted")
    assert(!parsed.obj.contains("phase_checkpoints"), "phase_checkpoints should be omitted")
    assert(!parsed.obj.contains("available_actions"), "available_actions should be omitted")
