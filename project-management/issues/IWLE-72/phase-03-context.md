# Phase 3 Context: Validate environment and configuration

**Issue:** IWLE-72
**Phase:** 3 of 7
**Status:** Ready for Implementation

---

## 1. Goals

This phase implements the `iw doctor` command that validates the environment is correctly set up for iw-cli operations. The command uses an extensible hook-based architecture where each command can register its own checks.

**Primary Objectives:**
- Implement DoctorChecks registry for extensible check registration
- Update bootstrap script to support hook file discovery
- Create base checks in doctor command (git repo, config file)
- Create command-specific checks as hook files colocated with commands
- Provide clear ✓/✗ reporting with actionable remediation hints
- Return appropriate exit code (0 = all pass, 1 = any failure)

---

## 2. Scope

### In Scope
- `DoctorChecks` singleton registry with check registration
- `CheckResult` enum (Success, Warning, Error, Skip)
- Bootstrap script update for `*.hook-{command}.scala` pattern
- `iw doctor` command with base checks
- `issue.hook-doctor.scala` - API token check for issue command
- `start.hook-doctor.scala` - tmux check for start/open/rm commands
- Formatted output with ✓/✗ symbols and hints

### Out of Scope
- YouTrack API validation (stub for now, project uses Linear)
- Network reachability beyond API token validation
- Other hook types (worktree-init, etc.) - future phases

---

## 3. Dependencies from Previous Phases

**From Phase 1 (Bootstrap):**
- Bootstrap script `iw` - needs modification for hook discovery
- `Output` utilities for formatted console output
- Command structure pattern with headers

**From Phase 2 (Init):**
- `ConfigFileRepository.read()` - read and parse configuration
- `ProjectConfiguration` - configuration data structure
- `IssueTrackerType` enum (Linear, YouTrack)
- `GitAdapter.isGitRepository()` - check if in git repo
- Path conventions: `.iw/config.conf`

---

## 4. Technical Approach

### Architecture: Hook-Based Extensibility

Commands and their checks are colocated in `.iw/commands/`:
```
.iw/commands/
  doctor.scala              # Main doctor command + base checks
  issue.scala               # Issue command
  issue.hook-doctor.scala   # Issue's doctor check (API token)
  start.scala               # Start command
  start.hook-doctor.scala   # Start's doctor check (tmux)
```

The bootstrap script uses a generalized pattern for ALL commands:
```bash
hook_files=$(find .iw/commands -maxdepth 1 -name "*.hook-${command}.scala" 2>/dev/null)
scala-cli run ".iw/commands/${command}.scala" $hook_files -- "$@"
```

This pattern enables future hook types (e.g., `*.hook-worktree-init.scala`) without bootstrap changes.

### Domain Layer

```scala
// .iw/core/DoctorChecks.scala
package iw.core

enum CheckResult:
  case Success(message: String)
  case Warning(message: String, hint: Option[String] = None)
  case Error(message: String, hint: Option[String] = None)
  case Skip(reason: String)  // For checks that don't apply to current config

case class Check(name: String, run: ProjectConfiguration => CheckResult)

object DoctorChecks:
  private var registry: List[Check] = Nil

  def register(name: String)(check: ProjectConfiguration => CheckResult): Unit =
    registry = Check(name, check) :: registry

  def all: List[Check] = registry.reverse  // Preserve registration order

  def runAll(config: ProjectConfiguration): List[(String, CheckResult)] =
    registry.reverse.map(c => (c.name, c.run(config)))
```

### Hook File Pattern

Each hook file:
1. Declares dependencies via `//> using file`
2. Defines a pure check function (testable)
3. Registers the function with DoctorChecks (side effect at load time)

```scala
// .iw/commands/issue.hook-doctor.scala
//> using file "../core/DoctorChecks.scala"
//> using file "../core/Config.scala"
//> using file "../core/LinearClient.scala"

import iw.core.*

// Pure function - easily testable in isolation
def checkLinearToken(config: ProjectConfiguration): CheckResult =
  if config.tracker.trackerType != IssueTrackerType.Linear then
    CheckResult.Skip("Not using Linear")
  else sys.env.get("LINEAR_API_TOKEN") match
    case None =>
      CheckResult.Error("Not set", Some("export LINEAR_API_TOKEN=lin_api_..."))
    case Some(token) =>
      if LinearClient.validateToken(token) then
        CheckResult.Success("Valid")
      else
        CheckResult.Error("Authentication failed", Some("Check token at linear.app/settings/api"))

// Registration happens at file load time
DoctorChecks.register("LINEAR_API_TOKEN")(checkLinearToken)
```

### Bootstrap Script Update

```bash
# In `iw` script - generalized for all commands
run_command() {
  local command="$1"
  shift

  # Find hook files (empty string if none match)
  local hook_files
  hook_files=$(find .iw/commands -maxdepth 1 -name "*.hook-${command}.scala" 2>/dev/null)

  # Run command with any discovered hooks
  # shellcheck disable=SC2086  # Intentional word splitting for hook_files
  scala-cli run ".iw/commands/${command}.scala" $hook_files -- "$@"
}
```

---

## 5. Files to Modify/Create

