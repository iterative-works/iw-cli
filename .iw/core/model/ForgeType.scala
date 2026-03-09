// PURPOSE: Forge type enum for detecting GitHub vs GitLab from git remote URL host
// PURPOSE: Used by phase commands to dispatch between gh and glab CLI tools

package iw.core.model

enum ForgeType:
  case GitHub, GitLab

object ForgeType:
  /** Detect forge type from a hostname. Defaults to GitLab for unknown hosts. */
  def fromHost(host: String): ForgeType =
    if host == "github.com" then GitHub
    else GitLab

  /** Detect forge type from a GitRemote by extracting its host. Defaults to GitLab on failure. */
  def fromRemote(remote: GitRemote): ForgeType =
    remote.host match
      case Right(host) => fromHost(host)
      case Left(_)     => GitLab
