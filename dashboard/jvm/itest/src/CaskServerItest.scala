// PURPOSE: Integration tests proving CaskServer routes and jar packaging are correct
// PURPOSE: Tests classpath-based asset resolution and fat-jar assembly for iw-dashboard.jar

package iw.dashboard.itest

import munit.FunSuite
import sttp.client4.quick.*
import java.nio.file.{Files, Paths}
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipFile
import iw.dashboard.CaskServer

class CaskServerItest extends FunSuite:

  // Helper: create isolated temp state path per test. Files.createTempDirectory
  // provides collision-free uniqueness, so no extra random suffix is needed.
  private def createTempStatePath(): String =
    val dir = Files.createTempDirectory("iw-itest-")
    dir.resolve("state.json").toString

  // Helper: start CaskServer in a background daemon thread. Picks a free port
  // internally and retries on BindException to tolerate the TOCTOU window
  // between port probe and bind. Unexpected exceptions on the server thread are
  // captured and re-thrown here so the original stack trace surfaces instead of
  // a misleading generic "failed to start" message. Returns the actual bound
  // port so callers build their HTTP URLs against the port CaskServer.start
  // actually claimed.
  private def startTestServer(statePath: String): Int =
    val maxAttempts = 5
    var attempts = 0
    var boundPort = -1
    var lastErr: Throwable = null
    val startupError = new AtomicReference[Throwable](null)

    while boundPort < 0 && attempts < maxAttempts do
      val candidatePort = freePort()
      val serverThread = new Thread(() =>
        try CaskServer.start(statePath, candidatePort)
        catch
          case _: java.net.BindException => () // retriable, try next port
          case t: Throwable              => startupError.set(t)
      )
      serverThread.setDaemon(true)
      serverThread.start()

      val deadline = System.currentTimeMillis() + 10000L
      var ready = false
      while !ready && System.currentTimeMillis() < deadline
        && startupError.get == null
      do
        val isReady =
          try
            quickRequest
              .get(uri"http://localhost:$candidatePort/health")
              .send()
              .code
              .code == 200
          catch case _: Exception => false
        if isReady then ready = true
        else Thread.sleep(100)

      // Surface any unexpected server-thread failure with its original stack
      // trace. This prevents misleading "failed to start" timeouts that mask
      // config-parse errors, NPEs, or other bugs in CaskServer.start.
      Option(startupError.get).foreach(t => throw t)

      if ready then boundPort = candidatePort
      else
        attempts += 1
        lastErr = new RuntimeException(
          s"Test server failed to start on port $candidatePort"
        )

    if boundPort < 0 then
      fail(s"Test server failed to start after $maxAttempts attempts: $lastErr")
    boundPort

  // Helper: find a free ephemeral port. There is an inherent TOCTOU window
  // between close and the subsequent bind; startTestServer wraps callers in a
  // retry loop to handle transient collisions.
  private def freePort(): Int =
    val s = new java.net.ServerSocket(0)
    val p = s.getLocalPort
    s.close()
    p

  // Helper: issue a GET with percent-encoded path segments preserved on the
  // wire. sttp's uri"..." normalises %2F before sending, hiding the encoded
  // slash from the server. Going through HttpURLConnection with a pre-built
  // URI keeps the encoded bytes intact so Cask's URL-decoder surfaces a
  // decoded filename containing a literal '/', exercising the traversal guard.
  private def rawEncodedGet(port: Int, encodedPath: String): Int =
    val url = new java.net.URI(s"http://localhost:$port$encodedPath").toURL
    val conn = url.openConnection().asInstanceOf[java.net.HttpURLConnection]
    conn.setRequestMethod("GET")
    try conn.getResponseCode
    finally conn.disconnect()

  // Helper: clean up state files
  private def cleanup(statePath: String): Unit =
    val stateFile = Paths.get(statePath)
    if Files.exists(stateFile) then Files.delete(stateFile)
    val parentDir = stateFile.getParent
    Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))

  // -----------------------------------------------------------------------
  // In-process HTTP tests
  // -----------------------------------------------------------------------

  test("GET / returns 200 with text/html content type"):
    val statePath = createTempStatePath()
    try
      val port = startTestServer(statePath)
      val response = quickRequest.get(uri"http://localhost:$port/").send()
      assertEquals(response.code.code, 200)
      assert(
        response.headers.exists(h =>
          h.name.equalsIgnoreCase("content-type") && h.value.contains(
            "text/html"
          )
        ),
        "GET / should return text/html"
      )
    finally cleanup(statePath)

  test(
    "GET / body contains <html and references /static/dashboard.css and /assets/main.js"
  ):
    val statePath = createTempStatePath()
    try
      val port = startTestServer(statePath)
      val body = quickRequest.get(uri"http://localhost:$port/").send().body
      assert(body.contains("<html"), "body should contain <html")
      assert(
        body.contains("/static/dashboard.css"),
        "body should reference /static/dashboard.css"
      )
      assert(
        body.contains("/assets/main.js"),
        "body should reference /assets/main.js (Vite frontend bundle)"
      )
    finally cleanup(statePath)

  test("GET /static/dashboard.css returns 200 from classpath"):
    val statePath = createTempStatePath()
    try
      val port = startTestServer(statePath)
      val response = quickRequest
        .get(uri"http://localhost:$port/static/dashboard.css")
        .send()
      assertEquals(response.code.code, 200)
      assert(
        response.headers.exists(h =>
          h.name.equalsIgnoreCase("content-type") && h.value.contains(
            "text/css"
          )
        ),
        "/static/dashboard.css should have text/css content type"
      )
      assert(
        response.body.nonEmpty,
        "/static/dashboard.css should have non-empty body"
      )
    finally cleanup(statePath)

  test("GET /static/dashboard.js returns 200 from classpath"):
    val statePath = createTempStatePath()
    try
      val port = startTestServer(statePath)
      val response =
        quickRequest.get(uri"http://localhost:$port/static/dashboard.js").send()
      assertEquals(response.code.code, 200)
      assert(
        response.headers.exists(h =>
          h.name.equalsIgnoreCase("content-type") && h.value.contains(
            "application/javascript"
          )
        ),
        "/static/dashboard.js should have application/javascript content type"
      )
    finally cleanup(statePath)

  test("GET /static/nonexistent.css returns 404"):
    val statePath = createTempStatePath()
    try
      val port = startTestServer(statePath)
      val response = quickRequest
        .get(uri"http://localhost:$port/static/nonexistent.css")
        .send()
      assertEquals(response.code.code, 404)
    finally cleanup(statePath)

  test(
    "GET /static/..%2FMETA-INF%2FMANIFEST.MF returns 404 (path traversal rejected)"
  ):
    val statePath = createTempStatePath()
    try
      val port = startTestServer(statePath)
      val code = rawEncodedGet(port, "/static/..%2FMETA-INF%2FMANIFEST.MF")
      assertEquals(
        code,
        404,
        "path-traversal request on /static/ should be rejected as 404"
      )
    finally cleanup(statePath)

  test(
    "GET /assets/..%2FMETA-INF%2FMANIFEST.MF returns 404 (path traversal rejected)"
  ):
    val statePath = createTempStatePath()
    try
      val port = startTestServer(statePath)
      val code = rawEncodedGet(port, "/assets/..%2FMETA-INF%2FMANIFEST.MF")
      assertEquals(
        code,
        404,
        "path-traversal request on /assets/ should be rejected as 404"
      )
    finally cleanup(statePath)

  test("GET /assets/main.js returns 200 with application/javascript"):
    val statePath = createTempStatePath()
    try
      val port = startTestServer(statePath)
      val response =
        quickRequest.get(uri"http://localhost:$port/assets/main.js").send()
      assertEquals(
        response.code.code,
        200,
        "GET /assets/main.js should return 200"
      )
      assert(
        response.headers.exists(h =>
          h.name.equalsIgnoreCase("content-type") && h.value.contains(
            "application/javascript"
          )
        ),
        "/assets/main.js should have application/javascript content type"
      )
      assert(
        response.body.nonEmpty,
        "/assets/main.js should have non-empty body"
      )
    finally cleanup(statePath)

  test("GET /assets/main.css returns 200 with text/css"):
    val statePath = createTempStatePath()
    try
      val port = startTestServer(statePath)
      val response =
        quickRequest.get(uri"http://localhost:$port/assets/main.css").send()
      assertEquals(
        response.code.code,
        200,
        "GET /assets/main.css should return 200"
      )
      assert(
        response.headers.exists(h =>
          h.name.equalsIgnoreCase("content-type") && h.value.contains(
            "text/css"
          )
        ),
        "/assets/main.css should have text/css content type"
      )
      assert(
        response.body.nonEmpty,
        "/assets/main.css should have non-empty body"
      )
    finally cleanup(statePath)

  test("GET /assets/nonexistent.js returns 404"):
    val statePath = createTempStatePath()
    try
      val port = startTestServer(statePath)
      val response = quickRequest
        .get(uri"http://localhost:$port/assets/nonexistent.js")
        .send()
      assertEquals(response.code.code, 404)
    finally cleanup(statePath)

  // -----------------------------------------------------------------------
  // Jar-contents assertions — require build/iw-dashboard.jar to exist
  // -----------------------------------------------------------------------

  test(
    "build/iw-dashboard.jar has Main-Class: iw.dashboard.ServerDaemon in manifest"
  ):
    val jarPath = resolveJarPath()
    assume(
      Files.exists(jarPath),
      s"$jarPath not found — run ./mill iwDashboardJar first"
    )
    val zip = new ZipFile(jarPath.toFile)
    try
      val manifest = zip.getEntry("META-INF/MANIFEST.MF")
      assert(manifest != null, "MANIFEST.MF should exist in jar")
      val content = new String(zip.getInputStream(manifest).readAllBytes())
      assert(
        content.contains("Main-Class: iw.dashboard.ServerDaemon"),
        s"MANIFEST.MF should contain Main-Class: iw.dashboard.ServerDaemon, got:\n$content"
      )
    finally zip.close()

  test("build/iw-dashboard.jar contains assets/main.js (Vite JS bundle)"):
    val jarPath = resolveJarPath()
    assume(
      Files.exists(jarPath),
      s"$jarPath not found — run ./mill iwDashboardJar first"
    )
    val zip = new ZipFile(jarPath.toFile)
    try
      val entry = zip.getEntry("assets/main.js")
      assert(entry != null, "Jar should contain assets/main.js")
    finally zip.close()

  test("build/iw-dashboard.jar contains assets/main.css (Vite CSS bundle)"):
    val jarPath = resolveJarPath()
    assume(
      Files.exists(jarPath),
      s"$jarPath not found — run ./mill iwDashboardJar first"
    )
    val zip = new ZipFile(jarPath.toFile)
    try
      val entry = zip.getEntry("assets/main.css")
      assert(entry != null, "Jar should contain assets/main.css")
    finally zip.close()

  test("build/iw-dashboard.jar contains static/dashboard.css"):
    val jarPath = resolveJarPath()
    assume(
      Files.exists(jarPath),
      s"$jarPath not found — run ./mill iwDashboardJar first"
    )
    val zip = new ZipFile(jarPath.toFile)
    try
      val entry = zip.getEntry("static/dashboard.css")
      assert(entry != null, "Jar should contain static/dashboard.css")
    finally zip.close()

  test("build/iw-dashboard.jar contains static/dashboard.js"):
    val jarPath = resolveJarPath()
    assume(
      Files.exists(jarPath),
      s"$jarPath not found — run ./mill iwDashboardJar first"
    )
    val zip = new ZipFile(jarPath.toFile)
    try
      val entry = zip.getEntry("static/dashboard.js")
      assert(entry != null, "Jar should contain static/dashboard.js")
    finally zip.close()

  // -----------------------------------------------------------------------
  // Helper
  // -----------------------------------------------------------------------

  /** Resolve the path to build/iw-dashboard.jar by walking up from cwd to find
    * the project root.
    */
  private def resolveJarPath(): java.nio.file.Path =
    val cwd = Paths.get(System.getProperty("user.dir"))
    val root = Iterator
      .iterate(cwd)(_.getParent)
      .take(10)
      .find(p => Files.exists(p.resolve("build.mill")))
      .getOrElse(cwd)
    root.resolve("build/iw-dashboard.jar")
