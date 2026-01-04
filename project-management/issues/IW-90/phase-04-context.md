# Phase 4 Context: GitLab issue URL generation in search and dashboard

**Issue:** IW-90
**Phase:** 4 of 7
**Estimated Effort:** 2-3 hours
**Story Reference:** Story 4 from analysis.md

---

## Goals

This phase adds GitLab issue URL generation to the search service and dashboard, enabling:
1. Clickable GitLab issue links in search results
2. Correct GitLab issue URLs in the dashboard worktree cards
3. Support for both gitlab.com and self-hosted GitLab instances

When complete, users with GitLab-configured projects will see proper issue links that navigate directly to their GitLab issues.

---

## Scope

### In Scope

- Add GitLab case to `IssueSearchService.buildIssueUrl`
- Add GitLab case to `IssueCacheService.buildIssueUrl`
- Add GitLab case to `DashboardService.buildUrlBuilder`
- URL format: `{baseUrl}/{repository}/-/issues/{number}`
- Default baseUrl to `https://gitlab.com` when not configured
- Extract issue number from formats like `123`, `IW-123`, `#123`
- Unit tests for all new URL building logic

### Out of Scope

- Issue fetching (Phase 1 - complete)
- Error handling (Phase 2 - complete)
- Configuration/init (Phase 3 - complete)
- Issue creation (Phase 5)
- ID parsing changes (Phase 6)

---

## Dependencies

### From Previous Phases

**Phase 1 (complete):**
- `IssueTrackerType.GitLab` enum variant exists in `Config.scala`
- `GitLabClient` exists with `fetchIssue` functionality

**Phase 3 (complete):**
- GitLab configuration is parsed and stored in `ProjectConfiguration`
- `youtrackBaseUrl` field is reused for GitLab baseUrl (semantic naming debt noted)
- GitLab `repository` field supports nested groups (e.g., `group/subgroup/project`)

### Configuration Structure

```hocon
tracker {
  type = gitlab
  repository = "my-org/my-project"      # or "group/subgroup/project"
  teamPrefix = "IW"
  baseUrl = "https://gitlab.company.com"  # Optional, defaults to gitlab.com
}
```

---

## Technical Approach

### GitLab URL Format

GitLab uses a different path structure than GitHub:
- **GitHub:** `https://github.com/{owner}/{repo}/issues/{number}`
- **GitLab:** `https://gitlab.com/{group}/{project}/-/issues/{number}`

The key difference is the `/-/` path segment before `issues`.

### Files to Modify

1. **`.iw/core/IssueSearchService.scala`**
   - Add `case "gitlab"` to `buildIssueUrl` pattern match
   - URL pattern: `{baseUrl}/{repository}/-/issues/{number}`
   - Reuse `extractGitHubIssueNumber` for number extraction (same logic)

2. **`.iw/core/IssueCacheService.scala`**
   - Add `case "gitlab"` to `buildIssueUrl` pattern match
   - Same URL pattern and number extraction logic

3. **`.iw/core/DashboardService.scala`**
   - Add `case "gitlab"` to `buildUrlBuilder` to pass correct configValue
   - GitLab needs both `repository` and `baseUrl` from config

4. **`.iw/core/test/IssueSearchServiceTest.scala`**
   - Add test for GitLab issue search with URL verification

5. **`.iw/core/test/IssueCacheServiceTest.scala`**
   - Add tests for GitLab URL generation:
     - gitlab.com with repository
     - Self-hosted with custom baseUrl
     - Number extraction from various formats

### Implementation Pattern

Following the existing pattern from GitHub and YouTrack:

```scala
// In IssueSearchService.buildIssueUrl
case "gitlab" =>
  // GitLab URL format: https://gitlab.com/owner/project/-/issues/123
  val number = extractGitHubIssueNumber(issueId)  // Same extraction logic
  val baseUrl = config.youtrackBaseUrl.getOrElse("https://gitlab.com")
  config.repository match
    case Some(repo) =>
      s"$baseUrl/$repo/-/issues/$number"
    case None =>
      s"$baseUrl/unknown/repo/-/issues/$number"
```

### Design Decision: baseUrl Field Reuse

Phase 3 made the decision to reuse `youtrackBaseUrl` for GitLab's baseUrl. This creates semantic naming debt but:
- Avoids adding a new config field
- Consistent with how both trackers need custom base URLs for self-hosted instances
- Can be renamed to a generic `baseUrl` in a future refactoring

---

## Testing Strategy

### Unit Tests Required

**IssueSearchServiceTest:**
1. Search with GitLab issue ID returns result with correct URL
2. GitLab URL contains `/-/issues/` path segment

**IssueCacheServiceTest:**
1. `buildIssueUrl` generates gitlab.com URL correctly
2. `buildIssueUrl` generates self-hosted GitLab URL with baseUrl
3. `buildIssueUrl` extracts number from `IW-123` format for GitLab
4. `buildIssueUrl` extracts number from `#123` format for GitLab
5. `buildIssueUrl` handles lowercase `gitlab` tracker type

### Test Data Examples

