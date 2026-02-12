// PURPOSE: CI workflow doctor check functions - validates CI workflow file presence
// PURPOSE: Provides checkWorkflowExistsWith for doctor hooks based on tracker type
package iw.core.model

object CIChecks:

  /** Check if CI workflow file exists based on tracker type.
    *
    * @param config Project configuration
    * @param fileExists Function to check file existence (injected for testability)
    * @return CheckResult indicating CI workflow file presence
    */
  def checkWorkflowExistsWith(
    config: ProjectConfiguration,
    fileExists: os.Path => Boolean
  ): CheckResult =
    config.trackerType match
      case IssueTrackerType.GitHub =>
        val githubWorkflow = os.pwd / ".github" / "workflows" / "ci.yml"
        if fileExists(githubWorkflow) then
          CheckResult.Success("Found (.github/workflows/ci.yml)")
        else
          CheckResult.Error("Missing", "Create .github/workflows/ci.yml")

      case IssueTrackerType.GitLab =>
        val gitlabCI = os.pwd / ".gitlab-ci.yml"
        if fileExists(gitlabCI) then
          CheckResult.Success("Found (.gitlab-ci.yml)")
        else
          CheckResult.Error("Missing", "Create .gitlab-ci.yml")

      case IssueTrackerType.Linear | IssueTrackerType.YouTrack =>
        val githubWorkflow = os.pwd / ".github" / "workflows" / "ci.yml"
        val gitlabCI = os.pwd / ".gitlab-ci.yml"

        if fileExists(githubWorkflow) then
          CheckResult.Success("Found (.github/workflows/ci.yml)")
        else if fileExists(gitlabCI) then
          CheckResult.Success("Found (.gitlab-ci.yml)")
        else
          CheckResult.Warning("No CI workflow found")

  /** Check if CI workflow file exists (uses real file system).
    *
    * @param config Project configuration
    * @return CheckResult indicating CI workflow file presence
    */
  def checkWorkflowExists(config: ProjectConfiguration): CheckResult =
    checkWorkflowExistsWith(config, os.exists(_))
