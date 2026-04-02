// PURPOSE: Integration tests for TmuxAdapter.sendKeys command execution
// PURPOSE: Verifies sending keystrokes to tmux sessions with real tmux server

package iw.core.test

import iw.core.adapters.TmuxAdapter
import munit.FunSuite

class TmuxAdapterSendKeysTest extends FunSuite:

  val testSessionPrefix = "iw-test-sendkeys"
  val sessionCounter = new java.util.concurrent.atomic.AtomicInteger(0)

  def uniqueSessionName(): String =
    val count = sessionCounter.incrementAndGet()
    s"$testSessionPrefix-$count"

  override def afterEach(context: AfterEach): Unit =
    // Cleanup: kill any test sessions
    val result = iw.core.adapters.ProcessAdapter
      .run(Seq("tmux", "list-sessions", "-F", "#{session_name}"))
    if result.exitCode == 0 then
      val sessions =
        result.stdout.split("\n").filter(_.startsWith(testSessionPrefix))
      sessions.foreach { session =>
        TmuxAdapter.killSession(session)
      }

  test("sendKeys returns Right(()) when sending to existing session"):
    val sessionName = uniqueSessionName()
    val workDir = os.Path(System.getProperty("user.home"))

    try
      // Create a test session
      val createResult = TmuxAdapter.createSession(sessionName, workDir)
      assert(createResult.isRight, s"Failed to create session: $createResult")

      // Send keys to the session
      val sendResult = TmuxAdapter.sendKeys(sessionName, "echo 'test'")
      assert(sendResult.isRight, s"Failed to send keys: $sendResult")
    finally TmuxAdapter.killSession(sessionName)

  test("sendKeys returns Left when session does not exist"):
    val nonExistentSession = "nonexistent-session-12345"

    val result = TmuxAdapter.sendKeys(nonExistentSession, "echo 'test'")
    assert(
      result.isLeft,
      s"Expected Left for non-existent session, got: $result"
    )
    val error = result.left.getOrElse("")
    assert(
      error.contains("Failed to send keys"),
      s"Error message should mention failure: $error"
    )
    assert(
      error.contains(nonExistentSession),
      s"Error message should include session name: $error"
    )
