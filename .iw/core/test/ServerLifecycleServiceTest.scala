// PURPOSE: Unit tests for ServerLifecycleService pure business logic
// PURPOSE: Validates uptime formatting and status creation

package iw.core.test

import iw.core.dashboard.ServerLifecycleService
import iw.core.model.ServerStatus
import iw.core.model.ServerState
import java.time.Instant
import java.time.temporal.ChronoUnit
import iw.core.model.WorktreeRegistration

class ServerLifecycleServiceTest extends munit.FunSuite:

  test("Format uptime for seconds only"):
    val startedAt = Instant.parse("2025-12-20T10:00:00Z")
    val now = startedAt.plus(45, ChronoUnit.SECONDS)

    val uptime = ServerLifecycleService.formatUptime(startedAt, now)
    assertEquals(uptime, "45s")

  test("Format uptime for minutes and seconds"):
    val startedAt = Instant.parse("2025-12-20T10:00:00Z")
    val now = startedAt.plus(5, ChronoUnit.MINUTES).plus(12, ChronoUnit.SECONDS)

    val uptime = ServerLifecycleService.formatUptime(startedAt, now)
    assertEquals(uptime, "5m 12s")

  test("Format uptime for hours and minutes"):
    val startedAt = Instant.parse("2025-12-20T10:00:00Z")
    val now = startedAt.plus(2, ChronoUnit.HOURS).plus(34, ChronoUnit.MINUTES)

    val uptime = ServerLifecycleService.formatUptime(startedAt, now)
    assertEquals(uptime, "2h 34m")

  test("Format uptime for hours only"):
    val startedAt = Instant.parse("2025-12-20T10:00:00Z")
    val now = startedAt.plus(3, ChronoUnit.HOURS)

    val uptime = ServerLifecycleService.formatUptime(startedAt, now)
    assertEquals(uptime, "3h 0m")

  test("Format uptime for days"):
    val startedAt = Instant.parse("2025-12-20T10:00:00Z")
    val now = startedAt.plus(25, ChronoUnit.HOURS)

    val uptime = ServerLifecycleService.formatUptime(startedAt, now)
    assertEquals(uptime, "1d 1h")

  test("Format uptime for zero duration"):
    val now = Instant.now()

    val uptime = ServerLifecycleService.formatUptime(now, now)
    assertEquals(uptime, "0s")

  test("Create status for running server"):
    val state = ServerState(Map.empty)
    val startedAt = Instant.parse("2025-12-20T10:00:00Z")
    val pid: Long = 12345L
    val port = 9876

    val status = ServerLifecycleService.createStatus(state, startedAt, pid, port)

    assert(status.running)
    assertEquals(status.port, port)
    assertEquals(status.worktreeCount, 0)
    assertEquals(status.startedAt, Some(startedAt))
    assertEquals(status.pid, Some(pid))

  test("Create status with worktrees"):
    // Create a state with worktrees
    val now = Instant.now()
    val registration1 = iw.core.domain.WorktreeRegistration(
      issueId = "IWLE-111",
      path = "/test/path1",
      trackerType = "Linear",
      team = "IWLE",
      registeredAt = now,
      lastSeenAt = now
    )
    val registration2 = iw.core.domain.WorktreeRegistration(
      issueId = "IWLE-222",
      path = "/test/path2",
      trackerType = "Linear",
      team = "IWLE",
      registeredAt = now,
      lastSeenAt = now
    )

    val state = ServerState(Map(
      "IWLE-111" -> registration1,
      "IWLE-222" -> registration2
    ))

    val startedAt = Instant.parse("2025-12-20T10:00:00Z")
    val pid: Long = 54321L
    val port = 8080

    val status = ServerLifecycleService.createStatus(state, startedAt, pid, port)

    assertEquals(status.worktreeCount, 2)

  test("Format status display with single host"):
    val hosts = Seq("localhost")
    val port = 9876

    val message = ServerLifecycleService.formatHostsDisplay(hosts, port)
    assertEquals(message, "Server running on localhost:9876")

  test("Format status display with multiple hosts"):
    val hosts = Seq("127.0.0.1", "10.0.0.1")
    val port = 8080

    val message = ServerLifecycleService.formatHostsDisplay(hosts, port)
    assertEquals(message, "Server running on 127.0.0.1:8080, 10.0.0.1:8080")

  test("Format status display with three hosts"):
    val hosts = Seq("localhost", "127.0.0.1", "192.168.1.5")
    val port = 9876

    val message = ServerLifecycleService.formatHostsDisplay(hosts, port)
    assertEquals(message, "Server running on localhost:9876, 127.0.0.1:9876, 192.168.1.5:9876")

  test("Format status display with empty hosts defaults to port"):
    val hosts = Seq.empty[String]
    val port = 9876

    val message = ServerLifecycleService.formatHostsDisplay(hosts, port)
    assertEquals(message, "Server running on port 9876")

  test("Format security warning returns None when no warning needed"):
    val analysis = iw.core.model.SecurityAnalysis(Seq.empty, false, false)
    val warning = ServerLifecycleService.formatSecurityWarning(analysis)
    assertEquals(warning, None)

  test("Format security warning for bind-all (0.0.0.0)"):
    val analysis = iw.core.model.SecurityAnalysis(Seq("0.0.0.0"), true, true)
    val warning = ServerLifecycleService.formatSecurityWarning(analysis)
    assert(warning.isDefined)
    assert(warning.get.contains("WARNING: Server is accessible from all network interfaces"))
    assert(warning.get.contains("0.0.0.0"))
    assert(warning.get.contains("Ensure your firewall is properly configured"))

  test("Format security warning for bind-all (::)"):
    val analysis = iw.core.model.SecurityAnalysis(Seq("::"), true, true)
    val warning = ServerLifecycleService.formatSecurityWarning(analysis)
    assert(warning.isDefined)
    assert(warning.get.contains("WARNING: Server is accessible from all network interfaces"))
    assert(warning.get.contains("::"))

  test("Format security warning for specific exposed hosts"):
    val analysis = iw.core.model.SecurityAnalysis(Seq("100.64.1.5"), false, true)
    val warning = ServerLifecycleService.formatSecurityWarning(analysis)
    assert(warning.isDefined)
    assert(warning.get.contains("WARNING: Server is accessible from non-localhost interfaces"))
    assert(warning.get.contains("100.64.1.5"))
    assert(warning.get.contains("Ensure your firewall is properly configured"))

  test("Format security warning lists multiple exposed hosts"):
    val analysis = iw.core.model.SecurityAnalysis(Seq("192.168.1.100", "10.0.0.1"), false, true)
    val warning = ServerLifecycleService.formatSecurityWarning(analysis)
    assert(warning.isDefined)
    assert(warning.get.contains("192.168.1.100"))
    assert(warning.get.contains("10.0.0.1"))
