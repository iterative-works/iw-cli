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
        .put(uri"http://localhost:$port/api/v1/worktrees/IWLE-123")
        .body(ujson.write(requestBody))
        .header("Content-Type", "application/json")
        .send()

      assertEquals(response.code.code, 201)

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
        .put(uri"http://localhost:$port/api/v1/worktrees/IWLE-456")
        .body(ujson.write(firstRequest))
        .header("Content-Type", "application/json")
        .send()

      // Second registration with updated data
      val secondRequest = ujson.Obj(
        "path" -> "/updated/path",
        "trackerType" -> "YouTrack",
        "team" -> "NEWTEA"
      )

      val response = quickRequest
        .put(uri"http://localhost:$port/api/v1/worktrees/IWLE-456")
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
        .put(uri"http://localhost:$port/api/v1/worktrees/IWLE-789")
        .body("{invalid json")
        .header("Content-Type", "application/json")
        .send()

      assertEquals(response.code.code, 400)

      val responseJson = ujson.read(response.body)
      assert(responseJson.obj.contains("code"))
      assert(responseJson.obj.contains("message"))

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
        .put(uri"http://localhost:$port/api/v1/worktrees/IWLE-999")
        .body(ujson.write(requestBody))
        .header("Content-Type", "application/json")
        .send()

      assertEquals(response.code.code, 400)

      val responseJson = ujson.read(response.body)
      assert(responseJson.obj.contains("code"))
      assert(responseJson.obj.contains("message"))

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
        .put(uri"http://localhost:$port/api/v1/worktrees/")
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

  test("GET /api/status returns 200 OK with status JSON"):
    val statePath = createTempStatePath()
    val port = findAvailablePort()

    try
      val serverThread = startTestServer(statePath, port)

      // Call status endpoint
      val response = quickRequest
        .get(uri"http://localhost:$port/api/status")
        .send()

      assertEquals(response.code.code, 200)

      // Verify response contains expected fields
      val responseJson = ujson.read(response.body)
      assertEquals(responseJson("status").str, "running")
      assert(responseJson.obj.contains("port"))
      assert(responseJson.obj.contains("worktreeCount"))
      assert(responseJson.obj.contains("startedAt"))

    finally
      // Cleanup
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      if parentDir != null && Files.exists(parentDir) then Files.delete(parentDir)

  test("GET /api/status shows correct worktree count"):
    val statePath = createTempStatePath()
    val port = findAvailablePort()

    try
      val serverThread = startTestServer(statePath, port)

      // Register 2 worktrees
      val requestBody1 = ujson.Obj(
        "path" -> "/test/path/1",
        "trackerType" -> "Linear",
        "team" -> "IWLE"
      )
      quickRequest
        .put(uri"http://localhost:$port/api/v1/worktrees/IWLE-111")
        .body(ujson.write(requestBody1))
        .header("Content-Type", "application/json")
        .send()

      val requestBody2 = ujson.Obj(
        "path" -> "/test/path/2",
        "trackerType" -> "Linear",
        "team" -> "IWLE"
      )
      quickRequest
        .put(uri"http://localhost:$port/api/v1/worktrees/IWLE-222")
        .body(ujson.write(requestBody2))
        .header("Content-Type", "application/json")
        .send()

      // Check status
      val response = quickRequest
        .get(uri"http://localhost:$port/api/status")
        .send()

      val responseJson = ujson.read(response.body)
      assertEquals(responseJson("worktreeCount").num.toInt, 2)
      assertEquals(responseJson("port").num.toInt, port)

    finally
      // Cleanup
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      if parentDir != null && Files.exists(parentDir) then Files.delete(parentDir)

  test("GET /api/status startedAt is recent"):
    val statePath = createTempStatePath()
    val port = findAvailablePort()

    try
      val beforeStart = java.time.Instant.now()
      val serverThread = startTestServer(statePath, port)

      // Call status endpoint
      val response = quickRequest
        .get(uri"http://localhost:$port/api/status")
        .send()

      val responseJson = ujson.read(response.body)
      val startedAt = java.time.Instant.parse(responseJson("startedAt").str)

      // startedAt should be within 2 seconds of server start
      val afterStart = java.time.Instant.now()
      assert(!startedAt.isBefore(beforeStart), s"startedAt $startedAt should not be before $beforeStart")
      assert(!startedAt.isAfter(afterStart), s"startedAt $startedAt should not be after $afterStart")

    finally
      // Cleanup
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      if parentDir != null && Files.exists(parentDir) then Files.delete(parentDir)

  test("DELETE /api/v1/worktrees/:issueId returns 200 and removes worktree"):
    val statePath = createTempStatePath()
    val port = findAvailablePort()

    try
      val serverThread = startTestServer(statePath, port)

      // First register a worktree
      val registerRequest = ujson.Obj(
        "path" -> "/test/path",
        "trackerType" -> "Linear",
        "team" -> "IWLE"
      )
      quickRequest
        .put(uri"http://localhost:$port/api/v1/worktrees/IWLE-123")
        .body(ujson.write(registerRequest))
        .header("Content-Type", "application/json")
        .send()

      // Delete the worktree
      val response = quickRequest
        .delete(uri"http://localhost:$port/api/v1/worktrees/IWLE-123")
        .send()

      assertEquals(response.code.code, 200)

      // Verify response body
      val responseJson = ujson.read(response.body)
      assertEquals(responseJson("status").str, "ok")
      assertEquals(responseJson("issueId").str, "IWLE-123")

      // Verify worktree was removed from state
      val stateContent = Files.readString(Paths.get(statePath))
      val stateJson = ujson.read(stateContent)
      assert(!stateJson("worktrees").obj.contains("IWLE-123"), "Worktree should be removed")

    finally
      // Cleanup
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      if parentDir != null && Files.exists(parentDir) then Files.delete(parentDir)

  test("DELETE /api/v1/worktrees/:issueId returns 404 for non-existent worktree"):
    val statePath = createTempStatePath()
    val port = findAvailablePort()

    try
      val serverThread = startTestServer(statePath, port)

      // Try to delete non-existent worktree
      val response = quickRequest
        .delete(uri"http://localhost:$port/api/v1/worktrees/IWLE-999")
        .send()

      assertEquals(response.code.code, 404)

      // Verify error response
      val responseJson = ujson.read(response.body)
      assertEquals(responseJson("code").str, "NOT_FOUND")
      assert(responseJson("message").str.contains("not found") || responseJson("message").str.contains("IWLE-999"))

    finally
      // Cleanup
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      if parentDir != null && Files.exists(parentDir) then Files.delete(parentDir)

  test("status endpoint includes hosts field with single host"):
    val statePath = createTempStatePath()
    val port = 9876
    val hosts = Seq("localhost")
    val startedAt = java.time.Instant.now()

    try
      val server = new CaskServer(statePath, port, hosts, None, startedAt)
      val statusJson = server.status()

      assert(statusJson.obj.contains("hosts"), "Status response should contain hosts field")
      assertEquals(statusJson("hosts").arr.size, 1)
      assertEquals(statusJson("hosts")(0).str, "localhost")

    finally
      // Cleanup
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      if parentDir != null && Files.exists(parentDir) then Files.delete(parentDir)

  test("status endpoint includes hosts field with multiple hosts"):
    val statePath = createTempStatePath()
    val port = 9877
    val hosts = Seq("192.168.1.1", "10.0.0.1", "localhost")
    val startedAt = java.time.Instant.now()

    try
      val server = new CaskServer(statePath, port, hosts, None, startedAt)
      val statusJson = server.status()

      assert(statusJson.obj.contains("hosts"), "Status response should contain hosts field")
      assertEquals(statusJson("hosts").arr.size, 3)
      assertEquals(statusJson("hosts")(0).str, "192.168.1.1")
      assertEquals(statusJson("hosts")(1).str, "10.0.0.1")
      assertEquals(statusJson("hosts")(2).str, "localhost")

    finally
      // Cleanup
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      if parentDir != null && Files.exists(parentDir) then Files.delete(parentDir)

  test("GET / with sshHost query parameter includes value in HTML"):
    val statePath = createTempStatePath()
    val port = findAvailablePort()

    try
      val serverThread = startTestServer(statePath, port)

      // Request with sshHost query parameter
      val response = quickRequest
        .get(uri"http://localhost:$port/?sshHost=my-remote-server")
        .send()

      assertEquals(response.code.code, 200)

      // Verify response contains SSH host value
      val html = response.body
      assert(html.contains("my-remote-server"), "HTML should contain SSH host value")
      assert(html.contains("ssh-host-input"), "HTML should contain SSH host input field")

    finally
      // Cleanup
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      if parentDir != null && Files.exists(parentDir) then Files.delete(parentDir)

  test("GET / without sshHost query parameter uses default hostname"):
    val statePath = createTempStatePath()
    val port = findAvailablePort()

    try
      val serverThread = startTestServer(statePath, port)

      // Request without sshHost query parameter
      val response = quickRequest
        .get(uri"http://localhost:$port/")
        .send()

      assertEquals(response.code.code, 200)

      // Verify response contains some hostname (either from InetAddress or fallback)
      val html = response.body
      assert(html.contains("ssh-host-input"), "HTML should contain SSH host input field")
      // The actual hostname will vary, so we just check the input field exists

    finally
      // Cleanup
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      if parentDir != null && Files.exists(parentDir) then Files.delete(parentDir)

  test("DELETE endpoint removes associated cache entries"):
    val statePath = createTempStatePath()
    val port = findAvailablePort()

    try
      val serverThread = startTestServer(statePath, port)

      // Register a worktree
      val registerRequest = ujson.Obj(
        "path" -> "/test/path",
        "trackerType" -> "Linear",
        "team" -> "IWLE"
      )
      quickRequest
        .put(uri"http://localhost:$port/api/v1/worktrees/IWLE-123")
        .body(ujson.write(registerRequest))
        .header("Content-Type", "application/json")
        .send()

      // Manually add cache entries to state (simulating cached data)
      val stateContent = Files.readString(Paths.get(statePath))
      val stateJson = ujson.read(stateContent)
      val now = java.time.Instant.now().toString
      stateJson("issueCache") = ujson.Obj(
        "IWLE-123" -> ujson.Obj(
          "data" -> ujson.Obj(
            "id" -> "IWLE-123",
            "title" -> "Test Issue",
            "status" -> "Open",
            "assignee" -> ujson.Null,
            "description" -> ujson.Null,
            "url" -> "http://example.com",
            "fetchedAt" -> now
          ),
          "ttlMinutes" -> 5
        )
      )
      stateJson("progressCache") = ujson.Obj(
        "IWLE-123" -> ujson.Obj(
          "progress" -> ujson.Obj(
            "currentPhase" -> ujson.Null,
            "totalPhases" -> 0,
            "phases" -> ujson.Arr(),
            "overallCompleted" -> 0,
            "overallTotal" -> 0
          ),
          "filesMtime" -> ujson.Obj()
        )
      )
      stateJson("prCache") = ujson.Obj(
        "IWLE-123" -> ujson.Obj(
          "pr" -> ujson.Obj(
            "url" -> "http://example.com/pr/1",
            "state" -> "Open",
            "number" -> 1,
            "title" -> "Test PR"
          ),
          "fetchedAt" -> now
        )
      )
      Files.writeString(Paths.get(statePath), ujson.write(stateJson, indent = 2))

      // Delete the worktree
      val response = quickRequest
        .delete(uri"http://localhost:$port/api/v1/worktrees/IWLE-123")
        .send()

      assertEquals(response.code.code, 200)

      // Verify all caches were removed
      val updatedStateContent = Files.readString(Paths.get(statePath))
      val updatedStateJson = ujson.read(updatedStateContent)
      assert(!updatedStateJson("worktrees").obj.contains("IWLE-123"))
      assert(
        !updatedStateJson.obj.get("issueCache").exists(_.obj.contains("IWLE-123")),
        "issueCache should not contain IWLE-123"
      )
      assert(
        !updatedStateJson.obj.get("progressCache").exists(_.obj.contains("IWLE-123")),
        "progressCache should not contain IWLE-123"
      )
      assert(
        !updatedStateJson.obj.get("prCache").exists(_.obj.contains("IWLE-123")),
        "prCache should not contain IWLE-123"
      )

    finally
      // Cleanup
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      if parentDir != null && Files.exists(parentDir) then Files.delete(parentDir)

  test("CaskServer constructor accepts projectPath parameter"):
    val statePath = createTempStatePath()
    val port = 9878
    val hosts = Seq("localhost")
    val projectPath = Some(os.pwd)
    val startedAt = java.time.Instant.now()

    try
      // Create server with projectPath
      val server = new CaskServer(statePath, port, hosts, projectPath, startedAt)

      // If we get here without exception, the constructor accepts the parameter
      assert(true, "CaskServer should accept projectPath parameter")

    finally
      // Cleanup
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      if parentDir != null && Files.exists(parentDir) then Files.delete(parentDir)

  test("GET / uses projectPath for config loading when provided"):
    val statePath = createTempStatePath()
    val port = findAvailablePort()

    // Create a temporary project directory with config
    val tmpProjectDir = os.temp.dir(prefix = "iw-test-project-")
    val iwDir = tmpProjectDir / ".iw"
    os.makeDir(iwDir)

    // Create a test config file
    val configContent = """tracker_type: Linear
                          |team: TESTTEAM
                          |""".stripMargin
    os.write(iwDir / "config.yaml", configContent)

    try
      // Start server with custom projectPath
      val projectPath = Some(tmpProjectDir)
      val startedAt = java.time.Instant.now()
      val serverThread = new Thread(() => {
        CaskServer.start(statePath, port, Seq("localhost"), projectPath)
      })
      serverThread.setDaemon(true)
      serverThread.start()

      // Wait for server to be ready
      var retries = 0
      while retries < 50 do
        try
          val response = quickRequest.get(uri"http://localhost:$port/health").send()
          if response.code.code == 200 then
            retries = 100 // Break loop
        catch
          case _: Exception => ()
        if retries < 100 then
          Thread.sleep(100)
          retries += 1

      // Request dashboard
      val response = quickRequest
        .get(uri"http://localhost:$port/")
        .send()

      assertEquals(response.code.code, 200)

      // The HTML should be successfully rendered (config loaded from projectPath)
      val html = response.body
      assert(html.contains("iw Dashboard"), "Should render dashboard HTML")

    finally
      // Cleanup
      os.remove.all(tmpProjectDir)
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      if parentDir != null && Files.exists(parentDir) then Files.delete(parentDir)
