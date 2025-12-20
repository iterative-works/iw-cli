# Phase 6 Tasks: Show git status and PR links

**Issue:** IWLE-100
**Phase:** 6 of 7
**Story:** Story 5 - Show git status and PR links
**Estimated Effort:** 6-8 hours
**Created:** 2025-12-20

---

## Task Breakdown

### Setup (Est: 0.5h)

- [ ] **[setup]** Review Phase 6 context and acceptance criteria
- [ ] **[setup]** Verify existing ServerState, DashboardService, and WorktreeListView implementations
- [ ] **[setup]** Confirm git available in PATH and test basic git commands

---

## Step 1: Domain Models (Est: 1h)

### Tests

- [x] **[test]** Write GitStatusTest: statusIndicator returns "✓ clean" when isClean=true
- [x] **[test]** Write GitStatusTest: statusIndicator returns "⚠ uncommitted" when isClean=false
- [x] **[test]** Write GitStatusTest: statusCssClass returns "git-clean" and "git-dirty" correctly
- [x] **[test]** Write PullRequestDataTest: stateBadgeClass returns correct classes for Open/Merged/Closed
- [x] **[test]** Write PullRequestDataTest: stateBadgeText returns "Open", "Merged", "Closed"
- [x] **[test]** Write CachedPRTest: isValid returns true when within 2-minute TTL
- [x] **[test]** Write CachedPRTest: isValid returns false when TTL expired (>2 minutes)
- [x] **[test]** Write CachedPRTest: age calculates duration correctly

### Implementation

- [x] **[impl]** Create `.iw/core/GitStatus.scala` case class with branchName, isClean
- [x] **[impl]** Implement GitStatus.statusIndicator computed property
- [x] **[impl]** Implement GitStatus.statusCssClass computed property
- [x] **[impl]** Create `.iw/core/PullRequestData.scala` with PRState enum
- [x] **[impl]** Implement PullRequestData case class with url, state, number, title
- [x] **[impl]** Implement PullRequestData.stateBadgeClass and stateBadgeText methods
- [x] **[impl]** Create `.iw/core/CachedPR.scala` case class with pr, fetchedAt
- [x] **[impl]** Implement CachedPR.isValid with 2-minute TTL check
- [x] **[impl]** Implement CachedPR.age duration calculation
- [x] **[impl]** Run unit tests and verify all pass
- [x] **[impl]** Commit: "feat(IWLE-100): Add GitStatus and PullRequestData domain models"

---

## Step 2: Command Execution Infrastructure (Est: 1h)

### Tests

- [ ] **[test]** Write CommandRunnerTest: execute returns stdout when command succeeds (echo test)
- [ ] **[test]** Write CommandRunnerTest: execute returns Left(error) when command fails (false)
- [ ] **[test]** Write CommandRunnerTest: execute works with workingDir parameter
- [ ] **[test]** Write CommandRunnerTest: isCommandAvailable returns true for "echo"
- [ ] **[test]** Write CommandRunnerTest: isCommandAvailable returns false for non-existent command

### Implementation

- [ ] **[impl]** Create `.iw/core/CommandRunner.scala` object
- [ ] **[impl]** Implement execute(command, args, workingDir) using scala.sys.process
- [ ] **[impl]** Add error handling with Either (catch RuntimeException, return Left)
- [ ] **[impl]** Implement isCommandAvailable(command) using "which" check
- [ ] **[impl]** Run CommandRunner tests and verify all pass
- [ ] **[impl]** Commit: "feat(IWLE-100): Add CommandRunner for shell command execution"

---

## Step 3: Git Status Service (Est: 1.5h)

### Tests

- [ ] **[test]** Write GitStatusServiceTest: getGitStatus returns status when git commands succeed
- [ ] **[test]** Write GitStatusServiceTest: getGitStatus handles dirty working tree (modified file)
- [ ] **[test]** Write GitStatusServiceTest: getGitStatus returns error when git command fails
- [ ] **[test]** Write GitStatusServiceTest: parseBranchName extracts branch from "main\n"
- [ ] **[test]** Write GitStatusServiceTest: parseBranchName handles "feature-branch" without newline
- [ ] **[test]** Write GitStatusServiceTest: isWorkingTreeClean returns true for empty output
- [ ] **[test]** Write GitStatusServiceTest: isWorkingTreeClean returns false for " M file.txt"
- [ ] **[test]** Write GitStatusServiceTest: isWorkingTreeClean returns false for "?? new-file.txt"

