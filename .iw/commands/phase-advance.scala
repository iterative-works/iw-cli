// PURPOSE: Verifies a phase PR is merged then advances the feature branch to match remote
// PURPOSE: Usage: iw phase-advance [--issue-id ID] [--phase-number N]

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def phaseAdvance(args: String*): Unit =
  val argList = args.toList

  // Parse optional overrides
  val issueIdArg = argList
    .sliding(2)
    .collectFirst { case "--issue-id" :: value :: Nil => value }

  val phaseNumberArg = argList
    .sliding(2)
    .collectFirst { case "--phase-number" :: value :: Nil => value }

  // Get current branch
  val currentBranch = GitAdapter.getCurrentBranch(os.pwd) match
    case Left(err) =>
      Output.error(s"Failed to get current branch: $err")
      sys.exit(1)
    case Right(branch) => branch

  // Determine if on phase sub-branch or feature branch
  val (featureBranch, phaseBranchName, phaseNumRaw) = currentBranch match
    case PhaseBranch(fb, pn) =>
      // On phase sub-branch: derive feature branch and phase number directly
      (fb, currentBranch, pn)
    case _ =>
      // On feature branch (or other): require --phase-number
      val pn = phaseNumberArg match
        case Some(n) => n
        case None =>
          Output.error(s"Cannot determine phase number from branch '$currentBranch'. Provide --phase-number N.")
          sys.exit(1)
      val parsedPn = PhaseNumber.parse(pn) match
        case Left(err) =>
          Output.error(err)
          sys.exit(1)
        case Right(n) => n

      val phaseBranch = PhaseBranch(currentBranch, parsedPn).branchName
      (currentBranch, phaseBranch, parsedPn.value)

  // Resolve issue ID (from arg or feature branch)
  val issueId = issueIdArg match
    case Some(rawId) =>
      IssueId.parse(rawId) match
        case Left(err) =>
          Output.error(err)
          sys.exit(1)
        case Right(id) => id
    case None =>
      IssueId.fromBranch(featureBranch) match
        case Left(err) =>
          Output.error(s"Cannot determine issue ID from branch '$featureBranch': $err")
          sys.exit(1)
        case Right(id) => id

  // Resolve phase number
  val phaseNumber = phaseNumberArg match
    case Some(raw) =>
      PhaseNumber.parse(raw) match
        case Left(err) =>
          Output.error(err)
          sys.exit(1)
        case Right(pn) => pn
    case None =>
      PhaseNumber.parse(phaseNumRaw) match
        case Left(err) =>
          Output.error(s"Could not parse phase number: $err")
          sys.exit(1)
        case Right(pn) => pn

  // Read config to determine tracker type
  val configPath = os.pwd / Constants.Paths.IwDir / "config.conf"
  val config = ConfigFileRepository.read(configPath) match
    case Some(c) => c
    case None =>
      Output.error("No config found. Run 'iw init' first.")
      sys.exit(1)

  // Verify gh/glab is available based on tracker type
  config.trackerType match
    case IssueTrackerType.GitHub =>
      if !ProcessAdapter.commandExists("gh") then
        Output.error("gh CLI is not installed. Install it from https://cli.github.com/")
        sys.exit(1)
    case IssueTrackerType.GitLab =>
      if !ProcessAdapter.commandExists("glab") then
        Output.error("glab CLI is not installed. Install it from https://gitlab.com/gitlab-org/cli")
        sys.exit(1)
    case other =>
      Output.error(s"Phase advance not supported for $other tracker")
      sys.exit(1)

  // Verify the phase PR/MR is merged
  val isMerged = config.trackerType match
    case IssueTrackerType.GitHub =>
      val result = ProcessAdapter.run(
        Seq("gh", "pr", "list", "--head", phaseBranchName, "--state", "merged", "--json", "url")
      )
      if result.exitCode != 0 then
        Output.error(s"Failed to check PR status: ${result.stderr}")
        sys.exit(1)
      val output = result.stdout.trim
      output != "[]" && output.nonEmpty
    case IssueTrackerType.GitLab =>
      val result = ProcessAdapter.run(
        Seq("glab", "mr", "list", "--head", phaseBranchName, "--state", "merged")
      )
      if result.exitCode != 0 then
        Output.error(s"Failed to check MR status: ${result.stderr}")
        sys.exit(1)
      result.stdout.trim.nonEmpty
    case _ =>
      false

  if !isMerged then
    // Check if PR exists at all (may still be open)
    val prExists = config.trackerType match
      case IssueTrackerType.GitHub =>
        val result = ProcessAdapter.run(
          Seq("gh", "pr", "list", "--head", phaseBranchName, "--state", "open", "--json", "url")
        )
        result.exitCode == 0 && result.stdout.trim != "[]" && result.stdout.trim.nonEmpty
      case _ => false

    if prExists then
      Output.error(s"PR for branch '$phaseBranchName' is still open. Merge it first.")
    else
      Output.error(s"No merged PR found for phase branch '$phaseBranchName'.")
    sys.exit(1)

  // If on phase sub-branch, checkout feature branch first
  if currentBranch == phaseBranchName then
    GitAdapter.checkoutBranch(featureBranch, os.pwd) match
      case Left(err) =>
        Output.error(s"Failed to checkout '$featureBranch': $err")
        sys.exit(1)
      case Right(_) => ()

  // Fetch from remote
  val fetchResult = ProcessAdapter.run(Seq("git", "-C", os.pwd.toString, "fetch", "origin"))
  if fetchResult.exitCode != 0 then
    Output.error(s"Failed to fetch from origin: ${fetchResult.stderr}")
    sys.exit(1)

  // Reset to remote
  val resetResult = ProcessAdapter.run(Seq("git", "-C", os.pwd.toString, "reset", "--hard", s"origin/$featureBranch"))
  if resetResult.exitCode != 0 then
    Output.error(s"Failed to reset to origin/$featureBranch: ${resetResult.stderr}")
    sys.exit(1)

  // Get current HEAD SHA
  val headSha = GitAdapter.getFullHeadSha(os.pwd) match
    case Left(err) =>
      Output.error(s"Failed to get HEAD SHA: $err")
      sys.exit(1)
    case Right(sha) => sha

  // Update review-state: phase_merged (best-effort)
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

  // Output JSON to stdout
  val result = PhaseOutput.AdvanceOutput(
    issueId = issueId.value,
    phaseNumber = phaseNumber.value,
    branch = featureBranch,
    previousBranch = phaseBranchName,
    headSha = headSha
  )
  println(result.toJson)
