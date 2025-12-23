# Story-Driven Analysis: Add GitHub Issues support using gh CLI

**Issue:** IWLE-132
**Created:** 2025-12-22
**Status:** Ready for Implementation
**Classification:** Feature

## Problem Statement

The `feedback` command currently only supports Linear for issue creation, which creates test pollution in the production Linear workspace (IWLE-131) and requires users to manage Linear API tokens. For open-source projects like iw-cli itself, GitHub Issues would be more appropriate and accessible.

Additionally, the current architecture requires implementing custom HTTP clients for each tracker (LinearClient uses GraphQL, YouTrackClient uses REST API), which adds complexity and maintenance burden.

**What user need are we addressing?**
- Users working on open-source GitHub projects need a native way to create and track issues without Linear/YouTrack accounts
- E2E tests need a way to create test issues without polluting production Linear workspace
- Contributors to iw-cli need to use the tool's issue tracking features on the project itself

**Why is this valuable?**
- Removes dependency on Linear API tokens for GitHub-based projects
- Provides a simpler authentication model (gh CLI handles auth)
- Enables iw-cli to "eat its own dog food" by using GitHub Issues for tracking
- Solves the E2E test pollution problem
- Lowers the barrier to entry for open-source contributors

## User Stories

### Story 1: Initialize project with GitHub tracker

```gherkin
Feature: Project initialization with GitHub Issues
  As a developer working on a GitHub repository
  I want to configure iw-cli to use GitHub Issues
  So that I can track work without needing Linear or YouTrack

Scenario: Initialize new project with GitHub tracker
  Given I am in a git repository with remote "https://github.com/iterative-works/iw-cli.git"
  And the gh CLI is installed and authenticated
  When I run "iw init --tracker github"
  Then the configuration is created at .iw/config.conf
  And the tracker type is set to "github"
  And the repository is auto-detected as "iterative-works/iw-cli"
  And I see a success message confirming GitHub tracker is configured
  And I do not see any instructions about API token setup
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**
Moderate complexity due to:
- Need to extend IssueTrackerType enum with GitHub case
- Repository extraction from git remote needs implementation
- Config schema changes (repository field instead of team/baseUrl)
- Different config validation for GitHub (no API token required)

**Key technical challenges:**
- GitHub uses repository (owner/repo) instead of team identifier
- Need to parse git remote URL to extract owner/repo
- Config serialization must handle GitHub-specific fields
- Init command needs updated prompts for GitHub

**Acceptance:**
- `iw init --tracker github` creates valid config.conf
- Repository is correctly extracted from git remote
- No API token environment variable instructions shown
- Interactive mode offers GitHub as an option
- Config validates correctly for GitHub tracker

---

### Story 2: Create GitHub issue via feedback command

```gherkin
Feature: Submit feedback as GitHub issue
  As a developer using iw-cli on a GitHub project
  I want to submit feedback via the feedback command
  So that issues are created directly in the project's GitHub Issues

Scenario: Create bug report via feedback
  Given the project is configured with GitHub tracker
  And the repository is "iterative-works/iw-cli"
  And gh CLI is authenticated
  When I run 'iw feedback "Bug in start command" --type bug --description "Command crashes on invalid input"'
  Then a GitHub issue is created via "gh issue create"
  And the issue has title "Bug in start command"
  And the issue has label "bug"
  And the issue body contains "Command crashes on invalid input"
  And I see the issue number and URL in the output

Scenario: Create feature request via feedback
  Given the project is configured with GitHub tracker
  And the repository is "iterative-works/iw-cli"
  And gh CLI is authenticated
  When I run 'iw feedback "Add completion support" --description "Would be nice to have shell completion"'
  Then a GitHub issue is created with label "feedback"
  And the issue title is "Add completion support"
  And I see a success message with the issue URL
