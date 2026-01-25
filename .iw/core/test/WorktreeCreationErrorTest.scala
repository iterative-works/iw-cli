// PURPOSE: Unit tests for WorktreeCreationError domain types
// PURPOSE: Tests error type construction and basic properties

package iw.core.domain

import munit.FunSuite
import iw.core.dashboard.domain.WorktreeCreationError

class WorktreeCreationErrorTest extends FunSuite:

  test("DirectoryExists error holds path"):
    val error = WorktreeCreationError.DirectoryExists("/some/path")
    error match
      case WorktreeCreationError.DirectoryExists(path) =>
        assertEquals(path, "/some/path")
      case _ =>
        fail("Should be DirectoryExists")

  test("AlreadyHasWorktree error holds issue ID and existing path"):
    val error = WorktreeCreationError.AlreadyHasWorktree("IW-79", "/existing/path")
    error match
      case WorktreeCreationError.AlreadyHasWorktree(issueId, existingPath) =>
        assertEquals(issueId, "IW-79")
        assertEquals(existingPath, "/existing/path")
      case _ =>
        fail("Should be AlreadyHasWorktree")

  test("GitError error holds message"):
    val error = WorktreeCreationError.GitError("git command failed")
    error match
      case WorktreeCreationError.GitError(message) =>
        assertEquals(message, "git command failed")
      case _ =>
        fail("Should be GitError")

  test("TmuxError error holds message"):
    val error = WorktreeCreationError.TmuxError("tmux session failed")
    error match
      case WorktreeCreationError.TmuxError(message) =>
        assertEquals(message, "tmux session failed")
      case _ =>
        fail("Should be TmuxError")

  test("IssueNotFound error holds issue ID"):
    val error = WorktreeCreationError.IssueNotFound("INVALID-999")
    error match
      case WorktreeCreationError.IssueNotFound(issueId) =>
        assertEquals(issueId, "INVALID-999")
      case _ =>
        fail("Should be IssueNotFound")

  test("ApiError error holds message"):
    val error = WorktreeCreationError.ApiError("API unavailable")
    error match
      case WorktreeCreationError.ApiError(message) =>
        assertEquals(message, "API unavailable")
      case _ =>
        fail("Should be ApiError")

  test("CreationInProgress error holds issue ID"):
    val error = WorktreeCreationError.CreationInProgress("IW-79")
    error match
      case WorktreeCreationError.CreationInProgress(issueId) =>
        assertEquals(issueId, "IW-79")
      case _ =>
        fail("Should be CreationInProgress")

  test("all error types extend WorktreeCreationError"):
    val errors: List[WorktreeCreationError] = List(
      WorktreeCreationError.DirectoryExists("/path"),
      WorktreeCreationError.AlreadyHasWorktree("IW-1", "/path"),
      WorktreeCreationError.GitError("error"),
      WorktreeCreationError.TmuxError("error"),
      WorktreeCreationError.IssueNotFound("IW-1"),
      WorktreeCreationError.ApiError("error"),
      WorktreeCreationError.CreationInProgress("IW-1")
    )

    assertEquals(errors.length, 7)
