# Story-Driven Analysis: Improve issue search in Create Worktree modal

**Issue:** IW-88
**Created:** 2026-01-04
**Status:** Ready for Implementation
**Classification:** Feature

## Problem Statement

The Create Worktree modal currently only supports ID-based search, requiring users to know exact issue IDs. This creates friction when users want to:
- Start work on recently viewed or created issues
- Find issues by remembering title keywords instead of IDs
- Discover what issues are available to work on

This feature improves the worktree creation workflow by making issue discovery easier through:
1. **Immediate context**: Show 5 most recent issues on modal open (before user types)
2. **Flexible search**: Allow searching by title text, not just exact ID match

**User value**: Faster workflow initiation, reduced cognitive load (no need to remember IDs), better issue discoverability.

## User Stories

### Story 1: View recent issues on modal open (GitHub)

```gherkin
Feature: Recent issues display in Create Worktree modal
  As a developer using GitHub tracker
  I want to see recent issues when I open the Create Worktree modal
  So that I can quickly start work without remembering issue IDs

Scenario: Modal opens with 5 recent GitHub issues
  Given the project uses GitHub as tracker
  And there are 10 issues in the repository
  When I open the Create Worktree modal
  Then I see 5 most recent issues displayed
  And each issue shows its ID, title, and status
  And the issues are sorted by creation date (newest first)
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**
- GitHub supports listing issues via `gh issue list --limit 5 --json number,title,state,createdAt`
- Need to create new `GitHubClient.listRecentIssues()` method
- Modal UI needs initial data load (separate from search)
- Frontend needs to render issue list and handle selection

**Acceptance:**
- Modal shows 5 recent issues immediately on open
- Issues fetched from GitHub API using gh CLI
- Each issue displays: ID, title, status badge
- Clicking an issue populates the worktree creation form

---

### Story 2: Search issues by title (GitHub)

```gherkin
Feature: Title-based issue search
  As a developer using GitHub tracker
  I want to search issues by typing title keywords
  So that I can find issues without knowing their exact ID

Scenario: Search finds issue by partial title match
  Given the project uses GitHub tracker
  And there is an issue titled "Improve issue search in Create Worktree modal"
  When I type "search modal" in the search box
  Then I see the matching issue in the results
  And the search is case-insensitive
  And partial word matches are found

Scenario: ID search still works with priority
  Given the project uses GitHub tracker
  And there is issue "IW-88" titled "Search feature"
  When I type "IW-88" in the search box
  Then I see issue IW-88 as the first result
  And exact ID matches take priority over title matches
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**
- GitHub supports search via `gh issue list --search "keywords" --limit 10`
- Need to implement debouncing (300ms) to avoid API spam
- Search should combine ID exact match + title search
- Priority: exact ID match first, then title matches

**Acceptance:**
- Typing in search box filters issues by title (partial, case-insensitive)
- Exact ID match shows as first result (priority)
- Search is debounced to prevent excessive API calls
- Empty search shows recent issues again

---

### Story 3: View recent issues on modal open (Linear)

```gherkin
Feature: Recent issues display for Linear tracker
  As a developer using Linear tracker
  I want to see recent issues when I open the Create Worktree modal
  So that I can quickly start work on relevant issues

Scenario: Modal opens with 5 recent Linear issues
  Given the project uses Linear as tracker
  And the team has 15 issues
  When I open the Create Worktree modal
  Then I see 5 most recent issues from my team
  And each issue shows its ID, title, and state
  And the issues are sorted by creation date (newest first)
```

**Estimated Effort:** 4-6h
**Complexity:** Moderate

**Technical Feasibility:**
- Linear GraphQL API has `issues` query with `first` limit and `orderBy` support
- Query: `issues(first: 5, orderBy: createdAt) { nodes { id identifier title state { name } } }`
- Need to create `LinearClient.listRecentIssues()` method
- Similar structure to GitHub story but different API

**Acceptance:**
- Modal shows 5 recent Linear issues on open
- Issues fetched via Linear GraphQL API
- Respects team configuration from project config
- Same UI rendering as GitHub story

---

### Story 4: Search issues by title (Linear)