```

**Estimated Effort:** 8-12h
**Complexity:** Moderate

**Technical Feasibility:**
Moderate complexity due to:
- Need to implement GitHubClient or gh CLI wrapper
- Shell command execution and output parsing
- Error handling for gh CLI failures (not installed, not authenticated, network errors)
- Label mapping (bug → "bug", feature → "feedback" or "enhancement")

**Key technical challenges:**
- Decision: Create GitHubClient.scala or use direct ProcessAdapter calls?
- gh CLI output parsing (JSON format)
- Error detection (gh CLI exit codes, stderr messages)
- Repository inference from config
- Label handling (GitHub uses label names, not UUIDs like Linear)

**Acceptance:**
- `iw feedback` creates GitHub issues when tracker is github
- Issue type maps to appropriate labels
- Success output shows issue number and URL
- Clear error messages when gh is not installed
- Clear error messages when gh is not authenticated
- Issue description properly formatted in GitHub

---

### Story 3: Display GitHub issue details

```gherkin
Feature: View GitHub issue details
  As a developer working in an issue branch
  I want to see the issue details
  So that I understand what I'm working on

Scenario: View issue by explicit ID
  Given the project is configured with GitHub tracker
  And the repository is "iterative-works/iw-cli"
  And issue #132 exists in GitHub
  And gh CLI is authenticated
  When I run "iw issue 132"
  Then I see the issue title, status, and description
  And the issue is fetched via "gh issue view 132"

Scenario: View issue inferred from branch
  Given I am on branch "132-add-github-support"
  And the project is configured with GitHub tracker
  And issue #132 exists in GitHub
  When I run "iw issue" without arguments
  Then the issue number is inferred as 132
  And I see the issue details for #132
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**
Moderate complexity due to:
- gh issue view command execution and JSON parsing
- Issue ID parsing (GitHub uses numbers, not TEAM-NNN format)
- Integration with existing IssueId domain model
- Formatting GitHub issue data to match existing Issue structure

**Key technical challenges:**
- IssueId currently assumes "TEAM-NNN" format - need to support plain numbers
- Issue status mapping (GitHub: open/closed vs Linear: state names)
- Assignee extraction from gh JSON
- Description formatting (GitHub markdown)

**Acceptance:**
- `iw issue 132` displays GitHub issue #132
- `iw issue` infers number from branch name (e.g., "132-feature")
- Issue display matches format of Linear/YouTrack output
- Error handling for non-existent issues
- Works without any API token configuration

---

### Story 4: Handle gh CLI not installed or not authenticated

```gherkin
Feature: GitHub tracker prerequisites validation
  As a developer configuring GitHub tracker
  I want clear error messages about missing prerequisites
  So that I can set up my environment correctly

Scenario: gh CLI not installed
  Given the project is configured with GitHub tracker
  And gh CLI is not installed
  When I run "iw feedback 'Test issue'"
  Then I see an error message "gh CLI is not installed"
  And I see instructions to install gh CLI
  And the exit code is non-zero

Scenario: gh CLI not authenticated
  Given the project is configured with GitHub tracker
  And gh CLI is installed but not authenticated
  When I run "iw feedback 'Test issue'"
  Then I see an error message "gh is not authenticated"
  And I see instructions to run "gh auth login"
  And the exit code is non-zero

Scenario: Repository not accessible
  Given the project is configured with GitHub tracker "private-org/private-repo"
  And gh CLI is authenticated
  But I don't have access to the repository
  When I run "iw feedback 'Test issue'"
  Then I see an error indicating permission denied
  And the exit code is non-zero
```

**Estimated Effort:** 4-6h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward - error detection and user-friendly messaging.

**Key technical challenges:**
- Detecting gh CLI availability (command not found)
- Parsing gh CLI error messages for authentication issues
- Providing helpful next-step instructions

**Acceptance:**
- Clear error when gh is not installed
- Clear error when gh is not authenticated
- Helpful instructions for each error case
- All GitHub commands check prerequisites before execution

---

### Story 5: Repository auto-detection from git remote

