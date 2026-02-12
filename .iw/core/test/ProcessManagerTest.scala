// PURPOSE: Integration tests for ProcessManager process control and PID file management
// PURPOSE: Validates background process spawning, PID file operations, and process lifecycle

package iw.core.test

import iw.core.dashboard.ProcessManager
import java.nio.file.{Files, Paths}
import iw.core.model.Check

class ProcessManagerTest extends munit.FunSuite:

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

  fixture.test("Write PID file and read back returns same PID"):
    tempDir =>
      val pidPath = tempDir / "server.pid"
      val testPid: Long = 12345L

      val writeResult = ProcessManager.writePidFile(testPid, pidPath.toString)
      assert(writeResult.isRight)

      val readResult = ProcessManager.readPidFile(pidPath.toString)
      assert(readResult.isRight)
      assertEquals(readResult.toOption.get, Some(testPid))

  fixture.test("Check if current process is alive"):
    tempDir =>
      // Use current JVM process PID
      val currentPid = ProcessHandle.current().pid()

      val isAlive = ProcessManager.isProcessAlive(currentPid)
      assert(isAlive, s"Current process $currentPid should be alive")

  fixture.test("Detect process is not alive for invalid PID"):
    tempDir =>
      // Use a very high PID that's unlikely to exist
      val invalidPid: Long = 999999999L

      val isAlive = ProcessManager.isProcessAlive(invalidPid)
      assert(!isAlive, s"Invalid PID $invalidPid should not be alive")

  fixture.test("Read non-existent PID file returns None"):
    tempDir =>
      val pidPath = tempDir / "nonexistent.pid"

      val result = ProcessManager.readPidFile(pidPath.toString)
      assert(result.isRight)
      assertEquals(result.toOption.get, None)

  fixture.test("Remove PID file after write"):
    tempDir =>
      val pidPath = tempDir / "server.pid"
      val testPid: Long = 54321L

      ProcessManager.writePidFile(testPid, pidPath.toString)
      assert(os.exists(pidPath))

      val removeResult = ProcessManager.removePidFile(pidPath.toString)
      assert(removeResult.isRight)
      assert(!os.exists(pidPath))

  fixture.test("Remove non-existent PID file succeeds"):
    tempDir =>
      val pidPath = tempDir / "nonexistent.pid"

      val removeResult = ProcessManager.removePidFile(pidPath.toString)
      assert(removeResult.isRight)

  fixture.test("Handle malformed PID file content"):
    tempDir =>
      val pidPath = tempDir / "malformed.pid"
      os.write(pidPath, "not-a-number")

      val result = ProcessManager.readPidFile(pidPath.toString)
      assert(result.isLeft)
      assert(result.left.exists(_.contains("Invalid PID")))

  fixture.test("Handle empty PID file"):
    tempDir =>
      val pidPath = tempDir / "empty.pid"
      os.write(pidPath, "")

      val result = ProcessManager.readPidFile(pidPath.toString)
      assert(result.isLeft)
      assert(result.left.exists(err => err.contains("Invalid PID") || err.contains("empty")))

  fixture.test("PID file parent directory created if missing"):
    tempDir =>
      val pidPath = tempDir / "nested" / "path" / "server.pid"
      val testPid: Long = 99999L

      val writeResult = ProcessManager.writePidFile(testPid, pidPath.toString)
      assert(writeResult.isRight)

      // Verify parent directories were created
      assert(os.exists(tempDir / "nested" / "path"))
      assert(os.exists(pidPath))