**New files:**
- `.iw/core/DoctorChecks.scala` - Registry and CheckResult types
- `.iw/core/Process.scala` - ProcessExecutor for shell commands (tmux check)
- `.iw/core/LinearClient.scala` - Linear API client with token validation
- `.iw/commands/issue.hook-doctor.scala` - Linear token check
- `.iw/commands/start.hook-doctor.scala` - tmux installation check
- `.iw/core/test/DoctorChecksTest.scala` - Unit tests for registry
- `.iw/core/test/LinearClientTest.scala` - Integration tests for Linear API

**Modified files:**
- `iw` - Bootstrap script with hook discovery pattern
- `.iw/commands/doctor.scala` - Full implementation with base checks

---

## 6. Testing Strategy

### Unit Tests
- `CheckResult` formatting
- `DoctorChecks.register` and `runAll` behavior
- Pure check functions in isolation (e.g., `checkLinearToken` with mocked env)

### Integration Tests
- `Process.commandExists("tmux")` with real process execution
- `LinearClient.validateToken()` with real API (requires LINEAR_API_TOKEN)
- Invalid/missing token error handling

### Testing Hook Functions

Extract check logic into pure functions, test those directly:

```scala
// test/IssueHookDoctorTest.scala
//> using file "../commands/issue.hook-doctor.scala"

// Test the pure function, not the registration
class IssueHookDoctorTest extends munit.FunSuite:
  test("checkLinearToken returns Skip when not using Linear"):
    val config = ProjectConfiguration(TrackerConfig(IssueTrackerType.YouTrack, "TEST"))
    assertEquals(checkLinearToken(config), CheckResult.Skip("Not using Linear"))
```

### E2E Scenario Tests (Manual/BATS)

1. **Complete environment with valid configuration**
   - Setup: Valid config, tmux installed, valid LINEAR_API_TOKEN
   - Run: `./iw doctor`
   - Expect: All ✓, exit 0

2. **Missing API token**
   - Setup: Valid config with Linear, unset LINEAR_API_TOKEN
   - Run: `./iw doctor`
   - Expect: ✗ for token, hint for export, exit 1

3. **Missing tmux**
   - Setup: Valid config, tmux not in PATH
   - Run: `./iw doctor`
   - Expect: ✗ for tmux, installation hint, exit 1

4. **No hook files exist**
   - Setup: Remove all `*.hook-doctor.scala` files
   - Run: `./iw doctor`
   - Expect: Only base checks run, no errors from missing hooks

---

## 7. Acceptance Criteria

- [ ] `DoctorChecks` registry allows check registration
- [ ] Bootstrap script discovers `*.hook-{command}.scala` files
- [ ] Empty glob (no hooks) doesn't cause errors
- [ ] `iw doctor` runs base checks (git repo, config)
- [ ] `issue.hook-doctor.scala` checks LINEAR_API_TOKEN
- [ ] `start.hook-doctor.scala` checks tmux installation
- [ ] Each check shows ✓, ⚠, or ✗ with descriptive message
- [ ] Failed checks provide actionable hints
- [ ] Exit code 0 when all checks pass (or only warnings)
- [ ] Exit code 1 when any check errors
- [ ] All checks run even if some fail (no short-circuit)

---

## 8. Output Format

Example successful output:
```
Environment Check

  ✓ Git repository     Found
  ✓ Configuration      .iw/config.conf valid
  ✓ tmux               Installed
  ✓ LINEAR_API_TOKEN   Valid

All checks passed
```

Example with failures:
```
Environment Check

  ✓ Git repository     Found
  ✓ Configuration      .iw/config.conf valid
  ✗ tmux               Not found
    → Install: sudo apt install tmux (Debian/Ubuntu) or brew install tmux (macOS)
  ✓ LINEAR_API_TOKEN   Set
  ✗ Linear API         Authentication failed
    → Check token at linear.app/settings/api

2 checks failed
```

Example with skip:
```
Environment Check

  ✓ Git repository     Found
  ✓ Configuration      .iw/config.conf valid
  ✓ tmux               Installed
  - LINEAR_API_TOKEN   Skipped (not using Linear)

All checks passed
```

---

## 9. Design Rationale

### Why Hook-Based Architecture?

1. **Colocation**: Commands and their checks live together - code that belongs together stays together
2. **Extensibility**: Adding a new command with checks doesn't require modifying doctor
3. **Generalization**: The `*.hook-{command}.scala` pattern works for any future hook type
4. **Testability**: Pure check functions can be tested in isolation

### Why Shell Glob via find?

Using `find` instead of relying on `//> using files` globs:
1. **Reliability**: Shell globs are well-understood and tested
2. **POSIX compliance**: Works in any POSIX shell, not just bash
3. **Safe empty case**: `find` returns empty string when no matches, which expands to nothing

### Why Mutable Registry?

The `var registry` in `DoctorChecks` is mutable, but:
1. Mutation only happens at initialization time (file load)
2. After startup, effectively immutable
3. Standard pattern for plugin registration
4. Pragmatic trade-off for simplicity

---

## 10. Notes

- YouTrack API client can be stubbed for now (project uses Linear)
- API token validation should timeout after ~10 seconds to avoid hanging
- Consider adding `--verbose` flag to show timing for each check
- tmux version in output is nice-to-have, not required
