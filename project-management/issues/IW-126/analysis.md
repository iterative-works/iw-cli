# Story-Driven Analysis: Add llms.txt files for core module documentation

**Issue:** IW-126
**Created:** 2026-01-25
**Status:** Draft
**Classification:** Simple

## Problem Statement

Agents using the `iw-command-creation` skill currently need to read full Scala source files to understand what APIs are available in iw-cli's core modules. This creates friction in the workflow and slows down script composition.

We need structured, LLM-readable documentation (llms.txt format) that provides quick API reference for core modules, enabling agents to quickly orient themselves when composing scripts from iw-cli's functional building blocks.

**User Value:** Faster agent workflow when creating custom commands, reduced context needed to understand available APIs, better discoverability of core module capabilities.

## User Stories

### Story 1: Agent discovers available core modules and their purpose

```gherkin
Feature: Quick module discovery
  As an agent using iw-command-creation skill
  I want to see a list of all core modules with brief descriptions
  So that I can quickly identify which modules to use for my task

Scenario: Agent reads master module index
  Given the agent needs to compose a script using iw-cli modules
  When the agent reads the core modules index
  Then the agent sees all available modules listed
  And each module has a one-line purpose description
  And modules are grouped by category (infrastructure, domain, presentation, etc.)
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward because:
- Single documentation file to create
- Information already exists in source file PURPOSE comments
- Clear structure based on existing module organization
- No integration or implementation needed

**Acceptance:**
- Agent can read one file to see all available modules
- Each module listed with category and purpose
- File format follows llms.txt conventions

---

### Story 2: Agent understands module APIs without reading source

```gherkin
Feature: Detailed module API reference
  As an agent composing an iw-cli script
  I want to see function signatures and return types for a module
  So that I can use the module correctly without reading source files

Scenario: Agent looks up Git operations API
  Given the agent needs to use GitAdapter in a script
  When the agent reads GitAdapter documentation
  Then the agent sees all available functions with signatures
  And each function has parameter types and return types
  And common usage patterns are documented
  And the agent can compose correct code without reading Git.scala
```

**Estimated Effort:** 3-4h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward because:
- Extract signatures from existing well-typed Scala code
- APIs are already stable and documented in source comments
- Focus on most commonly used modules first (Output, Git, GitWorktree, IssueId, Config)
- Can defer less-used modules to later

**Acceptance:**
- Agent can find function signatures without reading .scala files
- Type information is accurate and complete
- Usage examples show common patterns
- Documentation covers 5-7 most frequently used modules

---

### Story 3: Agent sees concrete usage examples for each module

```gherkin
Feature: Module usage examples
  As an agent creating a new iw-cli command
  I want to see working code examples for each module
  So that I can understand patterns and compose scripts faster

Scenario: Agent learns how to use Output module
  Given the agent needs to format command output
  When the agent reads Output module documentation
  Then the agent sees example code for info, error, success, warning
  And examples show proper error handling patterns
  And examples demonstrate section and keyValue formatting
  And the agent can copy-paste patterns into the script
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward because:
- Can extract examples from existing commands in `.iw/commands/`
- iw-command-creation skill already has good examples
- Focus on real-world usage patterns already proven in codebase

**Acceptance:**
- Each documented module has at least one concrete example
- Examples are copy-paste ready
- Examples show error handling with Either types
- Examples demonstrate integration between modules

## Architectural Sketch

**Purpose:** Define WHAT documentation files are needed and WHERE they go.

### For Story 1: Module Discovery

**Documentation Files Needed:**
- `modules-index.llms.txt` - Master index of all core modules

**Information to Include:**
- List of all modules grouped by category
- One-line purpose per module
- Quick navigation to detailed docs
- Module dependency relationships if relevant

**Placement:**
- `.iw/core/modules-index.llms.txt` (alongside core modules)

---

### For Story 2: API Reference

**Documentation Files Needed:**
- Per-module llms.txt files for commonly used modules
  - `Output.llms.txt`
  - `Git.llms.txt` (GitAdapter)
  - `GitWorktree.llms.txt` (GitWorktreeAdapter)
  - `IssueId.llms.txt`
  - `Config.llms.txt` and `ConfigRepository.llms.txt`
  - `Process.llms.txt` (ProcessAdapter)
  - `Prompt.llms.txt`

**Information to Include:**
- Function signatures with full type information
- Return types (especially Either[String, T] patterns)
- Parameter descriptions
- Brief description per function

