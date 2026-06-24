// PURPOSE: Forge type enum for detecting GitHub vs GitLab vs Forgejo from git remote URL host
// PURPOSE: Provides optional CLI tool names, install URLs, and forge resolution from config

package iw.core.model

enum ForgeType:
  case GitHub, GitLab, Forgejo

  /** CLI tool name for the forge, or None if the forge has no CLI binary.
    *
    * GitHub uses `gh`, GitLab uses `glab`. Forgejo is accessed directly over
    * HTTP with no CLI binary.
    */
  def cliTool: Option[String] = this match
    case GitHub  => Some("gh")
    case GitLab  => Some("glab")
    case Forgejo => None

  /** Install URL for the forge CLI, or None if the forge has no CLI binary.
    *
    * Used in error messages when the CLI tool is not found.
    */
  def installUrl: Option[String] = this match
    case GitHub  => Some("https://cli.github.com/")
    case GitLab  => Some("https://gitlab.com/gitlab-org/cli")
    case Forgejo => None

object ForgeType:
  /** Detect forge type from a hostname. Defaults to GitLab for unknown hosts.
    *
    * codeberg.org maps to Forgejo. Self-hosted Forgejo instances are
    * indistinguishable from self-hosted GitLab by hostname, so unknown hosts
    * fall through to GitLab; the `resolve` method corrects this using the
    * tracker type.
    */
  def fromHost(host: String): ForgeType =
    if host == "github.com" then GitHub
    else if host == "codeberg.org" then Forgejo
    else GitLab

  /** Detect forge type from a GitRemote by extracting its host.
    *
    * Returns Left if the remote URL cannot be parsed to extract a host.
    */
  def fromRemote(remote: GitRemote): Either[String, ForgeType] =
    remote.host match
      case Right(host) => Right(fromHost(host))
      case Left(err)   =>
        Left(s"Cannot determine forge type from remote URL: $err")

  /** Resolve forge type from an optional remote, falling back to tracker type.
    *
    * For Forgejo, the tracker type wins over host detection because self-hosted
    * Forgejo instances are indistinguishable from self-hosted GitLab by
    * hostname alone. For GitHub and GitLab, the remote host is used when
    * available (preserving existing behavior).
    */
  def resolve(
      remoteOpt: Option[GitRemote],
      trackerType: IssueTrackerType
  ): ForgeType =
    if trackerType == IssueTrackerType.Forgejo then Forgejo
    else
      remoteOpt.flatMap(r => fromRemote(r).toOption).getOrElse {
        trackerType match
          case IssueTrackerType.GitHub => GitHub
          case _                       => GitLab
      }
