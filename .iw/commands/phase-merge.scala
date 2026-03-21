// PURPOSE: Polls GitHub CI checks for a PR and auto-merges on success
// PURPOSE: Invoked as `iw phase-merge [--issue-id ID] [--phase-number N]`

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def phaseMerge(args: String*): Unit =
  val argList = args.toList

  val currentBranch = CommandHelpers.exitOnError(GitAdapter.getCurrentBranch(os.pwd))

  val (featureBranch, phaseNumRaw) = currentBranch match
    case PhaseBranch(fb, pn) => (fb, pn)
    case _ =>
      Output.error(s"Not on a phase sub-branch (current branch: '$currentBranch'). Run 'iw phase-start' first.")
      sys.exit(1)

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

  if forgeType == ForgeType.GitLab then
    Output.error("GitLab support coming in a future phase.")
    sys.exit(1)

  val repository = config.repository.getOrElse {
    remoteOpt.flatMap(r => r.extractRepositoryPath.toOption).getOrElse {
      Output.error("Cannot determine repository. Set 'tracker.repository' in .iw/config.conf")
      sys.exit(1)
    }
  }

  val reviewStatePath = os.pwd / "project-management" / "issues" / issueId.value / "review-state.json"

  val prUrl = CommandHelpers.exitOnError(ReviewStateAdapter.readPrUrl(reviewStatePath))

  val expectedPrefix = s"https://github.com/$repository/pull/"
  if !prUrl.startsWith(expectedPrefix) then
    Output.error(s"PR URL does not match repository '$repository'.")
    sys.exit(1)

  val prNumber = CommandHelpers.exitOnError(PhaseMerge.extractPrNumber(prUrl))

  // Update review-state to ci_pending
  if os.exists(reviewStatePath) then
    ReviewStateAdapter.update(reviewStatePath, ReviewStateUpdater.UpdateInput(
      status = Some("ci_pending"),
      displayText = Some(s"Phase ${phaseNumber.value}: Waiting for CI"),
      displayType = Some("progress")
    )) match
      case Left(err) => Output.error(s"Warning: Failed to update review-state: $err")
      case Right(_) => ()

  // Polling loop
  val mergeConfig = PhaseMergeConfig()
  val startTime = System.currentTimeMillis()

  @annotation.tailrec
  def poll(): Unit =
    val elapsed = System.currentTimeMillis() - startTime
    if elapsed > mergeConfig.timeoutMs then
      Output.error(s"Timed out waiting for CI checks after ${mergeConfig.timeoutMs / 1000}s.")
      Output.error(s"PR is at $prUrl. You can merge manually once CI passes.")
      sys.exit(1)

    val checks = GitHubClient.fetchCheckStatuses(prNumber, repository) match
      case Left(err) =>
        Output.error(s"Failed to fetch CI check statuses: $err")
        sys.exit(1)
      case Right(c) => c

    val verdict = PhaseMerge.evaluateChecks(checks)
    verdict match
      case CIVerdict.AllPassed =>
        Output.info("All CI checks passed.")
      case CIVerdict.NoChecksFound =>
        Output.info("No CI checks found — proceeding with merge.")
      case CIVerdict.StillRunning =>
        val pendingNames = checks.filter(_.status == CICheckStatus.Pending).map(_.name).mkString(", ")
        Output.info(s"CI still running (pending: $pendingNames). Waiting ${mergeConfig.pollIntervalMs / 1000}s...")
        Thread.sleep(mergeConfig.pollIntervalMs)
        poll()
      case CIVerdict.SomeFailed(failedChecks) =>
        Output.error("CI checks failed:")
        failedChecks.foreach { check =>
          val urlPart = check.url.fold("")(u => s" — $u")
          Output.error(s"  ${check.name}: ${check.status}$urlPart")
        }
        sys.exit(1)
      case CIVerdict.TimedOut =>
        // Should not reach here — handled by elapsed check above
        Output.error("Timed out waiting for CI.")
        sys.exit(1)

  poll()

  // Merge the PR
  Output.info(s"Merging PR: $prUrl")
  val mergeResult = ProcessAdapter.run(Seq("gh") ++ GitHubClient.buildMergePrWithDeleteCommand(prUrl).toSeq)
  if mergeResult.exitCode != 0 then
    Output.error(s"Failed to merge PR: ${mergeResult.stderr}")
    sys.exit(1)

  // Advance feature branch
  CommandHelpers.exitOnError(GitAdapter.checkoutBranch(featureBranch, os.pwd))
  CommandHelpers.exitOnError(GitAdapter.fetchAndReset(featureBranch, os.pwd))

  // Update review-state to phase_merged
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

  println(PhaseOutput.MergeOutput(
    issueId = issueId.value,
    phaseNumber = phaseNumber.value,
    prUrl = prUrl,
    featureBranch = featureBranch
  ).toJson)
