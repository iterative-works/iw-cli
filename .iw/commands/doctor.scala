// PURPOSE: Check system dependencies and configuration
// USAGE: iw doctor
// ARGS:
//   None
// EXAMPLE: iw doctor

//> using scala 3.3.1
//> using file "../core/Output.scala"
//> using file "../core/DoctorChecks.scala"
//> using file "../core/Config.scala"
//> using file "../core/ConfigRepository.scala"
//> using file "../core/Git.scala"

import iw.core.*
import java.nio.file.{Path, Paths}

val ConfigPath = Paths.get(".iw/config.conf")

// Register base doctor checks at file load time (val forces execution)
val _gitCheck = DoctorChecks.register("Git repository") { _ =>
  val currentDir = Paths.get(System.getProperty("user.dir"))
  if GitAdapter.isGitRepository(currentDir) then
    CheckResult.Success("Found")
  else
    CheckResult.Error("Not found", Some("Initialize git repository: git init"))
}

val _configCheck = DoctorChecks.register("Configuration") { _ =>
  ConfigFileRepository.read(ConfigPath) match
    case Some(_) =>
      CheckResult.Success(".iw/config.conf valid")
    case None =>
      CheckResult.Error("Missing or invalid", Some("Run: iw init"))
}

// Initialize hook objects discovered by bootstrap script
// IW_HOOK_CLASSES env var contains comma-separated list of hook class names
private def initializeHooks(): Unit =
  val hookClasses = sys.env.getOrElse("IW_HOOK_CLASSES", "")
  if hookClasses.nonEmpty then
    hookClasses.split(",").foreach { className =>
      try Class.forName(s"$className$$") // Scala object class names end with $
      catch case _: ClassNotFoundException => () // Hook not present, that's OK
    }

@main def doctor(args: String*): Unit =
  // Initialize discovered hooks (their registration runs when objects load)
  initializeHooks()

  // Load configuration (use default if not available for hook checks)
  val config = ConfigFileRepository.read(ConfigPath).getOrElse(
    ProjectConfiguration(IssueTrackerType.Linear, "UNKNOWN", "unknown")
  )

  System.out.println("Environment Check")
  System.out.println()

  // Run all registered checks (base + hooks registered via top-level code)
  val results = DoctorChecks.runAll(config)

  var errorCount = 0
  var warningCount = 0

  // Display results
  results.foreach { case (name, result) =>
    result match
      case CheckResult.Success(message) =>
        System.out.println(f"  ✓ $name%-20s $message")

      case CheckResult.Warning(message, hint) =>
        warningCount += 1
        System.out.println(f"  ⚠ $name%-20s $message")
        hint.foreach(h => System.out.println(s"    → $h"))

      case CheckResult.Error(message, hint) =>
        errorCount += 1
        System.out.println(f"  ✗ $name%-20s $message")
        hint.foreach(h => System.out.println(s"    → $h"))

      case CheckResult.Skip(reason) =>
        System.out.println(f"  - $name%-20s Skipped ($reason)")
  }

  System.out.println()

  // Summary
  if errorCount == 0 && warningCount == 0 then
    System.out.println("All checks passed")
    sys.exit(0)
  else if errorCount > 0 then
    val plural = if errorCount == 1 then "check" else "checks"
    System.out.println(s"$errorCount $plural failed")
    sys.exit(1)
  else
    val plural = if warningCount == 1 then "warning" else "warnings"
    System.out.println(s"$warningCount $plural")
    sys.exit(0)
