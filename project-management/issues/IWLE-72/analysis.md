# Story-Driven Analysis: Create iw-cli - Project-local worktree and issue management tool

**Issue:** IWLE-72
**Created:** 2025-12-10
**Status:** Ready for Implementation
**Classification:** Feature

## Problem Statement

Developers working on multiple issues or features need isolated git worktrees for each task, but current tools (like `par`) have significant limitations that impact workflow efficiency and AI agent integration.

**Current pain points:**
- Worktrees hidden in `~/.local/share/par/` make IDE navigation difficult
- Global installation prevents project-specific customization and AI agent access
- No built-in issue tracker integration requires separate MCP servers
- Manual context switching between worktrees and tracking systems

**Value proposition:**
A project-local CLI tool that creates sibling worktrees (visible in IDEs), integrates with issue trackers (Linear, YouTrack), and manages tmux sessions - all accessible to AI agents and extensible per-project.

## Design Decisions

All technical decisions have been resolved:

| # | Topic | Decision | Rationale |
|---|-------|----------|-----------|
| 1 | Distribution | Source with scala-cli | Project already requires scala-cli for `.iw/commands/` extensibility |
| 2 | Token storage | Environment variables | Works on remote servers, standard practice, no encryption complexity |
| 3 | Issue trackers | Linear + YouTrack for MVP | Both needed for day-to-day work; trait enables future GitHub/GitLab |
| 4 | Worktree collision | Error and abort | Collisions indicate a mistake; user should resolve manually |
| 5 | Session manager | Tmux only | YAGNI - add Zellij abstraction if/when needed |
| 6 | Branch deletion | Don't delete | Branch lifecycle tied to PR/MR process, not worktree removal |
| 7 | Network for `iw start` | Not required | Just create worktree from ID; issue fetch only on `iw issue` |
| 8 | Config format | HOCON | Scala-native (Typesafe Config), supports comments, lightweight dependency |

## User Stories

### Story 1: Initialize project with issue tracker configuration

```gherkin
Feature: Initialize project with issue tracker integration
  As a developer
  I want to configure iw-cli for my project
  So that the tool knows where to read issues and how to create worktrees

Scenario: Successful initialization with Linear tracker
  Given I am in the root directory of a git repository
  And the file .iw/config.conf does not exist
  When I run "./iw init"
  And I confirm the suggested issue tracker "Linear"
  And I confirm the suggested team "IWLE"
  Then I see the message "Configuration created successfully"
  And I see the hint "Set LINEAR_API_TOKEN environment variable"
  And the file .iw/config.conf exists with Linear configuration
```

**Estimated Effort:** 4-6h
**Complexity:** Moderate

**Technical Feasibility:**
Straightforward configuration setup with interactive prompts. Detects git remote to suggest appropriate tracker (gitlab.e-bs.cz → YouTrack, github.com → Linear). Token stored in environment variable (not in config).

**Acceptance:**
- `.iw/config.conf` created with tracker type and team ID
- Host-based tracker detection suggests correct tracker type
- Config validation ensures required fields are present
- Clear instructions for setting environment variable for API token
- Clear error messages for missing git repo or invalid configuration

---

### Story 2: Validate environment and configuration

```gherkin
Feature: Validate environment and configuration
  As a developer
  I want to verify that my environment is correctly set up
  So that iw-cli works properly

Scenario: Complete environment with valid configuration
  Given .iw/config.conf exists with Linear configuration
  And tmux is installed
  And the environment variable LINEAR_API_TOKEN is set and valid
  And the git repository has a remote origin
  When I run "./iw doctor"
  Then I see "✓ Git repository found"
  And I see "✓ Configuration file exists"
  And I see "✓ LINEAR_API_TOKEN is set and valid"
  And I see "✓ tmux installed"
  And I see "✓ All checks passed"

Scenario: Missing API token
  Given .iw/config.conf exists with Linear configuration
  And the environment variable LINEAR_API_TOKEN is not set
  When I run "./iw doctor"
  Then I see "✗ LINEAR_API_TOKEN not set"
  And I see the hint "export LINEAR_API_TOKEN=your-token"
  And the command returns exit code 1

Scenario: Missing tmux
  Given .iw/config.conf exists
  And tmux is not installed
  When I run "./iw doctor"
  Then I see "✗ tmux not found"
  And I see installation instructions for tmux
  And the command returns exit code 1
```

