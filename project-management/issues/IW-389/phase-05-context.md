# Phase 05: PR creation + CI-check polling (forge parity)

**Issue:** IW-389 — Support Forgejo issue tracker
**Layers:** 2–4 (forge paths)
**Estimate:** 4–8h

## Goals

Phases 1–4 made Forgejo a first-class **issue tracker** (read + create, init,
doctor). This phase makes Forgejo a first-class **forge** so the workflow
commands that drive pull requests work against it:

- `./iw phase-pr` creates a pull request on a Forgejo-configured project
  (`POST /api/v1/repos/{owner}/{repo}/pulls`), returning the PR's `html_url`
  exactly as the GitHub/GitLab paths return theirs.
- `./iw phase-merge` polls Forgejo CI checks (commit-status endpoint) through
  the same `PhaseMerge` verdict logic, then squash-merges and deletes the branch
  (`POST /api/v1/repos/{owner}/{repo}/pulls/{index}/merge`,
  `Do: "squash"`, `delete_branch_after_merge: true`).
- `ForgeType` gains a `Forgejo` case and **resolves to Forgejo whenever the
  tracker type is Forgejo** (host detection is unreliable for self-hosted
  instances); `codeberg.org` also maps to Forgejo by host.
- All four `TrackerOps` forge methods (`createPullRequest`,
  `mergeSquashAndDelete`, `mergeWithDelete`, `fetchCheckStatuses`) gain a
  Forgejo arm in `LiveTrackerOps`, delegating to new `ForgejoClient` methods
  over HTTP (no CLI shell-out).
- `core.test` stays green; everything compiles with `-Werror` and no warnings.

The forge abstraction (`ForgeType` + the PR/merge/check methods on
`TrackerOps`) is **separate** from the issue-tracker abstraction
(`IssueTrackerType` + `fetchForgejoIssue`/`createForgejoIssue`). Phases 1–4
extended the latter; this phase extends the former. Both happen to point at the
same `ForgejoClient` adapter and the same Forgejo REST API.

## Scope

### In scope

- **`ForgeType.scala`** — add the `Forgejo` case; decide `cliTool`/`installUrl`
  for a CLI-less forge; fix `fromHost`/`resolve` so Forgejo wins by tracker type
  and `codeberg.org` maps to Forgejo by host.
- **`ForgejoClient.scala`** — three new HTTP methods: `createPullRequest`,
  `mergePullRequest` (squash + delete branch), `fetchCheckStatuses` (combined
  commit status). Same injectable-`SyncBackend` seam and `Either[String, …]`
  return shape as the existing issue methods and the GitHub/GitLab clients.
- **`LiveTrackerOps`** — `Forgejo` arms in `createPullRequest`,
  `mergeSquashAndDelete`, `mergeWithDelete`, `fetchCheckStatuses`.
- **The baseUrl + token + SHA contract gap** — decide how Forgejo's forge ops
  obtain the `baseUrl`, `FORGEJO_API_TOKEN`, and (for check polling) the head
  commit SHA, which the current `TrackerOps` signatures do not carry. See the
  "Design decision" section — this is the crux of the phase.
- **Tests** — extend `ForgejoClientTest` (PR create, merge, check-status parse);
  harness tests driving `PhasePr`/`PhaseMerge` through a Forgejo-resolved
  `ForgeType` via `FakeCommandEnv`/`FakeTracker`; `ForgeTypeTest` for resolution.

### Out of scope

- **Issue read/create** — delivered in Phases 2–4, consumed unchanged. This
  phase touches only the *forge* (PR/merge/check) paths.
- **Init/doctor** — finished in Phase 4. No new config fields are introduced;
  Forgejo's `repository` + required `baseUrl` already exist.
- **CI-workflow *file* detection** (`CIChecks`, the doctor static check) — done
  in Phase 4. This phase polls **live commit statuses**, a different concern.
- **Recovery-prompt / auto-retry logic** (`PhaseMerge` model) — already
  forge-agnostic; it operates on `List[CICheckResult]` and needs no change.

## Dependencies on prior phases

