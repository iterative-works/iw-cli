// PURPOSE: Pure business logic for worktree creation orchestration
// PURPOSE: Coordinates issue fetching, git worktree creation, tmux session setup, and registration

package iw.core.dashboard.application

import iw.core.model.{ProjectConfiguration, IssueId, WorktreePath, IssueData}
import iw.core.dashboard.domain.{WorktreeCreationResult, WorktreeCreationError}
import iw.core.dashboard.infrastructure.CreationLockRegistry

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
   * @param checkDirectoryExists Function to check if directory already exists on disk
   * @param checkWorktreeExists Function to check if issue already has registered worktree
   * @return Either WorktreeCreationError or WorktreeCreationResult with paths and commands
   */
  def create(
    issueId: String,
    config: ProjectConfiguration,
    fetchIssue: String => Either[String, IssueData],
    createWorktree: (String, String) => Either[String, Unit],
    createTmuxSession: (String, String) => Either[String, Unit],
    registerWorktree: (String, String, String, String) => Either[String, Unit],
    checkDirectoryExists: String => Boolean = _ => false,
    checkWorktreeExists: String => Option[String] = _ => None
  ): Either[WorktreeCreationError, WorktreeCreationResult] =
    for
      // Fetch issue data from tracker
      issueData <- fetchIssue(issueId).left.map(err => WorktreeCreationError.IssueNotFound(issueId))

      // Parse issue ID for branch naming
      parsedIssueId <- IssueId.parse(issueId, config.teamPrefix).left.map(err => WorktreeCreationError.ApiError(err))

      // Generate paths and names using existing domain logic
      worktreePath = WorktreePath(config.projectName, parsedIssueId)
      branchName = parsedIssueId.toBranchName
      sessionName = worktreePath.sessionName

      // For the actual path, we need to use a sibling directory approach
      // In the web context, we'll use a base directory (this will be passed via the actual path parameter)
      // For now, we construct a relative path string that will be resolved by the caller
      fullPath = s"../${worktreePath.directoryName}"

      // Check if directory already exists
      _ <- if checkDirectoryExists(fullPath) then
        Left(WorktreeCreationError.DirectoryExists(fullPath))
      else
        Right(())

      // Check if worktree already registered for this issue
      _ <- checkWorktreeExists(issueId) match
        case Some(existingPath) =>
          Left(WorktreeCreationError.AlreadyHasWorktree(issueId, existingPath))
        case None =>
          Right(())

      // Create git worktree with branch
      _ <- createWorktree(fullPath, branchName).left.map(err => WorktreeCreationError.GitError(err))

      // Create detached tmux session
      _ <- createTmuxSession(sessionName, fullPath).left.map(err => WorktreeCreationError.TmuxError(err))

      // Register worktree in state file
      trackerTypeStr = config.trackerType.toString
      team = parsedIssueId.team
      _ <- registerWorktree(issueId, fullPath, trackerTypeStr, team).left.map(err => WorktreeCreationError.ApiError(err))

      // Build attach command
      attachCommand = s"tmux attach -t $sessionName"
    yield WorktreeCreationResult(
      issueId = issueId,
      worktreePath = fullPath,
      tmuxSessionName = sessionName,
      tmuxAttachCommand = attachCommand
    )

  /**
   * Creates a worktree with lock protection to prevent concurrent creation.
   *
   * This wraps the create method with lock acquisition/release using CreationLockRegistry.
   * If the lock cannot be acquired, returns CreationInProgress error immediately.
   * The lock is always released after creation completes (success or failure).
   *
   * @param issueId The issue identifier (e.g., "IW-79")
   * @param config Project configuration with tracker and project details
   * @param fetchIssue Function to fetch issue data from tracker API
   * @param createWorktree Function to create git worktree (path, branchName) => Either[error, ()]
   * @param createTmuxSession Function to create tmux session (sessionName, workDir) => Either[error, ()]
   * @param registerWorktree Function to register worktree in state (issueId, path, trackerType, team) => Either[error, ()]
   * @param checkDirectoryExists Function to check if directory already exists on disk
   * @param checkWorktreeExists Function to check if issue already has registered worktree
   * @return Either WorktreeCreationError or WorktreeCreationResult with paths and commands
   */
  def createWithLock(
    issueId: String,
    config: ProjectConfiguration,
    fetchIssue: String => Either[String, IssueData],
    createWorktree: (String, String) => Either[String, Unit],
    createTmuxSession: (String, String) => Either[String, Unit],
    registerWorktree: (String, String, String, String) => Either[String, Unit],
    checkDirectoryExists: String => Boolean = _ => false,
    checkWorktreeExists: String => Option[String] = _ => None
  ): Either[WorktreeCreationError, WorktreeCreationResult] =
    // Try to acquire lock
    if !CreationLockRegistry.tryAcquire(issueId) then
      Left(WorktreeCreationError.CreationInProgress(issueId))
    else
      try
        // Perform creation with all dependencies
        create(
          issueId,
          config,
          fetchIssue,
          createWorktree,
          createTmuxSession,
          registerWorktree,
          checkDirectoryExists,
          checkWorktreeExists
        )
      finally
        // Always release lock, even if creation failed
        CreationLockRegistry.release(issueId)
