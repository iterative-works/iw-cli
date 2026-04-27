# iw-cli Development Guide

## Project Overview

iw-cli is a project-local CLI tool for managing git worktrees and issue tracker integration. It's designed to be embedded in projects (like mill) rather than globally installed.

## Tech Stack

- **Language**: Scala 3
- **Build**: scala-cli (core, commands) + Mill 1.1.5 (dashboard)
- **Frontend toolchain**: Node 20, Yarn 4 via Corepack, Vite 8, Tailwind v4
- **Bootstrap**: Shell script that downloads/runs the tool

## Architecture

```
iw-cli/
├── iw                        # dev bootstrap shell script
├── iw-run                    # main launcher
├── iw-bootstrap              # consumer bootstrap (downloads releases)
├── VERSION                   # current version
├── build.mill                # Mill build definition (dashboard + core)
├── mill                      # Mill wrapper script
├── .mill-version             # pinned Mill version
├── commands/                 # shared scala-cli command scripts
├── core/                     # core library (model, adapters, output)
├── dashboard/
│   ├── jvm/src/              # dashboard server (Scala)
│   ├── jvm/test/src/         # dashboard unit tests
│   ├── jvm/itest/src/        # dashboard integration tests
│   ├── jvm/resources/        # bundled frontend assets
│   └── frontend/             # Vite/Tailwind frontend sources
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

## Dashboard development

`./iw dashboard` queries Mill for jar paths via `./mill show` (jars live under
`out/`, not `build/`). The active dev loop bypasses `./iw dashboard` entirely
and uses two terminals:

1. Start the Vite dev server:
   ```bash
   cd dashboard/frontend && ./start-dev.sh
   ```
   Requires `WEBAWESOME_NPM_TOKEN` in env and Node 20+ with `corepack enable`.

2. Start the dashboard with dev-mode asset routing:
   ```bash
   VITE_DEV_URL=http://localhost:5173 ./iw dashboard --dev
   ```
   Both `--dev` flag AND non-empty `VITE_DEV_URL` are required to activate
   dev routing. Either alone has no effect (with a warning for `--dev` alone).

To run dashboard server tests:
```bash
./mill dashboard.test          # unit tests
./mill dashboard.itest.testForked  # integration tests
```

Two build tools exist because scala-cli has no first-class JS story; Mill
handles the dashboard module which includes Vite frontend bundling.

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
