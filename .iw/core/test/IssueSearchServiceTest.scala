// PURPOSE: Unit tests for IssueSearchService application service
// PURPOSE: Tests issue search with ID parsing and URL building for different trackers

package iw.core.application

import munit.FunSuite
import iw.core.{IssueId, ProjectConfiguration, IssueTrackerType, Issue, ApiToken}
import iw.core.domain.IssueSearchResult

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

    val results = IssueSearchService.search("IWLE-100", config, fetchIssue)

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

    val results = IssueSearchService.search("INVALID", config, fetchIssue)

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

    val results = IssueSearchService.search("IWLE-999", config, fetchIssue)

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

    val results = IssueSearchService.search("IW-79", config, fetchIssue)

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

    val results = IssueSearchService.search("PROJ-123", config, fetchIssue)

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

    val results = IssueSearchService.search("", config, fetchIssue)

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

    val results = IssueSearchService.search("   ", config, fetchIssue)

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

    // Search with lowercase should work (IssueId.parse normalizes to uppercase)
    val results = IssueSearchService.search("iwle-100", config, fetchIssue)

    assert(results.isRight, "Search should succeed")
    assertEquals(results.map(_.length), Right(1), "Should return one result")
