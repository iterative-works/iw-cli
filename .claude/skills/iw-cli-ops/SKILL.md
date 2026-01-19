---
name: iw-cli-ops
description: |
  Fetch issue descriptions and manage development worktrees for isolated issue work.

  Use when:
  - Reading, planning, or discussing an issue (e.g., "read the issue", "what's issue 48 about?", "let's plan IW-48")
  - Need issue title, description, status, or assignee from GitHub/Linear/YouTrack/GitLab
  - Starting work on an issue (creates isolated worktree + tmux session)
  - Opening or switching to an existing worktree
  - Removing worktrees after merging or abandoning work
  - Checking environment health ("is everything set up correctly?")
---

# iw-cli Operations Guide

## Project Context

This project uses **GitHub** for issue tracking.
- Repository: `iterative-works/iw-cli`
- Team prefix: `IW`
- Issue ID format: `IW-<number>` (e.g., `IW-48`, `IW-132`) or just the number
- Branch naming: `IW-<number>` or `IW-<number>-description`

## Common Workflows

### Fetching Issue Information

**Most common use case:** Get issue details before planning or discussing.

```bash
./iw issue 48              # Fetch issue #48 (IW-48)
./iw issue IW-48           # Same, with full ID
./iw issue                 # Fetch issue for current branch
```

Returns: ID, title, status, assignee, and full description.

### Starting Work on an Issue

Creates an isolated worktree with its own tmux session:

```bash
./iw start 48              # Creates ../iw-cli-IW-48/ worktree + tmux session
```

This:
1. Creates a git worktree at `../iw-cli-IW-48/`
2. Creates (or uses existing) branch `IW-48`
3. Starts a tmux session named `iw-cli-IW-48`
4. Switches to (or attaches) the session

### Switching Between Issues

```bash
./iw open 48               # Switch to session for IW-48
./iw open                  # Switch to session for current branch's issue
```

If inside tmux, switches sessions. If outside, attaches.

### Cleaning Up After Merge

```bash
./iw rm 48                 # Prompts if uncommitted changes exist
./iw rm 48 --force         # Skip safety prompts
```

Removes the worktree and kills the tmux session.

## All Available Commands

| Command | Purpose |
|---------|---------|
| `./iw issue [id]` | Fetch and display issue details |
| `./iw start <id>` | Create worktree + tmux session |
| `./iw open [id]` | Open existing worktree session |
| `./iw rm <id> [--force]` | Remove worktree safely |
| `./iw doctor` | Check environment and configuration |
| `./iw init [--force]` | Initialize project configuration |
| `./iw server start\|stop\|status` | Manage dashboard server |
| `./iw dashboard` | Start server + open in browser |
| `./iw register` | Register current worktree with dashboard |
| `./iw test [unit\|e2e]` | Run tests |
| `./iw version [--verbose]` | Show version |
| `./iw feedback "title" [--type bug\|feature]` | Submit feedback |

## Getting Help

```bash
./iw --describe start      # Detailed docs for a command
./iw --help                # List all commands
```

## Environment Setup

For GitHub tracker (this project):
```bash
gh auth login              # Authenticate GitHub CLI
./iw doctor                # Verify setup
```

For other trackers:
- **Linear:** Set `LINEAR_API_TOKEN` environment variable
- **YouTrack:** Set `YOUTRACK_API_TOKEN` environment variable
- **GitLab:** Run `glab auth login`

## Composing Ad-hoc Scripts

For custom automation using iw-cli's core modules, see the `iw-command-creation` skill.

Quick example:
```bash
scala-cli run script.scala /home/mph/Devel/projects/iw-cli/.iw/core/*.scala -- args
```

Key modules: `IssueId`, `Issue`, `GitAdapter`, `GitWorktreeAdapter`, `TmuxAdapter`, `Output`, `ProcessAdapter`.

## Tips

1. **Issue inference:** Most commands infer the issue from the current branch name
2. **Team prefix:** Numbers are auto-prefixed with `IW-` (e.g., `48` → `IW-48`)
3. **Worktree isolation:** Each issue gets its own directory, separate from main
4. **Tmux sessions:** Named after worktree directory for easy switching
