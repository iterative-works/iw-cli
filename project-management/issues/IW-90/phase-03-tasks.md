# Phase 03 Tasks: Configure GitLab tracker during iw init

**Issue:** IW-90
**Phase:** 3 of 7
**Status:** Complete

## Task Breakdown

This phase enables GitLab configuration during `iw init`. Tasks follow TDD with tests written before implementation.

---

## Setup Tasks

- [ ] Review existing GitHub configuration implementation in init.scala (lines 85-128)
- [ ] Review existing TrackerDetector pattern for GitHub detection (Config.scala lines 103-108)
- [ ] Review existing ConfigSerializer GitLab handling (Config.scala lines 116, 123-126, 149, 154-169)

---

## Tests: GitRemote GitLab Detection

### Test Task 1: GitRemote recognizes gitlab.com (15-20 min)

- [ ] Write test: GitRemote.host returns Right("gitlab.com") for HTTPS gitlab.com URL
- [ ] Write test: GitRemote.host returns Right("gitlab.com") for SSH gitlab.com URL
- [ ] Verify tests fail (GitRemote already handles gitlab.com via generic URL parsing)
- [ ] Verify tests pass (no implementation needed - GitRemote is generic)

### Test Task 2: GitRemote recognizes self-hosted GitLab (15-20 min)

- [ ] Write test: GitRemote.host returns Right("gitlab.company.com") for self-hosted HTTPS URL
- [ ] Write test: GitRemote.host returns Right("gitlab.example.org") for self-hosted SSH URL
- [ ] Run tests to verify they pass (GitRemote is already generic)

### Test Task 3: GitRemote extracts GitLab repository paths (20-25 min)

- [x] Write test: Extract "owner/repo" from gitlab.com HTTPS URL
- [x] Write test: Extract "owner/repo" from gitlab.com SSH URL
- [x] Write test: Extract "group/subgroup/project" from nested GitLab path (HTTPS)
- [x] Write test: Extract "group/subgroup/project" from nested GitLab path (SSH)
- [x] Write test: Return error for non-GitLab URL when extracting GitLab repo
- [x] Run tests - expect failures (repositoryOwnerAndName currently GitHub-only)

---

## Implementation: GitRemote GitLab Support

### Implementation Task 1: Add GitLab repository extraction (25-30 min)

- [x] Rename GitRemote.repositoryOwnerAndName to extractGitHubRepository (preserve GitHub-only logic)
- [x] Create new GitRemote.extractGitLabRepository function
  - [x] Verify host is gitlab.com or contains "gitlab"
  - [x] Extract repository path (supports nested groups: group/subgroup/project)
  - [x] Clean up .git suffix and trailing slashes
  - [x] Return Either[String, String]
- [x] Run GitRemote tests to verify they pass
- [x] Commit: "feat(core): Add GitLab repository extraction to GitRemote"

---

## Tests: TrackerDetector GitLab Detection

### Test Task 4: TrackerDetector suggests GitLab (15-20 min)

