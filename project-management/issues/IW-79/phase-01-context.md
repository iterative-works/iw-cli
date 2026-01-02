# Phase 1: Display main repository with available issues

**Issue:** IW-79
**Phase:** 1 of 7
**Story:** Display main repository with available issues on dashboard
**Estimated Effort:** 6-8 hours
**Complexity:** Moderate

## Goals

This phase establishes the foundation for worktree spawning by adding a "Main Repository" section to the dashboard that displays available issues from the configured issue tracker (GitHub/Linear/YouTrack).

**Primary objectives:**
1. Fetch available issues from the issue tracker API (recent 50 open issues)
2. Cross-reference with registered worktrees to determine which issues already have worktrees
3. Display issues in a new "Main Repository" section at the top of the dashboard
4. Show issue metadata: ID (clickable link), title, status, assignee
5. Distinguish issues without worktrees (show "Start Worktree" button) vs issues with worktrees
6. Cache issue list to avoid excessive API calls
7. Handle API failures gracefully with user-friendly error messages

## Scope

### In Scope
- New "Main Repository" section in dashboard HTML (above existing worktree list)
- Issue list fetching for GitHub, Linear, and YouTrack trackers
- Server-side rendering of issue cards using ScalaTags
- Issue list caching with TTL (extend existing cache pattern)
- Worktree status enrichment (mark issues that already have worktrees)
- Error handling for API failures (auth errors, rate limits, network errors)
- Basic styling for main repository section and issue cards

### Out of Scope
- Button click functionality (Phase 2)
- Filtering and search (Phase 5)
- Auto-refresh/polling (Phase 6)
- Error handling for worktree creation (Phase 3)
- Concurrent creation protection (Phase 7)
- Client-side JavaScript (except what HTMX requires)
- Advanced pagination (fetch 50 issues, no "load more" yet)

## Dependencies

### Prerequisites (Must Exist)
- **CaskServer infrastructure:** ✓ Exists at `.iw/core/CaskServer.scala`
- **ScalaTags view rendering:** ✓ Used by `DashboardService.renderDashboard`
- **ServerState management:** ✓ Exists at `.iw/core/ServerState.scala`
- **Issue tracker clients:** ✓ Exist but need extension
  - `GitHubClient.fetchIssue` exists, need to add `listIssues`
  - `LinearClient.fetchIssue` exists, need to add `listIssues`
  - `YouTrackClient.fetchIssue` exists, need to add `listIssues`
- **Issue caching pattern:** ✓ `IssueCacheService` exists for single-issue cache
- **StateRepository:** ✓ Exists for reading/writing server state
- **ProjectConfiguration:** ✓ Exists for tracker config

### External Dependencies
- GitHub CLI (`gh`) for GitHub tracker (must be installed and authenticated)
- Linear API token in `LINEAR_API_TOKEN` environment variable
- YouTrack API token in `YOUTRACK_API_TOKEN` environment variable
- Network connectivity to issue tracker APIs

## Technical Approach

### Architecture Pattern: Functional Core, Imperative Shell

Following the existing codebase patterns:
- **Domain layer:** Pure data structures (no I/O)
- **Application layer:** Pure business logic (orchestration)
- **Infrastructure layer:** I/O effects (API calls, file operations)
- **Presentation layer:** View rendering (ScalaTags HTML generation)

### High-Level Flow

```
User opens dashboard (GET /)
  ↓
CaskServer.dashboard() route
  ↓
Load server state (existing worktrees)
  ↓
NEW: Check issue list cache
  ↓
If cache expired or missing:
  - Detect tracker type from config
  - Call appropriate tracker client (GitHub/Linear/YouTrack)
  - Fetch recent 50 open issues
  - Cache results with timestamp
  ↓
Enrich issues with worktree status
  (cross-reference issue IDs with registered worktrees)
  ↓
Render HTML:
  - Main Repository section (new)
  - Issue cards for each available issue
  - Existing worktree list (unchanged)
  ↓
Return HTML response
```

### Issue List Fetching Strategy

**GitHub (via gh CLI):**
```bash
gh issue list --repo owner/repo --limit 50 --state open --json number,title,state,assignees
```

