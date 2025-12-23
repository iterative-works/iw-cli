# Phase 5 Context: Display GitHub issue details

**Issue:** IWLE-132
**Phase:** 5 of 6
**Story:** Story 3 - Display GitHub issue details
**Status:** Ready for Implementation
**Estimated Effort:** 6-8 hours

---

## Goals

This phase extends the `iw issue` command to support GitHub tracker, enabling developers to view issue details from GitHub Issues using the `gh` CLI. The goal is to provide the same seamless experience for GitHub issues as we currently have for Linear and YouTrack.

**What this phase accomplishes:**
1. Add `gh issue view` support to GitHubClient for fetching issue details
2. Extend IssueId parsing to handle numeric GitHub issue IDs (e.g., "132")
3. Add GitHub case to `issue.scala` command routing
4. Map GitHub issue JSON to our Issue domain model
5. Support branch name inference for numeric issue IDs (e.g., "132-feature-name")
6. Display GitHub issues with the same formatting as Linear/YouTrack

**User value:**
- View GitHub issue details without leaving the terminal
- Automatic issue ID inference from branch names works for GitHub
- Consistent experience across all three tracker types (Linear, YouTrack, GitHub)
- No need to open a browser or remember issue URLs

---

## Scope

### In Scope

**IssueId Parsing Enhancements:**
- Extend `IssueId.parse()` to accept numeric-only strings (e.g., "132")
- Extend `IssueId.fromBranch()` to extract numeric prefixes (e.g., "132-feature")
- Maintain backward compatibility with "TEAM-NNN" format for Linear/YouTrack
- Make `team` field optional (returns empty string for numeric GitHub IDs)

**GitHubClient Extensions:**
- Add `fetchIssue(issueId: IssueId, repository: String)` method
- Build `gh issue view <number> --repo <owner/repo> --json` command
- Parse GitHub issue JSON response (number, title, state, assignees, body)
- Map GitHub state ("open", "closed") to our status field
- Extract assignee from JSON (handle multiple assignees, use first)
- Reuse prerequisite validation from Phase 4

**Command Integration:**
- Add GitHub case to `issue.scala` `fetchIssue()` pattern match
- Read repository from config for GitHub tracker
- Route to GitHubClient.fetchIssue when tracker is GitHub

**Testing:**
- Unit tests for numeric IssueId parsing
- Unit tests for GitHub JSON response parsing
- Unit tests for gh command building
- E2E tests for `iw issue <number>` with GitHub tracker
- E2E tests for `iw issue` (inferred from branch) with GitHub tracker
- Regression tests ensuring Linear/YouTrack still work

### Out of Scope

**Not in this phase:**
- Creating/editing GitHub issues (already done in Phase 3)
- Doctor command GitHub checks (Phase 6)
- GitHub-specific formatting or rich features (labels, milestones, etc.)
- Caching of issue data
- Offline mode or graceful degradation
- Support for GitHub pull request viewing (future enhancement)

