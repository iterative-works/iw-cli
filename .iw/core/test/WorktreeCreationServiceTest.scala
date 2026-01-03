// PURPOSE: Unit tests for WorktreeCreationService business logic
// PURPOSE: Verifies worktree creation orchestration with injected dependencies

package iw.core.application

import munit.FunSuite
import iw.core.domain.{IssueData, WorktreeCreationResult}
import iw.core.{ProjectConfiguration, IssueTrackerType}
import java.time.Instant

class WorktreeCreationServiceTest extends FunSuite:

  val testConfig = ProjectConfiguration(
    trackerType = IssueTrackerType.GitHub,
    team = "IW",
    projectName = "iw-cli",
    repository = Some("iterative-works/iw-cli"),
    teamPrefix = Some("IW")
  )

  val testIssueData = IssueData(
    id = "IW-79",
    title = "Add worktree creation from dashboard",
    status = "In Progress",
    assignee = Some("developer"),
    description = Some("Feature description"),
    url = "https://github.com/iterative-works/iw-cli/issues/79",
    fetchedAt = Instant.now()
  )

  test("create with all dependencies succeeding returns Right with result"):
    val fetchIssue = (id: String) => Right(testIssueData)
    val createWorktree = (path: String, branch: String) => Right(())
    val createTmux = (name: String, path: String) => Right(())
    val registerWorktree = (issueId: String, path: String, trackerType: String, team: String) => Right(())

    val result = WorktreeCreationService.create(
      issueId = "IW-79",
      config = testConfig,
      fetchIssue = fetchIssue,
      createWorktree = createWorktree,
      createTmuxSession = createTmux,
      registerWorktree = registerWorktree
    )

    assert(result.isRight)
    result.foreach { r =>
      assertEquals(r.issueId, "IW-79")
      assert(r.worktreePath.contains("iw-cli-IW-79"))
      assert(r.tmuxSessionName.contains("iw-cli-IW-79"))
      assert(r.tmuxAttachCommand.contains("tmux attach"))
      assert(r.tmuxAttachCommand.contains(r.tmuxSessionName))
    }

  test("create when fetchIssue fails returns Left with error"):
    val fetchIssue = (id: String) => Left("Issue not found")
    val createWorktree = (path: String, branch: String) => Right(())
    val createTmux = (name: String, path: String) => Right(())
    val registerWorktree = (issueId: String, path: String, trackerType: String, team: String) => Right(())

    val result = WorktreeCreationService.create(
      "IW-999",
      testConfig,
      fetchIssue,
      createWorktree,
      createTmux,
      registerWorktree
    )

    assert(result.isLeft)
    result.swap.foreach { error =>
      assert(error.contains("Issue not found"))
    }

  test("create when createWorktree fails returns Left with error"):
    val fetchIssue = (id: String) => Right(testIssueData)
    val createWorktree = (path: String, branch: String) => Left("Git worktree creation failed")
    val createTmux = (name: String, path: String) => Right(())
    val registerWorktree = (issueId: String, path: String, trackerType: String, team: String) => Right(())

    val result = WorktreeCreationService.create(
      "IW-79",
      testConfig,
      fetchIssue,
      createWorktree,
      createTmux,
      registerWorktree
    )

    assert(result.isLeft)
    result.swap.foreach { error =>
      assert(error.contains("Git worktree creation failed"))
    }

  test("create when createTmuxSession fails returns Left with error"):
    val fetchIssue = (id: String) => Right(testIssueData)
    val createWorktree = (path: String, branch: String) => Right(())
    val createTmux = (name: String, path: String) => Left("Tmux session creation failed")
    val registerWorktree = (issueId: String, path: String, trackerType: String, team: String) => Right(())

    val result = WorktreeCreationService.create(
      "IW-79",
      testConfig,
      fetchIssue,
      createWorktree,
      createTmux,
      registerWorktree
    )

    assert(result.isLeft)
    result.swap.foreach { error =>
      assert(error.contains("Tmux session creation failed"))
    }

  test("create when registerWorktree fails returns Left with error"):
    val fetchIssue = (id: String) => Right(testIssueData)
    val createWorktree = (path: String, branch: String) => Right(())
    val createTmux = (name: String, path: String) => Right(())
    val registerWorktree = (issueId: String, path: String, trackerType: String, team: String) =>
      Left("Registration failed")

    val result = WorktreeCreationService.create(
      "IW-79",
      testConfig,
      fetchIssue,
      createWorktree,
      createTmux,
      registerWorktree
    )

    assert(result.isLeft)
    result.swap.foreach { error =>
      assert(error.contains("Registration failed"))
    }

  test("create generates branch name with issue ID"):
    val fetchIssue = (id: String) => Right(testIssueData)
    var capturedBranch: Option[String] = None
    val createWorktree = (path: String, branch: String) => {
      capturedBranch = Some(branch)
      Right(())
    }
    val createTmux = (name: String, path: String) => Right(())
    val registerWorktree = (issueId: String, path: String, trackerType: String, team: String) => Right(())

    val result = WorktreeCreationService.create(
      "IW-79",
      testConfig,
      fetchIssue,
      createWorktree,
      createTmux,
      registerWorktree
    )

    assert(result.isRight)
    assert(capturedBranch.isDefined)
    capturedBranch.foreach { branch =>
      assert(branch.contains("IW-79"))
    }

  test("create generates worktree path with project name and issue ID"):
    val fetchIssue = (id: String) => Right(testIssueData)
    var capturedPath: Option[String] = None
    val createWorktree = (path: String, branch: String) => {
      capturedPath = Some(path)
      Right(())
    }
    val createTmux = (name: String, path: String) => Right(())
    val registerWorktree = (issueId: String, path: String, trackerType: String, team: String) => Right(())

    val result = WorktreeCreationService.create(
      "IW-79",
      testConfig,
      fetchIssue,
      createWorktree,
      createTmux,
      registerWorktree
    )

    assert(result.isRight)
    assert(capturedPath.isDefined)
    capturedPath.foreach { path =>
      assert(path.contains("iw-cli"))
      assert(path.contains("IW-79"))
    }

  test("create generates tmux session name matching worktree directory"):
    val fetchIssue = (id: String) => Right(testIssueData)
    val createWorktree = (path: String, branch: String) => Right(())
    var capturedSessionName: Option[String] = None
    val createTmux = (name: String, path: String) => {
      capturedSessionName = Some(name)
      Right(())
    }
    val registerWorktree = (issueId: String, path: String, trackerType: String, team: String) => Right(())

    val result = WorktreeCreationService.create(
      "IW-79",
      testConfig,
      fetchIssue,
      createWorktree,
      createTmux,
      registerWorktree
    )

    assert(result.isRight)
    assert(capturedSessionName.isDefined)
    capturedSessionName.foreach { sessionName =>
      assert(sessionName.contains("iw-cli"))
      assert(sessionName.contains("IW-79"))
    }

  test("create calls registerWorktree with correct parameters"):
    val fetchIssue = (id: String) => Right(testIssueData)
    val createWorktree = (path: String, branch: String) => Right(())
    val createTmux = (name: String, path: String) => Right(())
    var registerCalled = false
    var capturedIssueId: Option[String] = None
    var capturedTrackerType: Option[String] = None
    var capturedTeam: Option[String] = None

    val registerWorktree = (issueId: String, path: String, trackerType: String, team: String) => {
      registerCalled = true
      capturedIssueId = Some(issueId)
      capturedTrackerType = Some(trackerType)
      capturedTeam = Some(team)
      Right(())
    }

    val result = WorktreeCreationService.create(
      "IW-79",
      testConfig,
      fetchIssue,
      createWorktree,
      createTmux,
      registerWorktree
    )

    assert(result.isRight)
    assert(registerCalled)
    assertEquals(capturedIssueId, Some("IW-79"))
    assertEquals(capturedTrackerType, Some("GitHub"))
    assertEquals(capturedTeam, Some("IW"))
