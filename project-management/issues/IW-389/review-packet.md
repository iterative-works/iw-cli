---
generated_from: 0d85c95a951ff4043eee3e621f73504d031ca3be
generated_at: 2026-06-24T13:48:00Z
branch: IW-389
issue_id: IW-389
phase: N/A
files_analyzed:
  - core/adapters/ForgejoClient.scala
  - core/commands/CommandEnv.scala
  - core/commands/Doctor.scala
  - core/commands/ForgeConfigResolver.scala
  - core/commands/Init.scala
  - core/commands/Issue.scala
  - core/commands/LiveCommandEnv.scala
  - core/commands/PhaseAdvance.scala
  - core/commands/PhaseMerge.scala
  - core/commands/PhasePr.scala
  - core/model/CIChecks.scala
  - core/model/Config.scala
  - core/model/Constants.scala
  - core/model/ForgeConfig.scala
  - core/model/ForgePullRequest.scala
  - core/model/ForgeType.scala
  - core/model/ForgejoUrl.scala
  - core/model/PhaseMerge.scala
  - core/model/ProjectContext.scala
  - core/model/RepoUrlBuilder.scala
  - core/model/TrackerUrlBuilder.scala
  - core/test/CIChecksTest.scala
  - core/test/ConfigTest.scala
  - core/test/ConstantsTest.scala
  - core/test/DoctorHarnessTest.scala
  - core/test/fixtures/FakeCommandEnv.scala
  - core/test/ForgejoClientTest.scala
  - core/test/ForgeTypeTest.scala
  - core/test/InitHarnessTest.scala
  - core/test/IssueHarnessTest.scala
  - core/test/PhaseMergeHarnessTest.scala
  - core/test/PhasePrHarnessTest.scala
  - core/test/RepoUrlBuilderTest.scala
  - test/forgejo-issue.bats
  - test/forgejo-phase-pr.bats
---

# Review Packet: IW-389 — Support Forgejo issue tracker

## Goals

This feature adds Forgejo as a fully-supported forge and issue tracker, reaching parity with the existing GitHub and GitLab integrations.

Key objectives:

- `./iw issue <id>` fetches and displays issues from a Forgejo-configured project using direct HTTP (no CLI dependency)
- `./iw init --tracker=forgejo` configures a project for Forgejo, collecting `repository` + required `baseUrl`; auto-detects `codeberg.org` remotes
- `./iw phase-pr` and `./iw phase-merge` create/merge pull requests and poll CI check statuses via the Forgejo REST API
- `tracker.type = forgejo` round-trips correctly through `.iw/config.conf`
- `./iw doctor` reports correct status for a Forgejo-configured project, including Forgejo Actions CI detection

---

## Scenarios

- [ ] `./iw issue <id>` fetches and displays an issue from a Forgejo-configured project
- [ ] `./iw issue <id>` exits 1 with `FORGEJO_API_TOKEN` in the error message when the env var is unset
- [ ] `./iw issue <id>` exits 1 with a clear error when `repository` is missing from config
- [ ] `./iw issue <id>` exits 1 with a clear error when `baseUrl` is missing from config
- [ ] `./iw issue <id>` with a bare numeric arg composes `PREFIX-N` (teamPrefix parity with GitHub/GitLab)
- [ ] `./iw init --tracker=forgejo` configures the project with `repository`, `baseUrl`, and optional `teamPrefix`
- [ ] `./iw init` menu shows option `5. Forgejo` and accepts `"5"` or `"forgejo"` as input
- [ ] `./iw init` on a `codeberg.org` remote auto-detects Forgejo tracker type
- [ ] `./iw init` next-steps output includes `export FORGEJO_API_TOKEN=...`
- [ ] `tracker.type = forgejo` serialises and deserialises correctly (HOCON round-trip)
- [ ] `./iw doctor` reports "Forgejo Actions" as the CI platform for a Forgejo project
- [ ] `./iw doctor --fix` CI check looks for `.forgejo/workflows/ci.yml` first, then `.github/workflows/ci.yml` as compatibility fallback
- [ ] `./iw phase-pr` creates a pull request via the Forgejo API when `FORGEJO_API_TOKEN` is set
- [ ] `./iw phase-pr` exits 1 with `FORGEJO_API_TOKEN` in the error message when the env var is unset
- [ ] `./iw phase-merge` squash-merges and deletes the branch via the Forgejo API
- [ ] `./iw phase-merge` polls Forgejo commit status (`/api/v1/repos/{owner}/{repo}/commits/{sha}/status`) for CI checks
- [ ] `./iw phase-advance` skips CLI-prerequisite guard for Forgejo (no CLI binary)
- [ ] `ForgeType.resolve` gives tracker-type precedence for Forgejo so self-hosted instances are not misidentified as GitLab

