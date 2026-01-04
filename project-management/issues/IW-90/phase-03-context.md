# Phase 03 Context: Configure GitLab tracker during iw init

**Issue:** IW-90
**Phase:** 3 of 7
**Status:** Not Started

## Goals

This phase enables users to configure GitLab as their issue tracker during `iw init`. After this phase, users can set up GitLab tracking for their projects through both automatic detection (from git remote) and manual configuration.

**Primary goal:** When a user runs `iw init` in a GitLab-hosted repository, the tool should detect GitLab and offer it as the tracker option, or allow manual selection of GitLab as the tracker type.

## Scope

### In Scope

1. **IssueTrackerType enum extension**
   - Already done in Phase 1: `GitLab` variant exists
   - Verify serialization/deserialization works correctly

2. **GitLab remote URL detection**
   - Recognize `gitlab.com` in remote URLs
   - Support self-hosted GitLab instances (custom domains)
   - Extract repository path (owner/project, including nested groups)

3. **TrackerDetector update**
   - Add GitLab detection logic to `suggestTracker`
   - Return `IssueTrackerType.GitLab` when gitlab.com detected

4. **ConfigSerializer update**
   - Serialize GitLab tracker configuration to HOCON
   - Parse GitLab tracker configuration from HOCON
   - Handle optional `baseUrl` for self-hosted instances

5. **init.scala update**
   - Add "gitlab" to tracker type selection menu
   - Prompt for repository when GitLab selected manually
   - Prompt for baseUrl when self-hosted GitLab detected or selected
   - Display next steps including `glab auth login`

### Out of Scope

- GitLab authentication during init (glab handles this separately)
- Repository validation during init (deferred to first fetch)
- GitLab-specific project settings (labels, milestones, etc.)

## Dependencies

### From Previous Phases

- **Phase 1:** `IssueTrackerType.GitLab` enum variant in Config.scala
- **Phase 2:** Error formatting functions (for auth hints in next steps)

### External Dependencies

- Existing `GitRemote` for URL parsing
- Existing `TrackerDetector` for detection logic
- Existing `ConfigSerializer` for HOCON handling

### Codebase Dependencies

- `.iw/core/Config.scala` - Contains IssueTrackerType enum
- `.iw/core/GitRemote.scala` - Git remote URL parsing
- `.iw/core/TrackerDetector.scala` - Tracker auto-detection
- `.iw/core/ConfigSerializer.scala` - HOCON serialization
- `.iw/commands/init.scala` - Init command implementation

## Technical Approach

### 1. GitRemote Enhancement

Check if GitRemote already handles GitLab URLs. If not, add support:

```scala
// GitRemote.scala - verify/add GitLab host detection
def isGitLabHost(host: String): Boolean =
  host == "gitlab.com" ||
  host.endsWith(".gitlab.com") ||
  host.contains("gitlab")  // Catches self-hosted like gitlab.company.com
```

GitLab URL patterns to support:
- `https://gitlab.com/owner/project.git`
- `git@gitlab.com:owner/project.git`
- `https://gitlab.company.com/group/subgroup/project.git`
- `git@gitlab.company.com:group/project.git`

### 2. TrackerDetector Update

```scala
// TrackerDetector.scala
def suggestTracker(remotes: List[GitRemote]): Option[IssueTrackerType] =
  remotes.find(r => isGitHubHost(r.host)).map(_ => IssueTrackerType.GitHub)
    .orElse(remotes.find(r => isGitLabHost(r.host)).map(_ => IssueTrackerType.GitLab))
    .orElse(remotes.find(r => isLinearHost(r.host)).map(_ => IssueTrackerType.Linear))
    // ... other trackers
```

### 3. ConfigSerializer Enhancement

