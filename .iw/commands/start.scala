// PURPOSE: Start work on an issue by creating/switching to a worktree
// USAGE: iw start <issue-id>
// ARGS:
//   <issue-id>: The issue identifier to work on
// EXAMPLE: iw start IWLE-123

//> using scala 3.3.1
//> using file "../core/Output.scala"

import iw.core.Output

object StartCommand:
  def main(args: Array[String]): Unit =
    Output.info("Not implemented yet")
    Output.info("This command will create/switch to a worktree for an issue")
