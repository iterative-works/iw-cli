// PURPOSE: Integration tests for CaskServer HTTP endpoints
// PURPOSE: Tests registration endpoint with real StateRepository and JSON serialization

package iw.core.infrastructure

import munit.FunSuite
import sttp.client4.quick.*
import sttp.client4.Response
import java.nio.file.{Files, Paths}
import java.io.File
import scala.util.Random
import iw.core.model.{Issue, Check}
import iw.core.dashboard.{CaskServer, StateRepository}

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
      Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))

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
      Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))

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
      Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))

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
      Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))

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
      Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))

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
      Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))

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
      Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))

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
      Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))

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
      Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))

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
      Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))

  test("status endpoint includes hosts field with single host"):
    val statePath = createTempStatePath()
    val port = 9876
    val hosts = Seq("localhost")
    val startedAt = java.time.Instant.now()

    try
      val server = new CaskServer(statePath, port, hosts, startedAt)
      val statusJson = server.status()

      assert(statusJson.obj.contains("hosts"), "Status response should contain hosts field")
      assertEquals(statusJson("hosts").arr.size, 1)
      assertEquals(statusJson("hosts")(0).str, "localhost")

    finally
      // Cleanup
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))

  test("status endpoint includes hosts field with multiple hosts"):
    val statePath = createTempStatePath()
    val port = 9877
    val hosts = Seq("192.168.1.1", "10.0.0.1", "localhost")
    val startedAt = java.time.Instant.now()

    try
      val server = new CaskServer(statePath, port, hosts, startedAt)
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
      Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))

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
      Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))

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
      Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))

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
      Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))

  // Dev Mode Tests (IW-82 Phase 4)

  test("CaskServer constructor accepts devMode parameter"):
    val statePath = createTempStatePath()
    val port = 9999  // Use fixed port for unit test (not actually starting server)
    val hosts = Seq("localhost")
    val startedAt = java.time.Instant.now()

    try
      // Test devMode = true
      val serverWithDevMode = new CaskServer(statePath, port, hosts, startedAt, devMode = true)
      assert(Option(serverWithDevMode).isDefined, "Server should be created with devMode=true")

      // Test devMode = false (default)
      val serverWithoutDevMode = new CaskServer(statePath, port, hosts, startedAt, devMode = false)
      assert(Option(serverWithoutDevMode).isDefined, "Server should be created with devMode=false")

    finally
      // Cleanup
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))

  // Project worktree changes endpoint tests (IW-206 Phase 7)

  test("GET /api/projects/:projectName/worktrees/changes returns 200 with HTML"):
    val statePath = createTempStatePath()
    val port = findAvailablePort()

    try
      val serverThread = startTestServer(statePath, port)

      // Register a worktree whose path derives project name "iw-cli"
      val registerRequest = ujson.Obj(
        "path" -> "/test/projects/iw-cli-IW-123",
        "trackerType" -> "github",
        "team" -> "iterative-works/iw-cli"
      )
      quickRequest
        .put(uri"http://localhost:$port/api/v1/worktrees/IW-123")
        .body(ujson.write(registerRequest))
        .header("Content-Type", "application/json")
        .send()

      // Call project-scoped changes endpoint with no have param (client has nothing)
      val response = quickRequest
        .get(uri"http://localhost:$port/api/projects/iw-cli/worktrees/changes")
        .send()

      assertEquals(response.code.code, 200)
      assert(
        response.headers.exists { h =>
          h.name.equalsIgnoreCase("content-type") && h.value.contains("text/html")
        },
        "Response should have text/html content type"
      )

    finally
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))

  test("GET /api/projects/:projectName/worktrees/changes filters by project name"):
    val statePath = createTempStatePath()
    val port = findAvailablePort()

    try
      val serverThread = startTestServer(statePath, port)

      // Register worktree for project "iw-cli"
      val req1 = ujson.Obj(
        "path" -> "/test/projects/iw-cli-IW-100",
        "trackerType" -> "github",
        "team" -> "iterative-works/iw-cli"
      )
      quickRequest
        .put(uri"http://localhost:$port/api/v1/worktrees/IW-100")
        .body(ujson.write(req1))
        .header("Content-Type", "application/json")
        .send()

      // Register worktree for a different project "kanon"
      val req2 = ujson.Obj(
        "path" -> "/test/projects/kanon-IWLE-200",
        "trackerType" -> "linear",
        "team" -> "IWLE"
      )
      quickRequest
        .put(uri"http://localhost:$port/api/v1/worktrees/IWLE-200")
        .body(ujson.write(req2))
        .header("Content-Type", "application/json")
        .send()

      // Query changes for "iw-cli" only — client has nothing
      val response = quickRequest
        .get(uri"http://localhost:$port/api/projects/iw-cli/worktrees/changes")
        .send()

      assertEquals(response.code.code, 200)
      val body = response.body
      // Should include the iw-cli worktree card
      assert(body.contains("IW-100"), "Response should contain IW-100 (iw-cli project)")
      // Should NOT include the kanon worktree card
      assert(!body.contains("IWLE-200"), "Response should not contain IWLE-200 (kanon project)")

    finally
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))

  test("GET /api/projects/:projectName/worktrees/changes with have param detects no changes"):
    val statePath = createTempStatePath()
    val port = findAvailablePort()

    try
      val serverThread = startTestServer(statePath, port)

      // Register a worktree
      val registerRequest = ujson.Obj(
        "path" -> "/test/projects/iw-cli-IW-300",
        "trackerType" -> "github",
        "team" -> "iterative-works/iw-cli"
      )
      quickRequest
        .put(uri"http://localhost:$port/api/v1/worktrees/IW-300")
        .body(ujson.write(registerRequest))
        .header("Content-Type", "application/json")
        .send()

      // Client already has IW-300 — should detect no changes
      val response = quickRequest
        .get(uri"http://localhost:$port/api/projects/iw-cli/worktrees/changes?have=IW-300")
        .send()

      assertEquals(response.code.code, 200)
      val body = response.body
      // No changes detected — response should be empty or minimal
      assert(!body.contains("hx-swap-oob"), "No OOB swaps expected when client is up to date")

    finally
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))

  test("CaskServer.start() accepts devMode parameter"):
    val statePath = createTempStatePath()
    val port = findAvailablePort()
    try
      // Start server with devMode=true in background thread
      val serverThread = new Thread(() => {
        CaskServer.start(statePath, port, Seq("localhost"), devMode = true)
      })
      serverThread.setDaemon(true)
      serverThread.start()

      // Wait for server to be ready
      val serverStarted = (0 until 50).exists { _ =>
        val isReady = try
          val response = quickRequest.get(uri"http://localhost:$port/health").send()
          response.code.code == 200
        catch
          case _: Exception => false
        if !isReady then Thread.sleep(100)
        isReady
      }

      assert(serverStarted, "Server failed to start with devMode parameter")

    finally
      // Cleanup
      val stateFile = Paths.get(statePath)
      if Files.exists(stateFile) then Files.delete(stateFile)
      val parentDir = stateFile.getParent
      Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))
