// PURPOSE: Integration tests for static file serving in CaskServer
// PURPOSE: Tests /static/* routes return CSS and JS files with correct Content-Type

package iw.core.infrastructure

import munit.FunSuite
import sttp.client4.quick.*
import sttp.client4.Response
import java.nio.file.{Files, Paths}
import scala.util.Random
import iw.core.dashboard.CaskServer

class StaticFilesTest extends FunSuite:

  // Helper to create temp state file path
  def createTempStatePath(): String =
    val tmpDir = System.getProperty("java.io.tmpdir")
    val randomId = Random.nextLong().abs
    s"$tmpDir/iw-server-test-$randomId/state.json"

  // Helper to start server in background thread
  def startTestServer(statePath: String, port: Int): Thread =
    val serverThread = new Thread(() => {
      CaskServer.start(statePath, port)
    })
    serverThread.setDaemon(true)
    serverThread.start()

    // Wait for server to be ready
    val ready = (0 until 50).exists { _ =>
      val isReady = try
        val response = quickRequest.get(uri"http://localhost:$port/health").send()
        response.code.code == 200
      catch
        case _: Exception => false
      if !isReady then Thread.sleep(100)
      isReady
    }

    if !ready then fail(s"Server failed to start on port $port")
    serverThread

  // Helper to find available port
  def findAvailablePort(): Int =
    val socket = new java.net.ServerSocket(0)
    val port = socket.getLocalPort
    socket.close()
    port

  test("GET /static/dashboard.css returns CSS with correct Content-Type"):
    val statePath = createTempStatePath()
    val port = findAvailablePort()

    try
      val serverThread = startTestServer(statePath, port)

      val response = quickRequest
        .get(uri"http://localhost:$port/static/dashboard.css")
        .send()

      assertEquals(response.code.code, 200)
      assert(
        response.headers.exists(h =>
          h.name.equalsIgnoreCase("Content-Type") && h.value.contains("text/css")
        ),
        "Content-Type header should be text/css"
      )

    finally
      // Cleanup
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))

  test("GET /static/dashboard.js returns JS with correct Content-Type"):
    val statePath = createTempStatePath()
    val port = findAvailablePort()

    try
      val serverThread = startTestServer(statePath, port)

      val response = quickRequest
        .get(uri"http://localhost:$port/static/dashboard.js")
        .send()

      assertEquals(response.code.code, 200)
      assert(
        response.headers.exists(h =>
          h.name.equalsIgnoreCase("Content-Type") && h.value.contains("application/javascript")
        ),
        "Content-Type header should be application/javascript"
      )

    finally
      // Cleanup
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))

  test("GET /static/dashboard.css returns file contents"):
    val statePath = createTempStatePath()
    val port = findAvailablePort()

    try
      val serverThread = startTestServer(statePath, port)

      val response = quickRequest
        .get(uri"http://localhost:$port/static/dashboard.css")
        .send()

      assertEquals(response.code.code, 200)
      val body = response.body
      assert(body.contains("body {"), "CSS should contain body selector")
      assert(body.contains(".container"), "CSS should contain .container class")
      assert(body.contains(".worktree-card"), "CSS should contain .worktree-card class")

    finally
      // Cleanup
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))

  test("GET /static/dashboard.js returns file contents"):
    val statePath = createTempStatePath()
    val port = findAvailablePort()

    try
      val serverThread = startTestServer(statePath, port)

      val response = quickRequest
        .get(uri"http://localhost:$port/static/dashboard.js")
        .send()

      assertEquals(response.code.code, 200)
      val body = response.body
      assert(body.contains("visibilitychange"), "JS should contain visibilitychange event")
      assert(body.contains("htmx.trigger"), "JS should contain htmx.trigger call")

    finally
      // Cleanup
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))

  test("GET /static/nonexistent.css returns 404"):
    val statePath = createTempStatePath()
    val port = findAvailablePort()

    try
      val serverThread = startTestServer(statePath, port)

      val response = quickRequest
        .get(uri"http://localhost:$port/static/nonexistent.css")
        .send()

      assertEquals(response.code.code, 404)

    finally
      // Cleanup
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))
