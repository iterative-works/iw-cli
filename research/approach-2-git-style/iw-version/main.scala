// PURPOSE: Implementation of 'iw version' command
// PURPOSE: Displays version information for the iw tool

import iw.core.{Command, CommandHelpers}

object VersionCommand extends Command:
  def run(args: Array[String]): Int =
    CommandHelpers.success("iw version 0.1.0 (prototype)")

@main def main(args: String*): Unit =
  val exitCode = VersionCommand.run(args.toArray)
  sys.exit(exitCode)
