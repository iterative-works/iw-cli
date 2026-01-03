// PURPOSE: Unit tests for UserFriendlyError domain model
// PURPOSE: Tests user-facing error message construction

package iw.core.domain

import munit.FunSuite

class UserFriendlyErrorTest extends FunSuite:

  test("UserFriendlyError holds title and message"):
    val error = UserFriendlyError(
      title = "Error Title",
      message = "Error message",
      suggestion = None,
      canRetry = false
    )

    assert(error.title == "Error Title", "Should store title")
    assert(error.message == "Error message", "Should store message")

  test("UserFriendlyError holds suggestion when provided"):
    val error = UserFriendlyError(
      title = "Error",
      message = "Message",
      suggestion = Some("Try this"),
      canRetry = false
    )

    assert(error.suggestion.isDefined, "Should have suggestion")
    assert(error.suggestion.get == "Try this", "Should store suggestion text")

  test("UserFriendlyError has no suggestion when None"):
    val error = UserFriendlyError(
      title = "Error",
      message = "Message",
      suggestion = None,
      canRetry = false
    )

    assert(error.suggestion.isEmpty, "Should have no suggestion")

  test("UserFriendlyError indicates whether retry is possible"):
    val retryable = UserFriendlyError(
      title = "Error",
      message = "Message",
      suggestion = None,
      canRetry = true
    )

    val notRetryable = UserFriendlyError(
      title = "Error",
      message = "Message",
      suggestion = None,
      canRetry = false
    )

    assert(retryable.canRetry, "Should be retryable")
    assert(!notRetryable.canRetry, "Should not be retryable")
