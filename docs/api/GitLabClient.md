# GitLabClient

> GitLab API client via glab CLI for issue operations.

## Import

```scala
import iw.core.adapters.*
```

## Prerequisites

Requires `glab` CLI installed and authenticated:
- Install: https://gitlab.com/gitlab-org/cli
- Authenticate: `glab auth login`

## API

### GitLabClient.validateGlabPrerequisites(repository: String, ...): Either[GlabPrerequisiteError, Unit]

Validate glab CLI is installed and authenticated.

### GitLabClient.fetchIssue(issueIdValue: String, repository: String, ...): Either[String, Issue]

Fetch a GitLab issue. The `issueIdValue` should be the full ID (e.g., "PROJ-123") - the number is extracted for the API call.

### GitLabClient.createIssue(repository: String, title: String, description: String, issueType: IssueType, ...): Either[String, CreatedIssue]

Create a new GitLab issue with a label (bug or feature). Falls back to creating without label if label doesn't exist.

### GitLabClient.createIssue(repository: String, title: String, description: String): Either[String, CreatedIssue]

Create a new GitLab issue without labels.

### Error Helpers

```scala
GitLabClient.isNotFoundError(error: String): Boolean
GitLabClient.isAuthenticationError(error: String): Boolean
GitLabClient.isNetworkError(error: String): Boolean
GitLabClient.formatGlabNotInstalledError(): String
GitLabClient.formatGlabNotAuthenticatedError(): String
GitLabClient.formatIssueNotFoundError(issueId: String, repository: String): String
GitLabClient.formatNetworkError(details: String): String
```

## Examples

```scala
// From issue.scala - fetching an issue with error handling
config.repository match
  case Some(repository) =>
    GitLabClient.fetchIssue(issueId.value, repository) match
      case Left(error) if GitLabClient.isNotFoundError(error) =>
        Left(GitLabClient.formatIssueNotFoundError(issueId.value, repository))
      case Left(error) if GitLabClient.isAuthenticationError(error) =>
        Left(GitLabClient.formatGlabNotAuthenticatedError())
      case Left(error) if GitLabClient.isNetworkError(error) =>
        Left(GitLabClient.formatNetworkError(error))
      case result => result
  case None =>
    Left("GitLab repository not configured")

// Creating an issue
GitLabClient.createIssue(repository, title, description) match
  case Left(error) =>
    Output.error(s"Failed to create issue: $error")
  case Right(createdIssue) =>
    Output.success(s"Issue created: #${createdIssue.id}")
    Output.info(s"URL: ${createdIssue.url}")
```
