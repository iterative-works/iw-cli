// PURPOSE: Implementation of 'iw hello' command
// PURPOSE: Demonstrates command arguments handling with a simple greeting

import iw.core.{Command, CommandHelpers}

object HelloCommand extends Command:
  def run(args: Array[String]): Int =
    val name = if args.isEmpty then "World" else args.mkString(" ")
    CommandHelpers.success(s"Hello, $name!")

@main def main(args: String*): Unit =
  val exitCode = HelloCommand.run(args.toArray)
  sys.exit(exitCode)