```gherkin
Feature: Automatic repository detection
  As a developer initializing iw-cli
  I want the repository to be auto-detected from git remote
  So that I don't have to manually specify it

Scenario: Auto-detect from HTTPS remote
  Given I am in a git repository
  And the remote origin URL is "https://github.com/iterative-works/iw-cli.git"
  When I run "iw init --tracker github"
  Then the repository is detected as "iterative-works/iw-cli"
  And it is stored in the configuration

Scenario: Auto-detect from SSH remote
  Given I am in a git repository
  And the remote origin URL is "git@github.com:iterative-works/iw-cli.git"
  When I run "iw init --tracker github"
  Then the repository is detected as "iterative-works/iw-cli"
  And it is stored in the configuration

Scenario: Multiple remotes - use origin
  Given I am in a git repository
  And I have remotes "origin" and "upstream"
  When I run "iw init --tracker github"
  Then the repository from "origin" remote is used

Scenario: Non-GitHub remote with GitHub tracker
  Given I am in a git repository
  And the remote origin is "https://gitlab.com/company/project.git"
  When I run "iw init --tracker github"
  Then I see a warning that remote is not GitHub
  But the initialization proceeds
  And I am prompted to enter repository manually as "owner/repo"
```

**Estimated Effort:** 4-6h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward - extend existing GitRemote parsing logic.

**Key technical challenges:**
- Parse owner/repo from various GitHub URL formats
- Handle non-GitHub remotes gracefully
- Validation of repository format (owner/repo)

**Acceptance:**
- HTTPS GitHub URLs parsed correctly
- SSH GitHub URLs parsed correctly
- Non-GitHub URLs handled with manual prompt
- Repository stored in config.conf
- Repository available to feedback/issue commands

---

### Story 6: Doctor command validates GitHub setup

```gherkin
Feature: Doctor validates GitHub tracker configuration
  As a developer troubleshooting iw-cli setup
  I want the doctor command to check GitHub prerequisites
  So that I know if everything is configured correctly

Scenario: All GitHub prerequisites met
  Given the project is configured with GitHub tracker
  And gh CLI is installed and authenticated
  And I have access to the configured repository
  When I run "iw doctor"
  Then I see a checkmark for "gh CLI installed"
  And I see a checkmark for "gh CLI authenticated"
  And I see a checkmark for "Repository accessible"

Scenario: gh CLI not installed
  Given the project is configured with GitHub tracker
  And gh CLI is not installed
  When I run "iw doctor"
  Then I see a failure for "gh CLI installed"
  And I see instructions to install gh CLI

Scenario: gh CLI not authenticated
  Given the project is configured with GitHub tracker
  And gh CLI is installed but not authenticated
  When I run "iw doctor"
  Then I see a failure for "gh CLI authenticated"
  And I see instructions to run "gh auth login"
```

**Estimated Effort:** 3-4h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward - add GitHub checks to existing doctor command.

**Key technical challenges:**
- Check gh CLI availability
- Check gh authentication status (gh auth status)
- Check repository access (gh repo view)

**Acceptance:**
- Doctor checks gh installation
- Doctor checks gh authentication
- Doctor checks repository access
- Clear actionable messages for each failure
- Consistent with existing Linear/YouTrack checks

---

## Architectural Sketch

**Purpose:** List WHAT components each story needs, not HOW they're implemented.

### For Story 1: Initialize project with GitHub tracker

**Domain Layer:**
- `IssueTrackerType.GitHub` - New enum case
- `GitHubRepository` - Value object representing owner/repo (or extend existing model)
- Repository extraction logic from GitRemote

**Application Layer:**
- Config validation for GitHub (no team, no baseUrl, requires repository)
- Repository auto-detection service

**Infrastructure Layer:**
- `ConfigSerializer` - Updated to handle GitHub case
- `TrackerDetector` - Updated to suggest GitHub for github.com remotes
- Git remote parsing for owner/repo extraction

**Presentation Layer:**
- `init.scala` - Updated prompts for GitHub
- Config template generation for GitHub

---

### For Story 2: Create GitHub issue via feedback command

**Domain Layer:**
- Issue creation domain logic (title, description, labels)
- Label mapping rules (bug → "bug", feature → "feedback")

**Application Layer:**
- Feedback service adapted for GitHub
- Issue creation use case

