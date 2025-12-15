// PURPOSE: Unit tests for Issue display formatting
// PURPOSE: Tests IssueFormatter.format with various field combinations

//> using scala 3.3.1
//> using dep org.scalameta::munit::1.0.0
//> using file "../IssueFormatter.scala"
//> using file "../Issue.scala"
//> using file "../IssueId.scala"

package iw.core.test

import iw.core.*
import munit.FunSuite

class IssueFormatterTest extends FunSuite:

  test("format displays all fields with Unicode border"):
    val issue = Issue(
      id = "IWLE-123",
      title = "Add user login",
      status = "In Progress",
      assignee = Some("Michal Příhoda"),
      description = Some("Users need to be able to log in")
    )

    val output = IssueFormatter.format(issue)

    assert(output.contains("━"))
    assert(output.contains("IWLE-123: Add user login"))
    assert(output.contains("Status:"))
    assert(output.contains("In Progress"))
    assert(output.contains("Assignee:"))
    assert(output.contains("Michal Příhoda"))
    assert(output.contains("Description:"))
    assert(output.contains("Users need to be able to log in"))

  test("format displays issue with no assignee"):
    val issue = Issue(
      id = "IWLE-456",
      title = "Fix bug",
      status = "Todo",
      assignee = None,
      description = Some("Bug description")
    )

    val output = IssueFormatter.format(issue)

    assert(output.contains("IWLE-456: Fix bug"))
    assert(output.contains("Status:"))
    assert(output.contains("Todo"))
    assert(output.contains("Assignee:"))
    assert(output.contains("None"))
    assert(output.contains("Description:"))

  test("format displays issue with no description"):
    val issue = Issue(
      id = "IWLE-789",
      title = "Simple task",
      status = "Done",
      assignee = Some("John Doe"),
      description = None
    )

    val output = IssueFormatter.format(issue)

    assert(output.contains("IWLE-789: Simple task"))
    assert(output.contains("Status:"))
    assert(output.contains("Done"))
    assert(output.contains("Assignee:"))
    assert(output.contains("John Doe"))
    assert(!output.contains("Description:"))

  test("format displays issue with no assignee and no description"):
    val issue = Issue(
      id = "IWLE-999",
      title = "Unassigned task",
      status = "Backlog",
      assignee = None,
      description = None
    )

    val output = IssueFormatter.format(issue)

    assert(output.contains("IWLE-999: Unassigned task"))
    assert(output.contains("Status:"))
    assert(output.contains("Backlog"))
    assert(output.contains("Assignee:"))
    assert(output.contains("None"))
    assert(!output.contains("Description:"))

  test("format handles multiline description"):
    val issue = Issue(
      id = "IWLE-111",
      title = "Complex task",
      status = "In Progress",
      assignee = Some("Jane"),
      description = Some("Line 1\nLine 2\nLine 3")
    )

    val output = IssueFormatter.format(issue)

    assert(output.contains("Description:"))
    assert(output.contains("Line 1"))
    assert(output.contains("Line 2"))
    assert(output.contains("Line 3"))
