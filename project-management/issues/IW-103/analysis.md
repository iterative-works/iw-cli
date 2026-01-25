# Story-Driven Analysis: Add issue creation command

**Issue:** IW-103
**Created:** 2026-01-25
**Status:** Draft
**Classification:** Feature

## Problem Statement

Users need the ability to create issues in their project's configured issue tracker directly from the command line. Currently, users can only view existing issues (`iw issue [id]`) or submit feedback about iw-cli itself (`iw feedback`). This forces users to switch context to web UIs when they need to create project issues during development workflows.

**Value:** Streamlines developer workflow by keeping issue creation in the terminal, reducing context switching. Enables automation scripts and CI/CD pipelines to create issues programmatically. Provides symmetric CRUD operations (read via `issue`, create via `issue create`).

## User Stories

### Story 1: Create GitHub issue with title and description (non-interactive)

```gherkin
Feature: Create issue in GitHub repository
  As a developer working in a GitHub-tracked project
  I want to create issues via command line with title and description
  So that I can quickly log work without leaving my terminal

Scenario: Successfully create GitHub issue with title and description
  Given I am in a project configured for GitHub tracker
  And gh CLI is installed and authenticated
  And repository is set to "iterative-works/test-project"
  When I run "./iw issue create 'Fix login bug' --description 'Users cannot login with SSO'"
  Then a new issue is created in GitHub repository
  And the issue has title "Fix login bug"
  And the issue has body "Users cannot login with SSO"
  And I see output "Issue created: #123"
  And I see output "URL: https://github.com/iterative-works/test-project/issues/123"
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**
Moderate complexity because we're extending existing patterns. GitHubClient already has `createIssue` method used by feedback command. Main work is:
- Creating new command script at `.iw/commands/issue-create.scala`
- Implementing argument parser for title/description flags
- Adapting existing GitHubClient.createIssue (currently hardcoded for iw-cli feedback)
- Testing with real GitHub repository

Key technical challenge is that GitHubClient.createIssue currently uses FeedbackParser.IssueType for labels. Need to decide if we support issue types/labels for user's project or keep it simple (title + description only).

**Acceptance:**
- Command creates issue in configured GitHub repository
- Returns issue number and URL
- Works with both short title-only and title+description formats
- Follows existing error handling patterns (missing gh, not authenticated, etc.)

---

### Story 2: Create Linear issue with title and description

```gherkin
Feature: Create issue in Linear workspace
  As a developer working in a Linear-tracked project
  I want to create issues via command line
  So that I can capture tasks and bugs immediately

Scenario: Successfully create Linear issue with title and description
  Given I am in a project configured for Linear tracker
  And LINEAR_API_TOKEN environment variable is set to valid token
  And team ID is configured as "abc123-team-uuid"
  When I run "./iw issue create 'Implement search' --description 'Add full-text search to products'"
  Then a new issue is created in Linear team
  And the issue has title "Implement search"
  And the issue has description "Add full-text search to products"
  And I see output "Issue created: TEAM-456"
  And I see output "URL: https://linear.app/workspace/issue/TEAM-456"
```

**Estimated Effort:** 4-6h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward because LinearClient already has `createIssue` method with title, description, and teamId parameters. We just need to:
- Call existing LinearClient.createIssue from new command
- Extract teamId from ProjectConfiguration
- Handle API token validation (reuse existing pattern from issue.scala)

Less complex than GitHub story because Linear API is already abstracted and tested.

**Acceptance:**
- Command creates issue in configured Linear team
- Returns Linear issue ID (e.g., TEAM-456) and URL
- Validates API token before attempting creation
- Clear error message if token missing or invalid

---

### Story 3: Create GitLab issue with title and description

```gherkin
Feature: Create issue in GitLab repository
  As a developer working in a GitLab-tracked project
  I want to create issues via command line
  So that I can log work without opening browser

Scenario: Successfully create GitLab issue with title and description
  Given I am in a project configured for GitLab tracker
  And glab CLI is installed and authenticated
  And repository is set to "company/platform/api-service"
  When I run "./iw issue create 'API rate limiting' --description 'Implement rate limiting for public API'"
  Then a new issue is created in GitLab repository
  And the issue has title "API rate limiting"
  And the issue has description "Implement rate limiting for public API"
  And I see output "Issue created: #789"
  And I see output "URL: https://gitlab.com/company/platform/api-service/-/issues/789"
