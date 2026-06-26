# Phase 5 Tasks: PR creation + CI-check polling (forge parity)

**Issue:** IW-389 — Support Forgejo issue tracker
**Phase:** 5 — PR creation + CI-check polling (Layers 2–4, forge paths)

Make Forgejo a first-class **forge** so `./iw phase-pr` and `./iw phase-merge` drive
pull requests against it over HTTP (no CLI). `ForgeType` gains a `Forgejo` case and
**resolves to Forgejo whenever the tracker type is Forgejo** (host detection is
unreliable for self-hosted instances); `codeberg.org` also maps by host. The four
`TrackerOps` forge methods (`createPullRequest`, `mergeSquashAndDelete`,
`mergeWithDelete`, `fetchCheckStatuses`) gain a Forgejo arm in `LiveTrackerOps`,
delegating to three new `ForgejoClient` HTTP methods. The crux is the **baseUrl /
token / SHA contract gap**: the current forge signatures carry none of these, so we
widen them with a forge-config parameter (Option B), resolved in the command layer.
TDD: write the failing builder/parser/resolution/harness cases first, then implement
to green. Order: model → adapter → wiring → commands → tests, each green before the next.

## Setup

- [x] [setup] Confirm the mirror anchors named in `phase-05-context.md` still hold: `ForgeType` enum + `cliTool`/`installUrl`/`fromHost`/`resolve` (`ForgeType.scala:6–48`); the **single** `cliTool`/`installUrl` call site `PhaseAdvance.scala:91–93` (a `commandExists` prerequisite guard that runs for whatever forge resolves — NOT GitHub/GitLab-only); the four `TrackerOps` forge-method signatures + their lone `gitlabHost: Option[String]` parameter (`CommandEnv.scala:117–145`); `LiveTrackerOps` GitHub/GitLab arms (`LiveCommandEnv.scala:141–227`); `PhasePr.Resolved` + `gitlabHost = remoteOpt.flatMap(_.host.toOption)` + `createPullRequest`/`mergeSquashAndDelete` call sites (`PhasePr.scala:9,62,123–130,162`); `PhaseMerge.Resolved` + `prNumber = extractPrNumber(prUrl)` + `fetchCheckStatuses`/`mergeWithDelete` call sites (`PhaseMerge.scala:9,77,83,284–288,345`); `FakeTracker` PR/merge/check recorders (`PrCall`/`MergeCall`, `FakeCommandEnv.scala:323–618`) and the Phase-3 `kind = "forgejo"` issue-recorder convention (`:587,605`).
- [x] [setup] Confirm `Constants.EnvVars.ForgejoApiToken = "FORGEJO_API_TOKEN"` and how `Issue.scala`'s `forgejoToken(env)` / baseUrl guards resolve token + `config.trackerBaseUrl` — the forge ops resolve the same way. Confirm the existing `ForgejoClient` issue methods' shape: injectable `SyncBackend` seam, `Authorization: token <token>` header, `ujson` value-tree parsing, `Either[String, …]` return, `buildIssueUrl`-style pure builders (the three new methods mirror this exactly).
- [x] [setup] Confirm `test/forgejo-issue.bats` (Phase 4 smoke) structure as the smoke mirror: `setup()` exports `IW_SERVER_DISABLED=1`, `mktemp -d`, `git init`, hand-written `.iw/config.conf` with `tracker.type = forgejo` + `repository` + `baseUrl`, single hermetic no-token error-wiring assertion.

## Setup / model — `ForgeType`

### Tests (write first — TDD) — `core/test/ForgeTypeTest.scala`