**Infrastructure Layer:**
- `GitHubClient` or direct ProcessAdapter usage
- `gh issue create` command wrapper
- JSON response parsing from gh CLI
- Error detection and mapping

**Presentation Layer:**
- `feedback.scala` - Pattern matching on GitHub tracker type
- Success/error output formatting

---

### For Story 3: Display GitHub issue details

**Domain Layer:**
- Issue ID parsing for numeric GitHub issues
- Issue domain model (reuse existing Issue)

**Application Layer:**
- Issue fetching service for GitHub
- Issue formatting (reuse existing IssueFormatter)

**Infrastructure Layer:**
- `GitHubClient.fetchIssue` method
- `gh issue view` command wrapper
- JSON parsing for issue data
- Status/assignee mapping

**Presentation Layer:**
- `issue.scala` - Pattern matching on GitHub tracker type
- Issue display (reuse existing formatter)

---

### For Story 4: Handle gh CLI prerequisites

**Domain Layer:**
- Prerequisite validation rules
- Error categorization (not installed, not authenticated, no access)

**Application Layer:**
- GitHub prerequisites checker
- User-facing error messages

**Infrastructure Layer:**
- `gh --version` check for installation
- `gh auth status` check for authentication
- Exit code and stderr parsing

**Presentation Layer:**
- Error message formatting with instructions
- Exit code handling

---

### For Story 5: Repository auto-detection

**Domain Layer:**
- Repository format validation (owner/repo)
- GitRemote to Repository mapping

**Application Layer:**
- Repository extraction from git remote
- Fallback to manual input

**Infrastructure Layer:**
- GitRemote URL parsing for GitHub
- Owner/repo extraction logic

**Presentation Layer:**
- Manual repository prompt
- Repository display in config output

---

### For Story 6: Doctor validates GitHub setup

**Application Layer:**
- GitHub health check service
- Check aggregation

**Infrastructure Layer:**
- `gh --version` health check
- `gh auth status` health check
- `gh repo view` health check

**Presentation Layer:**
- `doctor.scala` - GitHub-specific checks
- Check result formatting

---

## Technical Decisions (Resolved)

### RESOLVED: GitHub client implementation approach

**Decision:** Create `GitHubClient.scala` following the same pattern as LinearClient/YouTrackClient.

**Rationale:**
- Consistent with existing codebase patterns
- Easier to test (can mock the client)
- Clear separation of concerns
- Simpler than LinearClient (shell commands instead of HTTP client)

**Impact:** Stories 2, 3, 4, 6 will use GitHubClient for all gh CLI interactions.

---

### RESOLVED: IssueId model compatibility with GitHub

**Decision:** Extend `IssueId.parse` to handle numeric IDs for GitHub.

**Rationale:**
- Single type handles both formats, minimal code changes
- Repository context stored in config, not in issue ID
- Team field becomes optional for GitHub tracker
- Users can use natural GitHub issue numbers (e.g., "132")

**Impact:** Stories 3, 5 - IssueId will accept both "TEAM-NNN" and plain numeric formats.

---

### RESOLVED: Label mapping strategy

**Decision:** Use hardcoded label names with graceful fallback.

**Labels:**
- Bug type → "bug" label
- Feature type → "feedback" label

**Fallback behavior:** If labels don't exist in the repository, create the issue without labels (no error).

**Rationale:**
- Simple and consistent
- No configuration burden
- Graceful degradation when labels missing

**Impact:** Story 2 - feedback command will attempt to apply labels but won't fail if they don't exist.

---

### RESOLVED: Config schema for GitHub

**Decision:** Add new `repository` field to ProjectConfiguration, keep `team` for Linear/YouTrack.

**Config structure for GitHub:**
```hocon
tracker {
  type = github
  repository = "owner/repo"
}
```

**Rationale:**
- Clear separation between tracker types
- No breaking changes to existing configs
- Each tracker type has appropriate fields

**Impact:** Stories 1, 5 - config model will have optional `repository` field, required only for GitHub.

---

