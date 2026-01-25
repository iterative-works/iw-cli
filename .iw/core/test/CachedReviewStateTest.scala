// PURPOSE: Unit tests for CachedReviewState cache validation logic
// PURPOSE: Verify mtime-based cache invalidation

package iw.core.test

import iw.core.model.{CachedReviewState, ReviewState, ReviewArtifact}

class CachedReviewStateTest extends munit.FunSuite:

  val sampleState = ReviewState(
    status = Some("awaiting_review"),
    phase = Some(1),
    message = Some("Ready"),
    artifacts = List(ReviewArtifact("Doc", "path/to/doc.md"))
  )

  test("CachedReviewState.isValid returns true when mtimes match"):
    val filesMtime = Map("file1.json" -> 1000L, "file2.json" -> 2000L)
    val cached = CachedReviewState(sampleState, filesMtime)

    val currentMtimes = Map("file1.json" -> 1000L, "file2.json" -> 2000L)
    assert(CachedReviewState.isValid(cached, currentMtimes))

  test("CachedReviewState.isValid returns false when mtime changed"):
    val filesMtime = Map("file1.json" -> 1000L)
    val cached = CachedReviewState(sampleState, filesMtime)

    val currentMtimes = Map("file1.json" -> 2000L) // mtime changed
    assert(!CachedReviewState.isValid(cached, currentMtimes))

  test("CachedReviewState.isValid returns false when file added"):
    val filesMtime = Map("file1.json" -> 1000L)
    val cached = CachedReviewState(sampleState, filesMtime)

    val currentMtimes = Map("file1.json" -> 1000L, "file2.json" -> 2000L) // new file
    assert(!CachedReviewState.isValid(cached, currentMtimes))

  test("CachedReviewState.isValid returns false when file removed"):
    val filesMtime = Map("file1.json" -> 1000L, "file2.json" -> 2000L)
    val cached = CachedReviewState(sampleState, filesMtime)

    val currentMtimes = Map("file1.json" -> 1000L) // file2 removed
    assert(!CachedReviewState.isValid(cached, currentMtimes))

  test("CachedReviewState.isValid returns true for empty file lists"):
    val cached = CachedReviewState(sampleState, Map.empty)
    val currentMtimes = Map.empty[String, Long]

    assert(CachedReviewState.isValid(cached, currentMtimes))

  test("CachedReviewState.isValid returns false when multiple mtimes changed"):
    val filesMtime = Map("file1.json" -> 1000L, "file2.json" -> 2000L, "file3.json" -> 3000L)
    val cached = CachedReviewState(sampleState, filesMtime)

    val currentMtimes = Map("file1.json" -> 1500L, "file2.json" -> 2500L, "file3.json" -> 3000L)
    assert(!CachedReviewState.isValid(cached, currentMtimes))
