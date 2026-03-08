// PURPOSE: Pushes the phase sub-branch and creates a GitHub/GitLab PR or MR
// PURPOSE: Usage: iw phase-pr --title TITLE [--body BODY] [--batch] [--issue-id ID] [--phase-number N]

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def phasePr(args: String*): Unit =
  val argList = args.toList

  // Parse --title (required)
  val titleArg = argList
    .sliding(2)
    .collectFirst { case "--title" :: value :: Nil => value }

  // Parse --body (optional)
  val bodyArg = argList
    .sliding(2)
    .collectFirst { case "--body" :: value :: Nil => value }

  // Parse flags and optional overrides
  val batchMode = argList.contains("--batch")

  val issueIdArg = argList
    .sliding(2)
    .collectFirst { case "--issue-id" :: value :: Nil => value }

  val phaseNumberArg = argList
    .sliding(2)
    .collectFirst { case "--phase-number" :: value :: Nil => value }

  // Require --title
  val title = titleArg match
    case Some(t) => t
    case None =>
      Output.error("Missing required argument: --title")
      Output.error("Usage: iw phase-pr --title TITLE [--body BODY] [--batch] [--issue-id ID] [--phase-number N]")
      sys.exit(1)

  // Get current branch
  val currentBranch = GitAdapter.getCurrentBranch(os.pwd) match
    case Left(err) =>
      Output.error(s"Failed to get current branch: $err")
      sys.exit(1)
    case Right(branch) => branch

  // Verify we ARE on a phase sub-branch
  val (featureBranch, phaseNum) = currentBranch match
    case PhaseBranch(fb, pn) => (fb, pn)
    case _ =>
      Output.error(s"Not on a phase sub-branch (current branch: '$currentBranch'). Run 'iw phase-start' first.")
      sys.exit(1)

  // Resolve issue ID
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
      PhaseNumber.parse(phaseNum) match
        case Left(err) =>
          Output.error(s"Could not parse phase number from branch: $err")
          sys.exit(1)
        case Right(pn) => pn

  // Read config
  val configPath = os.pwd / Constants.Paths.IwDir / "config.conf"
  val config = ConfigFileRepository.read(configPath) match
    case Some(c) => c
    case None =>
      Output.error("No config found. Run 'iw init' first.")
      sys.exit(1)

  // Get remote URL for file URL generation
  val remoteOpt = GitAdapter.getRemoteUrl(os.pwd)

  // Determine repository identifier
  val repository = config.repository.getOrElse {
    remoteOpt.flatMap(r => r.repositoryOwnerAndName.toOption).getOrElse {
      Output.error("Cannot determine repository. Set 'tracker.repository' in .iw/config.conf")
      sys.exit(1)
    }
  }

  // Push the phase sub-branch
  GitAdapter.push(currentBranch, os.pwd, setUpstream = true) match
    case Left(err) =>
      Output.error(s"Failed to push branch '$currentBranch': $err")
      sys.exit(1)
    case Right(_) => ()

  // Build default PR body if not provided
  val prBody = bodyArg.getOrElse {
    val fileUrlBase = remoteOpt.flatMap(r => FileUrlBuilder.build(r, currentBranch).toOption)
    val fileLinks = fileUrlBase.map { base =>
      s"\n\n### Files\nBrowse changed files: ${base}"
    }.getOrElse("")
    s"Phase ${phaseNumber.value} implementation for ${issueId.value}.$fileLinks"
  }

  // Create PR/MR based on tracker type
  val prUrl = config.trackerType match
    case IssueTrackerType.GitHub =>
      GitHubClient.createPullRequest(repository, currentBranch, featureBranch, title, prBody) match
        case Left(err) =>
          Output.error(s"Failed to create pull request: $err")
          Output.error(s"The branch '$currentBranch' was already pushed. You can create the PR manually.")
          sys.exit(1)
        case Right(url) => url
    case IssueTrackerType.GitLab =>
      GitLabClient.createMergeRequest(repository, currentBranch, featureBranch, title, prBody) match
        case Left(err) =>
          Output.error(s"Failed to create merge request: $err")
          Output.error(s"The branch '$currentBranch' was already pushed. You can create the MR manually.")
          sys.exit(1)
        case Right(url) => url
    case other =>
      Output.error(s"PR operations not supported for $other tracker")
      sys.exit(1)

  // --batch: squash-merge and advance; otherwise just update review-state
  val merged =
    if batchMode then
      // Squash-merge the PR
      val mergeResult = config.trackerType match
        case IssueTrackerType.GitHub =>
          ProcessAdapter.run(Seq("gh", "pr", "merge", "--squash", "--delete-branch", prUrl))
        case IssueTrackerType.GitLab =>
          ProcessAdapter.run(Seq("glab", "mr", "merge", "--squash", prUrl))
        case _ =>
          Output.error("Batch mode not supported for this tracker type")
          sys.exit(1)

      if mergeResult.exitCode != 0 then
        Output.error(s"Failed to merge PR: ${mergeResult.stderr}")
        Output.error(s"PR was created at $prUrl. Run 'iw phase-advance' after merging manually.")
        sys.exit(1)

      // Checkout feature branch
      GitAdapter.checkoutBranch(featureBranch, os.pwd) match
        case Left(err) =>
          Output.error(s"Failed to checkout '$featureBranch': $err")
          sys.exit(1)
        case Right(_) => ()

      // Fetch + reset to remote
      val fetchResult = ProcessAdapter.run(Seq("git", "-C", os.pwd.toString, "fetch", "origin"))
      if fetchResult.exitCode != 0 then
        Output.error(s"Failed to fetch from origin: ${fetchResult.stderr}")
        sys.exit(1)

      val resetResult = ProcessAdapter.run(Seq("git", "-C", os.pwd.toString, "reset", "--hard", s"origin/$featureBranch"))
      if resetResult.exitCode != 0 then
        Output.error(s"Failed to reset to origin/$featureBranch: ${resetResult.stderr}")
        sys.exit(1)

      // Update review-state: phase_merged
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

      true
    else
      // Update review-state: awaiting_review
      val reviewStatePath = os.pwd / "project-management" / "issues" / issueId.value / "review-state.json"
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

  // Output JSON to stdout
  val result = PhaseOutput.PrOutput(
    issueId = issueId.value,
    phaseNumber = phaseNumber.value,
    prUrl = prUrl,
    headBranch = currentBranch,
    baseBranch = featureBranch,
    merged = merged
  )
  println(result.toJson)
