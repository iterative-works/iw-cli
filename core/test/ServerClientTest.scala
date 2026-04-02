// PURPOSE: Unit tests for ServerClient health check functionality
// PURPOSE: Tests isHealthy() method with running and unavailable servers

package iw.core.infrastructure

import munit.FunSuite
import java.util.Random
import iw.core.dashboard.CaskServer
import iw.core.adapters.ServerClient

class ServerClientTest extends FunSuite:

  // Helper to find available port
  def findAvailablePort(): Int =
    val socket = new java.net.ServerSocket(0)
    val port = socket.getLocalPort
    socket.close()
    port

  // Helper to create temp state file path
  def createTempStatePath(): String =
    val tmpDir = System.getProperty("java.io.tmpdir")
    val randomId = Random().nextLong().abs
    s"$tmpDir/iw-server-test-$randomId/state.json"

  test("isHealthy returns true when server is running"):
    val statePath = createTempStatePath()
    val port = findAvailablePort()

    try
      // Start server
      val serverThread = new Thread(() => {
        CaskServer.start(statePath, port)
      })
      serverThread.setDaemon(true)
      serverThread.start()

      // Wait for server to be ready
      val retries = new java.util.concurrent.atomic.AtomicInteger(0)
      val serverReady = new java.util.concurrent.atomic.AtomicBoolean(false)
      while retries.get < 50 && !serverReady.get do
        Thread.sleep(100)
        serverReady.set(ServerClient.isHealthy(port))
        retries.incrementAndGet()

      assert(serverReady.get, "Server should become healthy")
      assert(
        ServerClient.isHealthy(port),
        "isHealthy should return true for running server"
      )

    finally
      // Cleanup
      val stateFile = java.nio.file.Paths.get(statePath)
      if java.nio.file.Files.exists(stateFile) then
        java.nio.file.Files.delete(stateFile)
      val parentDir = stateFile.getParent
      if Option(parentDir).exists(java.nio.file.Files.exists(_)) then
        java.nio.file.Files.delete(parentDir)

  test("isHealthy returns false when server is unavailable"):
    val port = findAvailablePort()

    // No server running on this port
    assert(
      !ServerClient.isHealthy(port),
      "isHealthy should return false when server is unavailable"
    )

  test("isHealthy returns false for invalid port"):
    // Port 99999 is invalid
    assert(
      !ServerClient.isHealthy(99999),
      "isHealthy should return false for invalid port"
    )

  // Project registration tests (IW-148 Phase 3)

  test("registerProject returns Right(()) when server is disabled"):
    val result = withEnv("IW_SERVER_DISABLED", "true") {
      ServerClient.registerProject(
        projectName = "my-app",
        path = "/some/path",
        trackerType = "GitHub",
        team = "iterative-works",
        trackerUrl = None
      )
    }
    assertEquals(result, Right(()))

  /** Run a block with an environment variable temporarily set.
    *
    * Uses reflection to modify the process environment for testing.
    */
  private def withEnv[A](key: String, value: String)(block: => A): A =
    val field = System.getenv().getClass.getDeclaredField("m")
    field.setAccessible(true)
    val mutableEnv =
      field.get(System.getenv()).asInstanceOf[java.util.Map[String, String]]
    val previous = Option(mutableEnv.put(key, value))
    try block
    finally
      previous match
        case Some(v) => mutableEnv.put(key, v)
        case None    => mutableEnv.remove(key)
