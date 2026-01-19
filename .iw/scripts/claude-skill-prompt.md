# iw-cli Skill Generation Instructions

Generate Claude Code skill files for the iw-cli tool in this project.

## Your Task

1. Read and understand:
   - `.iw/core/*.scala` - Core library modules (PURPOSE comments describe each)
   - `.iw/commands/*.scala` - Available commands (skip `*.hook-*.scala` files)
   - `.iw/config.conf` - Project configuration

2. Generate skill file(s) in `.claude/skills/` that enable agents to:
   - Discover and invoke iw commands
   - Compose ad-hoc scripts using core modules
   - Understand project context (tracker type, etc.)

## Skill File Format

Each skill needs a `SKILL.md` with YAML frontmatter:

```markdown
---
name: skill-name
description: |
  One-sentence summary of PROBLEMS this skill solves (not tools it provides).

  Use when:
  - [Natural language trigger phrases matching what users actually say]
  - [Problem statements, not command names]
---

# Skill Title

[Content here]
```

### Writing Good Descriptions

**Lead with problems, not tools.** Agents match skills based on user intent.

**BAD (tool-centric):**
```yaml
description: |
  Use iw-cli for worktree and issue management. Invoke when:
  - Fetching issue details (`./iw issue [issue-id]`)
  - Working on issues (`./iw start <issue-id>`)
```

**GOOD (problem-centric):**
```yaml
description: |
  Fetch issue descriptions and manage development worktrees.

  Use when:
  - User asks to read, plan, or discuss an issue (e.g., "read the issue", "let's plan IW-48")
  - Need issue description, title, status, or details from any tracker
  - Starting work on an issue (creates isolated worktree + tmux session)
  - Opening or removing existing worktrees
```

**Key principles:**
1. First line = problems solved, not tool name
2. "Use when" lists natural language phrases users actually say
3. Include example phrases in parentheses ("read the issue", "let's plan...")
4. Mention what data you can get (description, title, status) not just "details"
5. Put most common use case first (usually fetching issue info)

## Guidelines

### Command Discovery
- The `iw` script is in the project root and must be invoked as `./iw`, not `iw` (it's not in PATH)
- List available commands with their purpose
- Show how to get details: `./iw --describe <command>`
- Group related commands together

### Core Module Documentation
For each relevant module, document:
- Package and purpose (from PURPOSE comments)
- Key types and functions agents might use
- Keep it concise - agents can read full source if needed

### Composition Guide
Explain how to write ad-hoc scripts:
```bash
scala-cli run script.scala .iw/core/*.scala -- args
```

Include common patterns and examples.

IMPORTANT: In script examples, do NOT include `//> using scala` directives.
The Scala version is already defined in `.iw/core/project.scala` and gets
included automatically when running with `.iw/core/*.scala`.

### Splitting Decision
- If there are many commands, split into category-based skills
- Each skill should be focused and self-contained
- Categories might include: worktree-ops, issue-ops, server-ops, dev-tools
- For smaller projects, a single `iw-cli-ops` skill may suffice

## Output Location

Write skill files to `.claude/skills/`. Examples:
- Single skill: `.claude/skills/iw-cli-ops/SKILL.md`
- Multiple skills: `.claude/skills/iw-worktree-ops/SKILL.md`, etc.

## Important Notes

- Do NOT modify existing skills (like `iw-command-creation`)
- Focus on USING iw-cli, not creating new commands
- Include project-specific context from config.conf
- **Description quality is critical** - agents select skills based on matching user phrases
- Put "read/fetch issue description" use case prominently - it's the most common miss
- CRITICAL: Always use `./iw` not `iw` - the script is project-local, not in PATH