### Phase 1 (model/config)

- `IssueTrackerType.Forgejo` (`Config.scala:103`) — the discriminator
  `ForgeType.resolve` reads to pick the Forgejo forge.
- `TrackerConfig.repository` + `trackerBaseUrl` (the required Forgejo
  `baseUrl`) — the forge ops need both; they already round-trip through config.

### Phase 2 (adapter)

- `ForgejoClient` with the injectable `SyncBackend` seam, `Authorization: token
  <token>` header convention, `ujson` value-tree parsing, and the
  `buildIssueUrl`-style pure URL/body builders. The three new methods mirror
  this exact shape.

### Phase 3 (wiring + dispatch)

- `Constants.EnvVars.ForgejoApiToken = "FORGEJO_API_TOKEN"` — the source of the
  token for the forge ops, resolved the same way `Issue.scala`'s
  `forgejoToken(env)` does.
- `TrackerOps` is the seam the commands depend on; `FakeTracker` already records
  per-forge calls — extend its existing PR/merge/check recorders (which today
  only see GitHub/GitLab) to assert the Forgejo arm is reached.

### Phase 4 (init + doctor)

- No direct dependency. Phase 4's `CIChecks` Forgejo arm is the *static* file
  check; this phase is the *live* status poll. They are independent.

## Component specifications

### `core/model/ForgeType.scala` — add `Forgejo`, fix resolution

Today (`ForgeType.scala:6–48`): `enum ForgeType: case GitHub, GitLab`, with
`cliTool` (`gh`/`glab`), `installUrl`, `fromHost` (github.com → GitHub, else →
GitLab), `fromRemote`, and `resolve(remoteOpt, trackerType)` which prefers the
remote host and falls back to `GitHub` only for a GitHub tracker (else GitLab).

**Add the case:**

```scala
enum ForgeType:
  case GitHub, GitLab, Forgejo
```

**`cliTool` / `installUrl` for a CLI-less forge.** Forgejo PR/merge/check go
through `ForgejoClient` over HTTP — there is **no CLI binary** to invoke or
install. These two methods exist to power the `gh`/`glab` "is the tool
installed?" prerequisite checks in `GitHubClient`/`GitLabClient`, which the
Forgejo path does not use. Two viable options:

- **Preferred:** make both return an `Option[String]` (`None` for Forgejo), and
  update the (few) call sites to skip the prerequisite check when there is no
  CLI. This is honest about the domain — a forge may have no CLI.
- **Smaller-change alternative:** keep `String` and return a sentinel
  (`cliTool = ""`, `installUrl = ""` or the Forgejo docs URL). Cheaper but
  leaves a "CLI tool named empty-string" lie in the model.

> CLARIFY (resolve during implementation): which of the two. Audit the call
> sites of `cliTool`/`installUrl` first (`rg "\.cliTool|\.installUrl" core/`);
> if they are all inside GitHub/GitLab-guarded branches, the `Option` change is
> localized and preferred. If they fan out widely, the sentinel may be the
> smaller honest-enough change. Do **not** invent a fake CLI name.

**`fromHost` — add `codeberg.org`:**

```scala
def fromHost(host: String): ForgeType =
  if host == "github.com" then GitHub
  else if host == "codeberg.org" then Forgejo
  else GitLab
```

Self-hosted Forgejo hosts are arbitrary and indistinguishable from self-hosted
GitLab by hostname alone, so `fromHost` still falls through to `GitLab` for
unknown hosts — that misclassification is corrected by `resolve` (below), which
is the function the commands actually call.

**`resolve` — tracker type must win for Forgejo.** The current `resolve` trusts
the remote host first. For Forgejo that is wrong: a self-hosted instance's host
resolves to `GitLab`. The fix: when the tracker type is Forgejo, the forge is
Forgejo regardless of host.

```scala
def resolve(
    remoteOpt: Option[GitRemote],
    trackerType: IssueTrackerType
): ForgeType =
  if trackerType == IssueTrackerType.Forgejo then Forgejo
  else
    remoteOpt.flatMap(r => fromRemote(r).toOption).getOrElse {
      trackerType match
        case IssueTrackerType.GitHub => GitHub
        case _                       => GitLab
    }
```