### Implementation

- [ ] **[impl]** Create `.iw/core/GitStatusService.scala` object
- [ ] **[impl]** Implement getGitStatus with injected execCommand function
- [ ] **[impl]** Call execCommand for "git rev-parse --abbrev-ref HEAD" to get branch name
- [ ] **[impl]** Call execCommand for "git status --porcelain" to get status
- [ ] **[impl]** Implement parseBranchName(output) - trim and extract branch
- [ ] **[impl]** Implement isWorkingTreeClean(output) - return true if empty
- [ ] **[impl]** Combine results into GitStatus case class
- [ ] **[impl]** Run GitStatusService tests and verify all pass
- [ ] **[impl]** Commit: "feat(IWLE-100): Add GitStatusService for repository status detection"

---

## Step 4: PR Cache Service (Est: 2h)

### Tests

- [ ] **[test]** Write PullRequestCacheServiceTest: fetchPR uses cache when valid (<2 min)
- [ ] **[test]** Write PullRequestCacheServiceTest: fetchPR re-fetches when cache expired (>2 min)
- [ ] **[test]** Write PullRequestCacheServiceTest: fetchPR returns None when no PR tool available
- [ ] **[test]** Write PullRequestCacheServiceTest: fetchPR returns None when PR not found (404)
- [ ] **[test]** Write PullRequestCacheServiceTest: parseGitHubPR extracts url, state, number, title
- [ ] **[test]** Write PullRequestCacheServiceTest: parseGitHubPR handles OPEN, MERGED, CLOSED states
- [ ] **[test]** Write PullRequestCacheServiceTest: parseGitLabPR extracts mr data from JSON
- [ ] **[test]** Write PullRequestCacheServiceTest: parseGitLabPR handles opened, merged, closed states
- [ ] **[test]** Write PullRequestCacheServiceTest: detectPRTool returns Some("gh") when gh available
- [ ] **[test]** Write PullRequestCacheServiceTest: detectPRTool returns Some("glab") when only glab
- [ ] **[test]** Write PullRequestCacheServiceTest: detectPRTool returns None when neither available

### Implementation

- [ ] **[impl]** Create `.iw/core/PullRequestCacheService.scala` object
- [ ] **[impl]** Implement fetchPR with cache check using CachedPR.isValid
- [ ] **[impl]** Return cached PR if valid, otherwise proceed to fetch
- [ ] **[impl]** Implement detectPRTool checking "gh" first, then "glab"
- [ ] **[impl]** Return None if no PR tool available
- [ ] **[impl]** Execute "gh pr view --json url,state,number,title" for GitHub
- [ ] **[impl]** Execute "glab mr view --output json" for GitLab
- [ ] **[impl]** Implement parseGitHubPR JSON parsing with upickle
- [ ] **[impl]** Map GitHub states: OPEN→Open, MERGED→Merged, CLOSED→Closed
- [ ] **[impl]** Implement parseGitLabPR JSON parsing
- [ ] **[impl]** Map GitLab states: opened→Open, merged→Merged, closed→Closed
- [ ] **[impl]** Handle command failures gracefully (return None, not error)
- [ ] **[impl]** Run PullRequestCacheService tests and verify all pass
- [ ] **[impl]** Commit: "feat(IWLE-100): Add PullRequestCacheService with GitHub/GitLab support"

---

## Step 5: State Repository Extension (Est: 0.5h)

### Tests

- [ ] **[test]** Write StateRepositoryTest: serialize ServerState with prCache field
- [ ] **[test]** Write StateRepositoryTest: deserialize ServerState with prCache correctly
- [ ] **[test]** Write StateRepositoryTest: prCache persists PullRequestData url, state, number
- [ ] **[test]** Write StateRepositoryTest: prCache persists fetchedAt timestamp

### Implementation

- [ ] **[impl]** Extend ServerState case class with prCache: Map[String, CachedPR] = Map.empty
- [ ] **[impl]** Add upickle ReadWriter for PRState enum in StateRepository
- [ ] **[impl]** Add upickle ReadWriter for PullRequestData in StateRepository
- [ ] **[impl]** Add upickle ReadWriter for CachedPR in StateRepository
- [ ] **[impl]** Add upickle ReadWriter for GitStatus in StateRepository (for future use)
- [ ] **[impl]** Run StateRepository integration tests and verify serialization works
- [ ] **[impl]** Commit: "feat(IWLE-100): Extend ServerState with PR cache"

