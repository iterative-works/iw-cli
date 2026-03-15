// PURPOSE: Unit tests for WorktreeCardRenderer card HTML generation
// PURPOSE: Validates title links to detail page and preserved tracker links on issue ID badge

package iw.tests

import iw.core.dashboard.presentation.views.{WorktreeCardRenderer, HtmxCardConfig}
import iw.core.model.{WorktreeRegistration, IssueData}
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
    val html = WorktreeCardRenderer.renderCard(
      worktree, issueData, fromCache = false, isStale = false,
      progress = None, gitStatus = None, prData = None,
      reviewStateResult = None, now = now, sshHost = "test-host",
      htmxConfig = htmxConfig
    ).render

    assert(
      html.contains("""href="/worktrees/TEST-123""""),
      s"Card should contain detail page link, got: $html"
    )
  }

  test("renderCard issue ID badge still links to external tracker") {
    val html = WorktreeCardRenderer.renderCard(
      worktree, issueData, fromCache = false, isStale = false,
      progress = None, gitStatus = None, prData = None,
      reviewStateResult = None, now = now, sshHost = "test-host",
      htmxConfig = htmxConfig
    ).render

    assert(
      html.contains(s"""href="$trackerUrl""""),
      s"Card should contain tracker link, got: $html"
    )
  }

  test("renderCard has both detail page link and tracker link") {
    val html = WorktreeCardRenderer.renderCard(
      worktree, issueData, fromCache = false, isStale = false,
      progress = None, gitStatus = None, prData = None,
      reviewStateResult = None, now = now, sshHost = "test-host",
      htmxConfig = htmxConfig
    ).render

    assert(
      html.contains("""href="/worktrees/TEST-123"""") &&
      html.contains(s"""href="$trackerUrl""""),
      s"Card should contain both detail page and tracker links, got: $html"
    )
  }

  test("renderCard title link wraps title text inside h3") {
    val html = WorktreeCardRenderer.renderCard(
      worktree, issueData, fromCache = false, isStale = false,
      progress = None, gitStatus = None, prData = None,
      reviewStateResult = None, now = now, sshHost = "test-host",
      htmxConfig = htmxConfig
    ).render

    // Verify h3 contains an anchor with the detail link wrapping the title text
    val h3Pattern = """<h3><a href="/worktrees/TEST-123">My Issue</a></h3>""".r
    assert(
      h3Pattern.findFirstIn(html).isDefined,
      s"Card h3 should contain anchor wrapping title text, got: $html"
    )
  }

  test("renderSkeletonCard h3 title links to worktree detail page") {
    val html = WorktreeCardRenderer.renderSkeletonCard(
      worktree, gitStatus = None, now = now, htmxConfig = htmxConfig
    ).render

    assert(
      html.contains("""href="/worktrees/TEST-123""""),
      s"Skeleton card should contain detail page link, got: $html"
    )
  }

  test("renderSkeletonCard title link wraps Loading text inside h3") {
    val html = WorktreeCardRenderer.renderSkeletonCard(
      worktree, gitStatus = None, now = now, htmxConfig = htmxConfig
    ).render

    // Verify h3 contains an anchor with the detail link wrapping the Loading text
    val h3Pattern = """<h3 class="skeleton-title"><a href="/worktrees/TEST-123">Loading...</a></h3>""".r
    assert(
      h3Pattern.findFirstIn(html).isDefined,
      s"Skeleton card h3 should contain anchor wrapping Loading text, got: $html"
    )
  }

  test("renderSkeletonCard issue ID is not a link") {
    val html = WorktreeCardRenderer.renderSkeletonCard(
      worktree, gitStatus = None, now = now, htmxConfig = htmxConfig
    ).render

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
