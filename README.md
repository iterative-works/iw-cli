# iw-cli

Project-local CLI tool for managing git worktrees and integrating with issue trackers.

## Overview

`iw` is a project-local tool (similar to mill) that:
- Manages git worktrees as sibling directories for isolated development
- Integrates with issue trackers (Linear, YouTrack, GitHub, GitLab)
- Provides tmux session management per worktree
- Supports project-specific extensibility via scala-cli scripts

## Installation

Copy the `iw` bootstrap script to your project root:

```bash
# Coming soon
```

Then run:

```bash
./iw init
```

## Commands

| Command | Description |
|---------|-------------|
| `iw init` | Interactive setup, creates `.iw/config.yaml` |
| `iw doctor` | Validate environment (tokens, tmux, etc.) |
| `iw start <issue-id>` | Create sibling worktree + tmux session |
| `iw open [issue-id]` | Open tmux session (defaults to current branch) |
| `iw rm <issue-id>` | Kill session, remove worktree, delete branch |
| `iw issue` | Fetch issue details from configured tracker |

## Worktree Layout

Worktrees are created as sibling directories:

```
~/projects/
├── myproject/              # main worktree (master/main)
├── myproject-IW-123/       # worktree for issue IW-123
├── myproject-IW-456/       # worktree for issue IW-456
```

## License

MIT