```gherkin
Feature: Title-based issue search for Linear
  As a developer using Linear tracker
  I want to search issues by typing title keywords
  So that I can find issues without knowing their exact ID

Scenario: Search finds Linear issue by title
  Given the project uses Linear tracker
  And there is an issue titled "Dashboard refactoring"
  When I type "dashboard" in the search box
  Then I see the matching issue in the results
  And the search uses Linear's built-in search

Scenario: Linear ID format works (e.g., "IW-88")
  Given the project uses Linear tracker
  And there is issue "IW-88"
  When I type "IW-88" in the search box
  Then I see issue IW-88 as the first result
```

**Estimated Effort:** 4-6h
**Complexity:** Moderate

**Technical Feasibility:**
- Linear has `issuesSearch` field in GraphQL API
- Query: `issuesSearch(query: "search text") { nodes { id identifier title state { name } } }`
- Built-in search handles fuzzy matching and relevance
- Need debouncing like GitHub story

**Acceptance:**
- Title search works via Linear's GraphQL search
- Exact ID match prioritized over fuzzy matches
- Search debounced (300ms)
- Results limited to 10 issues

---

### Story 5: View recent issues on modal open (YouTrack)

```gherkin
Feature: Recent issues display for YouTrack tracker
  As a developer using YouTrack tracker
  I want to see recent issues when I open the Create Worktree modal
  So that I can quickly start work on relevant issues

Scenario: Modal opens with 5 recent YouTrack issues
  Given the project uses YouTrack as tracker
  And there are 20 issues in the project
  When I open the Create Worktree modal
  Then I see 5 most recent issues
  And each issue shows its ID, summary, and state
  And the issues are sorted by creation date (newest first)
```

**Estimated Effort:** 4-6h
**Complexity:** Moderate

**Technical Feasibility:**
- YouTrack REST API: `GET /api/issues?fields=idReadable,summary,customFields(name,value(name))&$top=5&$orderBy=created desc`
- Need to create `YouTrackClient.listRecentIssues()` method
- Similar structure to other trackers

**Acceptance:**
- Modal shows 5 recent YouTrack issues on open
- Issues fetched via YouTrack REST API
- Respects baseUrl from project configuration
- Same UI as other tracker stories

---

### Story 6: Search issues by title (YouTrack)

```gherkin
Feature: Title-based issue search for YouTrack
  As a developer using YouTrack tracker
  I want to search issues by typing title keywords
  So that I can find issues without knowing their exact ID

Scenario: Search finds YouTrack issue by summary text
  Given the project uses YouTrack tracker
  And there is an issue with summary "Fix authentication bug"
  When I type "authentication" in the search box
  Then I see the matching issue in the results
  And the search uses YouTrack query syntax

Scenario: YouTrack ID works (e.g., "PROJECT-123")
  Given the project uses YouTrack tracker
  And there is issue "PROJECT-123"
  When I type "PROJECT-123" in the search box
  Then I see issue PROJECT-123 as the first result
```

**Estimated Effort:** 4-6h
**Complexity:** Moderate

**Technical Feasibility:**
- YouTrack REST API supports search: `GET /api/issues?query=summary: {search text}&$top=10`
- Query syntax: `summary: authentication` for text search
- Need to combine exact ID match with text search
- Debouncing required

**Acceptance:**
- Title search works via YouTrack query syntax
- Exact ID match prioritized
- Search debounced (300ms)
- Results limited to 10 issues

---

### Story 7: Load recent issues on modal open

```gherkin
Feature: Recent issues displayed when modal opens
  As a developer
  I want to see recent issues when I open the Create Worktree modal
  So that I can quickly start work without searching

Scenario: Modal opens with recent issues pre-loaded
  Given I am viewing the dashboard
  When I click "Create Worktree" button
  Then a modal overlay appears
  And the modal displays 5 most recent open issues immediately
  And the search input is empty
  And I can click an issue to create a worktree

Scenario: Empty search shows recent issues again
  Given the Create Worktree modal is open
  And I have typed a search query
  When I clear the search input
  Then the recent issues are displayed again
```

**Estimated Effort:** 2-4h
**Complexity:** Low

**Technical Feasibility:**
- Modal already exists with full HTMX integration
- Need to add HTMX `hx-trigger="load"` to load recent issues on modal open
- Or: modify modal endpoint to return HTML with recent issues pre-loaded
- Need new API endpoint `GET /api/issues/recent` (calls through to tracker clients)

