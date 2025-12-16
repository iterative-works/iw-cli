// PURPOSE: Tests for configuration file repository
package iw.tests

// PURPOSE: Verifies ConfigFileRepository can write and read HOCON config files
import iw.core.*

class ConfigRepositoryTest extends munit.FunSuite, Fixtures:

  tempDir.test("ConfigFileRepository writes config to file"):
    dir =>
      val config = ProjectConfiguration(
        trackerType = IssueTrackerType.Linear,
        team = "IWLE",
        projectName = "kanon"
      )

      val configPath = dir / ".iw" / "config.conf"
      ConfigFileRepository.write(configPath, config)

      assert(os.exists(configPath))
      val content = os.read(configPath)
      assert(content.contains("tracker"))
      assert(content.contains("type = linear"))
      assert(content.contains("team = IWLE"))

  tempDir.test("ConfigFileRepository reads config from file"):
    dir =>
      val configPath = dir / ".iw" / "config.conf"
      os.makeDir.all(configPath / os.up)
      os.write(configPath, """
        tracker {
          type = youtrack
          team = TEST
        }

        project {
          name = myproject
        }
      """)

      val config = ConfigFileRepository.read(configPath)

      assert(config.isDefined)
      assertEquals(config.get.trackerType, IssueTrackerType.YouTrack)
      assertEquals(config.get.team, "TEST")
      assertEquals(config.get.projectName, "myproject")

  tempDir.test("ConfigFileRepository roundtrip preserves data"):
    dir =>
      val original = ProjectConfiguration(
        trackerType = IssueTrackerType.Linear,
        team = "IWLE",
        projectName = "kanon"
      )

      val configPath = dir / ".iw" / "config.conf"
      ConfigFileRepository.write(configPath, original)
      val restored = ConfigFileRepository.read(configPath)

      assert(restored.isDefined)
      assertEquals(restored.get, original)

  tempDir.test("ConfigFileRepository returns None for non-existent file"):
    dir =>
      val configPath = dir / ".iw" / "config.conf"
      val config = ConfigFileRepository.read(configPath)

      assertEquals(config, None)

  tempDir.test("ConfigFileRepository creates parent directories when writing"):
    dir =>
      val config = ProjectConfiguration(
        trackerType = IssueTrackerType.Linear,
        team = "IWLE",
        projectName = "kanon"
      )

      val configPath = dir / "deep" / "nested" / "config.conf"
      ConfigFileRepository.write(configPath, config)

      assert(os.exists(configPath))
      assert(os.exists(configPath / os.up))
      assert(os.exists(configPath / os.up / os.up))

  tempDir.test("ConfigFileRepository writes config with version field"):
    dir =>
      val config = ProjectConfiguration(
        trackerType = IssueTrackerType.Linear,
        team = "IWLE",
        projectName = "kanon",
        version = Some("0.1.0")
      )

      val configPath = dir / ".iw" / "config.conf"
      ConfigFileRepository.write(configPath, config)

      assert(os.exists(configPath))
      val content = os.read(configPath)
      assert(content.contains("version = 0.1.0"))

  tempDir.test("ConfigFileRepository reads config with version field"):
    dir =>
      val configPath = dir / ".iw" / "config.conf"
      os.makeDir.all(configPath / os.up)
      os.write(configPath, """
        tracker {
          type = linear
          team = IWLE
        }

        project {
          name = kanon
        }

        version = "0.2.0"
      """)

      val config = ConfigFileRepository.read(configPath)

      assert(config.isDefined)
      assertEquals(config.get.version, Some("0.2.0"))

  tempDir.test("ConfigFileRepository defaults to 'latest' when version is missing"):
    dir =>
      val configPath = dir / ".iw" / "config.conf"
      os.makeDir.all(configPath / os.up)
      os.write(configPath, """
        tracker {
          type = linear
          team = IWLE
        }

        project {
          name = kanon
        }
      """)

      val config = ConfigFileRepository.read(configPath)

      assert(config.isDefined)
      assertEquals(config.get.version, Some("latest"))