**Estimated Effort:** 4-6h
**Complexity:** Straightforward

**Technical Feasibility:**
Simple validation checks with clear reporting. Each check is independent: git repo existence, config file structure, tmux availability, environment variable presence, API token validity.

**Acceptance:**
- All checks run independently and report status clearly
- Failed checks provide actionable error messages with fix instructions
- API token validation makes actual API call to verify connectivity
- Exit code reflects overall health (0 = all pass, 1 = any failure)

---

### Story 3: Create worktree for issue with tmux session

```gherkin
Feature: Create worktree for issue
  As a developer
  I want to create an isolated worktree for a specific issue
  So that my code for different issues is not mixed

Scenario: Successfully create worktree for issue
  Given I am in the main worktree of project "kanon"
  And worktree "kanon-IWLE-123" does not exist
  When I run "./iw start IWLE-123"
  Then I see "Creating worktree kanon-IWLE-123..."
  And the directory "../kanon-IWLE-123" exists as a sibling
  And "../kanon-IWLE-123" has git branch "IWLE-123"
  And tmux session "kanon-IWLE-123" is created
  And I am attached to the tmux session with working directory "../kanon-IWLE-123"

Scenario: Worktree already exists
  Given worktree "kanon-IWLE-123" already exists
  When I run "./iw start IWLE-123"
  Then I see the error "Worktree for IWLE-123 already exists"
  And I see the hint to use "./iw open IWLE-123"
  And the command returns exit code 1

Scenario: Sibling directory already exists
  Given the directory "../kanon-IWLE-123" already exists (not a worktree)
  When I run "./iw start IWLE-123"
  Then I see the error "Directory kanon-IWLE-123 already exists"
  And the command returns exit code 1
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**
Creates worktree and session from issue ID alone - no network required. Involves: git worktree creation in sibling directory, branch creation with issue ID, tmux session creation, attaching to session. Edge cases: directory collisions, existing branches with same name, tmux already running.

**Acceptance:**
- Sibling worktree created with correct naming pattern (project-ISSUE-ID)
- Git branch created matching issue ID
- Tmux session created and user attached to it
- Working directory set to new worktree
- Error on collision (abort, don't auto-resolve)

---

### Story 4: Open existing worktree tmux session

```gherkin
Feature: Open existing worktree tmux session
  As a developer
  I want to attach to an existing tmux session for an issue
  So that my context from previous work is preserved

Scenario: Open session with explicit issue ID
  Given worktree "kanon-IWLE-123" exists
  And tmux session "kanon-IWLE-123" is running
  When I run "./iw open IWLE-123"
  Then I am attached to tmux session "kanon-IWLE-123"
  And the working directory is "../kanon-IWLE-123"

Scenario: Open session from current branch
  Given I am in worktree "kanon-IWLE-123"
  And current branch is "IWLE-123"
  And tmux session "kanon-IWLE-123" is running
  When I run "./iw open" (without parameter)
  Then I am attached to tmux session "kanon-IWLE-123"

Scenario: Session does not exist but worktree does
  Given worktree "kanon-IWLE-123" exists
  And tmux session "kanon-IWLE-123" is not running
  When I run "./iw open IWLE-123"
  Then a new tmux session "kanon-IWLE-123" is created
  And I am attached to that session
```

**Estimated Effort:** 4-6h
**Complexity:** Moderate

**Technical Feasibility:**
Medium complexity. Core challenge is detecting current branch to infer issue ID when parameter not provided. Tmux session management is straightforward (attach if exists, create if not). Need to handle edge case where user is already in tmux session.

**Acceptance:**
- Can open by explicit issue ID or infer from current branch
- Attaches to existing session or creates new one if needed
- Handles nested tmux scenario gracefully (warn or detach-attach)
- Working directory correctly set to worktree path

---

### Story 5: Fetch and display issue details

```gherkin
Feature: Fetch and display issue details
  As a developer
  I want to see issue details from the tracker
  So that I have context without opening a browser

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