**Placement:**
- `.iw/core/<ModuleName>.llms.txt` (alongside corresponding .scala file)

---

### For Story 3: Usage Examples

**Documentation Enhancement:**
- Add examples section to each per-module llms.txt file
- Extract from existing commands

**Information to Include:**
- Minimal working example per function/pattern
- Error handling examples
- Integration examples (e.g., Git + IssueId)
- Common patterns (reading config, checking branch, etc.)

**Placement:**
- Embedded in the per-module llms.txt files created in Story 2

## Technical Risks & Uncertainties

### CLARIFY: llms.txt format and conventions

We need to establish the exact format for llms.txt files.

**Questions to answer:**
1. Should we follow a specific llms.txt standard or create our own format optimized for iw-cli?
2. What's the ideal structure: markdown-like, structured sections, or freeform text?
3. Should we include type aliases and case class definitions from the modules?
4. How much detail: just signatures, or also implementation notes?

**Options:**
- **Option A**: Use standard llms.txt markdown format with clear sections (Overview, API, Examples)
  - Pros: Familiar format, good readability, works well with LLM parsing
  - Cons: May need to establish conventions since llms.txt is loosely specified
- **Option B**: Create structured custom format optimized for API docs (similar to API reference docs)
  - Pros: More precise, could be machine-parseable later
  - Cons: More work to design, less familiar
- **Option C**: Simple freeform text focused on LLM readability
  - Pros: Easiest to write, flexible
  - Cons: Less structured, harder to maintain consistency

**Impact:** Affects all three stories - format choice determines effort and maintainability.

---

### CLARIFY: Scope of module coverage

Which modules should be documented in detail vs just listed in the index?

**Questions to answer:**
1. Should we document all 69+ core modules or focus on the most commonly used?
2. How do we prioritize which modules get detailed docs first?
3. Should presentation layer modules (views, renderers) be included?
4. Should infrastructure adapters for issue trackers (GitHub, Linear, etc.) be fully documented?

**Options:**
- **Option A**: Document top 10-15 most commonly used modules in detail, list the rest in index
  - Pros: Focused effort, covers 80% of use cases, can expand later
  - Cons: Some modules left undocumented
- **Option B**: Document all modules comprehensively
  - Pros: Complete coverage
  - Cons: Significant effort (20-30h+), many modules rarely used in scripts
- **Option C**: Document only the "scriptable" modules (exclude server/presentation layer)
  - Pros: Focused on actual use case (script composition)
  - Cons: Artificial boundary, may exclude useful modules

**Impact:** Affects estimate significantly - Option A keeps us in Simple range, Option B would push to Feature.

---

### CLARIFY: Integration with iw-command-creation skill

How should the llms.txt files integrate with the existing skill?

**Questions to answer:**
1. Should the iw-command-creation skill be updated to reference llms.txt files?
2. Should llms.txt files be auto-generated or manually curated?
3. Should there be a command like `./iw docs Output` to read module docs?
4. How do we keep docs in sync with code changes?

**Options:**
- **Option A**: Manual curation, update skill to point agents to llms.txt
  - Pros: High quality, curated examples, focused content
  - Cons: Manual maintenance required
- **Option B**: Auto-generate from source comments and type signatures
  - Pros: Always in sync, less maintenance
  - Cons: Requires tooling, may need source comment improvements
- **Option C**: Hybrid - auto-generate structure, manually add examples
  - Pros: Balance of automation and quality
  - Cons: Most complex approach

**Impact:** Affects maintainability and long-term sustainability of docs.

## Total Estimates

**Story Breakdown:**
- Story 1 (Module discovery index): 2-3 hours
- Story 2 (API reference docs): 3-4 hours
- Story 3 (Usage examples): 2-3 hours

**Total Range:** 7-10 hours

**Confidence:** Medium

**Reasoning:**
- **Known factor**: Clear scope for Simple issue, existing source material (PURPOSE comments, type signatures)
- **Unknown factor**: llms.txt format conventions not yet established (CLARIFY marker)
- **Known factor**: Module coverage scope needs decision (CLARIFY marker) - estimate assumes Option A (top 10-15 modules)
- **Risk factor**: If we choose comprehensive coverage (all 69 modules), estimate would double to 15-20h

## Testing Approach

**Per Story Testing:**

Each story should have verification that agents can actually use the documentation.

**Story 1:**
- Unit: N/A (documentation file)
- Integration: Verify file is accessible from agent context
- E2E: Agent can list modules and identify correct module for a task

