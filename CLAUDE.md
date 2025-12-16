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

This project uses Linear for issue tracking. Issues are in the IWLE team.

## Testing

Run unit tests (from project root):
```bash
scala-cli test .iw/core/test/*.scala .iw/core/project.scala
```

Run integration tests:
```bash
bats .iw/test/
```

Run all tests:
```bash
scala-cli test .iw/core/test/*.scala .iw/core/project.scala && bats .iw/test/
```
