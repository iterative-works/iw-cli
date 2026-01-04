# Story-Driven Analysis: Support GitLab issue tracker via glab

**Issue:** IW-90
**Created:** 2026-01-04
**Status:** Draft
**Classification:** Feature

## Problem Statement

Currently, iw-cli supports three issue trackers: Linear (GraphQL API), YouTrack (REST API), and GitHub (via gh CLI). Teams using GitLab for issue tracking cannot use iw-cli's worktree and issue management features.

**User Need:** GitLab users need the same worktree-to-issue integration that GitHub, Linear, and YouTrack users enjoy - including issue fetching, display, and dashboard integration.

**Business Value:** Extends iw-cli's market to GitLab-based teams, completing support for the four major issue tracking platforms. This increases adoption potential and provides feature parity across popular development platforms.

## User Stories

### Story 1: Fetch and display GitLab issue via glab CLI

```gherkin
Feature: GitLab issue fetching
  As a developer using GitLab for issue tracking
  I want to fetch and display GitLab issues via `iw issue`
  So that I can view issue details without leaving my terminal

Scenario: Fetch existing GitLab issue successfully
  Given GitLab tracker is configured with repository "my-org/my-project"
  And glab CLI is installed and authenticated
  And issue "123" exists in GitLab with title "Add dark mode"
  When I run `iw issue 123`
  Then I see the issue formatted with ID, title, status, and assignee
  And the issue description is displayed
  And the command exits with status 0
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**
This story is moderate complexity because:
- Pattern established by GitHubClient (CLI-based) provides clear template
- glab CLI JSON output format needs investigation
- Authentication validation similar to gh CLI prerequisite checks
- Issue ID format for GitLab is numeric (like GitHub), simpler than Linear/YouTrack

**Key Technical Challenges:**
- Understanding glab CLI's JSON output schema
- Mapping GitLab issue states to our domain model
- Handling glab CLI authentication status checking

**Acceptance:**
- User can fetch GitLab issue by numeric ID
- Issue details (title, status, assignee, description) display correctly
- Clear error messages if glab not installed or not authenticated
- Works from any directory (uses configured repository)

---

### Story 2: Handle GitLab-specific error conditions gracefully

```gherkin
Feature: GitLab error handling
  As a developer using GitLab tracker
  I want clear error messages when issues cannot be fetched
  So that I can quickly resolve configuration or authentication problems

Scenario: glab CLI not installed
  Given GitLab tracker is configured
  And glab CLI is not installed
  When I run `iw issue 123`
  Then I see error message explaining glab is required
  And I see installation instructions for glab
  And the command exits with status 1

Scenario: glab not authenticated
  Given GitLab tracker is configured
  And glab CLI is installed but not authenticated
  When I run `iw issue 123`
  Then I see error message explaining authentication is required
  And I see instructions to run `glab auth login`
  And the command exits with status 1

Scenario: Issue not found
  Given GitLab tracker is configured
  And glab CLI is authenticated
  And issue "999" does not exist
  When I run `iw issue 999`
  Then I see error message "Issue not found"
  And the command exits with status 1
```

**Estimated Effort:** 3-4h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward because:
- GitHubClient provides exact pattern for prerequisite validation
- Error handling patterns well-established in existing clients
- glab CLI likely has similar authentication status command to gh

**Acceptance:**
- Missing glab CLI detected with helpful installation guidance
- Authentication failures detected with login instructions
- Network errors reported clearly
- Issue not found returns appropriate error

---

### Story 3: Configure GitLab tracker during iw init

```gherkin
Feature: GitLab tracker initialization
  As a developer starting with iw-cli in a GitLab project
  I want `iw init` to detect GitLab and configure it automatically
  So that I can start using iw-cli without manual configuration

Scenario: Auto-detect GitLab from git remote
  Given I am in a git repository
  And the git remote URL is "https://gitlab.com/my-org/my-project.git"
  And I run `iw init`
  When the tool detects the git remote
  Then it suggests "gitlab" as the tracker type
  And it extracts repository as "my-org/my-project"
  And it writes gitlab configuration to .iw/config.conf

