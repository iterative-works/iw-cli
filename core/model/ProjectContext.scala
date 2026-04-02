// PURPOSE: Pure rendering of project context for AI agent prompts
// PURPOSE: Generates deterministic markdown describing forge type, CLI tools, and project identity

package iw.core.model

object ProjectContext:

  /** All inputs needed to render a project context fragment. */
  case class Input(
      projectName: String,
      repository: Option[String],
      trackerType: IssueTrackerType,
      teamPrefix: Option[String],
      forgeType: ForgeType,
      gitlabHost: Option[String]
  )

  /** Build Input from project configuration and optional git remote. */
  def fromConfig(
      config: ProjectConfiguration,
      remote: Option[GitRemote]
  ): Input =
    val forgeType = ForgeType.resolve(remote, config.trackerType)
    val gitlabHost = forgeType match
      case ForgeType.GitLab => remote.flatMap(_.host.toOption)
      case ForgeType.GitHub => None

    Input(
      projectName = config.projectName,
      repository = config.repository,
      trackerType = config.trackerType,
      teamPrefix = config.teamPrefix,
      forgeType = forgeType,
      gitlabHost = gitlabHost
    )

  /** Render a deterministic markdown context fragment for AI agent prompts. */
  def render(input: Input): String =
    val sb = new StringBuilder
    sb.append("## Project Context\n\n")
    sb.append(s"**Project:** ${input.projectName}\n")
    input.repository.foreach(r => sb.append(s"**Repository:** $r\n"))

    val trackerName = input.trackerType.toString
    val prefixSuffix = input.teamPrefix.map(p => s" (prefix: $p)").getOrElse("")
    sb.append(s"**Tracker:** $trackerName$prefixSuffix\n")

    sb.append("\n")
    sb.append(renderForgeSection(input))
    sb.toString

  private def renderForgeSection(input: Input): String =
    input.forgeType match
      case ForgeType.GitHub => renderGitHubSection()
      case ForgeType.GitLab => renderGitLabSection(input.gitlabHost)

  private def renderGitHubSection(): String =
    s"""### Forge: GitHub
       |
       |- Use `gh` CLI for PR operations. Never use `glab`.
       |- PR creation: `gh pr create --title "..." --body "..."`
       |- PR listing: `gh pr list --head <branch> --json number,state,url`
       |- PR merge: `gh pr merge <url> --squash --delete-branch`
       |- The `iw phase-pr` and `iw phase-merge` commands handle this automatically.
       |""".stripMargin

  private def renderGitLabSection(host: Option[String]): String =
    val hostPrefix = host.map(h => s"GITLAB_HOST=$h ").getOrElse("")
    val hostWarning = host
      .map(h =>
        s"- **CRITICAL:** Always set `GITLAB_HOST=$h` when calling `glab` directly.\n"
      )
      .getOrElse("")

    s"""### Forge: GitLab
       |
       |- Use `glab` CLI for MR operations. Never use `gh`.
       |$hostWarning- MR creation: `${hostPrefix}glab mr create --title "..." --description "..."`
       |- MR listing: `${hostPrefix}glab mr list --source-branch <branch>`
       |- MR merge: `${hostPrefix}glab mr merge <id> --squash --remove-source-branch --yes`
       |- The `iw phase-pr` and `iw phase-merge` commands handle GITLAB_HOST automatically.
       |""".stripMargin