- [x] Write test: TrackerDetector.suggestTracker returns Some(GitLab) for gitlab.com HTTPS remote
- [x] Write test: TrackerDetector.suggestTracker returns Some(GitLab) for gitlab.com SSH remote
- [x] Write test: TrackerDetector.suggestTracker returns Some(GitLab) for gitlab.company.com remote
- [x] Write test: TrackerDetector prioritizes GitHub over GitLab when multiple remotes present
- [x] Run tests - expect failures (TrackerDetector doesn't check for GitLab yet)

---

## Implementation: TrackerDetector GitLab Support

### Implementation Task 2: Update TrackerDetector for GitLab (15-20 min)

- [x] Add GitLab detection to TrackerDetector.suggestTracker
  - [x] Check for host == "gitlab.com" → return Some(IssueTrackerType.GitLab)
  - [x] Check for host.contains("gitlab") → return Some(IssueTrackerType.GitLab)
  - [x] Ensure GitHub detection takes priority (remains first)
- [x] Run TrackerDetector tests to verify they pass
- [x] Commit: "feat(core): Add GitLab detection to TrackerDetector"

---

## Tests: ConfigSerializer GitLab Serialization

### Test Task 5: ConfigSerializer serializes GitLab config (20-25 min)

- [x] Write test: Serialize GitLab config without baseUrl (gitlab.com assumed)
- [x] Write test: Serialize GitLab config with baseUrl (self-hosted instance)
- [x] Write test: GitLab config includes repository field
- [x] Write test: GitLab config includes teamPrefix field
- [x] Write test: GitLab config omits team field
- [x] Run tests - expect some to fail (baseUrl handling may need updates)

### Test Task 6: ConfigSerializer deserializes GitLab config (20-25 min)

- [x] Write test: Deserialize GitLab config with repository and teamPrefix
- [x] Write test: Deserialize GitLab config with optional baseUrl
- [x] Write test: Deserialize GitLab config validates repository format (must contain /)
- [x] Write test: Deserialize GitLab config validates teamPrefix (2-10 uppercase letters)
- [x] Write test: Error when GitLab config missing repository
- [x] Write test: Error when GitLab config missing teamPrefix
- [x] Run tests - expect failures (ConfigSerializer doesn't handle GitLab baseUrl yet)

### Test Task 7: ConfigSerializer round-trip for GitLab (15-20 min)

- [x] Write test: Round-trip GitLab config without baseUrl
- [x] Write test: Round-trip GitLab config with baseUrl
- [x] Write test: Round-trip GitLab config with nested group repository
- [x] Run tests - expect failures until implementation complete

---

## Implementation: ConfigSerializer GitLab Support

### Implementation Task 3: Update ConfigSerializer.toHocon for GitLab baseUrl (20-25 min)

- [x] Modify toHocon to handle optional baseUrl for GitLab (similar to youtrackBaseUrl)
- [x] Add baseUrl line to GitLab tracker section when present
- [x] Ensure baseUrl omitted when None (defaults to gitlab.com)
- [x] Run ConfigSerializer serialization tests to verify they pass
- [x] Commit: "feat(core): Add baseUrl serialization for GitLab tracker"

### Implementation Task 4: Update ConfigSerializer.fromHocon for GitLab baseUrl (20-25 min)

- [x] Read optional tracker.baseUrl for GitLab (same key as YouTrack)
- [x] Store baseUrl in ProjectConfiguration.youtrackBaseUrl field (reuse for GitLab)
- [x] Ensure baseUrl is None when not present in config
- [x] Run ConfigSerializer deserialization tests to verify they pass
- [x] Run all ConfigSerializer round-trip tests to verify they pass
- [x] Commit: "feat(core): Add baseUrl parsing for GitLab tracker"

---

## Tests: init.scala GitLab Configuration

### Test Task 8: E2E test for GitLab auto-detection (manual test - 10-15 min)

NOTE: This is a manual E2E test (not automated BATS test)

- [ ] Create test git repository with gitlab.com remote
- [ ] Run `./iw init` in test repository
- [ ] Verify init suggests "gitlab" tracker
- [ ] Accept suggestion and verify prompts for repository and teamPrefix
- [ ] Verify config.conf contains tracker.type = gitlab
- [ ] Clean up test repository

### Test Task 9: E2E test for manual GitLab selection (manual test - 10-15 min)

NOTE: This is a manual E2E test (not automated BATS test)

- [ ] Create test git repository with non-GitLab remote
- [ ] Run `./iw init` and manually select "gitlab" option
- [ ] Enter repository as "owner/project"
- [ ] Enter teamPrefix
- [ ] Verify config.conf contains correct GitLab configuration
- [ ] Clean up test repository

---

## Implementation: init.scala GitLab Support

### Implementation Task 5: Add GitLab to init.scala tracker menu (15-20 min)

- [x] Add "4. GitLab" to askForTrackerType() menu display (after GitHub)
- [x] Handle "4" and "gitlab" choices → return IssueTrackerType.GitLab
- [x] Add GitLab to trackerArg parsing in init() (line 56-63)
- [x] Update error message to include gitlab option
- [x] Update interactive tracker name display to handle GitLab (lines 71-74)
- [x] Test locally: verify menu displays GitLab option
- [x] Commit: "feat(init): Add GitLab to tracker type menu"

### Implementation Task 6: Implement GitLab repository and baseUrl prompts (25-30 min)

- [x] Add GitLab case to tracker-specific configuration block (after GitHub case, line 129)
- [x] Implement GitLab repository extraction/prompting:
  - [x] Try GitRemote.extractGitLabRepository from git remote
  - [x] Auto-detect and display if successful
  - [x] Prompt for repository if auto-detection fails
- [x] Implement GitLab teamPrefix prompting (same as GitHub):
  - [x] Check for --team-prefix arg
  - [x] Suggest prefix from repository name
  - [x] Validate prefix format
- [x] Implement GitLab baseUrl prompting:
  - [x] Detect if remote is NOT gitlab.com (self-hosted)
  - [x] Prompt for baseUrl if self-hosted
  - [x] Set baseUrl to None if gitlab.com (uses default)
- [x] Return (team="", repository=Some(repo), teamPrefix=Some(prefix), baseUrl=Option[String])
- [x] Test locally with gitlab.com remote
- [x] Test locally with self-hosted GitLab remote (mock)
- [x] Commit: "feat(init): Add GitLab repository and baseUrl configuration"

### Implementation Task 7: Add GitLab next steps display (10-15 min)

- [x] Add IssueTrackerType.GitLab case to "Next steps" section (line 167)
- [x] Display glab CLI installation instructions
- [x] Display glab authentication command: glab auth login
- [x] Include link to glab CLI: https://gitlab.com/gitlab-org/cli
- [x] Test locally: verify next steps display after GitLab config
- [x] Commit: "feat(init): Add GitLab next steps with glab instructions"

---

## Integration & Validation

### Integration Task 1: Run full unit test suite (10-15 min)

- [x] Run `./iw test unit` to execute all Scala/munit tests
- [x] Verify all GitRemote tests pass
- [x] Verify all TrackerDetector tests pass
- [x] Verify all ConfigSerializer tests pass
- [x] Fix any test failures
- [x] Commit any fixes

### Integration Task 2: Manual E2E validation - gitlab.com (15-20 min)

- [ ] Create fresh test repository with gitlab.com remote
- [ ] Run `./iw init` and accept GitLab suggestion
- [ ] Verify repository auto-detected correctly
- [ ] Enter teamPrefix and verify validation
- [ ] Check .iw/config.conf contents:
  - [ ] tracker.type = gitlab
  - [ ] tracker.repository = "owner/project"
  - [ ] tracker.teamPrefix = "VALID"
  - [ ] No baseUrl line (defaults to gitlab.com)
- [ ] Verify "Next steps" mentions glab
- [ ] Clean up test repository

### Integration Task 3: Manual E2E validation - self-hosted GitLab (15-20 min)

- [ ] Create test repository with self-hosted GitLab remote (e.g., gitlab.company.com)
- [ ] Run `./iw init` and verify GitLab suggested
- [ ] Verify repository extraction works for self-hosted
- [ ] Enter teamPrefix
- [ ] Verify prompted for baseUrl
- [ ] Enter baseUrl: https://gitlab.company.com
- [ ] Check .iw/config.conf contents:
  - [ ] tracker.type = gitlab
  - [ ] tracker.repository = "owner/project"
  - [ ] tracker.teamPrefix = "VALID"
  - [ ] tracker.baseUrl = "https://gitlab.company.com"
- [ ] Clean up test repository

### Integration Task 4: Manual E2E validation - nested groups (15-20 min)

- [ ] Create test repository with nested GitLab path (group/subgroup/project)
- [ ] Run `./iw init`
- [ ] Verify nested repository path extracted correctly
- [ ] Complete configuration
- [ ] Check config.conf has correct nested path: "group/subgroup/project"
- [ ] Clean up test repository

### Integration Task 5: Verify backward compatibility (10-15 min)

- [ ] Create config.conf with Linear tracker
- [ ] Verify Linear config still loads correctly
- [ ] Create config.conf with YouTrack tracker
- [ ] Verify YouTrack config still loads correctly
- [ ] Create config.conf with GitHub tracker
- [ ] Verify GitHub config still loads correctly
- [ ] Confirm no regressions

---

## Documentation

### Documentation Task 1: Update phase context (5-10 min)

- [ ] Mark acceptance criteria as complete in phase-03-context.md
- [ ] Add any implementation notes or discoveries
- [ ] Document any deviations from original plan

---

## Definition of Done

- [ ] All unit tests pass (GitRemote, TrackerDetector, ConfigSerializer)
- [ ] GitLab detection works for gitlab.com and self-hosted instances
- [ ] GitLab repository extraction supports nested groups
- [ ] ConfigSerializer correctly serializes/deserializes GitLab config
- [ ] Optional baseUrl field handled correctly (omitted for gitlab.com)
- [ ] init.scala offers GitLab as tracker option
- [ ] init.scala auto-detects GitLab from git remote
- [ ] init.scala prompts for baseUrl when self-hosted GitLab detected
- [ ] Next steps display glab authentication instructions
- [ ] Backward compatibility verified (Linear, YouTrack, GitHub still work)
- [ ] Manual E2E validation complete for all scenarios
- [ ] Code committed with clear commit messages following TDD

---

## Estimated Time

**Total estimated time:** 5-7 hours

Breakdown:
- Setup & Review: 30-45 min
- Tests (Tasks 1-9): 2-3 hours
- Implementation (Tasks 1-7): 2-2.5 hours
- Integration & Validation (Tasks 1-5): 1-1.5 hours
- Documentation: 10-15 min

---

## Notes

- GitRemote.host is already generic and handles any git URL format (SSH/HTTPS)
- repositoryOwnerAndName is GitHub-specific; create separate extractGitLabRepository
- ConfigSerializer already has GitLab enum case; needs baseUrl handling
- TeamPrefix validation already exists (reuse for GitLab)
- Self-hosted detection: if host != "gitlab.com" → prompt for baseUrl
- baseUrl reuses youtrackBaseUrl field in ProjectConfiguration (both need custom base URLs)
