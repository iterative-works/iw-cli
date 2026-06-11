// PURPOSE: Unit tests for ReviewStateCliParser pure flag and format parsing
// PURPOSE: Absorbs review-state.bats scenarios that test argument parsing logic

package iw.core.test

import iw.core.model.*

class ReviewStateCliParserTest extends munit.FunSuite:

  // ----- extractFlag -----

  test("extractFlag returns Some when flag is followed by a value"):
    val args = List("--title", "hello", "--other", "x")
    assertEquals(
      ReviewStateCliParser.extractFlag(args, "--title"),
      Some("hello")
    )

  test("extractFlag returns None when flag is absent"):
    assertEquals(
      ReviewStateCliParser.extractFlag(List("--a", "1"), "--b"),
      None
    )

  test("extractFlag returns None when flag is last with no value"):
    assertEquals(ReviewStateCliParser.extractFlag(List("--a"), "--a"), None)

  // ----- extractRepeatedFlag -----

  test("extractRepeatedFlag collects all values for repeated flag"):
    val args = List("--badge", "A:info", "--badge", "B:warning", "--other", "x")
    assertEquals(
      ReviewStateCliParser.extractRepeatedFlag(args, "--badge"),
      List("A:info", "B:warning")
    )

  test("extractRepeatedFlag returns empty list when flag absent"):
    assertEquals(
      ReviewStateCliParser.extractRepeatedFlag(List("--a", "1"), "--b"),
      Nil
    )

  test("extractRepeatedFlag skips flag without value (value starts with --)"):
    val args = List("--badge", "--other", "v")
    assertEquals(ReviewStateCliParser.extractRepeatedFlag(args, "--badge"), Nil)

  // ----- hasFlag -----

  test("hasFlag returns true when present"):
    assertEquals(
      ReviewStateCliParser
        .hasFlag(List("--needs-attention"), "--needs-attention"),
      true
    )

  test("hasFlag returns false when absent"):
    assertEquals(ReviewStateCliParser.hasFlag(List("--a"), "--b"), false)

  // ----- parseBadge -----

  test("parseBadge accepts label:type"):
    assertEquals(
      ReviewStateCliParser.parseBadge("TDD:success"),
      Right(("TDD", "success"))
    )

  test("parseBadge with extra colons keeps only first split"):
    assertEquals(
      ReviewStateCliParser.parseBadge("CI:Build:passed"),
      Right(("CI", "Build:passed"))
    )

  test("parseBadge rejects missing colon"):
    assert(ReviewStateCliParser.parseBadge("TDD").isLeft)

  // ----- parseTaskList -----

  test("parseTaskList accepts label:path"):
    assertEquals(
      ReviewStateCliParser.parseTaskList("Phase 3:project-management/tasks.md"),
      Right(("Phase 3", "project-management/tasks.md"))
    )

  test("parseTaskList rejects missing colon"):
    assert(ReviewStateCliParser.parseTaskList("Phase3").isLeft)

  // ----- parseArtifact -----

  test("parseArtifact accepts label:path with no category"):
    assertEquals(
      ReviewStateCliParser.parseArtifact("Analysis:analysis.md"),
      Right(("Analysis", "analysis.md", None))
    )

  test("parseArtifact accepts label:path=category"):
    assertEquals(
      ReviewStateCliParser.parseArtifact("Analysis:analysis.md=input"),
      Right(("Analysis", "analysis.md", Some("input")))
    )

  test("parseArtifact splits on first colon only"):
    assertEquals(
      ReviewStateCliParser.parseArtifact(
        "Plan:project-management/issues/IW-42/phase-03.md"
      ),
      Right(("Plan", "project-management/issues/IW-42/phase-03.md", None))
    )

  test("parseArtifact uses last = for category split"):
    // If path contains =, only the last one separates category
    assertEquals(
      ReviewStateCliParser.parseArtifact("X:a=b=cat"),
      Right(("X", "a=b", Some("cat")))
    )

  test("parseArtifact rejects missing colon"):
    assert(ReviewStateCliParser.parseArtifact("Analysis").isLeft)

  // ----- parseAction -----

  test("parseAction accepts id:label:skill"):
    assertEquals(
      ReviewStateCliParser.parseAction("continue:Continue:ag-implement"),
      Right(("continue", "Continue", "ag-implement"))
    )

  test("parseAction with extra colons keeps third part as-is"):
    assertEquals(
      ReviewStateCliParser.parseAction("id:label:skill:extra"),
      Right(("id", "label", "skill:extra"))
    )

  test("parseAction rejects too few colons"):
    assert(ReviewStateCliParser.parseAction("id:label").isLeft)
    assert(ReviewStateCliParser.parseAction("id").isLeft)

  // ----- parseCheckpoint -----

  test("parseCheckpoint accepts phase:sha"):
    assertEquals(
      ReviewStateCliParser.parseCheckpoint("phase-01:abc123"),
      Right(("phase-01", "abc123"))
    )

  test("parseCheckpoint rejects missing colon"):
    assert(ReviewStateCliParser.parseCheckpoint("phase01").isLeft)

  // ----- parseArrayMode precedence -----

  test("parseArrayMode: --clear takes precedence over replace/append"):
    val args =
      List("--badge", "A:info", "--append-badge", "B:warn", "--clear-badges")
    val result = ReviewStateCliParser.parseArrayMode(
      args,
      "--badge",
      "--append-badge",
      "--clear-badges"
    )(ReviewStateCliParser.parseBadge)
    assertEquals(
      result,
      Right((Some(Nil), ReviewStateUpdater.ArrayMergeMode.Clear))
    )

  test("parseArrayMode: --replace takes precedence over append"):
    val args = List("--badge", "A:info", "--append-badge", "B:warn")
    val result = ReviewStateCliParser.parseArrayMode(
      args,
      "--badge",
      "--append-badge",
      "--clear-badges"
    )(ReviewStateCliParser.parseBadge)
    assertEquals(
      result,
      Right(
        (
          Some(List(("A", "info"))),
          ReviewStateUpdater.ArrayMergeMode.Replace
        )
      )
    )

  test("parseArrayMode: append-only when no replace and no clear"):
    val args = List("--append-badge", "A:info", "--append-badge", "B:warn")
    val result = ReviewStateCliParser.parseArrayMode(
      args,
      "--badge",
      "--append-badge",
      "--clear-badges"
    )(ReviewStateCliParser.parseBadge)
    assertEquals(
      result,
      Right(
        (
          Some(List(("A", "info"), ("B", "warn"))),
          ReviewStateUpdater.ArrayMergeMode.Append
        )
      )
    )

  test("parseArrayMode: no flags produces (None, Replace)"):
    val args = List("--something-else", "x")
    val result = ReviewStateCliParser.parseArrayMode(
      args,
      "--badge",
      "--append-badge",
      "--clear-badges"
    )(ReviewStateCliParser.parseBadge)
    assertEquals(
      result,
      Right((None, ReviewStateUpdater.ArrayMergeMode.Replace))
    )

  test("parseArrayMode propagates format errors from parser"):
    val args = List("--badge", "bogus")
    val result = ReviewStateCliParser.parseArrayMode(
      args,
      "--badge",
      "--append-badge",
      "--clear-badges"
    )(ReviewStateCliParser.parseBadge)
    assert(result.isLeft)

  // ----- parseWriteArgs (assembly) -----

  private val defaultsForWrite = ReviewStateCliParser.WriteDefaults(
    issueId = "IW-1",
    lastUpdated = "2026-01-28T12:00:00Z",
    gitSha = None
  )

  test("parseWriteArgs: minimal args returns BuildInput with defaults"):
    val result = ReviewStateCliParser.parseWriteArgs(Nil, defaultsForWrite)
    assertEquals(
      result,
      Right(
        ReviewStateBuilder.BuildInput(
          version = 2,
          issueId = "IW-1",
          lastUpdated = "2026-01-28T12:00:00Z"
        )
      )
    )

  test("parseWriteArgs: --version overrides default"):
    val result =
      ReviewStateCliParser.parseWriteArgs(
        List("--version", "1"),
        defaultsForWrite
      )
    assertEquals(result.toOption.get.version, 1)

  test("parseWriteArgs: --status sets status"):
    val result = ReviewStateCliParser.parseWriteArgs(
      List("--status", "implementing"),
      defaultsForWrite
    )
    assertEquals(result.toOption.get.status, Some("implementing"))

  test("parseWriteArgs: --display-text without --display-type returns Left"):
    val result = ReviewStateCliParser.parseWriteArgs(
      List("--display-text", "Hello"),
      defaultsForWrite
    )
    assert(result.isLeft)
    assert(result.left.toOption.get.contains("--display-type"))

  test("parseWriteArgs: --display-text + --display-type populates display"):
    val result = ReviewStateCliParser.parseWriteArgs(
      List(
        "--display-text",
        "Implementing",
        "--display-type",
        "progress",
        "--display-subtext",
        "Phase 3 of 4"
      ),
      defaultsForWrite
    )
    assertEquals(
      result.toOption.get.display,
      Some(("Implementing", Some("Phase 3 of 4"), "progress"))
    )

  test("parseWriteArgs: --badge with invalid format returns Left"):
    val result = ReviewStateCliParser.parseWriteArgs(
      List("--badge", "broken"),
      defaultsForWrite
    )
    assert(result.isLeft)

  test("parseWriteArgs: full v2 flag set assembles all fields"):
    val args = List(
      "--status",
      "awaiting_review",
      "--display-text",
      "Awaiting Review",
      "--display-subtext",
      "Phase 3 of 4",
      "--display-type",
      "warning",
      "--badge",
      "TDD:success",
      "--task-list",
      "Phase 3:project-management/tasks.md",
      "--needs-attention",
      "--message",
      "Phase 3 review complete",
      "--artifact",
      "Analysis:analysis.md=input",
      "--artifact",
      "Context:phase-03-context.md",
      "--action",
      "continue:Continue:ag-implement",
      "--pr-url",
      "https://github.com/org/repo/pull/99",
      "--activity",
      "working",
      "--workflow-type",
      "agile",
      "--checkpoint",
      "phase-01:abc123",
      "--version",
      "1"
    )
    val result = ReviewStateCliParser.parseWriteArgs(args, defaultsForWrite)
    val input = result.toOption.get
    assertEquals(input.version, 1)
    assertEquals(input.status, Some("awaiting_review"))
    assertEquals(
      input.display,
      Some(("Awaiting Review", Some("Phase 3 of 4"), "warning"))
    )
    assertEquals(input.badges, List(("TDD", "success")))
    assertEquals(
      input.taskLists,
      List(("Phase 3", "project-management/tasks.md"))
    )
    assertEquals(input.needsAttention, Some(true))
    assertEquals(input.message, Some("Phase 3 review complete"))
    assertEquals(input.artifacts.length, 2)
    assertEquals(input.artifacts(0), ("Analysis", "analysis.md", Some("input")))
    assertEquals(input.artifacts(1), ("Context", "phase-03-context.md", None))
    assertEquals(
      input.actions,
      List(("continue", "Continue", "ag-implement"))
    )
    assertEquals(input.prUrl, Some("https://github.com/org/repo/pull/99"))
    assertEquals(input.activity, Some("working"))
    assertEquals(input.workflowType, Some("agile"))
    assertEquals(input.phaseCheckpoints, Map("phase-01" -> "abc123"))

  // ----- parseUpdateArgs (assembly) -----

  test("parseUpdateArgs: empty args produces empty UpdateInput"):
    val result = ReviewStateCliParser.parseUpdateArgs(Nil)
    assertEquals(result, Right(ReviewStateUpdater.UpdateInput()))

  test("parseUpdateArgs: --display-text sets displayText"):
    val result =
      ReviewStateCliParser.parseUpdateArgs(List("--display-text", "New"))
    assertEquals(result.toOption.get.displayText, Some("New"))

  test(
    "parseUpdateArgs: --clear-message + no --message sets clearMessage=true"
  ):
    val result = ReviewStateCliParser.parseUpdateArgs(List("--clear-message"))
    val input = result.toOption.get
    assertEquals(input.message, None)
    assertEquals(input.clearMessage, true)

  test("parseUpdateArgs: --append-artifact mode is Append"):
    val result = ReviewStateCliParser.parseUpdateArgs(
      List("--append-artifact", "New:new.md")
    )
    val input = result.toOption.get
    assertEquals(
      input.artifacts,
      Some(List(("New", "new.md", None)))
    )
    assertEquals(input.artifactsMode, ReviewStateUpdater.ArrayMergeMode.Append)

  test("parseUpdateArgs: --clear-artifacts mode is Clear with empty list"):
    val result =
      ReviewStateCliParser.parseUpdateArgs(List("--clear-artifacts"))
    val input = result.toOption.get
    assertEquals(input.artifacts, Some(Nil))
    assertEquals(input.artifactsMode, ReviewStateUpdater.ArrayMergeMode.Clear)

  test("parseUpdateArgs: --activity sets activity"):
    val result =
      ReviewStateCliParser.parseUpdateArgs(List("--activity", "working"))
    assertEquals(result.toOption.get.activity, Some("working"))

  test("parseUpdateArgs: --clear-activity sets clearActivity=true"):
    val result =
      ReviewStateCliParser.parseUpdateArgs(List("--clear-activity"))
    val input = result.toOption.get
    assertEquals(input.activity, None)
    assertEquals(input.clearActivity, true)

  test("parseUpdateArgs: --workflow-type sets workflowType"):
    val result = ReviewStateCliParser.parseUpdateArgs(
      List("--workflow-type", "waterfall")
    )
    assertEquals(result.toOption.get.workflowType, Some("waterfall"))

  test("parseUpdateArgs: --clear-workflow-type sets clearWorkflowType=true"):
    val result =
      ReviewStateCliParser.parseUpdateArgs(List("--clear-workflow-type"))
    val input = result.toOption.get
    assertEquals(input.workflowType, None)
    assertEquals(input.clearWorkflowType, true)

  test("parseUpdateArgs: bad badge format returns Left"):
    val result =
      ReviewStateCliParser.parseUpdateArgs(List("--badge", "broken"))
    assert(result.isLeft)

  test("parseUpdateArgs: all clear flags individually"):
    val result = ReviewStateCliParser.parseUpdateArgs(
      List(
        "--clear-status",
        "--clear-message",
        "--clear-needs-attention",
        "--clear-pr-url",
        "--clear-display",
        "--clear-display-subtext",
        "--clear-activity",
        "--clear-workflow-type"
      )
    )
    val input = result.toOption.get
    assertEquals(input.clearStatus, true)
    assertEquals(input.clearMessage, true)
    assertEquals(input.clearNeedsAttention, true)
    assertEquals(input.clearPrUrl, true)
    assertEquals(input.clearDisplay, true)
    assertEquals(input.clearDisplaySubtext, true)
    assertEquals(input.clearActivity, true)
    assertEquals(input.clearWorkflowType, true)

  test("parseUpdateArgs: --git-sha sets gitSha"):
    val result =
      ReviewStateCliParser.parseUpdateArgs(List("--git-sha", "abc123"))
    assertEquals(result.toOption.get.gitSha, Some("abc123"))

  test("parseUpdateArgs: --checkpoint replace mode"):
    val result = ReviewStateCliParser.parseUpdateArgs(
      List("--checkpoint", "phase-01:abc", "--checkpoint", "phase-02:def")
    )
    val input = result.toOption.get
    assertEquals(
      input.phaseCheckpoints,
      Some(Map("phase-01" -> "abc", "phase-02" -> "def"))
    )
    assertEquals(
      input.phaseCheckpointsMode,
      ReviewStateUpdater.ArrayMergeMode.Replace
    )
