// PURPOSE: Integration tests for CaskServer HTTP endpoints
// PURPOSE: Tests registration endpoint with real StateRepository and JSON serialization

package iw.core.infrastructure

import munit.FunSuite
import sttp.client4.quick.*
import sttp.client4.Response
import java.nio.file.{Files, Paths}
import java.io.File
import scala.util.Random

class CaskServerTest extends FunSuite:

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
    var retries = 0
    while retries < 50 do
      try
        val response = quickRequest.get(uri"http://localhost:$port/health").send()
        if response.code.code == 200 then
          return serverThread
      catch
        case _: Exception => ()
      Thread.sleep(100)
      retries += 1

    fail(s"Server failed to start on port $port")
    serverThread

  // Helper to find available port
  def findAvailablePort(): Int =
    val socket = new java.net.ServerSocket(0)
    val port = socket.getLocalPort
    socket.close()
    port

  test("PUT /api/worktrees/{issueId} registers new worktree"):
    val statePath = createTempStatePath()
    val port = findAvailablePort()

    try
      val serverThread = startTestServer(statePath, port)

      // Send PUT request
      val requestBody = ujson.Obj(
        "path" -> "/test/path/worktree",
        "trackerType" -> "Linear",
        "team" -> "IWLE"
      )

      val response = quickRequest
        .put(uri"http://localhost:$port/api/worktrees/IWLE-123")
        .body(ujson.write(requestBody))
        .header("Content-Type", "application/json")
        .send()

      assertEquals(response.code.code, 200)

      // Verify response body
      val responseJson = ujson.read(response.body)
      assertEquals(responseJson("status").str, "registered")
      assertEquals(responseJson("issueId").str, "IWLE-123")
      assert(responseJson.obj.contains("lastSeenAt"))

      // Verify state.json was created and contains the registration
      val stateFile = Paths.get(statePath)
      assert(Files.exists(stateFile), s"State file should exist at $statePath")

      val stateContent = Files.readString(stateFile)
      val stateJson = ujson.read(stateContent)
      assert(stateJson("worktrees").obj.contains("IWLE-123"))

      val worktree = stateJson("worktrees")("IWLE-123")
      assertEquals(worktree("path").str, "/test/path/worktree")
      assertEquals(worktree("trackerType").str, "Linear")
      assertEquals(worktree("team").str, "IWLE")

    finally
      // Cleanup
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      if parentDir != null && Files.exists(parentDir) then Files.delete(parentDir)

  test("PUT /api/worktrees/{issueId} updates existing worktree"):
    val statePath = createTempStatePath()
    val port = findAvailablePort()

    try
      val serverThread = startTestServer(statePath, port)

      // First registration
      val firstRequest = ujson.Obj(
        "path" -> "/original/path",
        "trackerType" -> "Linear",
        "team" -> "IWLE"
      )

      quickRequest
        .put(uri"http://localhost:$port/api/worktrees/IWLE-456")
        .body(ujson.write(firstRequest))
        .header("Content-Type", "application/json")
        .send()

      Thread.sleep(50) // Ensure time difference

      // Second registration with updated data
      val secondRequest = ujson.Obj(
        "path" -> "/updated/path",
        "trackerType" -> "YouTrack",
        "team" -> "NEWTEA"
      )

      val response = quickRequest
        .put(uri"http://localhost:$port/api/worktrees/IWLE-456")
        .body(ujson.write(secondRequest))
        .header("Content-Type", "application/json")
        .send()

      assertEquals(response.code.code, 200)

      // Verify state was updated
      val stateContent = Files.readString(Paths.get(statePath))
      val stateJson = ujson.read(stateContent)
      val worktree = stateJson("worktrees")("IWLE-456")

      assertEquals(worktree("path").str, "/updated/path")
      assertEquals(worktree("trackerType").str, "YouTrack")
      assertEquals(worktree("team").str, "NEWTEA")

    finally
      // Cleanup
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      if parentDir != null && Files.exists(parentDir) then Files.delete(parentDir)

  test("PUT /api/worktrees/{issueId} returns 400 for malformed JSON"):
    val statePath = createTempStatePath()
    val port = findAvailablePort()

    try
      val serverThread = startTestServer(statePath, port)

      val response = quickRequest
        .put(uri"http://localhost:$port/api/worktrees/IWLE-789")
        .body("{invalid json")
        .header("Content-Type", "application/json")
        .send()

      assertEquals(response.code.code, 400)

      val responseJson = ujson.read(response.body)
      assert(responseJson.obj.contains("error"))

    finally
      // Cleanup
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      if parentDir != null && Files.exists(parentDir) then Files.delete(parentDir)

  test("PUT /api/worktrees/{issueId} returns 400 for missing fields"):
    val statePath = createTempStatePath()
    val port = findAvailablePort()

    try
      val serverThread = startTestServer(statePath, port)

      // Missing 'team' field
      val requestBody = ujson.Obj(
        "path" -> "/test/path",
        "trackerType" -> "Linear"
      )

      val response = quickRequest
        .put(uri"http://localhost:$port/api/worktrees/IWLE-999")
        .body(ujson.write(requestBody))
        .header("Content-Type", "application/json")
        .send()

      assertEquals(response.code.code, 400)

      val responseJson = ujson.read(response.body)
      assert(responseJson.obj.contains("error"))

    finally
      // Cleanup
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      if parentDir != null && Files.exists(parentDir) then Files.delete(parentDir)

  test("PUT /api/worktrees/{issueId} returns 400 for invalid issueId"):
    val statePath = createTempStatePath()
    val port = findAvailablePort()

    try
      val serverThread = startTestServer(statePath, port)

      val requestBody = ujson.Obj(
        "path" -> "/test/path",
        "trackerType" -> "Linear",
        "team" -> "IWLE"
      )

      // Empty issueId
      val response = quickRequest
        .put(uri"http://localhost:$port/api/worktrees/")
        .body(ujson.write(requestBody))
        .header("Content-Type", "application/json")
        .send()

      // Should get 404 or 400 (depends on router handling)
      assert(response.code.code >= 400)

    finally
      // Cleanup
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      if parentDir != null && Files.exists(parentDir) then Files.delete(parentDir)
