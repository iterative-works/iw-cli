// PURPOSE: Pure business logic for worktree creation orchestration
// PURPOSE: Coordinates issue fetching, git worktree creation, tmux session setup, and registration

package iw.core.application

import iw.core.{ProjectConfiguration, IssueId, WorktreePath}
import iw.core.domain.{IssueData, WorktreeCreationResult}

object WorktreeCreationService:
  /**
   * Creates a worktree for the given issue ID.
   *
   * This is a pure function that orchestrates the creation workflow by accepting
   * all I/O operations as function parameters. This makes it easy to test and keeps
   * the business logic separated from side effects.
   *
   * @param issueId The issue identifier (e.g., "IW-79")
   * @param config Project configuration with tracker and project details
   * @param fetchIssue Function to fetch issue data from tracker API
   * @param createWorktree Function to create git worktree (path, branchName) => Either[error, ()]
   * @param createTmuxSession Function to create tmux session (sessionName, workDir) => Either[error, ()]
   * @param registerWorktree Function to register worktree in state (issueId, path, trackerType, team) => Either[error, ()]
   * @return Either error message or WorktreeCreationResult with paths and commands
   */
  def create(
    issueId: String,
    config: ProjectConfiguration,
    fetchIssue: String => Either[String, IssueData],
    createWorktree: (String, String) => Either[String, Unit],
    createTmuxSession: (String, String) => Either[String, Unit],
    registerWorktree: (String, String, String, String) => Either[String, Unit]
  ): Either[String, WorktreeCreationResult] =
    for
      // Fetch issue data from tracker
      issueData <- fetchIssue(issueId)

      // Parse issue ID for branch naming
      parsedIssueId <- IssueId.parse(issueId, config.teamPrefix)

      // Generate paths and names using existing domain logic
      worktreePath = WorktreePath(config.projectName, parsedIssueId)
      branchName = parsedIssueId.toBranchName
      sessionName = worktreePath.sessionName

      // For the actual path, we need to use a sibling directory approach
      // In the web context, we'll use a base directory (this will be passed via the actual path parameter)
      // For now, we construct a relative path string that will be resolved by the caller
      fullPath = s"../${worktreePath.directoryName}"

      // Create git worktree with branch
      _ <- createWorktree(fullPath, branchName)

      // Create detached tmux session
      _ <- createTmuxSession(sessionName, fullPath)

      // Register worktree in state file
      trackerTypeStr = config.trackerType.toString
      team = parsedIssueId.team
      _ <- registerWorktree(issueId, fullPath, trackerTypeStr, team)

      // Build attach command
      attachCommand = s"tmux attach -t $sessionName"
    yield WorktreeCreationResult(
      issueId = issueId,
      worktreePath = fullPath,
      tmuxSessionName = sessionName,
      tmuxAttachCommand = attachCommand
    )
