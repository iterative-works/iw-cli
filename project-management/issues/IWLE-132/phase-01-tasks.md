# Phase 1 Implementation Tasks: Initialize project with GitHub tracker

**Issue:** IWLE-132
**Phase:** 1 of 6
**Created:** 2025-12-22
**Status:** Ready to begin

## Overview

This phase adds GitHub as a tracker type with repository auto-detection from git remotes. Each task follows TDD principles with tests written before implementation.

---

## Setup Tasks

- [x] Add `GitHub` constant to `Constants.TrackerTypeValues`
- [x] Add `repository` config key to `Constants.ConfigKeys`

---

## Unit Tests - Domain Layer

### IssueTrackerType Tests

- [ ] Test: Parse "github" string to `IssueTrackerType.GitHub`
- [ ] Test: Serialize `IssueTrackerType.GitHub` to "github" string

### GitRemote Repository Extraction Tests

- [ ] Test: Extract owner/repo from HTTPS URL with .git suffix
  - Input: `https://github.com/iterative-works/iw-cli.git`
  - Expected: `Right("iterative-works/iw-cli")`

- [ ] Test: Extract owner/repo from HTTPS URL without .git suffix
  - Input: `https://github.com/iterative-works/iw-cli`
  - Expected: `Right("iterative-works/iw-cli")`

- [ ] Test: Extract owner/repo from SSH URL with .git suffix
  - Input: `git@github.com:iterative-works/iw-cli.git`
  - Expected: `Right("iterative-works/iw-cli")`

- [ ] Test: Extract owner/repo from SSH URL without .git suffix
  - Input: `git@github.com:iterative-works/iw-cli`
  - Expected: `Right("iterative-works/iw-cli")`

- [ ] Test: Return error for non-GitHub HTTPS URL
  - Input: `https://gitlab.com/user/project.git`
  - Expected: `Left("Not a GitHub URL")`

- [ ] Test: Return error for non-GitHub SSH URL
  - Input: `git@gitlab.com:user/project.git`
  - Expected: `Left("Not a GitHub URL")`

- [ ] Test: Return error for invalid format (missing owner or repo)
  - Input: `https://github.com/single-component`
  - Expected: `Left("Invalid repository format")`

### Config Serialization Tests

- [ ] Test: Serialize GitHub config with repository to HOCON
  - Config: `ProjectConfiguration(GitHub, repository="owner/repo", projectName="test")`
  - Expected HOCON contains: `type = github` and `repository = "owner/repo"`
  - Expected HOCON does NOT contain: `team` field

- [ ] Test: Deserialize HOCON with GitHub tracker to ProjectConfiguration
  - Input HOCON: `tracker { type = github, repository = "owner/repo" }`
  - Expected: `ProjectConfiguration(GitHub, repository=Some("owner/repo"), ...)`

- [ ] Test: Round-trip serialization for GitHub config
  - Serialize config → parse HOCON → deserialize should equal original

- [ ] Test: GitHub config without repository field fails validation
  - Config: `tracker { type = github }` (no repository)
  - Expected: `Left("repository required for GitHub tracker")`

- [ ] Test: GitHub config with invalid repository format fails validation
  - Config: `tracker { type = github, repository = "invalid" }` (missing slash)
  - Expected: `Left("repository must be in owner/repo format")`

- [ ] Test: Linear config still works (regression test)
  - Config: `tracker { type = linear, team = IWLE }`
  - Expected: Parses successfully as Linear

- [ ] Test: YouTrack config still works (regression test)
  - Config: `tracker { type = youtrack, team = TEST }`
  - Expected: Parses successfully as YouTrack

---

## Implementation - Domain Layer

- [ ] Add `GitHub` case to `IssueTrackerType` enum in `Config.scala`

- [ ] Add `repository: Option[String]` field to `ProjectConfiguration` case class

- [ ] Add `repositoryOwnerAndName` method to `GitRemote` for extracting owner/repo
  - Returns `Either[String, String]`
  - Validates GitHub URL
  - Extracts owner/repo from path

- [ ] Update `ConfigSerializer.toHocon` to handle GitHub tracker
  - Use repository field instead of team
  - Do NOT output team field for GitHub

- [ ] Update `ConfigSerializer.fromHocon` to handle GitHub tracker
  - Parse repository field for GitHub
  - Parse team field for Linear/YouTrack
  - Validate repository format (owner/repo)

- [ ] Add validation logic for GitHub config
  - Require repository field when tracker is GitHub
  - Require team field when tracker is Linear/YouTrack
  - Validate repository format matches `^[^/]+/[^/]+$`

---

## Integration Tests - Infrastructure Layer

### TrackerDetector Tests

- [ ] Test: Suggest GitHub for github.com HTTPS remote
  - Remote: `https://github.com/user/repo.git`
  - Expected: `Some(IssueTrackerType.GitHub)`

- [ ] Test: Suggest GitHub for github.com SSH remote
  - Remote: `git@github.com:user/repo.git`
  - Expected: `Some(IssueTrackerType.GitHub)`

- [ ] Test: Linear suggestion replaced by GitHub for github.com
  - Verify TrackerDetector no longer suggests Linear for GitHub remotes

- [ ] Test: YouTrack suggestion unchanged for gitlab.e-bs.cz
  - Remote: `https://gitlab.e-bs.cz/user/repo.git`
  - Expected: `Some(IssueTrackerType.YouTrack)`

