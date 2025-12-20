// PURPOSE: Pure functions for parsing markdown task files
// PURPOSE: Extracts checkbox tasks and phase names from agile workflow files

package iw.core.application

/** Task count result from parsing markdown.
  *
  * @param total Total number of checkbox tasks found
  * @param completed Number of completed checkbox tasks (marked with [x])
  */
case class TaskCount(total: Int, completed: Int)

/** Phase entry from tasks.md Phase Index section.
  *
  * @param phaseNumber Phase number (1-based)
  * @param isComplete Whether the phase checkbox is checked
  * @param name Phase name (text after "Phase N:")
  */
case class PhaseIndexEntry(phaseNumber: Int, isComplete: Boolean, name: String)

/** Pure functions for parsing markdown task files.
  *
  * Recognizes standard checkbox format:
  * - `- [ ]` for incomplete tasks
  * - `- [x]` or `- [X]` for completed tasks
  *
  * Ignores:
  * - Other bullet styles (*, +, numbered lists)
  * - Malformed checkboxes
  * - Non-checkbox content
  */
object MarkdownTaskParser:

  /** Parse markdown lines and count checkbox tasks.
    *
    * Counts tasks marked with:
    * - `- [ ]` (incomplete)
    * - `- [x]` or `- [X]` (complete)
    *
    * Handles:
    * - Indented checkboxes (nested tasks)
    * - Extra whitespace around checkbox
    * - Case-insensitive completion marker
    *
    * Ignores:
    * - Other bullet styles (*, +)
    * - Numbered lists
    * - Malformed checkboxes
    *
    * @param lines Lines from markdown file
    * @return TaskCount with total and completed task counts
    */
  def parseTasks(lines: Seq[String]): TaskCount =
    // Regex: optional whitespace, dash, required whitespace, [x or X or space], optional rest
    val checkboxPattern = "^\\s*-\\s+\\[([ xX])\\]".r

    val tasks = lines.flatMap { line =>
      checkboxPattern.findFirstMatchIn(line).map { m =>
        val marker = m.group(1)
        val isComplete = marker.toLowerCase == "x"
        (1, if isComplete then 1 else 0)
      }
    }

    val (total, completed) = tasks.foldLeft((0, 0)) { case ((t, c), (taskTotal, taskCompleted)) =>
      (t + taskTotal, c + taskCompleted)
    }

    TaskCount(total, completed)

  /** Extract phase name from markdown header.
    *
    * Looks for headers matching:
    * - `# Phase N: Phase Name`
    * - `# Phase N Tasks: Phase Name`
    *
    * Returns the text after the colon, trimmed of whitespace.
    *
    * If multiple matching headers exist, returns the first one.
    * Returns None if no matching header found.
    *
    * @param lines Lines from markdown file
    * @return Phase name if found, None otherwise
    */
  def extractPhaseName(lines: Seq[String]): Option[String] =
    // Regex: # Phase followed by number, optional "Tasks", colon, then capture the name
    val phaseHeaderPattern = "^#\\s+Phase\\s+\\d+.*:\\s*(.+)$".r

    lines.collectFirst {
      case phaseHeaderPattern(name) => name.trim
    }

  /** Parse Phase Index from tasks.md to determine phase completion status.
    *
    * Looks for lines matching:
    * - `- [x] Phase N: Phase Name ...` (complete)
    * - `- [ ] Phase N: Phase Name ...` (incomplete)
    *
    * Extracts the phase name (text between colon and any parenthesis/arrow).
    *
    * @param lines Lines from tasks.md file
    * @return List of PhaseIndexEntry in order found
    */
  def parsePhaseIndex(lines: Seq[String]): List[PhaseIndexEntry] =
    // Regex: checkbox, "Phase", number, colon, name (stop at parenthesis, arrow, or end)
    val phaseIndexPattern = "^\\s*-\\s+\\[([ xX])\\]\\s+Phase\\s+(\\d+):\\s*([^(→]+)".r

    lines.flatMap { line =>
      phaseIndexPattern.findFirstMatchIn(line).map { m =>
        val marker = m.group(1)
        val phaseNum = m.group(2).toInt
        val name = m.group(3).trim
        val isComplete = marker.toLowerCase == "x"
        PhaseIndexEntry(phaseNum, isComplete, name)
      }
    }.toList
