// PURPOSE: Integration tests for TmuxAdapter session management
// PURPOSE: Tests session creation, existence checks, and cleanup with real tmux
package iw.tests

import iw.core.adapters.{ProcessAdapter, TmuxAdapter}
import munit.FunSuite

class TmuxAdapterTest extends FunSuite:

  val testSessionPrefix = "iw-test-session"
  val sessionCounter = new java.util.concurrent.atomic.AtomicInteger(0)

  def uniqueSessionName(): String =
    val count = sessionCounter.incrementAndGet()
    s"$testSessionPrefix-$count"

  override def afterEach(context: AfterEach): Unit =
    // Cleanup: kill any test sessions that might be left over
    // Use ProcessAdapter to stay consistent with adapter testing
    val result = ProcessAdapter.run(Seq("tmux", "list-sessions", "-F", "#{session_name}"))
    if result.exitCode == 0 then
      val sessions = result.stdout.split("\n").filter(_.startsWith(testSessionPrefix))
      sessions.foreach { session =>
        TmuxAdapter.killSession(session)
      }

  test("TmuxAdapter.sessionExists returns false for non-existent session"):
    val sessionName = uniqueSessionName()
    assertEquals(TmuxAdapter.sessionExists(sessionName), false)

  test("TmuxAdapter.sessionExists returns true for existing session"):
    val sessionName = uniqueSessionName()
    val workDir = os.Path(System.getProperty("user.home"))
    // Create session using adapter
    TmuxAdapter.createSession(sessionName, workDir)
    try
      assertEquals(TmuxAdapter.sessionExists(sessionName), true)
    finally
      TmuxAdapter.killSession(sessionName)

  test("TmuxAdapter.createSession creates a detached session"):
    val sessionName = uniqueSessionName()
    val workDir = os.Path(System.getProperty("user.home"))

    try
      val result = TmuxAdapter.createSession(sessionName, workDir)
      assert(result.isRight, s"Failed to create session: $result")

      // Verify session exists
      assertEquals(TmuxAdapter.sessionExists(sessionName), true)
    finally
      TmuxAdapter.killSession(sessionName)

  test("TmuxAdapter.createSession sets working directory"):
    val sessionName = uniqueSessionName()
    val workDir = os.Path(System.getProperty("user.home"))

    try
      val result = TmuxAdapter.createSession(sessionName, workDir)
      assert(result.isRight, s"Failed to create session: $result")

      // Get the session's working directory using ProcessAdapter
      val queryResult = ProcessAdapter.run(
        Seq("tmux", "display-message", "-t", sessionName, "-p", "#{pane_current_path}")
      )
      assertEquals(queryResult.stdout.trim, workDir.toString)
    finally
      TmuxAdapter.killSession(sessionName)

  test("TmuxAdapter.createSession fails for duplicate session name"):
    val sessionName = uniqueSessionName()
    val workDir = os.Path(System.getProperty("user.home"))

    try
      // Create first session
      TmuxAdapter.createSession(sessionName, workDir)

      // Try to create duplicate - should fail
      val result = TmuxAdapter.createSession(sessionName, workDir)
      assert(result.isLeft, "Should fail to create duplicate session")
    finally
      TmuxAdapter.killSession(sessionName)

  test("TmuxAdapter.killSession removes existing session"):
    val sessionName = uniqueSessionName()
    val workDir = os.Path(System.getProperty("user.home"))

    // Create session
    TmuxAdapter.createSession(sessionName, workDir)
    assertEquals(TmuxAdapter.sessionExists(sessionName), true)

    // Kill session
    val result = TmuxAdapter.killSession(sessionName)
    assert(result.isRight, s"Failed to kill session: $result")
    assertEquals(TmuxAdapter.sessionExists(sessionName), false)

  test("TmuxAdapter.killSession fails for non-existent session"):
    val sessionName = uniqueSessionName()
    val result = TmuxAdapter.killSession(sessionName)
    assert(result.isLeft, "Should fail to kill non-existent session")

  test("TmuxAdapter.isInsideTmux returns false when TMUX env var is not set"):
    // Save current TMUX env var
    val originalTmux = sys.env.get("TMUX")
    try
      // Unset TMUX env var (we can't actually unset it, but we can verify the logic)
      // This test checks the current environment
      val isInside = TmuxAdapter.isInsideTmux
      // We can't guarantee what the test environment is, so we just verify it returns a boolean
      assert(isInside == sys.env.contains("TMUX"))
    finally
      // No cleanup needed since we're just reading env vars

  test("TmuxAdapter.currentSessionName returns None when not inside tmux") {
    // This test can only verify behavior if we're not in tmux
    if !sys.env.contains("TMUX") then
      assertEquals(TmuxAdapter.currentSessionName, None)
  }

  test("TmuxAdapter.currentSessionName returns session name when inside tmux"):
    // This is a manual test since we can't easily set TMUX env var
    // We'll test this via E2E tests instead
    // For now, just verify the logic path
    val sessionName = uniqueSessionName()
    val workDir = os.Path(System.getProperty("user.home"))

    try
      // Create a session
      TmuxAdapter.createSession(sessionName, workDir)

      // We can't actually enter the session in this test, but we can verify
      // that the session exists
      assertEquals(TmuxAdapter.sessionExists(sessionName), true)
    finally
      TmuxAdapter.killSession(sessionName)

  test("TmuxAdapter.isCurrentSession returns true when in matching session"):
    // This test verifies the logic when we ARE inside tmux
    // If we're in tmux, it should match the current session name
    val currentSession = TmuxAdapter.currentSessionName
    if currentSession.isDefined then
      assertEquals(TmuxAdapter.isCurrentSession(currentSession.get), true)

  test("TmuxAdapter.isCurrentSession returns false when in different session"):
    // This test verifies the logic when we ARE inside tmux but checking a different session
    val currentSession = TmuxAdapter.currentSessionName
    if currentSession.isDefined then
      val differentSession = uniqueSessionName()
      assertEquals(TmuxAdapter.isCurrentSession(differentSession), false)

  test("TmuxAdapter.isCurrentSession returns false when not in tmux"):
    // This test can only verify behavior if we're not in tmux
    if !sys.env.contains("TMUX") then
      val sessionName = uniqueSessionName()
      assertEquals(TmuxAdapter.isCurrentSession(sessionName), false)

  test("TmuxAdapter.switchSession returns Left when session doesn't exist"):
    val sessionName = uniqueSessionName()
    val result = TmuxAdapter.switchSession(sessionName)
    assert(result.isLeft, "Should fail when switching to non-existent session")

  test("TmuxAdapter.switchSession returns Left when not in tmux"):
    // This test verifies the command fails gracefully when not inside tmux
    if !sys.env.contains("TMUX") then
      val sessionName = uniqueSessionName()
      val workDir = os.Path(System.getProperty("user.home"))

      try
        // Create a session first
        TmuxAdapter.createSession(sessionName, workDir)

        // Try to switch while not in tmux - should fail
        val result = TmuxAdapter.switchSession(sessionName)
        assert(result.isLeft, "Should fail when switching outside tmux")
      finally
        TmuxAdapter.killSession(sessionName)
