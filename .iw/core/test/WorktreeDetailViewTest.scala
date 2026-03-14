// PURPOSE: Unit tests for WorktreeDetailView presentation layer
// PURPOSE: Verifies rendering of worktree detail page with all data sections and edge cases

package iw.tests

import iw.core.model.{WorktreeRegistration, IssueData, WorkflowProgress, PhaseInfo, GitStatus, PullRequestData, PRState, ReviewState, Display, ReviewArtifact, Badge}
import iw.core.dashboard.presentation.views.WorktreeDetailView
import java.time.Instant
import java.time.temporal.ChronoUnit
import munit.FunSuite

class WorktreeDetailViewTest extends FunSuite:
  val now: Instant = Instant.now()

  val sampleWorktree: WorktreeRegistration = WorktreeRegistration(
    issueId = "IW-188",
    path = "/home/user/projects/iw-cli-IW-188",
    trackerType = "github",
    team = "iterative-works/iw-cli",
    registeredAt = now.minus(2, ChronoUnit.DAYS),
    lastSeenAt = now.minus(1, ChronoUnit.HOURS)
  )

  val sampleIssueData: IssueData = IssueData(
    id = "IW-188",
    title = "Add worktree detail page",
    status = "In Progress",
    assignee = Some("Michal Příhoda"),
    description = Some("Create a dedicated detail page for each worktree"),
    url = "https://github.com/iterative-works/iw-cli/issues/188",
    fetchedAt = now.minus(5, ChronoUnit.MINUTES)
  )

  val sampleGitStatus: GitStatus = GitStatus(
    branchName = "IW-188",
    isClean = true
  )

  val sampleProgress: WorkflowProgress = WorkflowProgress(
    currentPhase = Some(1),
    totalPhases = 3,
    phases = List(
      PhaseInfo(1, "Implementation", "/path/phase-01-tasks.md", 10, 4),
      PhaseInfo(2, "Testing", "/path/phase-02-tasks.md", 5, 0),
      PhaseInfo(3, "Review", "/path/phase-03-tasks.md", 3, 0)
    ),
    overallCompleted = 4,
    overallTotal = 18
  )

  val samplePR: PullRequestData = PullRequestData(
    url = "https://github.com/iterative-works/iw-cli/pull/42",
    state = PRState.Open,
    number = 42,
    title = "Add worktree detail page"
  )

  val sampleReviewState: ReviewState = ReviewState(
    display = Some(Display("In Review", Some("Phase 1 of 3"), "progress")),
    badges = None,
    taskLists = None,
    needsAttention = None,
    message = Some("Review in progress"),
    artifacts = List(
      ReviewArtifact("Analysis", "project-management/issues/IW-188/analysis.md")
    )
  )

  /** Helper that provides defaults for all render parameters, allowing tests to override only what they vary. */
  def renderDefault(
    issueData: Option[(IssueData, Boolean, Boolean)] = Some((sampleIssueData, false, false)),
    progress: Option[WorkflowProgress] = None,
    gitStatus: Option[GitStatus] = None,
    prData: Option[PullRequestData] = None,
    reviewStateResult: Option[Either[String, ReviewState]] = None,
    projectName: Option[String] = None,
    sshHost: String = "localhost"
  ): String =
    WorktreeDetailView.render(
      worktree = sampleWorktree,
      issueData = issueData,
      progress = progress,
      gitStatus = gitStatus,
      prData = prData,
      reviewStateResult = reviewStateResult,
      projectName = projectName,
      now = now,
      sshHost = sshHost
    ).render

  test("render includes issue title in heading"):
    val html = renderDefault()

    assert(html.contains("<h1>Add worktree detail page</h1>"), "Should contain issue title in h1 heading")

  test("render includes issue status badge"):
    val html = renderDefault()

    assert(html.contains("In Progress"), "Should contain issue status")
    assert(html.contains("status-badge"), "Should have status badge class")

  test("render includes assignee when present"):
    val html = renderDefault()

    assert(html.contains("Michal"), "Should contain assignee name")

  test("render omits assignee when absent"):
    val issueWithoutAssignee = sampleIssueData.copy(assignee = None)
    val html = renderDefault(issueData = Some((issueWithoutAssignee, false, false)))

    assert(!html.contains("Assigned:"), "Should not show assignee field when absent")

  test("render includes git branch name when git status is present"):
    val html = renderDefault(gitStatus = Some(sampleGitStatus))

    assert(html.contains("Branch: IW-188"), "Should contain labeled branch name")

  test("render includes clean indicator for clean git status"):
    val html = renderDefault(gitStatus = Some(sampleGitStatus))

    assert(html.contains("clean") || html.contains("✓"), "Should show clean indicator")

  test("render omits git section when git status is absent"):
    val html = renderDefault()

    assert(!html.contains("git-status"), "Should not show git section when absent")

  test("render includes PR number and link when PR data is present"):
    val html = renderDefault(prData = Some(samplePR))

    assert(html.contains("PR #42"), "Should contain PR number")
    assert(html.contains("https://github.com/iterative-works/iw-cli/pull/42"), "Should contain PR URL")

  test("render omits PR section when PR data is absent"):
    val html = renderDefault()

    assert(!html.contains("pr-link") && !html.contains("pr-button"), "Should not show PR section when absent")

  test("render includes workflow phase info when progress is present"):
    val html = renderDefault(progress = Some(sampleProgress))

    assert(html.contains("Implementation"), "Should show current phase name")
    assert(html.contains("phase-info"), "Should have phase-info section")

  test("render includes progress bar for workflow progress"):
    val html = renderDefault(progress = Some(sampleProgress))

    assert(html.contains("progress-bar"), "Should have progress-bar element")
    assert(html.contains("4/10 tasks"), "Should show task counts")

  test("render includes review artifacts when review state is present"):
    val html = renderDefault(reviewStateResult = Some(Right(sampleReviewState)))

    assert(html.contains("Review Artifacts"), "Should show review artifacts section")
    assert(html.contains("Analysis"), "Should show artifact name")

  test("render omits review artifacts section when review state is absent"):
    val html = renderDefault()

    assert(!html.contains("review-artifacts"), "Should not show review artifacts section when absent")

  test("render shows review error when review state result is a Left"):
    val html = renderDefault(reviewStateResult = Some(Left("Malformed JSON")))

    assert(html.contains("review-error"), "Should show review error class")
    assert(html.contains("Review Artifacts"), "Should still show Review Artifacts heading")

  test("render includes Zed editor link with correct URL"):
    val html = renderDefault(sshHost = "myserver.local")

    assert(html.contains("zed://ssh/myserver.local/home/user/projects/iw-cli-IW-188"), "Should contain correct Zed URL")

  test("render shows breadcrumb with project name when derivable"):
    val html = renderDefault(projectName = Some("iw-cli"))

    assert(html.contains("iw-cli"), "Should contain project name in breadcrumb")
    assert(html.contains("IW-188"), "Should contain issue ID in breadcrumb")
    assert(html.contains("Projects"), "Should have Projects in breadcrumb")
    assert(html.contains("href=\"/\""), "Should link to root")

  test("render shows breadcrumb without project name when not derivable"):
    val html = renderDefault()

    assert(html.contains("Projects"), "Should have Projects in breadcrumb")
    assert(html.contains("IW-188"), "Should contain issue ID in breadcrumb")
    assert(html.contains("href=\"/\""), "Should link to root")

  test("render shows skeleton state when issue data is absent"):
    val html = renderDefault(issueData = None)

    assert(html.contains("IW-188"), "Should show issue ID even without data")
    assert(html.contains("Loading"), "Should show loading text in skeleton state")
    assert(!html.contains("Add worktree detail page"), "Should not show issue title in skeleton state")

  test("render shows git status in skeleton state when available"):
    val html = renderDefault(issueData = None, gitStatus = Some(sampleGitStatus))

    assert(html.contains("IW-188"), "Should show branch name in skeleton state")

  test("render shows cache indicator when data is from cache"):
    val html = renderDefault(issueData = Some((sampleIssueData, true, false)))

    assert(html.contains("cache-indicator"), "Should show cache indicator")

  test("render shows stale indicator when data is stale"):
    val html = renderDefault(issueData = Some((sampleIssueData, true, true)))

    assert(html.contains("stale-indicator"), "Should show stale indicator")

  test("renderNotFound includes the issue ID"):
    val html = WorktreeDetailView.renderNotFound("IW-999").render

    assert(html.contains("IW-999"), "Should include the issue ID")

  test("renderNotFound includes link back to overview"):
    val html = WorktreeDetailView.renderNotFound("IW-999").render

    assert(html.contains("href=\"/\""), "Should include link to root overview")

  test("renderNotFound includes breadcrumb"):
    val html = WorktreeDetailView.renderNotFound("IW-999").render

    assert(html.contains("Projects"), "Should include Projects in breadcrumb")
    assert(html.contains("breadcrumb"), "Should have breadcrumb class")

  test("renderNotFound shows not found heading and explanation"):
    val html = WorktreeDetailView.renderNotFound("IW-999").render

    assert(html.contains("Worktree Not Found"), "Should render the not-found heading")
    assert(html.contains("not registered"), "Should explain the worktree is not registered")
