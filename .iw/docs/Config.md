# Config

> Configuration types for project and tracker settings.

## Import

```scala
import iw.core.model.*
```

## API

### IssueTrackerType

```scala
enum IssueTrackerType:
  case Linear, YouTrack, GitHub, GitLab
```

### ProjectConfiguration

```scala
case class ProjectConfiguration(
  trackerType: IssueTrackerType,
  team: String,                        // Team ID (Linear/YouTrack) or empty (GitHub/GitLab)
  projectName: String,                 // Project name for worktree directories
  version: Option[String] = Some("latest"),
  youtrackBaseUrl: Option[String] = None,
  repository: Option[String] = None,   // GitHub/GitLab: owner/repo format
  teamPrefix: Option[String] = None    // GitHub/GitLab: prefix for issue IDs (e.g., "IWCLI")
)
```

### GitRemote

```scala
case class GitRemote(url: String):
  def host: Either[String, String]
  def repositoryOwnerAndName: Either[String, String]  // For GitHub URLs
  def extractGitLabRepository: Either[String, String] // For GitLab URLs
```

### ConfigSerializer

```scala
object ConfigSerializer:
  def toHocon(config: ProjectConfiguration): String
  def fromHocon(hocon: String): Either[String, ProjectConfiguration]
```

### TrackerDetector

```scala
object TrackerDetector:
  def suggestTracker(remote: GitRemote): Option[IssueTrackerType]
```

### TeamPrefixValidator

```scala
object TeamPrefixValidator:
  def validate(prefix: String): Either[String, String]
  def suggestFromRepository(repository: String): String
```

## Examples

```scala
// From issue.scala - loading and using configuration
val configPath = os.pwd / Constants.Paths.IwDir / "config.conf"
ConfigFileRepository.read(configPath) match
  case Some(config) =>
    config.trackerType match
      case IssueTrackerType.GitHub =>
        val repository = config.repository.getOrElse(...)
        GitHubClient.fetchIssue(issueId.value, repository)
      case IssueTrackerType.Linear =>
        LinearClient.fetchIssue(issueId, token)
  case None =>
    Left("Configuration not found")
```