```

**Estimated Effort:** 4-6h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward - GitLabClient already has `createIssue` method matching the pattern from GitHubClient. Implementation follows same approach:
- Call existing GitLabClient.createIssue from command
- Extract repository from ProjectConfiguration
- Validate glab CLI prerequisites (already implemented)

GitLab supports nested repository paths (company/platform/api-service), which is already handled in existing code.

**Acceptance:**
- Command creates issue in configured GitLab repository
- Returns issue number and URL
- Works with nested repository paths
- Validates glab CLI installed and authenticated

---

### Story 4: Create YouTrack issue with title and description

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
  When I run "./iw issue create 'Memory leak' --description 'Application crashes after 24h runtime'"
  Then a new issue is created in YouTrack project
  And the issue has summary "Memory leak"
  And the issue has description "Application crashes after 24h runtime"
  And I see output "Issue created: PROJ-234"
  And I see output "URL: https://company.youtrack.cloud/issue/PROJ-234"
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**
Moderate complexity because YouTrackClient does NOT currently have a createIssue method. We need to:
- Implement YouTrackClient.createIssue following YouTrack REST API
- Build HTTP POST request with title (summary in YouTrack terms) and description
- Parse response to extract created issue ID and URL
- Handle authentication and error cases

More work than GitHub/GitLab/Linear because we're adding new functionality to YouTrackClient rather than calling existing method.

**Acceptance:**
- Command creates issue in configured YouTrack project
- Returns YouTrack issue ID (e.g., PROJ-234) and URL
- Validates API token before creation
- Maps title to YouTrack's "summary" field correctly

---

### Story 5: Handle missing prerequisites gracefully

```gherkin
Feature: Validate prerequisites before creating issue
  As a developer
  I want clear error messages when prerequisites are missing
  So that I know how to fix configuration issues

Scenario: GitHub tracker with gh CLI not installed
  Given I am in a project configured for GitHub tracker
  And gh CLI is not installed
  When I run "./iw issue create 'Test issue'"
  Then I see error message "gh CLI is not installed"
  And I see installation instructions for gh CLI
  And the command exits with code 1

Scenario: Linear tracker with missing API token
  Given I am in a project configured for Linear tracker
  And LINEAR_API_TOKEN environment variable is not set
  When I run "./iw issue create 'Test issue'"
  Then I see error message "LINEAR_API_TOKEN environment variable is not set"
  And the command exits with code 1

Scenario: GitLab tracker with glab not authenticated
  Given I am in a project configured for GitLab tracker
  And glab CLI is installed but not authenticated
  When I run "./iw issue create 'Test issue'"
  Then I see error message "glab is not authenticated"
  And I see authentication instructions
  And the command exits with code 1

Scenario: YouTrack tracker with missing base URL
  Given I am in a project configured for YouTrack tracker
  And baseUrl is not configured in .iw/config.conf
  When I run "./iw issue create 'Test issue'"
  Then I see error message "YouTrack base URL not configured"
  And I see configuration instructions
  And the command exits with code 1
```

**Estimated Effort:** 3-4h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward - reuses existing prerequisite validation from issue.scala:
- GitHub: GitHubClient.validateGhPrerequisites
- GitLab: GitLabClient.validateGlabPrerequisites
- Linear: ApiToken.fromEnv check
- YouTrack: ApiToken.fromEnv + baseUrl check

All validation logic exists. Just need to wire it into the new command and ensure error messages are user-friendly.

**Acceptance:**
- Clear, actionable error messages for each prerequisite failure
- Matches error message format from existing `iw issue` command
- No generic "command failed" messages - specific to the missing prerequisite

---

### Story 6: Create issue with title only (minimal usage)

```gherkin
Feature: Create issue with title only
  As a developer in a hurry
  I want to create an issue with just a title
  So that I can quickly log something without writing full description

Scenario: Create GitHub issue with title only
  Given I am in a project configured for GitHub tracker
  And gh CLI is installed and authenticated
  When I run "./iw issue create 'Quick bug note'"
  Then a new issue is created in GitHub repository
  And the issue has title "Quick bug note"
  And the issue has empty description
  And I see output "Issue created: #125"

