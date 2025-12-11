// PURPOSE: Version command that displays tool version information
// PURPOSE: Demonstrates simplest possible command implementation

//> using scala "3.3.1"
//> using file "../core/Command.scala"
//> using file "../core/Config.scala"

import iwcli.core.{Command => CommandTrait}

object VersionCommand extends CommandTrait {
  def name: String = "version"
  def description: String = "Show version information"

  def run(args: List[String]): Int = {
    CommandTrait.println("iw-cli version 0.1.0-SNAPSHOT")
    0
  }
}

@main def main(args: String*): Unit = {
  val exitCode = VersionCommand.run(args.toList)
  sys.exit(exitCode)
}