**Linear (GraphQL API):**
```graphql
query {
  issues(first: 50, filter: { state: { type: { nin: ["completed", "canceled"] } } }) {
    nodes {
      identifier
      title
      state { name }
      assignee { displayName }
    }
  }
}
```

**YouTrack (REST API):**
```
GET /api/issues?fields=idReadable,summary,customFields(name,value)&query=State:Open&$top=50
```

### Caching Strategy

Extend `ServerState` to include:
```scala
case class ServerState(
  worktrees: Map[String, WorktreeRegistration],
  issueCache: Map[String, CachedIssue],           // Existing: per-issue cache
  progressCache: Map[String, CachedProgress],     // Existing
  prCache: Map[String, CachedPR],                 // Existing
  reviewStateCache: Map[String, CachedReviewState], // Existing
  issueListCache: Option[CachedIssueList] = None  // NEW: full issue list cache
)
```

```scala
case class CachedIssueList(
  issues: List[AvailableIssue],
  fetchedAt: Instant,
  trackerType: String
)
```

**Cache TTL:** 5 minutes (reasonable balance between freshness and API usage)

**Cache invalidation:** Time-based only (no manual invalidation in Phase 1)

### Worktree Status Enrichment

For each fetched issue:
1. Check if `issueId` exists in `state.worktrees` map
2. If yes: `hasWorktree = true`
3. If no: `hasWorktree = false`

This is a simple O(n) operation where n = number of issues (max 50).

## Files to Modify

### Domain Layer (New Files)

**`.iw/core/domain/AvailableIssue.scala`** (NEW)
```scala
// PURPOSE: Domain model for issues available in the main repository
// PURPOSE: Represents an issue that may or may not have a worktree

package iw.core.domain

case class AvailableIssue(
  id: String,              // Issue ID (e.g., "IW-79", "#132", "ENG-456")
  title: String,
  status: String,
  assignee: Option[String],
  hasWorktree: Boolean,    // Cross-referenced with registered worktrees
  url: String              // Deep link to issue in tracker
)
```

**`.iw/core/domain/CachedIssueList.scala`** (NEW)
```scala
// PURPOSE: Domain model for cached issue list with timestamp
// PURPOSE: Enables TTL-based cache validation

package iw.core.domain

import java.time.Instant

case class CachedIssueList(
  issues: List[AvailableIssue],
  fetchedAt: Instant,
  trackerType: String
)
```

### Domain Layer (Modifications)

**`.iw/core/ServerState.scala`** (MODIFY)
- Add `issueListCache: Option[CachedIssueList] = None` field
- No other changes (backward compatible)

### Application Layer (New Files)

**`.iw/core/application/IssueListService.scala`** (NEW)
```scala
// PURPOSE: Application service for fetching and enriching issue lists
// PURPOSE: Orchestrates issue fetching, caching, and worktree status enrichment

object IssueListService:
  def fetchAvailableIssues(
    trackerType: String,
    config: ProjectConfiguration,
    cache: Option[CachedIssueList],
    now: Instant,
    fetchFn: () => Either[String, List[AvailableIssue]]
  ): Either[String, (List[AvailableIssue], Boolean)]
  
  def enrichWithWorktreeStatus(
    issues: List[AvailableIssue],
    worktrees: Map[String, WorktreeRegistration]
  ): List[AvailableIssue]
  
  def isCacheValid(cache: CachedIssueList, now: Instant): Boolean
```

### Infrastructure Layer (Modifications)

**`.iw/core/GitHubClient.scala`** (MODIFY)
Add new method:
```scala
def listIssues(
  repository: String,
  limit: Int = 50,
  isCommandAvailable: String => Boolean = CommandRunner.isCommandAvailable,
  execCommand: (String, Array[String]) => Either[String, String] = ...
): Either[String, List[Issue]]
```

Implementation:
```bash
gh issue list --repo <repo> --limit 50 --state open --json number,title,state,assignees
```

**`.iw/core/LinearClient.scala`** (MODIFY)
Add new method:
```scala
def listIssues(
  token: ApiToken,
  teamId: String,
  limit: Int = 50,
  backend: SyncBackend = defaultBackend
): Either[String, List[Issue]]
```

GraphQL query for open issues in team.

**`.iw/core/YouTrackClient.scala`** (MODIFY)
Add new method:
```scala
def listIssues(
  baseUrl: String,
  token: ApiToken,
  projectId: String,
  limit: Int = 50
): Either[String, List[Issue]]
```

