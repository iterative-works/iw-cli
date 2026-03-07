// PURPOSE: Pure functions for parsing and rewriting phase task markdown content
// PURPOSE: Updates Phase Status line and [reviewed] checkboxes based on [impl] state

package iw.core.model

object PhaseTaskFile:

  private val PhaseStatusPattern = """^\*\*Phase Status:\*\*.*$""".r

  /** Update the Phase Status line to "Complete".
    *
    * Finds the line matching `**Phase Status:**` (bold format) and replaces
    * the value with "Complete". If no such line exists, appends
    * `**Phase Status:** Complete` at the end.
    *
    * @param content Full markdown file content
    * @return Updated content with Phase Status set to Complete
    */
  def markComplete(content: String): String =
    val lines = content.split("\n", -1)
    var found = false
    val updated = lines.map { line =>
      if PhaseStatusPattern.matches(line) then
        found = true
        "**Phase Status:** Complete"
      else line
    }
    if found then updated.mkString("\n")
    else
      val trimmed = content.stripTrailing()
      trimmed + "\n**Phase Status:** Complete\n"

  // Matches lines where:
  // - primary checkbox is checked: [x]
  // - followed by any tag like [impl], [test], [setup], etc.
  // - followed by unchecked [reviewed]: [ ] [reviewed]
  private val ReviewablePattern =
    """^(- \[x\] \[[^\]]+\]) \[ \] \[reviewed\](.*)$""".r

  /** Mark all checked tasks as [reviewed] where a [reviewed] marker exists.
    *
    * For lines matching `- [x] [tag] [ ] [reviewed]`,
    * changes `[ ] [reviewed]` to `[x] [reviewed]`.
    *
    * Only touches lines that already have a `[reviewed]` marker.
    * Lines without `[reviewed]` are left unchanged.
    * Lines where the primary checkbox is unchecked `- [ ]` are left unchanged.
    *
    * @param content Full markdown file content
    * @return Updated content with reviewed checkboxes marked
    */
  def markReviewed(content: String): String =
    val lines = content.split("\n", -1)
    lines.map { line =>
      line match
        case ReviewablePattern(prefix, rest) => s"$prefix [x] [reviewed]$rest"
        case other => other
    }.mkString("\n")
