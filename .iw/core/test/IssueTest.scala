// PURPOSE: Unit tests for Issue entity construction and validation
// PURPOSE: Tests Issue case class creation with all field combinations
package iw.tests

import iw.core.*
import munit.FunSuite
import iw.core.model.Issue

class IssueTest extends FunSuite:

  test("Issue constructs with all fields populated"):
    val issue = Issue(
      id = "IWLE-123",
      title = "Add user login",
      status = "In Progress",
      assignee = Some("Michal Příhoda"),
      description = Some("Users need to be able to log in to the application")
    )

    assertEquals(issue.id, "IWLE-123")
    assertEquals(issue.title, "Add user login")
    assertEquals(issue.status, "In Progress")
    assertEquals(issue.assignee, Some("Michal Příhoda"))
    assertEquals(issue.description, Some("Users need to be able to log in to the application"))

  test("Issue constructs with no assignee"):
    val issue = Issue(
      id = "IWLE-456",
      title = "Fix bug",
      status = "Todo",
      assignee = None,
      description = Some("Bug description")
    )

    assertEquals(issue.id, "IWLE-456")
    assertEquals(issue.assignee, None)

  test("Issue constructs with no description"):
    val issue = Issue(
      id = "IWLE-789",
      title = "Simple task",
      status = "Done",
      assignee = Some("John Doe"),
      description = None
    )

    assertEquals(issue.id, "IWLE-789")
    assertEquals(issue.description, None)

  test("Issue constructs with no assignee and no description"):
    val issue = Issue(
      id = "IWLE-999",
      title = "Unassigned task",
      status = "Backlog",
      assignee = None,
      description = None
    )

    assertEquals(issue.assignee, None)
    assertEquals(issue.description, None)
