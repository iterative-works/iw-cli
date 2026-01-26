# Phase 2 Context: Create llms.txt Documentation

**Issue:** IW-126
**Phase:** 2 of 3
**Stories Covered:** Story 2 (module discovery), Story 3 (API documentation)

## Goals

Create structured, LLM-readable documentation for the public API modules established in Phase 1. This enables agents to discover and use iw-cli modules without reading source code.

## Scope

### In Scope
- Create `.iw/llms.txt` index file following [llms.txt standard](https://llmstxt.org)
- Create per-module `.md` files in `.iw/docs/` directory
- Document public API from: `model/`, `adapters/`, `output/`
- Extract examples from existing `.iw/commands/` scripts

### Out of Scope
- `dashboard/` directory (internal API per CLAUDE.md)
- Auto-generation tooling (documentation is manually curated)
- Skill integration (Phase 3)

## Dependencies

### From Phase 1
- Directory structure: `model/`, `adapters/`, `output/`, `dashboard/`
- CLAUDE.md guidelines defining public vs internal boundary
- Import patterns: `iw.core.model.*`, `iw.core.adapters.*`, `iw.core.output.*`

## Technical Approach

### 1. llms.txt Index File (`.iw/llms.txt`)

Follow standard format:
```markdown
# iw-cli Core Modules

> Functional Scala modules for CLI automation...

## Model (Pure Types)
- [IssueId](docs/IssueId.md): Issue ID parsing and validation
...

## Adapters (I/O)
- [Git](docs/Git.md): Git operations adapter
...

## Output (CLI)
- [Output](docs/Output.md): Console formatting
...
```

### 2. Per-Module Documentation (`.iw/docs/*.md`)

For each public module:
```markdown
# ModuleName

> One-line description.

## Import
\`\`\`scala
import iw.core.{layer}.*
\`\`\`

## API

### functionName(param: Type): ReturnType
Description.

## Examples
\`\`\`scala
// From actual command usage
\`\`\`
```

### 3. Documentation Priorities

**High priority** (commonly used in commands):
- Output (every command uses it)
- Git, GitWorktree (worktree operations)
- IssueId, Issue (issue tracking)
- Config, ConfigRepository (configuration)
- Process, CommandRunner (shell execution)

**Medium priority** (specialized use):
- API clients (GitHubClient, LinearClient, etc.)
- Prompt (interactive input)
- Tmux (session management)

**Lower priority** (less commonly used directly):
- Pure value types (WorkflowProgress, PhaseInfo, etc.)
- Formatting helpers (TimestampFormatter, MarkdownRenderer)

## Files to Create

```
.iw/
├── llms.txt                    # Index file
└── docs/                       # Per-module documentation
    ├── Output.md
    ├── Git.md
    ├── GitWorktree.md
    ├── IssueId.md
    ├── Issue.md
    ├── Config.md
    ├── ConfigRepository.md
    ├── Process.md
    ├── CommandRunner.md
    ├── Prompt.md
    ├── Log.md
    ├── Tmux.md
    ├── GitHubClient.md
    ├── LinearClient.md
    ├── GitLabClient.md
    └── YouTrackClient.md
```

## Testing Strategy

1. **Signature accuracy**: Documented signatures must match source
2. **Example validity**: Examples must be syntactically correct Scala
3. **Link validity**: All links in llms.txt must resolve
4. **Completeness**: All public modules should be documented

## Acceptance Criteria

- [ ] `.iw/llms.txt` exists and follows standard format
- [ ] Each public module has corresponding `.md` documentation
- [ ] API signatures are accurate and complete
- [ ] Examples are extracted from real command usage
- [ ] Internal (`dashboard/`) modules are NOT documented

## Notes

- Focus on commonly-used APIs first
- Extract real examples from `.iw/commands/` for authenticity
- Keep documentation concise - agents prefer dense, accurate info
- Documentation maintenance is part of normal code review process
