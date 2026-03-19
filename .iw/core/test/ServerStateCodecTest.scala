// PURPOSE: Unit tests for ServerStateCodec JSON serialization
// PURPOSE: Verifies roundtrip serialization for all domain model types and backward compatibility

package iw.tests

import iw.core.model.*
import munit.FunSuite
import java.time.Instant
import upickle.default.*

class ServerStateCodecTest extends FunSuite:
  import iw.core.model.ServerStateCodec.{given, *}

  test("roundtrip serialization of full StateJson with all caches populated"):
    val now = Instant.parse("2024-02-24T10:00:00Z")

    val worktree = WorktreeRegistration(
      issueId = "IWLE-123",
      path = "/home/user/projects/kanon-IWLE-123",
      trackerType = "linear",
      team = "IWLE",
      registeredAt = now,
      lastSeenAt = now
    )

    val issueData = IssueData(
      id = "issue-uuid-123",
      title = "Test Issue",
      status = "In Progress",
      assignee = Some("user@example.com"),
      description = Some("Test description"),
      url = "https://linear.app/issue/123",
      fetchedAt = now
    )

    val phaseInfo = PhaseInfo(
      phaseNumber = 1,
      phaseName = "Phase 1",
      taskFilePath = "/path/to/tasks.md",
      totalTasks = 10,
      completedTasks = 5
    )

    val workflowProgress = WorkflowProgress(
      currentPhase = Some(1),
      totalPhases = 3,
      phases = List(phaseInfo),
      overallCompleted = 5,
      overallTotal = 30
    )

    val prData = PullRequestData(
      number = 42,
      url = "https://github.com/org/repo/pull/42",
      state = PRState.Open,
      title = "Test PR"
    )

    val display = Display(text = "2 approvals", subtext = None, displayType = "success")
    val badge = Badge(label = "Approved", badgeType = "success")
    val taskList = TaskList(label = "Tasks", path = "/path/to/tasks.md")
    val reviewArtifact = ReviewArtifact(label = "Artifact", path = "/path/to/artifact")
    val reviewState = ReviewState(
      display = Some(display),
      badges = Some(List(badge)),
      taskLists = Some(List(taskList)),
      needsAttention = Some(false),
      message = None,
      artifacts = List(reviewArtifact)
    )

    val stateJson = StateJson(
      worktrees = Map("IWLE-123" -> worktree),
      issueCache = Map("IWLE-123" -> CachedIssue(issueData, 60)),
      progressCache = Map("IWLE-123" -> CachedProgress(workflowProgress, Map("/path/to/tasks.md" -> 123456L))),
      prCache = Map("IWLE-123" -> CachedPR(prData, now, 60)),
      reviewStateCache = Map("IWLE-123" -> CachedReviewState(reviewState, Map("/path/to/artifact" -> 123456L)))
    )

    val json = write(stateJson)
    val parsed = read[StateJson](json)

    assertEquals(parsed, stateJson)

  test("roundtrip serialization of empty StateJson"):
    val stateJson = StateJson(worktrees = Map.empty)

    val json = write(stateJson)
    val parsed = read[StateJson](json)

    assertEquals(parsed, stateJson)
    assertEquals(parsed.issueCache, Map.empty)
    assertEquals(parsed.progressCache, Map.empty)
    assertEquals(parsed.prCache, Map.empty)
    assertEquals(parsed.reviewStateCache, Map.empty)

  test("Instant timestamp preservation through serialize/deserialize"):
    val original = Instant.parse("2024-02-24T15:30:45.123Z")
    val json = write(original)
    val parsed = read[Instant](json)

    assertEquals(parsed, original)

  test("PRState enum roundtrip for Open"):
    val state = PRState.Open
    val json = write(state)
    val parsed = read[PRState](json)

    assertEquals(parsed, PRState.Open)

  test("PRState enum roundtrip for Merged"):
    val state = PRState.Merged
    val json = write(state)
    val parsed = read[PRState](json)

    assertEquals(parsed, PRState.Merged)

  test("PRState enum roundtrip for Closed"):
    val state = PRState.Closed
    val json = write(state)
    val parsed = read[PRState](json)

    assertEquals(parsed, PRState.Closed)

  test("backward compatibility - JSON with only worktrees key parses with all optional fields as empty"):
    val json = """{"worktrees":{}}"""
    val parsed = read[StateJson](json)

    assertEquals(parsed.worktrees, Map.empty)
    assertEquals(parsed.issueCache, Map.empty)
    assertEquals(parsed.progressCache, Map.empty)
    assertEquals(parsed.prCache, Map.empty)
    assertEquals(parsed.reviewStateCache, Map.empty)
    assertEquals(parsed.projects, Map.empty)

  test("ProjectRegistration roundtrip serialization"):
    val now = Instant.parse("2025-12-19T10:30:00Z")
    val reg = ProjectRegistration(
      path = "/home/user/projects/iw-cli",
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      trackerUrl = Some("https://github.com/iterative-works/iw-cli"),
      registeredAt = now
    )

    val json = write(reg)
    val parsed = read[ProjectRegistration](json)

    assertEquals(parsed, reg)

  test("ProjectRegistration roundtrip with no trackerUrl"):
    val now = Instant.parse("2025-12-19T10:30:00Z")
    val reg = ProjectRegistration(
      path = "/home/user/projects/kanon",
      projectName = "kanon",
      trackerType = "linear",
      team = "IWLE",
      trackerUrl = None,
      registeredAt = now
    )

    val json = write(reg)
    val parsed = read[ProjectRegistration](json)

    assertEquals(parsed, reg)

  test("StateJson with projects field roundtrips correctly"):
    val now = Instant.parse("2025-12-19T10:30:00Z")
    val project = ProjectRegistration(
      path = "/home/user/projects/iw-cli",
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      trackerUrl = None,
      registeredAt = now
    )

    val stateJson = StateJson(
      worktrees = Map.empty,
      projects = Map("/home/user/projects/iw-cli" -> project)
    )

    val json = write(stateJson)
    val parsed = read[StateJson](json)

    assertEquals(parsed, stateJson)
    assertEquals(parsed.projects.size, 1)
    assertEquals(parsed.projects("/home/user/projects/iw-cli"), project)

  test("full StateJson with worktrees AND projects roundtrip"):
    val now = Instant.parse("2025-12-19T10:30:00Z")
    val worktree = WorktreeRegistration(
      issueId = "IW-123",
      path = "/home/user/projects/iw-cli-IW-123",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      registeredAt = now,
      lastSeenAt = now
    )
    val project = ProjectRegistration(
      path = "/home/user/projects/iw-cli",
      projectName = "iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      trackerUrl = Some("https://github.com/iterative-works/iw-cli"),
      registeredAt = now
    )

    val stateJson = StateJson(
      worktrees = Map("IW-123" -> worktree),
      projects = Map("/home/user/projects/iw-cli" -> project)
    )

    val json = write(stateJson)
    val parsed = read[StateJson](json)

    assertEquals(parsed, stateJson)

  test("ProjectSummary roundtrip serialization"):
    val summary = ProjectSummary(
      name = "iw-cli",
      path = "/home/user/projects/iw-cli",
      trackerType = "github",
      team = "iterative-works/iw-cli",
      worktreeCount = 3
    )

    val json = write(summary)
    val parsed = read[ProjectSummary](json)

    assertEquals(parsed, summary)

  test("WorktreeSummary roundtrip with all Option fields None"):
    val summary = WorktreeSummary(
      issueId = "IW-123",
      path = "/home/user/projects/iw-cli-IW-123",
      issueTitle = None,
      issueStatus = None,
      issueUrl = None,
      prUrl = None,
      prState = None,
      activity = None,
      workflowType = None,
      workflowDisplay = None,
      needsAttention = false,
      currentPhase = None,
      totalPhases = None,
      completedTasks = None,
      totalTasks = None,
      registeredAt = None,
      lastActivityAt = None
    )

    val json = upickle.default.write[WorktreeSummary](summary)
    val parsed = upickle.default.read[WorktreeSummary](json)

    assertEquals(parsed, summary)

  test("WorktreeSummary roundtrip with all Option fields populated"):
    val summary = WorktreeSummary(
      issueId = "IW-123",
      path = "/home/user/projects/iw-cli-IW-123",
      issueTitle = Some("Test Issue"),
      issueStatus = Some("In Progress"),
      issueUrl = Some("https://linear.app/team/issue/IW-123"),
      prUrl = Some("https://github.com/org/repo/pull/42"),
      prState = Some("Open"),
      activity = Some("working"),
      workflowType = Some("waterfall"),
      workflowDisplay = Some("2 approvals"),
      needsAttention = true,
      currentPhase = Some(2),
      totalPhases = Some(4),
      completedTasks = Some(5),
      totalTasks = Some(12),
      registeredAt = Some("2024-01-01T00:00:00Z"),
      lastActivityAt = Some("2024-01-02T00:00:00Z")
    )

    val json = upickle.default.write[WorktreeSummary](summary)
    val parsed = upickle.default.read[WorktreeSummary](json)

    assertEquals(parsed, summary)

  test("WorktreeStatus roundtrip with all Option fields None"):
    val status = WorktreeStatus(
      issueId = "IW-123",
      path = "/home/user/projects/iw-cli-IW-123",
      branchName = None,
      gitClean = None,
      issueTitle = None,
      issueStatus = None,
      issueUrl = None,
      prUrl = None,
      prState = None,
      prNumber = None,
      reviewDisplay = None,
      reviewBadges = None,
      needsAttention = false,
      currentPhase = None,
      totalPhases = None,
      overallProgress = None
    )

    val json = write(status)
    val parsed = read[WorktreeStatus](json)

    assertEquals(parsed, status)

  test("WorktreeStatus roundtrip with all Option fields populated"):
    val status = WorktreeStatus(
      issueId = "IW-123",
      path = "/home/user/projects/iw-cli-IW-123",
      branchName = Some("IW-123"),
      gitClean = Some(true),
      issueTitle = Some("Test Issue"),
      issueStatus = Some("In Progress"),
      issueUrl = Some("https://linear.app/issue/123"),
      prUrl = Some("https://github.com/org/repo/pull/42"),
      prState = Some("Open"),
      prNumber = Some(42),
      reviewDisplay = Some("2 approvals"),
      reviewBadges = Some(List("Approved", "CI Passed")),
      needsAttention = true,
      currentPhase = Some(2),
      totalPhases = Some(5),
      overallProgress = Some(40)
    )

    val json = write(status)
    val parsed = read[WorktreeStatus](json)

    assertEquals(parsed, status)

  test("ReviewState with activity and workflowType roundtrips through macroRW codec"):
    val reviewArtifact = ReviewArtifact(label = "Artifact", path = "/path/to/artifact")
    val reviewState = ReviewState(
      display = None,
      badges = None,
      taskLists = None,
      needsAttention = None,
      message = None,
      artifacts = List(reviewArtifact),
      activity = Some("working"),
      workflowType = Some("agile")
    )

    val json = write(CachedReviewState(reviewState, Map.empty))
    val parsed = read[CachedReviewState](json)

    assertEquals(parsed.state.activity, Some("working"))
    assertEquals(parsed.state.workflowType, Some("agile"))

  test("ReviewState with activity and workflowType absent roundtrips correctly"):
    val reviewArtifact = ReviewArtifact(label = "Artifact", path = "/path/to/artifact")
    val reviewState = ReviewState(
      display = None,
      badges = None,
      taskLists = None,
      needsAttention = None,
      message = None,
      artifacts = List(reviewArtifact),
      activity = None,
      workflowType = None
    )

    val json = write(CachedReviewState(reviewState, Map.empty))
    val parsed = read[CachedReviewState](json)

    assertEquals(parsed.state.activity, None)
    assertEquals(parsed.state.workflowType, None)
