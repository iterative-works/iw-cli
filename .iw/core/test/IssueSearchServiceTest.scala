// PURPOSE: Unit tests for IssueSearchService application service
// PURPOSE: Tests issue search with ID parsing and URL building for different trackers

package iw.core.application

import munit.FunSuite
import iw.core.model.{IssueId, ProjectConfiguration, IssueTrackerType, Issue, ApiToken}
import iw.core.dashboard.IssueSearchResult
import iw.core.dashboard.IssueSearchService

class IssueSearchServiceTest extends FunSuite:

  test("search with valid Linear issue ID returns result"):
    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.Linear,
      team = "IWLE",
      projectName = "test",
      teamPrefix = Some("IWLE")
    )

    // Mock fetchIssue function that returns a successful result
    val fetchIssue = (id: IssueId) =>
      Right(Issue("IWLE-100", "Test Issue", "In Progress", Some("Jane"), Some("Description")))

    // Mock searchIssues - should not be called when exact ID match succeeds
    val searchIssues = (query: String) =>
      fail("searchIssues should not be called for exact ID match")

    val results = IssueSearchService.search("IWLE-100", config, fetchIssue, searchIssues)

    assert(results.isRight, "Search should succeed")
    assertEquals(results.map(_.length), Right(1), "Should return one result")

    results.foreach { list =>
      val result = list.head
      assertEquals(result.id, "IWLE-100")
      assertEquals(result.title, "Test Issue")
      assertEquals(result.status, "In Progress")
      assert(result.url.contains("IWLE-100"), "URL should contain issue ID")
    }

  test("search with invalid issue ID returns empty list"):
    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.Linear,
      team = "IWLE",
      projectName = "test",
      teamPrefix = Some("IWLE")
    )

    // Mock fetchIssue function that should not be called for invalid ID
    val fetchIssue = (id: IssueId) =>
      fail("fetchIssue should not be called for invalid ID")

    // Mock searchIssues - returns empty list (no matches)
    val searchIssues = (query: String) =>
      Right(List.empty)

    val results = IssueSearchService.search("INVALID", config, fetchIssue, searchIssues)

    assert(results.isRight, "Search should succeed with empty results")
    assertEquals(results.map(_.length), Right(0), "Should return zero results")

  test("search with valid ID but fetch failure returns empty list"):
    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.Linear,
      team = "IWLE",
      projectName = "test",
      teamPrefix = Some("IWLE")
    )

    // Mock fetchIssue function that returns an error
    val fetchIssue = (id: IssueId) =>
      Left("Issue not found")

    // Mock searchIssues - returns empty list (no matches in text search either)
    val searchIssues = (query: String) =>
      Right(List.empty)

    val results = IssueSearchService.search("IWLE-999", config, fetchIssue, searchIssues)

    assert(results.isRight, "Search should succeed with empty results")
    assertEquals(results.map(_.length), Right(0), "Should return zero results")

  test("search with GitHub issue ID returns result"):
    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.GitHub,
      team = "IW",
      projectName = "iw-cli",
      repository = Some("iterative-works/iw-cli"),
      teamPrefix = Some("IW")
    )

    val fetchIssue = (id: IssueId) =>
      Right(Issue("IW-79", "GitHub Issue", "Open", None, None))

    val searchIssues = (query: String) =>
      fail("searchIssues should not be called for exact ID match")

    val results = IssueSearchService.search("IW-79", config, fetchIssue, searchIssues)

    assert(results.isRight, "Search should succeed")
    assertEquals(results.map(_.length), Right(1), "Should return one result")

    results.foreach { list =>
      val result = list.head
      assertEquals(result.id, "IW-79")
      assertEquals(result.title, "GitHub Issue")
      assert(result.url.contains("github.com"), "URL should be GitHub URL")
    }

  test("search with YouTrack issue ID returns result"):
    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.YouTrack,
      team = "PROJ",
      projectName = "project",
      youtrackBaseUrl = Some("https://youtrack.example.com"),
      teamPrefix = Some("PROJ")
    )

    val fetchIssue = (id: IssueId) =>
      Right(Issue("PROJ-123", "YouTrack Issue", "To Do", Some("John"), None))

    val searchIssues = (query: String) =>
      fail("searchIssues should not be called for exact ID match")

    val results = IssueSearchService.search("PROJ-123", config, fetchIssue, searchIssues)

    assert(results.isRight, "Search should succeed")
    assertEquals(results.map(_.length), Right(1), "Should return one result")

    results.foreach { list =>
      val result = list.head
      assertEquals(result.id, "PROJ-123")
      assertEquals(result.title, "YouTrack Issue")
      assertEquals(result.status, "To Do")
      assert(result.url.contains("youtrack.example.com"), "URL should be YouTrack URL")
    }

  test("search with empty query returns empty list"):
    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.Linear,
      team = "IWLE",
      projectName = "test",
      teamPrefix = Some("IWLE")
    )

    val fetchIssue = (id: IssueId) =>
      fail("fetchIssue should not be called for empty query")

    val searchIssues = (query: String) =>
      fail("searchIssues should not be called for empty query")

    val results = IssueSearchService.search("", config, fetchIssue, searchIssues)

    assert(results.isRight, "Search should succeed with empty results")
    assertEquals(results.map(_.length), Right(0), "Should return zero results")

  test("search with whitespace-only query returns empty list"):
    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.Linear,
      team = "IWLE",
      projectName = "test",
      teamPrefix = Some("IWLE")
    )

    val fetchIssue = (id: IssueId) =>
      fail("fetchIssue should not be called for whitespace query")

    val searchIssues = (query: String) =>
      fail("searchIssues should not be called for whitespace query")

    val results = IssueSearchService.search("   ", config, fetchIssue, searchIssues)

    assert(results.isRight, "Search should succeed with empty results")
    assertEquals(results.map(_.length), Right(0), "Should return zero results")

  test("search handles case-insensitive issue IDs"):
    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.Linear,
      team = "IWLE",
      projectName = "test",
      teamPrefix = Some("IWLE")
    )

    val fetchIssue = (id: IssueId) =>
      Right(Issue("IWLE-100", "Test Issue", "Done", None, None))

    val searchIssues = (query: String) =>
      fail("searchIssues should not be called for exact ID match")

    // Search with lowercase should work (IssueId.parse normalizes to uppercase)
    val results = IssueSearchService.search("iwle-100", config, fetchIssue, searchIssues)

    assert(results.isRight, "Search should succeed")
    assertEquals(results.map(_.length), Right(1), "Should return one result")

  test("search with GitLab issue ID returns result with correct URL format"):
    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.GitLab,
      team = "IW",
      projectName = "my-project",
      repository = Some("my-org/my-project"),
      teamPrefix = Some("IW")
    )

    // GitLab uses numeric IDs, which are stored as "#123" format
    val fetchIssue = (id: IssueId) =>
      Right(Issue("#123", "GitLab Issue", "opened", None, None))

    // Search not implemented for GitLab yet
    val searchIssues = (query: String) => Left("Not implemented")

    // GitLab requires numeric issue IDs
    val results = IssueSearchService.search("123", config, fetchIssue, searchIssues)

    assert(results.isRight, "Search should succeed")
    assertEquals(results.map(_.length), Right(1), "Should return one result")

    results.foreach { list =>
      val result = list.head
      assertEquals(result.id, "#123")
      assertEquals(result.title, "GitLab Issue")
      assert(result.url.contains("gitlab.com"), "URL should be GitLab URL")
      assert(result.url.contains("/-/issues/"), "URL should have GitLab path format")
      assert(result.url.contains("my-org/my-project"), "URL should contain repository")
    }

  test("search with GitLab issue ID uses self-hosted baseUrl"):
    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.GitLab,
      team = "PROJ",
      projectName = "project",
      repository = Some("team/app"),
      youtrackBaseUrl = Some("https://gitlab.company.com"),
      teamPrefix = Some("PROJ")
    )

    // GitLab uses numeric IDs, which are stored as "#456" format
    val fetchIssue = (id: IssueId) =>
      Right(Issue("#456", "Self-hosted GitLab Issue", "closed", None, None))

    // Search not implemented for GitLab yet
    val searchIssues = (query: String) => Left("Not implemented")

    // GitLab requires numeric issue IDs
    val results = IssueSearchService.search("456", config, fetchIssue, searchIssues)

    assert(results.isRight, "Search should succeed")
    results.foreach { list =>
      val result = list.head
      assert(result.url.contains("gitlab.company.com"), "URL should use self-hosted base URL")
      assert(result.url.contains("/-/issues/456"), "URL should have correct issue number")
    }

  // ========== fetchRecent Tests ==========

  test("fetchRecent success case with GitHub tracker"):
    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.GitHub,
      team = "IW",
      projectName = "iw-cli",
      repository = Some("iterative-works/iw-cli"),
      teamPrefix = Some("IW")
    )

    // Mock fetch function that returns recent issues
    val fetchRecentIssues = (limit: Int) =>
      Right(List(
        Issue("#132", "Add feature", "open", None, None),
        Issue("#131", "Fix bug", "open", None, None),
        Issue("#130", "Update docs", "open", None, None)
      ))

    val results = IssueSearchService.fetchRecent(config, fetchRecentIssues)

    assert(results.isRight, "fetchRecent should succeed")
    assertEquals(results.map(_.length), Right(3), "Should return three results")

    results.foreach { list =>
      assertEquals(list(0).id, "#132")
      assertEquals(list(0).title, "Add feature")
      assertEquals(list(0).status, "open")
      assert(list(0).url.contains("github.com"), "URL should be GitHub URL")
      assert(list(0).url.contains("132"), "URL should contain issue number")
      assertEquals(list(0).hasWorktree, false, "Should not have worktree by default")
    }

  test("fetchRecent with worktree check integration"):
    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.GitHub,
      team = "IW",
      projectName = "iw-cli",
      repository = Some("iterative-works/iw-cli"),
      teamPrefix = Some("IW")
    )

    val fetchRecentIssues = (limit: Int) =>
      Right(List(
        Issue("#132", "Add feature", "open", None, None),
        Issue("#131", "Fix bug", "open", None, None)
      ))

    // Mock worktree check - #132 has worktree, #131 does not
    val checkWorktreeExists = (issueId: String) => issueId == "#132"

    val results = IssueSearchService.fetchRecent(config, fetchRecentIssues, checkWorktreeExists)

    assert(results.isRight, "fetchRecent should succeed")
    results.foreach { list =>
      assertEquals(list(0).hasWorktree, true, "Issue #132 should have worktree")
      assertEquals(list(1).hasWorktree, false, "Issue #131 should not have worktree")
    }

  test("fetchRecent handles fetch errors gracefully"):
    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.GitHub,
      team = "IW",
      projectName = "iw-cli",
      repository = Some("iterative-works/iw-cli"),
      teamPrefix = Some("IW")
    )

    // Mock fetch function that returns error
    val fetchRecentIssues = (limit: Int) =>
      Left("Failed to fetch recent issues: API error")

    val results = IssueSearchService.fetchRecent(config, fetchRecentIssues)

    assert(results.isLeft, "fetchRecent should return error")
    assert(results.left.getOrElse("").contains("Failed to fetch recent issues"))

  test("fetchRecent returns empty list when no issues"):
    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.GitHub,
      team = "IW",
      projectName = "iw-cli",
      repository = Some("iterative-works/iw-cli"),
      teamPrefix = Some("IW")
    )

    val fetchRecentIssues = (limit: Int) =>
      Right(List.empty)

    val results = IssueSearchService.fetchRecent(config, fetchRecentIssues)

    assert(results.isRight, "fetchRecent should succeed")
    assertEquals(results.map(_.length), Right(0), "Should return zero results")

  // ========== search() with text search fallback Tests ==========

  test("search() exact ID match returns that issue (priority over text search)"):
    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.GitHub,
      team = "IW",
      projectName = "iw-cli",
      repository = Some("iterative-works/iw-cli"),
      teamPrefix = Some("IW")
    )

    // Mock fetchIssue - returns the exact issue
    val fetchIssue = (id: IssueId) =>
      Right(Issue("#79", "Exact match issue", "open", None, None))

    // Mock searchIssues - should not be called for exact ID match
    val searchIssues = (query: String) =>
      fail("searchIssues should not be called when exact ID match succeeds")

    val results = IssueSearchService.search("IW-79", config, fetchIssue, searchIssues)

    assert(results.isRight, "Search should succeed")
    assertEquals(results.map(_.length), Right(1), "Should return one result")
    results.foreach { list =>
      assertEquals(list(0).id, "#79")
      assertEquals(list(0).title, "Exact match issue")
    }

  test("search() invalid ID format triggers text search"):
    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.GitHub,
      team = "IW",
      projectName = "iw-cli",
      repository = Some("iterative-works/iw-cli"),
      teamPrefix = Some("IW")
    )

    // Mock fetchIssue - should not be called for invalid ID
    val fetchIssue = (id: IssueId) =>
      fail("fetchIssue should not be called for invalid ID format")

    // Mock searchIssues - returns results from text search
    val searchIssues = (query: String) =>
      Right(List(
        Issue("#132", "Authentication bug fix", "open", None, None),
        Issue("#131", "Add authentication", "open", None, None)
      ))

    val results = IssueSearchService.search("authentication", config, fetchIssue, searchIssues)

    assert(results.isRight, "Search should succeed")
    assertEquals(results.map(_.length), Right(2), "Should return two results from text search")
    results.foreach { list =>
      assertEquals(list(0).id, "#132")
      assertEquals(list(0).title, "Authentication bug fix")
      assertEquals(list(1).id, "#131")
      assertEquals(list(1).title, "Add authentication")
    }

  test("search() valid ID but not found triggers text search"):
    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.GitHub,
      team = "IW",
      projectName = "iw-cli",
      repository = Some("iterative-works/iw-cli"),
      teamPrefix = Some("IW")
    )

    // Mock fetchIssue - returns error (issue not found)
    val fetchIssue = (id: IssueId) =>
      Left("Issue not found")

    // Mock searchIssues - returns results from text search
    val searchIssues = (query: String) =>
      Right(List(
        Issue("#999", "Issue 999 from text search", "open", None, None)
      ))

    val results = IssueSearchService.search("IW-999", config, fetchIssue, searchIssues)

    assert(results.isRight, "Search should succeed with text search fallback")
    assertEquals(results.map(_.length), Right(1), "Should return one result from text search")
    results.foreach { list =>
      assertEquals(list(0).id, "#999")
      assertEquals(list(0).title, "Issue 999 from text search")
    }

  test("search() text search returns matching issues"):
    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.GitHub,
      team = "IW",
      projectName = "iw-cli",
      repository = Some("iterative-works/iw-cli"),
      teamPrefix = Some("IW")
    )

    val fetchIssue = (id: IssueId) =>
      fail("fetchIssue should not be called for non-ID query")

    val searchIssues = (query: String) =>
      assertEquals(query, "fix bug")
      Right(List(
        Issue("#100", "Fix critical bug", "open", None, None),
        Issue("#99", "Bug fix for login", "open", None, None)
      ))

    val results = IssueSearchService.search("fix bug", config, fetchIssue, searchIssues)

    assert(results.isRight, "Search should succeed")
    assertEquals(results.map(_.length), Right(2), "Should return two results")
    results.foreach { list =>
      assert(list(0).url.contains("github.com"), "Should have GitHub URL")
      assert(list(0).url.contains("100"), "Should have issue number in URL")
    }

  test("search() empty query returns empty results (skips text search)"):
    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.GitHub,
      team = "IW",
      projectName = "iw-cli",
      repository = Some("iterative-works/iw-cli"),
      teamPrefix = Some("IW")
    )

    val fetchIssue = (id: IssueId) =>
      fail("fetchIssue should not be called for empty query")

    val searchIssues = (query: String) =>
      fail("searchIssues should not be called for empty query")

    val results = IssueSearchService.search("", config, fetchIssue, searchIssues)

    assert(results.isRight, "Search should succeed with empty results")
    assertEquals(results.map(_.length), Right(0), "Should return zero results")

  test("search() text search error handling"):
    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.GitHub,
      team = "IW",
      projectName = "iw-cli",
      repository = Some("iterative-works/iw-cli"),
      teamPrefix = Some("IW")
    )

    val fetchIssue = (id: IssueId) =>
      fail("fetchIssue should not be called for non-ID query")

    val searchIssues = (query: String) =>
      Left("Failed to search issues: API error")

    val results = IssueSearchService.search("test query", config, fetchIssue, searchIssues)

    assert(results.isLeft, "Search should return error from text search")
    assert(results.left.getOrElse("").contains("Failed to search issues"))

  test("search() text search sorts closed issues to end"):
    val config = ProjectConfiguration(
      trackerType = IssueTrackerType.GitHub,
      team = "IW",
      projectName = "iw-cli",
      repository = Some("iterative-works/iw-cli"),
      teamPrefix = Some("IW")
    )

    val fetchIssue = (id: IssueId) =>
      fail("fetchIssue should not be called for non-ID query")

    // Return issues with mixed states (closed issues first in original order)
    val searchIssues = (query: String) =>
      Right(List(
        Issue("#101", "Done issue", "Done", None, None),
        Issue("#102", "Open issue", "Open", None, None),
        Issue("#103", "Closed issue", "closed", None, None),
        Issue("#104", "In Progress", "In Progress", None, None),
        Issue("#105", "Canceled issue", "Canceled", None, None),
        Issue("#106", "Solved issue", "Solved", None, None)
      ))

    val results = IssueSearchService.search("test", config, fetchIssue, searchIssues)

    assert(results.isRight, "Search should succeed")
    results.foreach { list =>
      // Open issues should come first (Open, In Progress)
      assertEquals(list(0).status, "Open", "First should be Open")
      assertEquals(list(1).status, "In Progress", "Second should be In Progress")
      // Closed issues should be at the end (Done, closed, Canceled, Solved)
      assertEquals(list(2).status, "Done", "Third should be Done (closed)")
      assertEquals(list(3).status, "closed", "Fourth should be closed")
      assertEquals(list(4).status, "Canceled", "Fifth should be Canceled (closed)")
      assertEquals(list(5).status, "Solved", "Sixth should be Solved (closed)")
    }