---

## Entry Points

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `core/adapters/ForgejoClient.scala` | `ForgejoClient` | New HTTP adapter — the substantive I/O core of the feature |
| `core/commands/ForgeConfigResolver.scala` | `ForgeConfigResolver.resolve()` | New resolver: resolves `baseUrl` + token for forge operations; called by `PhasePr`/`PhaseMerge` |
| `core/commands/Issue.scala` | `fetchIssue()` — Forgejo arm | Dispatch into `fetchForgejoIssue`; shows token + config resolution pattern |
| `core/commands/PhasePr.scala` | `run()` — Forgejo path | PR creation entry point; wires `ForgeConfigResolver` into the forge dispatch |
| `core/model/ForgeType.scala` | `ForgeType.resolve()` | Tracker-type-wins precedence logic; key design decision for self-hosted Forgejo |
| `core/model/ForgeConfig.scala` | `ForgeConfig` | New carrier type threading `baseUrl`/`token` into the four `TrackerOps` forge methods |
| `core/commands/Init.scala` | `collectTrackerDetails()` — Forgejo arm | Init prompts for `repository`/`baseUrl`/`teamPrefix` + codeberg auto-detect |
| `core/model/CIChecks.scala` | Forgejo arm | `.forgejo/workflows/ci.yml` → `.github/workflows/ci.yml` fallback detection |

---

## Diagrams

### Layer Dependency (unchanged architecture, new arms added)

```
model/          (pure domain — no dependencies)
  └── Config           IssueTrackerType.Forgejo enum variant
  └── Constants        EnvVars.ForgejoApiToken, TrackerTypeValues.Forgejo
  └── ForgeConfig      NEW — baseUrl/token carrier for forge ops
  └── ForgePullRequest NEW — PR number + URL + head SHA
  └── ForgejoUrl       NEW — pure PR URL parsers
  └── ForgeType        Forgejo case + resolve() tracker-type-wins logic
  └── CIChecks         Forgejo Actions detection arm

adapters/       (I/O — depends on model)
  └── ForgejoClient    NEW — full HTTP adapter (issue, PR, merge, CI status)

commands/       (application — depends on model + adapters)
  └── ForgeConfigResolver  NEW — resolves ForgeConfig from config + env
  └── CommandEnv           TrackerOps.fetchForgejoIssue/createForgejoIssue + ForgeConfig param on forge methods
  └── LiveCommandEnv       Forgejo arms in LiveTrackerOps
  └── Issue                Forgejo dispatch + forgejoToken helper
  └── Init                 Forgejo menu, collect details, next-steps
  └── Doctor               ciPlatform Forgejo arm
  └── PhasePr              ForgeConfigResolver wired into Forgejo path
  └── PhaseMerge           ForgeConfigResolver + fetchPrHeadSha for CI polling
  └── PhaseAdvance         Optional cliTool guard (skips for Forgejo)
```

### Forgejo Issue Fetch Flow

```
./iw issue <id>
  └── Issue.run()
        └── Issue.fetchIssue()
              ├── forgejoToken(env)          reads FORGEJO_API_TOKEN
              ├── config.repository          from .iw/config.conf
              ├── config.trackerBaseUrl      from .iw/config.conf
              └── env.tracker.fetchForgejoIssue(issueId, repo, baseUrl, token)
                    └── LiveTrackerOps → ForgejoClient.fetchIssue()
                          └── GET {baseUrl}/api/v1/repos/{owner}/{repo}/issues/{N}
                                └── parseFetchIssueResponse() → Issue
```

### Forgejo PR Creation Flow

```
./iw phase-pr --title "..."
  └── PhasePr.run()
        └── ForgeType.resolve(remoteOpt, IssueTrackerType.Forgejo) → ForgeType.Forgejo
        └── ForgeConfigResolver.resolve(ForgeType.Forgejo, config, env)
              ├── config.trackerBaseUrl      required
              └── FORGEJO_API_TOKEN          required
        └── env.tracker.createPullRequest(forge=Forgejo, ..., forgeConfig)
              └── LiveTrackerOps → ForgejoClient.createPullRequest()
                    └── POST {baseUrl}/api/v1/repos/{owner}/{repo}/pulls
                          └── parseCreatePullRequestResponse() → ForgePullRequest
```

### Forgejo CI Status Polling Flow (phase-merge)