---

## Step 6: Dashboard Integration (Est: 1.5h)

### Tests

- [ ] **[test]** Write DashboardServiceTest: fetchGitStatusForWorktree returns GitStatus with branch
- [ ] **[test]** Write DashboardServiceTest: fetchGitStatusForWorktree handles git errors gracefully
- [ ] **[test]** Write DashboardServiceTest: fetchPRForWorktree uses cache when valid
- [ ] **[test]** Write DashboardServiceTest: fetchPRForWorktree re-fetches when cache expired
- [ ] **[test]** Write DashboardServiceTest: dashboard passes git status to WorktreeListView
- [ ] **[test]** Write DashboardServiceTest: dashboard passes PR data to WorktreeListView

### Implementation

- [ ] **[impl]** Modify DashboardService.renderDashboard to accept prCache parameter
- [ ] **[impl]** Add fetchGitStatusForWorktree helper method
- [ ] **[impl]** Call GitStatusService.getGitStatus with execCommand wrapper
- [ ] **[impl]** Add fetchPRForWorktree helper method
- [ ] **[impl]** Call PullRequestCacheService.fetchPR with execCommand and detectTool wrappers
- [ ] **[impl]** Pass Optional[GitStatus] and Optional[PullRequestData] to WorktreeListView
- [ ] **[impl]** Update worktreesWithData to include git status and PR data tuples
- [ ] **[impl]** Run DashboardService integration tests and verify all pass
- [ ] **[impl]** Commit: "feat(IWLE-100): Integrate git status and PR data in dashboard"

---

## Step 7: Presentation Layer Enhancement (Est: 1h)

### Tests

- [ ] **[test]** Write WorktreeListViewTest: renderWorktreeCard includes git status when present
- [ ] **[test]** Write WorktreeListViewTest: renderWorktreeCard shows "✓ clean" for clean status
- [ ] **[test]** Write WorktreeListViewTest: renderWorktreeCard shows "⚠ uncommitted" for dirty
- [ ] **[test]** Write WorktreeListViewTest: renderWorktreeCard includes PR link when present
- [ ] **[test]** Write WorktreeListViewTest: PR link has correct state badge class (pr-open, etc)
- [ ] **[test]** Write WorktreeListViewTest: git status omitted when None

### Implementation

- [ ] **[impl]** Modify WorktreeListView.renderWorktreeCard signature to include gitStatus, prData
- [ ] **[impl]** Add git status section with branch name display
- [ ] **[impl]** Add git status indicator with statusIndicator and statusCssClass
- [ ] **[impl]** Add PR link section when prData is Some
- [ ] **[impl]** Render "View PR ↗" button with href to pr.url, target="_blank"
- [ ] **[impl]** Add PR state badge with stateBadgeClass and stateBadgeText
- [ ] **[impl]** Add inline CSS for .git-status, .git-branch, .git-indicator
- [ ] **[impl]** Add inline CSS for .git-clean (green) and .git-dirty (yellow)
- [ ] **[impl]** Add inline CSS for .pr-link, .pr-button, .pr-badge
- [ ] **[impl]** Add inline CSS for .pr-open (blue), .pr-merged (purple), .pr-closed (gray)
- [ ] **[impl]** Run WorktreeListView tests and verify rendering correct
- [ ] **[impl]** Commit: "feat(IWLE-100): Enhance worktree cards with git status and PR links"

---

## Step 8: Error Handling & Edge Cases (Est: 0.5h)

### Manual Testing

- [ ] **[test]** Test missing git repository: worktree without .git directory
- [ ] **[test]** Verify graceful fallback: no git status shown, no error
- [ ] **[test]** Test gh/glab not installed: verify no PR link shown
- [ ] **[test]** Test PR not found for branch (404 from gh pr view)
- [ ] **[test]** Verify handled as "no PR", not error
- [ ] **[test]** Test git command timeout scenario

### Implementation

- [ ] **[impl]** Fix any issues found during edge case testing
- [ ] **[impl]** Commit fixes if needed: "fix(IWLE-100): Handle edge cases for git status and PR detection"

---

## Step 9: Manual E2E Verification (Est: 1h)

### Dashboard Scenarios