Scenario: Create Linear issue with title only
  Given I am in a project configured for Linear tracker
  And LINEAR_API_TOKEN is set
  When I run "./iw issue create 'Add dark mode'"
  Then a new issue is created in Linear team
  And the issue has title "Add dark mode"
  And the issue has empty description
  And I see output "Issue created: TEAM-458"
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward - argument parser needs to support:
- Title as first positional argument (possibly quoted multi-word string)
- Optional --description flag
- Default description to empty string if not provided

All tracker clients already handle empty descriptions correctly (seen in feedback.scala and existing client implementations).

**Acceptance:**
- Command works without --description flag
- Multi-word titles work correctly (either quoted or unquoted)
- Empty description is passed to tracker API
- No client-side validation requires description

---

### Story 7: Display help when no arguments provided

```gherkin
Feature: Display usage help
  As a developer learning the command
  I want to see help text when I run it without arguments
  So that I understand how to use it

Scenario: Run command without arguments
  Given I am in any iw-cli project
  When I run "./iw issue create"
  Then I see usage help text
  And help shows examples of title-only creation
  And help shows examples with --description flag
  And the command exits with code 1

Scenario: Run command with --help flag
  Given I am in any iw-cli project
  When I run "./iw issue create --help"
  Then I see the same usage help text
  And the command exits with code 0
```

**Estimated Effort:** 1-2h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward - follows pattern from feedback.scala which has showHelp() function. Implementation:
- Check for --help flag or empty args
- Display usage, arguments, examples
- Exit with appropriate code (0 for --help, 1 for missing args)

**Acceptance:**
- Help text shows command signature
- Examples cover both title-only and title+description usage
- Matches style of other iw-cli help outputs
- --help exits with 0, missing args exits with 1

## Architectural Sketch

**Purpose:** List WHAT components each story needs, not HOW they're implemented.

### For Story 1: GitHub issue creation

**Domain Layer:**
- Issue entity (already exists)
- IssueId value object (already exists)
- CreatedIssue case class (already exists in LinearClient.scala)

**Application Layer:**
- IssueCreateCommand (new command script at `.iw/commands/issue-create.scala`)
- IssueCreateParser (parse title and --description args)
- IssueCreateService or reuse GitHubClient.createIssue

**Infrastructure Layer:**
- GitHubClient.createIssue (exists, may need adaptation)
- GitHubClient.validateGhPrerequisites (exists)
- CommandRunner.execute (exists)

**Presentation Layer:**
- CLI command interface: `./iw issue create <title> [--description <text>]`
- Output formatter for created issue (number + URL)

---

### For Story 2: Linear issue creation

**Domain Layer:**
- Issue, IssueId, CreatedIssue (all exist)

**Application Layer:**
- IssueCreateCommand (same command script, switch on tracker type)
- IssueCreateParser (same parser)

**Infrastructure Layer:**
- LinearClient.createIssue (exists)
- ApiToken.fromEnv (exists)

**Presentation Layer:**
- Same CLI interface
- Output formatter (same as Story 1)

---

### For Story 3: GitLab issue creation

**Domain Layer:**
- Issue, IssueId, CreatedIssue (all exist)

**Application Layer:**
- IssueCreateCommand (same command script)
- IssueCreateParser (same parser)

**Infrastructure Layer:**
- GitLabClient.createIssue (exists)
- GitLabClient.validateGlabPrerequisites (exists)

**Presentation Layer:**
- Same CLI interface
- Output formatter (same)

---

### For Story 4: YouTrack issue creation

**Domain Layer:**
- Issue, IssueId, CreatedIssue (all exist)

**Application Layer:**
- IssueCreateCommand (same command script)
- IssueCreateParser (same parser)

**Infrastructure Layer:**
- YouTrackClient.createIssue (NEW - needs implementation)
- YouTrackClient.buildCreateIssueRequest (NEW)
- YouTrackClient.parseCreateIssueResponse (NEW)

**Presentation Layer:**
- Same CLI interface
- Output formatter (same)

---

### For Story 5: Prerequisite validation

**Application Layer:**
- Prerequisite validation orchestration in IssueCreateCommand

