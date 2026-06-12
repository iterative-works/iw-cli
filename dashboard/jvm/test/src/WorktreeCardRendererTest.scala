// PURPOSE: Unit tests for WorktreeCardRenderer card HTML generation
// PURPOSE: Validates title links to detail page and preserved tracker links on issue ID badge

package iw.tests

import iw.dashboard.presentation.views.{
  WorktreeCardRenderer,
  HtmxCardConfig,
  PrDisplayData
}
import iw.core.model.{WorktreeRegistration, IssueData, PullRequestData, PRState}
import java.time.Instant

class WorktreeCardRendererTest extends munit.FunSuite:

  val now: Instant = Instant.now()
  val issueId = "TEST-123"
  val trackerUrl = "https://tracker.example.com/TEST-123"

  val worktree: WorktreeRegistration = WorktreeRegistration(
    issueId = issueId,
    path = "/tmp/worktree",
    trackerType = "Linear",
    team = "TEST",
    registeredAt = now,
    lastSeenAt = now
  )

  val issueData: IssueData = IssueData(
    id = issueId,
    title = "My Issue",
    status = "In Progress",
    assignee = None,
    description = None,
    url = trackerUrl,
    fetchedAt = now
  )

  val htmxConfig: HtmxCardConfig = HtmxCardConfig.polling

  test("renderCard h3 title links to worktree detail page") {
    val html = WorktreeCardRenderer
      .renderCard(
        worktree,
        issueData,
        fromCache = false,
        isStale = false,
        progress = None,
        gitStatus = None,
        prData = None,
        reviewStateResult = None,
        now = now,
        sshHost = "test-host",
        htmxConfig = htmxConfig
      )
      .render

    assert(
      html.contains("""href="/worktrees/TEST-123""""),
      s"Card should contain detail page link, got: $html"
    )
  }

  test("renderCard issue ID badge still links to external tracker") {
    val html = WorktreeCardRenderer
      .renderCard(
        worktree,
        issueData,
        fromCache = false,
        isStale = false,
        progress = None,
        gitStatus = None,
        prData = None,
        reviewStateResult = None,
        now = now,
        sshHost = "test-host",
        htmxConfig = htmxConfig
      )
      .render

    assert(
      html.contains(s"""href="$trackerUrl""""),
      s"Card should contain tracker link, got: $html"
    )
  }

  test("renderCard has both detail page link and tracker link") {
    val html = WorktreeCardRenderer
      .renderCard(
        worktree,
        issueData,
        fromCache = false,
        isStale = false,
        progress = None,
        gitStatus = None,
        prData = None,
        reviewStateResult = None,
        now = now,
        sshHost = "test-host",
        htmxConfig = htmxConfig
      )
      .render

    assert(
      html.contains("""href="/worktrees/TEST-123"""") &&
        html.contains(s"""href="$trackerUrl""""),
      s"Card should contain both detail page and tracker links, got: $html"
    )
  }

  test("renderCard title link wraps title text inside h3") {
    val html = WorktreeCardRenderer
      .renderCard(
        worktree,
        issueData,
        fromCache = false,
        isStale = false,
        progress = None,
        gitStatus = None,
        prData = None,
        reviewStateResult = None,
        now = now,
        sshHost = "test-host",
        htmxConfig = htmxConfig
      )
      .render

    // Verify h3 contains an anchor with the detail link wrapping the title text
    val h3Pattern = """<h3><a href="/worktrees/TEST-123">My Issue</a></h3>""".r
    assert(
      h3Pattern.findFirstIn(html).isDefined,
      s"Card h3 should contain anchor wrapping title text, got: $html"
    )
  }

  test("renderSkeletonCard h3 title links to worktree detail page") {
    val html = WorktreeCardRenderer
      .renderSkeletonCard(
        worktree,
        gitStatus = None,
        now = now,
        htmxConfig = htmxConfig
      )
      .render

    assert(
      html.contains("""href="/worktrees/TEST-123""""),
      s"Skeleton card should contain detail page link, got: $html"
    )
  }

  test("renderSkeletonCard title link wraps Loading text inside h3") {
    val html = WorktreeCardRenderer
      .renderSkeletonCard(
        worktree,
        gitStatus = None,
        now = now,
        htmxConfig = htmxConfig
      )
      .render

    // Verify h3 contains an anchor with the detail link wrapping the Loading text
    val h3Pattern =
      """<h3 class="skeleton-title"><a href="/worktrees/TEST-123">Loading...</a></h3>""".r
    assert(
      h3Pattern.findFirstIn(html).isDefined,
      s"Skeleton card h3 should contain anchor wrapping Loading text, got: $html"
    )
  }

  test("renderCard renders repo-link section when repoUrl is Some") {
    val html = WorktreeCardRenderer
      .renderCard(
        worktree,
        issueData,
        fromCache = false,
        isStale = false,
        progress = None,
        gitStatus = None,
        prData = None,
        reviewStateResult = None,
        now = now,
        sshHost = "test-host",
        htmxConfig = htmxConfig,
        repoUrl = Some("https://github.com/org/repo")
      )
      .render

    assert(
      html.contains("""class="repo-link""""),
      s"Card should contain repo-link section, got: $html"
    )
    assert(
      html.contains("""href="https://github.com/org/repo""""),
      s"Card should contain repo URL, got: $html"
    )
    assert(
      html.contains("""class="repo-button""""),
      s"Card should contain repo-button class, got: $html"
    )
  }

  test("renderCard omits repo-link section when repoUrl is None") {
    val html = WorktreeCardRenderer
      .renderCard(
        worktree,
        issueData,
        fromCache = false,
        isStale = false,
        progress = None,
        gitStatus = None,
        prData = None,
        reviewStateResult = None,
        now = now,
        sshHost = "test-host",
        htmxConfig = htmxConfig,
        repoUrl = None
      )
      .render

    assert(
      !html.contains("repo-link"),
      s"Card should not contain repo-link section when repoUrl is None, got: $html"
    )
  }

  test(
    "renderCard renders PR section when prData is Some with isStale = false"
  ) {
    val pr = PullRequestData(
      "https://github.com/org/repo/pull/1",
      PRState.Open,
      1,
      "My PR"
    )
    val prDisplay = PrDisplayData(pr, isStale = false)

    val html = WorktreeCardRenderer
      .renderCard(
        worktree,
        issueData,
        fromCache = false,
        isStale = false,
        progress = None,
        gitStatus = None,
        prData = Some(prDisplay),
        reviewStateResult = None,
        now = now,
        sshHost = "test-host",
        htmxConfig = htmxConfig
      )
      .render

    val prSection = extractPrSection(html)
    assert(
      html.contains("""class="pr-link""""),
      s"Card should render PR section when prData is defined, got: $html"
    )
    assert(
      !prSection.contains("stale-indicator"),
      s"PR section should not contain stale-indicator when prData.isStale = false, got pr-section: $prSection"
    )
  }

  test(
    "renderCard renders PR section with stale badge when prData.isStale = true"
  ) {
    val pr = PullRequestData(
      "https://github.com/org/repo/pull/2",
      PRState.Open,
      2,
      "Stale PR"
    )
    val prDisplay = PrDisplayData(pr, isStale = true)

    val html = WorktreeCardRenderer
      .renderCard(
        worktree,
        issueData,
        fromCache = false,
        isStale = false,
        progress = None,
        gitStatus = None,
        prData = Some(prDisplay),
        reviewStateResult = None,
        now = now,
        sshHost = "test-host",
        htmxConfig = htmxConfig
      )
      .render

    val prSection = extractPrSection(html)
    assert(
      html.contains("""class="pr-link""""),
      s"Card should render PR section even when stale, got: $html"
    )
    assert(
      prSection.contains("stale-indicator"),
      s"PR section should contain stale-indicator when prData.isStale = true, got pr-section: $prSection"
    )
  }

  test("renderCard omits PR section when prData is None") {
    val html = WorktreeCardRenderer
      .renderCard(
        worktree,
        issueData,
        fromCache = false,
        isStale = false,
        progress = None,
        gitStatus = None,
        prData = None,
        reviewStateResult = None,
        now = now,
        sshHost = "test-host",
        htmxConfig = htmxConfig
      )
      .render

    assert(
      !html.contains("""class="pr-link""""),
      s"Card should not contain PR section when prData is None, got: $html"
    )
  }

  /** Extract the substring between the opening of the PR-link container and the
    * next closing `</p>` so that assertions about the PR section don't
    * accidentally match the card-level stale indicator on the issue-id badge.
    */
  private def extractPrSection(html: String): String =
    val marker = """class="pr-link""""
    val start = html.indexOf(marker)
    if start < 0 then ""
    else
      val end = html.indexOf("</p>", start)
      if end < 0 then html.substring(start) else html.substring(start, end)

  test("renderSkeletonCard issue ID is not a link") {
    val html = WorktreeCardRenderer
      .renderSkeletonCard(
        worktree,
        gitStatus = None,
        now = now,
        htmxConfig = htmxConfig
      )
      .render

    // The issue ID in skeleton card should be in a span, not an anchor
    // Extract the issue-id paragraph and check it doesn't contain <a
    val issueIdSection = """class="issue-id">(.*?)</p>""".r
      .findFirstMatchIn(html)
      .map(_.group(1))
      .getOrElse("")

    assert(
      !issueIdSection.contains("<a"),
      s"Skeleton card issue ID should not be a link, got issue-id section: $issueIdSection"
    )
  }