- [x] [test] `resolve(None, IssueTrackerType.Forgejo)` → `ForgeType.Forgejo` (no remote, tracker type wins).
- [x] [test] `resolve(Some(selfHostedRemote), IssueTrackerType.Forgejo)` → `ForgeType.Forgejo` — tracker type wins over a self-hosted host that `fromHost` would otherwise call `GitLab` (use e.g. `git@git.example.com:owner/repo.git`).
- [x] [test] `fromHost("codeberg.org")` → `Forgejo`; regression `fromHost("github.com")` → `GitHub`; `fromHost("gitlab.example.com")` → `GitLab` (unchanged).
- [x] [test] Regression: `resolve(Some(githubRemote), IssueTrackerType.GitHub)` → `GitHub`; `resolve(Some(gitlabRemote), IssueTrackerType.GitLab)` → `GitLab`; `resolve(None, IssueTrackerType.GitHub)` → `GitHub`; `resolve(None, IssueTrackerType.GitLab)` → `GitLab` (GitHub/GitLab precedence unchanged — host-first).
- [x] [test] **Decide and implement** the `cliTool`/`installUrl` decision in the test up front (see impl task): per the call-site audit (only `PhaseAdvance.scala:91–93`), the recommended default is `Option[String]` returning `None` for Forgejo — update/extend the existing `cliTool`/`installUrl` tests (`:50–63`) to assert `ForgeType.Forgejo.cliTool == None` / `ForgeType.GitHub.cliTool == Some("gh")` (or keep `String` + sentinel if the audit changes the call). Run `./mill core.test` and confirm these new `ForgeTypeTest` cases fail for the right reason (no `Forgejo` case / no codeberg arm / host-first resolve), not an unrelated failure.

### Implementation — `core/model/ForgeType.scala`

- [x] [impl] Add the case: `enum ForgeType: case GitHub, GitLab, Forgejo`.
- [x] [impl] **Decide and implement `cliTool`/`installUrl` for the CLI-less forge.** Audit confirmed a single call site (`PhaseAdvance.scala:91–93`, a `commandExists` prerequisite guard). Recommended default: change both to `Option[String]`, returning `None` for `Forgejo` and `Some("gh")`/`Some("glab")` (+ install URLs) for GitHub/GitLab; do NOT invent a fake CLI name. Update the `PhaseAdvance` guard to skip the prerequisite check when `cliTool == None` (Forgejo has no CLI to install). If the audit during impl reveals wider fan-out, fall back to the `String` sentinel (`""`), but prefer the honest `Option`.
- [x] [impl] `fromHost` (`ForgeType.scala:20–22`): add the codeberg arm — `else if host == "codeberg.org" then Forgejo` before the `else GitLab` fall-through. Self-hosted hosts still fall through to `GitLab` (corrected by `resolve`).
- [x] [impl] `resolve` (`ForgeType.scala:40–48`): make the tracker type win for Forgejo — `if trackerType == IssueTrackerType.Forgejo then Forgejo` before the existing remote-host-first logic. GitHub/GitLab branches unchanged (host still wins, falling back to tracker type → GitHub/GitLab). Update the scaladoc to describe the Forgejo precedence inversion.
- [x] [impl] Run `./mill core.test` ForgeTypeTest green; compile core `-Werror` and confirm every `match ForgeType` stays exhaustive (the `LiveTrackerOps` arms come next — expect a non-exhaustive warning there until step 3, which is the failing-test signal).

## Adapter (TDD) — `core/adapters/ForgejoClient.scala`

### Tests (write first — TDD) — `core/test/ForgejoClientTest.scala`

Extend with cases mirroring the existing 33 issue cases, all network-free via `SyncBackendStub`.

