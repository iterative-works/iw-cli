// PURPOSE: Application service for review state loading and parsing
// PURPOSE: Pure business logic with file I/O injection pattern

package iw.core.dashboard

import iw.core.model.{ReviewState, ReviewArtifact, CachedReviewState}
import iw.core.model.Issue

/** Application service for review state tracking.
  *
  * Uses file I/O injection pattern for testability.
  * All logic is pure except for file operations injected from caller.
  */
object ReviewStateService:

  /** Parse review state from JSON string.
    *
    * Expects JSON format:
    * {
    *   "status": "awaiting_review",  // optional
    *   "phase": 8,                    // optional
    *   "message": "Ready",            // optional
    *   "artifacts": [                 // required
    *     {"label": "Analysis", "path": "project-management/issues/46/analysis.md"}
    *   ]
    * }
    *
    * @param json JSON string to parse
    * @return Either error message or ReviewState
    */
  def parseReviewStateJson(json: String): Either[String, ReviewState] =
    try
      import upickle.default.*

      // Define JSON readers for domain models
      given ReadWriter[ReviewArtifact] = macroRW[ReviewArtifact]

      // Custom reader for ReviewState that handles optional fields
      given ReadWriter[ReviewState] = readwriter[ujson.Value].bimap[ReviewState](
        state => ujson.Obj(
          "status" -> (state.status match {
            case Some(s) => ujson.Str(s)
            case None => ujson.Null
          }),
          "phase" -> (state.phase match {
            case Some(p) => ujson.Num(p)
            case None => ujson.Null
          }),
          "message" -> (state.message match {
            case Some(m) => ujson.Str(m)
            case None => ujson.Null
          }),
          "artifacts" -> writeJs(state.artifacts)
        ),
        jsValue => {
          val obj = jsValue.obj

          // artifacts is required
          val artifacts = obj.get("artifacts") match {
            case Some(arr) => read[List[ReviewArtifact]](arr)
            case None => throw new Exception("Missing required field: artifacts")
          }

          // Optional fields
          val status = obj.get("status").flatMap {
            case ujson.Null => None
            case ujson.Str(s) => Some(s)
            case _ => None
          }

          val phase = obj.get("phase").flatMap {
            case ujson.Null => None
            case ujson.Num(n) => Some(n.toInt)
            case _ => None
          }

          val message = obj.get("message").flatMap {
            case ujson.Null => None
            case ujson.Str(s) => Some(s)
            case _ => None
          }

          ReviewState(status, phase, message, artifacts)
        }
      )

      val parsed = read[ReviewState](json)
      Right(parsed)
    catch
      case e: Exception => Left(s"Failed to parse review state JSON: ${e.getMessage}")

  /** Fetch review state with cache support.
    *
    * Checks cache validity using file modification time.
    * Re-parses JSON file if cache is invalid or missing.
    *
    * File I/O is injected for testability (FCIS pattern).
    *
    * @param issueId Issue identifier (e.g., "IWLE-123")
    * @param worktreePath Path to worktree root
    * @param cache Existing review state cache
    * @param readFile Function to read file content (injected I/O)
    * @param getMtime Function to get file modification time (injected I/O)
    * @return Either error message or CachedReviewState
    */
  def fetchReviewState(
    issueId: String,
    worktreePath: String,
    cache: Map[String, CachedReviewState],
    readFile: String => Either[String, String],
    getMtime: String => Either[String, Long]
  ): Either[String, CachedReviewState] =
    val reviewStatePath = s"$worktreePath/project-management/issues/$issueId/review-state.json"

    // Get current mtime
    getMtime(reviewStatePath) match {
      case Left(err) =>
        // File doesn't exist or can't read mtime
        Left(err)

      case Right(currentMtime) =>
        val currentMtimes = Map(reviewStatePath -> currentMtime)

        // Check cache validity
        cache.get(issueId) match {
          case Some(cached) if CachedReviewState.isValid(cached, currentMtimes) =>
            // Cache is valid, return cached CachedReviewState
            Right(cached)

          case _ =>
            // Cache invalid or missing, read and parse file
            readFile(reviewStatePath).flatMap { content =>
              parseReviewStateJson(content).map { state =>
                // Wrap ReviewState in CachedReviewState with current mtime
                CachedReviewState(state, currentMtimes)
              }
            }
        }
    }
