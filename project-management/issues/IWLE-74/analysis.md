# Story-Driven Analysis: Support project-specific commands alongside shared commands

**Issue:** IWLE-74
**Created:** 2025-12-18
**Status:** Draft
**Classification:** Simple

## Problem Statement

Currently, `iw-run` only discovers and executes commands from the tool's installation directory (`commands/`). Projects using iw-cli cannot add their own custom commands that leverage the shared core library.

This limits extensibility - projects may have domain-specific workflows (e.g., "deploy", "test-integration", "migrate-data") that would benefit from:
- Access to iw-cli's core library (Git, Config, IssueTracker, etc.)
- Consistent command metadata (PURPOSE, USAGE, ARGS)
- Integration with existing iw-cli infrastructure

**User Value:** Enable projects to extend iw-cli with custom commands while maintaining the same developer experience as shared commands.

## Design Decision: Explicit Namespacing

**Decision:** Project commands require explicit `./` prefix. No implicit overriding.

**Rationale:**
Implicit precedence (either global-first or local-first) creates maintenance problems:
- **Global-first:** If iw-cli later adds a global command (e.g., `deploy`), it would shadow existing local `deploy` commands, breaking projects silently.
- **Local-first:** Local commands could accidentally shadow global commands. Knowledge about why an override exists gets lost when team members leave. Future updates to global commands become invisible.

**Solution:**
- `iw start` → always executes global/shared command (error if not found)
- `iw ./deploy` → always executes project command (error if not found)
- No ambiguity, no accidental shadowing, no stale overrides
- Users consciously choose which namespace to invoke

This approach:
- Prevents time bombs from version evolution
- Makes command source explicit and traceable
- Allows both namespaces to have same-named commands without conflict
- Keeps maintenance burden predictable

## User Stories

### Story 1: Project command discoverable with explicit namespace

```gherkin
Feature: Project-specific command discovery
  As a developer using iw-cli
  I want to list all available commands (both shared and project-specific)
  So that I can see what commands are available in my project

Scenario: List commands shows both namespaces separately
  Given the iw-cli installation has shared commands ["version", "init", "start"]
  And my project has a custom command "deploy" in ".iw/commands/deploy.scala"
  When I run "iw --list"
  Then I see a "Commands:" section with shared commands
  And I see shared command "version" in the output
  And I see shared command "init" in the output
  And I see shared command "start" in the output
  And I see a "Project commands (use ./name):" section
  And I see project command "./deploy" in the output
  And each command shows its PURPOSE and USAGE metadata

Scenario: List commands when no project commands exist
  Given the iw-cli installation has shared commands ["version", "init", "start"]
  And my project does NOT have a ".iw/commands/" directory
  When I run "iw --list"
  Then I see shared commands listed
  And I do NOT see a "Project commands" section
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
This is straightforward because:
- `list_commands()` already iterates over a single directory
- We extend it to also check `$PROJECT_DIR/.iw/commands` (if exists)
- Output format changes to show two sections
- The metadata parsing logic (`parse_command_header`) remains unchanged

**Key Technical Challenges:**
- Need to handle the case where `$PROJECT_DIR/.iw/commands` doesn't exist (gracefully skip section)
- Output formatting for two distinct sections

**Acceptance:**
- `iw --list` shows shared commands in main section
- `iw --list` shows project commands in separate section with `./` prefix (if any exist)
- Each command displays correct PURPOSE and USAGE
- No errors or empty section if project commands directory doesn't exist

---

### Story 2: Execute project command with `./` prefix

```gherkin
Feature: Project command execution
  As a developer using iw-cli
  I want to execute project-specific commands using the ./ prefix
  So that I can run custom workflows specific to my project

Scenario: Execute project command successfully
  Given my project has a custom command "deploy" in ".iw/commands/deploy.scala"
  And the "deploy" command imports core library classes (Config, Git, etc.)
  When I run "iw ./deploy --env production"
  Then the "deploy" command executes successfully
  And it has access to the shared core library
  And it receives arguments "--env production"
  And it runs in the context of my project directory

