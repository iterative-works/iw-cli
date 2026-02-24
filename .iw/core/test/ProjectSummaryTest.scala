// PURPOSE: Unit tests for ProjectSummary view model and summary computation
// PURPOSE: Verifies worktree count and attention count calculations

package iw.tests

import iw.core.dashboard.domain.MainProject
import iw.core.dashboard.presentation.views.ProjectSummary
import iw.core.model.{CachedReviewState, ReviewState, WorktreeRegistration}
import munit.FunSuite
import java.time.Instant

class ProjectSummaryTest extends FunSuite:
  private val now = Instant.now()

  private def createWorktree(
    issueId: String,
    path: String,
    trackerType: String = "github",
    team: String = "test/repo"
  ): WorktreeRegistration =
    WorktreeRegistration(
      issueId = issueId,
      path = path,
      trackerType = trackerType,
      team = team,
      registeredAt = now,
      lastSeenAt = now
    )

  private def createProject(
    path: String,
    projectName: String,
    trackerType: String = "github",
    team: String = "test/repo"
  ): MainProject =
    MainProject(
      path = os.Path(path),
      projectName = projectName,
      trackerType = trackerType,
      team = team,
      trackerUrl = None
    )

  private def createReviewState(needsAttention: Option[Boolean]): ReviewState =
    ReviewState(
      display = None,
      badges = None,
      taskLists = None,
      needsAttention = needsAttention,
      message = None,
      artifacts = List.empty
    )

  test("computeSummaries with empty inputs returns empty list"):
    val summaries = ProjectSummary.computeSummaries(
      worktrees = List.empty,
      projects = List.empty,
      reviewStateCache = Map.empty
    )

    assertEquals(summaries, List.empty)

  test("computeSummaries with single project and multiple worktrees returns correct count"):
    val project = createProject("/home/user/projects/iw-cli", "iw-cli")
    val worktree1 = createWorktree("IW-1", "/home/user/projects/iw-cli-IW-1")
    val worktree2 = createWorktree("IW-2", "/home/user/projects/iw-cli-IW-2")
    val worktree3 = createWorktree("IW-3", "/home/user/projects/iw-cli-IW-3")

    val summaries = ProjectSummary.computeSummaries(
      worktrees = List(worktree1, worktree2, worktree3),
      projects = List(project),
      reviewStateCache = Map.empty
    )

    assertEquals(summaries.length, 1)
    assertEquals(summaries.head.project, project)
    assertEquals(summaries.head.worktreeCount, 3)
    assertEquals(summaries.head.attentionCount, 0)

  test("computeSummaries with multiple projects returns correct per-project counts"):
    val project1 = createProject("/home/user/projects/iw-cli", "iw-cli")
    val project2 = createProject("/home/user/projects/kanon", "kanon", "linear", "IWLE")

    val worktree1 = createWorktree("IW-1", "/home/user/projects/iw-cli-IW-1")
    val worktree2 = createWorktree("IW-2", "/home/user/projects/iw-cli-IW-2")
    val worktree3 = createWorktree("IWLE-1", "/home/user/projects/kanon-IWLE-1", "linear", "IWLE")
    val worktree4 = createWorktree("IWLE-2", "/home/user/projects/kanon-IWLE-2", "linear", "IWLE")
    val worktree5 = createWorktree("IWLE-3", "/home/user/projects/kanon-IWLE-3", "linear", "IWLE")

    val summaries = ProjectSummary.computeSummaries(
      worktrees = List(worktree1, worktree2, worktree3, worktree4, worktree5),
      projects = List(project1, project2),
      reviewStateCache = Map.empty
    )

    assertEquals(summaries.length, 2)

    val summary1 = summaries.find(_.project.projectName == "iw-cli").get
    assertEquals(summary1.worktreeCount, 2)
    assertEquals(summary1.attentionCount, 0)

    val summary2 = summaries.find(_.project.projectName == "kanon").get
    assertEquals(summary2.worktreeCount, 3)
    assertEquals(summary2.attentionCount, 0)

  test("computeSummaries worktree with needsAttention == Some(true) increments attention count"):
    val project = createProject("/home/user/projects/iw-cli", "iw-cli")
    val worktree1 = createWorktree("IW-1", "/home/user/projects/iw-cli-IW-1")
    val worktree2 = createWorktree("IW-2", "/home/user/projects/iw-cli-IW-2")

    val reviewStateCache = Map(
      "IW-1" -> CachedReviewState(
        createReviewState(Some(true)),
        Map.empty
      )
    )

    val summaries = ProjectSummary.computeSummaries(
      worktrees = List(worktree1, worktree2),
      projects = List(project),
      reviewStateCache = reviewStateCache
    )

    assertEquals(summaries.length, 1)
    assertEquals(summaries.head.worktreeCount, 2)
    assertEquals(summaries.head.attentionCount, 1)

  test("computeSummaries worktree with no review state does NOT count as needing attention"):
    val project = createProject("/home/user/projects/iw-cli", "iw-cli")
    val worktree1 = createWorktree("IW-1", "/home/user/projects/iw-cli-IW-1")
    val worktree2 = createWorktree("IW-2", "/home/user/projects/iw-cli-IW-2")

    val summaries = ProjectSummary.computeSummaries(
      worktrees = List(worktree1, worktree2),
      projects = List(project),
      reviewStateCache = Map.empty
    )

    assertEquals(summaries.length, 1)
    assertEquals(summaries.head.attentionCount, 0)

  test("computeSummaries worktree with needsAttention == Some(false) does NOT count"):
    val project = createProject("/home/user/projects/iw-cli", "iw-cli")
    val worktree1 = createWorktree("IW-1", "/home/user/projects/iw-cli-IW-1")
    val worktree2 = createWorktree("IW-2", "/home/user/projects/iw-cli-IW-2")

    val reviewStateCache = Map(
      "IW-1" -> CachedReviewState(
        createReviewState(Some(false)),
        Map.empty
      ),
      "IW-2" -> CachedReviewState(
        createReviewState(Some(false)),
        Map.empty
      )
    )

    val summaries = ProjectSummary.computeSummaries(
      worktrees = List(worktree1, worktree2),
      projects = List(project),
      reviewStateCache = reviewStateCache
    )

    assertEquals(summaries.length, 1)
    assertEquals(summaries.head.attentionCount, 0)

  test("computeSummaries excludes worktrees that don't match project pattern"):
    val project = createProject("/home/user/projects/iw-cli", "iw-cli")
    val validWorktree = createWorktree("IW-1", "/home/user/projects/iw-cli-IW-1")
    val orphanedWorktree = createWorktree("OTHER-1", "/home/user/projects/just-a-directory")

    val summaries = ProjectSummary.computeSummaries(
      worktrees = List(validWorktree, orphanedWorktree),
      projects = List(project),
      reviewStateCache = Map.empty
    )

    assertEquals(summaries.length, 1)
    assertEquals(summaries.head.worktreeCount, 1)

  test("computeSummaries handles project with no matching worktrees"):
    val project1 = createProject("/home/user/projects/iw-cli", "iw-cli")
    val project2 = createProject("/home/user/projects/kanon", "kanon", "linear", "IWLE")

    val worktree1 = createWorktree("IW-1", "/home/user/projects/iw-cli-IW-1")

    val summaries = ProjectSummary.computeSummaries(
      worktrees = List(worktree1),
      projects = List(project1, project2),
      reviewStateCache = Map.empty
    )

    assertEquals(summaries.length, 2)
    val kanonSummary = summaries.find(_.project.projectName == "kanon").get
    assertEquals(kanonSummary.worktreeCount, 0)
    assertEquals(kanonSummary.attentionCount, 0)
