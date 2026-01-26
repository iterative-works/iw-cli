// PURPOSE: Unit tests for WorktreeCreationError to UserFriendlyError mapping
// PURPOSE: Tests error translation to user-facing messages with suggestions

package iw.core.domain

import munit.FunSuite
import iw.core.dashboard.domain.{UserFriendlyError, WorktreeCreationError}

class WorktreeCreationErrorMappingTest extends FunSuite:

  val testIssueId = "IW-79"

  test("DirectoryExists maps to user-friendly error with title"):
    val error = WorktreeCreationError.DirectoryExists("../iw-cli-IW-79")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.title.nonEmpty, "Should have a title")
    assert(friendly.title.contains("Directory") || friendly.title.contains("Exists"),
      "Title should mention directory existence")

  test("DirectoryExists maps with descriptive message"):
    val error = WorktreeCreationError.DirectoryExists("../iw-cli-IW-79")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.message.nonEmpty, "Should have a message")
    assert(friendly.message.contains("exists") || friendly.message.contains("directory"),
      "Message should mention directory exists")

  test("DirectoryExists maps with helpful suggestion"):
    val error = WorktreeCreationError.DirectoryExists("../iw-cli-IW-79")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.suggestion.isDefined, "Should have a suggestion")
    assert(friendly.suggestion.get.contains("Remove") || friendly.suggestion.get.contains("open"),
      "Suggestion should mention removing or registering")

  test("DirectoryExists is not retryable"):
    val error = WorktreeCreationError.DirectoryExists("../path")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(!friendly.canRetry, "Should not be retryable until directory is handled")

  test("DirectoryExists includes issueId for retry"):
    val error = WorktreeCreationError.DirectoryExists("../path")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.issueId.contains(testIssueId), "Should include issueId")

  test("AlreadyHasWorktree maps to user-friendly error"):
    val error = WorktreeCreationError.AlreadyHasWorktree("IW-79", "../iw-cli-IW-79")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.title.nonEmpty, "Should have a title")
    assert(friendly.title.toLowerCase.contains("already") || friendly.title.toLowerCase.contains("exists"),
      "Title should indicate duplication")

  test("AlreadyHasWorktree maps with issue ID in message"):
    val error = WorktreeCreationError.AlreadyHasWorktree("IW-79", "../iw-cli-IW-79")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.message.contains("IW-79"), "Should mention issue ID")

  test("AlreadyHasWorktree maps with attach command suggestion"):
    val error = WorktreeCreationError.AlreadyHasWorktree("IW-79", "../iw-cli-IW-79")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.suggestion.isDefined, "Should have a suggestion")
    assert(friendly.suggestion.get.contains("tmux attach"), "Suggestion should show attach command")

  test("AlreadyHasWorktree is not retryable"):
    val error = WorktreeCreationError.AlreadyHasWorktree("IW-79", "../path")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(!friendly.canRetry, "Should not be retryable")

  test("GitError maps to user-friendly error"):
    val error = WorktreeCreationError.GitError("fatal: not a git repository")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.title.nonEmpty, "Should have a title")
    assert(friendly.title.toLowerCase.contains("git") || friendly.title.toLowerCase.contains("failed"),
      "Title should indicate git failure")

  test("GitError maps with generic message (no internal details)"):
    val error = WorktreeCreationError.GitError("fatal: not a git repository")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.message.toLowerCase.contains("git"), "Should mention git in message")
    assert(!friendly.message.contains("fatal"), "Should not expose internal error details")

  test("GitError is retryable"):
    val error = WorktreeCreationError.GitError("temporary failure")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.canRetry, "Git errors should be retryable")

  test("GitError includes issueId for retry"):
    val error = WorktreeCreationError.GitError("error")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.issueId.contains(testIssueId), "Should include issueId for retry button")

  test("TmuxError maps to user-friendly error"):
    val error = WorktreeCreationError.TmuxError("session creation failed")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.title.nonEmpty, "Should have a title")
    assert(friendly.title.toLowerCase.contains("session") || friendly.title.toLowerCase.contains("failed"),
      "Title should indicate session failure")

  test("TmuxError maps with generic message (no internal details)"):
    val error = WorktreeCreationError.TmuxError("internal tmux error: xyz")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.message.toLowerCase.contains("session"), "Should mention session in message")
    assert(!friendly.message.contains("xyz"), "Should not expose internal error details")

  test("TmuxError is retryable"):
    val error = WorktreeCreationError.TmuxError("temporary failure")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.canRetry, "Tmux errors should be retryable")

  test("IssueNotFound maps to user-friendly error"):
    val error = WorktreeCreationError.IssueNotFound("INVALID-999")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.title.nonEmpty, "Should have a title")
    assert(friendly.title.toLowerCase.contains("not found"),
      "Title should indicate issue not found")

  test("IssueNotFound maps with issue ID in message"):
    val error = WorktreeCreationError.IssueNotFound("INVALID-999")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.message.contains("INVALID-999"), "Should mention issue ID")

  test("IssueNotFound is not retryable"):
    val error = WorktreeCreationError.IssueNotFound("INVALID-999")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(!friendly.canRetry, "Non-existent issue should not be retryable")

  test("ApiError maps to user-friendly error"):
    val error = WorktreeCreationError.ApiError("Connection timeout")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.title.nonEmpty, "Should have a title")
    assert(friendly.title.toLowerCase.contains("connection") || friendly.title.toLowerCase.contains("error"),
      "Title should indicate connection failure")

  test("ApiError maps with generic message (no internal details)"):
    val error = WorktreeCreationError.ApiError("Internal server error: 500")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.message.toLowerCase.contains("connect") || friendly.message.toLowerCase.contains("tracker"),
      "Should mention connection issue")
    assert(!friendly.message.contains("500"), "Should not expose internal error details")

  test("ApiError is retryable"):
    val error = WorktreeCreationError.ApiError("Connection timeout")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.canRetry, "API errors should be retryable")

  test("ApiError maps with suggestion"):
    val error = WorktreeCreationError.ApiError("Connection timeout")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.suggestion.isDefined, "Should have a suggestion")
    assert(friendly.suggestion.get.toLowerCase.contains("check") || friendly.suggestion.get.toLowerCase.contains("connection"),
      "Suggestion should mention checking connection")

  test("ApiError includes issueId for retry"):
    val error = WorktreeCreationError.ApiError("error")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.issueId.contains(testIssueId), "Should include issueId for retry button")

  test("CreationInProgress maps to user-friendly error"):
    val error = WorktreeCreationError.CreationInProgress("IW-79")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.title.nonEmpty, "Should have a title")
    assert(friendly.title.toLowerCase.contains("progress") || friendly.title.toLowerCase.contains("creation"),
      "Title should indicate creation in progress")

  test("CreationInProgress maps with descriptive message"):
    val error = WorktreeCreationError.CreationInProgress("IW-79")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.message.nonEmpty, "Should have a message")
    assert(friendly.message.toLowerCase.contains("already") || friendly.message.toLowerCase.contains("progress"),
      "Message should indicate creation is already in progress")

  test("CreationInProgress maps with helpful suggestion"):
    val error = WorktreeCreationError.CreationInProgress("IW-79")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.suggestion.isDefined, "Should have a suggestion")
    assert(friendly.suggestion.get.toLowerCase.contains("wait") || friendly.suggestion.get.toLowerCase.contains("complete"),
      "Suggestion should advise waiting for completion")

  test("CreationInProgress is retryable"):
    val error = WorktreeCreationError.CreationInProgress("IW-79")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.canRetry, "Should be retryable after current creation completes")

  test("CreationInProgress includes issueId for retry"):
    val error = WorktreeCreationError.CreationInProgress("IW-79")
    val friendly = WorktreeCreationError.toUserFriendly(error, testIssueId)

    assert(friendly.issueId.contains(testIssueId), "Should include issueId for retry button")