Scenario: Project command not found shows clear error
  Given my project does NOT have a command "missing"
  When I run "iw ./missing"
  Then I see error "Project command 'missing' not found"
  And I see suggestion "Run 'iw --list' to see available commands"

Scenario: Shared command without prefix works normally
  Given the iw-cli installation has shared command "version"
  When I run "iw version"
  Then the shared "version" command executes
  And it does NOT look in project commands directory

Scenario: Shared command not found shows clear error
  Given the iw-cli installation does NOT have command "missing"
  When I run "iw missing"
  Then I see error "Command 'missing' not found"
  And I see suggestion "Run 'iw --list' to see available commands"

Scenario: Same name in both namespaces - no conflict
  Given the iw-cli installation has shared command "start"
  And my project has a custom command "start" in ".iw/commands/start.scala"
  When I run "iw start ISSUE-123"
  Then the SHARED "start" command executes
  When I run "iw ./start ISSUE-123"
  Then the PROJECT "start" command executes
```

**Estimated Effort:** 3-4h
**Complexity:** Moderate

**Technical Feasibility:**
Moderate complexity because:
- `execute_command()` needs to detect `./` prefix and route accordingly
- scala-cli invocation must include shared core library when running project commands
- Command validation differs based on namespace
- Hook discovery for global commands must check both directories

**Key Technical Challenges:**
- Parsing `./` prefix reliably (handle edge cases like `./`, `.//cmd`, etc.)
- scala-cli invocation for project commands needs `$CORE_DIR/*.scala`
- Error messages should indicate which namespace was searched
- Hook discovery: global commands check both `$COMMANDS_DIR` and `$PROJECT_DIR/.iw/commands` for `*.hook-<cmd>.scala`

**Acceptance:**
- `iw ./deploy` executes project command from `.iw/commands/deploy.scala`
- `iw start` executes shared command (never looks at project commands)
- Project command can import and use core library classes
- Project command receives all CLI arguments correctly
- Clear error messages indicate namespace searched

---

### Story 3: Describe project command with `./` prefix

```gherkin
Feature: Project command description
  As a developer using iw-cli
  I want to describe project-specific commands using the ./ prefix
  So that I can see detailed help for my project's commands

Scenario: Describe project command
  Given my project has a custom command "deploy" in ".iw/commands/deploy.scala"
  And the command has PURPOSE, USAGE, ARGS, and EXAMPLES metadata
  When I run "iw --describe ./deploy"
  Then I see the full description of the "deploy" command
  And I see PURPOSE, USAGE, ARGS, and EXAMPLES sections

Scenario: Describe shared command (no prefix)
  Given the iw-cli installation has shared command "start"
  When I run "iw --describe start"
  Then I see the full description of the shared "start" command
```

**Estimated Effort:** 1h
**Complexity:** Straightforward

**Technical Feasibility:**
Very straightforward - `describe_command()` just needs the same `./` prefix routing as `execute_command()`.

**Acceptance:**
- `iw --describe ./deploy` shows project command metadata
- `iw --describe start` shows shared command metadata
- Error message if command not found in specified namespace

---

## Architectural Sketch

**Purpose:** List WHAT components each story needs, not HOW they're implemented.

### For Story 1: Project command discovery

**Shell Script Components (iw-run):**
- `list_commands()` function (modified)
  - List shared commands from `$COMMANDS_DIR`
  - List project commands from `$PROJECT_DIR/.iw/commands` (if exists)
  - Output in two sections with clear labels
  - Project commands shown with `./` prefix

**No domain layer needed** - this is pure infrastructure.

---

### For Story 2: Project command execution

**Shell Script Components (iw-run):**
- `execute_command()` function (modified)
  - Detect `./` prefix in command name
  - If prefix: look only in `$PROJECT_DIR/.iw/commands`, no hooks
  - If no prefix: look only in `$COMMANDS_DIR` (shared), discover hooks from both dirs
  - Include core library in scala-cli invocation for project commands
  - Pass PROJECT_DIR to execution context

