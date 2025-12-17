// PURPOSE: Implementation of 'iw status' command
// PURPOSE: Displays status of the current worktree (mock example)

import iw.core.{Command, CommandHelpers}

object StatusCommand extends Command:
  def run(args: Array[String]): Int =
    println("Status: Working on IWLE-72")
    println("Worktree: /home/mph/.local/share/par/worktrees/fcd8a59c/IWLE-72")
    println("Branch: IWLE-72")
    CommandHelpers.success("All systems operational")

@main def main(args: String*): Unit =
  val exitCode = StatusCommand.run(args.toArray)
  sys.exit(exitCode)