**Story 2:**
- Unit: Verify all documented signatures match actual source code
- Integration: Check that type information is accurate
- E2E: Agent can compose working code using only the llms.txt files

**Story 3:**
- Unit: All examples are syntactically correct Scala
- Integration: Examples can actually compile with core modules
- E2E: Agent can copy-paste examples and they work

**Test Data Strategy:**
- Use actual core module source as source of truth
- Extract real examples from existing commands in `.iw/commands/`
- Validate against working iw-cli installation

**Regression Coverage:**
- Existing iw-command-creation skill should still work
- Commands in `.iw/commands/` should still compile/run
- No changes to actual code, only adding documentation

## Deployment Considerations

### Distribution
llms.txt files should be included in iw-cli releases alongside core modules.

**File locations in release:**
- `.iw/core/modules-index.llms.txt`
- `.iw/core/<Module>.llms.txt` for documented modules

### Installation
Files are automatically present when iw-cli is installed or updated - no special deployment steps.

### Rollout Strategy
Can deploy incrementally:
1. Deploy Story 1 (index) first - provides immediate value
2. Add Story 2 docs module by module (start with Output, Git, IssueId)
3. Enhance with Story 3 examples as modules are documented

No feature flags needed - documentation is purely additive.

### Rollback Plan
If documentation is incorrect or confusing:
- Simply remove or update the problematic llms.txt file
- No code changes needed
- No risk to existing functionality

## Dependencies

### Prerequisites
- Access to current iw-cli core modules source
- Understanding of most common use cases for script composition
- Decision on llms.txt format (CLARIFY marker)

### Story Dependencies
- Story 2 depends on Story 1 for context (agents need to know modules exist before reading details)
- Story 3 builds on Story 2 (examples reference the API documented in Story 2)
- Stories could be delivered incrementally: 1 → 2 (per module) → 3 (per module)

### External Blockers
None - this is purely additive documentation work.

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: Module Discovery** - Establishes foundation, provides immediate value (agents can see what exists)
2. **Story 2: API Reference** - Builds on index, enables script composition without reading source
3. **Story 3: Usage Examples** - Polishes docs with practical patterns, makes composition even faster

**Iteration Plan:**

- **Iteration 1** (Story 1): Create master index - quick win, immediate value
- **Iteration 2** (Stories 2-3, partial): Document top 5 modules (Output, Git, GitWorktree, IssueId, Config) with examples
- **Iteration 3** (Stories 2-3, completion): Document remaining high-value modules, refine examples based on feedback

**Incremental Delivery:**
Each documented module provides value independently. Can ship:
- After Story 1: Agents know what modules exist
- After first 3 modules in Story 2: Agents can compose basic scripts
- After all stories: Complete documentation coverage

## Documentation Requirements

- [ ] llms.txt files themselves serve as the documentation
- [ ] Update iw-command-creation skill to reference llms.txt files (brief mention)
- [ ] Consider adding `./iw docs <module>` command for easy access (future enhancement, out of scope)
- [ ] No user-facing docs needed (target audience is agents, not humans)

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Resolve CLARIFY markers:
   - Decide on llms.txt format and structure
   - Define module coverage scope (top N vs all modules)
   - Choose manual vs auto-generated approach
2. After CLARIFY resolution, ready to implement Story 1
3. Can deliver incrementally: Story 1 → Story 2 (per module) → Story 3 (per module)

---

## Appendix: Core Modules by Category

**Most Commonly Used in Scripts (high priority for Story 2):**
- Output.scala - Console formatting
- Git.scala (GitAdapter) - Git operations
- GitWorktree.scala (GitWorktreeAdapter) - Worktree management
- IssueId.scala - Issue ID parsing
- Config.scala, ConfigRepository.scala - Configuration
- Process.scala (ProcessAdapter) - Shell execution
- Prompt.scala - Interactive input

**Domain Models (medium priority):**
- Issue.scala - Issue entity and tracker trait
- WorktreePath.scala - Path calculations
- Constants.scala - Shared constants
- IssueData.scala - Issue data structures

**Infrastructure Clients (lower priority for scripts):**
- GitHubClient.scala, LinearClient.scala, YouTrackClient.scala, GitLabClient.scala
- May be useful for advanced scripts but less commonly needed

**Services and Presentation (lowest priority):**
- Dashboard services, view renderers, cache services
- Rarely used directly in ad-hoc scripts
- Could be listed in index but skip detailed docs initially
