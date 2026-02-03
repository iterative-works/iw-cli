// PURPOSE: Unit tests for ReviewStateUpdater pure merge logic
// PURPOSE: Verifies correct merging of updates with existing review-state JSON

package iw.core.test

import iw.core.model.*

class ReviewStateUpdaterTest extends munit.FunSuite:

  test("merge with no updates returns existing JSON unchanged"):
    val existing = """{
      "version": 2,
      "issue_id": "IW-1",
      "artifacts": [],
      "last_updated": "2026-01-01T12:00:00Z",
      "git_sha": "abc123"
    }"""
    val input = ReviewStateUpdater.UpdateInput()
    val result = ReviewStateUpdater.merge(existing, input)
    val parsed = ujson.read(result)

    // Verify structure is preserved (except last_updated which is always updated)
    assertEquals(parsed("version").num.toInt, 2)
    assertEquals(parsed("issue_id").str, "IW-1")
    assertEquals(parsed("artifacts").arr.length, 0)
    assertEquals(parsed("git_sha").str, "abc123")
    // last_updated will be different (current time)
    assert(parsed("last_updated").str != "2026-01-01T12:00:00Z")

  test("merge scalar field (displayText) replaces value"):
    val existing = """{
      "version": 2,
      "issue_id": "IW-1",
      "artifacts": [],
      "last_updated": "2026-01-01T12:00:00Z",
      "display": {"text": "Old Text", "type": "info"}
    }"""
    val input = ReviewStateUpdater.UpdateInput(displayText = Some("New Text"))
    val result = ReviewStateUpdater.merge(existing, input)
    val parsed = ujson.read(result)

    assertEquals(parsed("display")("text").str, "New Text")
    assertEquals(parsed("display")("type").str, "info")  // type preserved

  test("merge partial object (display.text only) keeps display.type"):
    val existing = """{
      "version": 2,
      "issue_id": "IW-1",
      "artifacts": [],
      "last_updated": "2026-01-01T12:00:00Z",
      "display": {"text": "Old", "subtext": "Old sub", "type": "warning"}
    }"""
    val input = ReviewStateUpdater.UpdateInput(displayText = Some("New"))
    val result = ReviewStateUpdater.merge(existing, input)
    val parsed = ujson.read(result)

    assertEquals(parsed("display")("text").str, "New")
    assertEquals(parsed("display")("subtext").str, "Old sub")
    assertEquals(parsed("display")("type").str, "warning")

  test("replace array (artifacts) replaces entire array"):
    val existing = """{
      "version": 2,
      "issue_id": "IW-1",
      "artifacts": [
        {"label": "Old1", "path": "old1.md"},
        {"label": "Old2", "path": "old2.md"}
      ],
      "last_updated": "2026-01-01T12:00:00Z"
    }"""
    val input = ReviewStateUpdater.UpdateInput(
      artifacts = Some(List(("New1", "new1.md", None), ("New2", "new2.md", None))),
      artifactsMode = ReviewStateUpdater.ArrayMergeMode.Replace
    )
    val result = ReviewStateUpdater.merge(existing, input)
    val parsed = ujson.read(result)

    assertEquals(parsed("artifacts").arr.length, 2)
    assertEquals(parsed("artifacts")(0)("label").str, "New1")
    assertEquals(parsed("artifacts")(1)("label").str, "New2")

  test("append to array (artifacts) adds to existing"):
    val existing = """{
      "version": 2,
      "issue_id": "IW-1",
      "artifacts": [
        {"label": "Existing", "path": "existing.md"}
      ],
      "last_updated": "2026-01-01T12:00:00Z"
    }"""
    val input = ReviewStateUpdater.UpdateInput(
      artifacts = Some(List(("New", "new.md", None))),
      artifactsMode = ReviewStateUpdater.ArrayMergeMode.Append
    )
    val result = ReviewStateUpdater.merge(existing, input)
    val parsed = ujson.read(result)

    assertEquals(parsed("artifacts").arr.length, 2)
    assertEquals(parsed("artifacts")(0)("label").str, "Existing")
    assertEquals(parsed("artifacts")(1)("label").str, "New")

  test("clear array (artifacts) removes all items"):
    val existing = """{
      "version": 2,
      "issue_id": "IW-1",
      "artifacts": [
        {"label": "Old1", "path": "old1.md"},
        {"label": "Old2", "path": "old2.md"}
      ],
      "last_updated": "2026-01-01T12:00:00Z"
    }"""
    val input = ReviewStateUpdater.UpdateInput(
      artifacts = Some(List(("Ignored", "ignored.md", None))),
      artifactsMode = ReviewStateUpdater.ArrayMergeMode.Clear
    )
    val result = ReviewStateUpdater.merge(existing, input)
    val parsed = ujson.read(result)

    assertEquals(parsed("artifacts").arr.length, 0)

  test("last_updated always updated to current time"):
    val existing = """{
      "version": 2,
      "issue_id": "IW-1",
      "artifacts": [],
      "last_updated": "2026-01-01T12:00:00Z"
    }"""
    val input = ReviewStateUpdater.UpdateInput()
    val result = ReviewStateUpdater.merge(existing, input)
    val parsed = ujson.read(result)

    assert(parsed("last_updated").str != "2026-01-01T12:00:00Z")
    assert(parsed("last_updated").str.startsWith("2026"))  // Current year

  test("git_sha preserved when not provided"):
    val existing = """{
      "version": 2,
      "issue_id": "IW-1",
      "artifacts": [],
      "last_updated": "2026-01-01T12:00:00Z",
      "git_sha": "original123"
    }"""
    val input = ReviewStateUpdater.UpdateInput(displayText = Some("Updated"))
    val result = ReviewStateUpdater.merge(existing, input)
    val parsed = ujson.read(result)

    assertEquals(parsed("git_sha").str, "original123")

  test("version and issue_id always preserved"):
    val existing = """{
      "version": 2,
      "issue_id": "IW-42",
      "artifacts": [],
      "last_updated": "2026-01-01T12:00:00Z"
    }"""
    val input = ReviewStateUpdater.UpdateInput(displayText = Some("Updated"))
    val result = ReviewStateUpdater.merge(existing, input)
    val parsed = ujson.read(result)

    assertEquals(parsed("version").num.toInt, 2)
    assertEquals(parsed("issue_id").str, "IW-42")

  test("merged result passes ReviewStateValidator"):
    val existing = """{
      "version": 2,
      "issue_id": "IW-1",
      "artifacts": [],
      "last_updated": "2026-01-01T12:00:00Z"
    }"""
    val input = ReviewStateUpdater.UpdateInput(
      displayText = Some("Testing"),
      displayType = Some("info")
    )
    val result = ReviewStateUpdater.merge(existing, input)

    val validation = ReviewStateValidator.validate(result)
    assert(validation.isValid, s"Merged result should be valid, but got errors: ${validation.errors}")
