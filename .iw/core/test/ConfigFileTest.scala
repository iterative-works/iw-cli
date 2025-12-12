// PURPOSE: Tests for HOCON configuration file serialization and deserialization
// PURPOSE: Verifies configuration can be written to and read from HOCON format

//> using scala 3.3.1
//> using dep org.scalameta::munit::1.0.0
//> using dep com.typesafe:config:1.4.3
//> using file "../Config.scala"

import iw.core.*
import java.nio.file.Files
import java.nio.file.Path
import com.typesafe.config.ConfigFactory

class ConfigFileTest extends munit.FunSuite:

  val tempDir = FunFixture[Path](
    setup = { _ =>
      Files.createTempDirectory("iw-config-test")
    },
    teardown = { dir =>
      Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
    }
  )

  tempDir.test("ProjectConfiguration serializes to HOCON"):
    tempDir =>
      val config = ProjectConfiguration(
        trackerType = IssueTrackerType.Linear,
        team = "IWLE",
        projectName = "kanon"
      )

      val hocon = ConfigSerializer.toHocon(config)

      assert(hocon.contains("tracker"))
      assert(hocon.contains("type = linear"))
      assert(hocon.contains("team = IWLE"))
      assert(hocon.contains("project"))
      assert(hocon.contains("name = kanon"))

  tempDir.test("ProjectConfiguration deserializes from HOCON"):
    tempDir =>
      val hocon = """
        tracker {
          type = linear
          team = IWLE
        }

        project {
          name = kanon
        }
      """

      val config = ConfigSerializer.fromHocon(hocon)

      assertEquals(config.trackerType, IssueTrackerType.Linear)
      assertEquals(config.team, "IWLE")
      assertEquals(config.projectName, "kanon")

  tempDir.test("ProjectConfiguration roundtrip preserves data"):
    tempDir =>
      val original = ProjectConfiguration(
        trackerType = IssueTrackerType.YouTrack,
        team = "TEST",
        projectName = "myproject"
      )

      val hocon = ConfigSerializer.toHocon(original)
      val restored = ConfigSerializer.fromHocon(hocon)

      assertEquals(restored, original)

  tempDir.test("ConfigSerializer handles YouTrack tracker type"):
    tempDir =>
      val hocon = """
        tracker {
          type = youtrack
          team = TEST
        }

        project {
          name = myproject
        }
      """

      val config = ConfigSerializer.fromHocon(hocon)

      assertEquals(config.trackerType, IssueTrackerType.YouTrack)

  test("ConfigSerializer fails on invalid tracker type"):
    val hocon = """
      tracker {
        type = invalid
        team = TEST
      }

      project {
        name = myproject
      }
    """

    intercept[IllegalArgumentException]:
      ConfigSerializer.fromHocon(hocon)

  test("ConfigSerializer fails on missing tracker type"):
    val hocon = """
      tracker {
        team = TEST
      }

      project {
        name = myproject
      }
    """

    intercept[com.typesafe.config.ConfigException]:
      ConfigSerializer.fromHocon(hocon)

  test("ConfigSerializer fails on missing tracker team"):
    val hocon = """
      tracker {
        type = linear
      }

      project {
        name = myproject
      }
    """

    intercept[com.typesafe.config.ConfigException]:
      ConfigSerializer.fromHocon(hocon)

  test("ConfigSerializer fails on missing project name"):
    val hocon = """
      tracker {
        type = linear
        team = TEST
      }

      project {
      }
    """

    intercept[com.typesafe.config.ConfigException]:
      ConfigSerializer.fromHocon(hocon)
