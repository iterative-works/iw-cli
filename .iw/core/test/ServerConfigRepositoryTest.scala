// PURPOSE: Integration tests for ServerConfigRepository file operations
// PURPOSE: Validates JSON config file read/write and default creation

package iw.core.test

import iw.core.{ServerConfig, ServerConfigRepository}
import java.nio.file.{Files, Paths}

class ServerConfigRepositoryTest extends munit.FunSuite:

  val fixture = FunFixture[os.Path](
    setup = { test =>
      // Create temp directory for each test
      val tempDir = os.temp.dir()
      tempDir
    },
    teardown = { tempDir =>
      // Clean up temp directory
      if os.exists(tempDir) then
        os.remove.all(tempDir)
    }
  )

  fixture.test("Write config file and read back returns same port"):
    tempDir =>
      val configPath = tempDir / "config.json"
      val config = ServerConfig(8080)

      val writeResult = ServerConfigRepository.write(config, configPath.toString)
      assert(writeResult.isRight)

      val readResult = ServerConfigRepository.read(configPath.toString)
      assert(readResult.isRight)
      assertEquals(readResult.toOption.get.port, 8080)

  fixture.test("Create default config if file missing"):
    tempDir =>
      val configPath = tempDir / "config.json"

      val result = ServerConfigRepository.getOrCreateDefault(configPath.toString)
      assert(result.isRight)
      assertEquals(result.toOption.get.port, 9876) // default port

      // Verify file was created
      assert(os.exists(configPath))

  fixture.test("Handle invalid JSON in config file"):
    tempDir =>
      val configPath = tempDir / "config.json"
      os.write(configPath, "{invalid json")

      val result = ServerConfigRepository.read(configPath.toString)
      assert(result.isLeft)
      assert(result.left.exists(_.contains("Failed to parse")))

  fixture.test("Handle missing port field in JSON"):
    tempDir =>
      val configPath = tempDir / "config.json"
      os.write(configPath, "{}")

      val result = ServerConfigRepository.read(configPath.toString)
      assert(result.isLeft)
      assert(result.left.exists(err => err.contains("port") || err.contains("Failed to parse")))

  fixture.test("Handle invalid port value in JSON"):
    tempDir =>
      val configPath = tempDir / "config.json"
      os.write(configPath, """{"port": 70000}""")

      val result = ServerConfigRepository.read(configPath.toString)
      assert(result.isLeft)
      assert(result.left.exists(_.contains("Port must be between 1024 and 65535")))

  fixture.test("Handle missing parent directory - creates it"):
    tempDir =>
      val configPath = tempDir / "nested" / "path" / "config.json"
      val config = ServerConfig(9876)

      val writeResult = ServerConfigRepository.write(config, configPath.toString)
      assert(writeResult.isRight)

      // Verify parent directories were created
      assert(os.exists(tempDir / "nested" / "path"))
      assert(os.exists(configPath))

  fixture.test("Read non-existent file returns error"):
    tempDir =>
      val configPath = tempDir / "nonexistent.json"

      val result = ServerConfigRepository.read(configPath.toString)
      assert(result.isLeft)
      assert(result.left.exists(err => err.contains("not found") || err.contains("does not exist")))

  fixture.test("getOrCreateDefault on existing file reads it"):
    tempDir =>
      val configPath = tempDir / "config.json"
      val config = ServerConfig(8080)

      // First create a config with custom port
      ServerConfigRepository.write(config, configPath.toString)

      // getOrCreateDefault should read existing, not overwrite with default
      val result = ServerConfigRepository.getOrCreateDefault(configPath.toString)
      assert(result.isRight)
      assertEquals(result.toOption.get.port, 8080)

  // Backward compatibility tests for hosts field
  fixture.test("Deserialize config without hosts field defaults to Seq(localhost)"):
    tempDir =>
      val configPath = tempDir / "config.json"
      // Old config format without hosts field
      os.write(configPath, """{"port": 9876}""")

      val result = ServerConfigRepository.read(configPath.toString)
      assert(result.isRight)
      val config = result.toOption.get
      assertEquals(config.port, 9876)
      assertEquals(config.hosts, Seq("localhost"))

  fixture.test("Deserialize config with hosts field preserves explicit values"):
    tempDir =>
      val configPath = tempDir / "config.json"
      // New config format with hosts field
      os.write(configPath, """{"port": 9876, "hosts": ["127.0.0.1", "192.168.1.5"]}""")

      val result = ServerConfigRepository.read(configPath.toString)
      assert(result.isRight)
      val config = result.toOption.get
      assertEquals(config.port, 9876)
      assertEquals(config.hosts, Seq("127.0.0.1", "192.168.1.5"))

  fixture.test("Deserialize config with empty hosts array fails validation"):
    tempDir =>
      val configPath = tempDir / "config.json"
      // Config with empty hosts array
      os.write(configPath, """{"port": 9876, "hosts": []}""")

      val result = ServerConfigRepository.read(configPath.toString)
      assert(result.isLeft)
      assert(result.left.exists(_.contains("at least one host")))

  fixture.test("Serialize config with hosts field includes hosts in JSON"):
    tempDir =>
      val configPath = tempDir / "config.json"
      val config = ServerConfig(9876, Seq("localhost", "0.0.0.0"))

      val writeResult = ServerConfigRepository.write(config, configPath.toString)
      assert(writeResult.isRight)

      // Read raw JSON and verify hosts field is present
      val json = os.read(configPath)
      assert(json.contains("hosts"))
      assert(json.contains("localhost"))
      assert(json.contains("0.0.0.0"))
