# YouTrackClient

> YouTrack REST API client for issue operations.

## Import

```scala
import iw.core.adapters.*
import iw.core.model.*
```

## Prerequisites

Requires:
- `YOUTRACK_API_TOKEN` environment variable set with a valid YouTrack permanent token
- YouTrack base URL configured in `.iw/config.conf` as `tracker.baseUrl`

## API

### YouTrackClient.fetchIssue(issueId: IssueId, baseUrl: String, token: ApiToken): Either[String, Issue]

Fetch a YouTrack issue by readable ID (e.g., "PROJ-123").

### YouTrackClient.createIssue(project: String, title: String, description: String, baseUrl: String, token: ApiToken): Either[String, CreatedIssue]

Create a new YouTrack issue in the specified project.

### YouTrackClient.listRecentIssues(baseUrl: String, project: String, limit: Int = 5, token: ApiToken): Either[String, List[Issue]]

Fetch recent unresolved issues from a YouTrack project.

### YouTrackClient.searchIssues(baseUrl: String, query: String, limit: Int = 10, token: ApiToken): Either[String, List[Issue]]

Search YouTrack issues by query string.

### CreatedIssue

```scala
case class CreatedIssue(id: String, url: String)
```

## Examples

```scala
// From issue.scala - fetching an issue
ApiToken.fromEnv(Constants.EnvVars.YouTrackApiToken) match
  case None =>
    Left(s"${Constants.EnvVars.YouTrackApiToken} environment variable is not set")
  case Some(token) =>
    config.youtrackBaseUrl match
      case Some(baseUrl) =>
        YouTrackClient.fetchIssue(issueId, baseUrl, token)
      case None =>
        Left(s"YouTrack base URL not configured")

// Creating an issue
ApiToken.fromEnv(Constants.EnvVars.YouTrackApiToken) match
  case Some(token) =>
    val baseUrl = config.youtrackBaseUrl.getOrElse {
      Output.error("YouTrack base URL not configured")
      sys.exit(1)
    }
    val project = config.team
    YouTrackClient.createIssue(project, title, description, baseUrl, token) match
      case Left(error) =>
        Output.error(s"Failed to create issue: $error")
      case Right(createdIssue) =>
        Output.success(s"Issue created: ${createdIssue.id}")
        Output.info(s"URL: ${createdIssue.url}")
  case None =>
    Output.error("YOUTRACK_API_TOKEN not set")

// Listing recent issues
YouTrackClient.listRecentIssues(baseUrl, project, 5, token) match
  case Right(issues) =>
    issues.foreach(i => Output.info(s"${i.id}: ${i.title}"))
  case Left(error) =>
    Output.error(error)
```