- [x] [test] **URL/body builders** — `buildCreatePullRequestUrl(baseUrl, repository)` → `{baseUrl}/api/v1/repos/{owner}/{repo}/pulls` (trailing-slash handling on `baseUrl`); `buildMergePullRequestUrl(baseUrl, repository, index)` → `.../pulls/{index}/merge` (index in path); `buildCommitStatusUrl(baseUrl, repository, sha)` → `.../commits/{sha}/status`.
- [x] [test] **Body builders** — `buildCreatePullRequestBody(headBranch, baseBranch, title, body)` exact keys `head`/`base`/`title`/`body`; `buildMergePullRequestBody()` exact keys `Do = "squash"`, `delete_branch_after_merge = true`.
- [x] [test] `extractPullRequestIndex(prUrl)` — `.../pulls/42` → `Right(42)`; malformed / no trailing index → `Left`.
- [x] [test] `parseCreatePullRequestResponse` — canned 201 JSON carrying `number`, `html_url`, `head.sha` → `Right(PullRequest(number, htmlUrl, headSha))`; malformed JSON → `Left`. (Confirm the head-SHA field is `head.sha` against the Forgejo API.)
- [x] [test] `createPullRequest` (wired via `SyncBackendStub`) — happy path 201 → `Right(PullRequest(...))`; 401 → `Left("API token is invalid or expired")`; 404 → not-found `Left`; 422 → `Left("Forgejo API error: 422")`; network exception → `Left("Network error: <msg>")`.
- [x] [test] `mergePullRequest` — happy path 200 → `Right(())`; 401; 404; other/error JSON → mapped `Left`.
- [x] [test] `parseCommitStatusResponse` / `fetchCheckStatuses` — map `statuses[].state`: `success → Passed`, `failure`/`error` → `Failed`, `pending → Pending`, other → `Unknown`; assert `CICheckResult(name = context, status, url = target_url)`; empty `statuses` array → `Right(Nil)`; malformed JSON → `Left`; missing optional fields handled.
- [x] [test] Run `./mill core.test` and confirm the new `ForgejoClientTest` cases fail for the right reason (methods/builders absent), not unrelated.

### Implementation — `core/adapters/ForgejoClient.scala`

Mirror the existing `fetchIssue`/`createIssue` shape; mirror `GitHubClient.parseGhChecksJson` (`:815–843`) / `GitLabClient.parseGlabJobsJson` (`:732–747`) for the state/name/url mapping.

- [x] [impl] Add a small `PullRequest(number: Int, htmlUrl: String, headSha: String)` domain type (in `model/` if a forge PR type does not already exist; else reuse) carrying `number`, `html_url`, `head.sha`.
- [x] [impl] `createPullRequest` — pure `buildCreatePullRequestUrl` + `buildCreatePullRequestBody` + `parseCreatePullRequestResponse`, then the wired method `createPullRequest(repository, headBranch, baseBranch, title, body, baseUrl, token: ApiToken, backend = defaultBackend): Either[String, PullRequest]`. `POST .../pulls`, `Authorization: token <token>`, `Accept: application/json`. Error mapping: 401/404/other + network-catch as above.
- [x] [impl] `extractPullRequestIndex(prUrl): Either[String, Int]` — pure helper extracting the trailing `/pulls/{index}` index.
- [x] [impl] `mergePullRequest(repository, index, baseUrl, token, backend): Either[String, Unit]` — `buildMergePullRequestUrl` + `buildMergePullRequestBody` (`Do=squash`, `delete_branch_after_merge=true`), `POST .../pulls/{index}/merge`, 200 → `Right(())`, error mapping as above.
- [x] [impl] `fetchCheckStatuses(repository, sha, baseUrl, token, backend): Either[String, List[CICheckResult]]` — `buildCommitStatusUrl` + `parseCommitStatusResponse`, `GET .../commits/{sha}/status`, map per the state table; empty `statuses` → `Right(Nil)`.
- [x] [impl] Run `./mill core.test` ForgejoClientTest green; compile core `-Werror` for this file (no warnings).

## Wiring — forge-config parameter (Option B)

### `core/commands/CommandEnv.scala` — widen the four forge methods

- [x] [impl] Introduce a focused `ForgeConfig` case class (carrying optional `baseUrl: Option[String]` and `token: Option[ApiToken]`; add `headSha` only if the SHA-threading sub-option is chosen — see PhaseMerge below). Place it with the `TrackerOps` trait / in `model/` per FCIS (pure data).
- [x] [impl] Add the `ForgeConfig` parameter to `createPullRequest`, `mergeSquashAndDelete`, `mergeWithDelete`, `fetchCheckStatuses` (`CommandEnv.scala:117–145`) — **additive**, alongside the existing `gitlabHost: Option[String]` (Option B; leave `gitlabHost` in place, defer the Option C collapse to its own ticket). GitHub/GitLab arms ignore the Forgejo fields.

