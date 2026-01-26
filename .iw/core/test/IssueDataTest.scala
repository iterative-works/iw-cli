// PURPOSE: Unit tests for IssueData domain model
// PURPOSE: Tests factory method and field preservation

package iw.core.domain

import munit.FunSuite
import iw.core.model.Issue
import java.time.Instant
import iw.core.model.IssueData

class IssueDataTest extends FunSuite:

  test("fromIssue creates IssueData with correct url and timestamp"):
    val issue = Issue("IWLE-123", "Test Issue", "Open", Some("Jane Doe"), Some("Description"))
    val url = "https://linear.app/team/issue/IWLE-123"
    val now = Instant.now()

    val issueData = IssueData.fromIssue(issue, url, now)

    assertEquals(issueData.id, "IWLE-123")
    assertEquals(issueData.url, url)
    assertEquals(issueData.fetchedAt, now)

  test("fromIssue preserves all Issue fields"):
    val issue = Issue("IWLE-456", "Another Issue", "In Progress", Some("John Smith"), Some("Test description"))
    val url = "https://linear.app/team/issue/IWLE-456"
    val now = Instant.now()

    val issueData = IssueData.fromIssue(issue, url, now)

    assertEquals(issueData.id, issue.id)
    assertEquals(issueData.title, issue.title)
    assertEquals(issueData.status, issue.status)
    assertEquals(issueData.assignee, issue.assignee)
    assertEquals(issueData.description, issue.description)

  test("fromIssue handles None assignee"):
    val issue = Issue("PROJ-789", "Unassigned Issue", "Todo", None, None)
    val url = "https://youtrack.example.com/issue/PROJ-789"
    val now = Instant.now()

    val issueData = IssueData.fromIssue(issue, url, now)

    assertEquals(issueData.assignee, None)
    assertEquals(issueData.description, None)

  test("fetchedAt timestamp is set to provided Instant"):
    val issue = Issue("TEST-1", "Test", "Open", None, None)
    val url = "https://example.com/issue/TEST-1"
    val specificTime = Instant.parse("2025-12-20T10:30:00Z")

    val issueData = IssueData.fromIssue(issue, url, specificTime)

    assertEquals(issueData.fetchedAt, specificTime)
