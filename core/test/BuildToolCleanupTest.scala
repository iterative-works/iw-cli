// PURPOSE: Unit tests for the pure BuildToolCleanup.detect decision function
// PURPOSE: Validates mapping from filesystem marker probes to ordered teardown commands

package iw.core.test

import iw.core.model.{BuildToolCleanup, BuildToolProbe}

class BuildToolCleanupTest extends munit.FunSuite:

  test("all false markers: no teardowns") {
    val probe = BuildToolProbe(
      millDaemon = false,
      millWrapper = false,
      bloopMarker = false,
      dockerCompose = false
    )
    assertEquals(BuildToolCleanup.detect(probe), Nil)
  }

  test("millDaemon without wrapper: shuts down via PATH-gated system mill") {
    val probe = BuildToolProbe(
      millDaemon = true,
      millWrapper = false,
      bloopMarker = false,
      dockerCompose = false
    )
    val teardowns = BuildToolCleanup.detect(probe)
    assertEquals(teardowns.size, 1)
    assertEquals(teardowns.head.tool, Some("mill"))
    assertEquals(
      teardowns.head.command,
      Seq("mill", "--no-server", "shutdown")
    )
  }

  test(
    "millDaemon with wrapper: shuts down via the worktree's ./mill, no PATH gate"
  ) {
    val probe = BuildToolProbe(
      millDaemon = true,
      millWrapper = true,
      bloopMarker = false,
      dockerCompose = false
    )
    val teardowns = BuildToolCleanup.detect(probe)
    assertEquals(teardowns.size, 1)
    // The vendored wrapper started the daemon and pins the Mill version, so it
    // is the executable that can shut it down. It is a known file in the
    // worktree, not a PATH lookup, hence no tool to gate on.
    assertEquals(teardowns.head.tool, None)
    assertEquals(
      teardowns.head.command,
      Seq("./mill", "--no-server", "shutdown")
    )
  }

  test("bloopMarker only: one bloop teardown") {
    val probe = BuildToolProbe(
      millDaemon = false,
      millWrapper = false,
      bloopMarker = true,
      dockerCompose = false
    )
    val teardowns = BuildToolCleanup.detect(probe)
    assertEquals(teardowns.size, 1)
    assertEquals(teardowns.head.tool, Some("bloop"))
    assertEquals(teardowns.head.command, Seq("bloop", "exit"))
  }

  test("dockerCompose only: one docker teardown") {
    val probe = BuildToolProbe(
      millDaemon = false,
      millWrapper = false,
      bloopMarker = false,
      dockerCompose = true
    )
    val teardowns = BuildToolCleanup.detect(probe)
    assertEquals(teardowns.size, 1)
    assertEquals(teardowns.head.tool, Some("docker"))
    assertEquals(teardowns.head.command, Seq("docker", "compose", "down"))
  }

  test("all true: three teardowns in Mill, Bloop, docker order") {
    val probe = BuildToolProbe(
      millDaemon = true,
      millWrapper = true,
      bloopMarker = true,
      dockerCompose = true
    )
    val teardowns = BuildToolCleanup.detect(probe)
    assertEquals(teardowns.size, 3)
    assertEquals(
      teardowns.map(_.tool),
      List(None, Some("bloop"), Some("docker"))
    )
  }
