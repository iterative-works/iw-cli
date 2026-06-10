// PURPOSE: Harness tests for Dashboard.run

package iw.core.test

import iw.core.commands.Dashboard
import iw.core.model.ServerConfig
import iw.core.test.fixtures.FakeCommandEnv

class DashboardHarnessTest extends munit.FunSuite:

  private def baseEnv(): FakeCommandEnv =
    val env = FakeCommandEnv()
    env.envVars.setEnv("IW_DASHBOARD_JAR", "/dashboard/server.jar")
    env.envVars.setEnv("HOME", "/home/user")
    env

  test("missing IW_DASHBOARD_JAR: exit 1") {
    val env = FakeCommandEnv()
    env.envVars.setEnv("HOME", "/home/user")

    val result = Dashboard.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("IW_DASHBOARD_JAR not set"))
  }

  test("missing HOME: exit 1") {
    val env = FakeCommandEnv()
    env.envVars.setEnv("IW_DASHBOARD_JAR", "/jar")

    val result = Dashboard.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("HOME environment variable not set"))
  }

  test("unknown arg: exit 1 with usage hint") {
    val env = baseEnv()

    val result = Dashboard.run(Seq("--bogus"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Unknown argument"))
  }

  test("--help: exit 0 prints usage banner") {
    val env = baseEnv()

    val result = Dashboard.run(Seq("--help"), env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("Start the iw dashboard server"))
  }

  test("server already running: opens browser, returns ok") {
    val env = baseEnv()
    env.dashboard.setServerRunning(true)
    env.serverConfig.put(
      "/home/user/.local/share/iw/server/config.json",
      ServerConfig(port = 9000, hosts = List("localhost"))
    )

    val result = Dashboard.run(Seq.empty, env)

    assertEquals(result.exitCode, 0)
    assert(
      env.console.stdout.contains("already running at http://localhost:9000")
    )
    assertEquals(env.dashboard.browserCallList, List("http://localhost:9000"))
    assertEquals(env.dashboard.startCallList, Nil)
  }

  test(
    "happy path: starts server, opens browser on ready, returns server exit"
  ) {
    val env = baseEnv()
    env.dashboard.setServerRunning(false)
    env.dashboard.setStartResult(Right(0))
    env.serverConfig.put(
      "/home/user/.local/share/iw/server/config.json",
      ServerConfig(port = 8080, hosts = List("localhost"))
    )

    val result = Dashboard.run(Seq.empty, env)

    assertEquals(result.exitCode, 0)
    val startCalls = env.dashboard.startCallList
    assertEquals(startCalls.size, 1)
    assertEquals(startCalls.head.healthUrl, "http://localhost:8080/health")
    val cmd = startCalls.head.cmd
    assertEquals(cmd.head, "java")
    assert(cmd.contains("/dashboard/server.jar"))
    assert(cmd.contains("8080"))
    // onReady fires browser open
    assertEquals(env.dashboard.browserCallList, List("http://localhost:8080"))
  }

  test("server startup failure: exit 1 with error") {
    val env = baseEnv()
    env.dashboard.setStartResult(Left("Server failed to start within timeout"))
    env.serverConfig.put(
      "/home/user/.local/share/iw/server/config.json",
      ServerConfig(port = 8080, hosts = List("localhost"))
    )

    val result = Dashboard.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Server failed to start within timeout"))
    assertEquals(env.dashboard.browserCallList, Nil)
  }

  test("--sample-data triggers runSync for SampleDataCli first") {
    val env = baseEnv()
    env.dashboard.setStartResult(Right(0))
    env.serverConfig.put(
      "/home/user/.local/share/iw/server/config.json",
      ServerConfig(port = 8080, hosts = List("localhost"))
    )

    val result = Dashboard.run(Seq("--sample-data"), env)

    assertEquals(result.exitCode, 0)
    val sampleCmds = env.dashboard.syncCallList
    assertEquals(sampleCmds.size, 1)
    assert(sampleCmds.head.contains("iw.dashboard.SampleDataCli"))
    assert(env.console.stdout.contains("Generating sample data"))
  }

  test("--sample-data failure: exit 1, no server start") {
    val env = baseEnv()
    env.dashboard.setSyncResult(1)

    val result = Dashboard.run(Seq("--sample-data"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Sample data generation failed"))
    assertEquals(env.dashboard.startCallList, Nil)
  }

  test("--state-path overrides default; flagged 'Using state file' note") {
    val env = baseEnv()
    env.dashboard.setStartResult(Right(0))
    env.serverConfig.put(
      "/home/user/.local/share/iw/server/config.json",
      ServerConfig(port = 8080, hosts = List("localhost"))
    )

    val result = Dashboard.run(Seq("--state-path", "/tmp/custom.json"), env)

    assertEquals(result.exitCode, 0)
    val cmd = env.dashboard.startCallList.head.cmd
    assert(cmd.contains("/tmp/custom.json"))
    assert(env.console.stdout.contains("Using state file: /tmp/custom.json"))
  }

  test("--dev creates temp dir + writes config + uses available port") {
    val env = baseEnv()
    env.clock.setNow(1234567890L)
    env.dashboard.setAvailablePort(54321)
    env.dashboard.setStartResult(Right(0))

    val result = Dashboard.run(Seq("--dev"), env)

    assertEquals(result.exitCode, 0)
    // Dev dir is created
    assert(
      env.fs.directoryList.exists(_.toString.contains("iw-dev-1234567890"))
    )
    // Server config is written to dev config path
    val writes = env.serverConfig.writeCallList
    assertEquals(writes.size, 1)
    assert(writes.head._1.contains("iw-dev-1234567890/config.json"))
    assertEquals(writes.head._2.port, 54321)
    // Server starts (sample data forced on by --dev)
    assert(env.console.stdout.contains("Sample data: enabled"))
    assertEquals(env.dashboard.syncCallList.size, 1) // sample data ran
  }

  test(
    "--dev with explicit --state-path: explicit wins for state but dev config is still written"
  ) {
    val env = baseEnv()
    env.clock.setNow(1L)
    env.dashboard.setStartResult(Right(0))

    val result =
      Dashboard.run(Seq("--dev", "--state-path", "/tmp/my.json"), env)

    assertEquals(result.exitCode, 0)
    val cmd = env.dashboard.startCallList.head.cmd
    assert(cmd.contains("/tmp/my.json"))
    assert(cmd.contains("--dev"))
  }

  test("serverConfig getOrCreate failure: exit 1") {
    val env = baseEnv()
    env.serverConfig.setGetOrCreateResult(Left("disk read failed"))

    val result = Dashboard.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Failed to read config"))
  }
