// PURPOSE: Run project tests (unit tests and E2E tests)
// USAGE: iw test [unit|e2e]
// ARGS:
//   [type]: Optional test type - 'unit' for Scala tests, 'e2e' for BATS tests
//           If not provided, runs both
// EXAMPLE: iw test
// EXAMPLE: iw test unit
// EXAMPLE: iw test e2e

import iw.core.*

@main def test(args: String*): Unit =
  val testType = args.headOption.getOrElse("all")

  testType match
    case "--help" | "-h" =>
      showUsage()
      sys.exit(0)
    case "unit" =>
      val result = runUnitTests()
      sys.exit(if result then 0 else 1)
    case "e2e" =>
      val result = runE2ETests()
      sys.exit(if result then 0 else 1)
    case "all" =>
      val unitResult = runUnitTests()
      val e2eResult = runE2ETests()
      sys.exit(if unitResult && e2eResult then 0 else 1)
    case other =>
      Output.error(s"Unknown test type: $other")
      showUsage()
      sys.exit(1)

def showUsage(): Unit =
  System.out.println("Usage: iw test [unit|e2e]")
  System.out.println()
  System.out.println("Arguments:")
  System.out.println("  unit    Run Scala unit tests only")
  System.out.println("  e2e     Run BATS E2E tests only")
  System.out.println("  (none)  Run all tests")
  System.out.println()
  System.out.println("Examples:")
  System.out.println("  iw test          # Run all tests")
  System.out.println("  iw test unit     # Run only unit tests")
  System.out.println("  iw test e2e      # Run only E2E tests")

def runUnitTests(): Boolean =
  val projectDir = os.Path(System.getProperty(Constants.SystemProps.UserDir))
  val iwDir = projectDir / Constants.Paths.IwDir
  val testDir = iwDir / "core" / "test"
  val coreDir = iwDir / "core"

  if !os.exists(testDir) then
    Output.info("No unit tests found (missing .iw/core/test/)")
    return true

  val testFiles = os.list(testDir).filter(_.ext == "scala")
  if testFiles.isEmpty then
    Output.info("No unit test files found")
    return true

  Output.section("Running Unit Tests")

  // Pass directories to scala-cli so it properly recognizes test vs main sources
  val command = Seq("scala-cli", "test", testDir.toString, coreDir.toString)

  val result = ProcessAdapter.run(command, maxOutputBytes = 10 * 1024 * 1024)

  // Print output
  if result.stdout.nonEmpty then
    System.out.println(result.stdout)
  if result.stderr.nonEmpty then
    System.err.println(result.stderr)

  result.exitCode == 0

def runE2ETests(): Boolean =
  val projectDir = os.Path(System.getProperty(Constants.SystemProps.UserDir))
  val testDir = projectDir / Constants.Paths.IwDir / "test"

  if !os.exists(testDir) then
    Output.info("No E2E tests found (missing .iw/test/)")
    return true

  val testFiles = os.list(testDir).filter(_.ext == "bats")
  if testFiles.isEmpty then
    Output.info("No BATS test files found")
    return true

  Output.section("Running E2E Tests")

  val command = Seq("bats", testDir.toString)

  val result = ProcessAdapter.run(command, maxOutputBytes = 10 * 1024 * 1024)

  // Print output
  if result.stdout.nonEmpty then
    System.out.println(result.stdout)
  if result.stderr.nonEmpty then
    System.err.println(result.stderr)

  result.exitCode == 0
