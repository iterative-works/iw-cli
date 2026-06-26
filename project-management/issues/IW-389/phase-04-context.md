# Phase 4: Init + doctor integration + smoke/harness coverage

**Issue:** IW-389 — Support Forgejo issue tracker
**Layers:** 5 (Init & Doctor) + 6 (Tests & Smoke)
**Estimate:** 3–5h

## Goals

After this phase, a project can be **configured** for Forgejo through `./iw init`
and **diagnosed** through `./iw doctor`, closing the loop opened in Phases 1–3
(which made `./iw issue` work but left `init`/`doctor` as stubs/placeholders).
Concretely and testably:

- `./iw init --tracker=forgejo` (and the interactive menu) configures a Forgejo
  project: collects `repository` (`owner/repo`), a **required** `baseUrl`, and an
  optional `teamPrefix`, writes `tracker.type = forgejo` to `.iw/config.conf`,
  and prints an `export FORGEJO_API_TOKEN=...` next-steps prompt.
- `init` auto-detects `codeberg.org` git remotes as Forgejo
  (`TrackerDetector.suggestTracker`), per RESOLVED clarify; all other Forgejo
  instances are selected explicitly (flag or menu).
- `./iw doctor` reports correct status for a Forgejo-configured project: the
  `--fix` CI-platform label resolves to "Forgejo Actions", and the CI-workflow
  check (`CIChecks`) gets a dedicated Forgejo arm instead of riding the
  Linear/YouTrack placeholder.
- One BATS E2E smoke test exercises `./iw issue` against a Forgejo-configured
  project (`IW_SERVER_DISABLED=1`), proving the end-to-end wiring from Phases 1–3.
- `*HarnessTest.scala` coverage for the new Init and Doctor arms via
  `FakeCommandEnv`; unit coverage for the new `CIChecks` Forgejo arm.
- `core.test` is fully green; everything compiles with `-Werror` and no warnings.

## Scope

### In scope

- **`Init.scala` Forgejo menu arm** — replace the stub
  `Left("Forgejo tracker is not supported by the init command")`
  (`Init.scala:162–163`) in `collectTrackerDetails` with a real arm that
  collects `repository` + required `baseUrl` + optional `teamPrefix`.
- **`Init.scala` tracker selection** — add Forgejo to `resolveTrackerType`
  (`Init.scala:67–85`, currently rejects `--tracker=forgejo` as invalid) and to
  the interactive `askForTracker` menu (`Init.scala:108–121`, no Forgejo option
  today). `trackerName` (`Init.scala:101–106`) already handles Forgejo.
- **`Init.scala` next-steps prompt** — replace the no-op
  `case IssueTrackerType.Forgejo => ()` in `printNextSteps` (`Init.scala:306`)
  with an `export FORGEJO_API_TOKEN=...` prompt, mirroring the Linear/YouTrack
  arms (`:283–292`).
- **`TrackerDetector.suggestTracker`** (`Config.scala:194–202`) — add a
  `Right("codeberg.org") => Some(IssueTrackerType.Forgejo)` arm so `init`
  auto-suggests Forgejo on Codeberg remotes (RESOLVED clarify).
- **`Doctor.scala` CI-platform arm** — add `IssueTrackerType.Forgejo =>
  "Forgejo Actions"` to the `ciPlatform` match (`Doctor.scala:107–110`,
  currently `_ => "Unknown"`).
- **`CIChecks.scala` dedicated Forgejo arm** — split `Forgejo` out of the
  `Linear | YouTrack | Forgejo` placeholder arm (`CIChecks.scala:33–42`) into a
  Forgejo Actions arm. Forgejo runs Gitea/Forgejo Actions from
  `.forgejo/workflows/` (also reads `.github/workflows/` for compatibility), so
  the check should look for a Forgejo Actions workflow first.
- **BATS E2E smoke** — one `./iw issue` round-trip for a Forgejo-configured
  project (new `test/forgejo-issue.bats`, mirroring `test/gitlab-issue.bats`),
  with `IW_SERVER_DISABLED=1` in `setup()`.
