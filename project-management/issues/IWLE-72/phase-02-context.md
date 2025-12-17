# Phase 2 Context: Initialize project with issue tracker configuration

**Issue:** IWLE-72
**Phase:** 2 of 7
**Prerequisite:** Phase 1 (Bootstrap script) - COMPLETE

## Goals

This phase implements the `iw init` command that:
1. Detects git remote to suggest appropriate issue tracker (Linear/YouTrack)
2. Guides user through interactive configuration setup
3. Creates `.iw/config.conf` with HOCON format configuration
4. Provides clear instructions for setting API token environment variable

## Scope

**In Scope:**
- Git remote URL parsing to detect tracker type
- Interactive prompts for tracker selection and team configuration
- HOCON configuration file creation
- Tracker-specific hints (which env var to set)
- Error handling for non-git directories

**Out of Scope:**
- API token validation (that's Phase 3 - doctor command)
- Issue fetching or any API calls
- Worktree operations
- Session management

## Dependencies

**From Phase 1:**
- `Output` utility from `.iw/core/Output.scala` (info, error, success, section, keyValue)
- Bootstrap script command discovery pattern
- Structured header format (PURPOSE, USAGE, ARGS, EXAMPLE)

**External:**
- Git installed and available
- Typesafe Config library for HOCON parsing

## Technical Approach

### Domain Layer

```scala
// Value objects
case class GitRemote(url: String):
  def host: String = // extract host from git@ or https:// URL

enum IssueTrackerType:
  case Linear, YouTrack

case class ProjectConfiguration(
  trackerType: IssueTrackerType,
  team: String,
  projectName: String
)
```

### Detection Logic

```
gitlab.e-bs.cz   → YouTrack
github.com       → Linear
Otherwise        → Ask user
```

### Configuration Format (HOCON)

```hocon
# .iw/config.conf
tracker {
  type = linear    # or youtrack
  team = IWLE      # team/project identifier
}

project {
  name = kanon     # derived from directory name
}
```

### Interactive Flow

1. Check we're in a git repository (error if not)
2. Check `.iw/config.conf` doesn't already exist (error if it does, suggest `--force`)
3. Read git remote origin URL
4. Suggest tracker based on host
5. Prompt for confirmation or alternative
6. Prompt for team/project identifier
7. Write config file
8. Display success message with env var hint

## Files to Modify

**New files:**
- `.iw/core/Config.scala` - Configuration domain model and HOCON parsing
- `.iw/core/Git.scala` - Git remote parsing utilities
- `.iw/core/Prompt.scala` - Interactive console prompts
- `.iw/commands/init.scala` - Main init command implementation

**Modify:**
- None from Phase 1 (init.scala stub will be replaced)

## Testing Strategy

### Unit Tests

- `GitRemote` URL parsing (github, gitlab, bitbucket, e-bs.cz, etc.)
- Tracker detection from host
- Configuration serialization to HOCON
- Configuration validation (required fields)

### Integration Tests

- Config file write/read roundtrip
- Git remote detection in actual git repo

### E2E Scenarios

1. `./iw init` in git repo → creates config, shows success
2. `./iw init` outside git repo → error message
3. `./iw init` when config exists → error, suggests --force
4. `./iw init --force` when config exists → overwrites
5. Interactive prompts work correctly

## Acceptance Criteria

- [ ] `.iw/config.conf` created with tracker type and team ID
- [ ] Host-based tracker detection suggests correct tracker type
- [ ] Config validation ensures required fields are present
- [ ] Clear instructions for setting environment variable for API token
- [ ] Clear error messages for missing git repo or invalid configuration
- [ ] Works with both SSH and HTTPS git remotes
- [ ] Project name auto-detected from current directory name

## Notes

- The configuration file DOES NOT contain secrets (tokens are in env vars)
- Configuration CAN be committed to git to share tracker config with team
- Use Typesafe Config library for HOCON - it's already standard in Scala ecosystem
- HOCON allows comments, which is nice for explaining config options

## Environment Variable Hints

After successful init, display:

For Linear:
```
Set your API token:
  export LINEAR_API_TOKEN=lin_api_...

Run ./iw doctor to verify your setup.
```

For YouTrack:
```
Set your API token:
  export YOUTRACK_API_TOKEN=perm:...

Run ./iw doctor to verify your setup.
```
