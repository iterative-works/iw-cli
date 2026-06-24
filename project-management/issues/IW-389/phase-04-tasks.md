# Phase 4 Tasks: Init + doctor integration + smoke/harness coverage

**Issue:** IW-389 — Support Forgejo issue tracker
**Phase:** 4 — Init + doctor integration + smoke/harness coverage (Layers 5 + 6)

Close the loop opened in Phases 1–3: make a Forgejo project **configurable** via
`./iw init` and **diagnosable** via `./iw doctor`, and prove the end-to-end issue
path with one BATS smoke. Forgejo's init shape = GitLab's `repository` + `teamPrefix`
combined with YouTrack's required `baseUrl`. No adapter/wiring/dispatch changes (those
are Phases 2–3, consumed unchanged); no PR/CI-polling (Phase 5). TDD: add the failing
harness/unit cases first, then fill in each `match` arm to green. The current stubs —
`Init.collectTrackerDetails` `Left("Forgejo tracker is not supported…")` (`Init.scala:162–163`),
`resolveTrackerType` rejecting `--tracker=forgejo` (`:80–83`), `printNextSteps`
no-op (`:306`), `Doctor` `ciPlatform` `_ => "Unknown"` (`Doctor.scala:107–110`), and
`CIChecks` riding the `Linear | YouTrack | Forgejo` placeholder (`CIChecks.scala:33–42`) —
are the failing-test starting points.

## Setup

- [x] [setup] Confirm the mirror anchors named in `phase-04-context.md` still hold: `Init.resolveTrackerType` (`Init.scala:67–85`, invalid-arg message `:80–83`), `askForTracker` menu (`:108–121`), `trackerName` already handling Forgejo (`:101–106`), `collectTrackerDetails` GitLab repo+prefix (`:138–143`) / YouTrack baseUrl (`:145–154`) / Forgejo stub (`:162–163`), `resolveTeamPrefix` helper, `detectOrAskTracker` (`:87–99`), `printNextSteps` Linear/YouTrack arms (`:283–292`) and Forgejo no-op (`:306`); `Constants.TrackerTypeValues.Forgejo = "forgejo"` (`Constants.scala:55`) and `Constants.EnvVars.ForgejoApiToken = "FORGEJO_API_TOKEN"` (`Constants.scala:12`); `TrackerDetector.suggestTracker` host arms (`Config.scala:194–202`); `Doctor.ciPlatform` match (`Doctor.scala:107–110`) and `DoctorFixContext`/FixAction-capture pattern; `CIChecks.checkWorkflowExistsWith` + injected `fileExists` seam (`CIChecks.scala:16`) and the placeholder arm (`:33–42`).
- [x] [setup] Confirm `test/gitlab-issue.bats` structure (the smoke mirror): `setup()` exports `IW_SERVER_DISABLED=1`, `mktemp -d`, `git init`, hand-written `.iw/config.conf`, single happy-path `./iw issue` round-trip.

## Tests (write first — TDD)

### Harness tests — `core/test/InitHarnessTest.scala`

Mirror the existing YouTrack/GitLab cases (`:142–201`) and the github auto-detect
case (`:203+`). Each case drives `Init.run` through `FakeCommandEnv`.

- [x] [test] `forgejo with repository + base-url + team-prefix args: no prompts` — `Init.run(Seq("--tracker=forgejo", "--repository=owner/sample", "--base-url=https://codeberg.org", "--team-prefix=SAMP"))` → exit 0; assert `cfg.trackerType == IssueTrackerType.Forgejo`, `cfg.repository == Some("owner/sample")`, `cfg.trackerBaseUrl == Some("https://codeberg.org")`, `cfg.teamPrefix == Some("SAMP")`, `cfg.team == ""`, and `env.prompt.askCallList.isEmpty`. Mirror the YouTrack all-args case (`:184–201`).
- [x] [test] `forgejo without base-url: prompts for base URL` — `queueAskAnswers(...)` supplies the baseUrl (and teamPrefix if no `--team-prefix`); assert it lands in `cfg.trackerBaseUrl`. Mirror "youtrack without base url prompts" pattern.
- [x] [test] `forgejo next steps print FORGEJO_API_TOKEN` — assert `env.console.stdout.contains("FORGEJO_API_TOKEN")` (mirrors the Linear "Set your API token" assertion `:74`).
- [x] [test] `init auto-detects forgejo from codeberg.org remote` — `env.git.setRemoteUrl(Some(GitRemote("git@codeberg.org:owner/sample.git")))`, `queueAnswers(true)` to accept the suggestion, then supply repo/baseUrl prompts; assert exit 0 and `cfg.trackerType == IssueTrackerType.Forgejo`. Mirror "auto-detect from github remote and accept suggestion" (`:203+`).
- [x] [test] `askForTracker menu selects forgejo` — drive the menu path (no `--tracker`, remote that does NOT auto-detect) with answer `"5"`, then supply repo/baseUrl prompts; assert exit 0 and the Forgejo arm is reached (`cfg.trackerType == IssueTrackerType.Forgejo`). Mirror the option-1–4 menu cases.

