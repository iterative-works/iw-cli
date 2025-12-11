// PURPOSE: Display version information
// USAGE: iw version [--verbose]
// ARGS:
//   --verbose: Show detailed version info including dependencies
// EXAMPLE: iw version
// EXAMPLE: iw version --verbose

//> using scala 3.3.1
//> using file "../core/Output.scala"

import iw.core.Output

object VersionCommand:
  val version = "0.1.0"
  val scalaVersion = "3.3.1"

  def main(args: Array[String]): Unit =
    val verbose = args.contains("--verbose")

    if verbose then
      Output.section("iw-cli Version Information")
      Output.keyValue("Version", version)
      Output.keyValue("Scala", scalaVersion)
      Output.keyValue("OS", System.getProperty("os.name"))
      Output.keyValue("Architecture", System.getProperty("os.arch"))
      Output.keyValue("Java", System.getProperty("java.version"))
    else
      println(s"iw-cli version $version")
