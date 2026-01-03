// PURPOSE: Domain error types for worktree creation failures
// PURPOSE: Represents all possible error scenarios with specific context

package iw.core.domain

enum WorktreeCreationError:
  case DirectoryExists(path: String)
  case AlreadyHasWorktree(issueId: String, existingPath: String)
  case GitError(message: String)
  case TmuxError(message: String)
  case IssueNotFound(issueId: String)
  case ApiError(message: String)

object WorktreeCreationError:
  /** Map domain error to user-friendly error with actionable message.
    *
    * Error messages are kept generic to avoid information disclosure.
    * Full technical details are logged server-side.
    *
    * @param error Domain error from worktree creation
    * @param issueId The issue ID being processed (for retry functionality)
    * @return User-friendly error with title, message, suggestion, and retry flag
    */
  def toUserFriendly(error: WorktreeCreationError, issueId: String): UserFriendlyError =
    import WorktreeCreationError.*
    error match
      case DirectoryExists(path) =>
        val dirName = path.split("/").lastOption.getOrElse("unknown")
        UserFriendlyError(
          title = "Directory Already Exists",
          message = s"A directory for this issue already exists.",
          suggestion = Some(s"Remove the existing directory or use './iw open $issueId' to register it"),
          canRetry = false,
          issueId = Some(issueId)
        )

      case AlreadyHasWorktree(existingIssueId, existingPath) =>
        val sessionName = existingPath.split("/").lastOption.getOrElse(existingIssueId)
        UserFriendlyError(
          title = "Worktree Already Exists",
          message = s"Issue $existingIssueId already has a worktree.",
          suggestion = Some(s"Use 'tmux attach -t $sessionName' to open the existing worktree"),
          canRetry = false,
          issueId = Some(issueId)
        )

      case GitError(_) =>
        // Don't expose internal git error message to user
        UserFriendlyError(
          title = "Git Operation Failed",
          message = "Failed to create git worktree. Please try again.",
          suggestion = Some("If the problem persists, check your git configuration"),
          canRetry = true,
          issueId = Some(issueId)
        )

      case TmuxError(_) =>
        // Don't expose internal tmux error message to user
        UserFriendlyError(
          title = "Session Creation Failed",
          message = "Failed to create development session. Please try again.",
          suggestion = Some("If the problem persists, check that tmux is running"),
          canRetry = true,
          issueId = Some(issueId)
        )

      case IssueNotFound(notFoundId) =>
        UserFriendlyError(
          title = "Issue Not Found",
          message = s"Could not find issue '$notFoundId' in the tracker.",
          suggestion = Some("Verify the issue ID is correct and exists in your tracker"),
          canRetry = false,
          issueId = Some(issueId)
        )

      case ApiError(_) =>
        // Don't expose internal API error message to user
        UserFriendlyError(
          title = "Connection Error",
          message = "Could not connect to issue tracker. Please try again.",
          suggestion = Some("Check your internet connection"),
          canRetry = true,
          issueId = Some(issueId)
        )