```
./iw phase-merge
  └── ForgeConfigResolver.resolve() → ForgeConfig(baseUrl, token)
  └── env.tracker.fetchCheckStatuses(forge=Forgejo, prNumber, repo, ..., forgeConfig)
        └── LiveTrackerOps:
              └── ForgejoClient.fetchPrHeadSha(repo, prNumber, baseUrl, token)
                    └── GET /api/v1/repos/{repo}/pulls/{N}  → head.sha
              └── ForgejoClient.fetchCheckStatuses(repo, sha, baseUrl, token)
                    └── GET /api/v1/repos/{repo}/commits/{sha}/status
                          └── parseCommitStatusResponse() → List[CICheckResult]
```

---

## Test Summary

### Unit Tests

| File | Type | Forgejo Cases | Notes |
|------|------|---------------|-------|
| `ForgejoClientTest.scala` | Unit | ~81 test lines / 33+ cases | URL builders, fetch/create/PR/merge/CI status, token validation, error mapping. All network-free via `SyncBackendStub`. |
| `ConfigTest.scala` | Unit | ~10 cases | HOCON round-trip, baseUrl scheme rejection, repository format, teamIdentifier |
| `ConstantsTest.scala` | Unit | 1 case | `TrackerTypeValues.Forgejo` constant value |
| `RepoUrlBuilderTest.scala` | Unit | 3 cases | URL build with/without baseUrl, missing repo |
| `ForgeTypeTest.scala` | Unit | ~8 cases | `resolve()` precedence, codeberg host detection, GitHub/GitLab regression |
| `CIChecksTest.scala` | Unit | 4 cases | `.forgejo` primary, `.github` compat fallback, neither→Error, both→prefers `.forgejo` |

### Harness Tests (command-level, via FakeCommandEnv)

| File | Type | Forgejo Cases | Notes |
|------|------|---------------|-------|
| `IssueHarnessTest.scala` | Harness | 10 cases | Missing token/baseUrl/repository, fetch happy path, numeric-prefix composition, create happy path, missing token/baseUrl/repository |
| `InitHarnessTest.scala` | Harness | 5 cases | All-args no-prompt, base-url prompt, next-steps token, codeberg auto-detect, menu option 5 |
| `DoctorHarnessTest.scala` | Harness | 2 cases | Valid Forgejo config, `--fix` "Forgejo Actions" CI label |
| `PhasePrHarnessTest.scala` | Harness | ~6 cases | Forgejo dispatch, missing token, missing baseUrl |
| `PhaseMergeHarnessTest.scala` | Harness | ~6 cases | Forgejo dispatch, CI status polling, missing token |

### E2E Smoke Tests (BATS)

| File | Scenario | Coverage |
|------|----------|---------|
| `test/forgejo-issue.bats` | `iw issue 1` with Forgejo config, no token | Proves config-parse → dispatch → error wiring (hermetic, no network) |
| `test/forgejo-phase-pr.bats` | `iw phase-pr --title "..."` with Forgejo config, no token | Proves `ForgeType.resolve` → Forgejo forge → `ForgeConfigResolver` error wiring (hermetic) |

All BATS tests export `IW_SERVER_DISABLED=1`. Both smoke tests are no-token hermetic variants; HTTP/JSON behavior is fully covered by the 33+ `ForgejoClientTest` unit tests.

---

## Files Changed

### New Files

| File | Purpose |
|------|---------|
| `core/adapters/ForgejoClient.scala` | Full HTTP adapter — issue CRUD, PR creation, squash-merge, CI status, token validation |
| `core/commands/ForgeConfigResolver.scala` | Resolves `ForgeConfig` (baseUrl + token) from config + env; shared by PhasePr/PhaseMerge |
| `core/model/ForgeConfig.scala` | Pure carrier type for optional baseUrl/token passed to forge `TrackerOps` methods |
| `core/model/ForgePullRequest.scala` | Pure data type: PR number, HTML URL, head SHA |
| `core/model/ForgejoUrl.scala` | Pure URL parsers: `extractPullRequestIndex`, `extractRepositoryFromPrUrl` |
| `core/test/ForgejoClientTest.scala` | 33+ unit tests for `ForgejoClient` using `SyncBackendStub` |
| `test/forgejo-issue.bats` | E2E smoke: `iw issue` token-error wiring for Forgejo |
| `test/forgejo-phase-pr.bats` | E2E smoke: `iw phase-pr` token-error wiring for Forgejo |

### Modified Files

<details>
<summary>Domain / model layer (5 files)</summary>

