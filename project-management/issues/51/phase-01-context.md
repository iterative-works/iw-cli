# Phase 1 Context: Configure team prefix for GitHub projects

**Issue:** #51
**Phase:** 1 of 3
**Estimated Effort:** 2-3 hours
**Status:** Not Started

## Goals

Enable GitHub projects to configure a team prefix so that issue branches follow the unified `TEAM-NNN` format (e.g., `IWCLI-51`) instead of bare numeric format (e.g., `51`).

This phase establishes the foundation for the unified branch naming convention across all tracker types (Linear, YouTrack, and GitHub).

## Scope

### In Scope

1. **Config Model Changes**
   - Add `teamPrefix: Option[String]` field to `ProjectConfiguration`
   - Make team prefix required for GitHub tracker type
   - Store team prefix in `.iw/config.conf`

2. **Init Command Enhancement**
   - Prompt for team prefix when GitHub tracker is selected
   - Suggest default team prefix derived from repository name
   - Validate team prefix format (uppercase letters only, 2-10 characters)
   - Store team prefix in config file

3. **IssueId Factory Method**
   - Add `IssueId.forGitHub(teamPrefix: String, number: Int): IssueId`
   - Compose `TEAM-NNN` format from prefix and number
   - Validate composed ID matches existing `TEAM-NNN` pattern

4. **Start Command Integration**
   - Read team prefix from config when tracker is GitHub
   - Use `IssueId.forGitHub` to create properly formatted issue IDs
   - Create branches with `TEAM-NNN` format

5. **Testing**
   - Unit tests for config serialization with team prefix
   - Unit tests for team prefix validation
   - Unit tests for `IssueId.forGitHub` factory method
   - E2E test for `iw init` with GitHub tracker
   - E2E test for `iw start` creating team-prefixed branch

### Out of Scope

- Parsing team-prefixed issue IDs from user input (Phase 2)
- Parsing team-prefixed branches (Phase 2)
- Removing numeric-only pattern support (Phase 3)
- Migration of existing bare numeric branches
- Changes to `IssueId.parse` or `IssueId.fromBranch` (Phase 2)

## Dependencies

### Prerequisites

- None (this is the first phase)

### External Dependencies

- Existing config infrastructure (`Config.scala`, `ConfigSerializer`, `ConfigFileRepository`)
- Existing `init.scala` command structure
- Existing `IssueId` value object
- Existing test framework (munit for unit tests, BATS for E2E)

## Technical Approach

### 1. Config Model Enhancement

**File:** `.iw/core/Config.scala`

**Changes:**
Add `teamPrefix` field to `ProjectConfiguration` case class around line 74.

**ConfigSerializer.toHocon updates:**
- For GitHub tracker: write `teamPrefix = "IWCLI"` field
- For Linear/YouTrack: omit teamPrefix (use existing team field)

**ConfigSerializer.fromHocon updates:**
- For GitHub tracker: require `teamPrefix` field (return error if missing)
- For GitHub tracker: validate prefix format (uppercase letters, 2-10 chars)
- For Linear/YouTrack: teamPrefix remains None

**Constants.scala updates:**
Add `TrackerTeamPrefix` constant to ConfigKeys object.

### 2. Team Prefix Validation

**Validation Rules:**
- Uppercase letters only: `[A-Z]+`
- Minimum length: 2 characters
- Maximum length: 10 characters
- No numbers, no special characters, no lowercase

**Default Suggestion Algorithm:**
```
Repository: iterative-works/iw-cli
Extract: iw-cli
Remove hyphens: iwcli
Uppercase: IWCLI
Suggested: IWCLI
```

**Create new validation object** in Config.scala or separate file.

### 3. Init Command Updates

**File:** `.iw/commands/init.scala`

**Changes in GitHub branch (lines 80-98):**
- Extract repository and suggest team prefix
- Prompt user for team prefix with suggested default
- Validate team prefix format
- Pass team prefix to ProjectConfiguration

### 4. IssueId Factory Method

**File:** `.iw/core/IssueId.scala`

