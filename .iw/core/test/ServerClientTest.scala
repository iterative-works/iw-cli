// PURPOSE: Unit tests for ServerClient health check functionality
// PURPOSE: Tests isHealthy() method with running and unavailable servers

package iw.core.infrastructure

import munit.FunSuite
import java.util.Random
import iw.core.dashboard.CaskServer
import iw.core.dashboard.ServerClient

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
      var retries = 0
      var serverReady = false
      while retries < 50 && !serverReady do
        Thread.sleep(100)
        serverReady = ServerClient.isHealthy(port)
        retries += 1

      assert(serverReady, "Server should become healthy")
      assert(ServerClient.isHealthy(port), "isHealthy should return true for running server")

    finally
      // Cleanup
      val stateFile = java.nio.file.Paths.get(statePath)
      if java.nio.file.Files.exists(stateFile) then
        java.nio.file.Files.delete(stateFile)
      val parentDir = stateFile.getParent
      if parentDir != null && java.nio.file.Files.exists(parentDir) then
        java.nio.file.Files.delete(parentDir)

  test("isHealthy returns false when server is unavailable"):
    val port = findAvailablePort()

    // No server running on this port
    assert(!ServerClient.isHealthy(port), "isHealthy should return false when server is unavailable")

  test("isHealthy returns false for invalid port"):
    // Port 99999 is invalid
    assert(!ServerClient.isHealthy(99999), "isHealthy should return false for invalid port")
