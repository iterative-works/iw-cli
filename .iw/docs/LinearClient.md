# LinearClient

> Linear API client via GraphQL for issue operations.

## Import

```scala
import iw.core.adapters.*
import iw.core.model.*
```

## Prerequisites

Requires `LINEAR_API_TOKEN` environment variable set with a valid Linear API token.

## API

### LinearClient.validateToken(token: ApiToken, backend: SyncBackend = defaultBackend): Boolean

Check if a Linear API token is valid.

### LinearClient.fetchIssue(issueId: IssueId, token: ApiToken, backend: SyncBackend = defaultBackend): Either[String, Issue]

Fetch an issue from Linear by ID (e.g., "IWLE-123").

### LinearClient.createIssue(title: String, description: String, teamId: String, token: ApiToken, labelIds: Seq[String] = Seq.empty, backend: SyncBackend = defaultBackend): Either[String, CreatedIssue]

Create a new Linear issue. Optionally attach labels by UUID.

### LinearClient.listRecentIssues(teamId: String, limit: Int = 5, token: ApiToken, backend: SyncBackend = defaultBackend): Either[String, List[Issue]]

Fetch recent unresolved issues from a Linear team.

### LinearClient.searchIssues(query: String, limit: Int = 10, token: ApiToken, backend: SyncBackend = defaultBackend): Either[String, List[Issue]]

Search Linear issues by text query.

### CreatedIssue

```scala
case class CreatedIssue(id: String, url: String)
```

## Examples

```scala
// From issue.scala - fetching an issue
ApiToken.fromEnv(Constants.EnvVars.LinearApiToken) match
  case None =>
    Left(s"${Constants.EnvVars.LinearApiToken} environment variable is not set")
  case Some(token) =>
    LinearClient.fetchIssue(issueId, token)

// Creating an issue with labels
ApiToken.fromEnv(Constants.EnvVars.LinearApiToken) match
  case Some(token) =>
    val teamId = config.team
    LinearClient.createIssue(title, description, teamId, token) match
      case Left(error) =>
        Output.error(s"Failed to create issue: $error")
      case Right(createdIssue) =>
        Output.success(s"Issue created: #${createdIssue.id}")
        Output.info(s"URL: ${createdIssue.url}")
  case None =>
    Output.error("LINEAR_API_TOKEN not set")

// Validate token
if LinearClient.validateToken(token) then
  CheckResult.Success("Token valid")
else
  CheckResult.Error("Token invalid", "Check LINEAR_API_TOKEN")
```