**Estimated Effort:** 8-10h
**Complexity:** Moderate

**Technical Feasibility:**
Requires implementing adapters for Linear and YouTrack. Each tracker has different API. Define `IssueTracker` trait for extensibility. Branch-to-issue-ID inference reuses logic from Story 4.

**Acceptance:**
- Displays key issue fields: ID, title, status, assignee, description
- Can infer issue ID from branch or accept explicit parameter
- Works with both Linear and YouTrack trackers
- Clean formatting with proper Unicode rendering
- Clear error for non-existent issues

---

### Story 6: Remove worktree and cleanup resources

```gherkin
Feature: Remove worktree and cleanup resources
  As a developer
  I want to remove a worktree when I no longer need it
  So that my filesystem is not cluttered

Scenario: Remove worktree with tmux session
  Given worktree "kanon-IWLE-123" exists
  And tmux session "kanon-IWLE-123" is running
  And I am not attached to that session
  When I run "./iw rm IWLE-123"
  Then I see "Killing tmux session kanon-IWLE-123..."
  And I see "Removing worktree kanon-IWLE-123..."
  And tmux session "kanon-IWLE-123" does not exist
  And the directory "../kanon-IWLE-123" does not exist
  And git branch "IWLE-123" still exists (not deleted)

Scenario: Protect against removing active session
  Given worktree "kanon-IWLE-123" exists
  And I am attached to tmux session "kanon-IWLE-123"
  When I run "./iw rm IWLE-123"
  Then I see the error "Cannot remove worktree - you are in its tmux session"
  And I see the hint "Detach from session first (Ctrl+B, D)"
  And the worktree still exists

Scenario: Uncommitted changes require confirmation
  Given worktree "kanon-IWLE-123" exists
  And the worktree has uncommitted changes
  When I run "./iw rm IWLE-123"
  Then I see the warning "Worktree has uncommitted changes"
  And I see the prompt "Continue? (y/N)"
  And the command waits for confirmation before deleting
```

**Estimated Effort:** 4-6h
**Complexity:** Moderate

**Technical Feasibility:**
Simpler than originally estimated - no branch deletion. Must kill tmux session and remove worktree directory. Critical: detect uncommitted changes and warn user, prevent self-deletion (removing worktree user is currently in).

**Acceptance:**
- Kills tmux session and removes worktree directory
- Does NOT delete the git branch (branch cleanup is part of PR/MR process)
- Protects against removing active session (user is attached)
- Warns about uncommitted changes and requires confirmation
- Handles partial cleanup (e.g., session already dead)
- Provides `--force` flag to bypass confirmations

---

### Story 7: Bootstrap script runs tool via scala-cli

```gherkin
Feature: Bootstrap script runs tool via scala-cli
  As a developer
  I want to run "./iw" without manual installation
  So that the tool works immediately after being added to the project

Scenario: First run compiles the tool
  Given scala-cli is installed
  When I run "./iw --version"
  Then I see "Compiling iw-cli..." (only on first run)
  And I see "iw-cli version 0.1.0"

Scenario: Subsequent runs use cache
  Given scala-cli has a compiled version in cache
  When I run "./iw --version"
  Then I do not see "Compiling..."
  And I see "iw-cli version 0.1.0"
  And the execution is fast

Scenario: Missing scala-cli
  Given scala-cli is not installed
  When I run "./iw --version"
  Then I see the error "scala-cli not found"
  And I see installation instructions for scala-cli
  And the command returns exit code 1
```

**Estimated Effort:** 2-4h
**Complexity:** Straightforward

**Technical Feasibility:**
Simple shell script that invokes scala-cli on the source files. Scala-cli handles compilation caching automatically. No JAR download, no version management needed.

**Acceptance:**
- Shell script is POSIX-compliant (works on Linux, macOS)
- Invokes scala-cli with correct source paths
- First run compiles (handled by scala-cli)
- Subsequent runs use scala-cli's built-in compilation cache
- Clear error if scala-cli not installed

---

## Architectural Sketch

**Purpose:** List WHAT components each story needs, not HOW they're implemented.