```scala
// gitlab.com (default)
IssueCacheService.buildIssueUrl("123", "GitLab", Some("my-org/my-project"), None)
// Expected: "https://gitlab.com/my-org/my-project/-/issues/123"

// Self-hosted
IssueCacheService.buildIssueUrl("456", "GitLab", Some("team/app"), Some("https://gitlab.company.com"))
// Expected: "https://gitlab.company.com/team/app/-/issues/456"

// Nested groups
IssueCacheService.buildIssueUrl("789", "GitLab", Some("company/team/project"), None)
// Expected: "https://gitlab.com/company/team/project/-/issues/789"

// With team prefix
IssueCacheService.buildIssueUrl("IW-123", "GitLab", Some("my-org/my-project"), None)
// Expected: "https://gitlab.com/my-org/my-project/-/issues/123"
```

---

## Acceptance Criteria

1. **gitlab.com URLs work correctly:**
   - Given GitLab tracker with repository `my-org/my-project` and no baseUrl
   - When URL is built for issue `123`
   - Then URL is `https://gitlab.com/my-org/my-project/-/issues/123`

2. **Self-hosted GitLab URLs work correctly:**
   - Given GitLab tracker with repository `team/app` and baseUrl `https://gitlab.company.com`
   - When URL is built for issue `456`
   - Then URL is `https://gitlab.company.com/team/app/-/issues/456`

3. **Nested groups work correctly:**
   - Given GitLab tracker with repository `company/team/project`
   - When URL is built for issue `789`
   - Then URL is `https://gitlab.com/company/team/project/-/issues/789`

4. **Issue number extraction works:**
   - IDs like `123`, `IW-123`, `#123` all extract to `123`

5. **Search results include correct URLs:**
   - Search for GitLab issue returns result with clickable URL

6. **Dashboard displays correct URLs:**
   - Worktree cards show GitLab issue links with correct URL format

7. **All existing tests pass:**
   - Linear, GitHub, YouTrack URL generation unchanged
   - No regressions in search or dashboard functionality

---

## Code References

### Current buildIssueUrl in IssueSearchService

File: `/home/mph/Devel/projects/iw-cli-IW-90/.iw/core/IssueSearchService.scala` (lines 69-99)

```scala
private def buildIssueUrl(issueId: String, config: ProjectConfiguration): String =
  config.trackerType.toString.toLowerCase match
    case "linear" =>
      val team = config.team.toLowerCase
      s"https://linear.app/$team/issue/$issueId"
    case "github" =>
      config.repository match
        case Some(repo) =>
          val number = extractGitHubIssueNumber(issueId)
          s"https://github.com/$repo/issues/$number"
        case None =>
          s"https://github.com/issues/$issueId"
    case "youtrack" =>
      config.youtrackBaseUrl match
        case Some(baseUrl) =>
          val cleanBaseUrl = baseUrl.stripSuffix("/")
          s"$cleanBaseUrl/issue/$issueId"
        case None =>
          s"https://youtrack.example.com/issue/$issueId"
    case _ =>
      s"#$issueId"
```

### Current buildIssueUrl in IssueCacheService

File: `/home/mph/Devel/projects/iw-cli-IW-90/.iw/core/IssueCacheService.scala` (lines 72-95)

```scala
def buildIssueUrl(issueId: String, trackerType: String, configValue: Option[String]): String =
  trackerType.toLowerCase match
    case "linear" =>
      s"https://linear.app/issue/$issueId"
    case "youtrack" =>
      configValue match
        case Some(url) => s"$url/issue/$issueId"
        case None => s"https://youtrack.example.com/issue/$issueId"
    case "github" =>
      val issueNumber = extractGitHubIssueNumber(issueId)
      configValue match
        case Some(repository) => s"https://github.com/$repository/issues/$issueNumber"
        case None => s"https://github.com/unknown/repo/issues/$issueNumber"
    case _ =>
      s"#unknown-tracker-$issueId"
```

### buildUrlBuilder in DashboardService

File: `/home/mph/Devel/projects/iw-cli-IW-90/.iw/core/DashboardService.scala` (lines 216-226)

```scala
private def buildUrlBuilder(
  trackerType: String,
  config: Option[ProjectConfiguration]
): String => String =
  (issueId: String) =>
    val configValue = trackerType.toLowerCase match
      case "github" => config.flatMap(_.repository)
      case "youtrack" => config.flatMap(_.youtrackBaseUrl)
      case _ => None
    IssueCacheService.buildIssueUrl(issueId, trackerType, configValue)
```

---

## Notes for Implementation

1. **API Change Consideration:** `IssueCacheService.buildIssueUrl` currently takes a single `configValue`. GitLab needs both `repository` and `baseUrl`. Options:
   - Pass a tuple or case class
   - Modify signature to take two optional values
   - Build URL directly with all needed config in DashboardService

2. **Recommended Approach:** Keep `IssueCacheService.buildIssueUrl` simple by having the caller (DashboardService) pre-compute the full baseUrl when constructing the configValue for GitLab.

3. **extractGitHubIssueNumber Reuse:** The same extraction logic works for GitLab. The function name is GitHub-specific but the logic is generic. Consider renaming to `extractIssueNumber` in a future refactoring.

---

## Verification Checklist

Before marking phase complete:
- [ ] `buildIssueUrl` handles GitLab case in IssueSearchService
- [ ] `buildIssueUrl` handles GitLab case in IssueCacheService
- [ ] `buildUrlBuilder` handles GitLab case in DashboardService
- [ ] gitlab.com URLs have correct format with `/-/issues/`
- [ ] Self-hosted GitLab URLs use configured baseUrl
- [ ] Nested group repositories work correctly
- [ ] Issue number extraction works for all formats
- [ ] Unit tests cover all GitLab URL scenarios
- [ ] All existing tests pass (no regressions)
- [ ] Code compiles without warnings
