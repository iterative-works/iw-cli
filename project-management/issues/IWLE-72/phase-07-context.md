# Phase 7 Context: Fetch and display issue details

**Issue:** IWLE-72
**Phase:** 7 of 7
**Status:** Ready for Implementation

---

## 1. Goals

Implement the `./iw issue [issue-id]` command that fetches and displays issue details from configured trackers (Linear and YouTrack), with support for inferring the issue ID from the current branch.

**Primary objectives:**
- Fetch issue details from Linear API using GraphQL
- Fetch issue details from YouTrack API using REST
- Display key fields: ID, title, status, assignee, description
- Infer issue ID from current git branch when not provided explicitly
- Clean formatting with proper Unicode rendering

**Non-goals:**
- Do NOT update or modify issues (read-only operation)
- Do NOT cache issue data locally
- Do NOT implement pagination for long descriptions

---

## 2. Scope

### In Scope

1. **Issue domain model:**
   - `Issue` entity with ID, title, status, assignee, description
   - `IssueTracker` trait for tracker abstraction

2. **Linear integration:**
   - GraphQL API for fetching issue by ID
   - Parse issue identifier format (e.g., "IWLE-123")
   - Extract: title, state name, assignee display name, description

3. **YouTrack integration:**
   - REST API for fetching issue by ID
   - Parse issue identifier format (e.g., "IWLE-123")
   - Extract: title, state, assignee full name, description

4. **Command implementation:**
   - `./iw issue [issue-id]` - fetch and display
   - Branch inference when issue-id omitted
   - Configuration lookup for tracker type and token

5. **Error handling:**
   - Issue not found (404)
   - Invalid issue ID format
   - Missing/invalid API token
   - Network errors

### Out of Scope

- Issue creation, update, or deletion
- Comment display or management
- Attachments or linked issues
- Interactive mode for browsing issues
- Issue list/search functionality

---

## 3. Dependencies

### From Previous Phases

**Phase 1:**
- `Output` utilities for formatted console output (info, error, success, keyValue, section)

**Phase 2:**
- `ConfigFileRepository` for reading project configuration
- `ProjectConfiguration` with tracker type and team

**Phase 3:**
- `LinearClient.validateToken()` - pattern for Linear API calls
- `ProcessAdapter` for command execution (if needed)

**Phase 4:**
- `IssueId` value object for parsing and validating issue IDs
- `IssueId.fromBranch()` for extracting issue ID from branch name

**Phase 5:**
- `GitAdapter.getCurrentBranch()` for branch detection

### External Dependencies

- sttp HTTP client (already in use by LinearClient)
- Linear GraphQL API: `https://api.linear.app/graphql`
- YouTrack REST API: `https://youtrack.e-bs.cz/api/issues/{issueId}` (configured per project)

### Environment Variables

- `LINEAR_API_TOKEN` - required for Linear tracker
- `YOUTRACK_API_TOKEN` - required for YouTrack tracker

---

## 4. Technical Approach

### Domain Layer

**Issue entity:**
```scala
case class Issue(
  id: String,
  title: String,
  status: String,
  assignee: Option[String],
  description: Option[String]
)
```

**IssueTracker trait:**
```scala
trait IssueTracker:
  def fetchIssue(issueId: IssueId): Either[String, Issue]
```

### Infrastructure Layer

**LinearIssueTracker:**
```scala
object LinearIssueTracker extends IssueTracker:
  def fetchIssue(issueId: IssueId): Either[String, Issue] =
    // GraphQL query for issue by identifier
    // Parse response into Issue
```

**YouTrackIssueTracker:**
```scala
object YouTrackIssueTracker extends IssueTracker:
  def fetchIssue(issueId: IssueId): Either[String, Issue] =
    // REST GET to /api/issues/{idReadable}
    // Parse JSON response into Issue
```

### Linear GraphQL Query

```graphql
query GetIssue($id: String!) {
  issue(id: $id) {
    identifier
    title
    state { name }
    assignee { displayName }
    description
  }
}
```