### For Story 1: Initialize project with issue tracker configuration

**Domain Layer:**
- `IssueTrackerType` (enum: Linear, YouTrack)
- `ProjectConfiguration` (value object with tracker type, team ID)
- `GitRemote` (value object for parsing remote URLs)

**Application Layer:**
- `ConfigurationService.initialize()` - interactive config creation
- `TrackerDetector.suggestTracker(gitRemote)` - detect from remote URL

**Infrastructure Layer:**
- `ConfigFileRepository` - read/write `.iw/config.conf` (HOCON)
- `ConsolePrompt` - interactive user input
- `GitAdapter` - read git config (remote URL)

**Presentation Layer:**
- `iw init` command entry point
- Formatted console output with prompts

---

### For Story 2: Validate environment and configuration

**Domain Layer:**
- `EnvironmentCheck` (value object: name, status, message)
- `ValidationResult` (aggregate of multiple checks)

**Application Layer:**
- `EnvironmentValidator.validateAll()` - run all checks
- `GitValidator.checkRepository()`
- `ConfigValidator.checkConfiguration()`
- `TmuxValidator.checkInstallation()`
- `TrackerValidator.checkApiToken()`

**Infrastructure Layer:**
- `ProcessExecutor` - run shell commands (check tmux, git)
- `ConfigFileRepository` - read config
- `EnvironmentAdapter` - read environment variables
- `LinearClient`, `YouTrackClient` - test API connectivity

**Presentation Layer:**
- `iw doctor` command entry point
- Formatted validation report with ✓/✗ symbols

---

### For Story 3: Create worktree for issue with tmux session

**Domain Layer:**
- `IssueId` (value object with validation)
- `WorktreePath` (value object for sibling directory naming)
- `BranchName` (value object derived from issue ID)
- `TmuxSession` (entity with session name, working directory)

**Application Layer:**
- `WorktreeService.createForIssue(issueId)`
- `SessionService.createAndAttach(sessionName, workdir)`

**Infrastructure Layer:**
- `GitWorktreeAdapter` - git worktree add/list
- `TmuxAdapter` - tmux session management
- `FileSystemAdapter` - sibling directory operations
- `ProcessExecutor` - shell command execution

**Presentation Layer:**
- `iw start <issue-id>` command entry point
- Progress messages during creation
- Error messages for conflicts

---

### For Story 4: Open existing worktree tmux session

**Domain Layer:**
- `IssueId` (value object)
- `TmuxSession` (entity)
- `CurrentBranch` (value object from git)

**Application Layer:**
- `SessionService.openOrCreate(issueId)`
- `SessionService.inferIssueFromBranch()` - parse current branch
- `SessionService.isAttached()` - detect nested tmux

**Infrastructure Layer:**
- `TmuxAdapter` - session attach/create/list
- `GitAdapter` - read current branch name
- `ProcessExecutor` - run tmux commands

**Presentation Layer:**
- `iw open [issue-id]` command entry point
- Warning for nested tmux scenarios

---

### For Story 5: Fetch and display issue details

**Domain Layer:**
- `Issue` (entity with ID, title, status, assignee, description, labels)
- `IssueId` (value object)
- `IssueTracker` (trait for tracker abstraction)

**Application Layer:**
- `IssueService.fetch(issueId)` - retrieve from configured tracker
- `IssueService.inferFromBranch()` - get issue ID from current branch
- `IssueFormatter.format(issue)` - render for console

**Infrastructure Layer:**
- `LinearClient` - Linear GraphQL API (implements `IssueTracker`)
- `YouTrackClient` - YouTrack REST API (implements `IssueTracker`)
- `ConfigFileRepository` - read tracker config
- `EnvironmentAdapter` - read API token from environment
- `GitAdapter` - read current branch

**Presentation Layer:**
- `iw issue [issue-id]` command entry point
- Formatted issue display

---

### For Story 6: Remove worktree and cleanup resources

**Domain Layer:**
- `IssueId` (value object)
- `WorktreePath` (value object)
- `DeletionSafety` (value object: uncommitted changes check, active session check)

