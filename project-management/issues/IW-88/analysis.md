# Story-Driven Analysis: Improve issue search in Create Worktree modal

**Issue:** IW-88
**Created:** 2026-01-04
**Status:** Draft
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

### Story 7: Create Worktree modal UI with HTMX

```gherkin
Feature: Interactive Create Worktree modal
  As a developer
  I want a modal interface for creating worktrees
  So that I can search and select issues without leaving the dashboard

Scenario: Open modal from dashboard
  Given I am viewing the dashboard
  When I click "Create Worktree" button
  Then a modal overlay appears
  And the modal shows a search input
  And the modal displays 5 recent issues immediately
  And I can interact with search without page reload

Scenario: Select issue and create worktree
  Given the Create Worktree modal is open
  And I see recent issues listed
  When I click on an issue
  Then the issue ID is selected
  And I can confirm worktree creation
  And the modal closes after creation
```

**Estimated Effort:** 8-12h
**Complexity:** Complex

**Technical Feasibility:**
- Need to create modal HTML structure with Scalatags
- HTMX for dynamic search: `hx-get="/api/issues/search?q={value}" hx-trigger="keyup changed delay:300ms"`
- Need new API endpoints in CaskServer:
  - `GET /api/issues/recent` - fetch recent issues
  - `GET /api/issues/search?q=query` - search issues
- CSS for modal overlay and styling
- JavaScript for modal open/close behavior

**Acceptance:**
- Modal opens/closes smoothly with overlay
- Search input triggers HTMX requests with debounce
- Issue list updates dynamically without page reload
- Selected issue triggers worktree creation
- Modal is accessible (keyboard navigation, ESC to close)

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

### For Story 7: Create Worktree modal UI with HTMX

**Domain Layer:**
- No new domain objects needed

**Application Layer:**
- No new application services (uses existing from Stories 1-6)

**Infrastructure Layer:**
- No new infrastructure adapters

**Presentation Layer:**
- View: `CreateWorktreeModalView.render()` - Scalatags HTML for modal
  - Modal overlay
  - Search input with HTMX attributes
  - Issue list container (target for HTMX swaps)
  - Create button + cancel button
- View: `IssueListView.render()` - HTML fragment for issue list
  - Reusable for both recent issues and search results
- API endpoints (already defined in Stories 1-2):
  - `GET /api/issues/recent`
  - `GET /api/issues/search?q={query}`
- Dashboard modification: Add "Create Worktree" button that triggers modal

**CSS:**
- `.modal-overlay` - full-screen semi-transparent background
- `.modal-content` - centered modal box
- `.issue-search-input` - styled search input
- `.issue-list-item` - clickable issue cards

**JavaScript:**
- Modal open/close handlers
- ESC key listener to close modal
- Click outside modal to close

---

## Technical Risks & Uncertainties

### CLARIFY: Modal implementation approach - new modal or build from scratch?

The issue description references "Create Worktree modal" from IW-79, but I found no modal implementation in the current codebase. This creates uncertainty about what exists vs. what needs to be built.

**Questions to answer:**
1. Does a Create Worktree modal already exist in the codebase (possibly in a file I didn't examine)?
2. If it exists, where is it located and what's its current structure?
3. If it doesn't exist, should we build it as part of this issue or defer to a separate issue?
4. What is the expected user flow: button on dashboard → modal → select issue → create worktree?

**Options:**
- **Option A**: Modal exists but wasn't found - User points to existing implementation
- **Option B**: Modal doesn't exist yet - Build it in Story 7
- **Option C**: Modal is partially implemented (stub) - Complete it

**Impact:** Affects Story 7 scope significantly. If modal doesn't exist, Story 7 estimate may increase to 12-16h.

---

### CLARIFY: Search API rate limiting and caching strategy

Each tracker has different rate limits and API characteristics. We need to decide on caching strategy for search results.

**Questions to answer:**
1. Should search results be cached? For how long?
2. How do we handle API rate limiting for GitHub/Linear/YouTrack?
3. Should we implement client-side debouncing, server-side debouncing, or both?
4. What happens if API is unreachable or rate-limited during search?

**Options:**
- **Option A**: No search result caching - Always hit API
- **Option B**: Short-term cache (5 minutes) - Cache per query
- **Option C**: Client-side debouncing only - No server cache

**Impact:** Affects Infrastructure Layer implementation for all search methods.

---

### CLARIFY: Issue selection flow and worktree creation

The acceptance criteria say "results update/filter as user types" but don't specify the complete flow from search to worktree creation.

**Questions to answer:**
1. After selecting an issue, does the modal stay open for confirmation or immediately create worktree?
2. Do we need a worktree path input field in the modal, or auto-generate paths?
3. Should the modal validate that a worktree doesn't already exist for that issue?
4. What happens on error (e.g., git worktree add fails) - show error in modal or close modal?

**Options:**
- **Option A**: Two-step flow - Select issue → Confirm → Create
- **Option B**: One-step flow - Select issue → Immediately create
- **Option C**: Hybrid - Select issue → Show preview with path → Create

**Impact:** Affects Story 7 UI design and API endpoint design.

---

### CLARIFY: Recent issues definition - what makes an issue "recent"?

Stories 1, 3, 5 fetch "5 most recent issues" but don't specify the criteria.

**Questions to answer:**
1. Recent by creation date, or by last updated date?
2. Should we filter by status (e.g., only open issues)?
3. Should we respect issue assignment (e.g., only issues assigned to current user)?
4. Do we need configuration for "recent issues count" (currently hardcoded to 5)?

**Options:**
- **Option A**: Recent = created date, all statuses, all assignees
- **Option B**: Recent = updated date, only open issues, all assignees
- **Option C**: Recent = created date, only open issues, current user's team

**Recommendation:** Start with Option B (updated date, open only) as it's most useful for "recent work."

---

## Total Estimates

**Story Breakdown:**
- Story 1 (Recent issues - GitHub): 6-8 hours
- Story 2 (Search by title - GitHub): 6-8 hours
- Story 3 (Recent issues - Linear): 4-6 hours
- Story 4 (Search by title - Linear): 4-6 hours
- Story 5 (Recent issues - YouTrack): 4-6 hours
- Story 6 (Search by title - YouTrack): 4-6 hours
- Story 7 (Create Worktree modal UI): 8-12 hours

**Total Range:** 36-52 hours

**Confidence:** Medium

**Reasoning:**
- GitHub stories (1-2) take longer because they're first - establish patterns, create `IssueSearchService`, API endpoints, tests
- Linear stories (3-4) are faster because they follow established patterns from GitHub
- YouTrack stories (5-6) similarly fast, just different API client
- Story 7 (Modal UI) is complex because HTMX integration, CSS, JavaScript, accessibility considerations
- CLARIFY markers add uncertainty - if modal doesn't exist, Story 7 could grow to 12-16h

---

## Dependencies

### Story Dependencies

**Sequential dependencies:**
- Story 2 depends on Story 1 (establishes GitHub client patterns)
- Story 3 follows Story 1 (can parallelize if separate developers)
- Story 4 follows Story 2 (can parallelize)
- Story 5 follows Story 1 (can parallelize)
- Story 6 follows Story 2 (can parallelize)
- Story 7 depends on Stories 1-6 (needs API endpoints to exist)

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
7. **Story 7: Create Worktree modal UI** - Integrates all APIs into user-facing feature

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Resolve CLARIFY markers with Michal
2. Run `/iterative-works:ag-create-tasks IW-88` to generate phase-based task index
3. Run `/iterative-works:ag-implement IW-88` for iterative story-by-story implementation