- **Harness tests** — Forgejo cases in `InitHarnessTest.scala` and
  `DoctorHarnessTest.scala`; a `CIChecks` Forgejo unit test (the model module's
  CIChecks test, alongside the existing GitHub/GitLab cases).

### Out of scope

- PR creation, CI-check **polling** (commit-status endpoints), `ForgeType`
  Forgejo arm — **Phase 5**. The "Forgejo Actions" doctor label and the
  `CIChecks` workflow-file check here are static config/file checks only; they
  do **not** poll the Forgejo API.
- The Forgejo adapter (`ForgejoClient`), `TrackerOps` wiring, and `Issue.scala`
  dispatch — delivered in Phases 2–3, consumed unchanged here. The BATS smoke
  test exercises them but does not modify them.
- `validateToken` consumption — see "API contracts" below for the decision
  (NOT consumed in this phase).

## Component specifications

### `Init.scala` — Forgejo configuration arm

Forgejo's init shape = **GitLab's `repository` + `teamPrefix`** combined with
**YouTrack's required `baseUrl`**. Three edits:

**1. `resolveTrackerType` (`Init.scala:67–85`)** — add the flag arm and extend
the invalid-arg message:

```scala
case Some(Constants.TrackerTypeValues.Forgejo) =>
  Right(IssueTrackerType.Forgejo)
```

(`Constants.TrackerTypeValues.Forgejo = "forgejo"` already exists,
`Constants.scala:55`.) Update the `Some(invalid)` message string
(`Init.scala:80–83`) to include `forgejo`.

**2. `askForTracker` (`Init.scala:108–121`)** — add a `5. Forgejo` menu option
and recursion-safe parse arm (mirrors options 1–4); update the prompt text and
the invalid-choice error to "Select 1, 2, 3, 4, or 5".

**3. `collectTrackerDetails` (`Init.scala:131–163`)** — replace the stub arm
(`:162–163`) with, mirroring `GitLab` (`:138–143`) for repository + teamPrefix
and `YouTrack` (`:145–154`) for the required baseUrl:

```scala
case IssueTrackerType.Forgejo =>
  val ownerRepo = repositoryArg.getOrElse(
    env.prompt.ask("Enter Forgejo repository (owner/repo format)")
  )
  val baseUrl = baseUrlArg.getOrElse(
    env.prompt.ask(
      "Enter Forgejo base URL (e.g., https://codeberg.org)"
    )
  )
  resolveTeamPrefix(teamPrefixArg, ownerRepo, env).map { prefix =>
    ("", Some(ownerRepo), Some(prefix), Some(baseUrl))
  }
```

This returns the `(team, repository, teamPrefix, trackerBaseUrl)` tuple
`collectTrackerDetails` is typed to produce; `team` stays `""` (RESOLVED:
`tracker.team` unused for Forgejo). `baseUrl` is **required** — there is no
`gitlab.com`-style "skip if public host" branch, since every Forgejo instance
(Codeberg included) needs an explicit base URL.

> Note (auto-detect of repository): GitLab/GitHub use `resolveGitLabRepo` /
> `resolveGitHubRepo` to derive `owner/repo` from the git remote. A
> `GitRemote.extractGitLabRepository`-style helper does not exist for Forgejo.
> Keeping the prompt-based collection above is the smallest change and matches
> YouTrack's prompt-only approach; remote-based repository auto-detection for
> Forgejo is a non-goal for this phase (the RESOLVED clarify only specifies
> *tracker-type* auto-detect for codeberg.org, not repository extraction).

**4. `printNextSteps` (`Init.scala:278–306`)** — replace
`case IssueTrackerType.Forgejo => ()` with:

```scala
case IssueTrackerType.Forgejo =>
  env.console.out("Set your API token:")
  env.console.out(
    s"  export ${Constants.EnvVars.ForgejoApiToken}=..."
  )
```

