// PURPOSE: Status command that demonstrates adding new commands
// PURPOSE: Shows that commands can be added without modifying any existing code

//> using scala "3.3.1"
//> using file "../core/Command.scala"
//> using file "../core/Config.scala"

import iwcli.core.{Command => CommandTrait, Config}

object StatusCommand extends CommandTrait {
  def name: String = "status"
  def description: String = "Show project status"

  def run(args: List[String]): Int = {
    val config = Config.load()
    CommandTrait.println("Project status:")
    CommandTrait.println(
      s"  Tracker: ${config.tracker.getOrElse("not configured")}"
    )
    CommandTrait.println(
      s"  Project: ${config.projectName.getOrElse("not configured")}"
    )
    0
  }
}

@main def main(args: String*): Unit = {
  val exitCode = StatusCommand.run(args.toList)
  sys.exit(exitCode)
}
