// PURPOSE: Cache wrapper for review state with mtime validation
// PURPOSE: Tracks file modification timestamps to invalidate stale cached data

package iw.core.domain

/** Cached review state with file modification timestamps.
  *
  * @param state The review state data
  * @param filesMtime Map of file path to modification time (epoch millis)
  */
case class CachedReviewState(
  state: ReviewState,
  filesMtime: Map[String, Long]
)

object CachedReviewState:
  /** Check if cached review state is still valid based on file modification times.
    *
    * Cache is valid if:
    * - All current files exist in cache with same mtime
    * - No new files have been added
    * - No files have been removed
    *
    * @param cached Cached review state with stored mtimes
    * @param currentMtimes Current file modification times
    * @return True if cache is still valid, false if needs re-parsing
    */
  def isValid(cached: CachedReviewState, currentMtimes: Map[String, Long]): Boolean =
    // Check if sets of files are the same
    val cachedFiles = cached.filesMtime.keySet
    val currentFiles = currentMtimes.keySet

    if cachedFiles != currentFiles then
      false // File added or removed
    else
      // Check if all mtimes match
      currentMtimes.forall { case (path, mtime) =>
        cached.filesMtime.get(path).contains(mtime)
      }
