# GitHubClient

> GitHub API client via gh CLI for issue and PR operations.

## Import

```scala
import iw.core.adapters.*
```

## Prerequisites

Requires `gh` CLI installed and authenticated:
- Install: https://cli.github.com/
- Authenticate: `gh auth login`

## API

### GitHubClient.validateGhPrerequisites(repository: String, ...): Either[GhPrerequisiteError, Unit]

Validate gh CLI is installed and authenticated.

### GitHubClient.fetchIssue(issueIdValue: String, repository: String, ...): Either[String, Issue]

Fetch a GitHub issue. The `issueIdValue` should be the full ID (e.g., "IWCLI-132") - the number is extracted for the API call.

### GitHubClient.createIssue(repository: String, title: String, description: String, issueType: IssueType, ...): Either[String, CreatedIssue]

Create a new GitHub issue with a label (bug or feature). Falls back to creating without label if label doesn't exist.

### GitHubClient.createIssue(repository: String, title: String, description: String): Either[String, CreatedIssue]

Create a new GitHub issue without labels.

### GitHubClient.listRecentIssues(repository: String, limit: Int = 5, ...): Either[String, List[Issue]]

Fetch recent open issues from GitHub.

### GitHubClient.searchIssues(repository: String, query: String, limit: Int = 10, ...): Either[String, List[Issue]]

Search GitHub issues by text (title and body).

### CreatedIssue

```scala
case class CreatedIssue(id: String, url: String)
```

## Examples

```scala
// From issue.scala - fetching an issue
config.repository match
  case Some(repository) =>
    GitHubClient.fetchIssue(issueId.value, repository) match
      case Right(issue) =>
        val formatted = IssueFormatter.format(issue)
        println(formatted)
      case Left(error) =>
        Output.error(s"Failed to fetch issue: $error")
  case None =>
    Output.error("GitHub repository not configured")

// Creating an issue
GitHubClient.createIssue(repository, title, description) match
  case Left(error) =>
    Output.error(s"Failed to create issue: $error")
  case Right(createdIssue) =>
    Output.success(s"Issue created: #${createdIssue.id}")
    Output.info(s"URL: ${createdIssue.url}")

// Listing recent issues
GitHubClient.listRecentIssues(repository, limit = 5) match
  case Right(issues) =>
    issues.foreach(i => Output.info(s"${i.id}: ${i.title}"))
  case Left(error) =>
    Output.error(error)
```
