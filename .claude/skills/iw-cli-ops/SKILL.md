---
name: iw-cli-ops
description: |
  Use iw-cli for worktree and issue management operations. Invoke when:
  - Working on a specific issue (use `./iw start <issue-id>` or `./iw open`)
  - Removing completed worktrees (`./iw rm <issue-id>`)
  - Fetching issue details (`./iw issue [issue-id]`)
  - Checking environment setup (`./iw doctor`)
  - Managing the dashboard server (`./iw server start|stop|status`)
  - Submitting feedback about iw-cli itself (`./iw feedback`)
---

# iw-cli Operations Guide

## Project Context

This project uses **GitHub** for issue tracking.
- Repository: `iterative-works/iw-cli`
- Issue ID format: numeric (e.g., `48`, `132`)
- Branch naming: `<issue-number>-description` (e.g., `48-worktree-feature`)

## Available Commands

### Worktree Management

| Command | Purpose |
|---------|---------|
| `./iw start <issue-id>` | Create worktree + tmux session for issue |
| `./iw open [issue-id]` | Open existing worktree session (infers from branch if omitted) |
| `./iw rm <issue-id> [--force]` | Remove worktree with safety checks |

**Examples:**
```bash
./iw start 48              # Create worktree for issue #48
./iw open                  # Open session for current branch's issue
./iw rm 48                 # Remove worktree (prompts if uncommitted changes)
./iw rm 48 --force         # Remove without safety prompts
```

### Issue Operations

| Command | Purpose |
|---------|---------|
| `./iw issue [issue-id]` | Fetch and display issue details |
| `./iw init [--force]` | Initialize project configuration |
| `./iw doctor` | Check system dependencies and configuration |

**Examples:**
```bash
./iw issue 48              # Show issue #48 details
./iw issue                 # Show issue for current branch
./iw doctor                # Verify git, config, and dependencies
```

### Dashboard & Server

| Command | Purpose |
|---------|---------|
| `./iw server start` | Start background dashboard server |
| `./iw server stop` | Stop the dashboard server |
| `./iw server status` | Show server status and uptime |
| `./iw dashboard` | Start server + open in browser (foreground) |

### Development Tools

| Command | Purpose |
|---------|---------|
| `./iw test [unit\|e2e]` | Run tests (both by default) |
| `./iw version [--verbose]` | Show version info |
| `./iw feedback "title" [--type bug\|feature] [--description "..."]` | Submit iw-cli feedback |
| `./iw claude-sync [--force]` | Regenerate Claude Code skills |

**Examples:**
```bash
./iw test                  # Run all tests
./iw test unit             # Run only Scala unit tests
./iw test e2e              # Run only BATS E2E tests
./iw feedback "Bug in start command" --type bug
```

## Getting Command Details

For any command, use the `--describe` flag or read the source:
```bash
./iw --describe start      # Show detailed command documentation
```

Or read the command file directly:
```bash
cat .iw/commands/start.scala  # See full implementation
```

## Composing Ad-hoc Scripts

For custom automation, compose scripts using core modules:

```bash
scala-cli run script.scala .iw/core/*.scala -- args
```

### Key Core Modules

| Module | Package | Purpose |
|--------|---------|---------|
| `IssueId.scala` | `iw.core` | Parse/validate issue IDs, extract from branches |
| `Issue.scala` | `iw.core` | Issue entity and `IssueTracker` trait |
| `Config.scala` | `iw.core` | `ProjectConfiguration`, `IssueTrackerType` |
| `GitHubClient.scala` | `iw.core` | GitHub issue fetching via `gh` CLI |
| `Git.scala` | `iw.core` | `GitAdapter` for repository operations |
| `GitWorktree.scala` | `iw.core` | `GitWorktreeAdapter` for worktree ops |
| `Tmux.scala` | `iw.core` | `TmuxAdapter` for session management |
| `Output.scala` | `iw.core` | Formatted console output |
| `Process.scala` | `iw.core` | `ProcessAdapter` for shell commands |
| `WorktreePath.scala` | `iw.core` | Calculate worktree directory/session names |

### Example: Custom Script

```scala
// script.scala - Get issue ID from current branch
import iw.core.*

@main def getCurrentIssue(): Unit =
  val currentDir = os.pwd
  GitAdapter.getCurrentBranch(currentDir) match
    case Left(err) =>
      Output.error(err)
      sys.exit(1)
    case Right(branch) =>
      IssueId.fromBranch(branch) match
        case Left(err) => Output.error(s"Not on an issue branch: $err")
        case Right(id) => Output.info(s"Current issue: ${id.value}")
```

Run with:
```bash
scala-cli run script.scala .iw/core/*.scala
```

## Environment Variables

| Variable | Purpose |
|----------|---------|
| `LINEAR_API_TOKEN` | Linear API token (for Linear tracker) |
| `YOUTRACK_API_TOKEN` | YouTrack API token (for YouTrack tracker) |

For GitHub tracker, ensure `gh` CLI is installed and authenticated:
```bash
gh auth login
```

## Workflow Tips

1. **Starting work on an issue:**
   ```bash
   ./iw start 48   # Creates worktree at ../iw-cli-48/ with tmux session
   ```

2. **Switching between issues:**
   ```bash
   ./iw open 48    # Switches to or attaches session for issue 48
   ```

3. **Checking issue context:**
   ```bash
   ./iw issue      # Shows details for current branch's issue
   ```

4. **Cleaning up after merge:**
   ```bash
   ./iw rm 48      # Removes worktree and kills session
   ```

5. **Troubleshooting:**
   ```bash
   ./iw doctor     # Validates environment and configuration
   ```
