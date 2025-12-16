// PURPOSE: Tests for HOCON configuration file serialization and deserialization
package iw.tests

// PURPOSE: Verifies configuration can be written to and read from HOCON format
import iw.core.*
import com.typesafe.config.ConfigFactory

class ConfigFileTest extends munit.FunSuite:

  val tempDir = FunFixture[os.Path](
    setup = { _ =>
      os.Path(java.nio.file.Files.createTempDirectory("iw-config-test"))
    },
    teardown = { dir =>
      os.remove.all(dir)
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
        projectName = "myproject",
        youtrackBaseUrl = Some("https://youtrack.example.com")
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
      assertEquals(config.youtrackBaseUrl, None)

  tempDir.test("ConfigSerializer reads YouTrack baseUrl when present"):
    tempDir =>
      val hocon = """
        tracker {
          type = youtrack
          team = TEST
          baseUrl = "https://youtrack.example.com"
        }

        project {
          name = myproject
        }
      """

      val result = ConfigSerializer.fromHocon(hocon)
      assert(result.isRight)
      val Right(config) = result: @unchecked

      assertEquals(config.trackerType, IssueTrackerType.YouTrack)
      assertEquals(config.youtrackBaseUrl, Some("https://youtrack.example.com"))

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
