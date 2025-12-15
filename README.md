# iw-cli

Project-local CLI tool for managing git worktrees and integrating with issue trackers.

## Overview

`iw` is a project-local tool (similar to mill) that:
- Manages git worktrees as sibling directories for isolated development
- Integrates with issue trackers (Linear, YouTrack, GitHub, GitLab)
- Provides tmux session management per worktree
- Supports project-specific extensibility via scala-cli scripts

## Installation

### Quick Start

Download and run the bootstrap script in your project root:

```bash
curl -L https://github.com/iterative-works/iw-cli/releases/latest/download/iw-bootstrap -o iw
chmod +x iw
./iw init
```

The bootstrap script will:
1. Read the version from `.iw/config.conf` (defaults to "latest")
2. Download the specified version to `~/.local/share/iw/versions/<version>/`
3. Pre-compile dependencies for offline use
4. Execute the requested command

### Version Pinning

Pin a specific version in your project's `.iw/config.conf`:

```hocon
version = "0.1.0"
```

Or use the latest release:

```hocon
version = "latest"
```

If not specified, defaults to "latest".

### Requirements

- **scala-cli**: Install from [scala-cli.virtuslab.org](https://scala-cli.virtuslab.org/install)
- **git**: For worktree management
- **tmux** (optional): For session management
- **curl**: For downloading releases

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
