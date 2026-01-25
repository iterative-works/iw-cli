# Phase 7 Context: YouTrack issue creation

**Issue:** IW-103
**Phase:** 7 - YouTrack issue creation
**Story:** Story 4 - Create YouTrack issue with title and description

## User Story

```gherkin
Feature: Create issue in YouTrack project
  As a developer working in a YouTrack-tracked project
  I want to create issues via command line
  So that I can quickly capture bugs and tasks

Scenario: Successfully create YouTrack issue with title and description
  Given I am in a project configured for YouTrack tracker
  And YOUTRACK_API_TOKEN environment variable is set
  And YouTrack base URL is configured as "https://company.youtrack.cloud"
  And project ID is configured as "PROJ"
  When I run "./iw issue create --title 'Memory leak' --description 'Application crashes after 24h runtime'"
  Then a new issue is created in YouTrack project
  And the issue has summary "Memory leak"
  And the issue has description "Application crashes after 24h runtime"
  And I see output "Issue created: PROJ-234"
  And I see output "URL: https://company.youtrack.cloud/issue/PROJ-234"
```

## Acceptance Criteria

1. Command creates issue in configured YouTrack project
2. Returns YouTrack issue ID (e.g., PROJ-234) and URL
3. Validates API token before creation
4. Maps title to YouTrack's "summary" field correctly
5. Handles description (optional)
6. Clear error messages for missing token, invalid project, etc.

## What Was Built in Previous Phases

**Phase 1-4:** Command structure, help display, IssueCreateParser, argument handling
**Phase 5:** Linear issue creation using LinearClient.createIssue
**Phase 6:** GitLab issue creation using GitLabClient infrastructure

Available utilities:
- `IssueCreateParser.parse(args)` - Returns `Either[String, IssueCreateRequest]`
- `IssueCreateRequest(title: String, description: Option[String])`
- `handleCreateSubcommand(args: Seq[String])` - Main entry point
- `createGitHubIssue()`, `createLinearIssue()`, `createGitLabIssue()` - Reference patterns

## Technical Approach

### Key Difference from GitHub/GitLab/Linear

YouTrackClient does **NOT** have a `createIssue` method yet (unlike other trackers). We need to implement:

1. **YouTrackClient.buildCreateIssueUrl** - URL builder for POST endpoint
2. **YouTrackClient.buildCreateIssueBody** - JSON body with project and summary
3. **YouTrackClient.parseCreateIssueResponse** - Parse response to extract issue ID and URL
4. **YouTrackClient.createIssue** - Full method orchestrating the above

### YouTrack REST API

Create issue endpoint:
```
POST {baseUrl}/api/issues
Content-Type: application/json
Authorization: Bearer {token}

{
  "project": {"id": "PROJECT_ID"},
  "summary": "Issue title",
  "description": "Issue description"
}
```

Response returns the created issue with `idReadable` (e.g., "PROJ-234").

### Configuration

YouTrack config uses:
- `youtrackBaseUrl` - Base URL (e.g., "https://company.youtrack.cloud")
- `team` - Project short name (e.g., "PROJ") - used as project ID

### Extension Point

Add case to tracker type match in `handleCreateSubcommand`:
```scala
case IssueTrackerType.YouTrack =>
  createYouTrackIssue(request.title, description, config)
```

## Constraints

- Follow existing patterns from other tracker implementations
- Reuse `CreatedIssue` case class for return type
- Validate API token before attempting creation
- Handle HTTP errors gracefully
- E2E tests require HTTP mocking (or test against real YouTrack instance)