REST API call to `/api/issues?query=State:Open&$top=50`

### Presentation Layer (New Files)

**`.iw/core/presentation/views/MainRepositoryView.scala`** (NEW)
```scala
// PURPOSE: View component for main repository section of dashboard
// PURPOSE: Renders issue list with cards for available issues

import scalatags.Text.all.*
import iw.core.domain.AvailableIssue

object MainRepositoryView:
  def render(
    issues: List[AvailableIssue],
    error: Option[String]
  ): Frag
```

HTML structure:
```html
<section class="main-repository">
  <h2>Main Repository</h2>
  <div class="issue-list">
    <!-- Issue cards here -->
  </div>
</section>
```

**`.iw/core/presentation/views/AvailableIssueCard.scala`** (NEW)
```scala
// PURPOSE: View component for individual issue card
// PURPOSE: Renders issue metadata and action button

import scalatags.Text.all.*
import iw.core.domain.AvailableIssue

object AvailableIssueCard:
  def render(issue: AvailableIssue): Frag
```

HTML structure:
```html
<div class="issue-card">
  <div class="issue-header">
    <a href="{issue.url}" class="issue-id">{issue.id}</a>
    <span class="issue-status">{issue.status}</span>
  </div>
  <h3 class="issue-title">{issue.title}</h3>
  <div class="issue-meta">
    <span class="assignee">{issue.assignee or "Unassigned"}</span>
  </div>
  <div class="issue-actions">
    {if issue.hasWorktree then "Open Worktree" else "Start Worktree"}
  </div>
</div>
```

Note: Button is non-functional in Phase 1 (just displayed).

### Application Layer (Modifications)

**`.iw/core/application/DashboardService.scala`** (MODIFY)
- Add issue list fetching to `renderDashboard`
- Call `IssueListService.fetchAvailableIssues`
- Call `IssueListService.enrichWithWorktreeStatus`
- Pass issue list to `MainRepositoryView.render`
- Update cache in `ServerState` if issues were fetched

Changes:
```scala
def renderDashboard(
  worktrees: List[WorktreeRegistration],
  issueCache: Map[String, CachedIssue],
  progressCache: Map[String, CachedProgress],
  prCache: Map[String, CachedPR],
  reviewStateCache: Map[String, CachedReviewState],
  issueListCache: Option[CachedIssueList],  // NEW parameter
  config: Option[ProjectConfiguration]
): (String, Map[String, CachedReviewState], Option[CachedIssueList])  // NEW return
```

### Infrastructure Layer (Modifications - if needed)

**`.iw/core/StateRepository.scala`** (VERIFY)
- Should already support reading/writing `ServerState` with any fields
- If using upickle, might need ReadWriter for new domain types
- Test backward compatibility with old state files

## Testing Strategy

### Unit Tests

**Domain Layer:**
- `AvailableIssue` creation and validation
- `CachedIssueList` creation

**Application Layer:**
- `IssueListService.enrichWithWorktreeStatus`:
  - Empty issue list → empty result
  - Issues with no worktrees → all `hasWorktree = false`
  - Issues with matching worktrees → `hasWorktree = true`
  - Mixed scenario
- `IssueListService.isCacheValid`:
  - Fresh cache (fetched 1 minute ago) → valid
  - Stale cache (fetched 10 minutes ago) → invalid
  - Edge case: exactly at TTL boundary

**Infrastructure Layer:**
- `GitHubClient.listIssues`:
  - Successful fetch with mock `gh` output
  - gh CLI not installed → error
  - gh CLI not authenticated → error
  - Repository not found → error
  - Empty issue list → return empty array
- `LinearClient.listIssues`:
  - Successful GraphQL response → parsed issues
  - Invalid token → error
  - Network error → error
  - Empty result → return empty array
- `YouTrackClient.listIssues`:
  - Successful REST response → parsed issues
  - Invalid token → error
  - Project not found → error
  - Empty result → return empty array

**Presentation Layer:**
- `MainRepositoryView.render`:
  - Empty issue list → show "No issues found"
  - Error message → show error banner
  - Issue list with data → render cards
