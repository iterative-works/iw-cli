// PURPOSE: Application service for PR detection with caching
// PURPOSE: Pure business logic with injected command execution and tool detection

package iw.core.application

import iw.core.domain.{PullRequestData, PRState, CachedPR}
import java.time.Instant

/** Pull request cache service.
  * Pure application logic - receives command execution and tool detection functions from caller.
  * Follows FCIS pattern: no side effects in service, effects injected.
  */
object PullRequestCacheService:

  /** Fetch PR data with cache support.
    *
    * Checks cache validity (2-minute TTL) and re-fetches if expired.
    * Returns None if no PR found (not an error).
    *
    * @param worktreePath Path to worktree directory
    * @param cache Current PR cache
    * @param issueId Issue ID for cache key
    * @param now Current timestamp for TTL validation
    * @param execCommand Command execution function (command, args) => Either[error, stdout]
    * @param detectTool Tool detection function (toolName) => Boolean
    * @return Right(Some(PR)) if found, Right(None) if no PR/tool, Left(error) on failure
    */
  def fetchPR(
    worktreePath: String,
    cache: Map[String, CachedPR],
    issueId: String,
    now: Instant,
    execCommand: (String, Array[String]) => Either[String, String],
    detectTool: String => Boolean
  ): Either[String, Option[PullRequestData]] =
    // Check cache validity
    cache.get(issueId) match
      case Some(cached) if CachedPR.isValid(cached, now) =>
        Right(Some(cached.pr))
      case _ =>
        // Cache miss or expired, fetch fresh data
        fetchFreshPR(worktreePath, execCommand, detectTool)

  /** Fetch fresh PR data from gh/glab CLI.
    *
    * @param worktreePath Path to worktree directory
    * @param execCommand Command execution function
    * @param detectTool Tool detection function
    * @return Right(Some(PR)) if found, Right(None) if no PR/tool, Left(error) on failure
    */
  private def fetchFreshPR(
    worktreePath: String,
    execCommand: (String, Array[String]) => Either[String, String],
    detectTool: String => Boolean
  ): Either[String, Option[PullRequestData]] =
    detectPRTool(detectTool) match
      case Some("gh") =>
        execCommand("gh", Array("pr", "view", "--json", "url,state,number,title")) match
          case Right(json) => parseGitHubPR(json).map(Some(_))
          case Left(_) => Right(None) // No PR found, not an error
      case Some("glab") =>
        execCommand("glab", Array("mr", "view", "--output", "json")) match
          case Right(json) => parseGitLabPR(json).map(Some(_))
          case Left(_) => Right(None) // No PR found, not an error
      case _ =>
        Right(None) // No tool available

  /** Parse PR data from gh pr view JSON output.
    *
    * @param jsonOutput JSON output from gh pr view
    * @return Right(PullRequestData) if parsed, Left(error) if parsing fails
    */
  def parseGitHubPR(jsonOutput: String): Either[String, PullRequestData] =
    try
      import upickle.default.*

      val json = ujson.read(jsonOutput)
      val url = json("url").str
      val stateStr = json("state").str
      val number = json("number").num.toInt
      val title = json("title").str

      val state = stateStr match
        case "OPEN" => PRState.Open
        case "MERGED" => PRState.Merged
        case "CLOSED" => PRState.Closed
        case other => return Left(s"Unknown GitHub PR state: $other")

      Right(PullRequestData(url, state, number, title))
    catch
      case e: Exception =>
        Left(s"Failed to parse GitHub PR JSON: ${e.getMessage}")

  /** Parse PR data from glab mr view JSON output.
    *
    * @param jsonOutput JSON output from glab mr view
    * @return Right(PullRequestData) if parsed, Left(error) if parsing fails
    */
  def parseGitLabPR(jsonOutput: String): Either[String, PullRequestData] =
    try
      import upickle.default.*

      val json = ujson.read(jsonOutput)
      val url = json("url").str
      val stateStr = json("state").str
      val number = json("iid").num.toInt // GitLab uses 'iid' for number
      val title = json("title").str

      val state = stateStr match
        case "opened" => PRState.Open
        case "merged" => PRState.Merged
        case "closed" => PRState.Closed
        case other => return Left(s"Unknown GitLab MR state: $other")

      Right(PullRequestData(url, state, number, title))
    catch
      case e: Exception =>
        Left(s"Failed to parse GitLab MR JSON: ${e.getMessage}")

  /** Detect which PR tool is available (gh or glab).
    *
    * Prefers gh over glab if both available.
    *
    * @param detectTool Tool detection function (toolName) => Boolean
    * @return Some("gh"), Some("glab"), or None
    */
  def detectPRTool(detectTool: String => Boolean): Option[String] =
    if detectTool("gh") then Some("gh")
    else if detectTool("glab") then Some("glab")
    else None