### Harness tests — `core/test/DoctorHarnessTest.scala`

- [x] [test] Add a `forgejoConfig` fixture string mirroring `linearConfig` (`:19–28`) with `type = forgejo`, `repository = "owner/sample"`, `baseUrl = "https://codeberg.org"`.
- [x] [test] `forgejo valid config + git repo: exit 0, all checks pass` — mirrors "valid config + git repo" (`:43–53`); confirms `baseChecks` are tracker-agnostic for Forgejo.
- [x] [test] `forgejo --fix CI platform label is 'Forgejo Actions'` — seed a failing Quality check + a `FixAction` capturing `DoctorFixContext`; assert `ctx.ciPlatform == "Forgejo Actions"`. Mirror the FixAction-capture pattern (`:143–167`).

### Unit test — `core/test/CIChecksTest.scala` (model CIChecks test)

Use the injected `fileExists` seam (`checkWorkflowExistsWith`, `CIChecks.scala:16`) —
no real filesystem. Add alongside the existing GitHub/GitLab cases.

- [x] [test] Forgejo + `.forgejo/workflows/ci.yml` present → `CheckResult.Success` (message references `.forgejo/workflows/ci.yml`).
- [x] [test] Forgejo + only `.github/workflows/ci.yml` present → `CheckResult.Success` (compat fallback).
- [x] [test] Forgejo + neither present → `CheckResult.Error("Missing", ...)`. **Review-confirm decision (per context):** missing Forgejo workflow is an **Error** (parity with GitHub/GitLab — Forgejo has first-class Actions), not a `Warning`. Keep Error; flag in review if reviewers prefer the Linear/YouTrack "no CI assumption" downgrade.

### BATS E2E smoke — `core` (run via `./iw ./test unit` for harness; this file via `./iw ./test e2e`)

- [x] [test] Run `./mill core.test` (or `./iw ./test unit`) and confirm the new Init/Doctor/CIChecks cases fail for the right reason (stub `Left`, rejected flag, `_ => "Unknown"`, placeholder arm), not an unrelated failure.

## Implementation

### `Init.scala` — Forgejo configuration arms

- [x] [impl] `resolveTrackerType` (`Init.scala:67–85`): add `case Some(Constants.TrackerTypeValues.Forgejo) => Right(IssueTrackerType.Forgejo)` and extend the `Some(invalid)` message (`:80–83`) to include `forgejo`.
- [x] [impl] `askForTracker` (`Init.scala:108–121`): add a `5. Forgejo` menu option and a recursion-safe parse arm for `"5"` (mirror options 1–4); update the prompt text and the invalid-choice error to "Select 1, 2, 3, 4, or 5".
- [x] [impl] `collectTrackerDetails` (`Init.scala:131–163`): replace the stub arm (`:162–163`) with a real `case IssueTrackerType.Forgejo` arm — `repositoryArg.getOrElse(env.prompt.ask("Enter Forgejo repository (owner/repo format)"))`, required `baseUrlArg.getOrElse(env.prompt.ask("Enter Forgejo base URL (e.g., https://codeberg.org)"))` (no `gitlab.com`-style public-host skip), then `resolveTeamPrefix(teamPrefixArg, ownerRepo, env).map { prefix => ("", Some(ownerRepo), Some(prefix), Some(baseUrl)) }`. `team` stays `""` (unused). Mirror GitLab repo+prefix (`:138–143`) + YouTrack required baseUrl (`:145–154`). Do NOT add Forgejo remote-based repository auto-detection (out of scope).
- [x] [impl] `printNextSteps` (`Init.scala:278–306`): replace `case IssueTrackerType.Forgejo => ()` with an `export ${Constants.EnvVars.ForgejoApiToken}=...` prompt (`env.console.out("Set your API token:")` + the export line). Mirror the Linear/YouTrack arms (`:283–292`).

