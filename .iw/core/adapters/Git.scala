// PURPOSE: Git infrastructure adapter for reading and manipulating git repositories
// PURPOSE: Provides utilities to check git status, read remote URLs, branch, commit, and push

package iw.core.adapters

import iw.core.model.GitRemote

import scala.sys.process.*
import scala.util.Try

object GitAdapter:
  def getRemoteUrl(dir: os.Path): Option[GitRemote] =
    Try {
      val result = Process(Seq("git", "config", "--get", "remote.origin.url"), dir.toIO).!!.trim
      if result.nonEmpty then Some(GitRemote(result))
      else None
    }.toOption.flatten

  def isGitRepository(dir: os.Path): Boolean =
    Try {
      Process(Seq("git", "rev-parse", "--git-dir"), dir.toIO).! == 0
    }.getOrElse(false)

  def getCurrentBranch(dir: os.Path): Either[String, String] =
    val result = ProcessAdapter.run(Seq("git", "-C", dir.toString, "rev-parse", "--abbrev-ref", "HEAD"))
    if result.exitCode == 0 then
      Right(result.stdout.trim)
    else
      Left(s"Failed to get current branch: ${result.stderr}")

  def getHeadSha(dir: os.Path): Either[String, String] =
    val result = ProcessAdapter.run(Seq("git", "-C", dir.toString, "rev-parse", "--short", "HEAD"))
    if result.exitCode == 0 then
      Right(result.stdout.trim)
    else
      Left(s"Failed to get HEAD SHA: ${result.stderr}")

  def hasUncommittedChanges(path: os.Path): Either[String, Boolean] =
    val result = ProcessAdapter.run(Seq("git", "-C", path.toString, "status", "--porcelain"))
    if result.exitCode == 0 then
      Right(result.stdout.trim.nonEmpty)
    else
      Left(s"Failed to check for uncommitted changes: ${result.stderr}")

  /** Create and checkout a new branch from HEAD. */
  def createAndCheckoutBranch(name: String, dir: os.Path): Either[String, Unit] =
    val result = ProcessAdapter.run(Seq("git", "-C", dir.toString, "checkout", "-b", name))
    if result.exitCode == 0 then Right(())
    else Left(s"Failed to create branch '$name': ${result.stderr}")

  /** Checkout an existing branch. */
  def checkoutBranch(name: String, dir: os.Path): Either[String, Unit] =
    val result = ProcessAdapter.run(Seq("git", "-C", dir.toString, "checkout", name))
    if result.exitCode == 0 then Right(())
    else Left(s"Failed to checkout branch '$name': ${result.stderr}")

  /** Stage all changes (git add -A). */
  def stageAll(dir: os.Path): Either[String, Unit] =
    val result = ProcessAdapter.run(Seq("git", "-C", dir.toString, "add", "-A"))
    if result.exitCode == 0 then Right(())
    else Left(s"Failed to stage all changes: ${result.stderr}")

  /** Commit with message and return the resulting commit SHA. */
  def commit(message: String, dir: os.Path): Either[String, String] =
    val commitResult = ProcessAdapter.run(Seq("git", "-C", dir.toString, "commit", "-m", message))
    if commitResult.exitCode != 0 then
      Left(s"Failed to commit: ${commitResult.stderr}")
    else
      getFullHeadSha(dir)

  /** Push branch to origin, optionally setting upstream. */
  def push(branch: String, dir: os.Path, setUpstream: Boolean = false): Either[String, Unit] =
    val args =
      if setUpstream then Seq("git", "-C", dir.toString, "push", "-u", "origin", branch)
      else Seq("git", "-C", dir.toString, "push", "origin", branch)
    val result = ProcessAdapter.run(args)
    if result.exitCode == 0 then Right(())
    else Left(s"Failed to push branch '$branch': ${result.stderr}")

  /** List changed files since a baseline commit (git diff --name-only). */
  def diffNameOnly(baseline: String, dir: os.Path): Either[String, List[String]] =
    val result = ProcessAdapter.run(Seq("git", "-C", dir.toString, "diff", "--name-only", baseline))
    if result.exitCode == 0 then
      val files = result.stdout.trim
      if files.isEmpty then Right(Nil)
      else Right(files.split("\n").toList)
    else
      Left(s"Failed to diff from baseline '$baseline': ${result.stderr}")

  /** Pull current branch from origin. */
  def pull(dir: os.Path): Either[String, Unit] =
    val result = ProcessAdapter.run(Seq("git", "-C", dir.toString, "pull"))
    if result.exitCode == 0 then Right(())
    else Left(s"Failed to pull: ${result.stderr}")

  /** Get full (non-abbreviated) HEAD SHA. */
  def getFullHeadSha(dir: os.Path): Either[String, String] =
    val result = ProcessAdapter.run(Seq("git", "-C", dir.toString, "rev-parse", "HEAD"))
    if result.exitCode == 0 then
      Right(result.stdout.trim)
    else
      Left(s"Failed to get full HEAD SHA: ${result.stderr}")

  /** Fetch from origin and reset the current branch to the remote tracking branch.
    *
    * Used after a squash-merge to advance the feature branch without spurious conflicts.
    * `reset --hard` is correct here because origin/{branch} is a superset of local content.
    */
  def fetchAndReset(branch: String, dir: os.Path): Either[String, Unit] =
    val fetchResult = ProcessAdapter.run(Seq("git", "-C", dir.toString, "fetch", "origin"))
    if fetchResult.exitCode != 0 then
      return Left(s"Failed to fetch from origin: ${fetchResult.stderr}")

    val resetResult = ProcessAdapter.run(Seq("git", "-C", dir.toString, "reset", "--hard", s"origin/$branch"))
    if resetResult.exitCode != 0 then
      Left(s"Failed to reset to origin/$branch: ${resetResult.stderr}")
    else
      Right(())
