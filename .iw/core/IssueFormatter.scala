// PURPOSE: Formatter for displaying issue details with Unicode borders
// PURPOSE: Provides format method to create human-readable issue output

package iw.core

object IssueFormatter:
  def format(issue: Issue): String =
    val border = "━" * 40
    val header = s"${issue.id}: ${issue.title}"

    val assigneeValue = issue.assignee.getOrElse("None")

    val lines = Seq(
      border,
      header,
      border,
      "",
      f"Status:     ${issue.status}",
      f"Assignee:   $assigneeValue"
    )

    val withDescription = issue.description match
      case Some(desc) =>
        lines ++ Seq(
          "",
          "Description:",
          desc.split("\n").map(line => s"  $line").mkString("\n")
        )
      case None =>
        lines

    withDescription.mkString("\n")
