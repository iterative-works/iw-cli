// PURPOSE: Pure function for deriving main project paths from worktree paths
// PURPOSE: Strips issue ID suffix patterns to identify the base project directory

package iw.core.model

object ProjectPath:
  /** Derive main project path from a worktree path by stripping the issue ID suffix.
    *
    * Worktree paths follow the pattern: {mainProjectPath}-{issueId}
    * where issueId matches: [A-Z]+-[0-9]+ or just [0-9]+
    *
    * @param worktreePath The full path to the worktree directory
    * @return Some(mainProjectPath) if pattern matches, None otherwise
    */
  def deriveMainProjectPath(worktreePath: String): Option[String] =
    // Extract the directory name (last component of path)
    val dirName = worktreePath.split('/').lastOption.getOrElse("")

    // Pattern to match issue ID suffix: -{LETTERS}-{DIGITS} or -{DIGITS}
    // This matches:
    // - IW-79 (standard format)
    // - IWLE-123 (Linear format)
    // - ABC-9999 (multi-digit)
    // - 123 (GitHub format - numeric only)
    // - A-123 (single letter prefix)
    val issueIdPattern = """-([A-Z]+-\d+|\d+)$""".r

    issueIdPattern.findFirstIn(dirName) match
      case Some(suffix) =>
        // Found issue ID suffix - remove it from the full path
        val mainProjectName = dirName.stripSuffix(suffix)
        // Don't just remove from directory name, reconstruct the full path
        val parentPath = worktreePath.stripSuffix("/" + dirName).stripSuffix(dirName)
        if parentPath.isEmpty then
          Some(mainProjectName)
        else
          Some(s"$parentPath/$mainProjectName")
      case None =>
        // No issue ID pattern found
        None