Scenario: Manually select GitLab during init
  Given I am in a git repository
  And I run `iw init`
  When I select "gitlab" as tracker type
  Then I am prompted for GitLab repository (owner/project format)
  And the configuration is written with tracker.type = gitlab
  And I see next steps including `glab auth login`
```

**Estimated Effort:** 4-6h
**Complexity:** Moderate

**Technical Feasibility:**
Moderate complexity because:
- Config.scala enum needs new GitLab variant
- TrackerDetector needs GitLab remote URL patterns
- ConfigSerializer needs GitLab HOCON serialization
- GitRemote extraction logic might need extension for gitlab.com and self-hosted instances

**Key Technical Challenges:**
- Supporting both gitlab.com and self-hosted GitLab instances
- Extracting repository owner/name from various GitLab URL formats (SSH vs HTTPS)
- Determining if we need baseUrl for self-hosted instances (similar to YouTrack)

**Acceptance:**
- `iw init` detects gitlab.com in git remote and suggests GitLab tracker
- User can manually select GitLab as tracker type
- Configuration file contains tracker.type = gitlab and repository = "owner/project"
- Self-hosted GitLab URLs are handled (optional baseUrl in config)

---

### Story 4: GitLab issue URL generation in search and dashboard

```gherkin
Feature: GitLab issue URL building
  As a developer using GitLab tracker
  I want issue URLs generated correctly for my GitLab instance
  So that I can click through to issues from search results and dashboard

Scenario: Build URL for gitlab.com issue
  Given GitLab tracker is configured with repository "my-org/my-project"
  And no custom base URL is set
  When the system builds URL for issue "123"
  Then the URL is "https://gitlab.com/my-org/my-project/-/issues/123"

Scenario: Build URL for self-hosted GitLab issue
  Given GitLab tracker is configured with repository "team/app"
  And base URL is "https://gitlab.company.com"
  When the system builds URL for issue "456"
  Then the URL is "https://gitlab.company.com/team/app/-/issues/456"
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward because:
- IssueSearchService already has buildIssueUrl with pattern matching by tracker
- GitLab URL format is well-documented: `{baseUrl}/{owner}/{project}/-/issues/{number}`
- Similar to GitHub with just a different path structure

**Acceptance:**
- URLs generated correctly for gitlab.com
- URLs generated correctly for self-hosted GitLab (using baseUrl from config)
- Search results include clickable GitLab URLs
- Dashboard displays correct GitLab issue links

---

### Story 5: Create GitLab issues via glab CLI

```gherkin
Feature: GitLab issue creation
  As a developer using GitLab tracker
  I want to create issues via `iw feedback`
  So that I can capture bugs and feature requests without leaving terminal

Scenario: Create bug issue in GitLab
  Given GitLab tracker is configured with repository "my-org/my-project"
  And glab CLI is authenticated
  When I run `iw feedback` and enter:
    | Field       | Value                           |
    | Type        | bug                             |
    | Title       | Login button doesn't work       |
    | Description | Clicking login does nothing     |
  Then a new GitLab issue is created with label "bug"
  And I see the issue URL
  And the command exits with status 0

Scenario: Create feature request in GitLab
  Given GitLab tracker is configured
  And glab CLI is authenticated
  When I create a feature request via `iw feedback`
  Then the issue is created with label "feature" or "enhancement"
  And I see the created issue URL
```

**Estimated Effort:** 4-6h
**Complexity:** Moderate

**Technical Feasibility:**
Moderate complexity because:
- GitHubClient.createIssue provides exact pattern
- glab CLI has issue create command with similar flags
- Need to handle label mapping (bug vs feature/enhancement)
- Possible label fallback if labels don't exist (like GitHub implementation)

**Key Technical Challenges:**
- Understanding glab issue create output format
- Label mapping for issue types (GitLab may use different label conventions)
- Handling GitLab-specific issue fields (milestones, epics) - defer to future