**Add new factory method:**
```scala
def forGitHub(teamPrefix: String, number: Int): Either[String, IssueId]
```

Compose `TEAM-NNN` format and validate through existing `parse` method.

### 5. Start Command Integration

**File:** `.iw/commands/start.scala`

**Changes:**
- Read config to get team prefix
- For GitHub tracker + numeric input: use `IssueId.forGitHub`
- Otherwise: use existing `IssueId.parse`

## Files to Modify

### Core Domain Files

1. **`.iw/core/Config.scala`**
   - Add `teamPrefix: Option[String]` to `ProjectConfiguration`
   - Update `ConfigSerializer.toHocon` to serialize team prefix for GitHub
   - Update `ConfigSerializer.fromHocon` to parse and validate team prefix for GitHub
   - Add `TeamPrefixValidator` object (or separate file)
   - Lines affected: ~74-159

2. **`.iw/core/IssueId.scala`**
   - Add `IssueId.forGitHub(teamPrefix: String, number: Int)` factory method
   - Lines affected: ~8-51 (add new method around line 30)

3. **`.iw/core/Constants.scala`**
   - Add `TrackerTeamPrefix` to `ConfigKeys` object
   - Lines affected: ~16-22

### Command Files

4. **`.iw/commands/init.scala`**
   - Add team prefix prompt for GitHub tracker
   - Add default suggestion logic
   - Add validation before storing config
   - Lines affected: ~80-116 (GitHub branch handling)

5. **`.iw/commands/start.scala`**
   - Read team prefix from config
   - Use `IssueId.forGitHub` for bare numeric GitHub inputs
   - Lines affected: ~7-21 (issue ID parsing section)

### Test Files

6. **`.iw/core/test/ConfigTest.scala`**
   - Add test for GitHub config with team prefix serialization
   - Add test for GitHub config with team prefix deserialization
   - Add test for missing team prefix validation
   - Add test for invalid team prefix format validation
   - Lines affected: ~94-164 (GitHub config section)

7. **`.iw/core/test/IssueIdTest.scala`** (new tests)
   - Add test for `IssueId.forGitHub` success case
   - Add test for `IssueId.forGitHub` validation
   - Lines affected: ~130-184 (add after numeric GitHub tests)

8. **E2E tests** (check for existing test files, add tests as appropriate)

## Testing Strategy

### Unit Tests (munit)

**Config Tests:**
- Serialization: GitHub config with team prefix writes to HOCON
- Deserialization: GitHub config with team prefix reads from HOCON
- Validation: Missing team prefix for GitHub returns error
- Validation: Invalid team prefix format returns error

**IssueId Tests:**
- Factory method creates valid TEAM-NNN format
- Factory method validates composed ID
- Factory method preserves uppercase

**TeamPrefixValidator Tests:**
- Accepts valid uppercase prefix
- Rejects lowercase
- Rejects too short prefix
- Rejects too long prefix
- Suggests prefix from repository name

### E2E Tests (BATS)

**init workflow:**
- GitHub tracker prompts for team prefix
- Rejects invalid team prefix format
- Suggests team prefix from repository

**start workflow:**
- Creates TEAM-NNN branch for GitHub with team prefix

### Test Execution Order

1. **Unit tests first**: `./iw test unit`
2. **E2E tests second**: `./iw test e2e`

## Acceptance Criteria

### Functional Acceptance

- [ ] GitHub projects can configure a team prefix during `iw init`
- [ ] Team prefix is stored in `.iw/config.conf` as `tracker.teamPrefix`
- [ ] Team prefix validation rejects invalid formats (non-uppercase, too short/long)
- [ ] Team prefix suggestion derives reasonable default from repository name
- [ ] `iw start <number>` creates branch `TEAMPREFIX-<number>` for GitHub projects
- [ ] `iw start TEAMPREFIX-<number>` still works (forwards to existing parse logic)

### Technical Acceptance

