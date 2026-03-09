// PURPOSE: Verifies a phase PR is merged then advances the feature branch to match remote
// PURPOSE: Usage: iw phase-advance [--issue-id ID] [--phase-number N]

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def phaseAdvance(args: String*): Unit =
  val argList = args.toList

  val currentBranch = CommandHelpers.exitOnError(GitAdapter.getCurrentBranch(os.pwd))

  val (featureBranch, phaseBranchName, phaseNumRaw) = currentBranch match
    case PhaseBranch(fb, pn) =>
      (fb, currentBranch, pn)
    case _ =>
      val pn = PhaseArgs.namedArg(argList, "--phase-number") match
        case Some(n) => n
        case None =>
          Output.error(s"Cannot determine phase number from branch '$currentBranch'. Provide --phase-number N.")
          sys.exit(1)
      val parsedPn = CommandHelpers.exitOnError(PhaseNumber.parse(pn))
      val phaseBranch = PhaseBranch(currentBranch, parsedPn).branchName
      (currentBranch, phaseBranch, parsedPn.value)

  val issueId = CommandHelpers.exitOnError(
    PhaseArgs.resolveIssueId(PhaseArgs.namedArg(argList, "--issue-id"), featureBranch)
  )

  val phaseNumber = CommandHelpers.exitOnError(
    PhaseArgs.resolvePhaseNumber(PhaseArgs.namedArg(argList, "--phase-number"), phaseNumRaw)
  )

  val configPath = os.pwd / Constants.Paths.IwDir / "config.conf"
  val config = CommandHelpers.exitOnNone(
    ConfigFileRepository.read(configPath),
    "No config found. Run 'iw init' first."
  )

  val remoteOpt = GitAdapter.getRemoteUrl(os.pwd)

  val forgeType = ForgeType.resolve(remoteOpt, config.trackerType)

  val cliTool = forgeType.cliTool

  if !ProcessAdapter.commandExists(cliTool) then
    Output.error(s"$cliTool CLI is not installed. Install it from ${forgeType.installUrl}")
    sys.exit(1)

  val isMerged = forgeType match
    case ForgeType.GitHub =>
      val result = ProcessAdapter.run(
        Seq(forgeType.cliTool, "pr", "list", "--head", phaseBranchName, "--state", "merged", "--json", "url")
      )
      if result.exitCode != 0 then
        Output.error(s"Failed to check PR status: ${result.stderr}")
        sys.exit(1)
      val out = result.stdout.trim
      out != "[]" && out.nonEmpty
    case ForgeType.GitLab =>
      val result = ProcessAdapter.run(
        Seq(forgeType.cliTool, "mr", "list", "--head", phaseBranchName, "--state", "merged")
      )
      if result.exitCode != 0 then
        Output.error(s"Failed to check MR status: ${result.stderr}")
        sys.exit(1)
      result.stdout.trim.nonEmpty

  if !isMerged then
    val prExists = forgeType match
      case ForgeType.GitHub =>
        val result = ProcessAdapter.run(
          Seq(forgeType.cliTool, "pr", "list", "--head", phaseBranchName, "--state", "open", "--json", "url")
        )
        result.exitCode == 0 && result.stdout.trim != "[]" && result.stdout.trim.nonEmpty
      case _ => false

    if prExists then
      Output.error(s"PR for branch '$phaseBranchName' is still open. Merge it first.")
    else
      Output.error(s"No merged PR found for phase branch '$phaseBranchName'.")
    sys.exit(1)

  if currentBranch == phaseBranchName then
    CommandHelpers.exitOnError(GitAdapter.checkoutBranch(featureBranch, os.pwd))

  CommandHelpers.exitOnError(GitAdapter.fetchAndReset(featureBranch, os.pwd))

  val headSha = CommandHelpers.exitOnError(GitAdapter.getFullHeadSha(os.pwd))

  val reviewStatePath = os.pwd / "project-management" / "issues" / issueId.value / "review-state.json"
  if os.exists(reviewStatePath) then
    ReviewStateAdapter.update(reviewStatePath, ReviewStateUpdater.UpdateInput(
      status = Some("phase_merged"),
      displayText = Some(s"Phase ${phaseNumber.value}: Merged"),
      displayType = Some("success"),
      badges = Some(List(("Complete", "success"))),
      badgesMode = ReviewStateUpdater.ArrayMergeMode.Append
    )) match
      case Left(err) => Output.error(s"Warning: Failed to update review-state: $err")
      case Right(_) => ()

  println(PhaseOutput.AdvanceOutput(
    issueId = issueId.value,
    phaseNumber = phaseNumber.value,
    branch = featureBranch,
    previousBranch = phaseBranchName,
    headSha = headSha
  ).toJson)