**Acceptance:**
- Bug reports create GitLab issues with appropriate label
- Feature requests create GitLab issues with appropriate label
- Created issue URL is returned and displayed
- Works with both gitlab.com and self-hosted instances

---

### Story 6: GitLab issue ID parsing and validation

```gherkin
Feature: GitLab issue ID handling
  As a developer using GitLab tracker
  I want to reference issues by their numeric ID
  So that I can work with GitLab's native issue numbering

Scenario: Parse numeric GitLab issue ID
  Given GitLab tracker is configured
  When I parse issue ID "123"
  Then it is recognized as valid GitLab issue ID
  And the numeric value "123" is extracted

Scenario: Infer GitLab issue from branch name
  Given GitLab tracker is configured
  And I am in a worktree with branch "123-add-dark-mode"
  When I run `iw issue` without arguments
  Then it infers issue ID "123" from the branch name
  And fetches issue "123" from GitLab
```

**Estimated Effort:** 3-4h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward because:
- GitLab uses simple numeric IDs (like GitHub)
- IssueId.parse already handles numeric format for GitHub
- Branch name inference pattern exists in issue.scala
- No team prefix needed for GitLab (unlike GitHub's teamPrefix requirement)

**Acceptance:**
- Numeric issue IDs (e.g., "123") are parsed correctly
- Branch names with issue numbers (e.g., "123-feature-name") are parsed
- No team prefix required (GitLab IDs are project-scoped)
- Error messages are clear when ID format is invalid

---

### Story 7: Integration testing with real glab CLI

```gherkin
Feature: GitLab integration test coverage
  As a developer maintaining iw-cli
  I want comprehensive tests for GitLab integration
  So that I can confidently release and maintain the feature

Scenario: Unit tests for GitLabClient
  Given GitLabClient module exists
  When I run unit tests
  Then I verify glab command building logic
  And I verify JSON response parsing
  And I verify prerequisite validation
  And I verify error handling

Scenario: E2E test with glab CLI
  Given a test GitLab repository exists
  And glab CLI is installed and authenticated
  When I run E2E tests for GitLab tracker
  Then I verify issue fetching end-to-end
  And I verify issue creation end-to-end
  And I verify error scenarios
```

**Estimated Effort:** 4-6h
**Complexity:** Moderate

**Technical Feasibility:**
Moderate complexity because:
- Unit test patterns established (see GitHubClientTest, LinearClientTest)
- E2E testing may require test GitLab repository setup
- Mock injection points exist in all clients (execCommand parameter)
- Need to handle glab CLI availability in CI environment

**Key Technical Challenges:**
- Setting up test GitLab project for E2E tests
- Handling glab authentication in CI (may need to skip or mock E2E tests)
- Ensuring tests don't create spam issues in real repositories

**Acceptance:**
- Unit tests cover all GitLabClient functions
- Mock-based tests verify command building and parsing
- E2E tests verify actual glab CLI integration (when available)
- Test coverage matches existing clients (GitHub, Linear, YouTrack)

---

## Architectural Sketch

**Purpose:** List WHAT components each story needs, not HOW they're implemented.

### For Story 1: Fetch and display GitLab issue via glab CLI

**Domain Layer:**
- No new domain entities needed (Issue model already exists)
- IssueId parsing already handles numeric IDs

**Application Layer:**
- No changes needed (IssueSearchService already abstracts tracker differences)

**Infrastructure Layer:**
- `GitLabClient` object with functions:
  - `validateGlabPrerequisites(repository: String): Either[GlabPrerequisiteError, Unit]`
  - `buildFetchIssueCommand(issueNumber: String, repository: String): Array[String]`
  - `parseFetchIssueResponse(jsonOutput: String, issueNumber: String): Either[String, Issue]`
  - `fetchIssue(issueNumber: String, repository: String): Either[String, Issue]`
- Enum `GlabPrerequisiteError` (similar to GitHub's pattern)

**Presentation Layer:**
- Update `issue.scala` command to handle `IssueTrackerType.GitLab` case
- Add GitLab-specific fetch logic in fetchIssue function

---

### For Story 2: Handle GitLab-specific error conditions

**Infrastructure Layer:**
- GitLabClient functions:
  - `formatGlabNotInstalledError(): String`
  - `formatGlabNotAuthenticatedError(): String`
  - `isAuthenticationError(error: String): Boolean`
- Error detection logic in validateGlabPrerequisites

**Presentation Layer:**
- Error handling in issue.scala for GitLab tracker type

---

### For Story 3: Configure GitLab tracker during iw init

**Domain Layer:**
- Extend `IssueTrackerType` enum: add `GitLab` variant
- Update `GitRemote.host` to recognize gitlab.com and other GitLab hosts
- Update `GitRemote.repositoryOwnerAndName` to handle GitLab URLs (or make generic)

**Application Layer:**
- Update `TrackerDetector.suggestTracker` to detect GitLab hosts
- Update `ConfigSerializer.toHocon` to serialize GitLab config
- Update `ConfigSerializer.fromHocon` to parse GitLab config

**Infrastructure Layer:**
- Update `ConfigFileRepository` to handle GitLab tracker type

**Presentation Layer:**
- Update `init.scala` to support GitLab as tracker option
- Add GitLab-specific prompts (repository, optional baseUrl)

---

### For Story 4: GitLab issue URL generation

**Application Layer:**
- Update `IssueSearchService.buildIssueUrl` to handle GitLab case
- Add GitLab URL pattern: `{baseUrl}/{repo}/-/issues/{number}`

**Domain Layer:**
- Optional: Add `gitlabBaseUrl` field to ProjectConfiguration (or reuse youtrackBaseUrl as generic baseUrl)

---

### For Story 5: Create GitLab issues via glab CLI

**Infrastructure Layer:**
- GitLabClient functions:
  - `buildCreateIssueCommand(repository: String, title: String, description: String, issueType: FeedbackParser.IssueType): Array[String]`
  - `buildCreateIssueCommandWithoutLabel(repository: String, title: String, description: String): Array[String]`
  - `parseCreateIssueResponse(output: String): Either[String, CreatedIssue]`
  - `createIssue(repository: String, title: String, description: String, issueType: FeedbackParser.IssueType): Either[String, CreatedIssue]`
  - `isLabelError(error: String): Boolean`

**Presentation Layer:**
- Update `feedback.scala` to handle GitLab tracker type
- Add GitLab-specific issue creation logic

---

### For Story 6: GitLab issue ID parsing

**Domain Layer:**
- Update `IssueId.parse` to handle GitLab numeric IDs (likely already works)
- Update `IssueId.fromBranch` to extract numeric ID from branch name (likely already works)

**No new components needed** - existing IssueId parsing should handle numeric format.

---

### For Story 7: Integration testing

**Test Layer:**
- `GitLabClientTest.scala` (unit tests):
  - Test command building functions
  - Test JSON parsing with sample responses
  - Test error handling
  - Test prerequisite validation
- E2E tests in BATS:
  - Test issue fetching with real glab CLI
  - Test issue creation with test repository
  - Test error scenarios

---

## Technical Risks & Uncertainties

### CLARIFY: GitLab base URL configuration

**Description:** GitLab supports both gitlab.com (SaaS) and self-hosted instances. We need to decide how to handle base URLs in configuration.

**Questions to answer:**
1. Should we reuse `tracker.baseUrl` field (like YouTrack) or add `tracker.gitlabBaseUrl`?
2. Should gitlab.com be the default when baseUrl is not provided?
3. Do we need to validate the base URL format during init?
4. Should we detect self-hosted GitLab from git remote URL and prompt for baseUrl?

**Options:**
- **Option A: Reuse tracker.baseUrl field**
  - Pros: Consistent with YouTrack, no new config fields
  - Cons: baseUrl mixing across tracker types could be confusing
  - Implementation: Default to "https://gitlab.com" if not set and tracker is GitLab

- **Option B: Add dedicated tracker.gitlabBaseUrl field**
  - Pros: Explicit separation, clear configuration
  - Cons: More config fields, not DRY
  - Implementation: Optional field in ProjectConfiguration

- **Option C: Make tracker.baseUrl generic "base URL for this tracker"**
  - Pros: One field serves all trackers that need custom URLs
  - Cons: Requires refactoring YouTrack usage
  - Implementation: Rename youtrackBaseUrl to baseUrl in domain model

**Impact:** Affects Story 3 (init configuration) and Story 4 (URL building). Decision impacts ProjectConfiguration schema and migration path for existing configs.

---

### CLARIFY: glab CLI JSON output format

**Description:** We need to understand glab's exact JSON output format for issue view and issue create commands to parse responses correctly.

**Questions to answer:**
1. What is the exact JSON schema for `glab issue view --json` output?
2. Does glab support custom --json field selection like gh does?
3. What fields are available (state, assignee, labels, description, milestone, etc.)?
4. What is the output format for `glab issue create` (URL only, or JSON)?
5. How does glab represent null/empty fields in JSON?

**Options:**
- **Option A: Research glab documentation first**
  - Pros: Understand before implementing
  - Cons: Documentation might be incomplete
  - Implementation: Check glab --help and official docs

- **Option B: Experiment with glab CLI directly**
  - Pros: Get real output samples
  - Cons: Requires authenticated GitLab access
  - Implementation: Create test issue and inspect output

- **Option C: Implement based on gh pattern and iterate**
  - Pros: Fast start, similar CLI tools often have similar formats
  - Cons: May require rework if assumptions wrong
  - Implementation: Copy GitHubClient pattern, adjust during testing

**Impact:** Affects Story 1 (parsing logic) and Story 5 (create response parsing). Critical for correct implementation.

---

### CLARIFY: GitLab issue type labels

**Description:** GitLab and GitHub may have different label conventions for issue types (bug vs feature).

**Questions to answer:**
1. What labels does GitLab use by default for issue types?
2. Are labels "bug" and "feature" standard, or does GitLab use "enhancement", "incident", etc.?
3. Should we make labels configurable per-tracker?
4. Should we handle label creation if they don't exist (or just fail gracefully)?
5. Does glab CLI support `--label` flag similar to gh?

**Options:**
- **Option A: Hardcode GitLab-specific labels**
  - Pros: Simple, follows existing pattern
  - Cons: Assumes standard labels exist
  - Implementation: Map bug -> "bug", feature -> "feature" (or "enhancement")

- **Option B: Make issue type labels configurable in config.conf**
  - Pros: Flexible for different GitLab configurations
  - Cons: More complex setup, more config surface
  - Implementation: Add tracker.labels.bug and tracker.labels.feature to config

- **Option C: Use glab's scoped labels (type::bug, type::feature)**
  - Pros: Follows GitLab best practices
  - Cons: Assumes scoped labels are set up
  - Implementation: Use GitLab scoped label format

**Impact:** Affects Story 5 (issue creation). Low risk - can implement fallback like GitHub (create without labels if label assignment fails).

---

### CLARIFY: GitLab repository URL extraction

**Description:** GitLab URLs can have various formats including self-hosted instances, group/subgroup hierarchies.

**Questions to answer:**
1. Should we support nested groups (e.g., "company/team/project")?
2. How do we distinguish between gitlab.com and self-hosted GitLab in git remote?
3. Do we need special handling for GitLab subgroups?
4. Should repository path be the full path (group/subgroup/project) or shortened?

**Options:**
- **Option A: Support only two-level paths (owner/project)**
  - Pros: Simple, matches GitHub pattern
  - Cons: Doesn't work with GitLab nested groups
  - Implementation: Validate path has exactly one slash

- **Option B: Support full GitLab path (group/subgroup/project)**
  - Pros: Works with GitLab's group structure
  - Cons: More complex parsing and validation
  - Implementation: Allow multiple slashes in repository path

- **Option C: Extract full path, validate it exists via glab CLI**
  - Pros: Accurate, handles all GitLab structures
  - Cons: Requires glab during init (dependency)
  - Implementation: Parse full path from URL, validate with `glab repo view`

**Impact:** Affects Story 3 (init) and all GitLab URL/command building. Medium risk - GitLab's group structure is more complex than GitHub's.

---

### CLARIFY: glab authentication check command

**Description:** We need to validate glab authentication before attempting API calls, similar to gh auth status.

**Questions to answer:**
1. Does glab have an equivalent to `gh auth status`?
2. What exit codes does glab use for authentication failures?
3. How do we check authentication without making actual API calls?
4. Should we cache authentication status or check every time?

**Options:**
- **Option A: Use `glab auth status` if it exists**
  - Pros: Direct check, consistent with gh pattern
  - Cons: Command might not exist
  - Implementation: Run `glab auth status`, check exit code

- **Option B: Use `glab api user` as authentication probe**
  - Pros: Actual API call validates auth fully
  - Cons: Slower, makes unnecessary API call
  - Implementation: Try simple API call, check for 401

- **Option C: Skip authentication check, rely on error handling**
  - Pros: Simpler, fewer commands
  - Cons: Worse UX, unclear error messages
  - Implementation: Let glab commands fail, parse errors

**Impact:** Affects Story 1 and Story 2 (prerequisite validation). Medium risk - poor authentication checks lead to confusing errors.

---

## Total Estimates

**Story Breakdown:**
- Story 1 (Fetch and display GitLab issue): 6-8 hours
- Story 2 (Error handling): 3-4 hours
- Story 3 (Init configuration): 4-6 hours
- Story 4 (URL generation): 2-3 hours
- Story 5 (Issue creation): 4-6 hours
- Story 6 (ID parsing): 3-4 hours
- Story 7 (Testing): 4-6 hours

**Total Range:** 26-37 hours

**Confidence:** Medium

**Reasoning:**
- **Existing patterns reduce risk:** GitHubClient provides nearly identical pattern (CLI-based, JSON parsing, prerequisite checks)
- **glab CLI unknowns add uncertainty:** Need to validate glab output formats and authentication patterns through experimentation
- **Configuration complexity:** GitLab's self-hosted support and nested groups add configuration complexity beyond GitHub
- **Testing requires setup:** E2E tests may need test GitLab repository and authentication setup
- **Medium confidence appropriate:** Pattern is proven (GitHub), but GitLab-specific details need clarification

---

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests**: Pure logic, command building, parsing functions
2. **Integration Tests**: glab CLI execution (with mocked subprocess for fast tests)
3. **E2E Scenario Tests**: Actual glab CLI calls with test repository

**Story-Specific Testing Notes:**

**Story 1 (Fetch issue):**
- Unit: Test buildFetchIssueCommand arguments, test parseFetchIssueResponse with sample JSON
- Integration: Mock execCommand to return sample glab output, verify parsing
- E2E: Create test issue in GitLab, fetch via iw issue, verify output

**Story 2 (Error handling):**
- Unit: Test error message formatters, test isAuthenticationError detection
- Integration: Mock execCommand to return various error exit codes, verify error handling
- E2E: Test with glab not installed, glab not authenticated, issue not found

**Story 3 (Init configuration):**
- Unit: Test GitRemote.host extraction for gitlab.com and self-hosted URLs
- Unit: Test ConfigSerializer for GitLab config format
- Integration: Test TrackerDetector with GitLab remote URLs
- E2E: Run `iw init` in test repo with GitLab remote, verify config.conf

**Story 4 (URL generation):**
- Unit: Test IssueSearchService.buildIssueUrl for GitLab case
- Unit: Test both gitlab.com and self-hosted URL patterns
- Integration: Verify URL generation in search results

**Story 5 (Issue creation):**
- Unit: Test buildCreateIssueCommand arguments, test parseCreateIssueResponse
- Integration: Mock glab issue create response, verify parsing
- E2E: Create real issue via iw feedback, verify in GitLab UI

**Story 6 (ID parsing):**
- Unit: Test IssueId.parse with numeric GitLab IDs
- Unit: Test IssueId.fromBranch with numeric branch prefixes
- Integration: Verify parsing in issue command context

**Story 7 (Testing):**
- Meta: Verify test coverage metrics for GitLabClient
- Meta: Ensure test suite runs in CI environment
- Meta: Document E2E test setup requirements

**Test Data Strategy:**
- Unit tests use hardcoded sample JSON responses (committed in test fixtures)
- Integration tests use mocked execCommand with realistic glab output
- E2E tests require dedicated test GitLab project (document setup in README)
- Consider using GitLab's test instance or creating temporary projects for E2E

**Regression Coverage:**
- Verify existing GitHub, Linear, YouTrack tests still pass
- Add tests that exercise tracker selection logic with all four types
- Test config migration (old configs without GitLab should still work)
- Test IssueSearchService with all four tracker types

---

## Deployment Considerations

### Database Changes
No database - configuration is file-based (.iw/config.conf).

### Configuration Changes

**Story 3 - New config format:**
```hocon
tracker {
  type = gitlab
  repository = "my-org/my-project"
  baseUrl = "https://gitlab.com"  # Optional, defaults to gitlab.com
}

project {
  name = my-project
}
```

**Environment Variables:**
- No new environment variables required (glab manages its own authentication)
- glab stores tokens via `glab auth login` (similar to gh)

### Rollout Strategy

**Can deploy per story:**
- Story 1-2: Core functionality (fetch + errors) - deployable as read-only GitLab support
- Story 3-4: Configuration + URLs - completes read-only integration
- Story 5: Issue creation - adds write capability
- Story 6-7: Polish + tests - improve robustness

**Feature flag approach not needed** - tracker type selection in config naturally gates feature.

**Migration path:**
- Existing configs (GitHub, Linear, YouTrack) continue working without changes
- New GitLab projects run `iw init` and select GitLab
- No breaking changes to config schema

### Rollback Plan

**If GitLab support fails in production:**
1. Users can switch back to previous tracker in .iw/config.conf
2. No data loss - GitLab issues remain in GitLab
3. Worktrees remain functional (not tracker-dependent)
4. Worst case: Users can delete .iw/ directory and re-run `iw init` with different tracker

**Safe rollback because:**
- No destructive operations on user data
- Configuration is file-based and user-editable
- glab CLI failures don't affect git operations
- Each tracker implementation is isolated (no shared state)

---

## Dependencies

### Prerequisites
**Before starting Story 1:**
- glab CLI must be available for testing and development
- Access to test GitLab repository (gitlab.com or self-hosted)
- Understanding of glab CLI JSON output format (resolve CLARIFY marker)

**Development environment:**
- glab CLI installed: `brew install glab` (macOS) or from https://gitlab.com/gitlab-org/cli
- glab authenticated: `glab auth login`
- Test GitLab project for E2E tests

### Story Dependencies

**Sequential dependencies:**
1. **Story 1 must complete before Story 2** - Error handling builds on fetch implementation
2. **Story 3 can be parallel with Story 1-2** - Init configuration is independent of client
3. **Story 4 depends on Story 3** - URL building needs config structure defined
4. **Story 5 depends on Story 1** - Issue creation uses same prerequisite validation as fetch
5. **Story 6 can be parallel** - ID parsing is mostly domain logic, minimal tracker coupling
6. **Story 7 depends on all others** - Testing validates complete implementation

**Recommended parallelization:**
- **Phase 1:** Story 1 + Story 3 (parallel)
- **Phase 2:** Story 2 + Story 6 (parallel, both build on Phase 1)
- **Phase 3:** Story 4 + Story 5 (parallel, both need config and client)
- **Phase 4:** Story 7 (final testing)

### External Blockers

**glab CLI availability:**
- Development machines need glab installed
- CI environment needs glab in PATH (add to CI setup)
- Documentation needs glab installation instructions

**GitLab API stability:**
- glab CLI is official GitLab tool (low risk of breaking changes)
- JSON output format should be stable (similar to gh)
- GitLab API changes could break glab (mitigated by version pinning)

**Test infrastructure:**
- Need test GitLab project for E2E tests
- Consider gitlab.com free tier or self-hosted test instance
- E2E tests might be slower than unit tests (glab CLI invocation overhead)

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: Fetch and display GitLab issue** - Establishes core GitLabClient and validates glab integration pattern
2. **Story 3: Configure GitLab tracker during init** - (Parallel with Story 1) Enables users to configure GitLab, completes config layer
3. **Story 2: Error handling** - Builds on Story 1, improves UX before public testing
4. **Story 6: ID parsing** - (Parallel with Story 2) Completes domain layer, enables branch inference
5. **Story 4: URL generation** - Enables search and dashboard integration with GitLab
6. **Story 5: Issue creation** - Adds write capability, completes feature parity with other trackers
7. **Story 7: Testing** - Final hardening, ensures quality before release

**Iteration Plan:**

- **Iteration 1 (Stories 1, 3): Core foundation** - 10-14 hours
  - Deliverable: Can fetch GitLab issues via `iw issue 123`
  - Deliverable: Can configure GitLab during `iw init`
  - Value: Basic GitLab support functional, can start dogfooding

- **Iteration 2 (Stories 2, 6): Robustness and UX** - 6-8 hours
  - Deliverable: Clear error messages for common failures
  - Deliverable: Branch name inference works for GitLab
  - Value: Production-ready read-only GitLab support

- **Iteration 3 (Stories 4, 5): Full integration** - 6-9 hours
  - Deliverable: Search and dashboard show GitLab URLs
  - Deliverable: Can create GitLab issues via `iw feedback`
  - Value: Feature parity with GitHub tracker

- **Iteration 4 (Story 7): Quality assurance** - 4-6 hours
  - Deliverable: Comprehensive test coverage
  - Deliverable: E2E tests validate real glab CLI
  - Value: Confidence for release and maintenance

**Total across iterations:** 26-37 hours

---

## Documentation Requirements

- [x] Gherkin scenarios serve as living documentation
- [ ] API documentation: Document GitLabClient public interface (similar to GitHubClient)
- [ ] README updates:
  - Add GitLab to list of supported trackers
  - Add glab CLI installation instructions
  - Add GitLab authentication setup (`glab auth login`)
- [ ] Configuration guide:
  - Document tracker.type = gitlab
  - Document repository format for GitLab
  - Document optional baseUrl for self-hosted GitLab
- [ ] Migration guide: None needed (new feature, no breaking changes)
- [ ] User-facing docs:
  - Add GitLab example to `iw issue` documentation
  - Add GitLab example to `iw init` workflow
  - Document GitLab-specific limitations (if any)
- [ ] Developer docs:
  - Document test GitLab repository setup for E2E tests
  - Document glab CLI output format (samples in test fixtures)

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. **Resolve CLARIFY markers** (especially glab JSON format and authentication check)
2. Experiment with glab CLI to validate JSON output format assumptions
3. Decide on baseUrl configuration approach (reuse vs dedicated field)
4. Run `/iterative-works:ag-create-tasks IW-90` to map stories to implementation phases
5. Run `/iterative-works:ag-implement IW-90` for iterative story-by-story implementation

---

This analysis provides a story-driven roadmap for adding GitLab support to iw-cli. The vertical slicing approach ensures each story delivers independent user value, and the CLARIFY markers identify key decisions needed before implementation. The 26-37 hour estimate reflects medium confidence due to proven patterns (GitHubClient) balanced against GitLab-specific unknowns.
