# Implementation Log: Improve branch naming convention for GitHub issues

Issue: #51

This log tracks the evolution of implementation across phases.

---

## Phase 1: Configure team prefix for GitHub projects (2025-12-26)

**What was built:**
- Domain: `TeamPrefixValidator` object in Config.scala - validates format (2-10 uppercase letters) and suggests prefix from repository name
- Domain: `IssueId.forGitHub(prefix, number)` factory method - composes TEAM-NNN format
- Config: Added `teamPrefix: Option[String]` field to `ProjectConfiguration`
- Serialization: Updated `ConfigSerializer` to read/write teamPrefix for GitHub tracker
- Command: Updated `init.scala` to prompt for and validate team prefix for GitHub projects
- Command: Updated `start.scala` to apply team prefix when given numeric input for GitHub

**Decisions made:**
- Team prefix is required for GitHub tracker (enforced during config parsing)
- Validation: uppercase letters only, 2-10 characters (matches Linear/YouTrack conventions)
- Suggestion algorithm: extract repo name, remove hyphens, uppercase, truncate to 10 chars
- `start` command auto-applies prefix only for numeric-only input (preserves full format input)

**Patterns applied:**
- Smart constructor pattern: `IssueId.forGitHub` validates through existing `parse` method
- Functional Core: `TeamPrefixValidator` is pure, effects remain in shell (commands)
- Either-based error handling: validation returns `Either[String, T]` consistently

**Testing:**
- Unit tests: 29 new tests added (17 for Config, 12 for IssueId)
- E2E tests: 11 new tests added (7 for init, 4 for start)

**Code review:**
- Iterations: 1
- Review file: review-phase-01-20251226.md
- Major findings: No critical issues. 5 warnings about shell layer containing some domain logic (acceptable for CLI tool scope)

**For next phases:**
- Available utilities:
  - `TeamPrefixValidator.validate(prefix)` - validates team prefix format
  - `TeamPrefixValidator.suggestFromRepository(repo)` - suggests prefix from repo name
  - `IssueId.forGitHub(prefix, number)` - creates GitHub issue ID with team prefix
- Extension points:
  - `IssueId.parse` can be extended with context awareness (Phase 2)
  - `fromBranch` can be updated to require TEAM-NNN format (Phase 3)
- Notes: Numeric pattern support still exists in IssueId (will be removed in Phase 3)

**Files changed:**
```
M  .iw/commands/init.scala
M  .iw/commands/start.scala
M  .iw/core/Config.scala
M  .iw/core/Constants.scala
M  .iw/core/IssueId.scala
M  .iw/core/test/ConfigTest.scala
M  .iw/core/test/IssueIdTest.scala
M  .iw/test/init.bats
M  .iw/test/start.bats
```

---