Note: Linear uses `issue(id: $id)` where `id` can be the readable identifier like "IWLE-123".

### YouTrack REST Endpoint

```
GET /api/issues/{idReadable}?fields=idReadable,summary,state(name),Assignee(fullName),description
```

Headers:
- `Authorization: Bearer {YOUTRACK_API_TOKEN}`
- `Accept: application/json`

### Command Workflow

```
1. Parse arguments (optional issue-id)
2. If no issue-id provided:
   - Get current branch via GitAdapter
   - Extract issue ID from branch via IssueId.fromBranch()
3. Validate issue ID format
4. Load configuration (tracker type)
5. Get API token from environment
6. Create appropriate tracker (Linear or YouTrack)
7. Fetch issue
8. Display formatted output
```

### Output Format

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
IWLE-123: Add user login
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Status:     In Progress
Assignee:   Michal Příhoda

Description:
  Users need to be able to log in to the application
  using their email and password.
```

### Error Handling

| Scenario | Response |
|----------|----------|
| Invalid issue ID format | Error with format hint |
| Issue not found | Error: "Issue {ID} not found" |
| Missing API token | Error with hint to set environment variable |
| Invalid API token | Error: "API token is invalid or expired" |
| Network error | Error with underlying message |
| No branch (not in git repo) | Error: "Not in a git repository" |
| Cannot infer issue from branch | Error: "Could not determine issue ID from branch name. Use: iw issue IWLE-123" |

---

## 5. Files to Modify

### New Files

| File | Purpose |
|------|---------|
| `.iw/core/Issue.scala` | Issue entity and IssueTracker trait |
| `.iw/core/YouTrackClient.scala` | YouTrack REST API client |
| `.iw/core/test/IssueTest.scala` | Unit tests for Issue entity |
| `.iw/core/test/LinearIssueTrackerTest.scala` | Integration tests for Linear |
| `.iw/core/test/YouTrackIssueTrackerTest.scala` | Integration tests for YouTrack |
| `.iw/test/issue.bats` | E2E tests |

### Modified Files

| File | Changes |
|------|---------|
| `.iw/commands/issue.scala` | Full implementation (currently stub) |
| `.iw/core/LinearClient.scala` | Add `fetchIssue(issueId)` method |

### Existing Files to Reuse

- `.iw/core/IssueId.scala` - Issue ID parsing and validation
- `.iw/core/Git.scala` - `getCurrentBranch()` for branch inference
- `.iw/core/Output.scala` - Console output utilities
- `.iw/core/ConfigRepository.scala` - Configuration loading
- `.iw/core/Config.scala` - `ProjectConfiguration`, `IssueTrackerType`

---

## 6. Testing Strategy

### Unit Tests (IssueTest.scala)

1. `Issue` case class construction
2. `Issue` with all fields populated
3. `Issue` with optional fields as None

### Integration Tests - LinearIssueTracker

1. `fetchIssue` returns issue for valid ID (requires valid token)
2. `fetchIssue` returns error for non-existent issue
3. `fetchIssue` returns error for invalid token
4. `fetchIssue` handles missing assignee gracefully
5. `fetchIssue` handles empty description gracefully

### Integration Tests - YouTrackIssueTracker

1. `fetchIssue` returns issue for valid ID (requires valid token)
2. `fetchIssue` returns error for non-existent issue
3. `fetchIssue` returns error for invalid token
4. `fetchIssue` handles missing assignee gracefully
5. `fetchIssue` handles empty description gracefully

### E2E Tests (issue.bats)

1. Error for invalid issue ID format
2. Error when config file missing
3. Error when API token not set
4. Success message format (with mocked API response)
5. Infers issue ID from branch name
6. Error when cannot infer from branch (on main branch)

Note: Full E2E tests against real APIs are manual/CI-only due to API token requirements.

---

## 7. Acceptance Criteria

From analysis.md Story 5:

- [x] Displays key issue fields: ID, title, status, assignee, description
- [x] Can infer issue ID from branch or accept explicit parameter
- [x] Works with both Linear and YouTrack trackers
- [x] Clean formatting with proper Unicode rendering
- [x] Clear error for non-existent issues

### Scenario Verification

```gherkin
Scenario: Display issue from current branch
  Given I am in worktree "kanon-IWLE-123"
  And current branch is "IWLE-123"
  And Linear issue "IWLE-123" has:
    | field       | value                |
    | title       | Add user login       |
    | status      | In Progress          |
    | assignee    | Michal Příhoda       |
    | description | Users need to log in |
  When I run "./iw issue"
  Then I see "IWLE-123: Add user login"
  And I see "Status: In Progress"
  And I see "Assignee: Michal Příhoda"
  And I see the description "Users need to log in"

