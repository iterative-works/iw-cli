// PURPOSE: Harness tests for Version.run
// PURPOSE: Asserts stdout for compact + verbose modes against in-VM FakeCommandEnv

package iw.core.test

import iw.core.commands.Version
import iw.core.test.fixtures.FakeCommandEnv

class VersionHarnessTest extends munit.FunSuite:

  private val info = Version.Info(
    version = "0.6.0",
    osName = "Linux",
    osArch = "amd64",
    javaVersion = "21.0.4"
  )

  test("compact: prints 'iw-cli version <v>'") {
    val env = FakeCommandEnv()

    val result = Version.run(Seq.empty, env, info)

    assertEquals(result.exitCode, 0)
    assertEquals(env.console.stdout, "iw-cli version 0.6.0")
  }

  test(
    "--verbose: prints section header and key/value lines including version"
  ) {
    val env = FakeCommandEnv()

    val result = Version.run(Seq("--verbose"), env, info)

    assertEquals(result.exitCode, 0)
    val out = env.console.stdout
    assert(out.contains("=== iw-cli Version Information ==="))
    assert(out.contains("Version") && out.contains("0.6.0"))
    assert(out.contains("OS") && out.contains("Linux"))
    assert(out.contains("Architecture") && out.contains("amd64"))
    assert(out.contains("Java") && out.contains("21.0.4"))
  }