**Infrastructure Layer:**
- GitHubClient.validateGhPrerequisites (exists)
- GitLabClient.validateGlabPrerequisites (exists)
- ApiToken.fromEnv validation (exists)
- Config validation for baseUrl (exists)

**Presentation Layer:**
- Error message formatters (mostly exist, may need minor additions)

---

### For Story 6: Title-only creation

**Application Layer:**
- IssueCreateParser enhancement to handle optional description

**Presentation Layer:**
- Argument parsing for positional title arg

---

### For Story 7: Help display

**Application Layer:**
- showHelp() function in IssueCreateCommand

**Presentation Layer:**
- Help text content

## Technical Risks & Uncertainties

### CLARIFY: Issue type / label support

Currently, feedback.scala uses FeedbackParser.IssueType (Bug | Feature) to apply labels to created issues. Should `iw issue create` support issue types/labels for user's projects?

**Questions to answer:**
1. Do we want to support `--type bug|feature` flag for user projects?
2. If yes, how do we map types to tracker-specific labels (each project may have different labels)?
3. Should we support arbitrary `--labels` flag for advanced users?
4. Or keep MVP simple: title + description only, no labels?

**Options:**
- **Option A: No labels in MVP** - Only support title + description. Simplest implementation. Users can add labels via web UI after creation.
  - Pros: Simplest, fastest to implement, covers 80% use case
  - Cons: Users need second step to add labels

- **Option B: Support --type flag with default mapping** - Map bug/feature to common label names (like feedback.scala does)
  - Pros: Covers common case, consistent with feedback command
  - Cons: Assumes user's project has "bug" and "feature" labels, may fail silently or error

- **Option C: Support --labels flag with arbitrary labels** - Let users specify `--labels bug,priority-high`
  - Pros: Maximum flexibility, works with any project label structure
  - Cons: More complex parsing, need to handle label validation errors gracefully

- **Option D: Configurable label mapping** - Add optional label config to `.iw/config.conf`
  - Pros: Project-specific label mapping, reusable
  - Cons: More complex config, extra setup burden

**Impact:** Affects Story 1-4 implementation. If we support labels, all tracker clients need label parameter handling. If not, we can simplify to just title + description.

---

### CLARIFY: Command structure - subcommand or flag?

Should issue creation be a subcommand (`iw issue create`) or a flag on existing command (`iw issue --create`)?

**Questions to answer:**
1. Does `iw issue create` align with user mental model?
2. Should we support both `iw issue create` and `iw create-issue` for discoverability?
3. How does this affect help output and command routing?

**Options:**
- **Option A: Subcommand `iw issue create`** - Hierarchical structure
  - Pros: Groups related operations (view, create, list, etc.), scalable for future operations
  - Cons: Requires routing logic in issue.scala or separate command file

- **Option B: Separate command `iw create-issue`** - Flat structure
  - Pros: Simple routing, follows existing pattern (feedback, start, init, etc.)
  - Cons: Doesn't group with `iw issue` conceptually, less discoverable

- **Option C: Flag-based `iw issue --create`** - Flag-driven
  - Pros: Single command file handles both view and create
  - Cons: Awkward UX (mixing view and create args), complex arg parsing

**Impact:** Affects command script structure, help text, and user experience. Also impacts whether we modify `.iw/commands/issue.scala` or create new `.iw/commands/issue-create.scala`.

**Recommendation:** Option A (subcommand) aligns with best practices for CLI design and scales better. But this needs routing mechanism - either smart dispatcher in issue.scala or bootstrap script routing.

---

### CLARIFY: Interactive mode support

Acceptance criteria mentions "both interactive and non-interactive modes." Do we need interactive mode in MVP?

**Questions to answer:**
1. What does interactive mode mean? Prompt for title, then description?
2. Is this a must-have or nice-to-have?
3. How does interactive mode work with different terminals (TTY detection)?
4. Can we defer interactive mode to future iteration?

**Options:**
- **Option A: Non-interactive only in MVP** - Require all args on command line
  - Pros: Simpler, works in scripts/CI, clear scope
  - Cons: May not satisfy acceptance criteria "both interactive and non-interactive modes"

