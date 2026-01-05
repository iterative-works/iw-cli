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

## GitHub Integration

### Repository Auto-Detection

When using `iw init --tracker=github`, the repository is automatically detected from your git remote URL:

```bash
# Automatically detects "iterative-works/iw-cli" from remote URL
iw init --tracker=github
```

**Supported URL formats:**
- HTTPS: `https://github.com/owner/repo.git`
- HTTPS without .git: `https://github.com/owner/repo`
- HTTPS with username: `https://username@github.com/owner/repo.git`
- HTTPS with trailing slash: `https://github.com/owner/repo/`
- SSH: `git@github.com:owner/repo.git`
- SSH without .git: `git@github.com:owner/repo`

**Multiple remotes:**
When your repository has multiple remotes (e.g., `origin` and `upstream`), the `origin` remote is always used for auto-detection.

**Manual input:**
If auto-detection fails (non-GitHub remote or no remote configured), you'll be prompted to enter the repository manually in `owner/repo` format.

### Authentication

GitHub integration uses the `gh` CLI for authentication:

```bash
# Authenticate once
gh auth login

# Then use iw normally
iw init --tracker=github
iw issue
```

No API tokens needed - `iw` uses your existing `gh` authentication.

## GitLab Integration

### Repository Auto-Detection

When using `iw init --tracker=gitlab`, the repository is automatically detected from your git remote URL:

```bash
# Automatically detects "owner/project" from remote URL
iw init --tracker=gitlab --team-prefix=PROJ
```

**Supported URL formats:**
- HTTPS: `https://gitlab.com/owner/project.git`
- HTTPS without .git: `https://gitlab.com/owner/project`
- HTTPS with trailing slash: `https://gitlab.com/owner/project/`
- SSH: `git@gitlab.com:owner/project.git`
- SSH without .git: `git@gitlab.com:owner/project`
- Self-hosted: `https://gitlab.company.com/owner/project.git`
- Nested groups: `https://gitlab.com/company/team/project.git`

**Multiple remotes:**
When your repository has multiple remotes (e.g., `origin` and `upstream`), the `origin` remote is always used for auto-detection.

**Manual input:**
If auto-detection fails (non-GitLab remote or no remote configured), you'll be prompted to enter the repository manually in `owner/project` or `group/subgroup/project` format.

### Authentication

GitLab integration uses the `glab` CLI for authentication:

```bash
# Install glab CLI (macOS)
brew install glab

# Install glab CLI (Linux/Windows)
# See https://gitlab.com/gitlab-org/cli#installation

# Authenticate once
glab auth login

# Then use iw normally
iw init --tracker=gitlab --team-prefix=PROJ
iw issue
```

No API tokens needed - `iw` uses your existing `glab` authentication.

### Self-Hosted GitLab

For self-hosted GitLab instances, specify the base URL during initialization:

```bash
iw init --tracker=gitlab --team-prefix=PROJ --base-url=https://gitlab.company.com
```

Or add it manually to `.iw/config.conf`:

```hocon
tracker {
  type = gitlab
  repository = "team/project"
  teamPrefix = "PROJ"
  baseUrl = "https://gitlab.company.com"  # Optional, defaults to gitlab.com
}
```

### Nested Groups

GitLab supports nested group structures like `company/team/project`. These are fully supported:

```bash
# Auto-detection works with nested groups
git remote add origin https://gitlab.com/company/team/project.git
iw init --tracker=gitlab --team-prefix=PROJ
```

Configuration:

```hocon
tracker {
  type = gitlab
  repository = "company/team/project"
  teamPrefix = "PROJ"
}
```

## Worktree Layout

Worktrees are created as sibling directories:

```
~/projects/
├── myproject/              # main worktree (master/main)
├── myproject-IW-123/       # worktree for issue IW-123
├── myproject-IW-456/       # worktree for issue IW-456
```

## Development

### Setup

Clone the repository and use the local `iw` script directly:

```bash
git clone https://github.com/iterative-works/iw-cli.git
cd iw-cli
./iw --list
```

The local `iw` script runs commands from `.iw/commands/` without downloading anything - ideal for development.

### Testing in Other Projects

To test your local iw-cli changes in other projects, set `IW_HOME`:

```bash
# In any project using iw-bootstrap
IW_HOME=/path/to/iw-cli ./iw --list

# Or export for the session
export IW_HOME=/path/to/iw-cli
./iw start ISSUE-123
```

This bypasses version download and uses your local `iw-run` directly.

### Testing

The project has three types of tests:

#### Unit Tests (Scala/munit)

Run Scala unit tests with:

```bash
./iw test unit
```

Or directly with scala-cli:

```bash
scala-cli test .iw/core/test/*.scala .iw/core/*.scala
```

#### E2E Tests (BATS)

End-to-end tests verify the CLI behavior. By default, tests that would create real Linear issues are skipped.

Run E2E tests (without live API calls):

```bash
./iw test e2e
```

Or directly with BATS:

```bash
bats .iw/test/
```

#### Live API Tests

Some E2E tests can create real Linear issues for comprehensive testing. These are **disabled by default** to prevent issue accumulation.

To enable live API tests:

```bash
ENABLE_LIVE_API_TESTS=1 ./iw test e2e
```

**Requirements:**
- `LINEAR_API_TOKEN` environment variable must be set
- `ENABLE_LIVE_API_TESTS=1` must be explicitly set

**Warning:** Live API tests will create real issues in Linear with `[TEST]` prefix. These should be cleaned up periodically.

#### Run All Tests

```bash
./iw test
```

Or manually:

```bash
scala-cli test .iw/core/test/*.scala .iw/core/*.scala && bats .iw/test/
```

### Project Structure

```
iw-cli/
├── iw                    # Development launcher (runs locally)
├── iw-bootstrap          # Distribution bootstrap (downloads releases)
├── iw-run                # Distribution launcher (in release tarball)
├── .iw/
│   ├── commands/         # Command implementations (*.scala)
│   ├── core/             # Shared library code
│   │   ├── project.scala # Build configuration (deps, Scala version)
│   │   └── test/         # Unit tests
│   ├── scripts/          # Build/release scripts
│   └── test/             # Integration tests (*.bats)
└── RELEASE.md            # Release process documentation
```

### Creating a Release

See [RELEASE.md](RELEASE.md) for the full release process. Quick version:

```bash
.iw/scripts/package-release.sh 0.1.0
# Creates release/iw-cli-0.1.0.tar.gz
```

## License

MIT
