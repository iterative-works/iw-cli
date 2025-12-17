// PURPOSE: Display a friendly greeting message
// USAGE: iw hello [name]
// ARGS:
//   name: Optional name to greet (defaults to "World")
// EXAMPLE: iw hello
// EXAMPLE: iw hello Michal

//> using scala 3.3.1
//> using dep com.lihaoyi::os-lib:0.9.1

import iw.core.Output

object HelloCommand:
  def main(args: Array[String]): Unit =
    val name = args.headOption.getOrElse("World")
    Output.success(s"Hello, $name!")

    if args.length > 1 then
      Output.info("Note: Only the first argument is used as the name")