- `AvailableIssueCard.render`:
  - Issue with worktree → "Open Worktree" button
  - Issue without worktree → "Start Worktree" button
  - Issue with assignee → show assignee name
  - Issue without assignee → show "Unassigned"

### Integration Tests

**API Integration:**
- GitHub: Call `gh issue list` with test repository (if CI has gh CLI)
- Linear: Mock HTTP backend with realistic response
- YouTrack: Mock HTTP backend with realistic response

**Cache Integration:**
- Fetch issues → cache populated
- Re-fetch within TTL → cache used (no API call)
- Re-fetch after TTL → cache invalidated, API called

**State Persistence:**
- Fetch issues → save state → reload state → cache persists
- Old state file (no issueListCache) → loads successfully (backward compat)

### E2E Tests (Manual for Phase 1)

**GitHub Tracker:**
1. Configure project with GitHub tracker
2. Ensure `gh` CLI authenticated
3. Start dashboard server
4. Open dashboard in browser
5. Verify: Main Repository section visible
6. Verify: Issue cards show recent 50 open issues
7. Verify: Issue IDs are clickable links to GitHub
8. Verify: Issues without worktrees show "Start Worktree"
9. Start a worktree via CLI: `./iw start IW-79`
10. Refresh dashboard
11. Verify: Issue IW-79 now shows different button state

**Linear Tracker:**
(Same flow, using Linear API token)

**YouTrack Tracker:**
(Same flow, using YouTrack API token and base URL)

**Error Scenarios:**
1. Invalid/expired API token → error message displayed
2. Network offline → error message displayed
3. Tracker API rate limit → error message displayed
4. Empty repository (no issues) → "No issues found" message

### Test Data Strategy

**Mock Data:**
- Create realistic JSON responses for each tracker API
- Include variations: assigned/unassigned, different statuses
- Edge cases: very long titles, special characters, missing fields

**Test Repository:**
- Use `iterative-works/iw-cli` for GitHub E2E tests (has real issues)
- For Linear/YouTrack: use test workspace/project if available

**Fixtures:**
```scala
object IssueListFixtures:
  val sampleGitHubIssues: String = """[
    {"number": 132, "title": "...", "state": "OPEN", "assignees": [...]}
  ]"""
  
  val sampleLinearResponse: String = """{"data": {"issues": {"nodes": [...]}}}"""
  
  val sampleYouTrackResponse: String = """[
    {"idReadable": "IW-79", "summary": "...", "customFields": [...]}
  ]"""
```

## Acceptance Criteria

This phase is considered **DONE** when:

### Functional Requirements
- [ ] Dashboard displays "Main Repository" section at top of page
- [ ] Section shows recent 50 open issues from configured tracker
- [ ] Each issue card displays: ID (clickable), title, status, assignee
- [ ] Issue ID links to issue in tracker (opens in new tab)
- [ ] Issues without worktrees show "Start Worktree" button (non-functional)
- [ ] Issues with existing worktrees are visually distinguished
- [ ] Issue list is cached for 5 minutes to reduce API calls
- [ ] Works with all three tracker types: GitHub, Linear, YouTrack

### Error Handling
- [ ] Invalid/expired API token → user-friendly error message
- [ ] Network error → "Failed to load issues" message
- [ ] Tracker API unavailable → graceful degradation (show error, don't crash)
- [ ] gh CLI not installed (GitHub) → clear installation instructions
- [ ] gh CLI not authenticated (GitHub) → clear authentication instructions

### Non-Functional Requirements
- [ ] No visual regression on existing worktree list
- [ ] Dashboard loads in under 3 seconds (with cache)
- [ ] First load (cache miss) acceptable up to 10 seconds
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] Code follows existing codebase patterns (FP, DDD, clean architecture)

### Code Quality
- [ ] All new files have PURPOSE comments (2 lines)
- [ ] All functions have descriptive names
- [ ] No hardcoded magic numbers (use constants)
- [ ] Error messages are user-friendly (not technical stack traces)
- [ ] No warnings in compilation

### Documentation
- [ ] New domain models documented
- [ ] API functions have doc comments
- [ ] Cache TTL configurable via constant (easy to change)

## Open Questions / Risks

### CLARIFY: Issue list scope
- **Question:** Should we filter issues by assignee (e.g., only show issues assigned to current user)?
- **Current decision:** Show all open issues (50 most recent)
- **Rationale:** Simpler for MVP, filtering added in Phase 5