### `Config.scala` — codeberg auto-detect

- [x] [impl] `TrackerDetector.suggestTracker` (`Config.scala:194–202`): add `case Right("codeberg.org") => Some(IssueTrackerType.Forgejo)` alongside the existing host arms. Self-hosted hosts fall through to `None` → the menu, as intended.

### `Doctor.scala` — CI platform arm

- [x] [impl] `ciPlatform` match (`Doctor.scala:107–110`): add `case IssueTrackerType.Forgejo => "Forgejo Actions"` before the `_ => "Unknown"` catch-all. This only feeds the `DoctorFixContext` label used by `--fix`; `baseChecks` stay tracker-agnostic.

### `CIChecks.scala` — dedicated Forgejo Actions arm

- [x] [impl] Split `Forgejo` out of the `Linear | YouTrack | Forgejo` placeholder (`CIChecks.scala:33–42`) into its own `case IssueTrackerType.Forgejo` arm: check `.forgejo/workflows/ci.yml` first → `Success`, else `.github/workflows/ci.yml` → `Success` (compat), else `CheckResult.Error("Missing", "Create .forgejo/workflows/ci.yml")`. Narrow the placeholder back to `case IssueTrackerType.Linear | IssueTrackerType.YouTrack =>`. Keep the `IssueTrackerType` match exhaustive. Use the injected `fileExists` seam — no real filesystem in the check logic. (Missing = **Error**, per the review-confirm decision above.)

### `test/forgejo-issue.bats` — E2E smoke (new)

- [x] [test] Create `test/forgejo-issue.bats` mirroring `test/gitlab-issue.bats`: `setup()` exports `IW_SERVER_DISABLED=1`, `mktemp -d`, `git init`, write a hand-written `.iw/config.conf` with `tracker.type = forgejo`, `repository`, `baseUrl`. One happy-path test (per the testing guide: "BATS keeps the wiring smoke test only"). **Review-confirm decision (per context): no-token error-wiring variant** (hermetic, no network/HTTP stub) — with a Forgejo config but **no** `FORGEJO_API_TOKEN`, assert `./iw issue 1` exits 1 with a message mentioning `FORGEJO_API_TOKEN`. This still proves config-parse → dispatch → token resolution end-to-end (the adapter HTTP/JSON behavior is already covered by Phase 2's 33 unit tests). Flag in review if a live HTTP-stub round-trip is preferred.

## Integration & Verification

- [x] [test] Run `./mill core.test` (or `./iw ./test unit`) and confirm the full `core.test` suite is green, including every new Init/Doctor harness case and CIChecks unit case.
- [x] [test] Run the BATS smoke (`./iw ./test e2e`, or the single file) and confirm `forgejo-issue.bats` is green with `IW_SERVER_DISABLED=1`.
- [x] [impl] Compile core with `-Werror` and confirm **no warnings**: `scala-cli compile --scalac-option -Werror core/`. In particular, confirm every exhaustive `match IssueTrackerType` (`Init`, `Doctor.ciPlatform`, `CIChecks`) stays exhaustive with no non-exhaustive-match warnings.
- [x] [integration] Confirm no changes were made to `ForgejoClient`, `TrackerOps`/`LiveTrackerOps`, `Issue.scala` dispatch, or any PR/CI-polling path (consumed unchanged from Phases 2–3; Phase 5 respectively). Confirm `validateToken` is NOT wired into `init` or `doctor` (per the context API-contract decision — matches sibling trackers, keeps network I/O out of those commands).
- [x] [integration] Verify acceptance criteria from `phase-04-context.md`: `--tracker=forgejo` accepted (not rejected) and menu offers option 5; full-args init writes `tracker.type = forgejo` with `repository`/`baseUrl`/`teamPrefix` and prints `export FORGEJO_API_TOKEN=...`; codeberg.org remote suggests Forgejo; config round-trips (re-read by `issue`/`doctor` without error); `doctor` base checks pass and `--fix` CI label is "Forgejo Actions"; `CIChecks` has a dedicated exhaustive Forgejo arm; the smoke is green.

**Phase Status:** Complete
