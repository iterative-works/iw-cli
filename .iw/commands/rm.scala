// PURPOSE: Remove a worktree for a completed issue
// USAGE: iw rm <issue-id>
// ARGS:
//   <issue-id>: The issue identifier to remove
// EXAMPLE: iw rm IWLE-123

//> using scala 3.3.1
//> using file "../core/Output.scala"

import iw.core.Output

object RmCommand:
  def main(args: Array[String]): Unit =
    Output.info("Not implemented yet")
    Output.info("This command will remove a worktree for a completed issue")