### `core/commands/LiveCommandEnv.scala` — `LiveTrackerOps` Forgejo arms

- [x] [impl] `createPullRequest` (`LiveCommandEnv.scala:141`): add `case ForgeType.Forgejo =>` delegating to `ForgejoClient.createPullRequest(...)` with `forgeConfig.baseUrl`/`forgeConfig.token` (surfacing a clear `Left` if either is absent), projecting `PullRequest.htmlUrl` out to the `String` the method returns.
- [x] [impl] `mergeSquashAndDelete` (`:169`) and `mergeWithDelete` (`:191`): add `case ForgeType.Forgejo =>` — both extract the index via `ForgejoClient.extractPullRequestIndex(prUrl)` then call `ForgejoClient.mergePullRequest(repository, index, baseUrl, token)` (both map to the same squash + `delete_branch_after_merge` — Forgejo has no separate "with delete" distinction).
- [x] [impl] `fetchCheckStatuses` (`:213`): add `case ForgeType.Forgejo =>` delegating to `ForgejoClient.fetchCheckStatuses(repository, sha, baseUrl, token)`; obtain `sha` per the chosen SHA sub-option (see PhaseMerge task).
- [x] [impl] Confirm all four `match ForgeType` blocks in `LiveTrackerOps` are now exhaustive (resolves the warning expected from the model step).

### `core/commands/PhasePr.scala` / `PhaseMerge.scala` — resolve + thread

