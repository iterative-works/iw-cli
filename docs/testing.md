<!-- PURPOSE: Test pyramid, runners, and coverage workflow for iw-cli. -->
<!-- PURPOSE: Pointer to the in-flight test-rebalance plan; describes current state, not target state. -->

# Testing

iw-cli is moving to a three-tier test pyramid. The end state is described in
[docs/plans/2026-06-03-test-rebalance.md](plans/2026-06-03-test-rebalance.md);
this document describes the runner and coverage workflow as they exist today.

## Tiers

| Tier | Where | Runner | Purpose |
|---|---|---|---|
| Unit | `core/test/*.scala`, `dashboard/jvm/test/src/` | Mill + munit | Pure logic, fast, instrumented for coverage |
| Tool contract | `test/contract/` (planned) | BATS | Pin assumptions about external tools (git, gh, scala-cli, etc.) |
| E2E smoke | `test/*.bats` | BATS via `./iw ./test e2e` | Prove iw-run → scala-cli → core wiring works end-to-end |

The current BATS suite is broader than the target end state — see the plan
for the reduction work.

## Running

```bash
./iw ./test            # all tiers
./iw ./test unit       # core.test + dashboard.test via Mill
./iw ./test itest      # dashboard integration tests via Mill (testForked)
./iw ./test compile    # command compilation check
./iw ./test e2e        # BATS only
```

Direct Mill invocations (useful for iteration):

```bash
./mill core.test                     # 95+ munit tests in core/test/
./mill dashboard.test                # dashboard server unit tests
./mill dashboard.itest.testForked    # dashboard integration tests
```

## Coverage

Scoverage (`2.1.0`) is wired on `core` and `dashboard`. Coverage is
baseline-only — there are no thresholds or gating rules. Run it locally with:

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

## CI

`.github/workflows/ci.yml` runs `./iw ./test` followed by coverage steps:

1. `Run coverage` — generates per-module XML + aggregated HTML (only on green tests).
2. `Coverage summary` — parses per-module XML, writes a markdown table to the run summary.
3. `Upload coverage report` — uploads the `coverage-report` artifact (14-day retention).

When adding a new instrumented module, update the module list in both the
`Coverage summary` shell step and the `Upload coverage report` paths.
