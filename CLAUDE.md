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

## Claude Code Skills

iw-cli skills (`iw-cli-ops`, `iw-command-creation`) are maintained in the
dev-docs repository (`iterative-works/dev-docs`) and distributed via its
Claude Code plugin. Skills should be updated in dev-docs when releasing
new iw-cli versions that add or change commands.

## Server Configuration

The dashboard server config is at `~/.local/share/iw/server/config.json`.

See [docs/server-config.md](docs/server-config.md) for full documentation on:
- Config file format and fields
- Host binding options
- Troubleshooting

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
