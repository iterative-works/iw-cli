// PURPOSE: Open a tmux session for an existing worktree
// USAGE: iw open <issue-id>
// ARGS:
//   <issue-id>: The issue identifier to open
// EXAMPLE: iw open IWLE-123

//> using scala 3.3.1
//> using file "../core/Output.scala"

import iw.core.Output

object OpenCommand:
  def main(args: Array[String]): Unit =
    Output.info("Not implemented yet")
    Output.info("This command will open a tmux session for an existing worktree")
