// PURPOSE: Forge type enum for detecting GitHub vs GitLab from git remote URL host
// PURPOSE: Provides CLI tool names, install URLs, and forge resolution from config

package iw.core.model

enum ForgeType:
  case GitHub, GitLab

  def cliTool: String = this match
    case GitHub => "gh"
    case GitLab => "glab"

  def installUrl: String = this match
    case GitHub => "https://cli.github.com/"
    case GitLab => "https://gitlab.com/gitlab-org/cli"

object ForgeType:
  /** Detect forge type from a hostname. Defaults to GitLab for unknown hosts. */
  def fromHost(host: String): ForgeType =
    if host == "github.com" then GitHub
    else GitLab

  /** Detect forge type from a GitRemote by extracting its host.
    *
    * Returns Left if the remote URL cannot be parsed to extract a host.
    */
  def fromRemote(remote: GitRemote): Either[String, ForgeType] =
    remote.host match
      case Right(host) => Right(fromHost(host))
      case Left(err)   => Left(s"Cannot determine forge type from remote URL: $err")

  /** Resolve forge type from an optional remote, falling back to tracker type.
    *
    * Uses the remote URL host when available and parseable; otherwise falls back
    * to the tracker type (GitHub tracker → GitHub forge, anything else → GitLab).
    */
  def resolve(remoteOpt: Option[GitRemote], trackerType: IssueTrackerType): ForgeType =
    remoteOpt.flatMap(r => fromRemote(r).toOption).getOrElse {
      trackerType match
        case IssueTrackerType.GitHub => GitHub
        case _                       => GitLab
    }
