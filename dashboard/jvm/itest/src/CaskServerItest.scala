// PURPOSE: Integration tests proving CaskServer routes and jar packaging are correct
// PURPOSE: Tests classpath-based asset resolution and fat-jar assembly for iw-dashboard.jar

package iw.dashboard.itest

import munit.FunSuite
import sttp.client4.quick.*
import java.nio.file.{Files, Paths}
import java.util.zip.ZipFile
import scala.util.Random
import scala.jdk.CollectionConverters.*
import iw.dashboard.CaskServer

class CaskServerItest extends FunSuite:

  // Helper: create isolated temp state path per test
  def createTempStatePath(): String =
    val tmpDir = System.getProperty("java.io.tmpdir")
    val randomId = Random.nextLong().abs
    s"$tmpDir/iw-itest-$randomId/state.json"

  // Helper: start CaskServer in a background daemon thread, wait until /health responds
  def startTestServer(statePath: String, port: Int): Thread =
    val serverThread = new Thread(() => {
      CaskServer.start(statePath, port)
    })
    serverThread.setDaemon(true)
    serverThread.start()

    val ready = (0 until 100).exists { _ =>
      val isReady =
        try
          quickRequest
            .get(uri"http://localhost:$port/health")
            .send()
            .code
            .code == 200
        catch case _: Exception => false
      if !isReady then Thread.sleep(100)
      isReady
    }

    if !ready then fail(s"Test server failed to start on port $port")
    serverThread

  // Helper: find a free ephemeral port
  def freePort(): Int =
    val s = new java.net.ServerSocket(0)
    val p = s.getLocalPort
    s.close()
    p

  // Helper: clean up state files
  def cleanup(statePath: String): Unit =
    val stateFile = Paths.get(statePath)
    if Files.exists(stateFile) then Files.delete(stateFile)
    val parentDir = stateFile.getParent
    Option(parentDir).filter(Files.exists(_)).foreach(Files.delete(_))

  // -----------------------------------------------------------------------
  // In-process HTTP tests
  // -----------------------------------------------------------------------

  test("GET / returns 200 with text/html content type"):
    val statePath = createTempStatePath()
    val port = freePort()
    try
      startTestServer(statePath, port)
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
    val port = freePort()
    try
      startTestServer(statePath, port)
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
    val port = freePort()
    try
      startTestServer(statePath, port)
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
    val port = freePort()
    try
      startTestServer(statePath, port)
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
    val port = freePort()
    try
      startTestServer(statePath, port)
      val response = quickRequest
        .get(uri"http://localhost:$port/static/nonexistent.css")
        .send()
      assertEquals(response.code.code, 404)
    finally cleanup(statePath)

  test("GET /assets/main.js returns 200 with application/javascript"):
    val statePath = createTempStatePath()
    val port = freePort()
    try
      startTestServer(statePath, port)
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
    val port = freePort()
    try
      startTestServer(statePath, port)
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
    val port = freePort()
    try
      startTestServer(statePath, port)
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