- [ ] `ProjectConfiguration` has `teamPrefix: Option[String]` field
- [ ] `ConfigSerializer.toHocon` writes team prefix for GitHub
- [ ] `ConfigSerializer.fromHocon` requires team prefix for GitHub
- [ ] `IssueId.forGitHub(prefix, number)` factory method exists and validates
- [ ] `TeamPrefixValidator` validates format and suggests defaults
- [ ] `start.scala` uses team prefix when GitHub + numeric input

### Testing Acceptance

- [ ] All new unit tests pass
- [ ] All existing unit tests still pass (no regressions)
- [ ] E2E test for `iw init` with GitHub passes
- [ ] E2E test for `iw start` with team prefix passes
- [ ] Code coverage maintained or improved

### Code Quality Acceptance

- [ ] All files have proper PURPOSE comments
- [ ] No compilation warnings
- [ ] Code follows existing functional style (immutable, pure functions)
- [ ] Error messages are clear and actionable
- [ ] No breaking changes to Linear/YouTrack functionality

## Known Risks & Mitigations

### Risk 1: Breaking existing GitHub workflows

**Risk:** Users with existing bare numeric branches will break.

**Mitigation (Phase 1 scope):**
- Phase 1 only affects NEW branch creation via `iw start`
- Existing branches continue to work (Phase 2 handles parsing)
- `iw init --force` can re-configure existing projects

### Risk 2: Team prefix collision with repository names

**Risk:** Suggested prefix might collide with existing branches or conventions.

**Mitigation:**
- User can override suggestion during `iw init`
- Validation ensures format consistency
- Team prefix is per-project, not global

### Risk 3: Config file format breaking changes

**Risk:** Adding required field breaks existing GitHub configs.

**Mitigation:**
- Version field already in config for future migrations
- Old configs without team prefix will fail gracefully with clear error
- Users can run `iw init --force` to upgrade

## Implementation Notes

### Order of Implementation

1. **Start with Config layer** (foundation):
   - Add `teamPrefix` field to `ProjectConfiguration`
   - Update serialization/deserialization
   - Write unit tests for config changes

2. **Add validation logic**:
   - Create `TeamPrefixValidator` object
   - Write unit tests for validation

3. **Update IssueId**:
   - Add `forGitHub` factory method
   - Write unit tests for factory

4. **Update init command**:
   - Add team prefix prompt
   - Add suggestion logic
   - Test interactively

5. **Update start command**:
   - Add team prefix application logic
   - Test with real branches

6. **E2E tests last**:
   - Write BATS tests for full workflows
   - Verify end-to-end behavior

### Testing During Development

Use TDD approach:
1. Write failing test
2. Implement minimum code to pass
3. Refactor
4. Repeat

### Commit Strategy

Commit after each major component:
- "Add teamPrefix field to ProjectConfiguration"
- "Add TeamPrefixValidator with unit tests"
- "Add IssueId.forGitHub factory method"
- "Update init command with team prefix prompt"
- "Update start command to use team prefix"
- "Add E2E tests for team prefix workflow"

## Definition of Done

Phase 1 is complete when:

1. All acceptance criteria are met
2. All tests pass (unit + E2E)
3. Code is committed to branch `51`
4. Phase 1 changes are working together (can init GitHub project with team prefix and create team-prefixed branch)
5. No regressions in Linear/YouTrack functionality
6. Ready to proceed to Phase 2 (parsing team-prefixed IDs)

## Next Phase Preview

**Phase 2: Parse and display GitHub issues with team prefix**

Will handle:
- `IssueId.parse("51")` with GitHub config → apply team prefix automatically
- `IssueId.parse("IWCLI-51")` → accept full format
- `IssueId.fromBranch("IWCLI-51-description")` → extract correctly (already works!)
- Commands like `iw issue IWCLI-51` work correctly

Dependencies from Phase 1:
- Config has team prefix available
- `IssueId.forGitHub` factory exists for composition
- Team prefix validation logic exists

---

**Phase Context Status:** Ready for Implementation

**Estimated Effort:** 2-3 hours
**Complexity:** Straightforward - adding config field and factory method
**Confidence:** High - well-understood changes with clear scope
