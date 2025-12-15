// PURPOSE: Git infrastructure adapter for reading git repository information
// PURPOSE: Provides utilities to check git status and read remote URLs

package iw.core

import java.nio.file.Path
import scala.sys.process.*
import scala.util.Try

object GitAdapter:
  def getRemoteUrl(dir: Path): Option[GitRemote] =
    Try {
      val result = Process(Seq("git", "config", "--get", "remote.origin.url"), dir.toFile).!!.trim
      if result.nonEmpty then Some(GitRemote(result))
      else None
    }.toOption.flatten

  def isGitRepository(dir: Path): Boolean =
    Try {
      Process(Seq("git", "rev-parse", "--git-dir"), dir.toFile).! == 0
    }.getOrElse(false)

  def getCurrentBranch(dir: Path): Either[String, String] =
    val result = ProcessAdapter.run(Seq("git", "-C", dir.toString, "rev-parse", "--abbrev-ref", "HEAD"))
    if result.exitCode == 0 then
      Right(result.stdout.trim)
    else
      Left(s"Failed to get current branch: ${result.stderr}")

  def hasUncommittedChanges(path: Path): Either[String, Boolean] =
    val result = ProcessAdapter.run(Seq("git", "-C", path.toString, "status", "--porcelain"))
    if result.exitCode == 0 then
      Right(result.stdout.trim.nonEmpty)
    else
      Left(s"Failed to check for uncommitted changes: ${result.stderr}")
