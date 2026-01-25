// PURPOSE: Create a new issue in the configured issue tracker
// PURPOSE: Supports both GitHub and Linear trackers with title and optional description

import iw.core.*

@main def issueCreate(args: String*): Unit =
  // Handle --help flag
  if args.contains("--help") || args.contains("-h") then
    showHelp()
    sys.exit(0)

  // Placeholder for actual implementation (Phase 2+)
  showHelp()
  sys.exit(1)

private def showHelp(): Unit =
  println("Create a new issue in the configured tracker")
  println()
  println("Usage:")
  println("  iw issue create --title \"Issue title\" [--description \"Details\"]")
  println()
  println("Arguments:")
  println("  --title        Issue title (required)")
  println("  --description  Detailed description (optional)")
  println()
  println("Examples:")
  println("  iw issue create --title \"Bug in start command\"")
  println("  iw issue create --title \"Feature request\" --description \"Would be nice to have X\"")