(`Constants.EnvVars.ForgejoApiToken = "FORGEJO_API_TOKEN"` already exists,
`Constants.scala:12`.)

### `TrackerDetector.suggestTracker` (`Config.scala:194–202`) — codeberg arm

```scala
case Right("codeberg.org") => Some(IssueTrackerType.Forgejo)
```

inserted alongside the existing host arms. This is what makes `init` (no
`--tracker`) suggest Forgejo on a Codeberg remote via `detectOrAskTracker`
(`Init.scala:87–99`). Self-hosted Forgejo hosts are not pattern-matchable
(no canonical hostname), so they fall through to `None` → the menu, as intended.

### `Doctor.scala` — CI platform arm (`Doctor.scala:107–110`)

```scala
val ciPlatform = config.tracker.trackerType match
  case IssueTrackerType.GitHub  => "GitHub Actions"
  case IssueTrackerType.GitLab  => "GitLab CI"
  case IssueTrackerType.Forgejo => "Forgejo Actions"
  case _                        => "Unknown"
```

This only feeds the `DoctorFixContext` label used by `--fix` plugins; the base
config/git checks (`baseChecks`, `Doctor.scala:58–84`) are tracker-agnostic and
already cover Forgejo (they only verify the config parses and git exists).

### `CIChecks.scala` — Forgejo Actions arm (`CIChecks.scala:20–42`)

Pull `Forgejo` out of the `Linear | YouTrack | Forgejo` placeholder arm into its
own arm checking for a Forgejo Actions workflow, falling back to `.github`
(Forgejo Actions reads both):

```scala
case IssueTrackerType.Forgejo =>
  val forgejoWorkflow = os.pwd / ".forgejo" / "workflows" / "ci.yml"
  val githubWorkflow = os.pwd / ".github" / "workflows" / "ci.yml"
  if fileExists(forgejoWorkflow) then
    CheckResult.Success("Found (.forgejo/workflows/ci.yml)")
  else if fileExists(githubWorkflow) then
    CheckResult.Success("Found (.github/workflows/ci.yml)")
  else
    CheckResult.Error(
      "Missing",
      "Create .forgejo/workflows/ci.yml"
    )
```

The remaining placeholder arm narrows back to
`case IssueTrackerType.Linear | IssueTrackerType.YouTrack =>`. The match stays
exhaustive over `IssueTrackerType`.

> Decision to confirm in review: making the Forgejo CI check an **Error** when
> absent (like GitHub/GitLab) rather than a **Warning** (like Linear/YouTrack).
> Forgejo is a forge with first-class Actions, so a missing CI workflow is a
> genuine gap — consistent with GitHub/GitLab. If reviewers prefer parity with
> the "no official CI assumption" trackers, downgrade to
> `CheckResult.Warning("No CI workflow found")`.

## API contracts / signatures touched

- **No new public signatures.** Phase 4 fills in existing `match` arms
  (`Init.collectTrackerDetails`, `Init.resolveTrackerType`,
  `Init.printNextSteps`, `Doctor` `ciPlatform`, `CIChecks.checkWorkflowExistsWith`,
  `TrackerDetector.suggestTracker`). The tuple contract of
  `collectTrackerDetails` (`Either[String, (String, Option[String],
  Option[String], Option[String])]`) is unchanged.

- **`ForgejoClient.validateToken` — NOT consumed in this phase.** Phase 3's log
  flagged it as "needed by Phase 4 doctor/init", but on inspection neither
  `init` nor `doctor` calls any tracker adapter today:
  - `Init` writes config and prints next-steps; it never validates a token
    (Linear/YouTrack init don't either — they just print
    `export ..._API_TOKEN=...`). Adding a live token-validation call to `init`
    would be a behavioral divergence from every sibling tracker and would pull
    network I/O into `init`. **Decision: do not consume `validateToken` in
    `init`** — match the sibling pattern (print the env-var prompt only).
  - `Doctor`'s `baseChecks` are config/git-presence checks; no tracker performs
    an authenticated API probe in `doctor` today. Adding one for Forgejo alone
    would be inconsistent and would make `doctor` hit the network. **Decision:
    do not consume `validateToken` in `doctor`** either.

  Net: `validateToken` stays available on `ForgejoClient` (built in Phase 2) but
  is **not wired** in Phase 4. If a token-validation doctor check is wanted, it
  should be a cross-tracker feature (its own ticket), not a Forgejo-only arm.
  Flag in review if a Forgejo-specific live check is desired despite the
  inconsistency.

