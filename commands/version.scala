// PURPOSE: Display version information
// USAGE: iw version [--verbose]

import iw.core.commands.{LiveCommandEnv, Version}

val iwVersion: String =
  val versionFile =
    os.Path(sys.env.getOrElse("IW_COMMANDS_DIR", ".")) / os.up / "VERSION"
  if os.exists(versionFile) then os.read(versionFile).trim
  else "0.0.0"

@main def version(args: String*): Unit =
  val info = Version.Info(
    version = iwVersion,
    osName = System.getProperty("os.name"),
    osArch = System.getProperty("os.arch"),
    javaVersion = System.getProperty("java.version")
  )
  sys.exit(Version.run(args, LiveCommandEnv.default, info).exitCode)
