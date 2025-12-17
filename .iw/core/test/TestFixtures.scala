// PURPOSE: Shared test fixtures for consistent setup across test suites
// PURPOSE: Provides reusable FunFixtures for temp directories, git repos, and sample data
package iw.tests

import iw.core.*
import munit.FunSuite
import scala.sys.process.*

/** Shared fixtures trait for test setup and teardown.
  *
  * Mix in this trait to get access to common test fixtures.
  *
  * Usage:
  * {{{
  * class MyTest extends munit.FunSuite, Fixtures:
  *
  *   tempDir.test("my test"): dir =>
  *     // dir is a clean temp directory
  *
  *   gitRepo.test("my git test"): repo =>
  *     // repo is initialized with git, user config, and initial commit
  * }}}
  */
trait Fixtures:
  self: FunSuite =>

  /** Temporary directory fixture with automatic cleanup. */
  val tempDir: FunFixture[os.Path] = FunFixture[os.Path](
    setup = { _ =>
      os.Path(java.nio.file.Files.createTempDirectory("iw-test"))
    },
    teardown = { dir =>
      os.remove.all(dir)
    }
  )

  /** Git repository fixture with user config and initial commit.
    *
    * Creates a temp directory, initializes git, sets user config, and makes an initial commit.
    * The remote is set to a sample GitHub URL for tests that need remote detection.
    */
  val gitRepo: FunFixture[os.Path] = FunFixture[os.Path](
    setup = { _ =>
      val dir = os.Path(java.nio.file.Files.createTempDirectory("iw-test-git"))
      Process(Seq("git", "init"), dir.toIO).!
      Process(Seq("git", "config", "user.email", "test@example.com"), dir.toIO).!
      Process(Seq("git", "config", "user.name", "Test User"), dir.toIO).!
      Process(
        Seq("git", "remote", "add", "origin", "https://github.com/iterative-works/test-repo.git"),
        dir.toIO
      ).!
      os.write(dir / "README.md", "# Test Repo")
      Process(Seq("git", "add", "README.md"), dir.toIO).!
      Process(Seq("git", "commit", "-m", "Initial commit"), dir.toIO).!
      dir
    },
    teardown = { dir =>
      os.remove.all(dir)
    }
  )

/** Companion object for Fixtures trait - provides factory methods for standalone use. */
object Fixtures:
  /** Create a temporary directory and return its path. Caller responsible for cleanup. */
  def createTempDir(prefix: String = "iw-test"): os.Path =
    os.Path(java.nio.file.Files.createTempDirectory(prefix))

  /** Create a git repo in a temporary directory. Caller responsible for cleanup. */
  def createGitRepo(prefix: String = "iw-test-git"): os.Path =
    val dir = os.Path(java.nio.file.Files.createTempDirectory(prefix))
    Process(Seq("git", "init"), dir.toIO).!
    Process(Seq("git", "config", "user.email", "test@example.com"), dir.toIO).!
    Process(Seq("git", "config", "user.name", "Test User"), dir.toIO).!
    Process(
      Seq("git", "remote", "add", "origin", "https://github.com/iterative-works/test-repo.git"),
      dir.toIO
    ).!
    os.write(dir / "README.md", "# Test Repo")
    Process(Seq("git", "add", "README.md"), dir.toIO).!
    Process(Seq("git", "commit", "-m", "Initial commit"), dir.toIO).!
    dir

/** Sample test data for consistent test objects.
  *
  * Provides canonical examples of domain objects with sensible defaults.
  * Use these to avoid creating similar objects in every test file.
  */
object SampleData:

  /** Sample issue with all fields populated. */
  val issue: Issue = Issue(
    id = "IWLE-123",
    title = "Add user login",
    status = "In Progress",
    assignee = Some("Michal Příhoda"),
    description = Some("Users need to be able to log in")
  )

  /** Sample issue with no assignee. */
  val unassignedIssue: Issue = Issue(
    id = "IWLE-456",
    title = "Fix bug",
    status = "Todo",
    assignee = None,
    description = Some("Bug needs to be fixed")
  )

  /** Sample issue with no description. */
  val issueNoDescription: Issue = Issue(
    id = "IWLE-789",
    title = "Simple task",
    status = "Done",
    assignee = Some("John Doe"),
    description = None
  )

  /** Sample issue with minimal fields (no assignee, no description). */
  val minimalIssue: Issue = Issue(
    id = "IWLE-999",
    title = "Minimal task",
    status = "Backlog",
    assignee = None,
    description = None
  )

  /** Sample Linear project configuration. */
  val linearConfig: ProjectConfiguration = ProjectConfiguration(
    trackerType = IssueTrackerType.Linear,
    team = "IWLE",
    projectName = "kanon"
  )

  /** Sample YouTrack project configuration. */
  val youtrackConfig: ProjectConfiguration = ProjectConfiguration(
    trackerType = IssueTrackerType.YouTrack,
    team = "TEST",
    projectName = "myproject",
    youtrackBaseUrl = Some("https://youtrack.example.com")
  )

  /** Sample issue ID (parsed and valid). */
  val issueId: IssueId = IssueId.parse("IWLE-123").getOrElse(
    throw new IllegalStateException("Sample issue ID should always parse")
  )

  /** Sample Git remote for GitHub. */
  val githubRemote: GitRemote = GitRemote("https://github.com/iterative-works/kanon.git")

  /** Sample Git remote for GitLab (YouTrack detection). */
  val gitlabRemote: GitRemote = GitRemote("https://gitlab.e-bs.cz/iterative-works/project.git")

/** Test JSON responses for API client tests. */
object SampleJson:

  /** Valid Linear API response with all fields. */
  val linearIssueResponse: String = """{
    "data": {
      "issue": {
        "identifier": "IWLE-123",
        "title": "Add user login",
        "state": { "name": "In Progress" },
        "assignee": { "displayName": "Michal Příhoda" },
        "description": "Users need to log in"
      }
    }
  }"""

  /** Linear API response with null assignee. */
  val linearIssueNoAssignee: String = """{
    "data": {
      "issue": {
        "identifier": "IWLE-456",
        "title": "Unassigned task",
        "state": { "name": "Todo" },
        "assignee": null,
        "description": "Description here"
      }
    }
  }"""

  /** Linear API response with issue not found. */
  val linearIssueNotFound: String = """{
    "data": {
      "issue": null
    }
  }"""

  /** Valid YouTrack API response. */
  val youtrackIssueResponse: String = """{
    "idReadable": "TEST-123",
    "summary": "Test issue title",
    "customFields": [
      { "name": "State", "value": { "name": "Open" } },
      { "name": "Assignee", "value": { "fullName": "Test User" } }
    ],
    "description": "Issue description text"
  }"""

  /** YouTrack API response with no assignee. */
  val youtrackIssueNoAssignee: String = """{
    "idReadable": "TEST-456",
    "summary": "Unassigned YouTrack issue",
    "customFields": [
      { "name": "State", "value": { "name": "Todo" } },
      { "name": "Assignee", "value": null }
    ],
    "description": "No one is assigned"
  }"""