**Hook discovery for global commands:**
- Check `$COMMANDS_DIR/*.hook-<cmd>.scala` (shared hooks)
- Check `$PROJECT_DIR/.iw/commands/*.hook-<cmd>.scala` (project hooks)
- Include all discovered hooks in scala-cli invocation

**No new domain components** - project commands use existing core library.

---

### For Story 3: Describe project command

**Shell Script Components (iw-run):**
- `describe_command()` function (modified)
  - Same `./` prefix detection as `execute_command()`
  - Route to appropriate directory based on prefix

**No new components** - same routing pattern as execution.

---

## Technical Risks & Uncertainties

### RESOLVED: Command precedence order

**Decision:** Explicit namespacing with `./` prefix. No implicit override.

- `iw command` → shared only
- `iw ./command` → project only
- Same-named commands can coexist without conflict

---

### RESOLVED: Hook file discovery for project commands

**Decision:** Project can define hooks that extend global commands. No hooks for project commands initially.

**Hook discovery behavior:**
- When running `iw doctor` (global command):
  - Discover hooks from `$COMMANDS_DIR/*.hook-doctor.scala` (shared hooks)
  - Discover hooks from `$PROJECT_DIR/.iw/commands/*.hook-doctor.scala` (project hooks)
  - Both are included in scala-cli invocation
- When running `iw ./deploy` (project command):
  - No hook discovery (deferred - YAGNI)

**Rationale:**
- Hooks are a way to *extend* built-in command behavior without overriding
- Project hooks for global commands is the primary use case (e.g., project-specific doctor checks)
- Global hooks for project commands is speculative (YAGNI)
- Hooks for project commands themselves can be added later if needed

---

### RESOLVED: Bootstrap behavior for project commands

**Decision:** Bootstrap only shared commands (current behavior unchanged).

**Rationale:**
- Bootstrap is an installation-time operation; project commands don't exist yet
- JIT compilation on first use is acceptable for project commands
- Keeps bootstrap simple and self-contained
- No confusing PROJECT_DIR context issues during installation

---

## Total Estimates

**Story Breakdown:**
- Story 1 (Project command discovery): 2-3h
- Story 2 (Execute project command with `./`): 3-4h
- Story 3 (Describe project command): 1h

**Total Range:** 6-8 hours

**Confidence:** High

**Reasoning:**
- Well-understood domain - extending existing patterns, not creating new ones
- Clear requirements with implementation notes in issue
- Single file modification (`iw-run`)
- Explicit namespacing simplifies logic (no precedence rules)
- Existing test infrastructure (BATS) is already in place

---

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests**: None needed (pure shell script, no Scala domain logic)
2. **Integration Tests**: None needed (no new core library components)
3. **E2E Scenario Tests**: BATS tests for each Gherkin scenario

**Story-Specific Testing Notes:**

**Story 1: Discovery**
- E2E BATS test:
  - Create temp project with custom command
  - Run `iw-run --list` from project directory
  - Assert shared commands appear in main section
  - Assert project commands appear in separate section with `./` prefix
  - Verify PURPOSE/USAGE metadata for both types
  - Test without project commands directory (no error, no section)