- **Option B: Simple interactive prompts** - If title missing, prompt for it; if description missing, prompt
  - Pros: Better UX for manual usage, satisfies acceptance criteria
  - Cons: More complex (TTY detection, prompt library, testing), doesn't work in non-TTY environments

- **Option C: Editor-based interactive** - Like git commit -e, open $EDITOR for description
  - Pros: Rich description editing, familiar pattern
  - Cons: Complex implementation, requires temp file handling, may be overkill

**Impact:** Affects Story 1-4 implementation complexity. Interactive mode requires prompt library (or custom input handling) and TTY detection. May double implementation effort.

**Recommendation:** Clarify with user if interactive mode is MVP requirement or future enhancement. If MVP, Option B (simple prompts) is reasonable. If not required immediately, defer to avoid scope creep.

---

### CLARIFY: YouTrack project field requirement

YouTrack API requires project ID to create issues. Current ProjectConfiguration has `team` field (used for Linear), but YouTrack may need explicit project ID.

**Questions to answer:**
1. Does YouTrack use the `team` field from config as project ID?
2. Do we need a separate `youtrackProject` field in config?
3. How do we map IssueId prefix (e.g., "PROJ" in "PROJ-123") to YouTrack project?

**Options:**
- **Option A: Reuse team field** - Assume config.team is the YouTrack project ID
  - Pros: No config changes needed, simple
  - Cons: May not be semantically correct (team vs project), unclear if Linear team UUID works for YouTrack

- **Option B: Add youtrackProject config field** - Explicit project ID in config
  - Pros: Clear separation, correct semantics
  - Cons: Requires config migration, more complex ProjectConfiguration

- **Option C: Extract from IssueId prefix** - Use team prefix from IssueId (e.g., "PROJ" from "PROJ-123")
  - Pros: No config changes, follows YouTrack convention
  - Cons: May not work if user wants different prefix in IssueId vs YouTrack project

**Impact:** Affects Story 4 implementation and potentially ProjectConfiguration domain model.

---

### CLARIFY: Multi-word title argument parsing

How should users specify multi-word titles? Quoted string or all args before --description?

**Questions to answer:**
1. Should `./iw issue create Fix login bug` work (unquoted multi-word)?
2. Or require quotes: `./iw issue create "Fix login bug"`?
3. What happens with: `./iw issue create Fix the login bug --description Details`?

**Options:**
- **Option A: Require quotes for multi-word titles**
  - Pros: Unambiguous parsing, explicit
  - Cons: Extra typing, easy to forget quotes

- **Option B: Auto-join args before first flag** - All args before `--` become title
  - Pros: Convenient, matches feedback.scala pattern
  - Cons: Slightly magical, may surprise users

- **Option C: Single arg only, force quotes or dashes** - First arg is title, must quote multi-word
  - Pros: Simple parsing, explicit
  - Cons: Inconvenient UX

**Impact:** Affects IssueCreateParser implementation in all stories.

**Recommendation:** Option B (auto-join before flag) matches existing feedback.scala pattern and provides best UX. Document clearly in help text.

---

### CLARIFY: Error handling for API failures

When issue creation fails (network error, validation error, permissions), how much detail should we show?

**Questions to answer:**
1. Do we show raw API error messages or translate to user-friendly text?
2. Should we suggest remediation steps?
3. Do we log failures anywhere for debugging?

**Options:**
- **Option A: Pass through tracker error messages**
  - Pros: Maximum detail, simple implementation
  - Cons: May expose API internals, not user-friendly

- **Option B: Translate to friendly messages with remediation**
  - Pros: Better UX, actionable errors
  - Cons: More code, may lose detail

- **Option C: Hybrid - friendly message + raw error if verbose flag**
  - Pros: Best of both, helps debugging
  - Cons: More complex

**Impact:** Affects error handling in all stories.

**Recommendation:** Start with Option A for MVP (pass through), add Option C (verbose mode) if time permits.

## Total Estimates

**Story Breakdown:**
- Story 1 (GitHub issue creation): 6-8 hours
- Story 2 (Linear issue creation): 4-6 hours
- Story 3 (GitLab issue creation): 4-6 hours
- Story 4 (YouTrack issue creation): 6-8 hours
- Story 5 (Prerequisite validation): 3-4 hours
- Story 6 (Title-only creation): 2-3 hours
- Story 7 (Help display): 1-2 hours