**Acceptance:**
- Modal shows 5 recent issues immediately on open (no user action required)
- Clearing search input returns to showing recent issues
- Existing search and worktree creation flow unchanged

---

## Architectural Sketch

**Purpose:** List WHAT components each story needs, not HOW they're implemented.

### For Story 1: View recent issues on modal open (GitHub)

**Domain Layer:**
- No new domain objects needed (reuse existing Issue)

**Application Layer:**
- `IssueSearchService.fetchRecent()` - use case for getting recent issues
  - Input: tracker type, config
  - Output: List[Issue] (up to 5)

**Infrastructure Layer:**
- `GitHubClient.listRecentIssues()` - fetch recent issues from GitHub
  - Uses: gh CLI command
  - Returns: Either[String, List[Issue]]
- `GitHubClient.buildListRecentCommand()` - build gh CLI args

**Presentation Layer:**
- API endpoint: `GET /api/issues/recent`
  - Returns: JSON array of recent issues
- No UI changes yet (covered in Story 7)

---

### For Story 2: Search issues by title (GitHub)

**Domain Layer:**
- `SearchQuery` value object - represents search text
- `SearchResult` - wrapper for search results with relevance

**Application Layer:**
- `IssueSearchService.search()` - use case for searching issues
  - Input: SearchQuery, tracker type, config
  - Output: List[SearchResult]
  - Logic: exact ID match first, then title matches

**Infrastructure Layer:**
- `GitHubClient.searchIssues()` - search GitHub issues
  - Uses: gh CLI search
  - Returns: Either[String, List[Issue]]
- `GitHubClient.buildSearchCommand()` - build search args

**Presentation Layer:**
- API endpoint: `GET /api/issues/search?q={query}`
  - Query param: search text
  - Returns: JSON array of matching issues

---

### For Story 3: View recent issues on modal open (Linear)

**Application Layer:**
- Same `IssueSearchService.fetchRecent()` as Story 1

**Infrastructure Layer:**
- `LinearClient.listRecentIssues()` - fetch recent issues from Linear
  - Uses: GraphQL API query
  - Returns: Either[String, List[Issue]]
- `LinearClient.buildListRecentQuery()` - build GraphQL query

**Presentation Layer:**
- Same API endpoint as Story 1 (adapter pattern routes to correct client)

---

### For Story 4: Search issues by title (Linear)

**Application Layer:**
- Same `IssueSearchService.search()` as Story 2

**Infrastructure Layer:**
- `LinearClient.searchIssues()` - search Linear issues
  - Uses: GraphQL `issuesSearch` field
  - Returns: Either[String, List[Issue]]
- `LinearClient.buildSearchQuery()` - build GraphQL search query

**Presentation Layer:**
- Same API endpoint as Story 2 (adapter pattern routes to correct client)

---

### For Story 5: View recent issues on modal open (YouTrack)

**Application Layer:**
- Same `IssueSearchService.fetchRecent()` as Story 1

**Infrastructure Layer:**
- `YouTrackClient.listRecentIssues()` - fetch recent issues from YouTrack
  - Uses: REST API with query params
  - Returns: Either[String, List[Issue]]
- `YouTrackClient.buildListRecentUrl()` - build API URL

**Presentation Layer:**
- Same API endpoint as Story 1 (adapter pattern routes to correct client)

---

### For Story 6: Search issues by title (YouTrack)

**Application Layer:**
- Same `IssueSearchService.search()` as Story 2

**Infrastructure Layer:**
- `YouTrackClient.searchIssues()` - search YouTrack issues
  - Uses: REST API with query syntax
  - Returns: Either[String, List[Issue]]
- `YouTrackClient.buildSearchUrl()` - build search URL

**Presentation Layer:**
- Same API endpoint as Story 2 (adapter pattern routes to correct client)

---

### For Story 7: Load recent issues on modal open

**Domain Layer:**
- No new domain objects needed

**Application Layer:**
- `IssueSearchService.fetchRecent()` - already defined in Story 1

**Infrastructure Layer:**
- No new infrastructure adapters (reuse from Stories 1, 3, 5)

**Presentation Layer:**
- Modify `CreateWorktreeModal.render()` to:
  - Add HTMX `hx-trigger="load"` to search results container
  - Or: pre-load recent issues in the initial HTML response
- New API endpoint: `GET /api/issues/recent`
  - Returns HTML fragment with recent issues (reuses `SearchResultsView`)
