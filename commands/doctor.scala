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
  Check(
    "Git repository",
    { _ =>
      val currentDir =
        os.Path(System.getProperty(Constants.SystemProps.UserDir))
      if GitAdapter.isGitRepository(currentDir) then
        CheckResult.Success("Found")
      else CheckResult.Error("Not found", "Initialize git repository: git init")
    }
  ),
  Check(
    "Configuration",
    { _ =>
      ConfigFileRepository.read(ConfigPath) match
        case Some(_) =>
          CheckResult.Success(s"${Constants.Paths.ConfigFile} valid")
        case None =>
          CheckResult.Error("Missing or invalid", "Run: iw init")
    }
  )
)

@main def doctor(args: String*): Unit =
  val config = ConfigFileRepository
    .read(ConfigPath)
    .getOrElse(
      ProjectConfiguration.create(IssueTrackerType.Linear, "UNKNOWN", "unknown")
    )

  val flags = DoctorCliFlags.parse(args)

  val allChecks = baseChecks ++ HookDiscovery.collectValues[Check]
  val checksToRun =
    DoctorChecks.filterByCategory(allChecks, flags.filterCategory)
  val results = DoctorChecks.runAll(checksToRun, config)

  val rendered = DoctorOutput.render(
    results,
    showHeaders = flags.filterCategory.isEmpty
  )
  rendered.lines.foreach(System.out.println)

  if flags.fixMode then runFixMode(results, config)
  else sys.exit(rendered.exitCode)

def runFixMode(
    results: List[(String, CheckResult, String)],
    config: ProjectConfiguration
): Unit =
  val errorCount = results.count(_._2.isInstanceOf[CheckResult.Error])
  if errorCount == 0 then
    System.out.println("All quality gate checks pass. Nothing to fix.")
    sys.exit(0)

  val fixActions = HookDiscovery.collectValues[FixAction]
  if fixActions.isEmpty then
    Output.warning(
      "No fix provider installed. Install a plugin that provides a FixAction hook."
    )
    sys.exit(1)

  val failedChecks = results.collect {
    case (name, CheckResult.Error(_, _), _) => name
  }
  val buildSystem = BuildSystem.detect()
  val ciPlatform = config.tracker.trackerType match
    case IssueTrackerType.GitHub => "GitHub Actions"
    case IssueTrackerType.GitLab => "GitLab CI"
    case _                       => "Unknown"

  val ctx = DoctorFixContext(failedChecks, buildSystem, ciPlatform, config)
  sys.exit(fixActions.head.fix(ctx))