```scala
// Serialize GitLab config
case IssueTrackerType.GitLab =>
  s"""tracker {
     |  type = gitlab
     |  repository = "${config.repository}"
     |  ${config.baseUrl.map(u => s"baseUrl = \"$u\"").getOrElse("")}
     |}""".stripMargin

// Parse GitLab config
case "gitlab" =>
  val repository = trackerConfig.getString("repository")
  val baseUrl = if (trackerConfig.hasPath("baseUrl"))
    Some(trackerConfig.getString("baseUrl"))
  else None
  // Build IssueTrackerConfig for GitLab
```

### 4. init.scala Enhancement

```scala
// Add GitLab to tracker options
val trackerOptions = List(
  "github" -> "GitHub",
  "gitlab" -> "GitLab",
  "linear" -> "Linear",
  "youtrack" -> "YouTrack",
  "none" -> "None"
)

// GitLab-specific prompts
case "gitlab" =>
  val repository = promptFor("GitLab repository (owner/project or group/subgroup/project)")
  val isCustomHost = promptYesNo("Is this a self-hosted GitLab instance?")
  val baseUrl = if (isCustomHost) Some(promptFor("GitLab base URL")) else None
  // Build config
```

### 5. Next Steps Display

After successful GitLab configuration:

```
GitLab tracker configured successfully!

Next steps:
1. Install glab CLI: brew install glab (macOS) or see https://gitlab.com/gitlab-org/cli
2. Authenticate: glab auth login
3. Try: iw issue <issue-number>
```

## Files to Modify

| File | Change |
|------|--------|
| `.iw/core/GitRemote.scala` | Add GitLab host detection (if not present) |
| `.iw/core/TrackerDetector.scala` | Add GitLab to suggestTracker |
| `.iw/core/ConfigSerializer.scala` | Add GitLab HOCON serialization/parsing |
| `.iw/core/test/TrackerDetectorTest.scala` | Test GitLab detection |
| `.iw/core/test/ConfigSerializerTest.scala` | Test GitLab serialization |
| `.iw/commands/init.scala` | Add GitLab option and prompts |

## Testing Strategy

### Unit Tests

1. **GitRemote tests:**
   - `isGitLabHost("gitlab.com")` returns true
   - `isGitLabHost("gitlab.company.com")` returns true
   - `isGitLabHost("github.com")` returns false
   - Repository extraction from GitLab URLs

2. **TrackerDetector tests:**
   - Remote with gitlab.com suggests GitLab tracker
   - Remote with custom gitlab.* suggests GitLab tracker
   - Multiple remotes with GitLab uses first match

3. **ConfigSerializer tests:**
   - Serialize GitLab config to HOCON
   - Parse GitLab config from HOCON
   - Handle missing optional baseUrl
   - Handle present baseUrl

### E2E Tests (BATS)

1. `iw init` in repo with gitlab.com remote suggests GitLab
2. Manual selection of GitLab creates correct config
3. Config file contains `tracker.type = gitlab`

## Acceptance Criteria

- [ ] `TrackerDetector.suggestTracker` returns GitLab for gitlab.com remotes
- [ ] `TrackerDetector.suggestTracker` returns GitLab for self-hosted GitLab remotes
- [ ] `ConfigSerializer` correctly serializes GitLab tracker config
- [ ] `ConfigSerializer` correctly parses GitLab tracker config
- [ ] Optional `baseUrl` field handled correctly (omitted for gitlab.com, present for self-hosted)
- [ ] `init.scala` offers GitLab as tracker option
- [ ] `init.scala` prompts for baseUrl when self-hosted GitLab selected
- [ ] Next steps displayed after GitLab configuration
- [ ] Unit tests pass for all GitLab configuration logic
- [ ] Existing tracker configurations continue to work

## Notes

- Check existing TrackerDetector to understand current detection patterns
- Look at how YouTrack handles baseUrl for self-hosted instances
- GitLab nested groups (e.g., `company/team/project`) must be supported
- Default baseUrl should be `https://gitlab.com` when not specified
- The `teamPrefix` field may or may not apply to GitLab - investigate current usage