- Modify search endpoint behavior:
  - Empty query (`q=""`) returns recent issues instead of empty list

---

## Resolved Design Decisions

### ✓ RESOLVED: Modal implementation

**Decision:** Modal already exists in `.iw/core/presentation/views/CreateWorktreeModal.scala`

The existing modal has:
- HTMX integration with debounced search (300ms delay)
- Search input calling `/api/issues/search?q={query}`
- Results rendered via `SearchResultsView`
- One-step flow: clicking a result POSTs to `/api/worktrees/create`
- "Already has worktree" badge for existing worktrees

**Impact:** Story 7 is significantly reduced - only need to add "load recent issues on modal open" behavior.

---

### ✓ RESOLVED: Issue selection flow

**Decision:** One-step flow (click issue → worktree created immediately)

This is already implemented in the existing modal. Clicking a search result triggers HTMX POST to `/api/worktrees/create`.

---

### ✓ RESOLVED: Caching strategy

**Decision:** No search result caching - rely on HTMX debouncing only

- HTMX debouncing (300ms) already prevents API spam during typing
- Always hit API for fresh data
- Simpler implementation, no cache invalidation complexity

---

### ✓ RESOLVED: Recent issues definition

**Decision:** Recent = updated date, only open issues

- Sort by last updated date (most active issues first)
- Filter to open issues only (closed issues not relevant for new work)
- All assignees (not filtered by current user)
- Hardcoded to 5 issues (no configuration needed initially)

---

## Remaining Technical Considerations

### API behavior when query is empty

When search input is empty (modal just opened), we should:
1. Call `/api/issues/recent` to fetch 5 most recent open issues
2. Display them in the same search results container

This requires a new endpoint and client methods for listing recent issues.

---

## Total Estimates

**Story Breakdown:**
- Story 1 (Recent issues - GitHub): 6-8 hours
- Story 2 (Search by title - GitHub): 6-8 hours
- Story 3 (Recent issues - Linear): 4-6 hours
- Story 4 (Search by title - Linear): 4-6 hours
- Story 5 (Recent issues - YouTrack): 4-6 hours
- Story 6 (Search by title - YouTrack): 4-6 hours
- Story 7 (Load recent on modal open): 2-4 hours

**Total Range:** 30-44 hours

**Confidence:** High

**Reasoning:**
- GitHub stories (1-2) take longer because they're first - establish patterns, extend `IssueSearchService`, add API endpoints, tests
- Linear stories (3-4) are faster because they follow established patterns from GitHub
- YouTrack stories (5-6) similarly fast, just different API client
- Story 7 is minimal because modal already exists - just need to trigger recent issues load on open

---

## Dependencies

### Story Dependencies

**Sequential dependencies:**
- Story 2 depends on Story 1 (establishes GitHub client patterns)
- Story 3 follows Story 1 (can parallelize if separate developers)
- Story 4 follows Story 2 (can parallelize)
- Story 5 follows Story 1 (can parallelize)
- Story 6 follows Story 2 (can parallelize)
- Story 7 depends on Stories 1, 3, 5 (needs `fetchRecent` methods to exist)

**Recommended sequence for single developer:**
1. Story 1 (establish patterns)
2. Story 2 (complete GitHub support)
3. Story 3 (copy pattern to Linear)
4. Story 4 (copy pattern to Linear)
5. Story 5 (copy pattern to YouTrack)
6. Story 6 (copy pattern to YouTrack)
7. Story 7 (UI integration)

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: Recent issues (GitHub)** - Establishes foundation for API patterns
2. **Story 2: Search by title (GitHub)** - Completes GitHub support
3. **Story 3: Recent issues (Linear)** - Applies pattern to Linear
4. **Story 4: Search by title (Linear)** - Completes Linear support
5. **Story 5: Recent issues (YouTrack)** - Applies pattern to YouTrack
6. **Story 6: Search by title (YouTrack)** - Completes YouTrack support
7. **Story 7: Load recent on modal open** - Triggers recent issues display when modal opens

---

**Analysis Status:** Ready for Implementation

**Next Steps:**
1. Run `/iterative-works:ag-create-tasks IW-88` to generate phase-based task index
2. Run `/iterative-works:ag-implement IW-88` for iterative story-by-story implementation
