// PURPOSE: Integration tests for TmuxAdapter session management
// PURPOSE: Tests session creation, existence checks, and cleanup with real tmux

//> using scala 3.3.1
//> using dep org.scalameta::munit::1.0.0
//> using file "../Process.scala"
//> using file "../Tmux.scala"

package iw.core.test

import iw.core.*
import munit.FunSuite
import java.nio.file.Paths

class TmuxAdapterTest extends FunSuite:

  val testSessionPrefix = "iw-test-session"
  var sessionCounter = 0

  def uniqueSessionName(): String =
    sessionCounter += 1
    s"$testSessionPrefix-$sessionCounter"

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
    val workDir = Paths.get(System.getProperty("user.home"))
    // Create session using adapter
    TmuxAdapter.createSession(sessionName, workDir)
    try
      assertEquals(TmuxAdapter.sessionExists(sessionName), true)
    finally
      TmuxAdapter.killSession(sessionName)

  test("TmuxAdapter.createSession creates a detached session"):
    val sessionName = uniqueSessionName()
    val workDir = Paths.get(System.getProperty("user.home"))

    try
      val result = TmuxAdapter.createSession(sessionName, workDir)
      assert(result.isRight, s"Failed to create session: $result")

      // Verify session exists
      assertEquals(TmuxAdapter.sessionExists(sessionName), true)
    finally
      TmuxAdapter.killSession(sessionName)

  test("TmuxAdapter.createSession sets working directory"):
    val sessionName = uniqueSessionName()
    val workDir = Paths.get(System.getProperty("user.home"))

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
    val workDir = Paths.get(System.getProperty("user.home"))

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
    val workDir = Paths.get(System.getProperty("user.home"))

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
