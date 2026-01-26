// PURPOSE: Doctor check for legacy numeric branches
// PURPOSE: Detects bare numeric branches and warns about migration to TEAM-NNN format

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*
import iw.core.dashboard.{Check, CheckResult}
import scala.util.Try

object LegacyBranchesHookDoctor:
  // Pure function - easily testable in isolation
  def checkLegacyBranches(config: ProjectConfiguration): CheckResult =
    // Get all branches using git branch command
    val branchesResult = Try {
      val result = ProcessAdapter.run(Seq("git", "branch", "--all", "--format=%(refname:short)"))
      if result.exitCode == 0 then
        result.stdout.split("\n").map(_.trim).filter(_.nonEmpty).toList
      else
        List.empty[String]
    }.getOrElse(List.empty[String])

    // Filter to local branches only (exclude remote branches)
    val localBranches = branchesResult.filter(!_.startsWith("origin/"))

    // Pattern for numeric-only branches or numeric with suffix
    val numericBranchPattern = """^([0-9]+)(-.*)?$""".r

    // Find legacy branches
    val legacyBranches = localBranches.filter {
      case numericBranchPattern(_, _) => true
      case _ => false
    }

    if legacyBranches.isEmpty then
      CheckResult.Success("No legacy branches found")
    else
      val branchList = legacyBranches.mkString(", ")
      CheckResult.WarningWithHint(
        s"Found ${legacyBranches.size} legacy numeric branch(es): $branchList",
        "Rename to TEAM-NNN format (e.g., '48' → 'IWCLI-48'). Use: git branch -m <old> <new>"
      )

  // Expose check as immutable value for discovery
  val check: Check = Check("Legacy branches", checkLegacyBranches)
