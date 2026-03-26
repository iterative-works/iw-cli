// PURPOSE: Display version information
// USAGE: iw version [--verbose]
// ARGS:
//   --verbose: Show detailed version info including dependencies
// EXAMPLE: iw version
// EXAMPLE: iw version --verbose

import iw.core.output.Output

val iwVersion = "0.3.6"

@main def version(args: String*): Unit =
  val verbose = args.contains("--verbose")

  if verbose then
    Output.section("iw-cli Version Information")
    Output.keyValue("Version", iwVersion)
    Output.keyValue("OS", System.getProperty("os.name"))
    Output.keyValue("Architecture", System.getProperty("os.arch"))
    Output.keyValue("Java", System.getProperty("java.version"))
  else
    Output.info(s"iw-cli version $iwVersion")