**Total Range:** 26-37 hours

**Confidence:** Medium

**Reasoning:**
- **Medium confidence** because several CLARIFY items affect scope significantly:
  - Interactive mode requirement (could add 6-10h if required)
  - Label support (could add 4-6h if Option C or D chosen)
  - YouTrack createIssue implementation is new code (untested complexity)

- **High confidence components:**
  - GitHub/GitLab/Linear stories leverage existing createIssue methods
  - Prerequisite validation reuses existing code
  - Argument parsing similar to feedback.scala

- **Lower confidence components:**
  - YouTrack story requires new API implementation
  - Command routing if we choose subcommand approach (may need bootstrap changes)
  - Integration testing across 4 different trackers (each needs real/stubbed environment)

- **Assumptions baked into estimate:**
  - MVP is non-interactive mode only
  - No label support (title + description only)
  - Reuse existing tracker client methods where they exist
  - Unit + integration tests per story (not counted separately)

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests**: Argument parsing, prerequisite validation, response parsing
2. **Integration Tests**: Tracker client create methods with stubbed HTTP/CLI
3. **E2E Scenario Tests**: Automated verification of the Gherkin scenario with real trackers (or docker mocks)

**Story-Specific Testing Notes:**

**Story 1 (GitHub):**
- Unit: Parse title/description args, validate gh prerequisites, parse gh CLI response
- Integration: GitHubClient.createIssue with stubbed execCommand (mock gh output)
- E2E: Real gh CLI against test repository (requires gh auth in CI)

**Story 2 (Linear):**
- Unit: Extract teamId from config, parse Linear GraphQL response
- Integration: LinearClient.createIssue with stubbed HTTP backend (mock Linear API)
- E2E: Real Linear API against test workspace (requires LINEAR_API_TOKEN in CI)

**Story 3 (GitLab):**
- Unit: Parse title/description, validate glab prerequisites, parse glab response
- Integration: GitLabClient.createIssue with stubbed execCommand (mock glab output)
- E2E: Real glab CLI against test repository (requires glab auth in CI)

**Story 4 (YouTrack):**
- Unit: Build YouTrack create request JSON, parse response JSON
- Integration: YouTrackClient.createIssue with stubbed HTTP backend (mock YouTrack API)
- E2E: Real YouTrack API against test instance (requires YOUTRACK_API_TOKEN and test project in CI)

**Story 5 (Prerequisites):**
- Unit: Test each prerequisite validator independently
- Integration: Test error path with missing prerequisites (gh not found, token unset, etc.)
- E2E: Run command in environment without gh/glab/token and verify error messages

**Story 6 (Title-only):**
- Unit: Parser handles title without --description flag
- Integration: Each tracker client handles empty description
- E2E: Create issue with title-only in at least one tracker

**Story 7 (Help):**
- Unit: showHelp() produces expected output
- Integration: --help flag triggers help, missing args triggers help
- E2E: Verify help text includes examples and argument descriptions

**Test Data Strategy:**
- **Unit tests:** Pure data (no side effects), hardcoded JSON responses
- **Integration tests:** Stubbed functions (isCommandAvailable, execCommand, HTTP backend)
- **E2E tests:** Real trackers with dedicated test projects/workspaces OR docker-based tracker instances (for GitHub/GitLab, can use local Gitea/GitLab containers)

**Regression Coverage:**
- Existing `iw issue` fetch command should continue working
- Existing `iw feedback` command should not be affected
- All tracker client fetch operations remain functional
- Configuration loading/parsing unchanged

## Deployment Considerations

### Database Changes
No database - iw-cli is stateless, only config files.

### Configuration Changes
No config changes required for MVP (assuming we reuse existing fields).

**If CLARIFY items add config:**
- Story 4 might need `youtrackProject` field (if Option B chosen)
- Label support might need label mapping config

### Rollout Strategy
No rollout concerns - command is additive:
- New command script at `.iw/commands/issue-create.scala`
- No changes to existing commands
- Users can adopt incrementally