### CLARIFY: Cache TTL
- **Question:** Is 5 minutes the right TTL?
- **Options:**
  - 5 minutes: Frequent updates, more API calls
  - 15 minutes: Less fresh, fewer API calls
  - Configurable: Let user decide
- **Current decision:** 5 minutes hardcoded
- **Future:** Make configurable in `.iw/config.yaml`

### CLARIFY: Issue limit
- **Question:** Is 50 issues enough?
- **Current decision:** 50 for MVP
- **Rationale:** Most repositories have fewer active issues. Can add pagination later.

### Known Risks

**GitHub Rate Limits:**
- gh CLI has rate limits (5000/hour for authenticated users)
- Mitigation: Caching reduces calls to max 12/hour (5-min TTL)

**Linear/YouTrack API Changes:**
- External APIs can change
- Mitigation: Comprehensive error handling, graceful degradation

**Large Issue Lists:**
- Fetching 50 issues might be slow for some trackers
- Mitigation: Async approach in future (Phase 2+)

**Browser Compatibility:**
- ScalaTags HTML should work everywhere
- Mitigation: Test in Chrome, Firefox, Safari

## Implementation Notes

### Code Style Conventions

Follow existing codebase patterns:
- **Immutability:** All domain models are `case class` (immutable)
- **Pure functions:** Application layer has no side effects
- **Dependency injection:** Infrastructure functions passed as parameters
- **Error handling:** Use `Either[String, T]` for errors
- **Option handling:** Use `Option[T]` for nullable fields

### Performance Considerations

**Issue list fetching:**
- GitHub `gh` CLI: Typically 1-2 seconds
- Linear GraphQL: Typically 500ms-1s
- YouTrack REST: Typically 1-2 seconds

**Worktree status enrichment:**
- O(n × m) where n=issues (50), m=worktrees (typically <10)
- Negligible performance impact

**HTML rendering:**
- ScalaTags is fast (pure Scala, no template parsing)
- 50 issue cards render in <100ms

### Security Considerations

**API Tokens:**
- Never log API tokens
- Never include tokens in HTML
- Read from environment variables only

**URL Construction:**
- Validate issue IDs before building URLs
- Prevent injection attacks (ScalaTags auto-escapes)

**Error Messages:**
- Don't expose internal paths or technical details
- Generic messages to users, detailed logs to server stderr

### Backward Compatibility

**State File:**
- Old state files (no `issueListCache`) must load successfully
- New field is `Option[CachedIssueList] = None` (default)
- upickle should handle this automatically

**Dashboard Rendering:**
- If config missing → no main repository section (graceful degradation)
- If tracker API fails → show error, still render worktree list

## Follow-Up Work (Future Phases)

**Phase 2:**
- Make "Start Worktree" button functional
- POST endpoint to create worktree
- HTMX integration for button click

**Phase 3:**
- Error handling for worktree creation
- User-friendly error messages

**Phase 4:**
- "Open Worktree" functionality
- Modal with tmux attach instructions

**Phase 5:**
- Filtering (assignee, status, labels)
- Search by text

**Phase 6:**
- Auto-refresh via polling
- Live updates without page refresh

**Phase 7:**
- Concurrent creation protection
- Lock mechanism for in-progress creation

---

## Summary Checklist

Before starting implementation:
- [ ] Read this context document fully
- [ ] Review referenced files: `DashboardService.scala`, `ServerState.scala`, tracker clients
- [ ] Understand existing cache pattern (`IssueCacheService`)
- [ ] Verify test environment has tracker credentials
- [ ] Prepare test data (mock JSON responses)

During implementation:
- [ ] TDD: Write test first, make it pass, refactor
- [ ] Commit frequently with descriptive messages
- [ ] Run tests after each significant change
- [ ] Check compilation warnings (must fix all)

Before marking phase complete:
- [ ] All acceptance criteria met
- [ ] All tests pass (unit + integration)
- [ ] Manual E2E test successful for all 3 trackers
- [ ] No compilation warnings
- [ ] Code reviewed against this context
- [ ] Update `tasks.md` to mark Phase 1 complete

---

**Ready to implement? Run `/iterative-works:ag-implement IW-79 --phase 1`**