## Dependencies on prior phases

### Phase 1 (Layer 1 — model/config)

- `IssueTrackerType.Forgejo` (`Config.scala:103`); `trackerName`
  (`Init.scala:106`) and the model-layer exhaustive matches already handle it.
- `Constants.TrackerTypeValues.Forgejo = "forgejo"` (`Constants.scala:55`); the
  HOCON serializer round-trips `tracker.type = forgejo` (`Config.scala:211`,
  parse side covered by Phase 1 tests). This is what makes the BATS smoke test's
  hand-written config parse, and what `init`'s `writeConfig` emits.
- `CIChecks.scala` Forgejo currently rides the Linear/YouTrack placeholder arm
  (`:33–42`) — Phase 1 left this as a placeholder explicitly deferring the
  dedicated arm to "Phase 4 doctor".

### Phase 2 (Layer 2 — adapter)

- `ForgejoClient.{fetchIssue, createIssue, validateToken}` exist and are unit
  tested (33 tests). `validateToken` is available but unconsumed (see above).
  The BATS smoke test drives `fetchIssue` end-to-end via the dispatch path.

### Phase 3 (Layers 3–4 — wiring + dispatch)

- `Constants.EnvVars.ForgejoApiToken = "FORGEJO_API_TOKEN"`
  (`Constants.scala:12`) — used by `init`'s next-steps prompt and by the BATS
  smoke test's `export`.
- `TrackerOps.fetchForgejoIssue` / `createForgejoIssue`, `LiveTrackerOps`
  delegations, and `Issue.scala` Forgejo dispatch (token + `repository` +
  required `baseUrl` resolution, teamPrefix composition). The BATS smoke test
  exercises this whole path; Phase 4 does not touch it.

## Testing requirements

TDD: write the failing harness/unit cases first, then implement each arm to
green. Run `./mill core.test` (or `./iw ./test unit`) for unit/harness; the BATS
smoke runs under `./iw ./test e2e`.

### Harness tests — `InitHarnessTest.scala`

Mirror the existing YouTrack/GitLab cases (`:142–201`). Add:

1. **`forgejo with repository + base-url + team-prefix args: no prompts`** —
   `Init.run(Seq("--tracker=forgejo", "--repository=owner/sample",
   "--base-url=https://codeberg.org", "--team-prefix=SAMP"))` → exit 0;
   assert `cfg.trackerType == IssueTrackerType.Forgejo`,
   `cfg.repository == Some("owner/sample")`,
   `cfg.trackerBaseUrl == Some("https://codeberg.org")`,
   `cfg.teamPrefix == Some("SAMP")`, `cfg.team == ""`, and
   `env.prompt.askCallList.isEmpty`. (Mirrors the YouTrack all-args case
   `:184–201`.)
2. **`forgejo without base-url: prompts for base URL`** —
   `queueAskAnswers(...)` supplies the baseUrl (and teamPrefix if no
   `--team-prefix`); assert it lands in `cfg.trackerBaseUrl`.
3. **`forgejo next steps print FORGEJO_API_TOKEN`** — assert
   `env.console.stdout.contains("FORGEJO_API_TOKEN")` (mirrors the Linear
   "Set your API token" assertion `:74`).
