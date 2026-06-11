<!-- PURPOSE: Test pyramid, runners, and coverage workflow for iw-cli. -->
<!-- PURPOSE: Describes the end-state model (three tiers + harness pattern). -->

# Testing

iw-cli uses a three-tier test pyramid: **unit** (broad, in-process, instrumented),
**tool contract** (narrow, external-tool assumptions), and **E2E smoke**
(one round-trip per command). Anything that does not fit one of these three
shapes is a deletion candidate.

The rebalance from a BATS-dominant suite to this shape was tracked in
[docs/plans/2026-06-03-test-rebalance.md](plans/2026-06-03-test-rebalance.md).
This document describes the resulting model and the workflow for adding
tests today.

## Tiers

| Tier | Where | Runner | Purpose |
|---|---|---|---|
| Unit | `core/test/*.scala`, `dashboard/jvm/test/src/` | Mill + munit | Pure logic + command harness tests against fakes |
| Tool contract | `test/contract/*.bats` | BATS (gated) | Pin assumptions about git, gh, glab, scala-cli, mill, tmux |
| E2E smoke | `test/*.bats` | BATS via `./iw ./test e2e` | Prove iw-run → scala-cli → core wiring works end-to-end |

### Unit

Two kinds of unit tests live in `core/test/`:

- **Pure-logic tests** — `*Test.scala`. Cover `core/model/`, `core/output/`,
  and individual adapter helpers in `core/adapters/`. No I/O, no fakes
  required beyond ordinary value construction.
- **Command harness tests** — `*HarnessTest.scala`. Exercise a command's
  body (in `core/commands/<Name>.scala`) against `FakeCommandEnv`, a
  capability-trait composition of fake console, filesystem, git, tracker,
  process, server, tmux, prompt, etc. Scenarios that used to require
  spinning up scala-cli + a real git tree now run in-process in
  milliseconds.

The command-harness pattern is described in detail below.

### Tool contract

`test/contract/` pins our world-assumptions about external tools. There is
one file per tool (`git_adapter_contract.bats`, `gh_adapter_contract.bats`,
`glab_adapter_contract.bats`, `scala_cli_contract.bats`,
`mill_contract.bats`, `tmux_adapter_contract.bats`). These tests assert
that the CLI surfaces we depend on (flag names, JSON shapes,
short-revision width, etc.) still hold.

Gating:

- Default run is fast (~8 s total); skips network-bound and PTY-bound cases.
- Live GitHub calls run only when `gh auth status` succeeds locally or in CI.
- Live GitLab calls run only when `IW_CONTRACT_GLAB_PROJECT` is set.
- Tmux PTY round-trips run only when `IW_CONTRACT_TMUX=1` is set.
- CI runs the suite nightly and on PRs labeled `contract`.

### E2E smoke

`test/*.bats` files now contain one or two round-trip scenarios per command
(happy path + one error or wiring case). They prove the launcher
(`iw-run`) finds the script, scala-cli compiles + runs it against a real
classpath, and `LiveCommandEnv` wires the real adapters. Detailed
per-scenario coverage lives in the harness tests above.

All BATS files export `IW_SERVER_DISABLED=1` in `setup()`. `ServerClient`
checks this env var on every server call and short-circuits — this keeps
tests from contacting a real dashboard daemon running on the developer's
machine. `dashboard.test` sets the same flag via `forkEnv` in
`build.mill`.

## Command harness pattern

Each command in `commands/<name>.scala` is a 7-line shim:

```scala
// PURPOSE: <one-line summary>
// USAGE: iw <name> <args>

import iw.core.commands.{LiveCommandEnv, <Name>}

@main def <name>(args: String*): Unit =
  sys.exit(<Name>.run(args, LiveCommandEnv.default).exitCode)
```

The body lives in `core/commands/<Name>.scala` as
`run(args: Seq[String], env: CommandEnv): CommandResult`. `CommandEnv`
composes small capability traits — `Console`, `FileSystem`, `GitOps`,
`Process`, `Clock`, `TrackerOps`, `HookOps`, `ServerOps`, `ServerConfigOps`,
`DashboardLifecycle`, `ProcessLifecycle`, `TmuxOps`, `WorktreeOps`,
`StateReader`, `Prompt`, `EnvVars`, `ConfigOps`, `ReviewStateOps`, `Stdin`.
Each command threads `for`-comprehensions over the capabilities it
actually touches.

`LiveCommandEnv` delegates to the existing adapters. `FakeCommandEnv`
(at `core/test/fixtures/FakeCommandEnv.scala`) gives every capability a
deterministic in-memory fake with seeding/inspection helpers. Harness
tests assert on console output, file writes, git command lists, tracker
call lists, etc.

When adding or extending a command:

1. Identify which capabilities the new logic touches.
2. Extend `CommandEnv` traits / add a new trait under `core/commands/`.
   Update `LiveCommandEnv`. Add the corresponding `Fake*` impl in
   `FakeCommandEnv.scala`.
3. Move command body to `core/commands/<Name>.scala` returning
   `CommandResult`.
4. Shrink `commands/<name>.scala` to the 7-line shim.
5. Write `<Name>HarnessTest.scala` covering scenarios.
6. Keep the BATS file at 1–2 round-trip smoke tests.

## Running

```bash
./iw ./test            # unit + e2e
./iw ./test unit       # core.test + dashboard.test via Mill
./iw ./test itest      # dashboard integration tests via Mill (testForked)
./iw ./test compile    # command compilation check
./iw ./test e2e        # BATS only
./iw ./test contract   # tool contract suite
```

Direct Mill invocations (useful for iteration):

```bash
./mill core.test                                  # ~190 munit tests in core/test/
./mill core.test.testOnly '*HarnessTest'          # only the command harness tests
./mill dashboard.test                             # dashboard server unit tests
./mill dashboard.itest.testForked                 # dashboard integration tests
```

For a single munit test class:

```bash
./mill core.test.testOnly 'iw.core.test.PhaseStartHarnessTest'
```

## Coverage

Scoverage (`2.1.0`) is wired on `core` and `dashboard`. Coverage is
baseline-only — there are no thresholds or gating rules.

```bash
./mill __.scoverage.xmlReport      # per-module scoverage.xml
./mill scoverage.htmlReportAll     # cross-module HTML at out/scoverage/htmlReportAll.dest/
```

Per-module XML reports land at:

- `out/core/scoverage/xmlReport.dest/scoverage.xml`
- `out/dashboard/scoverage/xmlReport.dest/scoverage.xml`

The aggregated HTML report lives at
`out/scoverage/htmlReportAll.dest/index.html`.

### Baseline (2026-06-03)

| Module | Statements covered | Statements total | Coverage % |
|--------|-------------------|-----------------|------------|
| `core` | 4876 | 6297 | 77.43% |
| `dashboard` | 3392 | 4449 | 76.24% |

The number has shifted upward as Phase 4 migrated command bodies into
`core/commands/` (now reachable by scoverage). Re-run the report locally
or check the CI artifact for the current figure.

## CI

`.github/workflows/ci.yml` runs `./iw ./test` followed by coverage steps:

1. `Run coverage` — generates per-module XML + aggregated HTML (only on green tests).
2. `Coverage summary` — parses per-module XML, writes a markdown table to the run summary.
3. `Upload coverage report` — uploads the `coverage-report` artifact (14-day retention).

A separate `contract` job runs the tool contract suite on a nightly cron and on
PRs labeled `contract`.

When adding a new instrumented module, update the module list in both the
`Coverage summary` shell step and the `Upload coverage report` paths.
