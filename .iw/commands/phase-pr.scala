// PURPOSE: Pushes the phase sub-branch and creates a GitHub/GitLab PR or MR
// PURPOSE: Usage: iw phase-pr --title TITLE [--body BODY] [--batch] [--issue-id ID] [--phase-number N]

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def phasePr(args: String*): Unit =
  val argList = args.toList

  val title = PhaseArgs.namedArg(argList, "--title") match
    case Some(t) => t
    case None =>
      Output.error("Missing required argument: --title")
      Output.error("Usage: iw phase-pr --title TITLE [--body BODY] [--batch] [--issue-id ID] [--phase-number N]")
      sys.exit(1)

  val bodyArg = PhaseArgs.namedArg(argList, "--body")
  val batchMode = PhaseArgs.hasFlag(argList, "--batch")

  val currentBranch = CommandHelpers.exitOnError(
    GitAdapter.getCurrentBranch(os.pwd).left.map(err => s"Failed to get current branch: $err")
  )

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

  val forgeType = remoteOpt.flatMap(r => ForgeType.fromRemote(r).toOption).getOrElse {
    config.trackerType match
      case IssueTrackerType.GitHub => ForgeType.GitHub
      case _                       => ForgeType.GitLab
  }

  val repository = config.repository.getOrElse {
    remoteOpt.flatMap(r => r.repositoryOwnerAndName.toOption).getOrElse {
      Output.error("Cannot determine repository. Set 'tracker.repository' in .iw/config.conf")
      sys.exit(1)
    }
  }

  CommandHelpers.exitOnError(
    GitAdapter.push(currentBranch, os.pwd, setUpstream = true)
      .left.map(err => s"Failed to push branch '$currentBranch': $err")
  )

  val prBody = bodyArg.getOrElse {
    val fileUrlBase = remoteOpt.flatMap(r => FileUrlBuilder.build(r, currentBranch).toOption)
    val fileLinks = fileUrlBase.map(base => s"\n\n### Files\nBrowse changed files: ${base}").getOrElse("")
    s"Phase ${phaseNumber.value} implementation for ${issueId.value}.$fileLinks"
  }

  val prUrl = forgeType match
    case ForgeType.GitHub =>
      CommandHelpers.exitOnError(
        GitHubClient.createPullRequest(repository, currentBranch, featureBranch, title, prBody)
          .left.map { err =>
            Output.error(s"The branch '$currentBranch' was already pushed. You can create the PR manually.")
            s"Failed to create pull request: $err"
          }
      )
    case ForgeType.GitLab =>
      CommandHelpers.exitOnError(
        GitLabClient.createMergeRequest(repository, currentBranch, featureBranch, title, prBody)
          .left.map { err =>
            Output.error(s"The branch '$currentBranch' was already pushed. You can create the MR manually.")
            s"Failed to create merge request: $err"
          }
      )

  val reviewStatePath = os.pwd / "project-management" / "issues" / issueId.value / "review-state.json"

  val merged =
    if batchMode then
      val mergeResult = forgeType match
        case ForgeType.GitHub =>
          ProcessAdapter.run(Seq(forgeType.cliTool, "pr", "merge", "--squash", "--delete-branch", prUrl))
        case ForgeType.GitLab =>
          ProcessAdapter.run(Seq(forgeType.cliTool, "mr", "merge", "--squash", prUrl))

      if mergeResult.exitCode != 0 then
        Output.error(s"Failed to merge PR: ${mergeResult.stderr}")
        Output.error(s"PR was created at $prUrl. Run 'iw phase-advance' after merging manually.")
        sys.exit(1)

      CommandHelpers.exitOnError(
        GitAdapter.checkoutBranch(featureBranch, os.pwd)
          .left.map(err => s"Failed to checkout '$featureBranch': $err")
      )

      CommandHelpers.exitOnError(GitAdapter.fetchAndReset(featureBranch, os.pwd))

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

      true
    else
      if os.exists(reviewStatePath) then
        ReviewStateAdapter.update(reviewStatePath, ReviewStateUpdater.UpdateInput(
          status = Some("awaiting_review"),
          displayText = Some(s"Phase ${phaseNumber.value}: Awaiting Review"),
          displayType = Some("warning"),
          needsAttention = Some(true),
          prUrl = Some(prUrl),
          badges = Some(List(("Review Needed", "warning"))),
          badgesMode = ReviewStateUpdater.ArrayMergeMode.Append,
          actions = Some(List(("view-pr", "View Pull Request", "external-link"))),
          actionsMode = ReviewStateUpdater.ArrayMergeMode.Append
        )) match
          case Left(err) => Output.error(s"Warning: Failed to update review-state: $err")
          case Right(_) => ()

      false

  println(PhaseOutput.PrOutput(
    issueId = issueId.value,
    phaseNumber = phaseNumber.value,
    prUrl = prUrl,
    headBranch = currentBranch,
    baseBranch = featureBranch,
    merged = merged
  ).toJson)
