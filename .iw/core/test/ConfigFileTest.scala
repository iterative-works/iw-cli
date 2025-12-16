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

      val result = ConfigSerializer.fromHocon(hocon)
      assert(result.isRight)
      val Right(config) = result: @unchecked

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
      val result = ConfigSerializer.fromHocon(hocon)
      assert(result.isRight)
      val Right(restored) = result: @unchecked

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

      val result = ConfigSerializer.fromHocon(hocon)
      assert(result.isRight)
      val Right(config) = result: @unchecked

      assertEquals(config.trackerType, IssueTrackerType.YouTrack)

  test("ConfigSerializer returns Left with meaningful message for invalid tracker type"):
    val hocon = """
      tracker {
        type = invalid
        team = TEST
      }

      project {
        name = myproject
      }
    """

    val result = ConfigSerializer.fromHocon(hocon)
    assert(result.isLeft)
    val Left(errorMsg) = result: @unchecked
    assert(errorMsg.contains("Unknown tracker type"))
    assert(errorMsg.contains("invalid"))

  test("ConfigSerializer returns Left with meaningful message for missing tracker type"):
    val hocon = """
      tracker {
        team = TEST
      }

      project {
        name = myproject
      }
    """

    val result = ConfigSerializer.fromHocon(hocon)
    assert(result.isLeft)
    val Left(errorMsg) = result: @unchecked
    assert(errorMsg.contains("Failed to parse config"))

  test("ConfigSerializer returns Left with meaningful message for missing tracker team"):
    val hocon = """
      tracker {
        type = linear
      }

      project {
        name = myproject
      }
    """

    val result = ConfigSerializer.fromHocon(hocon)
    assert(result.isLeft)
    val Left(errorMsg) = result: @unchecked
    assert(errorMsg.contains("Failed to parse config"))

  test("ConfigSerializer returns Left with meaningful message for missing project name"):
    val hocon = """
      tracker {
        type = linear
        team = TEST
      }

      project {
      }
    """

    val result = ConfigSerializer.fromHocon(hocon)
    assert(result.isLeft)
    val Left(errorMsg) = result: @unchecked
    assert(errorMsg.contains("Failed to parse config"))