### RESOLVED: Error handling for gh CLI failures

**Decision:** Use exit codes + stderr output, with specific checks for common cases.

**Error detection strategy:**
1. Check exit code (non-zero = error)
2. Show stderr output to user
3. Specific detection for:
   - Command not found → "gh CLI is not installed"
   - Exit code 4 → "gh is not authenticated"
   - Other errors → show stderr message

**Rationale:**
- Balance of simplicity and helpfulness
- Not fragile to minor gh CLI output changes
- Users see actual error messages from gh

**Impact:** Stories 2, 3, 4 - all gh CLI interactions will use this error handling pattern.

---

## Total Estimates

**Story Breakdown:**
- Story 1 (Initialize project with GitHub tracker): 6-8 hours
- Story 2 (Create GitHub issue via feedback command): 8-12 hours
- Story 3 (Display GitHub issue details): 6-8 hours
- Story 4 (Handle gh CLI prerequisites): 4-6 hours
- Story 5 (Repository auto-detection): 4-6 hours
- Story 6 (Doctor validates GitHub setup): 3-4 hours

**Total Range:** 31-44 hours

**Confidence:** Medium

**Reasoning:**
- **Moderate unknowns**: gh CLI behavior, error handling edge cases, IssueId model compatibility
- **Existing patterns**: Can follow LinearClient/YouTrackClient pattern, git remote parsing exists
- **Simple integration**: No custom HTTP client needed, gh CLI handles auth and API
- **Test complexity**: Need E2E tests that interact with GitHub (or mock gh CLI)
- **CLARIFY markers**: 5 key decisions needed before implementation, which could shift estimates by ±20%

Confidence is Medium because:
1. The gh CLI approach is simpler than custom API clients (reduces complexity)
2. We have existing patterns to follow (config, clients, commands)
3. But we have several architectural decisions that could impact implementation
4. Testing strategy unclear (do we test against real GitHub or mock gh?)

---

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests**: Domain logic, parsing, validation
2. **Integration Tests**: gh CLI command execution, JSON parsing
3. **E2E Scenario Tests**: Automated verification of the Gherkin scenario

**Story-Specific Testing Notes:**

**Story 1: Initialize with GitHub tracker**
- Unit: Repository parsing from git remotes (HTTPS, SSH formats)
- Unit: Config serialization for GitHub tracker type
- Integration: TrackerDetector suggestion for github.com
- E2E: Run `iw init --tracker github` in test repo, verify config.conf content

**Story 2: Create GitHub issue via feedback**
- Unit: Label mapping logic (bug → "bug", feature → "feedback")
- Unit: Command argument building for gh issue create
- Integration: gh CLI response parsing (mock gh with test JSON)
- E2E: Create real GitHub issue in test repository, verify via gh CLI

**Story 3: Display GitHub issue**
- Unit: Issue ID parsing for numeric IDs
- Unit: gh JSON response parsing
- Integration: gh issue view command execution
- E2E: Fetch real GitHub issue, verify display format

**Story 4: Prerequisites validation**
- Unit: Error message formatting
- Integration: Detection of gh not installed (PATH manipulation)
- Integration: Detection of gh not authenticated (mock auth status)
- E2E: Test with gh not installed, verify error message

**Story 5: Repository auto-detection**
- Unit: Owner/repo extraction from git remote URLs
- Unit: Repository format validation
- Integration: Git remote reading
- E2E: Initialize in repos with HTTPS/SSH remotes, verify detection

**Story 6: Doctor validates GitHub**
- Unit: Health check result aggregation
- Integration: gh --version, gh auth status, gh repo view checks
- E2E: Run doctor with various gh states, verify output

**Test Data Strategy:**
- For unit tests: Hardcoded test data (URLs, JSON snippets)
- For integration tests: Mock gh CLI responses with fixture JSON files
- For E2E tests: Hybrid approach (see below)

**RESOLVED: E2E Testing Strategy**

**Decision:** Hybrid approach - mock by default, real GitHub tests with env var.

