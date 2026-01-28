// PURPOSE: Unit tests for WorktreeCreationService business logic
// PURPOSE: Verifies worktree creation orchestration with injected dependencies

package iw.core.application

import munit.FunSuite
import iw.core.model.{Issue, IssueData, IssueTrackerType, ProjectConfiguration}
import iw.core.dashboard.domain.{WorktreeCreationError, WorktreeCreationResult}
import iw.core.dashboard.application.WorktreeCreationService
import iw.core.dashboard.infrastructure.CreationLockRegistry
import iw.core.adapters.{GitWorktreeAdapter, ProcessAdapter, TmuxAdapter}
import java.time.Instant

class WorktreeCreationServiceTest extends FunSuite:

  val testConfig = ProjectConfiguration.create(
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
      assert(error.isInstanceOf[WorktreeCreationError.IssueNotFound])
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
      assert(error.isInstanceOf[WorktreeCreationError.GitError])
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
      assert(error.isInstanceOf[WorktreeCreationError.TmuxError])
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
      assert(error.isInstanceOf[WorktreeCreationError.ApiError])
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

  // Group D: Tests for specific error types

  test("create returns DirectoryExists error when directory check fails"):
    val fetchIssue = (id: String) => Right(testIssueData)
    val createWorktree = (path: String, branch: String) => Right(())
    val createTmux = (name: String, path: String) => Right(())
    val registerWorktree = (issueId: String, path: String, trackerType: String, team: String) => Right(())
    val checkDirectoryExists = (path: String) => true

    val result = WorktreeCreationService.create(
      "IW-79",
      testConfig,
      fetchIssue,
      createWorktree,
      createTmux,
      registerWorktree,
      checkDirectoryExists
    )

    assert(result.isLeft)
    result.swap.foreach { error =>
      assert(error.isInstanceOf[WorktreeCreationError.DirectoryExists])
      error match
        case WorktreeCreationError.DirectoryExists(path) =>
          assert(path.contains("iw-cli-IW-79"))
        case _ => fail("Expected DirectoryExists error")
    }

  test("create returns AlreadyHasWorktree error when worktree already registered"):
    val fetchIssue = (id: String) => Right(testIssueData)
    val createWorktree = (path: String, branch: String) => Right(())
    val createTmux = (name: String, path: String) => Right(())
    val registerWorktree = (issueId: String, path: String, trackerType: String, team: String) => Right(())
    val checkDirectoryExists = (path: String) => false
    val checkWorktreeExists = (issueId: String) => Some("../iw-cli-IW-79")

    val result = WorktreeCreationService.create(
      "IW-79",
      testConfig,
      fetchIssue,
      createWorktree,
      createTmux,
      registerWorktree,
      checkDirectoryExists,
      checkWorktreeExists
    )

    assert(result.isLeft)
    result.swap.foreach { error =>
      assert(error.isInstanceOf[WorktreeCreationError.AlreadyHasWorktree])
      error match
        case WorktreeCreationError.AlreadyHasWorktree(issueId, existingPath) =>
          assertEquals(issueId, "IW-79")
          assert(existingPath.contains("iw-cli-IW-79"))
        case _ => fail("Expected AlreadyHasWorktree error")
    }

  test("create returns GitError when git worktree creation fails"):
    val fetchIssue = (id: String) => Right(testIssueData)
    val createWorktree = (path: String, branch: String) => Left("fatal: git worktree add failed")
    val createTmux = (name: String, path: String) => Right(())
    val registerWorktree = (issueId: String, path: String, trackerType: String, team: String) => Right(())
    val checkDirectoryExists = (path: String) => false
    val checkWorktreeExists = (issueId: String) => None

    val result = WorktreeCreationService.create(
      "IW-79",
      testConfig,
      fetchIssue,
      createWorktree,
      createTmux,
      registerWorktree,
      checkDirectoryExists,
      checkWorktreeExists
    )

    assert(result.isLeft)
    result.swap.foreach { error =>
      assert(error.isInstanceOf[WorktreeCreationError.GitError])
      error match
        case WorktreeCreationError.GitError(message) =>
          assert(message.contains("git worktree add failed"))
        case _ => fail("Expected GitError")
    }

  test("create returns TmuxError when tmux session creation fails"):
    val fetchIssue = (id: String) => Right(testIssueData)
    val createWorktree = (path: String, branch: String) => Right(())
    val createTmux = (name: String, path: String) => Left("tmux: session already exists")
    val registerWorktree = (issueId: String, path: String, trackerType: String, team: String) => Right(())
    val checkDirectoryExists = (path: String) => false
    val checkWorktreeExists = (issueId: String) => None

    val result = WorktreeCreationService.create(
      "IW-79",
      testConfig,
      fetchIssue,
      createWorktree,
      createTmux,
      registerWorktree,
      checkDirectoryExists,
      checkWorktreeExists
    )

    assert(result.isLeft)
    result.swap.foreach { error =>
      assert(error.isInstanceOf[WorktreeCreationError.TmuxError])
      error match
        case WorktreeCreationError.TmuxError(message) =>
          assert(message.contains("session already exists"))
        case _ => fail("Expected TmuxError")
    }

  // Group D: Tests for createWithLock

  override def beforeEach(context: BeforeEach): Unit =
    iw.core.dashboard.infrastructure.CreationLockRegistry.clear()

  test("createWithLock acquires lock before creation"):
    val fetchIssue = (id: String) => Right(testIssueData)
    val createWorktree = (path: String, branch: String) => Right(())
    val createTmux = (name: String, path: String) => Right(())
    val registerWorktree = (issueId: String, path: String, trackerType: String, team: String) => Right(())

    val result = WorktreeCreationService.createWithLock(
      "IW-79",
      testConfig,
      fetchIssue,
      createWorktree,
      createTmux,
      registerWorktree
    )

    assert(result.isRight, "createWithLock should succeed when lock is acquired")

  test("createWithLock returns CreationInProgress when already locked"):
    val fetchIssue = (id: String) => Right(testIssueData)
    val createWorktree = (path: String, branch: String) => Right(())
    val createTmux = (name: String, path: String) => Right(())
    val registerWorktree = (issueId: String, path: String, trackerType: String, team: String) => Right(())

    // First creation acquires lock
    iw.core.dashboard.infrastructure.CreationLockRegistry.tryAcquire("IW-79")

    // Second creation should fail with CreationInProgress
    val result = WorktreeCreationService.createWithLock(
      "IW-79",
      testConfig,
      fetchIssue,
      createWorktree,
      createTmux,
      registerWorktree
    )

    assert(result.isLeft, "createWithLock should fail when lock already held")
    result.swap.foreach { error =>
      assert(error.isInstanceOf[WorktreeCreationError.CreationInProgress],
        "Error should be CreationInProgress")
      error match
        case WorktreeCreationError.CreationInProgress(issueId) =>
          assertEquals(issueId, "IW-79")
        case _ => fail("Expected CreationInProgress error")
    }

  test("createWithLock releases lock on success"):
    val fetchIssue = (id: String) => Right(testIssueData)
    val createWorktree = (path: String, branch: String) => Right(())
    val createTmux = (name: String, path: String) => Right(())
    val registerWorktree = (issueId: String, path: String, trackerType: String, team: String) => Right(())

    WorktreeCreationService.createWithLock(
      "IW-79",
      testConfig,
      fetchIssue,
      createWorktree,
      createTmux,
      registerWorktree
    )

    // Lock should be released after successful creation
    assert(!iw.core.dashboard.infrastructure.CreationLockRegistry.isLocked("IW-79"),
      "Lock should be released after successful creation")

  test("createWithLock releases lock on failure"):
    val fetchIssue = (id: String) => Right(testIssueData)
    val createWorktree = (path: String, branch: String) => Left("Git error")
    val createTmux = (name: String, path: String) => Right(())
    val registerWorktree = (issueId: String, path: String, trackerType: String, team: String) => Right(())

    val result = WorktreeCreationService.createWithLock(
      "IW-79",
      testConfig,
      fetchIssue,
      createWorktree,
      createTmux,
      registerWorktree
    )

    assert(result.isLeft, "Creation should fail")
    // Lock should be released even on failure
    assert(!iw.core.dashboard.infrastructure.CreationLockRegistry.isLocked("IW-79"),
      "Lock should be released even after failed creation")