- `core/model/Config.scala` — `IssueTrackerType.Forgejo` enum case; HOCON serialize/deserialize arms; `TrackerDetector.suggestTracker` codeberg.org detection; `teamIdentifier` Forgejo arm
- `core/model/Constants.scala` — `TrackerTypeValues.Forgejo`, `EnvVars.ForgejoApiToken`
- `core/model/ForgeType.scala` — `Forgejo` case; `cliTool`/`installUrl` as `Option[String]`; `fromHost("codeberg.org")`; `resolve()` tracker-type-wins precedence
- `core/model/CIChecks.scala` — Forgejo arm checking `.forgejo/workflows/ci.yml` with `.github/workflows/ci.yml` fallback
- `core/model/RepoUrlBuilder.scala` — Forgejo URL build arm (`baseUrl/repo`)
- `core/model/TrackerUrlBuilder.scala` — Forgejo issues URL arm (`baseUrl/repo/issues`)
- `core/model/PhaseMerge.scala` — `forgejoPrPattern` + Forgejo arm in `extractPrNumber`
- `core/model/ProjectContext.scala` — Forgejo arms in exhaustive matches

</details>

<details>
<summary>Application / command layer (6 files)</summary>

- `core/commands/CommandEnv.scala` — `TrackerOps.fetchForgejoIssue` / `createForgejoIssue` methods; `ForgeConfig` additive parameter on four forge methods
- `core/commands/LiveCommandEnv.scala` — `LiveTrackerOps` Forgejo implementations; `mergeForgejoSquash` helper
- `core/commands/Issue.scala` — `forgejoToken()` helper; `IssueTrackerType.Forgejo` fetch + create dispatch arms; Forgejo added to `resolveIssueId` teamPrefix arm
- `core/commands/Init.scala` — Menu option 5 (Forgejo); `--tracker=forgejo` flag handling; `collectTrackerDetails` Forgejo arm (repository + baseUrl + teamPrefix); `printNextSteps` FORGEJO_API_TOKEN prompt
- `core/commands/Doctor.scala` — `ciPlatform` Forgejo arm (`"Forgejo Actions"`); match made exhaustive
- `core/commands/PhasePr.scala` — `ForgeConfigResolver` wired into `Resolved` for-comprehension
- `core/commands/PhaseMerge.scala` — `ForgeConfigResolver` wired; `fetchPrHeadSha` used for CI polling
- `core/commands/PhaseAdvance.scala` — `Option[String]` cliTool guard (skips for CLI-less Forgejo); Forgejo arm in `checkMerged`

</details>

<details>
<summary>Test files (8 files modified)</summary>

- `core/test/ConfigTest.scala` — Forgejo HOCON round-trip, baseUrl/repository validation, teamIdentifier
- `core/test/ConstantsTest.scala` — Forgejo constant
- `core/test/RepoUrlBuilderTest.scala` — Forgejo URL cases
- `core/test/ForgeTypeTest.scala` — Forgejo resolution precedence + codeberg host + regressions
- `core/test/CIChecksTest.scala` — 4 Forgejo CI workflow detection cases
- `core/test/IssueHarnessTest.scala` — 10 Forgejo dispatch cases
- `core/test/InitHarnessTest.scala` — 5 Forgejo init cases
- `core/test/DoctorHarnessTest.scala` — 2 Forgejo doctor cases
- `core/test/PhasePrHarnessTest.scala` — Forgejo PR creation harness cases
- `core/test/PhaseMergeHarnessTest.scala` — Forgejo merge + CI polling harness cases
- `core/test/fixtures/FakeCommandEnv.scala` — `FakeTracker` recorders for `fetchForgejoIssue`, `createForgejoIssue`, `CheckStatusCall`

</details>

### Deferred / Out of Scope (documented for follow-up)

The following were identified during code review but deliberately deferred as they affect the whole tracker family, not Forgejo alone:

- `CreatedIssue` lives in `LinearClient.scala` — should be moved to a shared location (family-wide)
- `validateToken` returns `Boolean` rather than `Either[String, Boolean]` — family-wide inconsistency
- Broad `catch Exception` in adapter methods — family-wide
- `TrackerOps` method-pair growth — collapse behind a single `fetch/createIssue(IssueTrackerConfig)` pair (architecture refactor)
- `os.pwd` in `CIChecks` model layer — pre-existing, family-wide
- Forgejo repository auto-detection and self-hosted host heuristics — out of scope per RESOLVED clarification
- `http://` cleartext-token warning in `ForgejoClient` — deferred
- `sealed ForgeConfig` — minor, deferred