**Deferred decisions:**
- Whether to show GitHub labels in issue display (current Issue model doesn't include labels)
- Whether to show GitHub milestones or projects
- Whether to include comments or timeline in issue view
- How to display multiple assignees (current: use first assignee)

---

## Dependencies

### Prerequisites from Previous Phases

**From Phase 1 (Critical):**
- `IssueTrackerType.GitHub` enum case exists
- Config schema supports `repository` field for GitHub tracker
- `iw init --tracker github` creates valid GitHub configuration

**From Phase 2:**
- Repository auto-detection from git remote works
- Config stores repository in `owner/repo` format

**From Phase 3 (Critical):**
- `GitHubClient` object exists with command execution patterns
- `buildCreateIssueCommand()` demonstrates gh CLI array-based command building
- `parseCreateIssueResponse()` demonstrates JSON parsing pattern
- Function injection pattern (`execCommand` parameter) for testability

**From Phase 4 (Critical - Will Reuse):**
- `validateGhPrerequisites()` validates gh CLI installation and authentication
- `formatGhNotInstalledError()` and `formatGhNotAuthenticatedError()` provide user-friendly messages
- Prerequisite checking pattern: validate before attempting operation

### Current System Dependencies

**From issue.scala:**
- `getIssueId()` function for parsing/inferring issue IDs
- `loadConfig()` function for reading project configuration
- `fetchIssue()` pattern match on tracker type (needs GitHub case)
- `IssueFormatter.format()` for displaying issues (reuse as-is)

**From IssueId domain:**
- `IssueId.parse()` opaque type with validation pattern
- `IssueId.fromBranch()` for branch name parsing
- `.team` extension method (must handle numeric IDs)
- Pattern-based validation using regex

**From Issue domain:**
- `Issue` case class with id, title, status, assignee, description fields
- `IssueFormatter.format()` generates bordered display
- No changes needed to Issue model (GitHub data maps to existing fields)

### External Dependencies

**Required tools:**
- `gh` CLI (validated via Phase 4's prerequisite checks)
- GitHub authentication (via `gh auth login`)

**Expected `gh issue view` behavior:**
```bash
gh issue view 132 --repo owner/repo --json number,title,state,assignees,body
```

**JSON output format:**
```json
{
  "number": 132,
  "title": "Add GitHub Issues support",
  "state": "OPEN",
  "assignees": [
    {"login": "username"}
  ],
  "body": "Issue description markdown..."
}
```

---

## Technical Approach

### High-Level Strategy

**Three-Part Implementation:**

1. **IssueId Parsing Enhancement** - Support numeric IDs alongside "TEAM-NNN"
2. **GitHubClient.fetchIssue** - Fetch and parse GitHub issue JSON
3. **Command Routing** - Add GitHub case to issue.scala

**Design Principle:** Minimal changes, maximum reuse
- Reuse existing Issue domain model (no schema changes)
- Reuse IssueFormatter (no display changes)
- Extend IssueId parsing (backward compatible)
- Follow Phase 3 patterns for GitHubClient

### Part 1: IssueId Parsing for Numeric GitHub IDs

**Current Implementation (IssueId.scala):**
```scala
private val Pattern = """^[A-Z]+-[0-9]+$""".r
private val BranchPattern = """^([A-Z]+-[0-9]+).*""".r

def parse(raw: String): Either[String, IssueId] =
  val normalized = raw.toUpperCase.trim
  normalized match
    case Pattern() => Right(normalized)
    case _ => Left(s"Invalid issue ID format: $raw (expected: PROJECT-123)")
```

**Enhanced Implementation:**
```scala
private val Pattern = """^[A-Z]+-[0-9]+$""".r
private val NumericPattern = """^[0-9]+$""".r  // NEW: GitHub numeric IDs
private val BranchPattern = """^([A-Z]+-[0-9]+).*""".r
private val NumericBranchPattern = """^([0-9]+)[-_].*""".r  // NEW: "132-feature"

def parse(raw: String): Either[String, IssueId] =
  val trimmed = raw.trim
  trimmed.toUpperCase match
    case Pattern() => Right(trimmed.toUpperCase)  // IWLE-132
    case _ =>
      trimmed match
        case NumericPattern() => Right(trimmed)  // 132 (GitHub)
        case _ => Left(s"Invalid issue ID format: $raw (expected: PROJECT-123 or 123)")

def fromBranch(branchName: String): Either[String, IssueId] =
  branchName.toUpperCase match
    case BranchPattern(issueId) => Right(issueId)  // IWLE-132-description
    case _ =>
      branchName match
        case NumericBranchPattern(issueId) => Right(issueId)  // 132-description
        case _ => Left(s"Cannot extract issue ID from branch '$branchName'")

extension (issueId: IssueId)
  def team: String =
    if issueId.contains("-") then
      issueId.split("-").head  // IWLE-132 -> IWLE
    else
      ""  // 132 -> "" (GitHub has no team)
```

**Key decisions:**
- Keep original patterns first (Linear/YouTrack priority)
- Add numeric patterns as fallback (GitHub compatibility)
- Don't uppercase numeric IDs (preserve "132" not "132")
- Support both `-` and `_` separators in branch names
- `team` returns empty string for numeric IDs (graceful degradation)

### Part 2: GitHubClient.fetchIssue Implementation

**Method Signature:**
```scala
def fetchIssue(
  issueNumber: String,  // Just the number, not full IssueId
  repository: String,   // owner/repo format
  execCommand: (String, Array[String]) => Either[String, String] =
    (cmd, args) => CommandRunner.execute(cmd, args)
): Either[String, Issue]
```

**Implementation Flow:**
```scala
def fetchIssue(
  issueNumber: String,
  repository: String,
  execCommand: (String, Array[String]) => Either[String, String] = ...
): Either[String, Issue] =
  // 1. Validate prerequisites (reuse from Phase 4)
  validateGhPrerequisites(repository, execCommand = execCommand) match
    case Left(GhNotInstalled) => return Left(formatGhNotInstalledError())
    case Left(GhNotAuthenticated) => return Left(formatGhNotAuthenticatedError())
    case Left(GhOtherError(msg)) => return Left(s"gh CLI error: $msg")
    case Right(_) => ()  // Continue

  // 2. Build command
  val args = buildFetchIssueCommand(issueNumber, repository)

  // 3. Execute gh issue view
  execCommand("gh", args) match
    case Left(error) => Left(s"Failed to fetch issue: $error")
    case Right(jsonOutput) =>
      // 4. Parse JSON response
      parseFetchIssueResponse(jsonOutput, issueNumber)
```

**Command Building:**
```scala
def buildFetchIssueCommand(
  issueNumber: String,
  repository: String
): Array[String] =
  Array(
    "issue", "view", issueNumber,
    "--repo", repository,
    "--json", "number,title,state,assignees,body"
  )
```

**JSON Parsing:**
```scala
def parseFetchIssueResponse(
  jsonOutput: String,
  issueNumber: String
): Either[String, Issue] =
  try
    import ujson.*
    val json = read(jsonOutput)

    val id = s"#${issueNumber}"  // Format as #132
    val title = json("title").str
    val state = json("state").str.toLowerCase  // "OPEN" -> "open"
    
    // Extract first assignee if any
    val assignee = 
      if json("assignees").arr.isEmpty then None
      else Some(json("assignees").arr.head("login").str)
    
    // Body might be null in JSON
    val description = 
      if json("body").isNull then None
      else Some(json("body").str)

    Right(Issue(
      id = id,
      title = title,
      status = state,
      assignee = assignee,
      description = description
    ))
  catch
    case e: Exception =>
      Left(s"Failed to parse issue response: ${e.getMessage}")
```

**Error Handling:**
- Reuse `validateGhPrerequisites()` from Phase 4 (gh not installed, not authenticated)
- Handle issue not found (gh CLI returns error)
- Handle malformed JSON response
- Provide specific error messages for each case

### Part 3: Command Integration in issue.scala

**Current Pattern Match (Linear + YouTrack):**
```scala
def fetchIssue(issueId: IssueId, config: ProjectConfiguration): Either[String, Issue] =
  config.trackerType match
    case IssueTrackerType.Linear =>
      ApiToken.fromEnv(Constants.EnvVars.LinearApiToken) match
        case None => Left(s"${Constants.EnvVars.LinearApiToken} environment variable is not set")
        case Some(token) => LinearClient.fetchIssue(issueId, token)

    case IssueTrackerType.YouTrack =>
      // ... YouTrack logic
```

**Enhanced Pattern Match (Add GitHub):**
```scala
def fetchIssue(issueId: IssueId, config: ProjectConfiguration): Either[String, Issue] =
  config.trackerType match
    case IssueTrackerType.Linear =>
      ApiToken.fromEnv(Constants.EnvVars.LinearApiToken) match
        case None => Left(s"${Constants.EnvVars.LinearApiToken} environment variable is not set")
        case Some(token) => LinearClient.fetchIssue(issueId, token)

    case IssueTrackerType.YouTrack =>
      ApiToken.fromEnv(Constants.EnvVars.YouTrackApiToken) match
        case None => Left(s"${Constants.EnvVars.YouTrackApiToken} environment variable is not set")
        case Some(token) =>
          config.youtrackBaseUrl match
            case Some(baseUrl) => YouTrackClient.fetchIssue(issueId, baseUrl, token)
            case None => Left(s"YouTrack base URL not configured")

    case IssueTrackerType.GitHub =>  // NEW CASE
      config.repository match
        case None =>
          Left("GitHub repository not configured. Run 'iw init' first.")
        case Some(repository) =>
          // Extract numeric part from IssueId (handle both "132" and "IWLE-132")
          val issueNumber = if issueId.value.contains("-") then
            issueId.value.split("-")(1)  // IWLE-132 -> 132 (shouldn't happen, but handle it)
          else
            issueId.value  // 132 -> 132
          
          GitHubClient.fetchIssue(issueNumber, repository)
```

**Key Integration Points:**
- Read repository from config.repository (required for GitHub)
- Extract numeric issue number from IssueId
- No API token needed (gh handles auth)
- Error handling flows through to output (same as Linear/YouTrack)

---

## Files to Modify

### Core Domain (Major Changes)

**`.iw/core/IssueId.scala`** (Major changes)
- Add `NumericPattern` regex for GitHub numeric IDs
- Add `NumericBranchPattern` regex for numeric branch prefixes
- Update `parse()` to accept numeric strings
- Update `fromBranch()` to extract numeric prefixes
- Update `team` extension to handle numeric IDs (return "")

**Estimated lines:** ~20 new lines, ~15 modified

### Infrastructure (Major Additions)

**`.iw/core/GitHubClient.scala`** (Major additions)
- Add `fetchIssue(issueNumber, repository, execCommand)` method
- Add `buildFetchIssueCommand(issueNumber, repository)` helper
- Add `parseFetchIssueResponse(jsonOutput, issueNumber)` helper
- Reuse `validateGhPrerequisites()` from Phase 4

**Estimated lines:** ~80-100 new lines (similar to createIssue method)

### Command Layer (Minor Changes)

**`.iw/commands/issue.scala`** (Minor changes)
- Add `IssueTrackerType.GitHub` case to `fetchIssue()` pattern match
- Extract numeric issue number from IssueId for GitHub
- Handle missing repository in config

**Estimated lines:** ~15-20 new lines in fetchIssue()

### Testing (Major Additions)

**`.iw/core/test/IssueIdTest.scala`** (Major additions)
- Test `parse()` with numeric IDs ("132", "1", "999")
- Test `parse()` with "TEAM-NNN" (regression)
- Test `fromBranch()` with numeric prefixes ("132-feature", "123_bugfix")
- Test `fromBranch()` with "TEAM-NNN" prefixes (regression)
- Test `team` extension with numeric IDs (should return "")
- Test `team` extension with "TEAM-NNN" (regression)

**Estimated lines:** ~60-80 new test lines

**`.iw/core/test/GitHubClientTest.scala`** (Major additions)
- Test `buildFetchIssueCommand()` generates correct args
- Test `parseFetchIssueResponse()` with valid JSON
- Test `parseFetchIssueResponse()` with missing assignees
- Test `parseFetchIssueResponse()` with null body
- Test `parseFetchIssueResponse()` with malformed JSON
- Test `fetchIssue()` validates prerequisites first
- Test `fetchIssue()` handles command execution errors

**Estimated lines:** ~100-120 new test lines

**`.iw/test/issue.bats`** (Check if exists, create if not)
- E2E test: `iw issue 132` with GitHub tracker
- E2E test: `iw issue` (inferred from branch "132-feature")
- E2E test: Error when issue not found
- E2E test: Error when gh not installed
- E2E test: Regression - Linear issue still works
- E2E test: Regression - YouTrack issue still works

**Estimated lines:** ~80-100 new test lines (new file if doesn't exist)

---

## Testing Strategy

### Unit Tests (munit)

**Test File 1: `.iw/core/test/IssueIdTest.scala`**

Coverage areas:
- Numeric ID parsing (GitHub): "132", "1", "999"
- Whitespace handling for numeric IDs
- Invalid formats rejection
- Backward compatibility with "TEAM-NNN" format
- Case normalization for "TEAM-NNN"
- Branch name parsing for numeric prefixes
- Branch name parsing for "TEAM-NNN" prefixes
- Team extraction for both formats

**Test File 2: `.iw/core/test/GitHubClientTest.scala`**

Coverage areas:
- Command building: correct gh CLI arguments
- JSON parsing: complete issue data
- JSON parsing: no assignees
- JSON parsing: null body
- JSON parsing: multiple assignees (use first)
- JSON parsing: malformed JSON error handling
- Integration: prerequisite validation before fetch
- Integration: successful fetch returns Issue

### E2E Tests (BATS)

**Test File: `.iw/test/issue.bats`** (create if doesn't exist)

Coverage areas:
- Display GitHub issue by explicit number
- Infer GitHub issue from branch name
- Error when issue not found
- Error when gh CLI not installed
- Regression: Linear tracker still works
- Mock strategy: fake gh scripts in temp PATH

### Test Coverage Requirements

**Must Cover:**
- Numeric IssueId parsing ("132", "1", "999")
- Numeric branch name parsing ("132-feature", "123_bugfix")
- GitHub JSON parsing (complete, no assignees, null body, multiple assignees)
- gh issue view command building
- Prerequisite validation (reuse Phase 4 tests)
- GitHub case in issue.scala routing
- Regression: Linear/YouTrack still work
- Regression: "TEAM-NNN" IDs still parse correctly
- Error handling: issue not found, malformed JSON, gh errors

---

## Acceptance Criteria

### Functional Requirements

- [ ] `iw issue 132` displays GitHub issue #132 when tracker is GitHub
- [ ] `iw issue` infers numeric issue ID from branch name "132-feature"
- [ ] Issue display shows title, status, assignee, and description
- [ ] GitHub state "OPEN" maps to status "open" (lowercase)
- [ ] GitHub state "CLOSED" maps to status "closed"
- [ ] First assignee is extracted when multiple assignees exist
- [ ] No assignee shown as "None" when assignees array is empty
- [ ] Description displays properly when body is null (shows nothing in description section)
- [ ] Error message when issue not found in repository
- [ ] Error message when gh not installed (reuses Phase 4 message)
- [ ] Error message when gh not authenticated (reuses Phase 4 message)
- [ ] Linear tracker still works with "IWLE-132" format (no regression)
- [ ] YouTrack tracker still works (no regression)

### Technical Requirements

- [ ] `IssueId.parse()` accepts both "TEAM-NNN" and numeric formats
- [ ] `IssueId.fromBranch()` extracts both "TEAM-NNN-desc" and "132-desc"
- [ ] `IssueId.team` returns "" for numeric IDs (graceful degradation)
- [ ] `GitHubClient.fetchIssue()` validates prerequisites first
- [ ] `GitHubClient.fetchIssue()` uses function injection for testability
- [ ] `GitHubClient.buildFetchIssueCommand()` uses array-based args (no shell injection)
- [ ] `GitHubClient.parseFetchIssueResponse()` handles all JSON edge cases
- [ ] GitHub case added to `issue.scala` `fetchIssue()` pattern match
- [ ] Repository read from config.repository for GitHub tracker
- [ ] Unit tests cover IssueId parsing edge cases
- [ ] Unit tests cover GitHub JSON parsing edge cases
- [ ] E2E tests verify end-to-end issue viewing
- [ ] No new dependencies introduced (reuse ujson from existing code)

### Quality Requirements

- [ ] Error messages are user-friendly (not technical jargon)
- [ ] Issue display formatting matches Linear/YouTrack (consistent UX)
- [ ] Code follows existing patterns (GitHubClient matches createIssue style)
- [ ] Tests are comprehensive (cover happy path + edge cases + regressions)
- [ ] No regressions in existing test suite (all tests still pass)
- [ ] Function injection maintained for testability

### Documentation Requirements

- [ ] Inline comments explain IssueId parsing strategy
- [ ] Inline comments explain GitHub JSON field mappings
- [ ] Test descriptions clearly state what scenario is being tested

---

## Implementation Checklist

### Phase A: IssueId Enhancement (1-2h)

- [ ] Add `NumericPattern` and `NumericBranchPattern` to IssueId.scala
- [ ] Update `parse()` to accept numeric strings
- [ ] Update `fromBranch()` to extract numeric prefixes
- [ ] Update `team` extension to handle numeric IDs
- [ ] Write unit tests for numeric IssueId parsing
- [ ] Write unit tests for numeric branch parsing
- [ ] Write regression tests for "TEAM-NNN" format
- [ ] Run tests and verify all pass

### Phase B: GitHubClient.fetchIssue (2-3h)

- [ ] Add `fetchIssue(issueNumber, repository, execCommand)` method
- [ ] Add `buildFetchIssueCommand(issueNumber, repository)` helper
- [ ] Add `parseFetchIssueResponse(jsonOutput, issueNumber)` helper
- [ ] Reuse `validateGhPrerequisites()` from Phase 4
- [ ] Write unit tests for command building
- [ ] Write unit tests for JSON parsing (complete, no assignees, null body, malformed)
- [ ] Write integration test for fetchIssue flow
- [ ] Run tests and verify all pass

### Phase C: Command Integration (1h)

- [ ] Add GitHub case to `fetchIssue()` in issue.scala
- [ ] Extract numeric issue number from IssueId
- [ ] Handle missing repository in config
- [ ] Test manually with GitHub config
- [ ] Verify Linear case still works (regression)

### Phase D: E2E Testing (2h)

- [ ] Create/update `.iw/test/issue.bats`
- [ ] Add test: `iw issue 132` with GitHub tracker
- [ ] Add test: `iw issue` inferred from branch "132-feature"
- [ ] Add test: Error when issue not found
- [ ] Add test: Error when gh not installed
- [ ] Add test: Regression - Linear still works
- [ ] Create mock gh scripts for testing
- [ ] Run E2E tests and fix any failures
- [ ] Verify no regressions in existing tests

### Phase E: Refinement (1h)

- [ ] Review error messages in terminal (verify formatting)
- [ ] Test with real GitHub repository (if available)
- [ ] Check exit codes are correct
- [ ] Final test run (unit + E2E)
- [ ] Update implementation-log.md with phase summary

---

## Risks and Mitigations

### Risk 1: IssueId changes break Linear/YouTrack

**Likelihood:** Medium
**Impact:** High

**Mitigation:**
- Write comprehensive regression tests first
- Keep "TEAM-NNN" patterns BEFORE numeric patterns (priority order)
- Test both formats thoroughly
- Don't uppercase numeric IDs (preserve original)

### Risk 2: GitHub JSON schema changes over time

**Likelihood:** Low
**Impact:** Medium

**Mitigation:**
- Use explicit `--json` field list (not all fields)
- Handle missing/null fields gracefully
- Add version check if needed (future)
- Document gh CLI version compatibility

### Risk 3: Multiple assignees UX is unclear

**Likelihood:** Low
**Impact:** Low

**Mitigation:**
- Use first assignee (simple, predictable)
- Document this behavior in code comments
- Future: could show all assignees or "Multiple assignees"

### Risk 4: Branch name pattern conflicts

**Likelihood:** Low
**Impact:** Low

**Mitigation:**
- Support both `-` and `_` separators
- Require numeric prefix (can't be middle/end)
- Document expected branch naming convention
- Fall back to explicit issue ID if branch parsing fails

---

## Open Questions

None - all decisions were resolved during analysis phase.

**Previously resolved:**
- IssueId format: Extend parse() for numeric GitHub IDs (analysis.md)
- Team field: Return empty string for numeric IDs (this document)
- Assignee handling: Use first assignee from array (this document)
- State mapping: Lowercase GitHub states (this document)
- Prerequisite validation: Reuse Phase 4 validateGhPrerequisites (this document)

---

## Success Metrics

**This phase is complete when:**

1. All acceptance criteria met
2. All unit tests passing (existing + ~20 new IssueId tests + ~15 new GitHubClient tests)
3. All E2E tests passing (existing + ~6 new issue command tests)
4. Manual testing with GitHub repository successful
5. No regressions in Linear/YouTrack functionality
6. Code review checklist completed
7. Implementation log updated

**User experience validation:**
- Run `iw issue 132` in GitHub project → See issue details
- Run `iw issue` in "132-feature" branch → See issue #132
- Run `iw issue` in GitHub project without gh → See helpful error
- Run `iw issue IWLE-132` in Linear project → Still works (regression check)

---

## Next Steps After This Phase

**Phase 6: Doctor validates GitHub setup**
- Add GitHub checks to `iw doctor` command
- Reuse `validateGhPrerequisites()` from Phase 4
- Show checkmarks for gh installed, gh authenticated, repository accessible
- Display troubleshooting guidance when checks fail

**Integration:**
- Phase 6 will call same validation logic established in Phase 4
- Doctor output will show GitHub-specific status checks
- Provides proactive validation before users encounter errors

**Feature Complete:**
- After Phase 6, GitHub tracker has full parity with Linear/YouTrack
- Users can: initialize, create feedback, view issues, validate setup
- All three trackers (Linear, YouTrack, GitHub) work independently

---

## References

- **Story Definition:** analysis.md Story 3
- **Technical Decisions:** analysis.md sections on IssueId compatibility
- **Existing Patterns:**
  - IssueId.scala (domain model to extend)
  - GitHubClient.scala (add fetchIssue alongside createIssue)
  - issue.scala (add GitHub case to pattern match)
  - Issue.scala and IssueFormatter.scala (reuse as-is)
- **Phase 4 Patterns:**
  - validateGhPrerequisites() (reuse for prerequisite checking)
  - formatGhNotInstalledError() and formatGhNotAuthenticatedError() (reuse for error messages)
- **Test Patterns:**
  - GitHubClientTest.scala (follow createIssue test patterns)
  - IssueIdTest.scala (add numeric ID tests)
  - feedback.bats (follow mocking patterns for issue.bats)

---

**Phase Status:** Ready for Implementation

**Confidence:** High - Clear requirements, existing patterns to follow, comprehensive testing strategy, reuses Phase 4 validation

**Estimated Effort:** 6-8 hours
- IssueId enhancement: 1-2h
- GitHubClient.fetchIssue: 2-3h
- Command integration: 1h
- E2E testing: 2h
- Refinement: 1h
