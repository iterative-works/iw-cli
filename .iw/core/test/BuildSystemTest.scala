// PURPOSE: Unit tests for build system detection logic
// PURPOSE: Verifies Mill, SBT, scala-cli, and Unknown detection cases

package iw.core.test

import iw.core.model.BuildSystem

class BuildSystemTest extends munit.FunSuite:
  test("detect Mill when build.mill exists"):
    val fileExists = (path: os.Path) => path.last == "build.mill"
    val result = BuildSystem.detectWith(fileExists)
    assertEquals(result, BuildSystem.Mill)

  test("detect SBT when build.sbt exists"):
    val fileExists = (path: os.Path) => path.last == "build.sbt"
    val result = BuildSystem.detectWith(fileExists)
    assertEquals(result, BuildSystem.SBT)

  test("detect ScalaCli when project.scala exists"):
    val fileExists = (path: os.Path) => path.last == "project.scala"
    val result = BuildSystem.detectWith(fileExists)
    assertEquals(result, BuildSystem.ScalaCli)

  test("detect Unknown when no build files exist"):
    val fileExists = (path: os.Path) => false
    val result = BuildSystem.detectWith(fileExists)
    assertEquals(result, BuildSystem.Unknown)

  test("prioritize Mill over SBT when both exist"):
    val fileExists = (path: os.Path) =>
      path.last == "build.mill" || path.last == "build.sbt"
    val result = BuildSystem.detectWith(fileExists)
    assertEquals(result, BuildSystem.Mill)

  test("prioritize SBT over ScalaCli when both exist"):
    val fileExists = (path: os.Path) =>
      path.last == "build.sbt" || path.last == "project.scala"
    val result = BuildSystem.detectWith(fileExists)
    assertEquals(result, BuildSystem.SBT)

  test("prioritize Mill over all when all exist"):
    val fileExists = (path: os.Path) =>
      path.last == "build.mill" || path.last == "build.sbt" || path.last == "project.scala"
    val result = BuildSystem.detectWith(fileExists)
    assertEquals(result, BuildSystem.Mill)
