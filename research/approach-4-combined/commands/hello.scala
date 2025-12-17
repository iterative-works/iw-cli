// PURPOSE: Greet someone by name
// USAGE: iw hello <name>
// ARGS:
//   <name>: The name of the person to greet
// EXAMPLE: iw hello World
// EXAMPLE: iw hello Alice

//> using scala 3.3.1
//> using file "../core/Output.scala"

import iw.core.Output

object HelloCommand:
  def main(args: Array[String]): Unit =
    args.toList match
      case Nil =>
        Output.error("Missing name argument")
        System.err.println("Usage: iw hello <name>")
        sys.exit(1)
      case name :: _ =>
        println(s"Hello, $name!")
