# Phase 1: Initialize project with GitHub tracker

**Issue:** IWLE-132
**Phase:** 1 of 6
**Status:** Complete

## Goals

This phase establishes the foundation for GitHub Issues support by:
1. Adding `GitHub` as a new tracker type in `IssueTrackerType`
2. Extending the config schema with a `repository` field for GitHub
3. Updating `iw init` to support `--tracker github`
4. Auto-detecting the repository from git remote (Story 5 combined)

## Scope

### In Scope
- Add `IssueTrackerType.GitHub` enum case
- Add `repository` field to ProjectConfiguration (optional, required for GitHub)
- Update config serialization/deserialization for GitHub tracker type
- Update `iw init --tracker github` command
- Parse owner/repo from git remote URL (HTTPS and SSH formats)
- Update TrackerDetector to suggest GitHub for github.com remotes
- Interactive mode offers GitHub as an option

### Out of Scope
- Creating issues (Phase 3)
- Viewing issues (Phase 5)
- gh CLI validation (Phase 4)
- Doctor command updates (Phase 6)
- Actually calling gh CLI (this phase only sets up config)

## Dependencies

### Previous Phases
None - this is the first phase.

### External Dependencies
- Existing `IssueTrackerType` enum (will be extended)
- Existing `ProjectConfiguration` model (will be extended)
- Existing `init.scala` command (will be modified)
- Existing git remote parsing infrastructure

## Technical Approach

### 1. Domain Layer Changes

**IssueTrackerType.scala:**
- Add `GitHub` case to the enum
- Ensure pattern matching in all usages handles the new case

**ProjectConfiguration.scala:**
- Add optional `repository: Option[String]` field
- Add validation: `repository` required when tracker is GitHub
- Add validation: `repository` format must be "owner/repo"

### 2. Infrastructure Layer Changes

**ConfigSerializer.scala:**
- Handle `github` as tracker type value
- Serialize/deserialize `repository` field

**TrackerDetector.scala:**
- Update to suggest GitHub when git remote contains "github.com"

**GitRemoteParser (new or extend existing):**
- Parse HTTPS URL: `https://github.com/owner/repo.git` → `owner/repo`
- Parse SSH URL: `git@github.com:owner/repo.git` → `owner/repo`
- Handle URLs with or without `.git` suffix

### 3. Presentation Layer Changes

**init.scala:**
- Add GitHub to tracker type options
- When GitHub selected:
  - Auto-detect repository from git remote (origin)
  - If remote is github.com, extract owner/repo
  - If remote is not github.com, prompt for manual input
  - Do NOT prompt for API token (unlike Linear/YouTrack)
- Generate appropriate config.conf for GitHub

## Files to Modify

### Core Domain Files
- `src/main/scala/works/iterative/cli/domain/IssueTrackerType.scala` - Add GitHub case
- `src/main/scala/works/iterative/cli/domain/ProjectConfiguration.scala` - Add repository field

### Infrastructure Files
- `src/main/scala/works/iterative/cli/infrastructure/ConfigSerializer.scala` - GitHub serialization
- `src/main/scala/works/iterative/cli/infrastructure/TrackerDetector.scala` - GitHub detection

### Command Files
- `.iw/commands/init.scala` - GitHub initialization flow

### Test Files (to create/modify)
- Unit tests for IssueTrackerType with GitHub
- Unit tests for repository parsing from git remotes
- Unit tests for config serialization with GitHub
- E2E test for `iw init --tracker github`

## Testing Strategy

### Unit Tests
1. **IssueTrackerType:**
   - Parse "github" string to GitHub case
   - Serialize GitHub case to "github" string

2. **Repository Parsing:**
   - Parse HTTPS URL: `https://github.com/iterative-works/iw-cli.git`
   - Parse HTTPS URL without .git: `https://github.com/iterative-works/iw-cli`
   - Parse SSH URL: `git@github.com:iterative-works/iw-cli.git`
   - Parse SSH URL without .git: `git@github.com:iterative-works/iw-cli`
   - Reject non-GitHub URLs (gitlab.com, bitbucket.org)
   - Reject invalid formats

3. **Config Serialization:**
   - Round-trip test: GitHub config with repository field
   - Validation: GitHub without repository fails
   - Validation: repository format "owner/repo" required

### Integration Tests
1. **TrackerDetector:**
   - Suggests GitHub for github.com remote
   - Suggests Linear for other remotes (existing behavior)

### E2E Tests
1. **`iw init --tracker github`:**
   - In repo with GitHub remote → creates config with auto-detected repository
   - Verify config.conf contains `tracker.type = github` and `tracker.repository`

## Acceptance Criteria

From the Gherkin scenarios:

```gherkin
Scenario: Initialize new project with GitHub tracker
  Given I am in a git repository with remote "https://github.com/iterative-works/iw-cli.git"
  And the gh CLI is installed and authenticated
  When I run "iw init --tracker github"
  Then the configuration is created at .iw/config.conf
  And the tracker type is set to "github"
  And the repository is auto-detected as "iterative-works/iw-cli"
  And I see a success message confirming GitHub tracker is configured
  And I do not see any instructions about API token setup
```

Additional criteria:
- `iw init` interactive mode lists GitHub as an option
- SSH remote URLs are parsed correctly
- Non-GitHub remotes prompt for manual repository input
- Existing Linear/YouTrack initialization is not affected

## Config Output Example

Expected `.iw/config.conf` for GitHub:

```hocon
tracker {
  type = github
  repository = "iterative-works/iw-cli"
}

project {
  name = iw-cli
}
```

Note: No `apiTokenEnvVar` for GitHub (gh CLI handles auth).

## Notes

- This phase combines Stories 1 and 5 from the analysis (init + auto-detection)
- The combined approach is more efficient since both touch the same code (init command)
- gh CLI is NOT called in this phase - only config is set up
- Actual gh CLI validation happens in Phase 4
