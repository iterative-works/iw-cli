# Phase 1 Tasks: Configure team prefix for GitHub projects

**Issue:** #51
**Phase:** 1 of 3
**Status:** Complete
**Estimated Effort:** 2-3 hours

## Task Breakdown

### Setup (15 min)

- [x] [test] Read existing ConfigTest.scala to understand test patterns
- [x] [test] Read existing IssueIdTest.scala to understand test patterns
- [x] [test] Read init.bats to understand E2E test setup

### Core Domain - Config Model (45-60 min)

#### Constants

- [x] [test] Write test for TrackerTeamPrefix constant existence in ConfigKeys
- [x] [impl] Add TrackerTeamPrefix = "tracker.teamPrefix" to ConfigKeys in Constants.scala

#### ProjectConfiguration Model

- [x] [test] Write test for ProjectConfiguration with teamPrefix field serialization
- [x] [test] Write test that GitHub config requires teamPrefix field
- [x] [impl] Add teamPrefix: Option[String] field to ProjectConfiguration case class

#### Config Serialization

- [x] [test] Write test for toHocon serializing GitHub config with teamPrefix
- [x] [test] Write test that toHocon omits teamPrefix for Linear/YouTrack
- [x] [impl] Update ConfigSerializer.toHocon to write teamPrefix for GitHub tracker
- [x] [impl] Run unit tests to verify serialization works

#### Config Deserialization

- [x] [test] Write test for fromHocon parsing GitHub config with teamPrefix
- [x] [test] Write test for fromHocon rejecting GitHub config without teamPrefix
- [x] [impl] Update ConfigSerializer.fromHocon to read teamPrefix for GitHub
- [x] [impl] Update ConfigSerializer.fromHocon to require teamPrefix for GitHub
- [x] [impl] Run unit tests to verify deserialization works

#### Team Prefix Validation

- [x] [test] Write test that validates uppercase-only team prefix (e.g., "IWCLI")
- [x] [test] Write test that rejects lowercase team prefix (e.g., "iwcli")
- [x] [test] Write test that rejects too-short prefix (< 2 chars)
- [x] [test] Write test that rejects too-long prefix (> 10 chars)
- [x] [test] Write test that rejects prefix with numbers (e.g., "IW2CLI")
- [x] [test] Write test that rejects prefix with special chars (e.g., "IW-CLI")
- [x] [impl] Create TeamPrefixValidator object with validate(prefix: String) method
- [x] [impl] Run unit tests to verify validation logic

#### Default Prefix Suggestion

- [x] [test] Write test that suggests "IWCLI" from "iterative-works/iw-cli"
- [x] [test] Write test that suggests "MYAPP" from "owner/my-app"
- [x] [test] Write test that handles single-word repos (e.g., "project" → "PROJECT")
- [x] [impl] Add suggestFromRepository(repo: String) method to TeamPrefixValidator
- [x] [impl] Run unit tests to verify suggestion logic

#### Round-trip Config Tests

- [x] [test] Write test for GitHub config round-trip (serialize + deserialize)
- [x] [test] Write test that Linear/YouTrack configs still work (no regression)
- [x] [impl] Run all config unit tests to verify no regressions

### Core Domain - IssueId Factory (30-45 min)

#### IssueId.forGitHub Factory Method

- [x] [test] Write test for IssueId.forGitHub("IWCLI", 51) returns Right("IWCLI-51")
- [x] [test] Write test for IssueId.forGitHub("iwcli", 51) returns error (lowercase)
- [x] [test] Write test for IssueId.forGitHub("IW", 123) returns Right("IW-123")
- [x] [test] Write test for IssueId.forGitHub("X", 1) returns error (too short)
- [x] [impl] Add forGitHub(teamPrefix: String, number: Int) method to IssueId object
- [x] [impl] Validate teamPrefix using TeamPrefixValidator
- [x] [impl] Compose "PREFIX-NUMBER" format
- [x] [impl] Validate composed ID through existing parse method
- [x] [impl] Run IssueId unit tests to verify factory method

### Commands - Init Enhancement (30-45 min)

#### Init Command GitHub Flow

- [x] [impl] Locate GitHub tracker handling in init.scala (around line 80-98)
- [x] [test] Write E2E test for init with --tracker=github --repository=owner/repo --team-prefix=IWCLI
- [x] [impl] Add --team-prefix optional flag to init command arguments
- [x] [impl] Extract repository name when GitHub tracker selected
- [x] [impl] Suggest team prefix using TeamPrefixValidator.suggestFromRepository
- [x] [impl] Prompt user for team prefix (with suggested default)
- [x] [impl] Validate team prefix before storing in config
- [x] [impl] Pass teamPrefix to ProjectConfiguration when creating GitHub config
- [x] [impl] Test init command interactively (manual smoke test)

