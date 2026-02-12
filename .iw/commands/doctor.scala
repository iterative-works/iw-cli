// PURPOSE: Check system dependencies and configuration
// USAGE: iw doctor
// ARGS:
//   None
// EXAMPLE: iw doctor

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

val ConfigPath = os.pwd / Constants.Paths.IwDir / "config.conf"

// Base checks defined as immutable values
val baseChecks: List[Check] = List(
  Check("Git repository", { _ =>
    val currentDir = os.Path(System.getProperty(Constants.SystemProps.UserDir))
    if GitAdapter.isGitRepository(currentDir) then
      CheckResult.Success("Found")
    else
      CheckResult.Error("Not found", "Initialize git repository: git init")
  }),
  Check("Configuration", { _ =>
    ConfigFileRepository.read(ConfigPath) match
      case Some(_) =>
        CheckResult.Success(s"${Constants.Paths.ConfigFile} valid")
      case None =>
        CheckResult.Error("Missing or invalid", "Run: iw init")
  })
)

// Collect hook checks from discovered hook classes via reflection
private def collectHookChecks(): List[Check] =
  val hookClasses = sys.env.getOrElse(Constants.EnvVars.IwHookClasses, "")
  if hookClasses.isEmpty then Nil
  else hookClasses.split(",").toList.flatMap { className =>
    try
      val clazz = Class.forName(s"$className$$") // Scala object class names end with $
      val instance = clazz.getField(Constants.ScalaReflection.ModuleField).get(null)

      // Collect all fields of type Check from the hook
      clazz.getDeclaredMethods
        .filter(_.getReturnType == classOf[Check])
        .filter(_.getParameterCount == 0)
        .flatMap { method =>
          try Some(method.invoke(instance).asInstanceOf[Check])
          catch case _: Exception => None
        }
        .toList
    catch
      case _: Exception => Nil // Hook not present or error accessing checks
  }

@main def doctor(args: String*): Unit =
  // Load configuration (use default if not available for hook checks)
  val config = ConfigFileRepository.read(ConfigPath).getOrElse(
    ProjectConfiguration.create(IssueTrackerType.Linear, "UNKNOWN", "unknown")
  )

  // Parse flags
  val fixMode = args.contains("--fix")
  val filterCategory =
    if args.contains("--quality") || fixMode then Some("Quality")
    else if args.contains("--env") then Some("Environment")
    else None

  System.out.println("Environment Check")
  System.out.println()

  // Collect all checks: base + hooks (immutable composition)
  val allChecks = baseChecks ++ collectHookChecks()

  // Filter checks by category if requested
  val checksToRun = DoctorChecks.filterByCategory(allChecks, filterCategory)

  // Run all checks
  val results = DoctorChecks.runAll(checksToRun, config)

  // Group results by category
  val groupedResults = results.groupBy(_._3)

  var errorCount = 0
  var warningCount = 0

  // Display results grouped by category
  val categoryOrder = List("Environment", "Quality")
  val categoryHeaders = Map(
    "Environment" -> "  === Environment ===",
    "Quality" -> "  === Project Quality Gates ==="
  )

  categoryOrder.foreach { category =>
    groupedResults.get(category).foreach { categoryResults =>
      if categoryResults.nonEmpty then
        if filterCategory.isEmpty then
          System.out.println()
          System.out.println(categoryHeaders(category))
          System.out.println()

        categoryResults.foreach { case (name, result, _) =>
          result match
            case CheckResult.Success(message) =>
              System.out.println(f"  ✓ $name%-20s $message")

            case CheckResult.Warning(message) =>
              warningCount += 1
              System.out.println(f"  ⚠ $name%-20s $message")

            case CheckResult.WarningWithHint(message, hintText) =>
              warningCount += 1
              System.out.println(f"  ⚠ $name%-20s $message")
              System.out.println(s"    → $hintText")

            case CheckResult.Error(message, hintText) =>
              errorCount += 1
              System.out.println(f"  ✗ $name%-20s $message")
              System.out.println(s"    → $hintText")

            case CheckResult.Skip(reason) =>
              System.out.println(f"  - $name%-20s Skipped ($reason)")
        }
    }
  }

  System.out.println()

  // Fix mode: launch Claude Code with remediation prompt
  if fixMode then
    if errorCount == 0 then
      System.out.println("All quality gate checks pass. Nothing to fix.")
      sys.exit(0)
    else
      // Collect failed check names
      val failedChecks = results.collect {
        case (name, CheckResult.Error(_, _), _) => name
      }

      // Detect build system
      val buildSystem = BuildSystem.detect()

      // Detect CI platform from tracker type
      val ciPlatform = config.tracker.trackerType match
        case IssueTrackerType.GitHub => "GitHub Actions"
        case IssueTrackerType.GitLab => "GitLab CI"
        case _ => "Unknown"

      // Generate remediation prompt
      val prompt = FixPrompt.generate(failedChecks, buildSystem, ciPlatform)

      // Launch Claude Code
      val exitCode = ProcessAdapter.runStreaming(
        Seq("claude", "-p", prompt)
      )
      sys.exit(exitCode)

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