- [ ] **[test]** Scenario 1: Clean working directory - verify "✓ clean" indicator in green
- [ ] **[test]** Scenario 2: Uncommitted changes - verify "⚠ uncommitted" indicator in yellow
- [ ] **[test]** Scenario 3: PR links displayed - verify "View PR ↗" link opens GitHub in new tab
- [ ] **[test]** Scenario 4: PR cache validity - load dashboard, wait 1 min, verify cache used
- [ ] **[test]** Scenario 4 continued: Wait 3 minutes total, verify PR re-fetched
- [ ] **[test]** Scenario 5: Missing git repo - verify no git status, dashboard renders correctly
- [ ] **[test]** Scenario 6: No PR CLI tools - verify no PR link shown, no error

### State Persistence

- [ ] **[test]** Verify state.json contains prCache with PullRequestData
- [ ] **[test]** Verify fetchedAt timestamp stored correctly
- [ ] **[test]** Restart server, verify PR cache loaded from state.json

### Cross-Browser Testing

- [ ] **[test]** Test dashboard in Chrome/Chromium
- [ ] **[test]** Test dashboard on mobile browser (if accessible)
- [ ] **[test]** Verify PR state badges display correctly (Open=blue, Merged=purple, Closed=gray)

### Integration

- [ ] **[impl]** Fix any issues found during E2E testing
- [ ] **[impl]** Commit fixes if needed: "fix(IWLE-100): E2E verification fixes for Phase 6"

---

## Step 10: Documentation (Est: 0.5h)

### Implementation

- [ ] **[impl]** Update implementation-log.md with Phase 6 summary
- [ ] **[impl]** Document git status detection approach (git status --porcelain)
- [ ] **[impl]** Document PR cache TTL (2 minutes) and rationale
- [ ] **[impl]** Add comments for PR JSON parsing logic (GitHub vs GitLab)
- [ ] **[impl]** Document tool detection strategy (gh first, then glab)
- [ ] **[impl]** Commit: "docs(IWLE-100): Document Phase 6 implementation"

---

## Acceptance Criteria Checklist

### Functional Requirements

- [ ] Git branch name displayed for each worktree
- [ ] Clean vs dirty status clearly indicated (✓ clean / ⚠ uncommitted)
- [ ] Clean status styled with green background
- [ ] Dirty status styled with yellow background
- [ ] PR links appear when PRs exist for current branch
- [ ] PR state (open/merged/closed) shown visually with badges
- [ ] Open PRs show blue badge, merged show purple, closed show gray
- [ ] PR link opens in new browser tab (target="_blank")
- [ ] PR data cached with 2-minute TTL
- [ ] Cache invalidated after 2 minutes, PR re-fetched
- [ ] GitHub PRs detected via `gh pr view` command
- [ ] GitLab PRs detected via `glab mr view` command
- [ ] Non-existent git repos show no git status (no error)
- [ ] Missing `gh`/`glab` tools handled gracefully (no PR link shown)

### Non-Functional Requirements

- [ ] All unit tests passing (GitStatus, PullRequestData, CachedPR, services)
- [ ] All integration tests passing (StateRepository with PR cache)
- [ ] Manual scenarios verified (clean/dirty, PR links, cache TTL)
- [ ] Git status detection completes within 100ms per worktree
- [ ] PR detection completes within 2 seconds per worktree (network call)
- [ ] Command execution errors don't crash dashboard
- [ ] Code follows FCIS pattern (pure domain/application, effects in infrastructure)
- [ ] No new compilation warnings

### Quality Checks

- [ ] Code review self-check: Are command execution functions injected?
- [ ] Code review self-check: Does PR cache TTL work correctly?
- [ ] Code review self-check: Are edge cases handled (no git, no tools, no PR)?

---

## Notes

- **TDD:** Write tests before implementation for each component
- **FCIS:** Keep domain and application layers pure, effects in infrastructure
- **Error handling:** Graceful degradation when git or PR tools unavailable
- **PR cache:** 2-minute TTL (shorter than issue cache, PRs change more frequently)
- **Tool detection:** Check `gh` first, then `glab`, gracefully fallback if neither
- **Testing:** Focus on unit tests for parsing logic, integration tests for command execution
- **Commit frequency:** Commit after each major step (domain models, services, integration)

---

**Phase Status:** Ready for Implementation

**Estimated Total Time:** 6-8 hours

**Next Steps:**
1. Begin with Step 1 (Domain Models)
2. Follow TDD: test → implementation → commit
3. Run all tests continuously during development
4. Verify acceptance criteria before marking phase complete