#### Init E2E Tests

- [x] [test] Write E2E test that init rejects invalid team prefix format
- [x] [test] Write E2E test verifying .iw/config.conf contains teamPrefix field
- [x] [impl] Run init E2E tests to verify workflow

### Commands - Start Integration (30-45 min)

#### Start Command Team Prefix Application

- [x] [impl] Locate issue ID parsing in start.scala (around line 7-21)
- [x] [test] Write E2E test for start creating IWCLI-51 branch with team prefix config
- [x] [impl] Read ProjectConfiguration to get teamPrefix
- [x] [impl] Check if tracker is GitHub and input is numeric-only
- [x] [impl] If GitHub + numeric: use IssueId.forGitHub(teamPrefix, number)
- [x] [impl] Otherwise: use existing IssueId.parse logic
- [x] [impl] Test start command with numeric input (manual smoke test)

#### Start E2E Tests

- [x] [test] Write E2E test that start IWCLI-51 (full format) still works
- [x] [test] Write E2E test that start creates worktree with correct branch name
- [x] [impl] Run start E2E tests to verify workflow

### Integration & Verification (15-30 min)

#### Full Workflow Testing

- [x] [impl] Run all unit tests: ./iw test unit
- [x] [impl] Fix any failing unit tests
- [x] [impl] Run all E2E tests: ./iw test e2e
- [x] [impl] Fix any failing E2E tests
- [x] [test] Manual end-to-end test: init GitHub project → start 51 → verify IWCLI-51 branch

#### Code Quality

- [x] [impl] Verify all modified files have PURPOSE comments
- [x] [impl] Check for compilation warnings
- [x] [impl] Verify functional style (immutable values, pure functions)
- [x] [impl] Verify error messages are clear and actionable

### Commit & Documentation (15 min)

- [ ] [impl] Review all changes with git diff
- [ ] [impl] Commit config changes: "Add teamPrefix to ProjectConfiguration for GitHub"
- [ ] [impl] Commit validation: "Add TeamPrefixValidator with suggestion logic"
- [ ] [impl] Commit factory: "Add IssueId.forGitHub factory method"
- [ ] [impl] Commit init: "Update init command to prompt for GitHub team prefix"
- [ ] [impl] Commit start: "Update start command to apply team prefix for GitHub"
- [ ] [impl] Commit tests: "Add E2E tests for GitHub team prefix workflow"
- [ ] [impl] Update phase-01-context.md status to "Completed"

## Task Categories Summary

- **[test]**: 28 tasks - Writing tests (unit + E2E)
- **[impl]**: 29 tasks - Implementation and verification
- **[refactor]**: 0 tasks - No refactoring in this phase

**Total**: 57 tasks (mix of 15-30 minute chunks)

## Notes

- Follow TDD: Write tests before implementation
- Run tests frequently (after each small group of tasks)
- Commit after each major component (not after every task)
- Keep Linear/YouTrack functionality unchanged (no regressions)
- Manual smoke tests verify real user workflows

## Acceptance Criteria Checklist

From phase-01-context.md:

### Functional
- [ ] GitHub projects can configure team prefix during `iw init`
- [ ] Team prefix stored in `.iw/config.conf` as `tracker.teamPrefix`
- [ ] Team prefix validation rejects invalid formats
- [ ] Team prefix suggestion works from repository name
- [ ] `iw start <number>` creates `TEAMPREFIX-<number>` branch for GitHub
- [ ] `iw start TEAMPREFIX-<number>` still works

### Technical
- [ ] ProjectConfiguration has teamPrefix field
- [ ] ConfigSerializer.toHocon writes team prefix for GitHub
- [ ] ConfigSerializer.fromHocon requires team prefix for GitHub
- [ ] IssueId.forGitHub factory method exists
- [ ] TeamPrefixValidator validates and suggests
- [ ] start.scala uses team prefix for GitHub + numeric input

### Testing
- [ ] All new unit tests pass
- [ ] All existing unit tests pass (no regressions)
- [ ] E2E test for init with GitHub passes
- [ ] E2E test for start with team prefix passes
- [ ] Code coverage maintained

### Code Quality
- [ ] All files have PURPOSE comments
- [ ] No compilation warnings
- [ ] Functional style maintained
- [ ] Clear error messages
- [ ] No breaking changes to Linear/YouTrack

---

**Ready to start implementation!**
**Next step:** Begin with Setup tasks, then proceed through sections in order.
