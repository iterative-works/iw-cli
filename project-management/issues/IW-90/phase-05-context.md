# Phase 5 Context: Create GitLab issues via glab CLI

**Issue:** IW-90
**Phase:** 5 of 7
**Story:** Create GitLab issues via glab CLI

---

## Goals

This phase adds issue creation capability to the GitLab tracker integration:

1. Add `createIssue` function to `GitLabClient` following the GitHubClient pattern
2. Add response parsing for `glab issue create` output
3. Handle label-based issue type mapping (bug/feature) with fallback
4. Enable `iw feedback` command to work with GitLab-configured projects

## Scope

### In Scope

- `GitLabClient.buildCreateIssueCommand()` - Command building for glab issue create
- `GitLabClient.buildCreateIssueCommandWithoutLabel()` - Fallback without labels
- `GitLabClient.parseCreateIssueResponse()` - Parse URL from glab output
- `GitLabClient.createIssue()` - Main entry point with label fallback logic
- `GitLabClient.isLabelError()` - Detect label-related errors for retry
- Update `feedback.scala` to route to GitLabClient when tracker is GitLab
- Unit tests for all new functions
- E2E test for GitLab issue creation

### Out of Scope

- GitLab-specific fields (milestones, epics, weight) - deferred to future
- Multi-label support - single label per issue type is sufficient
- Project label management - rely on existing labels or create without

## Dependencies

### From Previous Phases

- **Phase 1**: `GitLabClient` module exists with `validateGlabPrerequisites`
- **Phase 2**: Error handling functions (`formatGlabNotInstalledError`, `formatGlabNotAuthenticatedError`)
- **Phase 3**: GitLab tracker configuration in `.iw/config.conf` with `repository` field

### External

- `glab` CLI installed and authenticated
- `FeedbackParser` module for parsing issue type (already exists)
- `CreatedIssue` domain model (already exists)

## Technical Approach

### glab CLI Command Format

```bash
# Create issue with label
glab issue create --repo "owner/project" --title "Bug title" --description "Details" --label "bug"

# Create issue without label (fallback)
glab issue create --repo "owner/project" --title "Bug title" --description "Details"
```

### Expected glab Output

glab issue create outputs the issue URL on success:
```
https://gitlab.com/owner/project/-/issues/123
```

For self-hosted:
```
https://gitlab.company.com/owner/project/-/issues/123
```

### Label Mapping

| Issue Type | GitLab Label |
|------------|--------------|
| Bug | `bug` |
| Feature | `feature` |

If label assignment fails (label doesn't exist), retry without label.

### Pattern to Follow

Follow `GitHubClient.createIssue` exactly:
1. Validate prerequisites (reuse existing `validateGlabPrerequisites`)
2. Build command with label
3. Execute command
4. If label error, retry without label
5. Parse response URL
6. Return `CreatedIssue`

## Files to Modify

| File | Changes |
|------|---------|
| `.iw/core/GitLabClient.scala` | Add `buildCreateIssueCommand`, `buildCreateIssueCommandWithoutLabel`, `parseCreateIssueResponse`, `isLabelError`, `createIssue` |
| `.iw/core/test/GitLabClientTest.scala` | Add unit tests for all new functions |
| `.iw/commands/feedback.scala` | Add GitLab tracker routing (read config, call GitLabClient) |
| `.iw/test/feedback.bats` | Add E2E test for GitLab issue creation |

## Testing Strategy

### Unit Tests (GitLabClientTest.scala)

1. **Command building with label**
   - Bug type → `--label bug`
   - Feature type → `--label feature`
   - Includes `--repo`, `--title`, `--description`

2. **Command building without label**
   - Same as above but no `--label` flag

3. **Response parsing**
   - gitlab.com URL → extracts issue number
   - Self-hosted URL → extracts issue number
   - Invalid URL → error
   - Empty response → error

4. **Label error detection**
   - "label 'bug' not found" → true
   - "label does not exist" → true
   - Other errors → false

5. **Integration test (mocked)**
   - Success with label
   - Label error → retry without label → success
   - Other error → failure
   - Prerequisite failure → appropriate error

### E2E Tests (feedback.bats)

1. **GitLab issue creation**
   - Configure GitLab tracker
   - Run `iw feedback "Test issue" --type bug`
   - Verify output contains GitLab issue URL
   - (Skip if glab not available)

## Acceptance Criteria

- [ ] `GitLabClient.createIssue` creates GitLab issues via glab CLI
- [ ] Bug reports get `bug` label (with fallback if label missing)
- [ ] Feature requests get `feature` label (with fallback if label missing)
- [ ] Response URL parsed correctly for gitlab.com and self-hosted
- [ ] `iw feedback` works when GitLab tracker configured
- [ ] All unit tests pass
- [ ] E2E test passes (when glab available)
- [ ] Works with both gitlab.com and self-hosted GitLab

## Implementation Notes

### Reading Config in feedback.scala

The feedback command needs to:
1. Read `.iw/config.conf` to get tracker type
2. If GitLab, read repository and call `GitLabClient.createIssue`
3. If not GitLab (or no config), fall back to GitHub (current behavior)

```scala
// Pseudocode
Config.load(".iw/config.conf") match
  case Right(config) if config.trackerType == Some("gitlab") =>
    GitLabClient.createIssue(config.repository, ...)
  case _ =>
    GitHubClient.createIssue(Constants.Feedback.Repository, ...)
```

### glab vs gh Command Differences

| Aspect | gh | glab |
|--------|-----|------|
| Description flag | `--body` | `--description` |
| URL format | `/issues/123` | `/-/issues/123` |
| JSON output | `--json` | `--output json` |

### Error Messages

Reuse existing error formatters from Phase 2:
- `formatGlabNotInstalledError()`
- `formatGlabNotAuthenticatedError()`

---

**Next Steps:**
1. Generate phase-05-tasks.md with TDD task breakdown
2. Implement in TDD cycle (tests first, then implementation)
3. Run code review
4. Create PR for human review
