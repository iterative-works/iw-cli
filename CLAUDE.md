# iw-cli Development Guide

## Project Overview

iw-cli is a project-local CLI tool for managing git worktrees and issue tracker integration. It's designed to be embedded in projects (like mill) rather than globally installed.

## Tech Stack

- **Language**: Scala 3
- **Build**: scala-cli
- **Bootstrap**: Shell script that downloads/runs the tool

## Architecture

```
iw-cli/
├── iw                        # dev bootstrap shell script
├── iw-run                    # main launcher
├── iw-bootstrap              # consumer bootstrap (downloads releases)
├── VERSION                   # current version
├── commands/                 # shared scala-cli command scripts
├── core/                     # core library (model, adapters, output, dashboard)
├── test/                     # BATS E2E tests
├── scripts/                  # release and packaging scripts
├── .iw/
│   ├── config.conf           # project config (tracker, etc.)
│   └── commands/             # project-specific commands (e.g., test.scala)
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

To regenerate the published skills (plus `llms.txt` and `docs/api/`) from
current source, use the project-local `build-iw-cli-skills` skill at
`.claude/skills/build-iw-cli-skills/`. It stages output under
`build/published-skills/` for copying into the dev-docs plugin, supports
incremental updates via a `.last-built` SHA marker, and is the canonical
way to keep the published skills in sync with iw-cli changes.

## Server Configuration

The dashboard server config is at `~/.local/share/iw/server/config.json`.

See [docs/server-config.md](docs/server-config.md) for full documentation on:
- Config file format and fields
- Host binding options
- Troubleshooting

## Testing

Run all tests (unit + E2E):
```bash
./iw ./test
```

Run only unit tests (Scala/munit):
```bash
./iw ./test unit
```

Run only E2E tests (BATS):
```bash
./iw ./test e2e
```
