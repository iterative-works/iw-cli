// PURPOSE: Hello command that demonstrates argument handling
// PURPOSE: Shows how commands can access and process command-line arguments

//> using scala "3.3.1"
//> using file "../core/Command.scala"
//> using file "../core/Config.scala"

import iwcli.core.{Command => CommandTrait}

object HelloCommand extends CommandTrait {
  def name: String = "hello"
  def description: String = "Greet someone (usage: hello <name>)"

  def run(args: List[String]): Int = {
    args match {
      case Nil =>
        CommandTrait.eprintln("Error: Missing name argument")
        CommandTrait.eprintln("Usage: hello <name>")
        1
      case name :: _ =>
        CommandTrait.println(s"Hello, $name!")
        0
    }
  }
}

@main def main(args: String*): Unit = {
  val exitCode = HelloCommand.run(args.toList)
  sys.exit(exitCode)
}
