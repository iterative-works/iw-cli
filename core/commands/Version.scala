// PURPOSE: Version command logic: print iw-cli version (compact or verbose with system info)
// PURPOSE: System info passed in by the shim so the body is testable in-VM

package iw.core.commands

object Version:
  final case class Info(
      version: String,
      osName: String,
      osArch: String,
      javaVersion: String
  )

  def run(args: Seq[String], env: CommandEnv, info: Info): CommandResult =
    val verbose = args.contains("--verbose")
    if verbose then
      env.console.out("\n=== iw-cli Version Information ===")
      env.console.out(f"Version              ${info.version}")
      env.console.out(f"OS                   ${info.osName}")
      env.console.out(f"Architecture         ${info.osArch}")
      env.console.out(f"Java                 ${info.javaVersion}")
    else env.console.out(s"iw-cli version ${info.version}")
    CommandResult.ok
