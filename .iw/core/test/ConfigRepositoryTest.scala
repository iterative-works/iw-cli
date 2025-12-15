// PURPOSE: Tests for configuration file repository
// PURPOSE: Verifies ConfigFileRepository can write and read HOCON config files

//> using scala 3.3.1
//> using dep org.scalameta::munit::1.0.0
//> using dep com.typesafe:config:1.4.3
//> using file "../ConfigRepository.scala"
//> using file "../Config.scala"

import iw.core.*
import java.nio.file.Files
import java.nio.file.Path

class ConfigRepositoryTest extends munit.FunSuite:

  val tempDir = FunFixture[Path](
    setup = { _ =>
      Files.createTempDirectory("iw-config-repo-test")
    },
    teardown = { dir =>
      Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
    }
  )

  tempDir.test("ConfigFileRepository writes config to file"):
    dir =>
      val config = ProjectConfiguration(
        trackerType = IssueTrackerType.Linear,
        team = "IWLE",
        projectName = "kanon"
      )

      val configPath = dir.resolve(".iw").resolve("config.conf")
      ConfigFileRepository.write(configPath, config)

      assert(Files.exists(configPath))
      val content = Files.readString(configPath)
      assert(content.contains("tracker"))
      assert(content.contains("type = linear"))
      assert(content.contains("team = IWLE"))

  tempDir.test("ConfigFileRepository reads config from file"):
    dir =>
      val configPath = dir.resolve(".iw").resolve("config.conf")
      Files.createDirectories(configPath.getParent)
      Files.writeString(configPath, """
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

      val configPath = dir.resolve(".iw").resolve("config.conf")
      ConfigFileRepository.write(configPath, original)
      val restored = ConfigFileRepository.read(configPath)

      assert(restored.isDefined)
      assertEquals(restored.get, original)

  tempDir.test("ConfigFileRepository returns None for non-existent file"):
    dir =>
      val configPath = dir.resolve(".iw").resolve("config.conf")
      val config = ConfigFileRepository.read(configPath)

      assertEquals(config, None)

  tempDir.test("ConfigFileRepository creates parent directories when writing"):
    dir =>
      val config = ProjectConfiguration(
        trackerType = IssueTrackerType.Linear,
        team = "IWLE",
        projectName = "kanon"
      )

      val configPath = dir.resolve("deep").resolve("nested").resolve("config.conf")
      ConfigFileRepository.write(configPath, config)

      assert(Files.exists(configPath))
      assert(Files.exists(configPath.getParent))
      assert(Files.exists(configPath.getParent.getParent))

  tempDir.test("ConfigFileRepository writes config with version field"):
    dir =>
      val config = ProjectConfiguration(
        trackerType = IssueTrackerType.Linear,
        team = "IWLE",
        projectName = "kanon",
        version = Some("0.1.0")
      )

      val configPath = dir.resolve(".iw").resolve("config.conf")
      ConfigFileRepository.write(configPath, config)

      assert(Files.exists(configPath))
      val content = Files.readString(configPath)
      assert(content.contains("version = 0.1.0"))

  tempDir.test("ConfigFileRepository reads config with version field"):
    dir =>
      val configPath = dir.resolve(".iw").resolve("config.conf")
      Files.createDirectories(configPath.getParent)
      Files.writeString(configPath, """
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
      val configPath = dir.resolve(".iw").resolve("config.conf")
      Files.createDirectories(configPath.getParent)
      Files.writeString(configPath, """
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