4. **`init auto-detects forgejo from codeberg.org remote`** —
   `env.git.setRemoteUrl(Some(GitRemote("git@codeberg.org:owner/sample.git")))`,
   `queueAnswers(true)` to accept the suggestion, then supply repo/baseUrl
   prompts; assert exit 0 and `cfg.trackerType == IssueTrackerType.Forgejo`.
   (Mirrors "auto-detect from github remote and accept suggestion" `:203+`.)
5. **`askForTracker menu selects forgejo`** (option `"5"`) — drive the
   menu path (no `--tracker`, remote that doesn't auto-detect) and assert the
   Forgejo arm is reached.

### Harness tests — `DoctorHarnessTest.scala`

Add a `forgejoConfig` fixture (mirror `linearConfig` `:19–28`, with
`type = forgejo`, `repository`, `baseUrl`):

1. **`forgejo valid config + git repo: exit 0, all checks pass`** — mirrors
   "valid config + git repo" (`:43–53`); confirms `baseChecks` are
   tracker-agnostic for Forgejo.
2. **`forgejo --fix CI platform label is 'Forgejo Actions'`** — seed a failing
   Quality check + a `FixAction` capturing `DoctorFixContext`, assert
   `ctx.ciPlatform == "Forgejo Actions"` (mirrors the FixAction-capture pattern
   `:143–167`).

### Unit test — `CIChecks` Forgejo arm

In the model module's CIChecks test (alongside the GitHub/GitLab cases):

1. Forgejo + `.forgejo/workflows/ci.yml` present → `Success`.
2. Forgejo + only `.github/workflows/ci.yml` present → `Success` (compat).
3. Forgejo + neither present → `Error("Missing", ...)` (or `Warning` if the
   review decision flips per the note above).

Use the injected `fileExists` seam (`checkWorkflowExistsWith`,
`CIChecks.scala:16`) — no real filesystem.

### BATS E2E smoke — `test/forgejo-issue.bats` (new)

Mirror `test/gitlab-issue.bats` structure (`setup()` exports
`IW_SERVER_DISABLED=1`, `mktemp -d`, `git init`). One happy-path test is
sufficient (per the project testing guide: "BATS keeps the wiring smoke test
only"). Forgejo is a **direct HTTP** adapter (no `glab`-style CLI to mock), so
the smoke test must stub the HTTP endpoint, not a CLI binary. Two viable
approaches — pick the one that fits the BATS harness:

- **Preferred:** point `baseUrl` at a tiny local HTTP stub (e.g. a
  backgrounded `python3 -m http.server` serving a canned
  `/api/v1/repos/owner/sample/issues/1` JSON body) and `export
  FORGEJO_API_TOKEN=dummy`; assert `./iw issue 1` exits 0 and prints the issue
  title + `SAMP-1`.
- **Fallback (if a local HTTP stub is impractical in BATS):** assert the
  **error wiring** instead — with a Forgejo config but **no**
  `FORGEJO_API_TOKEN`, `./iw issue 1` exits 1 with a message mentioning
  `FORGEJO_API_TOKEN`. This still proves config-parse → dispatch → token
  resolution end-to-end without a network dependency, consistent with the
  "one round-trip" smoke philosophy.

> Decision to confirm in review: which smoke variant. The HTTP-stub variant
> proves a true round-trip; the no-token variant is hermetic and matches the
> "smoke, not coverage" intent (the adapter's HTTP/JSON behavior is already
> covered by Phase 2's 33 unit tests). Recommend the no-token error-wiring
> smoke unless a local HTTP stub is already an established pattern in `test/`.

## Acceptance criteria

- [ ] `./iw init --tracker=forgejo --repository=... --base-url=... --team-prefix=...`
      writes `tracker.type = forgejo` with `repository`, `baseUrl`, and
      `teamPrefix`, and prints `export FORGEJO_API_TOKEN=...` (analysis AC:
      "`init --tracker=forgejo` configures a project for Forgejo").
- [ ] `--tracker=forgejo` is accepted (not rejected as invalid) and the
      interactive menu offers Forgejo (option 5).
- [ ] `init` with no `--tracker` on a `codeberg.org` remote suggests Forgejo.
- [ ] `tracker.type = forgejo` round-trips through `.iw/config.conf` — the
      config `init` writes is re-read by `issue`/`doctor` without error
      (analysis AC: "round-trips correctly").
- [ ] `./iw doctor` reports correct status for a Forgejo project: base checks
      pass on a valid config; `--fix` CI label is "Forgejo Actions" (analysis
      AC: "doctor reports correct status").
- [ ] `CIChecks` has a dedicated Forgejo arm; the `IssueTrackerType` match stays
      exhaustive.
- [ ] The Forgejo BATS smoke test is green and exports `IW_SERVER_DISABLED=1`.
- [ ] New Init/Doctor harness cases and the CIChecks unit cases are green; full
      `core.test` stays green.
- [ ] Compiles with `-Werror` and **no warnings**.
- [ ] No changes to `ForgejoClient`, `TrackerOps`/`LiveTrackerOps`,
      `Issue.scala` dispatch, or any PR/CI-polling path (those are Phases 2–3
      consumed unchanged, and Phase 5 respectively).

## Files to modify / create

| File | Change | Mirror |
|------|--------|--------|
| `core/commands/Init.scala` | Forgejo arms in `resolveTrackerType` (`:67–85`), `askForTracker` (`:108–121`), `collectTrackerDetails` (replace stub `:162–163`), `printNextSteps` (replace no-op `:306`). | GitLab repo+prefix (`:138–143`), YouTrack baseUrl (`:145–154`), Linear/YouTrack next-steps (`:283–292`) |
| `core/model/Config.scala` | `TrackerDetector.suggestTracker`: add `codeberg.org → Forgejo` arm (`:194–202`). | existing host arms |
| `core/commands/Doctor.scala` | `ciPlatform` match: add `Forgejo => "Forgejo Actions"` (`:107–110`). | GitHub/GitLab arms |
| `core/model/CIChecks.scala` | Split `Forgejo` into a dedicated Forgejo Actions arm; narrow placeholder to `Linear | YouTrack` (`:20–42`). | GitHub (`:21–25`) / GitLab (`:27–31`) arms |
| `core/test/InitHarnessTest.scala` | Forgejo init cases (args, prompt, next-steps, codeberg auto-detect, menu). | YouTrack/GitLab cases (`:142–201`) |
| `core/test/DoctorHarnessTest.scala` | Forgejo config fixture + valid-config and `--fix` CI-label cases. | linearConfig (`:19–28`), FixAction capture (`:143–167`) |
| `core/test/CIChecksTest.scala` (model CIChecks test) | Forgejo CI-workflow arm cases. | existing GitHub/GitLab cases |
| `test/forgejo-issue.bats` (new) | One E2E smoke for `./iw issue` on a Forgejo project; `IW_SERVER_DISABLED=1`. | `test/gitlab-issue.bats` |

## Notes / divergences from the analysis

- **`validateToken` not consumed** (analysis Layer 5 and the Phase 3 log both
  anticipated init/doctor using it). On inspection, no sibling tracker validates
  tokens at init/doctor time, so wiring it for Forgejo alone would be an
  inconsistency and would pull network I/O into those commands. Flagged above
  as a review decision; the method remains available for a future cross-tracker
  feature.
- **Repository auto-detection for Forgejo is not implemented** — the RESOLVED
  clarify only specifies *tracker-type* auto-detect for `codeberg.org`. No
  `GitRemote` Forgejo-repository extractor exists; init prompts for
  `owner/repo` (YouTrack-style). Adding a remote extractor is out of scope.
- **CIChecks Forgejo workflow location** assumed to be `.forgejo/workflows/`
  (Forgejo Actions convention) with `.github/workflows/` fallback. If the team
  standardizes on `.github/workflows/` only, the dedicated arm can mirror the
  GitHub arm exactly — adjust during implementation.
