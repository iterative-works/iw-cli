---
name: iw-cli-ops
description: |
  Use and operate iw-cli for worktree management and issue tracking.
  TRIGGERS: When creating worktrees, managing issue branches, starting work on issues,
  running project tests, checking project health, or interacting with the dashboard.
---

# iw-cli Operations

iw-cli manages git worktrees integrated with issue trackers (GitHub, Linear, YouTrack).

## Project Context

This project uses:
- **Tracker**: GitHub (`iterative-works/iw-cli`)
- **Issue ID format**: Numeric (e.g., `48`, `132`)

## Quick Reference

| Command | Purpose | Example |
|---------|---------|---------|
| `./iw start <id>` | Create worktree + tmux session for issue | `./iw start 48` |
| `./iw open [id]` | Open existing worktree session | `./iw open` |
| `./iw rm <id>` | Remove worktree and session | `./iw rm 48 --force` |
| `./iw issue [id]` | Fetch and display issue details | `./iw issue` |
| `./iw feedback "title"` | Submit bug/feature to iw-cli | `./iw feedback "Bug" --type bug` |
| `./iw server <cmd>` | Manage dashboard server | `./iw server start` |
| `./iw dashboard` | Open dashboard in browser | `./iw dashboard` |
| `./iw init` | Initialize project config | `./iw init --tracker=github` |
| `./iw doctor` | Check dependencies and config | `./iw doctor` |
| `./iw test [type]` | Run tests | `./iw test unit` |
| `./iw version` | Show version info | `./iw version --verbose` |

## Command Details

### Worktree Lifecycle

#### start - Create worktree for an issue

Creates a git worktree and tmux session for isolated issue work.

```bash
./iw start <issue-id>
```

- Creates worktree at `../<project>-<issue-id>/` (sibling directory)
- Creates new branch `<issue-id>` or uses existing
- Creates tmux session `<project>-<issue-id>`
- Switches to or attaches the session

#### open - Open existing worktree

Opens an existing worktree's tmux session, creating session if needed.

```bash
./iw open [issue-id]
```

- If no issue-id given, infers from current branch
- Creates session if worktree exists but session doesn't
- Switches (in tmux) or attaches (outside tmux)

#### rm - Remove worktree

Removes worktree and kills tmux session with safety checks.

```bash
./iw rm <issue-id> [--force]
```

- Warns about uncommitted changes (unless `--force`)
- Cannot remove if you're inside the target session
- Kills tmux session if running
- Does NOT delete the git branch (manual cleanup)

### Issue Tracking

#### issue - Fetch issue details

Displays issue information from the configured tracker.

```bash
./iw issue [issue-id]
```

- Without ID: infers from current branch name
- Displays: title, status, assignee, description

#### feedback - Submit feedback to iw-cli

Creates GitHub issues in the iw-cli repository for bugs/features.

```bash
./iw feedback "Issue title" [--description "Details"] [--type bug|feature]
```

- Requires `gh` CLI authenticated
- Default type: `feature`

### Dashboard Server

#### server - Manage dashboard server

Controls the background dashboard server process.

```bash
./iw server start   # Start daemon (background process)
./iw server stop    # Stop the server
./iw server status  # Check if running
```

- Server tracks registered worktrees
- Fetches issue data from trackers
- Shows workflow progress from task files
- Config at `~/.local/share/iw/server/config.json`

#### dashboard - Open dashboard

Starts server (if needed) and opens browser to dashboard.

```bash
./iw dashboard
```

- Server runs in foreground when started this way
- Press Ctrl+C to stop

### Setup & Development

#### init - Initialize project

Creates `.iw/config.conf` for the project.

```bash
./iw init [--force] [--tracker=github|linear|youtrack] [--team=TEAM]
```

- Auto-detects GitHub from git remote
- `--force` overwrites existing config
- For GitHub: extracts repository from remote
- For Linear/YouTrack: requires team identifier

#### doctor - Health check

Validates dependencies and configuration.

```bash
./iw doctor
```

Checks:
- Git repository exists
- Config file valid
- Tracker-specific requirements (gh CLI for GitHub, API tokens for others)

#### test - Run tests

Runs unit tests (Scala/munit) and E2E tests (BATS).

```bash
./iw test           # Run all tests
./iw test unit      # Unit tests only (.iw/core/test/*.scala)
./iw test e2e       # E2E tests only (.iw/test/*.bats)
```

#### version - Show version

```bash
./iw version [--verbose]
```

## Composing Ad-Hoc Scripts

For custom operations, write Scala scripts using core modules:

```bash
scala-cli run script.scala .iw/core/*.scala -- args
```

### Key Core Modules

| Module | Purpose |
|--------|---------|
| `Config.scala` | ProjectConfiguration, IssueTrackerType |
| `ConfigRepository.scala` | Read/write `.iw/config.conf` |
| `IssueId.scala` | Parse/validate issue IDs, extract from branch |
| `Git.scala` | GitAdapter - branch, remote, uncommitted changes |
| `GitWorktree.scala` | GitWorktreeAdapter - create/remove worktrees |
| `Tmux.scala` | TmuxAdapter - session management |
| `Issue.scala` | Issue model and IssueTracker trait |
| `GitHubClient.scala` | Fetch/create GitHub issues via gh CLI |
| `Output.scala` | Console output (info, error, success, warning) |
| `Process.scala` | ProcessAdapter - run shell commands |
| `Prompt.scala` | Interactive prompts (confirm, ask) |

### Example: Custom Script

```scala
//> using scala 3.3.1

import iw.core.*

@main def customScript(): Unit =
  // Read config
  val configPath = os.pwd / ".iw" / "config.conf"
  ConfigFileRepository.read(configPath) match
    case None =>
      Output.error("No config found")
      sys.exit(1)
    case Some(config) =>
      Output.info(s"Project: ${config.projectName}")
      Output.info(s"Tracker: ${config.trackerType}")
```

## Environment Variables

| Variable | Tracker | Purpose |
|----------|---------|---------|
| `LINEAR_API_TOKEN` | Linear | API authentication |
| `YOUTRACK_API_TOKEN` | YouTrack | API authentication |
| (none) | GitHub | Uses `gh` CLI auth |

## Getting Command Details

Use `--describe` flag to get detailed command info:

```bash
./iw --describe start
./iw --describe issue
```

## Common Workflows

### Start work on a new issue

```bash
./iw start 123          # Creates worktree and session
# Work on the issue in the tmux session
./iw issue              # View issue details
```

### Switch between issues

```bash
./iw open 123           # Switch to issue 123's session
./iw open 456           # Switch to issue 456's session
```

### Clean up completed work

```bash
./iw rm 123             # Remove worktree (prompts if dirty)
git branch -d 123       # Delete branch manually
```

### Check project health

```bash
./iw doctor             # Verify setup
./iw test               # Run all tests
```
