// PURPOSE: Shared test fixtures for consistent setup across test suites
// PURPOSE: Provides reusable FunFixtures for temp directories, git repos, and sample data
package iw.tests

import iw.core.*
import iw.core.dashboard.domain.*
import munit.FunSuite
import scala.sys.process.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import iw.core.model.CachedIssue
import iw.core.model.CachedPR
import iw.core.model.CachedProgress
import iw.core.model.CachedReviewState
import iw.core.model.ReviewState
import iw.core.model.ReviewArtifact
import iw.core.model.Issue
import iw.core.model.IssueData
import iw.core.model.WorkflowProgress
import iw.core.model.PullRequestData
import iw.core.model.WorktreeRegistration

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

  /** Sample worktree registrations across multiple tracker types.
    * Includes 5 worktrees: 2 Linear (IWLE-123, IWLE-456), 1 GitHub (GH-100), 2 YouTrack (YT-111, YT-222).
    * Timestamps vary to simulate different registration times and activity levels.
    */
  lazy val sampleWorktrees: List[WorktreeRegistration] =
    val now = Instant.now()
    List(
      WorktreeRegistration(
        issueId = "IWLE-123",
        path = "/tmp/sample-worktree-iwle-123",
        trackerType = "Linear",
        team = "IWLE",
        registeredAt = now.minus(7, ChronoUnit.DAYS),
        lastSeenAt = now.minus(1, ChronoUnit.HOURS)
      ),
      WorktreeRegistration(
        issueId = "IWLE-456",
        path = "/tmp/sample-worktree-iwle-456",
        trackerType = "Linear",
        team = "IWLE",
        registeredAt = now.minus(5, ChronoUnit.DAYS),
        lastSeenAt = now.minus(30, ChronoUnit.MINUTES)
      ),
      WorktreeRegistration(
        issueId = "GH-100",
        path = "/tmp/sample-worktree-gh-100",
        trackerType = "GitHub",
        team = "iw-cli",
        registeredAt = now.minus(3, ChronoUnit.DAYS),
        lastSeenAt = now.minus(2, ChronoUnit.HOURS)
      ),
      WorktreeRegistration(
        issueId = "YT-111",
        path = "/tmp/sample-worktree-yt-111",
        trackerType = "YouTrack",
        team = "TEST",
        registeredAt = now.minus(10, ChronoUnit.DAYS),
        lastSeenAt = now.minus(5, ChronoUnit.HOURS)
      ),
      WorktreeRegistration(
        issueId = "YT-222",
        path = "/tmp/sample-worktree-yt-222",
        trackerType = "YouTrack",
        team = "TEST",
        registeredAt = now.minus(2, ChronoUnit.DAYS),
        lastSeenAt = now.minus(10, ChronoUnit.MINUTES)
      )
    )

  /** Sample issue data with various statuses and assignee states.
    * Includes issues from Linear, GitHub, and YouTrack trackers.
    * One issue (GH-100) has no assignee.
    */
  lazy val sampleIssues: List[IssueData] =
    val now = Instant.now()
    List(
      IssueData(
        id = "IWLE-123",
        title = "Implement dashboard sample data support",
        status = "In Progress",
        assignee = Some("Michal Příhoda"),
        description = Some("Add support for loading sample data in development mode"),
        url = "https://linear.app/iterative-works/issue/IWLE-123",
        fetchedAt = now.minus(10, ChronoUnit.MINUTES)
      ),
      IssueData(
        id = "IWLE-456",
        title = "Complete phase 1 implementation",
        status = "Done",
        assignee = Some("Jane Smith"),
        description = Some("Finish all tasks for phase 1"),
        url = "https://linear.app/iterative-works/issue/IWLE-456",
        fetchedAt = now.minus(1, ChronoUnit.HOURS)
      ),
      IssueData(
        id = "GH-100",
        title = "Fix dashboard layout bug",
        status = "Backlog",
        assignee = None,
        description = Some("Dashboard layout breaks on mobile devices"),
        url = "https://github.com/iterative-works/iw-cli/issues/100",
        fetchedAt = now.minus(5, ChronoUnit.MINUTES)
      ),
      IssueData(
        id = "YT-111",
        title = "Update documentation",
        status = "Under Review",
        assignee = Some("John Doe"),
        description = Some("Update user guide with new features"),
        url = "https://youtrack.example.com/issue/YT-111",
        fetchedAt = now.minus(2, ChronoUnit.HOURS)
      ),
      IssueData(
        id = "YT-222",
        title = "Refactor state management",
        status = "Todo",
        assignee = Some("Alice Brown"),
        description = Some("Simplify state management logic"),
        url = "https://youtrack.example.com/issue/YT-222",
        fetchedAt = now.minus(3, ChronoUnit.MINUTES)
      )
    )

  /** Sample cached issues with varying TTL states.
    * Includes fresh (valid), aging (valid), and stale (expired) cache entries.
    */
  lazy val sampleCachedIssues: List[CachedIssue] =
    val now = Instant.now()
    List(
      // Fresh cache - just fetched
      CachedIssue(
        data = IssueData(
          id = "IWLE-123",
          title = "Implement dashboard sample data support",
          status = "In Progress",
          assignee = Some("Michal Příhoda"),
          description = Some("Add support for loading sample data in development mode"),
          url = "https://linear.app/iterative-works/issue/IWLE-123",
          fetchedAt = now.minus(1, ChronoUnit.MINUTES)
        ),
        ttlMinutes = 5
      ),
      // Valid but aging cache
      CachedIssue(
        data = IssueData(
          id = "IWLE-456",
          title = "Complete phase 1 implementation",
          status = "Done",
          assignee = Some("Jane Smith"),
          description = Some("Finish all tasks for phase 1"),
          url = "https://linear.app/iterative-works/issue/IWLE-456",
          fetchedAt = now.minus(3, ChronoUnit.MINUTES)
        ),
        ttlMinutes = 5
      ),
      // Stale cache - expired
      CachedIssue(
        data = IssueData(
          id = "GH-100",
          title = "Fix dashboard layout bug",
          status = "Backlog",
          assignee = None,
          description = Some("Dashboard layout breaks on mobile devices"),
          url = "https://github.com/iterative-works/iw-cli/issues/100",
          fetchedAt = now.minus(10, ChronoUnit.MINUTES)
        ),
        ttlMinutes = 5
      )
    )

  /** Sample pull request data with all PR states.
    * Includes Open, Merged, and Closed PRs to test UI state rendering.
    */
  lazy val samplePRs: List[PullRequestData] =
    List(
      PullRequestData(
        url = "https://github.com/iterative-works/iw-cli/pull/42",
        state = PRState.Open,
        number = 42,
        title = "Add dashboard sample data support"
      ),
      PullRequestData(
        url = "https://github.com/iterative-works/iw-cli/pull/45",
        state = PRState.Merged,
        number = 45,
        title = "Complete phase 1 implementation"
      ),
      PullRequestData(
        url = "https://github.com/iterative-works/iw-cli/pull/1",
        state = PRState.Closed,
        number = 1,
        title = "Update documentation (rejected)"
      )
    )

  /** Sample cached PRs with varying TTL states.
    * Includes fresh (valid) and stale (expired) cache entries.
    * Note: PR cache TTL is 2 minutes (shorter than issue cache).
    */
  lazy val sampleCachedPRs: List[CachedPR] =
    val now = Instant.now()
    List(
      // Fresh cache - just fetched (IWLE-123 -> PR#42 Open)
      CachedPR(
        pr = PullRequestData(
          url = "https://github.com/iterative-works/iw-cli/pull/42",
          state = PRState.Open,
          number = 42,
          title = "Add dashboard sample data support"
        ),
        fetchedAt = now.minus(30, ChronoUnit.SECONDS),
        ttlMinutes = 2
      ),
      // Valid but aging cache (IWLE-456 -> PR#45 Merged)
      CachedPR(
        pr = PullRequestData(
          url = "https://github.com/iterative-works/iw-cli/pull/45",
          state = PRState.Merged,
          number = 45,
          title = "Complete phase 1 implementation"
        ),
        fetchedAt = now.minus(90, ChronoUnit.SECONDS),
        ttlMinutes = 2
      ),
      // Stale cache - expired (YT-111 -> PR#1 Closed)
      CachedPR(
        pr = PullRequestData(
          url = "https://github.com/iterative-works/iw-cli/pull/1",
          state = PRState.Closed,
          number = 1,
          title = "Update documentation (rejected)"
        ),
        fetchedAt = now.minus(5, ChronoUnit.MINUTES),
        ttlMinutes = 2
      ),
      // Fresh cache (YT-222 -> PR#5 Open)
      CachedPR(
        pr = PullRequestData(
          url = "https://github.com/iterative-works/iw-cli/pull/5",
          state = PRState.Open,
          number = 5,
          title = "Refactor state management"
        ),
        fetchedAt = now.minus(1, ChronoUnit.MINUTES),
        ttlMinutes = 2
      )
    )

  /** Sample workflow progress with various completion levels.
    * Covers 0%, 10%, 40%, 60%, and 100% completion.
    */
  lazy val sampleWorkflowProgress: List[WorkflowProgress] =
    List(
      // IWLE-123: 40% complete (2/5 phases, mid-progress)
      WorkflowProgress(
        currentPhase = Some(2),
        totalPhases = 5,
        phases = List(
          PhaseInfo(1, "Setup", "/tmp/sample-worktree-iwle-123/phase-01-tasks.md", 10, 10),
          PhaseInfo(2, "Implementation", "/tmp/sample-worktree-iwle-123/phase-02-tasks.md", 20, 10),
          PhaseInfo(3, "Testing", "/tmp/sample-worktree-iwle-123/phase-03-tasks.md", 15, 0),
          PhaseInfo(4, "Documentation", "/tmp/sample-worktree-iwle-123/phase-04-tasks.md", 10, 0),
          PhaseInfo(5, "Review", "/tmp/sample-worktree-iwle-123/phase-05-tasks.md", 5, 0)
        ),
        overallCompleted = 20,
        overallTotal = 60
      ),
      // IWLE-456: 100% complete (5/5 phases, all done)
      WorkflowProgress(
        currentPhase = Some(5),
        totalPhases = 5,
        phases = List(
          PhaseInfo(1, "Setup", "/tmp/sample-worktree-iwle-456/phase-01-tasks.md", 8, 8),
          PhaseInfo(2, "Implementation", "/tmp/sample-worktree-iwle-456/phase-02-tasks.md", 15, 15),
          PhaseInfo(3, "Testing", "/tmp/sample-worktree-iwle-456/phase-03-tasks.md", 12, 12),
          PhaseInfo(4, "Documentation", "/tmp/sample-worktree-iwle-456/phase-04-tasks.md", 10, 10),
          PhaseInfo(5, "Review", "/tmp/sample-worktree-iwle-456/phase-05-tasks.md", 5, 5)
        ),
        overallCompleted = 50,
        overallTotal = 50
      ),
      // GH-100: 10% complete (1/3 phases, just started)
      WorkflowProgress(
        currentPhase = Some(1),
        totalPhases = 3,
        phases = List(
          PhaseInfo(1, "Analysis", "/tmp/sample-worktree-gh-100/phase-01-tasks.md", 20, 2),
          PhaseInfo(2, "Implementation", "/tmp/sample-worktree-gh-100/phase-02-tasks.md", 30, 0),
          PhaseInfo(3, "Testing", "/tmp/sample-worktree-gh-100/phase-03-tasks.md", 10, 0)
        ),
        overallCompleted = 2,
        overallTotal = 60
      ),
      // YT-222: 60% complete (3/4 phases, mid-high progress)
      WorkflowProgress(
        currentPhase = Some(3),
        totalPhases = 4,
        phases = List(
          PhaseInfo(1, "Design", "/tmp/sample-worktree-yt-222/phase-01-tasks.md", 10, 10),
          PhaseInfo(2, "Implementation", "/tmp/sample-worktree-yt-222/phase-02-tasks.md", 25, 25),
          PhaseInfo(3, "Testing", "/tmp/sample-worktree-yt-222/phase-03-tasks.md", 20, 5),
          PhaseInfo(4, "Review", "/tmp/sample-worktree-yt-222/phase-04-tasks.md", 5, 0)
        ),
        overallCompleted = 40,
        overallTotal = 60
      )
    )

  /** Sample cached workflow progress with file modification timestamps.
    * Simulates various cache states based on file mtimes.
    */
  lazy val sampleCachedProgress: List[CachedProgress] =
    val baseTime = System.currentTimeMillis()
    List(
      // IWLE-123: Fresh cache (recently parsed)
      CachedProgress(
        progress = sampleWorkflowProgress(0), // 40% complete
        filesMtime = Map(
          "/tmp/sample-worktree-iwle-123/phase-01-tasks.md" -> (baseTime - 3600000),  // 1 hour ago
          "/tmp/sample-worktree-iwle-123/phase-02-tasks.md" -> (baseTime - 600000),   // 10 min ago
          "/tmp/sample-worktree-iwle-123/phase-03-tasks.md" -> (baseTime - 7200000),  // 2 hours ago
          "/tmp/sample-worktree-iwle-123/phase-04-tasks.md" -> (baseTime - 7200000),
          "/tmp/sample-worktree-iwle-123/phase-05-tasks.md" -> (baseTime - 7200000)
        )
      ),
      // IWLE-456: Fresh cache (all phases complete)
      CachedProgress(
        progress = sampleWorkflowProgress(1), // 100% complete
        filesMtime = Map(
          "/tmp/sample-worktree-iwle-456/phase-01-tasks.md" -> (baseTime - 86400000),  // 1 day ago
          "/tmp/sample-worktree-iwle-456/phase-02-tasks.md" -> (baseTime - 86400000),
          "/tmp/sample-worktree-iwle-456/phase-03-tasks.md" -> (baseTime - 86400000),
          "/tmp/sample-worktree-iwle-456/phase-04-tasks.md" -> (baseTime - 86400000),
          "/tmp/sample-worktree-iwle-456/phase-05-tasks.md" -> (baseTime - 86400000)
        )
      ),
      // GH-100: Fresh cache (just started)
      CachedProgress(
        progress = sampleWorkflowProgress(2), // 10% complete
        filesMtime = Map(
          "/tmp/sample-worktree-gh-100/phase-01-tasks.md" -> (baseTime - 1800000),  // 30 min ago
          "/tmp/sample-worktree-gh-100/phase-02-tasks.md" -> (baseTime - 3600000),  // 1 hour ago
          "/tmp/sample-worktree-gh-100/phase-03-tasks.md" -> (baseTime - 3600000)
        )
      ),
      // YT-222: Fresh cache (mid-high progress)
      CachedProgress(
        progress = sampleWorkflowProgress(3), // 60% complete
        filesMtime = Map(
          "/tmp/sample-worktree-yt-222/phase-01-tasks.md" -> (baseTime - 172800000),  // 2 days ago
          "/tmp/sample-worktree-yt-222/phase-02-tasks.md" -> (baseTime - 86400000),   // 1 day ago
          "/tmp/sample-worktree-yt-222/phase-03-tasks.md" -> (baseTime - 3600000),    // 1 hour ago
          "/tmp/sample-worktree-yt-222/phase-04-tasks.md" -> (baseTime - 86400000)
        )
      )
    )

  /** Sample review states with various statuses and artifact collections.
    * Covers awaiting_review, in_review, and ready_to_merge statuses.
    * Some have artifacts, some have none.
    */
  lazy val sampleReviewStates: List[ReviewState] =
    List(
      // IWLE-123: in_review with artifacts
      ReviewState(
        status = Some("in_review"),
        phase = Some(2),
        message = Some("Code review in progress"),
        artifacts = List(
          ReviewArtifact("Analysis", "project-management/issues/IWLE-123/analysis.md"),
          ReviewArtifact("Phase 2 Context", "project-management/issues/IWLE-123/phase-02-context.md")
        )
      ),
      // IWLE-456: ready_to_merge with minimal artifacts
      ReviewState(
        status = Some("ready_to_merge"),
        phase = Some(5),
        message = Some("All reviews complete, ready to merge"),
        artifacts = List(
          ReviewArtifact("Implementation Log", "project-management/issues/IWLE-456/implementation-log.md")
        )
      ),
      // GH-100: awaiting_review with no artifacts yet
      ReviewState(
        status = Some("awaiting_review"),
        phase = Some(1),
        message = Some("Awaiting initial review"),
        artifacts = List.empty
      ),
      // YT-222: in_review with multiple artifacts
      ReviewState(
        status = Some("in_review"),
        phase = Some(3),
        message = Some("Review artifacts ready"),
        artifacts = List(
          ReviewArtifact("Tasks", "project-management/issues/YT-222/tasks.md"),
          ReviewArtifact("Review Notes", "project-management/issues/YT-222/review.md")
        )
      )
    )

  /** Sample cached review states with file modification timestamps.
    * Note: YT-111 has no review state (edge case in design).
    */
  lazy val sampleCachedReviewStates: List[CachedReviewState] =
    val baseTime = System.currentTimeMillis()
    List(
      // IWLE-123: Fresh cache
      CachedReviewState(
        state = sampleReviewStates(0),
        filesMtime = Map(
          "project-management/issues/IWLE-123/analysis.md" -> (baseTime - 3600000),  // 1 hour ago
          "project-management/issues/IWLE-123/phase-02-context.md" -> (baseTime - 1800000)  // 30 min ago
        )
      ),
      // IWLE-456: Fresh cache
      CachedReviewState(
        state = sampleReviewStates(1),
        filesMtime = Map(
          "project-management/issues/IWLE-456/implementation-log.md" -> (baseTime - 86400000)  // 1 day ago
        )
      ),
      // GH-100: Fresh cache (no artifact files)
      CachedReviewState(
        state = sampleReviewStates(2),
        filesMtime = Map(
          "project-management/issues/GH-100/review-state.json" -> (baseTime - 600000)  // 10 min ago
        )
      ),
      // YT-222: Fresh cache
      CachedReviewState(
        state = sampleReviewStates(3),
        filesMtime = Map(
          "project-management/issues/YT-222/tasks.md" -> (baseTime - 7200000),  // 2 hours ago
          "project-management/issues/YT-222/review.md" -> (baseTime - 3600000)  // 1 hour ago
        )
      )
    )

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
