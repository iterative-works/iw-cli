// PURPOSE: Pure URL construction for file-browseable links from git remote URLs
// PURPOSE: Derives GitHub blob or GitLab blob URLs from a GitRemote and branch name

package iw.core.model

object FileUrlBuilder:

  /** Build a browseable file URL base from remote URL and branch name.
    *
    * GitHub pattern: https://github.com/owner/repo/blob/{branch}/
    * GitLab pattern: https://gitlab.com/owner/project/-/blob/{branch}/
    *
    * @param remote Git remote URL (HTTPS or SSH)
    * @param branch Branch name
    * @return Right(url base) or Left(error) if remote format unrecognized
    */
  def build(remote: GitRemote, branch: String): Either[String, String] =
    remote.host.flatMap { host =>
      extractRepoPath(remote).map { repoPath =>
        if host == "github.com" then
          s"https://github.com/$repoPath/blob/$branch/"
        else
          s"https://$host/$repoPath/-/blob/$branch/"
      }
    }

  /** Extract the repository path (owner/repo or owner/group/project) from the remote URL. */
  private def extractRepoPath(remote: GitRemote): Either[String, String] =
    val url = remote.url
    val rawPath =
      if url.startsWith("git@") then
        // SSH format: git@host:owner/repo.git
        val afterColon = url.dropWhile(_ != ':').drop(1)
        afterColon
      else
        // HTTPS format: https://host/owner/repo.git or https://user@host/owner/repo.git
        val afterProtocol = url.dropWhile(_ != '/').drop(2) // remove protocol://
        val afterUsername =
          if afterProtocol.contains('@') then afterProtocol.dropWhile(_ != '@').drop(1)
          else afterProtocol
        afterUsername.dropWhile(_ != '/').drop(1) // skip host, take path

    // Clean .git suffix and surrounding slashes
    val path = rawPath.stripSuffix("/").stripSuffix(".git").stripSuffix("/")

    if path.isEmpty || !path.contains('/') then
      Left(s"Cannot extract repository path from remote URL: ${remote.url}")
    else if path.split("/", -1).exists(_.isEmpty) then
      Left(s"Invalid repository path in remote URL: ${remote.url}")
    else
      Right(path)
