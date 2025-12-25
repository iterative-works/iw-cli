# iw-cli Development Guide

## Project Overview

iw-cli is a project-local CLI tool for managing git worktrees and issue tracker integration. It's designed to be embedded in projects (like mill) rather than globally installed.

## Tech Stack

- **Language**: Scala 3
- **Build**: scala-cli
- **Bootstrap**: Shell script that downloads/runs the tool

## Architecture

```
project/
├── iw                        # bootstrap shell script
├── .iw/
│   ├── config.yaml           # project config (tracker, etc.)
│   ├── commands/             # local scala-cli scripts
│   └── cache/                # downloaded tool jar
```

## Development Principles

- Follow functional programming principles (immutable values, pure functions)
- Keep effects at the edges (Functional Core, Imperative Shell)
- Use TDD for all new features
- Prefer simple solutions over clever ones

## Issue Tracking

This project uses GitHub for issue tracking: `iterative-works/iw-cli`

## Claude Code Integration

iw-cli includes Claude Code skill generation for agent awareness.

### Generating Skills

```bash
./iw claude-sync          # Generate skill files in .claude/skills/
./iw claude-sync --force  # Regenerate existing skills
```

This uses Claude CLI to analyze the codebase and generate skill files that teach agents:
- What commands are available and how to use them
- How to compose ad-hoc scripts from core modules
- Project-specific context (tracker type, issue ID format)

### Updating Skills

Run `./iw claude-sync --force` when:
- Adding new commands to `.iw/commands/`
- Changing core module APIs
- Updating project configuration

### Skill Location

Generated skills go to `.claude/skills/iw-cli-ops/`. These are separate from the
`iw-command-creation` skill which teaches how to create new iw commands.

## Testing

Run all tests (unit + E2E):
```bash
./iw test
```

Run only unit tests (Scala/munit):
```bash
./iw test unit
```

Run only E2E tests (BATS):
```bash
./iw test e2e
```
