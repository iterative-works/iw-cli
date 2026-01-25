# .iw/core/ Architecture

This directory organizes modules by **architectural responsibility** following FCIS (Functional Core Imperative Shell) principles.

## Directory Structure

```
.iw/core/
├── model/        # Pure domain types - no I/O, no side effects
├── adapters/     # I/O operations - shell commands, API clients
├── output/       # CLI presentation - console formatting
└── dashboard/    # Dashboard server internals - not for scripts
```

## Placement Criteria

Use this flowchart to determine where a module belongs:

```
Is this for the dashboard server specifically?
├─ YES → dashboard/
└─ NO → Does it perform I/O (shell, network, filesystem)?
         ├─ YES → adapters/
         └─ NO → Does it format output for the CLI?
                  ├─ YES → output/
                  └─ NO → model/
```

### model/ - Pure Domain Types

**Purpose:** Pure functional core with no side effects.

**Belongs here:**
- Domain value objects (IssueId, WorktreePath)
- Domain entities (Issue, Config)
- Data structures (IssueData, ReviewState)
- Enums and constants
- Pure validation logic

**Must NOT:**
- Perform I/O operations
- Call external systems
- Mutate state
- Depend on `adapters/`, `output/`, or `dashboard/`

**Examples:**
- `IssueId.scala` - issue ID parsing/validation
- `Config.scala` - configuration types
- `WorktreePath.scala` - path value object
- `ReviewState.scala` - review state data

### adapters/ - I/O Operations

**Purpose:** Imperative shell that performs side effects.

**Belongs here:**
- Shell command execution
- API clients (GitHub, Linear, GitLab, YouTrack)
- Filesystem operations
- User input prompts
- External process management

**May depend on:**
- `model/` (to use domain types)

**Examples:**
- `Git.scala` - git operations via shell
- `GitHubClient.scala` - GitHub API client
- `Process.scala` - shell command execution
- `Prompt.scala` - user input prompts

### output/ - CLI Presentation

**Purpose:** CLI-specific formatting and rendering.

**Belongs here:**
- Console output formatting
- Text rendering
- CLI-specific display logic

**May depend on:**
- `model/` (to format domain types)

**Examples:**
- `Output.scala` - console output formatting
- `IssueFormatter.scala` - issue display formatting
- `MarkdownRenderer.scala` - markdown rendering

### dashboard/ - Server Internals

**Purpose:** Dashboard server implementation (not public API).

**Belongs here:**
- HTTP server (CaskServer)
- Dashboard services
- Cache services
- Server state management
- All subdirectories: `application/`, `domain/`, `infrastructure/`, `presentation/`

**May depend on:**
- Everything (`model/`, `adapters/`, `output/`)

**Examples:**
- `CaskServer.scala` - HTTP server
- `DashboardService.scala` - dashboard logic
- `IssueCacheService.scala` - issue caching

## Public API vs Internal

### Public API (for .iw/commands/ scripts)
- `model/` - ✅ Public
- `adapters/` - ✅ Public
- `output/` - ✅ Public
- `dashboard/` - ❌ Internal

Scripts in `.iw/commands/` should import from `model/`, `adapters/`, and `output/`, but NOT from `dashboard/`.

## Import Conventions

### In commands (`.iw/commands/*.scala`)
```scala
import iw.core.model.*           // Pure domain types
import iw.core.adapters.*        // I/O operations
import iw.core.output.*          // CLI formatting
// Do NOT import iw.core.dashboard.*
```

### Within core modules
```scala
// model/ cannot import from other core dirs
package iw.core.model

// adapters/ can import model/
package iw.core.adapters
import iw.core.model.*

// output/ can import model/
package iw.core.output
import iw.core.model.*

// dashboard/ can import everything
package iw.core.dashboard
import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*
```

## Dependency Rules

```
dashboard/  →  adapters/  →  model/
    ↓           ↓
  output/   ────┘
```

- `model/` depends on nothing (pure)
- `adapters/` may depend on `model/`
- `output/` may depend on `model/`
- `dashboard/` may depend on all others

## Maintaining This Structure

### When adding a new module

1. Ask: "Does it perform I/O?" → `adapters/`
2. Ask: "Is it pure data/logic?" → `model/`
3. Ask: "Is it CLI formatting?" → `output/`
4. Ask: "Is it dashboard-specific?" → `dashboard/`

### When a module doesn't fit cleanly

- If it does ANY I/O, it goes to `adapters/`
- If it's pure but calls adapters, extract the pure parts to `model/`
- When in doubt, prefer `adapters/` over `model/`

### Updating llms.txt

When Phase 2 is complete, `llms.txt` will document:
- All modules in `model/`, `adapters/`, `output/` (public API)
- None of the modules in `dashboard/` (internal)

## Testing Implications

### model/ modules
- Test with pure assertions
- No mocking needed (no I/O)
- Fast, deterministic tests

### adapters/ modules
- May need mocking for external dependencies
- Integration tests for shell commands
- Can use Testcontainers for databases

### output/ modules
- Test with expected string outputs
- Capture stdout for assertions

### dashboard/ modules
- Full integration tests
- May need test server instances
- Test with real HTTP calls