Rationale: the iw-cli config's `tracker.type` is the authoritative, explicit
signal; the remote host is a heuristic that only helps when the tracker type is
ambiguous. For GitHub/GitLab the host heuristic stays first (preserving today's
behavior — e.g. a GitHub tracker pushing to a GitLab mirror). For Forgejo,
where the heuristic is actively wrong, the explicit config wins. This is the
**single most load-bearing change in the phase**: every command resolves its
forge through `resolve`, so getting this right makes `phase-pr`/`phase-merge`
dispatch to the Forgejo arms.

### `core/adapters/ForgejoClient.scala` — three new forge methods

Mirror the existing issue methods: pure URL/body builders + pure response
parsers, then a method that wires them through the injectable `SyncBackend`,
returning `Either[String, …]`. Auth header `Authorization: token <token>`,
`Accept: application/json`, `ujson` value-tree parsing.

**1. `createPullRequest`** → `POST /api/v1/repos/{owner}/{repo}/pulls`

```scala
def buildCreatePullRequestUrl(baseUrl: String, repository: String): String
def buildCreatePullRequestBody(
    headBranch: String,
    baseBranch: String,
    title: String,
    body: String
): String  // ujson.Obj("head" -> .., "base" -> .., "title" -> .., "body" -> ..)
def parseCreatePullRequestResponse(json: String): Either[String, PullRequest]

def createPullRequest(
    repository: String,
    headBranch: String,
    baseBranch: String,
    title: String,
    body: String,
    baseUrl: String,
    token: ApiToken,
    backend: SyncBackend = defaultBackend
): Either[String, PullRequest]
```

The response carries both `number` and `html_url`. `PhasePr` consumes the URL
(string) today; `PhaseMerge` later needs the PR `number` and the head SHA for
check polling. Return a small `PullRequest(number: Int, htmlUrl: String,
headSha: String)` (read `head.sha` from the create response) so the number/SHA
are available without a second round-trip — but see the contract-gap note: the
current `TrackerOps.createPullRequest` returns only `String`, so the
LiveTrackerOps arm will project `PullRequest.htmlUrl` out. Confirm the exact
PR-response field for the head SHA against the Forgejo API (`head.sha`).

**2. `mergePullRequest`** (squash + delete branch) →
`POST /api/v1/repos/{owner}/{repo}/pulls/{index}/merge`

```scala
def buildMergePullRequestUrl(
    baseUrl: String,
    repository: String,
    index: Int
): String
def buildMergePullRequestBody(): String
    // ujson.Obj("Do" -> "squash", "delete_branch_after_merge" -> true)

def mergePullRequest(
    repository: String,
    index: Int,
    baseUrl: String,
    token: ApiToken,
    backend: SyncBackend = defaultBackend
): Either[String, Unit]
```