**Application Layer:**
- `WorktreeService.remove(issueId, force)`
- `SafetyChecker.checkUncommittedChanges(worktreePath)`
- `SafetyChecker.checkActiveSession(sessionName)`
- `SessionService.kill(sessionName)`

**Infrastructure Layer:**
- `TmuxAdapter` - kill session, check if attached
- `GitWorktreeAdapter` - remove worktree (not branch)
- `GitAdapter` - check git status for uncommitted changes
- `ConsolePrompt` - confirmation dialogs

**Presentation Layer:**
- `iw rm <issue-id>` command entry point
- `--force` flag handling
- Warning messages and confirmations

---

### For Story 7: Bootstrap script runs tool via scala-cli

**Infrastructure Layer:**
- Shell script `iw` (bash/sh)
- Scala-cli handles compilation and caching

**Presentation Layer:**
- Shell script error messages
- Scala-cli dependency check

---

## Total Estimates

**Story Breakdown:**
- Story 1 (Initialize config): 4-6 hours
- Story 2 (Validate environment): 4-6 hours
- Story 3 (Create worktree + session): 6-8 hours
- Story 4 (Open existing session): 4-6 hours
- Story 5 (Fetch issue details - Linear + YouTrack): 8-10 hours
- Story 6 (Remove worktree): 4-6 hours
- Story 7 (Bootstrap script): 2-4 hours

**Total Range:** 32-46 hours

**Confidence:** Medium-High

**Reasoning:**
- **Simplified by decisions**: No JAR management, no token encryption, no branch deletion, no network for `iw start`
- **Well-understood domain**: Git worktrees and tmux are familiar technologies
- **Two trackers**: Linear + YouTrack adds complexity but provides real utility
- **Functional core**: Pure domain logic keeps complexity manageable

---

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests**: Pure domain logic, value objects, business rules
2. **Integration Tests**: Adapters, external services, git/tmux operations
3. **E2E Scenario Tests**: Automated verification of the Gherkin scenario

**Test Data Strategy:**
- Git repos: Create temporary git repos in test fixtures with controlled state
- Issue tracker: Use Linear/YouTrack test APIs, or mock HTTP responses for deterministic tests
- Tmux: Use test tmux socket (`-L test-socket`) to isolate from user sessions
- Filesystem: Use temporary directories with cleanup in afterEach

---

## Deployment Considerations

### Configuration

**Required `.gitignore` entries:**
```
.iw/cache/
```

Note: `.iw/config.conf` does NOT contain secrets (tokens are in environment variables), so it CAN be committed to share tracker configuration with team.

**Sample `.iw/config.conf`:**
```hocon
tracker {
  type = linear
  team = IWLE
}

project {
  name = kanon
}
```

**Required environment variables (per tracker):**
- Linear: `LINEAR_API_TOKEN`
- YouTrack: `YOUTRACK_API_TOKEN`

### External Dependencies

- scala-cli (required)
- Git >= 2.15 (for worktree support)
- Tmux >= 2.0 (for session management)
- Typesafe Config library (for HOCON parsing)

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 7: Bootstrap script** - Foundation for running anything
2. **Story 1: Initialize config** - Enables configuration setup
3. **Story 2: Validate environment** - Early feedback for users
4. **Story 3: Create worktree** - Core value proposition
5. **Story 4: Open session** - Natural follow-up to create
6. **Story 6: Remove worktree** - Completes lifecycle
7. **Story 5: Fetch issue** - Polish feature, requires both trackers

**Iteration Plan:**

**Iteration 1 (Stories 7, 1, 2): Foundation - 10-16 hours**
- Deliverable: Users can install tool, initialize config, validate environment

**Iteration 2 (Stories 3, 4, 6): Core workflow - 14-20 hours**
- Deliverable: Full worktree lifecycle (create, open, remove)

**Iteration 3 (Story 5): Issue integration - 8-10 hours**
- Deliverable: Issue details display for both Linear and YouTrack

**Total: 32-46 hours**

---

**Analysis Status:** Ready for Implementation

**Next Steps:**
1. Run `/ag-create-tasks IWLE-72` to generate phase-based implementation tasks
2. Run `/ag-implement IWLE-72` for iterative story-by-story development