**Can deploy per story?** Partially:
- Story 1 alone provides value (GitHub users can create issues)
- Stories 2-4 can be deployed independently (each tracker is isolated)
- Story 5 is cross-cutting (affects all trackers, should be in initial deployment)
- Stories 6-7 enhance UX (can be added later)

**Recommended delivery:**
- **Iteration 1:** Stories 1, 5, 7 (GitHub + validation + help) - 10-14h
- **Iteration 2:** Stories 2, 3, 6 (Linear, GitLab, title-only) - 10-15h
- **Iteration 3:** Story 4 (YouTrack) - 6-8h

### Rollback Plan
If command fails in production:
- Remove `.iw/commands/issue-create.scala` file
- No data corruption risk (only creates issues in external trackers)
- Created issues in tracker can be closed/deleted manually if needed

## Dependencies

### Prerequisites
Before starting Story 1:
- Access to GitHub test repository for development/testing
- gh CLI installed in dev environment
- Linear/GitLab/YouTrack test accounts (for Stories 2-4)

### Story Dependencies
- Story 5 (prerequisites) should be implemented alongside Stories 1-4 (not after)
- Story 6 (title-only) can be done in parallel with Stories 1-4 (parser enhancement)
- Story 7 (help) can be done early (just text, no functional dependency)

**Parallelization opportunities:**
- Stories 1-4 can be worked independently (different tracker clients)
- Story 5 integrates with Stories 1-4 (could be done by different developer in parallel, merged at end)

**Sequential requirements:**
- Story 1 establishes command structure (parser, output format, error handling patterns)
- Stories 2-4 follow Story 1's patterns
- Story 4 (YouTrack) requires new client method, more isolated

### External Blockers
None identified. All tracker APIs are public and documented:
- GitHub: gh CLI documented at https://cli.github.com/manual/
- Linear: API docs at https://developers.linear.app/docs/graphql/working-with-the-graphql-api
- GitLab: glab CLI documented at https://gitlab.com/gitlab-org/cli
- YouTrack: REST API at https://www.jetbrains.com/help/youtrack/devportal/resource-api-issues.html

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 7: Help display** - Establishes UX expectations, helps with testing
2. **Story 1: GitHub issue creation** - Most common tracker, establishes patterns
3. **Story 5: Prerequisite validation** - Integrates with Story 1, makes it production-ready
4. **Story 6: Title-only creation** - Enhances Story 1, simple addition
5. **Story 2: Linear issue creation** - Second most complex, reuses existing createIssue
6. **Story 3: GitLab issue creation** - Similar to GitHub, straightforward
7. **Story 4: YouTrack issue creation** - Most work (new client method), done last

**Iteration Plan:**

- **Iteration 1 (Stories 7, 1, 5):** Foundation - 10-14h
  - Help text shows users how to use command
  - GitHub support covers majority of users
  - Prerequisite validation makes it production-ready
  - Deliverable: Usable for GitHub projects

- **Iteration 2 (Stories 6, 2, 3):** Breadth - 10-15h
  - Title-only improves UX
  - Linear + GitLab support covers more trackers
  - Deliverable: Works for 3/4 trackers (GitHub, Linear, GitLab)

- **Iteration 3 (Story 4):** Completeness - 6-8h
  - YouTrack support (full coverage)
  - Deliverable: All trackers supported

## Documentation Requirements

- [x] Gherkin scenarios serve as living documentation
- [ ] Command help text (`./iw issue create --help`)
- [ ] Update main README.md with issue creation example
- [ ] Update `.claude/skills/iw-cli-ops/` skill files (if using claude-sync)
- [ ] Add troubleshooting section for common errors (gh not authenticated, etc.)
- [ ] API documentation: None needed (command-level feature, not library)

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. **CLARIFY REQUIRED** - Resolve CLARIFY markers with Michal:
   - Label support strategy (Option A recommended: no labels in MVP)
   - Command structure (Option A recommended: subcommand `iw issue create`)
   - Interactive mode requirement (defer or implement?)
   - YouTrack project field mapping (Option A or C recommended)
   - Multi-word title parsing (Option B recommended: auto-join)

2. Once CLARIFY items resolved, run `/iterative-works:ag-create-tasks IW-103` to map stories to implementation phases

3. Run `/iterative-works:ag-implement IW-103` for iterative story-by-story implementation
