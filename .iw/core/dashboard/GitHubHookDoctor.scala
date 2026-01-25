// PURPOSE: GitHub doctor check functions - validates gh CLI prerequisites
// PURPOSE: Provides checkGhInstalled and checkGhAuthenticated for doctor hooks
package iw.core.dashboard

import iw.core.adapters.GitHubClient
import iw.core.model.{IssueTrackerType, ProjectConfiguration}
import iw.core.CheckResult

object GitHubHookDoctor:

  /** Check if gh CLI is installed.
    *
    * @param config Project configuration
    * @param isCommandAvailable Function to check command availability (injected for testability)
    * @return CheckResult indicating gh CLI installation status
    */
  def checkGhInstalledWith(
    config: ProjectConfiguration,
    isCommandAvailable: String => Boolean
  ): CheckResult =
    if config.trackerType != IssueTrackerType.GitHub then
      CheckResult.Skip("Not using GitHub")
    else if isCommandAvailable("gh") then
      CheckResult.Success("Installed")
    else
      CheckResult.Error("Not found", "Install: https://cli.github.com/")

  /** Check if gh CLI is installed (uses default CommandRunner).
    *
    * @param config Project configuration
    * @return CheckResult indicating gh CLI installation status
    */
  def checkGhInstalled(config: ProjectConfiguration): CheckResult =
    checkGhInstalledWith(config, CommandRunner.isCommandAvailable)

  /** Check if gh CLI is authenticated.
    *
    * @param config Project configuration
    * @param isCommandAvailable Function to check command availability (injected for testability)
    * @param execCommand Function to execute commands (injected for testability)
    * @return CheckResult indicating gh CLI authentication status
    */
  def checkGhAuthenticatedWith(
    config: ProjectConfiguration,
    isCommandAvailable: String => Boolean,
    execCommand: (String, Array[String]) => Either[String, String]
  ): CheckResult =
    if config.trackerType != IssueTrackerType.GitHub then
      CheckResult.Skip("Not using GitHub")
    else if !isCommandAvailable("gh") then
      CheckResult.Skip("gh not installed")
    else
      // Use validateGhPrerequisites from Phase 4 to check auth
      val repository = config.repository.getOrElse("")
      GitHubClient.validateGhPrerequisites(repository, isCommandAvailable, execCommand) match
        case Right(_) =>
          CheckResult.Success("Authenticated")
        case Left(GitHubClient.GhPrerequisiteError.GhNotAuthenticated) =>
          CheckResult.Error("Not authenticated", "Run: gh auth login")
        case Left(GitHubClient.GhPrerequisiteError.GhNotInstalled) =>
          // This shouldn't happen since we already checked installation
          CheckResult.Skip("gh not installed")
        case Left(GitHubClient.GhPrerequisiteError.GhOtherError(msg)) =>
          CheckResult.Error("Authentication check failed", msg)

  /** Check if gh CLI is authenticated (uses default CommandRunner).
    *
    * @param config Project configuration
    * @return CheckResult indicating gh CLI authentication status
    */
  def checkGhAuthenticated(config: ProjectConfiguration): CheckResult =
    checkGhAuthenticatedWith(
      config,
      CommandRunner.isCommandAvailable,
      (cmd, args) => CommandRunner.execute(cmd, args)
    )
