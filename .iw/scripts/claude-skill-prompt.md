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
  Brief description of what this skill covers.
  IMPORTANT: Include trigger phrases - when should an agent use this skill?
---

# Skill Title

[Content here]
```

## Guidelines

### Command Discovery
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
- Keep descriptions actionable for agents