- [x] [impl] `PhasePr` (`:9,62,123–130`): resolve `baseUrl` from `config.trackerBaseUrl` and the token from `FORGEJO_API_TOKEN` (mirror `Issue.scala`'s `forgejoToken(env)` + baseUrl guard) **once** into a `ForgeConfig` in `Resolved` (next to `gitlabHost`); thread it into the `createPullRequest`/`mergeSquashAndDelete` calls. Guard: for a Forgejo forge, a missing token or baseUrl must surface a clear `Left`/error exit **before** any forge call (mirror `Issue.scala` guards). GitHub/GitLab unaffected (empty/ignored `ForgeConfig`).
- [x] [impl] `PhaseMerge` (`:9,77,83,284–288,345`): same `ForgeConfig` resolution into `Resolved`; thread into `fetchCheckStatuses`/`mergeWithDelete`. **Decide and implement the SHA sub-option** — recommended default: the Forgejo `fetchCheckStatuses` adapter arm does a `GET /pulls/{index}` lookup to read `head.sha` itself (keeps the shared signature smallest, SHA stays inside the adapter), where `index == prNumber` (already derived via `extractPrNumber`). Alternative (if the extra round-trip is a concern): thread `headSha` through `ForgeConfig`, resolved in `PhaseMerge` from the PR. Pick one, note it for review.

### `core/test/fixtures/FakeCommandEnv.scala` — extend `FakeTracker`

- [x] [impl] Extend `PrCall`/`MergeCall` (and the check recorder, `FakeCommandEnv.scala:323–618`) to capture the new `ForgeConfig` (baseUrl/token, + headSha if threaded) extras, matching the Phase-3 `kind = "forgejo"` recorder convention, so harness tests can assert the Forgejo arm + resolved baseUrl/token. Keep the GitHub/GitLab recording behavior unchanged.

## Harness tests — `PhasePrHarnessTest.scala` / `PhaseMergeHarnessTest.scala`

Drive `PhasePr.run` / `PhaseMerge.run` through `FakeCommandEnv` with a Forgejo-typed
config so `ForgeType.resolve` yields `Forgejo`. Mirror the existing GitHub/GitLab cases.

- [x] [test] `phase-pr on a Forgejo project` — config `tracker.type = forgejo` + `repository` + `baseUrl`, `FORGEJO_API_TOKEN` in env → exit 0; assert `FakeTracker.createPullRequest` was invoked with `forge == ForgeType.Forgejo` and the resolved baseUrl/token (via `ForgeConfig`); the printed PR URL is the fake's return.
- [x] [test] `phase-merge on a Forgejo project` — assert `FakeTracker.fetchCheckStatuses` invoked with `forge == ForgeType.Forgejo`; script an all-passing verdict and assert `mergeSquashAndDelete`/`mergeWithDelete` invoked with `forge == ForgeType.Forgejo`.
- [x] [test] `phase-pr error wiring: missing FORGEJO_API_TOKEN (or missing baseUrl) on a Forgejo project` → non-zero exit / clear `Left` mentioning `FORGEJO_API_TOKEN` (resp. baseUrl) **before** any `FakeTracker.createPullRequest` call (mirrors `Issue.scala` guards).
- [x] [test] `phase-merge error wiring: missing FORGEJO_API_TOKEN/baseUrl` → same guard before any forge call.
- [x] [test] Run `./mill core.test` and confirm the new harness cases fail for the right reason (no Forgejo arm / guard absent), then go green after wiring.

## Smoke / integration

- [x] [test] **Optional** hermetic no-token Forgejo `phase-pr` smoke (`test/phase-pr.bats` or a new `test/forgejo-phase-pr.bats`), mirroring `test/forgejo-issue.bats`: `setup()` exports `IW_SERVER_DISABLED=1`, `mktemp -d`, `git init`, hand-written `.iw/config.conf` with `tracker.type = forgejo` + `repository` + `baseUrl`; with **no** `FORGEJO_API_TOKEN`, assert `./iw phase-pr ...` exits non-zero mentioning `FORGEJO_API_TOKEN`. Proves config-parse → `ForgeType.resolve` → Forgejo dispatch → token resolution (adapter HTTP/JSON is covered by the unit tests). Flag in review whether this smoke is warranted vs. relying on the harness tests + Phase-4 issue smoke (recommend the single smoke for parity).
- [x] [test] Run `./mill core.test` (or `./iw ./test unit`) and confirm the full `core.test` suite is green, including every new `ForgeTypeTest`, `ForgejoClientTest`, and `PhasePr`/`PhaseMerge` harness case.
- [x] [test] Run the BATS smoke (`./iw ./test e2e`, or the single file) if added; confirm green with `IW_SERVER_DISABLED=1`.
- [x] [impl] Compile core `-Werror` and confirm **no warnings**: `scala-cli compile --scalac-option -Werror core/`. In particular, confirm every `match ForgeType` (`ForgeType` methods, all four `LiveTrackerOps` arms, `PhaseMerge.validatePrUrl`, `PhaseAdvance`) stays exhaustive with no non-exhaustive-match warnings.
- [x] [integration] Verify acceptance criteria from `phase-05-context.md`: `ForgeType.resolve` returns `Forgejo` whenever `trackerType == Forgejo` (tracker wins over host) and `fromHost("codeberg.org") == Forgejo`, GitHub/GitLab unchanged; `ForgejoClient` has the three network-free unit-tested methods; all four `LiveTrackerOps` methods have a Forgejo arm and stay exhaustive; `phase-pr` creates a PR and prints `html_url`; `phase-merge` polls checks through `PhaseMerge` verdict logic and squash-merges + deletes branch on pass; forge ops obtain baseUrl + `FORGEJO_API_TOKEN` (+ head SHA) via command-layer `ForgeConfig` resolution (secrets stay in the imperative shell).
- [x] [integration] Confirm **no changes** to the issue-read/create paths (`Issue.scala` dispatch, `fetchForgejoIssue`/`createForgejoIssue`), `init`, or `doctor` (consumed unchanged from Phases 1–4).

**Phase Status:** Complete
