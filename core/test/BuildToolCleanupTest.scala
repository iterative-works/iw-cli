// PURPOSE: Unit tests for the pure BuildToolCleanup.detect decision function
// PURPOSE: Validates mapping from filesystem marker probes to ordered teardown commands

package iw.core.test

import iw.core.model.{BuildToolCleanup, BuildToolProbe}

class BuildToolCleanupTest extends munit.FunSuite:

  test("all false markers: no teardowns") {
    val probe = BuildToolProbe(
      millDaemon = false,
      bloopMarker = false,
      dockerCompose = false
    )
    assertEquals(BuildToolCleanup.detect(probe), Nil)
  }

  test("millDaemon only: one mill teardown") {
    val probe = BuildToolProbe(
      millDaemon = true,
      bloopMarker = false,
      dockerCompose = false
    )
    val teardowns = BuildToolCleanup.detect(probe)
    assertEquals(teardowns.size, 1)
    assertEquals(teardowns.head.tool, "mill")
    assertEquals(
      teardowns.head.command,
      Seq("mill", "--no-server", "shutdown")
    )
  }

  test("bloopMarker only: one bloop teardown") {
    val probe = BuildToolProbe(
      millDaemon = false,
      bloopMarker = true,
      dockerCompose = false
    )
    val teardowns = BuildToolCleanup.detect(probe)
    assertEquals(teardowns.size, 1)
    assertEquals(teardowns.head.tool, "bloop")
    assertEquals(teardowns.head.command, Seq("bloop", "exit"))
  }

  test("dockerCompose only: one docker teardown") {
    val probe = BuildToolProbe(
      millDaemon = false,
      bloopMarker = false,
      dockerCompose = true
    )
    val teardowns = BuildToolCleanup.detect(probe)
    assertEquals(teardowns.size, 1)
    assertEquals(teardowns.head.tool, "docker")
    assertEquals(teardowns.head.command, Seq("docker", "compose", "down"))
  }

  test("all true: three teardowns in Mill, Bloop, docker order") {
    val probe = BuildToolProbe(
      millDaemon = true,
      bloopMarker = true,
      dockerCompose = true
    )
    val teardowns = BuildToolCleanup.detect(probe)
    assertEquals(teardowns.size, 3)
    assertEquals(teardowns.map(_.tool), List("mill", "bloop", "docker"))
  }