**Story 2: Execution**
- E2E BATS test:
  - Create project command that imports core library (e.g., `Config`)
  - Execute via `iw-run ./project-cmd`
  - Assert successful execution and core library access
  - Test `iw-run shared-cmd` still works (doesn't check project)
  - Test error case: `iw-run ./missing` shows project-specific error
  - Test error case: `iw-run missing` shows shared-specific error
  - Test same name in both namespaces (each invoked correctly)
  - Test argument passing to project command

**Story 3: Describe**
- E2E BATS test:
  - Create project command with full metadata
  - Run `iw-run --describe ./project-cmd`
  - Assert all metadata sections shown
  - Run `iw-run --describe shared-cmd`
  - Assert shared command metadata shown

**Test Data Strategy:**
- Use temporary directories (mktemp) for isolated test projects
- Create minimal project commands (simple echo/println scripts)
- Reuse existing shared commands from `.iw/commands/` as fixtures
- Clean up temp directories in teardown

**Regression Coverage:**
- Existing BATS tests should continue to pass (shared commands still work)
- Verify bootstrap.bats tests still pass (iw-run without project context)
- Verify all command-specific tests (start.bats, doctor.bats, etc.) still pass

---

## Deployment Considerations

### Database Changes
None - this is pure shell script modification.

### Configuration Changes
None - no new config required. Projects opting in will create `.iw/commands/` directory manually.

### Rollout Strategy
Single-commit change to `iw-run` script. No phased rollout needed.

**Deployment sequence:**
1. Merge changes to `iw-run`
2. Release new version of iw-cli tarball
3. Projects can adopt by creating `.iw/commands/` directory and adding custom commands

**Feature is backward compatible:**
- Existing projects without `.iw/commands/` directory - no change in behavior
- Existing projects with empty directory - no change in behavior
- Only projects that add custom commands and use `./` prefix see new functionality
- Shared commands continue to work exactly as before

### Rollback Plan
Single-file change, easy to revert:
1. Revert commit to `iw-run`
2. Re-release previous version tarball
3. `./` prefix commands will fail, but shared commands unaffected

---

## Dependencies

### Prerequisites
- scala-cli installed (already required by iw-cli)
- Project must be using iw-cli (has `iw` bootstrap script)
- No external system dependencies

### Story Dependencies
- Story 2 depends on Story 1 (discovery informs users about available commands)
- Story 3 is independent but best done after Story 2 (same routing pattern)
- Stories can be implemented sequentially in order

### External Blockers
None identified.

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: Project command discovery** - Establishes the foundation for showing both namespaces. Users can see what's available.

2. **Story 2: Execute project command** - Core value delivery. Users can run `iw ./command` to execute project commands.

3. **Story 3: Describe project command** - Polish. Users can get detailed help with `iw --describe ./command`.

**Iteration Plan:**

- **Iteration 1** (Stories 1-2): Core functionality - discover and execute project commands (5-7h)
- **Iteration 2** (Story 3): Polish - describe support (1h)

**Why this order:**
- Story 1 is pure discovery (read-only) - safest starting point
- Story 2 delivers the primary user value - can't use commands without execution
- Story 3 is convenience feature - nice to have for completeness

---

## Documentation Requirements

- [ ] Update `iw-run` comments to document project command discovery
- [ ] Add example project command to repository (e.g., `.iw/commands/example.scala.template`)
- [ ] Update README to explain project-specific commands feature
- [ ] Document `./` prefix requirement clearly
- [ ] Add USAGE examples showing project command creation and invocation

**Suggested README addition:**

```markdown
## Project-Specific Commands

You can extend iw-cli with custom commands specific to your project:

1. Create `.iw/commands/` directory in your project root
2. Add Scala scripts following the command template
3. Project commands have access to the shared core library
4. Invoke with `./` prefix: `iw ./mycommand`

**Important:** Project commands use explicit `./` prefix to avoid conflicts with shared commands. Both namespaces can have same-named commands without collision.

Example:
```scala
// .iw/commands/deploy.scala
// PURPOSE: Deploy application to staging environment
// USAGE: iw ./deploy [--env staging|production]

import works.iterative.core.Config
import works.iterative.core.Process

@main def deploy(args: String*) = {
  val config = Config.load()
  // Your deployment logic here
}
```

Usage:
```bash
# List all commands (shows both shared and project)
iw --list

# Run shared command
iw start ISSUE-123

# Run project command (note ./ prefix)
iw ./deploy --env staging

# Get help for project command
iw --describe ./deploy
```
```

---

**Analysis Status:** Approved

**All design decisions resolved:**
- Command namespacing: Explicit `./` prefix for project commands
- Hook discovery: Project hooks extend global commands; no hooks for project commands
- Bootstrap: Shared commands only

**Next Steps:**
1. `/ag-create-tasks IWLE-74` - Generate phase-based task index
2. `/ag-implement IWLE-74` - Begin implementation