Forgejo's merge endpoint is keyed by the PR **index** (its `number`), not the
URL. `mergeSquashAndDelete`/`mergeWithDelete` in `TrackerOps` receive a `prUrl`
string — the Forgejo arm must extract the index from that URL (the PR
`html_url` ends in `/pulls/{index}`), a small pure helper
(`extractPullRequestIndex(prUrl): Either[String, Int]`). Both
`mergeSquashAndDelete` and `mergeWithDelete` map to the same Forgejo squash
merge with branch deletion (Forgejo has no separate "with delete" vs "squash
and delete" CLI distinction; both set `delete_branch_after_merge: true`).

**3. `fetchCheckStatuses`** → combined commit status

```scala
def buildCommitStatusUrl(
    baseUrl: String,
    repository: String,
    sha: String
): String  // GET /api/v1/repos/{owner}/{repo}/commits/{sha}/status
def parseCommitStatusResponse(
    json: String
): Either[String, List[CICheckResult]]

def fetchCheckStatuses(
    repository: String,
    sha: String,
    baseUrl: String,
    token: ApiToken,
    backend: SyncBackend = defaultBackend
): Either[String, List[CICheckResult]]
```

Map the combined-status JSON to the existing `CICheckResult(name, status: 
CICheckStatus, url: Option[String])` (`PhaseMerge.scala:20–24`). The combined
endpoint returns a `statuses` array of `{ context, state, target_url }`; map
each `state` to `CICheckStatus`:

| Forgejo `state` | `CICheckStatus` |
|-----------------|-----------------|
| `success`       | `Passed`        |
| `failure`/`error` | `Failed`      |
| `pending`       | `Pending`       |
| (other)         | `Unknown`       |

(Mirror `GitHubClient.parseGhChecksJson` `:815–843` and
`GitLabClient.parseGlabJobsJson` `:732–747` for the name/state/url mapping
shape.) An empty `statuses` array → `Right(Nil)`, which `PhaseMerge.evaluateChecks`
treats as `NoChecksFound` (proceed), matching the GitHub "no checks reported"
behavior.

### `core/commands/LiveCommandEnv.scala` — `LiveTrackerOps` Forgejo arms

Add a `case ForgeType.Forgejo =>` arm to each of the four methods
(`LiveCommandEnv.scala:140–227`), delegating to the new `ForgejoClient` methods.
Each arm must supply `baseUrl` + `token` (+ `sha` for checks) — which is the
contract gap addressed next.

### Design decision: the baseUrl / token / SHA contract gap

The four `TrackerOps` forge methods (`CommandEnv.scala:117–145`) thread
`gitlabHost: Option[String]` for GitLab self-hosting, but carry **no
`baseUrl`, no token, and no commit SHA** — because GitHub/GitLab get those from
their CLI's ambient auth and from `gh`/`glab` resolving the PR's commit
internally. Forgejo's HTTP client needs all three explicitly:

- `baseUrl` — lives in `config.trackerBaseUrl` (required for Forgejo).
- token — `FORGEJO_API_TOKEN` from the environment.
- head SHA — needed only by `fetchCheckStatuses`; GitHub/GitLab pass `prNumber`
  and let the CLI find the commit, but the Forgejo combined-status endpoint is
  keyed by SHA, not PR number.

This is the central design choice of the phase. Options:

- **Option A — resolve at the `LiveTrackerOps` layer (preferred for the
  signatures, but read-env-in-Live is a smell).** Keep the `TrackerOps`
  signatures unchanged; inside each `case ForgeType.Forgejo` arm read
  `sys.env("FORGEJO_API_TOKEN")` and... but the arm has **no `baseUrl`** — the
  method only receives `gitlabHost`. So this option cannot work without also
  routing `baseUrl` in. Rejected on its own.

- **Option B — widen the forge-method signatures with a forge-config
  parameter (preferred).** Introduce a small `ForgeConfig` (or reuse a focused
  case class) carrying the optional `baseUrl` and token, replacing or
  supplementing `gitlabHost: Option[String]`. The command layer (`PhasePr`,
  `PhaseMerge`) already holds `config` (a `ProjectConfiguration`) and `env` in
  its `Resolved` struct, so it resolves `baseUrl` from `config.trackerBaseUrl`
  and the token from env **once**, then passes it down. GitHub/GitLab arms
  ignore the Forgejo fields. This keeps secret/config resolution in the
  command (imperative shell), not buried in `LiveTrackerOps`, and is consistent
  with how `gitlabHost` is already resolved in the command and threaded down
  (`PhasePr.scala:62`, `PhaseMerge.scala:77`).

- **Option C — fold `gitlabHost` and the new fields into one forge-context
  type.** Same as B but also collapses the existing `gitlabHost` parameter into
  the new type, reducing the per-method parameter count. Cleaner long-term, but
  touches the GitHub/GitLab call paths too (wider blast radius). Reasonable if
  the churn is acceptable; otherwise B (additive) is the smaller change.

**Recommendation:** Option B (additive forge-config parameter, resolved in the
command layer). It keeps token/baseUrl resolution at the edge, leaves the
GitHub/GitLab paths untouched, and matches the existing `gitlabHost` threading
pattern. Trade-off: the four signatures grow by one parameter that two of three
forges ignore — acceptable, and a future refactor can fold `gitlabHost` in
(Option C) as its own ticket.

**The SHA for check polling.** `fetchCheckStatuses(forge, prNumber, repository,
gitlabHost)` has no SHA. For Forgejo, the head SHA is available from the
`createPullRequest` response (the `PullRequest.headSha` proposed above), which
`PhasePr` produces and `PhaseMerge` re-resolves. Two sub-options:

- Thread the head SHA into the forge-config parameter (resolved in `PhaseMerge`
  by either reading it from the PR via a cheap `GET /pulls/{index}` or from the
  review-state the PR step persisted), **or**
- have the Forgejo `fetchCheckStatuses` arm do a `GET /pulls/{index}` first to
  read `head.sha`, then the combined-status call (one extra round-trip, but
  keeps the SHA entirely inside the adapter and off the shared signature).

> CLARIFY (resolve during implementation): which SHA-resolution sub-option.
> The second (adapter does the `/pulls/{index}` lookup) keeps the shared
> signature smallest and is self-contained — recommended unless the extra
> round-trip is a concern. Note `PhaseMerge` derives `prNumber` from the PR URL
> today (`extractPrNumber`-style); the Forgejo index == that number.

## API contracts (Forgejo REST, Gitea-derived, under `{baseUrl}/api/v1`)

Auth header on every call: `Authorization: token <token>`.

- **Create PR:** `POST /repos/{owner}/{repo}/pulls`
  - body: `{ "head": "<headBranch>", "base": "<baseBranch>", "title": "...",
    "body": "..." }`
  - success: `201 Created`; response carries `number` (int), `html_url`
    (string), and `head.sha` (string).
- **Merge PR:** `POST /repos/{owner}/{repo}/pulls/{index}/merge`
  - body: `{ "Do": "squash", "delete_branch_after_merge": true }`
  - success: `200 OK` (Forgejo returns 200 on a successful merge).
- **Combined commit status:** `GET /repos/{owner}/{repo}/commits/{sha}/status`
  - response: `{ "state": "...", "statuses": [ { "context": "...", "state":
    "...", "target_url": "..." }, ... ] }`
  - map `statuses[].state` to `CICheckStatus` per the table above; empty
    `statuses` → `Right(Nil)`.

Error mapping mirrors the existing issue methods: `401 → "API token is invalid
or expired"`, `404 → not found`, other → `"Forgejo API error: <code>"`, and a
`catch` → `"Network error: <msg>"`.

## Testing requirements

TDD throughout: write the failing parse/builder test first, implement to green.
Run `./mill core.test` (or `./iw ./test unit`) for unit/harness; the BATS smoke
runs under `./iw ./test e2e`.

### Unit — `core/test/ForgejoClientTest.scala` (extend)

Add cases mirroring the existing 33, all network-free via `SyncBackendStub`:

1. **URL/body builders** — `buildCreatePullRequestUrl`,
   `buildMergePullRequestUrl` (index in path), `buildCommitStatusUrl`
   (trailing-slash handling on `baseUrl`); `buildCreatePullRequestBody` exact
   keys (`head`/`base`/`title`/`body`), `buildMergePullRequestBody` exact keys
   (`Do=squash`, `delete_branch_after_merge=true`).
2. **`extractPullRequestIndex`** — parses `.../pulls/42` → `Right(42)`;
   malformed URL → `Left`.
3. **`createPullRequest`** — happy path (201 → `PullRequest(number, htmlUrl,
   headSha)` from canned JSON), 401, 404, 422, malformed JSON.
4. **`mergePullRequest`** — happy path (200 → `Right(())`), 401, 404, error JSON.
5. **`fetchCheckStatuses` / `parseCommitStatusResponse`** — success/failure/
   error/pending state mapping; empty `statuses` → `Right(Nil)`; malformed JSON
   → `Left`; missing fields handled.

### Unit — `core/test/ForgeTypeTest.scala`

1. `resolve(None, IssueTrackerType.Forgejo)` → `Forgejo`.
2. `resolve(Some(selfHostedRemote), IssueTrackerType.Forgejo)` → `Forgejo`
   (tracker type wins over a host that `fromHost` would call GitLab).
3. `fromHost("codeberg.org")` → `Forgejo`; `fromHost("github.com")` →
   `GitHub` (unchanged); `fromHost("gitlab.example.com")` → `GitLab` (unchanged).
4. Regression: `resolve(Some(githubRemote), IssueTrackerType.GitHub)` → `GitHub`,
   `resolve` for GitLab tracker/host unchanged.

### Harness — `PhasePrHarnessTest.scala` / `PhaseMergeHarnessTest.scala`

Drive `PhasePr.run` / `PhaseMerge.run` through `FakeCommandEnv` with a
Forgejo-typed config so `ForgeType.resolve` yields `Forgejo`:

1. **`phase-pr` on a Forgejo project** → `FakeTracker.createPullRequest` is
   invoked with `forge == ForgeType.Forgejo` and the resolved baseUrl/token (via
   the forge-config parameter); the printed PR URL is the fake's return.
2. **`phase-merge` on a Forgejo project** → `FakeTracker.fetchCheckStatuses` is
   invoked with `forge == ForgeType.Forgejo`; an all-passing scripted verdict
   drives `mergeSquashAndDelete`/`mergeWithDelete` with `forge == Forgejo`.
3. Error wiring: missing `FORGEJO_API_TOKEN` (or missing `baseUrl`) surfaces a
   clear `Left`/error exit before any forge call (mirrors `Issue.scala`'s
   token/baseUrl guards).

Extend `FakeTracker` so its PR/merge/check recorders capture the new
forge-config (baseUrl/token) extras, matching the Phase 3 `kind = "forgejo"`
recorder convention for the issue methods.

### E2E smoke

The existing `phase-pr.bats` / `phase-merge.bats` exercise the GitHub/GitLab
paths via CLI stubs. Forgejo is **direct HTTP** with no CLI to stub, so — per
the Phase 4 precedent (`forgejo-issue.bats`) and the project testing guide
("BATS keeps the wiring smoke test only") — a hermetic **no-token error-wiring**
smoke is sufficient: with a Forgejo config but no `FORGEJO_API_TOKEN`,
`./iw phase-pr ...` exits non-zero mentioning `FORGEJO_API_TOKEN`. The adapter's
HTTP/JSON behavior is covered by the unit tests above; the smoke proves
config-parse → `ForgeType.resolve` → Forgejo dispatch → token resolution.

> Decision to confirm in review: whether a new Forgejo `phase-pr`/`phase-merge`
> BATS smoke is warranted at all, or whether the harness tests (which exercise
> the full dispatch through `FakeCommandEnv`) plus the Phase 4 issue smoke are
> enough. Recommend a single hermetic no-token smoke for parity with Phase 4.

## Acceptance criteria

- [ ] `ForgeType` has a `Forgejo` case; `resolve` returns `Forgejo` whenever
      `trackerType == IssueTrackerType.Forgejo` (tracker type wins over host),
      and `fromHost("codeberg.org") == Forgejo`. GitHub/GitLab resolution is
      unchanged (regression tests green).
- [ ] `ForgejoClient` has `createPullRequest`, `mergePullRequest` (squash +
      delete branch), and `fetchCheckStatuses`, each network-free unit-tested
      via `SyncBackendStub`, returning the same `Either`/domain shapes as the
      GitHub/GitLab clients.
- [ ] `LiveTrackerOps.{createPullRequest, mergeSquashAndDelete, mergeWithDelete,
      fetchCheckStatuses}` each have a `Forgejo` arm; the `ForgeType` matches
      stay exhaustive.
- [ ] `./iw phase-pr` creates a PR on a Forgejo-configured project and prints
      its `html_url`.
- [ ] `./iw phase-merge` polls Forgejo CI checks through `PhaseMerge` verdict
      logic and squash-merges with branch deletion when checks pass.
- [ ] Forgejo forge ops obtain `baseUrl` + `FORGEJO_API_TOKEN` (+ head SHA for
      checks) via the chosen contract design (recommended: command-layer
      resolution into a forge-config parameter); secret/config resolution stays
      in the imperative shell.
- [ ] New `ForgeTypeTest`, `ForgejoClientTest`, and `PhasePr`/`PhaseMerge`
      Forgejo harness cases are green; full `core.test` stays green.
- [ ] Compiles with `-Werror` and **no warnings**.
- [ ] No changes to the issue-read/create paths (`Issue.scala` dispatch,
      `fetchForgejoIssue`/`createForgejoIssue`), init, or doctor.

## Files to modify / create

| File | Change | Mirror |
|------|--------|--------|
| `core/model/ForgeType.scala` | Add `Forgejo` case; `cliTool`/`installUrl` for CLI-less forge; `fromHost` codeberg arm; `resolve` tracker-type-wins-for-Forgejo. | existing GitHub/GitLab arms |
| `core/adapters/ForgejoClient.scala` | Add `createPullRequest`, `mergePullRequest`, `fetchCheckStatuses` + pure URL/body builders, response parsers, `extractPullRequestIndex`. | existing `fetchIssue`/`createIssue` (`:144–211`); `GitHubClient.parseGhChecksJson` (`:815`) |
| `core/commands/CommandEnv.scala` | Widen the four forge methods with a forge-config parameter (baseUrl/token), if Option B/C chosen (`:117–145`). | existing `gitlabHost` parameter |
| `core/commands/LiveCommandEnv.scala` | `Forgejo` arms in `createPullRequest`, `mergeSquashAndDelete`, `mergeWithDelete`, `fetchCheckStatuses` (`:140–227`). | existing GitHub/GitLab arms |
| `core/commands/PhasePr.scala` / `PhaseMerge.scala` | Resolve Forgejo `baseUrl`/token/SHA in the command and thread into the forge-config parameter (`PhasePr.scala:62,113–145`; `PhaseMerge.scala:77,282–289`). | existing `gitlabHost` resolution |
| `core/test/ForgeTypeTest.scala` | Forgejo resolution + codeberg host + GitHub/GitLab regression. | — |
| `core/test/ForgejoClientTest.scala` | PR create, merge, check-status, index-extract, builder cases. | existing 33 issue cases |
| `core/test/PhasePrHarnessTest.scala` / `PhaseMergeHarnessTest.scala` | Forgejo dispatch + error-wiring cases. | existing GitHub/GitLab harness cases |
| `core/test/fixtures/FakeCommandEnv.scala` | `FakeTracker` forge recorders capture Forgejo forge-config extras. | Phase 3 issue recorders (`kind = "forgejo"`) |
| `test/phase-pr.bats` (or new) | Optional hermetic no-token Forgejo smoke. | `test/forgejo-issue.bats` |

## Notes / divergences from the analysis

- **`ForgeType.resolve` precedence inversion for Forgejo** is the key
  architectural decision: for GitHub/GitLab the remote host wins, for Forgejo
  the tracker type wins. This is justified because self-hosted Forgejo hosts are
  not host-distinguishable from self-hosted GitLab, so the explicit config is
  the only reliable signal. Flag in review.
- **The forge-method signatures grow** (the baseUrl/token/SHA contract gap).
  The analysis anticipated "extend the `ForgeType`/PR-creation paths" but did
  not specify *how* Forgejo gets its baseUrl/token through signatures shaped for
  CLI forges. Recommended: additive forge-config parameter resolved in the
  command layer (Option B). The cleaner full collapse of `gitlabHost` into one
  forge-context type (Option C) is a reasonable larger change; deferring it
  keeps this phase's blast radius small.
- **CLI-less forge in `ForgeType`** (`cliTool`/`installUrl`): preferred fix is
  `Option[String]` returning `None` for Forgejo, pending a call-site audit. Do
  not invent a fake CLI name.
- **Check-status SHA resolution** is left as a CLARIFY between threading the SHA
  from the PR-create response vs. the adapter doing a `GET /pulls/{index}`
  lookup; the latter keeps the shared signature smallest. Decide during
  implementation.