**Implementation:**
- Default: Mock gh CLI commands, verify correct commands are called with correct arguments
- With `IW_TEST_REAL_GITHUB=1`: Run against real GitHub test repository
- Real tests verify end-to-end functionality works with actual GitHub API

**Rationale:**
- Fast tests by default (no network, no auth required)
- Real integration tests available for thorough verification
- CI can run both modes (mocked always, real on specific triggers)

**Regression Coverage:**
- Existing Linear and YouTrack functionality must not break
- Config parsing for Linear/YouTrack must remain unchanged
- Feedback command for Linear must still work
- Issue command for Linear/YouTrack must still work

**Test Coverage Requirements:**
- All IssueTrackerType cases covered in pattern matching (GitHub, Linear, YouTrack)
- All git remote URL formats tested (HTTPS, SSH, invalid)
- All gh CLI error scenarios covered (not installed, not authenticated, permission denied)
- Config serialization round-trip for all tracker types

---

## Deployment Considerations

### Database Changes
None - this is a CLI tool with file-based configuration.

### Configuration Changes

**Story 1 changes:**
- Add `repository` field to ProjectConfiguration (optional)
- Update config.conf template for GitHub tracker type

Example GitHub config:
```hocon
tracker {
  type = github
  repository = "iterative-works/iw-cli"
}

project {
  name = iw-cli
}
```

**Migration:**
- Existing Linear/YouTrack configs remain unchanged
- No automatic migration needed (new field is optional)

### Environment Variables
**GitHub tracker does NOT require API token** - this is a key difference from Linear/YouTrack.

Authentication handled by `gh auth login` (OAuth, stored by gh CLI).

### External Dependencies
**New requirement: gh CLI must be installed**

- Installation: https://cli.github.com/
- Authentication: `gh auth login`
- Verification: `iw doctor` checks for gh availability

### Rollout Strategy

**Incremental delivery:**
1. Deploy Story 1 (init): Users can configure GitHub but commands don't work yet
2. Deploy Stories 2-3 together: Full GitHub functionality (feedback + issue)
3. Deploy Stories 4-6: Improved UX (error handling, validation)

**Feature flag:**
Not needed - tracker type selection is already user-controlled via config.

**Can deploy per story:**
- Story 1: Yes, can deploy standalone (init works, other commands fail gracefully)
- Story 2: Should bundle with Story 3 for complete feature
- Stories 4-6: Can deploy independently as improvements

### Rollback Plan

**If GitHub support fails in production:**
1. Users can switch back to Linear/YouTrack in config.conf
2. Existing tracker functionality unaffected (separate code paths)
3. No data migration needed (configs are per-project)

**Safe rollback:**
- GitHub support is additive, not replacing existing trackers
- Pattern matching on tracker type provides isolation
- No shared state between tracker implementations

---

## Dependencies

### Prerequisites
- **Before Story 1**:
  - None - can start immediately

- **Before any E2E testing**:
  - GitHub test repository created (e.g., "iterative-works/iw-cli-test")
  - CI environment with gh CLI installed and authenticated
  - Test repository has appropriate labels (bug, enhancement, feedback)

### Story Dependencies

**Sequential dependencies:**
- Story 2 depends on Story 1 (needs GitHub config to exist)
- Story 3 depends on Story 1 (needs GitHub config to exist)
- Story 6 depends on Stories 1-3 (validates what they implement)

**Can be parallelized:**
- Stories 2 and 3 are independent (different commands)
- Story 4 can be developed alongside Story 2 (error handling is same code path)
- Story 5 can be developed alongside Story 1 (repository detection is init logic)

**Dependency graph:**
```
Story 1 (init)
  ├─> Story 2 (feedback)
  │     └─> Story 4 (error handling)
  ├─> Story 3 (issue)
  ├─> Story 5 (auto-detect) [same code area as Story 1]
  └─> Story 6 (doctor) [depends on 1, 2, 3]
```

### External Blockers
- **gh CLI availability**: Users must install gh CLI - document in README
- **GitHub repository access**: Repository must exist and user must have access
- **CI environment setup**: GitHub Actions needs gh CLI installed for E2E tests

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: Initialize project with GitHub tracker** - Establishes foundation, config model, enum extension. Must be first to enable other stories.

