// PURPOSE: Data structures for command metadata and documentation
// PURPOSE: Used by commands to declare their purpose, usage, and arguments

package iw.core

case class ArgSpec(
    name: String,
    description: String,
    required: Boolean = false
)

case class CommandMetadata(
    name: String,
    purpose: String,
    usage: String,
    args: List[ArgSpec] = List.empty,
    examples: List[String] = List.empty
)

object CommandMetadata:
  def parseFromSource(source: String): Option[CommandMetadata] =
    val lines = source.linesIterator.toList

    val purpose = lines
      .find(_.trim.startsWith("// PURPOSE:"))
      .map(_.replaceFirst(".*// PURPOSE:", "").trim)

    val usage = lines
      .find(_.trim.startsWith("// USAGE:"))
      .map(_.replaceFirst(".*// USAGE:", "").trim)

    val argsSection = lines
      .dropWhile(!_.trim.startsWith("// ARGS:"))
      .drop(1)
      .takeWhile(line =>
        line.trim.startsWith("//") && !line.trim.startsWith("// EXAMPLE")
      )
      .map(_.replaceFirst(".*//", "").trim)
      .filter(_.nonEmpty)

    val args = argsSection.map { line =>
      val parts = line.split(":", 2)
      if parts.length == 2 then
        val argName = parts(0).trim
        val argDesc = parts(1).trim
        val required = !argName.startsWith("[") && !argName.startsWith("--")
        ArgSpec(argName, argDesc, required)
      else ArgSpec(line, "", false)
    }

    val examples = lines
      .dropWhile(!_.trim.startsWith("// EXAMPLE:"))
      .filter(_.trim.startsWith("// EXAMPLE:"))
      .map(_.replaceFirst(".*// EXAMPLE:", "").trim)

    for
      p <- purpose
      u <- usage
    yield
      val cmdName = u.split("\\s+").drop(1).headOption.getOrElse("unknown")
      CommandMetadata(cmdName, p, u, args, examples)
