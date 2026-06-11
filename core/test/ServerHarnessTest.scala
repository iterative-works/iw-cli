// PURPOSE: Harness tests for Server.run

package iw.core.test

import iw.core.commands.Server as ServerCmd
import iw.core.model.ServerConfig
import iw.core.test.fixtures.FakeCommandEnv

class ServerHarnessTest extends munit.FunSuite:

  private val configPath = "/home/u/.local/share/iw/server/config.json"
  private val pidPath = "/home/u/.local/share/iw/server/server.pid"

  private def baseEnv(): FakeCommandEnv =
    val env = FakeCommandEnv()
    env.envVars.setEnv("HOME", "/home/u")
    env.serverConfig.put(
      configPath,
      ServerConfig(port = 9876, hosts = List("localhost"))
    )
    env

  test("no args: usage on stderr, exit 1") {
    val env = baseEnv()

    val result = ServerCmd.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Usage"))
  }

  test("unknown subcommand: error + usage, exit 1") {
    val env = baseEnv()

    val result = ServerCmd.run(Seq("frobnicate"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Unknown command"))
  }

  test("start when already running: exits 1 with informational message") {
    val env = baseEnv()
    env.processLifecycle.seedPidFile(pidPath, 1234L)
    env.processLifecycle.markAlive(1234L)

    val result = ServerCmd.run(Seq("start"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stdout.contains("already running"))
    assertEquals(env.processLifecycle.spawnCallList, Nil)
  }

  test("start with stale pid file: removes pid, then spawns") {
    val env = baseEnv()
    env.processLifecycle.seedPidFile(pidPath, 999L)
    // 999 is NOT marked alive
    env.processLifecycle.setNextSpawnedPid(7777L)

    val result = ServerCmd.run(Seq("start"), env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("Removing stale PID file"))
    assertEquals(env.processLifecycle.spawnCallList.size, 1)
    assertEquals(env.processLifecycle.spawnCallList.head.port, 9876)
  }

  test("start happy path: spawns, writes pid, health-checks, announces") {
    val env = baseEnv()
    env.processLifecycle.setNextSpawnedPid(4242L)

    val result = ServerCmd.run(Seq("start"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.processLifecycle.writePidCallList.size, 1)
    assertEquals(env.processLifecycle.writePidCallList.head, (4242L, pidPath))
    assertEquals(env.processLifecycle.healthCallList.size, 1)
    assertEquals(
      env.processLifecycle.healthCallList.head._1,
      "http://localhost:9876/health"
    )
    assert(env.console.stdout.contains("Server started on localhost:9876"))
  }

  test("start: spawn failure exits 1 without health check") {
    val env = baseEnv()
    env.processLifecycle.setSpawnResult(Left("port in use"))

    val result = ServerCmd.run(Seq("start"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("port in use"))
    assertEquals(env.processLifecycle.healthCallList, Nil)
  }

  test("start: health check failure exits 1 with log hint") {
    val env = baseEnv()
    env.processLifecycle.setHealthy(false)

    val result = ServerCmd.run(Seq("start"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("health check failed"))
    assert(env.console.stderr.contains("Check logs"))
  }

  test("start: config read failure exits 1") {
    val env = baseEnv()
    env.serverConfig.setGetOrCreateResult(Left("disk corruption"))

    val result = ServerCmd.run(Seq("start"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Error reading config"))
  }

  test("stop when no pid file: graceful 'not running' exit 0") {
    val env = baseEnv()

    val result = ServerCmd.run(Seq("stop"), env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("Server is not running"))
    assertEquals(env.processLifecycle.stopCallList, Nil)
  }

  test("stop with stale pid: cleans up, exits 0") {
    val env = baseEnv()
    env.processLifecycle.seedPidFile(pidPath, 5555L)
    // 5555 not marked alive

    val result = ServerCmd.run(Seq("stop"), env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("stale PID file"))
    assertEquals(env.processLifecycle.removePidCallList, List(pidPath))
    assertEquals(env.processLifecycle.stopCallList, Nil)
  }

  test("stop happy path: calls stopProcess, removes pid") {
    val env = baseEnv()
    env.processLifecycle.seedPidFile(pidPath, 8888L)
    env.processLifecycle.markAlive(8888L)

    val result = ServerCmd.run(Seq("stop"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.processLifecycle.stopCallList, List((8888L, 10)))
    assert(env.processLifecycle.removePidCallList.contains(pidPath))
    assert(env.console.stdout.contains("Server stopped"))
  }

  test("stop failure: exit 1 with manual-kill hint") {
    val env = baseEnv()
    env.processLifecycle.seedPidFile(pidPath, 8888L)
    env.processLifecycle.markAlive(8888L)
    env.processLifecycle.setStopResult(Left("permission denied"))

    val result = ServerCmd.run(Seq("stop"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Failed to stop server"))
    assert(env.console.stderr.contains("manually kill"))
  }

  test("status when not running: exit 0 with message") {
    val env = baseEnv()

    val result = ServerCmd.run(Seq("status"), env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("Server is not running"))
  }

  test(
    "status: pid alive + JSON status: prints worktree count + uptime + PID"
  ) {
    val env = baseEnv()
    env.processLifecycle.seedPidFile(pidPath, 100L)
    env.processLifecycle.markAlive(100L)
    env.clock.setNow(
      java.time.Instant.parse("2026-06-10T10:00:00Z").toEpochMilli
    )
    env.processLifecycle.setFetchJsonResult(
      Right(
        """{"worktreeCount":3,"startedAt":"2026-06-10T09:30:00Z","hosts":["localhost"]}"""
      )
    )

    val result = ServerCmd.run(Seq("status"), env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("Tracking 3 worktrees"))
    assert(env.console.stdout.contains("PID: 100"))
  }

  test("status: HTTP failure: exit 0 with degraded message") {
    val env = baseEnv()
    env.processLifecycle.seedPidFile(pidPath, 200L)
    env.processLifecycle.markAlive(200L)
    env.processLifecycle.setFetchJsonResult(Left("connection refused"))

    val result = ServerCmd.run(Seq("status"), env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("status endpoint failed"))
  }