---

## Implementation - Infrastructure Layer

- [ ] Update `TrackerDetector.suggestTracker` to return GitHub for github.com
  - Change from: `case Right("github.com") => Some(IssueTrackerType.Linear)`
  - Change to: `case Right("github.com") => Some(IssueTrackerType.GitHub)`

---

## Implementation - Presentation Layer (init command)

- [ ] Update `askForTrackerType()` to include GitHub option
  - Add "3. GitHub" to menu
  - Handle "3" or "github" input

- [ ] Update non-interactive tracker parsing to accept "github"
  - Add GitHub case to trackerArg match in init.scala
  - Map `Constants.TrackerTypeValues.GitHub` to `IssueTrackerType.GitHub`

- [ ] Add repository auto-detection for GitHub tracker
  - When GitHub selected, extract repository from git remote
  - Use `GitRemote.repositoryOwnerAndName` method
  - If extraction fails, prompt user for manual input

- [ ] Add manual repository prompt as fallback
  - Prompt: "Enter GitHub repository (owner/repo format)"
  - Validate format before accepting

- [ ] Update config creation for GitHub tracker
  - Pass repository instead of team
  - Do NOT prompt for team when GitHub selected

- [ ] Update success message for GitHub tracker
  - Do NOT show API token instructions for GitHub
  - Show "GitHub tracker configured" message
  - Show "Ensure gh CLI is installed and authenticated"

---

## E2E Tests

- [ ] Test: `iw init --tracker=github --team=ignored` in GitHub repo
  - Setup: Temp dir with git repo, HTTPS GitHub remote
  - Run: `iw init --tracker=github`
  - Verify: `.iw/config.conf` exists
  - Verify: Config contains `type = github`
  - Verify: Config contains `repository = "owner/repo"`
  - Verify: Config does NOT contain `team` field
  - Verify: Success message shown
  - Verify: No API token instructions shown

- [ ] Test: `iw init --tracker=github` with SSH remote
  - Setup: Temp dir with git repo, SSH GitHub remote
  - Run: `iw init --tracker=github`
  - Verify: Repository correctly extracted from SSH URL
  - Verify: Config contains correct owner/repo

- [ ] Test: `iw init --tracker=github` with non-GitHub remote
  - Setup: Temp dir with git repo, GitLab remote
  - Run: Interactive init selecting GitHub (would require mock input)
  - Note: This scenario may be challenging to test non-interactively
  - Alternative: Test manual flag `--repository=owner/repo`

- [ ] Test: Interactive mode suggests GitHub for github.com remote
  - Setup: Temp dir with GitHub remote
  - Run: `iw init` (interactive, would need input mocking)
  - Note: May require BATS input mocking or skip for now

- [ ] Test: Existing Linear config not affected by changes
  - Setup: Temp dir with git repo
  - Run: `iw init --tracker=linear --team=IWLE`
  - Verify: Config contains `type = linear` and `team = IWLE`
  - Verify: No regression in Linear functionality

- [ ] Test: Existing YouTrack config not affected by changes
  - Setup: Temp dir with git repo  
  - Run: `iw init --tracker=youtrack --team=TEST`
  - Verify: Config contains `type = youtrack` and `team = TEST`
  - Verify: No regression in YouTrack functionality

---

## Verification Checklist

Before marking phase complete:

- [ ] All unit tests pass (`./iw test unit`)
- [ ] All E2E tests pass (`./iw test e2e`)
- [ ] Config.scala has GitHub enum case
- [ ] GitRemote can extract owner/repo from GitHub URLs
- [ ] ConfigSerializer handles GitHub serialization/deserialization
- [ ] TrackerDetector suggests GitHub for github.com
- [ ] init.scala accepts `--tracker=github`
- [ ] Repository auto-detected from git remote
- [ ] No API token instructions shown for GitHub
- [ ] Linear and YouTrack still work (regression tests pass)
- [ ] Code follows existing patterns and style
- [ ] All pattern matches handle GitHub case

---

## Implementation Notes

**Test-First Approach:**
1. Write the failing test
2. Run test to confirm it fails
3. Write minimal code to pass test
4. Run test to confirm success
5. Refactor if needed

**Key Files:**
- `.iw/core/Config.scala` - Domain model
- `.iw/core/Constants.scala` - String constants
- `.iw/core/test/ConfigTest.scala` - Unit tests
- `.iw/commands/init.scala` - Init command
- `.iw/test/init.bats` - E2E tests

**Pattern Matching:**
Ensure all pattern matches on `IssueTrackerType` include the `GitHub` case to avoid incomplete match warnings.

**Validation Strategy:**
- Repository format: Must match `owner/repo` (single slash, non-empty parts)
- GitHub URLs: Must be github.com domain
- Config completeness: GitHub requires repository, Linear/YouTrack require team

---

## Acceptance Criteria (from phase-01-context.md)

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

Additional:
- `iw init` interactive mode lists GitHub as an option
- SSH remote URLs are parsed correctly  
- Non-GitHub remotes prompt for manual repository input (or fail gracefully)
- Existing Linear/YouTrack initialization is not affected

---

**Total Tasks:** 52
**Estimated Time:** 6-8 hours
**Next Phase:** Phase 2 - Repository auto-detection from git remote

---

**Phase Status:** Complete
**Completed:** 2025-12-22
**PR:** https://github.com/iterative-works/iw-cli/pull/36
