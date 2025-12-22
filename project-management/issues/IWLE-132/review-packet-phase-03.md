# Review Packet: Phase 3 - Create GitHub issue via feedback command

**Issue:** IWLE-132
**Phase:** 3 of 6
**Date:** 2025-12-22

---

## Goals

1. **Make feedback command work with GitHub tracker** - When `tracker.type = github`, the feedback command should create a GitHub issue via `gh issue create`
2. **Create GitHubClient service** - Follow the same pattern as LinearClient/YouTrackClient for consistency
3. **Map issue types to GitHub labels** - Bug → "bug", Feature → "feedback" (with graceful fallback if labels don't exist)
4. **Show issue URL on success** - Display the created issue number and URL

## Entry Points

### New Files

| File | Purpose |
|------|---------|
| `.iw/core/GitHubClient.scala` | GitHub CLI wrapper for issue creation via `gh` command |
| `.iw/core/test/GitHubClientTest.scala` | Unit tests for GitHubClient (14 tests) |

### Modified Files

| File | Changes |
|------|---------|
| `.iw/commands/feedback.scala` | Multi-tracker routing: GitHub vs Linear |
| `.iw/test/feedback.bats` | E2E tests for GitHub tracker feedback (6 tests) |

## Architecture

```
feedback.scala
    │
    ├── Load config (detect tracker type)
    │
    ├── Match tracker type:
    │   ├── GitHub → GitHubClient.createIssue()
    │   │              └── gh issue create --repo owner/repo --title ... --json
    │   │
    │   ├── Linear → LinearClient.createIssue()
    │   │              └── GraphQL API
    │   │
    │   └── YouTrack → Error (not implemented yet)
    │
    └── Display result (issue number + URL)
```

## Flow Diagram

```
User runs: iw feedback "Bug report" --type bug
    │
    ▼
feedback.scala: Load .iw/config.conf
    │
    ▼
Detect tracker.type = github
    │
    ▼
Extract repository from config (owner/repo)
    │
    ▼
GitHubClient.createIssue(repository, title, description, issueType)
    │
    ├── buildCreateIssueCommand() → Array of gh CLI args
    │   └── ["gh", "issue", "create", "--repo", "owner/repo", "--title", "...", "--label", "bug", "--json", "number,url"]
    │
    ├── Execute command via CommandRunner
    │
    ├── On label error → retry without --label flag (graceful fallback)
    │
    ├── parseCreateIssueResponse() → CreatedIssue(number, url)
    │
    └── Return Either[String, CreatedIssue]
    │
    ▼
Display: "Feedback submitted successfully!"
         "Issue: #42"
         "URL: https://github.com/owner/repo/issues/42"
```

## Test Summary

### Unit Tests (GitHubClientTest.scala) - 14 tests

| Test | Description |
|------|-------------|
| buildCreateIssueCommand with title only | Verifies basic command structure |
| buildCreateIssueCommand with description | Adds --body flag |
| buildCreateIssueCommand with bug label | Maps Bug → "bug" |
| buildCreateIssueCommand with feedback label | Maps Feature → "feedback" |
| buildCreateIssueCommand uses correct repository format | owner/repo format |
| parseCreateIssueResponse parses valid JSON | Extracts number and URL |
| parseCreateIssueResponse returns error for missing number | Error handling |
| parseCreateIssueResponse returns error for missing url | Error handling |
| parseCreateIssueResponse returns error for malformed JSON | Error handling |
| parseCreateIssueResponse returns error for empty response | Error handling |
| buildCreateIssueCommandWithoutLabel generates command without label | Fallback support |
| createIssue retries without label on label error | Graceful fallback |
| createIssue does not retry on non-label error | Only label-specific retry |
| createIssue returns error when retry also fails | Fallback error handling |

### E2E Tests (feedback.bats) - GitHub specific

| Test | Description |
|------|-------------|
| feedback with GitHub tracker without repository fails | Config validation |
| feedback with GitHub tracker and missing repository in config fails | Config validation |
| feedback with GitHub tracker creates issue successfully | Full flow (skipped without gh) |
| feedback with GitHub tracker and bug type applies bug label | Label mapping (skipped without gh) |
| feedback with GitHub tracker shows issue number in output | Output format |

### Regression Tests

All existing Linear tests continue to pass (tests 12-20 in feedback.bats).

## Acceptance Criteria

| AC | Status | Description |
|----|--------|-------------|
| AC1 | ✅ | GitHub tracker routes to GitHubClient - LINEAR_API_TOKEN not required |
| AC2 | ✅ | Issue created successfully with title, body, and labels |
| AC3 | ✅ | Success output shows "Issue: #N" and URL |
| AC4 | ✅ | Basic error handling for gh failures and missing repository |
| AC5 | ✅ | Existing Linear flow unchanged and tested |

## Key Implementation Decisions

1. **Label fallback strategy**: If `gh issue create` fails with a label-related error (e.g., "label 'bug' not found"), retry without labels. This allows issue creation even in repos without predefined labels.

2. **Function injection for testability**: `createIssue` accepts an `execCommand` parameter for testing, following the same pattern as other clients.

3. **Config loading in feedback command**: The command now reads `.iw/config.conf` to detect tracker type and repository. LINEAR_API_TOKEN is only required for Linear tracker.

4. **Pattern consistency**: GitHubClient follows the same structure as LinearClient (buildCommand, parseResponse, createIssue methods).

## Files for Review

```
.iw/core/GitHubClient.scala        # NEW - 146 lines
.iw/core/test/GitHubClientTest.scala # NEW - 189 lines
.iw/commands/feedback.scala        # MODIFIED - multi-tracker routing
.iw/test/feedback.bats             # MODIFIED - GitHub tests added
```

---

**Total test count:** 115 E2E tests, all passing
**Unit tests:** All GitHubClient tests passing