Scenario: Display specific issue
  Given I am in the main worktree
  When I run "./iw issue IWLE-456"
  Then I see details for issue IWLE-456 from Linear

Scenario: Issue not found
  Given I am in the main worktree
  When I run "./iw issue INVALID-999"
  Then I see the error "Issue INVALID-999 not found"
  And the command returns exit code 1
```

---

## 8. Implementation Notes

### Linear API Details

**Authentication:**
- Header: `Authorization: lin_api_xxxxx` (token directly, no "Bearer" prefix)

**GraphQL Endpoint:**
- URL: `https://api.linear.app/graphql`
- Method: POST

**Issue Query by Identifier:**
```graphql
query {
  issue(id: "IWLE-123") {
    identifier
    title
    state { name }
    assignee { displayName }
    description
  }
}
```

**Response Structure:**
```json
{
  "data": {
    "issue": {
      "identifier": "IWLE-123",
      "title": "Add user login",
      "state": { "name": "In Progress" },
      "assignee": { "displayName": "Michal Příhoda" },
      "description": "Users need to log in..."
    }
  }
}
```

### YouTrack API Details

**Authentication:**
- Header: `Authorization: Bearer {token}`

**REST Endpoint:**
- URL: `https://youtrack.e-bs.cz/api/issues/{idReadable}`
- Method: GET
- Query params: `fields=idReadable,summary,customFields(name,value(name,fullName)),description`

Note: YouTrack uses custom fields for State and Assignee. The exact field structure depends on project configuration.

**Common Custom Field Names:**
- State: Look for field with name "State"
- Assignee: Look for field with name "Assignee"

**Response Structure:**
```json
{
  "idReadable": "IWSD-123",
  "summary": "Fix login bug",
  "description": "Users cannot log in...",
  "customFields": [
    {
      "name": "State",
      "value": { "name": "In Progress" }
    },
    {
      "name": "Assignee",
      "value": { "fullName": "John Smith" }
    }
  ]
}
```

### JSON Parsing

Use simple string parsing or add a JSON library dependency. Options:
1. **upickle** - lightweight, Scala-native
2. **circe** - popular, well-documented
3. **Manual parsing** - for simple cases (not recommended)

Recommendation: Use **upickle** for minimal dependencies:
```scala
//> using dep com.lihaoyi::upickle:4.0.2
```

---

## 9. Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Linear API changes | Pin to documented GraphQL schema, handle missing fields gracefully |
| YouTrack API variations | Document required fields, warn on missing custom fields |
| Rate limiting | No mitigation needed for single-request use case |
| Network timeout | Set reasonable timeout (10s), show clear error |
| Unicode encoding issues | Use UTF-8 throughout, test with non-ASCII characters |

---

## 10. Related Documentation

- Analysis: `analysis.md` → Story 5
- Linear GraphQL API: https://developers.linear.app/docs/graphql/working-with-the-graphql-api
- YouTrack REST API: https://www.jetbrains.com/help/youtrack/standalone/api-issues-get.html
- Phase 3 implementation: `phase-03-context.md` (LinearClient pattern)
- Phase 5 implementation: `phase-05-context.md` (branch inference)