2. **Story 5: Repository auto-detection** - Builds directly on Story 1, same code area (init command), makes UX better before implementing features.

3. **Story 2: Create GitHub issue via feedback** - Core value delivery, most important user-facing feature. Proves the gh CLI integration approach.

4. **Story 4: Handle gh CLI prerequisites** - Immediately improves UX of Story 2 with better error messages. Same code path.

5. **Story 3: Display GitHub issue details** - Second core feature, reuses patterns from Story 2 (gh CLI integration, JSON parsing).

6. **Story 6: Doctor validates GitHub setup** - Polish, helps users troubleshoot. Should be last as it validates all previous stories.

**Iteration Plan:**

- **Iteration 1** (Stories 1, 5): Foundation - 10-14h
  - Config model supports GitHub
  - Init command works with GitHub
  - Repository auto-detection from git remote
  - Can configure a project for GitHub but features don't work yet

- **Iteration 2** (Stories 2, 4): Core feature - feedback - 12-18h
  - Feedback command creates GitHub issues
  - Error handling for gh CLI issues
  - Complete user workflow for submitting feedback
  - Most valuable functionality delivered

- **Iteration 3** (Stories 3, 6): Complete feature set - 9-12h
  - Issue command displays GitHub issues
  - Doctor command validates setup
  - Full parity with Linear/YouTrack feature set
  - Production-ready

**Why this order:**
- **Business value**: Iterations 1+2 deliver the most critical need (submit feedback without Linear)
- **Technical dependencies**: Story 1 unblocks all others, Story 5 builds on Story 1
- **Risk reduction**: Iteration 2 proves the gh CLI approach early
- **User value**: Each iteration delivers independently usable functionality

---

## Documentation Requirements

- [x] Gherkin scenarios serve as living documentation (in this analysis.md)
- [ ] Update README.md with GitHub tracker setup instructions
- [ ] Add example config.conf for GitHub tracker type
- [ ] Update `iw init --help` to mention GitHub option
- [ ] Update `iw feedback --help` (no changes needed, same interface)
- [ ] Update `iw issue --help` (no changes needed, same interface)
- [ ] Add troubleshooting guide for gh CLI installation/authentication
- [ ] Document gh CLI version compatibility (minimum version required)
- [ ] Update architecture docs to include GitHub integration approach

**User-facing documentation changes:**
- Installation: Add gh CLI as optional dependency
- Configuration: Show GitHub config example
- Troubleshooting: gh not installed, gh not authenticated
- Contributing: How to test GitHub integration locally

---

**Analysis Status:** Ready for Implementation

**Decisions Resolved (2025-12-22):**
- ✅ GitHub client: Create `GitHubClient.scala` (consistent with existing pattern)
- ✅ Issue IDs: Extend `IssueId.parse` for numeric GitHub IDs
- ✅ Labels: Hardcoded ("bug", "feedback") with graceful fallback
- ✅ Config schema: Add `repository` field, keep `team` for Linear/YouTrack
- ✅ Error handling: Exit codes + stderr, with specific detection for common cases
- ✅ E2E testing: Hybrid (mock by default, real with `IW_TEST_REAL_GITHUB=1`)

**Next Steps:**
1. **RUN TASK GENERATION**: `/iterative-works:ag-create-tasks IWLE-132` to generate implementation tasks

2. **BEGIN ITERATION 1**: Start with Stories 1 and 5 (foundation)

3. **CREATE TEST REPOSITORY** (when needed): Set up "iterative-works/iw-cli-test" for real E2E tests

---

**Key Success Criteria:**
- Users can configure iw-cli for GitHub projects without Linear API tokens
- E2E tests can run against GitHub test repository without polluting production Linear
- Implementation follows existing patterns (consistency with LinearClient/YouTrackClient)
- All three trackers (Linear, YouTrack, GitHub) work independently
- Clear error messages guide users through gh CLI setup
