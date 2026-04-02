// PURPOSE: Tests for ReviewStateAdapter read/merge/validate/write sequence
// PURPOSE: Verifies correct I/O behavior and error handling for review-state.json

package iw.core.test

import iw.core.adapters.ReviewStateAdapter
import iw.core.model.ReviewStateUpdater

class ReviewStateAdapterTest extends munit.FunSuite:

  private val sampleValidJson =
    """{
      "version": 2,
      "issue_id": "IW-238",
      "artifacts": [],
      "last_updated": "2026-01-01T12:00:00Z"
    }"""

  private def withTempFile[A](content: String)(f: os.Path => A): A =
    val dir = os.Path(java.nio.file.Files.createTempDirectory("iw-test-rs"))
    val path = dir / "review-state.json"
    os.write(path, content)
    try f(path)
    finally os.remove.all(dir)

  private def withTempDir[A](f: os.Path => A): A =
    val dir = os.Path(java.nio.file.Files.createTempDirectory("iw-test-rs"))
    try f(dir)
    finally os.remove.all(dir)

  // ========== read Tests ==========

  test("read returns Right(jsonString) when file exists and is readable"):
    withTempFile(sampleValidJson) { path =>
      val result = ReviewStateAdapter.read(path)
      assert(result.isRight, s"Expected Right but got: $result")
      assert(result.toOption.get.contains("IW-238"), "Should contain issue ID")
    }

  test("read returns Left when file does not exist"):
    val nonExistent = os.Path(
      "/tmp/iw-test-nonexistent-" + System
        .currentTimeMillis() + "/review-state.json"
    )
    val result = ReviewStateAdapter.read(nonExistent)
    assert(result.isLeft, s"Expected Left but got: $result")

  // ========== update Tests ==========

  test(
    "update reads existing file, merges, validates, and writes updated content to disk"
  ):
    withTempFile(sampleValidJson) { path =>
      val input = ReviewStateUpdater.UpdateInput(
        displayText = Some("In Review"),
        displayType = Some("progress")
      )
      val result = ReviewStateAdapter.update(path, input)
      assert(result.isRight, s"Expected Right but got: $result")
      // Verify the file was actually written with the update
      val written = os.read(path)
      assert(
        written.contains("In Review"),
        s"Updated content should contain display text: $written"
      )
    }

  test("update returns Left when the target file does not exist"):
    withTempDir { dir =>
      val path = dir / "review-state.json"
      val input = ReviewStateUpdater.UpdateInput(displayText = Some("Updated"))
      val result = ReviewStateAdapter.update(path, input)
      assert(result.isLeft, s"Expected Left but got: $result")
    }

  test("update does NOT write to disk when validation fails after merge"):
    // Produce JSON that will fail validation after merge by using invalid data
    // We'll use a JSON that becomes invalid after merge - adding an unknown field via a trick:
    // Actually, the simplest way is to start with JSON that has an invalid value after merge.
    // The merge function only updates fields from UpdateInput - we can't directly inject invalid JSON.
    // Instead, use a pre-existing file that contains content that when merged will fail validation.
    // The validator checks additionalProperties: false at root level.
    // We'll write a file that after a regular merge still has an unknown property.
    val invalidBaseJson =
      """{
        "version": 2,
        "issue_id": "IW-238",
        "artifacts": [],
        "last_updated": "2026-01-01T12:00:00Z",
        "unknown_property": "this will fail validation"
      }"""
    withTempFile(invalidBaseJson) { path =>
      val originalContent = os.read(path)
      val input = ReviewStateUpdater.UpdateInput(displayText = Some("Updated"))
      val result = ReviewStateAdapter.update(path, input)
      assert(
        result.isLeft,
        s"Expected Left for validation failure but got: $result"
      )
      // File content should be unchanged
      val currentContent = os.read(path)
      assertEquals(
        currentContent,
        originalContent,
        "File should not be modified when validation fails"
      )
    }

  test(
    "update returns Left with formatted error message listing validation errors when validation fails"
  ):
    val invalidBaseJson =
      """{
        "version": 2,
        "issue_id": "IW-238",
        "artifacts": [],
        "last_updated": "2026-01-01T12:00:00Z",
        "unknown_property": "this fails validation"
      }"""
    withTempFile(invalidBaseJson) { path =>
      val input = ReviewStateUpdater.UpdateInput()
      val result = ReviewStateAdapter.update(path, input)
      assert(result.isLeft, s"Expected Left but got: $result")
      val errorMessage = result.left.getOrElse("")
      assert(
        errorMessage.contains("Validation failed"),
        s"Error should mention validation: $errorMessage"
      )
    }
