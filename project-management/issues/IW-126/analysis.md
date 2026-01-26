# Story-Driven Analysis: Add llms.txt files for core module documentation

**Issue:** IW-126
**Created:** 2026-01-25
**Updated:** 2026-01-25
**Status:** Draft
**Classification:** Feature

## Problem Statement

Agents using the `iw-command-creation` skill currently need to read full Scala source files to understand what APIs are available in iw-cli's core modules. This creates friction in the workflow and slows down script composition.

Additionally, the current `.iw/core/` directory mixes public API modules (intended for script composition) with internal implementation details (dashboard services, caches, views). This makes it unclear which modules are stable and supported for external use.

We need:
1. A clear public API boundary in the module structure
2. Structured, LLM-readable documentation (llms.txt format) for the public API
3. Integration with the iw-command-creation skill so agents can discover and use the docs

**User Value:** Faster agent workflow when creating custom commands, reduced context needed to understand available APIs, better discoverability of core module capabilities, and clear contract for what's stable/supported.

## Design Decisions (Resolved)

The following decisions were made during analysis clarification:

### Format: Standard llms.txt with per-module markdown files
- Follow the [llms.txt standard](https://llmstxt.org): H1 title, blockquote summary, H2 sections with links
- Main `llms.txt` index file linking to per-module `.md` documentation files
- Per-module files contain: overview, API signatures, usage examples

### Scope: Public API only (structural criterion)
- Refactor `.iw/core/` to have explicit public/internal boundaries
- Only modules in the public API directory get detailed documentation
- Directory structure declares intent - no ambiguity about what to document
- Sustainable: future modules automatically included/excluded based on location

### Integration: Update skill with dynamic path resolution
- Update `iw-command-creation` skill to reference llms.txt
- Use path relative to iw-cli installation (e.g., `$IW_CORE_DIR/../llms.txt`)
- llms.txt lifecycle tied to iw-cli releases, not `claude-sync`

## User Stories

### Story 1: Developer understands which modules are public API

```gherkin
Feature: Clear public API boundary
  As a developer creating iw-cli scripts
  I want to know which modules are part of the stable public API
  So that I can depend on them without risk of breaking changes

Scenario: Public modules are clearly separated
  Given the developer explores the .iw/core/ directory
  When they look at the directory structure
  Then public API modules are in a dedicated location (e.g., core/api/)
  And internal modules are in a separate location (e.g., core/internal/)
  And the boundary is self-documenting through structure
```

**Estimated Effort:** 3-4h
**Complexity:** Medium (requires careful analysis of current usage)

**Technical Feasibility:**
- Need to analyze which modules are used by `.iw/commands/` and external scripts
- Some modules may need minor refactoring if they mix public/internal concerns
- Imports in existing commands will need updating

**Acceptance:**
- Clear directory separation between public and internal modules
- All existing commands still compile and work
- Public API contains modules needed for script composition

---

### Story 2: Agent discovers available public modules

```gherkin
Feature: Quick module discovery via llms.txt
  As an agent using iw-command-creation skill
  I want to see a list of all public modules with brief descriptions
  So that I can quickly identify which modules to use for my task

Scenario: Agent reads llms.txt index
  Given the agent needs to compose a script using iw-cli modules
  When the agent reads the llms.txt file
  Then the agent sees all public API modules listed
  And each module has a one-line description
  And each entry links to detailed per-module documentation
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
- Standard llms.txt format is well-defined
- Information exists in source file PURPOSE comments
- Only need to document public API modules (bounded scope)

**Acceptance:**
- `llms.txt` follows standard format (H1, blockquote, H2 sections with links)
- All public API modules are listed
- Links point to per-module `.md` files

---

### Story 3: Agent understands module APIs without reading source

```gherkin
Feature: Detailed module API reference
  As an agent composing an iw-cli script
  I want to see function signatures and return types for a module
  So that I can use the module correctly without reading source files

Scenario: Agent looks up GitAdapter API
  Given the agent needs to use GitAdapter in a script
  When the agent reads the GitAdapter documentation
  Then the agent sees all public functions with signatures
  And each function has parameter types and return types
  And usage examples demonstrate common patterns
  And the agent can compose correct code without reading Git.scala
```

**Estimated Effort:** 4-6h (depends on number of public modules)
**Complexity:** Straightforward but repetitive

**Technical Feasibility:**
- Extract signatures from well-typed Scala code
- Focus on modules in the public API directory
- Can use existing commands as source for examples

**Acceptance:**
- Each public module has a corresponding `.md` file
- Files contain: overview, API signatures with types, usage examples
- Examples are copy-paste ready and show error handling patterns

---

### Story 4: Agent finds documentation via skill

```gherkin
Feature: Skill references llms.txt documentation
  As an agent using the iw-command-creation skill
  I want the skill to point me to llms.txt documentation
  So that I can find API reference without searching

Scenario: Skill includes documentation path
  Given the agent loads the iw-command-creation skill
  When the agent reads the skill instructions
  Then the skill references the llms.txt location
  And the path works regardless of iw-cli installation location
  And the agent can read llms.txt to discover modules
```

**Estimated Effort:** 1-2h
**Complexity:** Straightforward

**Technical Feasibility:**
- Skill already references `$IW_CORE_DIR` for module paths
- Add reference to llms.txt using same pattern
- Minor text update to skill file

**Acceptance:**
- `iw-command-creation` skill mentions llms.txt
- Path uses environment variable or relative reference
- Agents can follow the reference to find documentation

## Architectural Sketch

### Directory Structure (After Refactoring)

```
.iw/
├── core/
│   ├── api/                    # Public API - documented in llms.txt
│   │   ├── Output.scala
│   │   ├── Git.scala
│   │   ├── GitWorktree.scala
│   │   ├── IssueId.scala
│   │   ├── Issue.scala
│   │   ├── Config.scala
│   │   ├── ConfigRepository.scala
│   │   ├── Process.scala
│   │   ├── Prompt.scala
│   │   ├── Constants.scala
│   │   └── WorktreePath.scala
│   │
│   ├── internal/               # Internal implementation - not documented
│   │   ├── CaskServer.scala
│   │   ├── DashboardService.scala
│   │   ├── CachedProgress.scala
│   │   └── ...
│   │
│   ├── infrastructure/         # Adapters - selectively documented
│   │   ├── GitHubClient.scala
│   │   ├── LinearClient.scala
│   │   └── ...
│   │
│   └── domain/                 # Domain models - documented if public
│       └── ...
│
├── llms.txt                    # Index file (standard format)
└── docs/                       # Per-module documentation
    ├── Output.md
    ├── Git.md
    ├── GitWorktree.md
    └── ...
```

### llms.txt Format (Standard)

```markdown
# iw-cli Core Modules

> Functional Scala modules for CLI automation: git operations, issue tracking,
> console output, and shell execution. Import with `import iw.core.api.*`

## Public API

- [Output](docs/Output.md): Console output formatting (info, error, success, section)
- [Git](docs/Git.md): Git operations via GitAdapter (branch, remote, status)
- [GitWorktree](docs/GitWorktree.md): Worktree management via GitWorktreeAdapter
- [IssueId](docs/IssueId.md): Issue ID parsing and validation
- [Config](docs/Config.md): Configuration types and repository

## Optional

- [GitHubClient](docs/GitHubClient.md): GitHub API via gh CLI
- [LinearClient](docs/LinearClient.md): Linear API client
```

### Per-Module Documentation Format

```markdown
# Output

> Console output formatting with consistent styling for CLI tools.

## Import

\`\`\`scala
import iw.core.api.Output
\`\`\`

## API

### info(message: String): Unit
Print an informational message to stdout.

### error(message: String): Unit
Print an error message to stderr.

### success(message: String): Unit
Print a success message with checkmark (✓).

...

## Examples

\`\`\`scala
Output.section("Processing")
Output.info("Starting...")
Output.success("Done!")
Output.keyValue("Files", "42")
\`\`\`
```

## Technical Risks & Considerations

### Risk: Breaking existing commands during refactoring
- **Mitigation:** Update imports in all `.iw/commands/` files as part of refactoring
- **Mitigation:** Run full test suite after restructuring
- **Mitigation:** Keep module names identical, only change package paths

### Risk: Unclear boundary between public and internal
- **Mitigation:** Start with clear criterion: "used by commands or skill examples = public"
- **Mitigation:** Can adjust boundary in future releases if needed

### Consideration: Documentation maintenance
- llms.txt files are manually curated (not auto-generated)
- Updated when public API changes (part of normal code review)
- `claude-sync` does NOT touch llms.txt (only updates skills)

## Total Estimates

**Story Breakdown:**
- Story 1 (Refactor for public API): 3-4 hours
- Story 2 (Create llms.txt index): 2-3 hours
- Story 3 (Per-module documentation): 4-6 hours
- Story 4 (Update skill): 1-2 hours

**Total Range:** 10-15 hours

**Confidence:** Medium-High

**Reasoning:**
- Refactoring scope is bounded (only moving files, updating imports)
- Documentation scope is bounded by public API (structural criterion)
- Format is standardized (llms.txt spec)
- Existing material (PURPOSE comments, skill examples) reduces writing effort

## Testing Approach

### Story 1 (Refactoring)
- All existing commands must compile after refactoring
- Run `./iw test` to verify no regressions
- Verify imports work from external scripts

### Story 2-3 (Documentation)
- Documented signatures must match actual source code
- Examples must be syntactically correct Scala
- Examples should compile with core modules

### Story 4 (Skill Update)
- Skill file must parse correctly
- Path references must resolve in installed iw-cli

## Implementation Sequence

**Phase 1: Establish Public API Boundary (Story 1)**
1. Analyze current module usage in commands and skills
2. Create `api/` and `internal/` directories
3. Move modules to appropriate locations
4. Update all imports in commands
5. Verify tests pass

**Phase 2: Create Documentation (Stories 2-3)**
1. Create `llms.txt` index file
2. Create per-module `.md` files for public API
3. Extract examples from existing commands
4. Verify documentation accuracy

**Phase 3: Integrate with Skill (Story 4)**
1. Update `iw-command-creation` skill to reference llms.txt
2. Use `$IW_CORE_DIR` or similar for path resolution
3. Test that agents can find and use documentation

## Dependencies

### Prerequisites
- Understanding of which modules are used externally
- Existing `.iw/commands/` as reference for public API usage

### Story Dependencies
- Story 1 must complete before Stories 2-3 (need to know what's public)
- Stories 2-3 can proceed in parallel
- Story 4 can start after Story 2 (needs llms.txt to exist)

### External Blockers
None

## Deployment Considerations

### Distribution
- llms.txt and docs/ included in iw-cli releases
- Part of the tarball alongside core modules

### Backward Compatibility
- Import paths change from `iw.core.*` to `iw.core.api.*`
- Existing scripts will need import updates
- Consider: provide migration note in release notes

### Rollout
- Can deploy incrementally: refactoring first, then docs
- Each phase provides value independently

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Review and approve analysis
2. `/iterative-works:ag-create-tasks IW-126` to generate implementation phases
3. `/iterative-works:ag-implement IW-126` to begin implementation
