// PURPOSE: Polls GitHub CI checks for a PR and auto-merges on success
// PURPOSE: Recovers from CI failures by invoking an agent up to a configurable number of times

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def phaseMerge(args: String*): Unit =
  val argList = args.toList

  val timeoutStr = PhaseArgs.namedArg(argList, "--timeout").getOrElse("30m")
  val pollIntervalStr = PhaseArgs.namedArg(argList, "--poll-interval").getOrElse("30s")
  val maxRetriesStr = PhaseArgs.namedArg(argList, "--max-retries").getOrElse("2")

  val timeoutMs = PhaseMerge.parseDuration(timeoutStr) match
    case Left(err) =>
      Output.error(s"Invalid --timeout: $err")
      sys.exit(1)
    case Right(ms) => ms

  val pollIntervalMs = PhaseMerge.parseDuration(pollIntervalStr) match
    case Left(err) =>
      Output.error(s"Invalid --poll-interval: $err")
      sys.exit(1)
    case Right(ms) => ms

  val maxRetries = maxRetriesStr.toIntOption match
    case None =>
      Output.error(s"Invalid --max-retries: '$maxRetriesStr' is not a valid integer")
      sys.exit(1)
    case Some(n) if n < 0 =>
      Output.error(s"Invalid --max-retries: value must not be negative")
      sys.exit(1)
    case Some(n) => n

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

  def tryUpdateState(input: ReviewStateUpdater.UpdateInput): Unit =
    if os.exists(reviewStatePath) then
      ReviewStateAdapter.update(reviewStatePath, input) match
        case Left(err) => Output.error(s"Warning: Failed to update review-state: $err")
        case Right(_)  => ()

  val prUrl = CommandHelpers.exitOnError(ReviewStateAdapter.readPrUrl(reviewStatePath))

  val expectedPrefix = s"https://github.com/$repository/pull/"
  if !prUrl.startsWith(expectedPrefix) then
    Output.error(s"PR URL does not match repository '$repository'.")
    sys.exit(1)

  val prNumber = CommandHelpers.exitOnError(PhaseMerge.extractPrNumber(prUrl))

  // Update review-state to ci_pending
  tryUpdateState(ReviewStateUpdater.UpdateInput(
    status = Some("ci_pending"),
    displayText = Some(s"Phase ${phaseNumber.value}: Waiting for CI"),
    displayType = Some("progress")
  ))

  // Polling and retry loop
  val mergeConfig = PhaseMergeConfig(timeoutMs = timeoutMs, pollIntervalMs = pollIntervalMs, maxRetries = maxRetries)
  val startTime = System.currentTimeMillis()

  @annotation.tailrec
  def poll(): CIVerdict =
    val elapsed = System.currentTimeMillis() - startTime
    if elapsed > mergeConfig.timeoutMs then
      tryUpdateState(ReviewStateUpdater.UpdateInput(
        activity = Some("waiting")
      ))
      Output.error(s"Timed out waiting for CI checks after ${PhaseMerge.formatDuration(mergeConfig.timeoutMs)}.")
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
        CIVerdict.AllPassed
      case CIVerdict.NoChecksFound =>
        Output.info("No CI checks found — proceeding with merge.")
        CIVerdict.NoChecksFound
      case CIVerdict.StillRunning =>
        val pendingNames = checks.filter(_.status == CICheckStatus.Pending).map(_.name).mkString(", ")
        Output.info(s"CI still running (pending: $pendingNames). Waiting ${PhaseMerge.formatDuration(mergeConfig.pollIntervalMs)}...")
        Thread.sleep(mergeConfig.pollIntervalMs)
        poll()
      case failed @ CIVerdict.SomeFailed(failedChecks) =>
        Output.error("CI checks failed:")
        failedChecks.foreach { check =>
          val urlPart = check.url.fold("")(u => s" — $u")
          Output.error(s"  ${check.name}: ${check.status}$urlPart")
        }
        failed
      case CIVerdict.TimedOut =>
        // Should not reach here — handled by elapsed check above
        Output.error("Timed out waiting for CI.")
        sys.exit(1)

  def invokeRecoveryAgent(attempt: Int, failedChecks: List[CICheckResult]): Unit =
    val attemptDisplay = s"${attempt + 1}/${mergeConfig.maxRetries}"
    Output.info(s"Invoking recovery agent (attempt $attemptDisplay)...")
    tryUpdateState(ReviewStateUpdater.UpdateInput(
      status = Some("ci_fixing"),
      displayText = Some(s"Phase ${phaseNumber.value}: CI Fixing (attempt $attemptDisplay)")
    ))
    val basePrompt = PhaseMerge.buildRecoveryPrompt(failedChecks)
    val fullPrompt =
      s"You are fixing CI failures for PR $prUrl (branch $currentBranch).\n$basePrompt\n" +
      "Fix the issues, commit your changes, and push to the branch."
    ProcessAdapter.runInteractive(
      Seq("claude", "--dangerously-skip-permissions", "-p", fullPrompt)
    )
    tryUpdateState(ReviewStateUpdater.UpdateInput(
      status = Some("ci_pending"),
      displayText = Some(s"Phase ${phaseNumber.value}: Waiting for CI")
    ))

  @annotation.tailrec
  def retryLoop(attempt: Int): Unit =
    poll() match
      case CIVerdict.SomeFailed(failedChecks) =>
        if PhaseMerge.shouldRetry(attempt, mergeConfig) then
          invokeRecoveryAgent(attempt, failedChecks)
          retryLoop(attempt + 1)
        else
          Output.error(s"CI checks still failing after ${mergeConfig.maxRetries} recovery attempt(s). Giving up.")
          Output.error(s"PR is at $prUrl. Fix the failures manually.")
          tryUpdateState(ReviewStateUpdater.UpdateInput(
            activity = Some("waiting")
          ))
          sys.exit(1)
      case _ => ()

  retryLoop(0)

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
  tryUpdateState(ReviewStateUpdater.UpdateInput(
    status = Some("phase_merged"),
    displayText = Some(s"Phase ${phaseNumber.value}: Merged"),
    displayType = Some("success"),
    badges = Some(List(("Complete", "success"))),
    badgesMode = ReviewStateUpdater.ArrayMergeMode.Append
  ))

  println(PhaseOutput.MergeOutput(
    issueId = issueId.value,
    phaseNumber = phaseNumber.value,
    prUrl = prUrl,
    featureBranch = featureBranch
  ).toJson)
