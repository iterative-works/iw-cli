# Phase 3: Capability wiring + command dispatch/auth

**Issue:** IW-389 — Support Forgejo issue tracker
**Layers:** 3 (Capability Wiring) + 4 (Command Dispatch & Auth)
**Estimate:** 3–6h

## Goals

After this phase, `./iw issue <id>` works end-to-end for a Forgejo-configured
project. Concretely and testably:

- `TrackerOps` (the capability seam in `core/commands/CommandEnv.scala`) exposes
  a `fetchForgejoIssue(...)` method (and `createForgejoIssue(...)` for parity
  with the other create siblings — see Scope) keyed off the Forgejo adapter's
  real signatures.
- `LiveTrackerOps` (`core/commands/LiveCommandEnv.scala`) implements those
  methods by delegating to `ForgejoClient.fetchIssue` / `ForgejoClient.createIssue`
  with the real `DefaultSyncBackend` (the adapter's default backend).
- `FakeTracker` (`core/test/fixtures/FakeCommandEnv.scala`) records Forgejo
  fetch/create calls the same way it records the Linear/YouTrack/GitHub/GitLab
  ones, so harness tests can assert dispatch and argument wiring without I/O.
- `Issue.scala` dispatches the `IssueTrackerType.Forgejo` arm (replacing the
  Phase-2 `Left("Forgejo issue fetch is not supported")` / `Left("Forgejo issue
  creation is not supported")` stubs at `Issue.scala:163–164` and `:230–231`),
  resolving the token from `FORGEJO_API_TOKEN`, reading the required `baseUrl`
  and `repository` from config, and calling `env.tracker.fetchForgejoIssue(...)`.
- A new env-var constant `Constants.EnvVars.ForgejoApiToken = "FORGEJO_API_TOKEN"`
  exists and is used for token resolution.
- A `*HarnessTest.scala` drives `Issue.run` through `FakeCommandEnv` for the
  Forgejo path: happy fetch, missing-token error, missing-baseUrl error,
  adapter-`Left` surfacing. (Extend `IssueHarnessTest.scala`, the existing
  per-tracker harness file.)
- `core.test` is fully green; everything compiles with `-Werror` and no warnings.

## Scope

### In scope

- **`TrackerOps.fetchForgejoIssue`** added to the capability trait
  (`CommandEnv.scala`, alongside the sibling fetch methods at lines 154–174).
- **`TrackerOps.createForgejoIssue`** added for parity. Create dispatch **is**
  reachable from a command today: `Issue.runCreate` → `Issue.createIssue`
  (`Issue.scala:181–231`) already routes GitHub/GitLab/Linear/YouTrack creates,
  and Phase 2 left a `IssueTrackerType.Forgejo` create stub at `Issue.scala:230`.
  So `createForgejoIssue` is genuinely wired (not dead parity) and must be added.
- **`LiveTrackerOps`** implementation of both methods (`LiveCommandEnv.scala`,
  after the sibling block at lines 236–301), delegating to `ForgejoClient`.
- **`FakeTracker`** extension (`FakeCommandEnv.scala`): `fetchForgejoIssue` and
  `createForgejoIssue` recording into the existing `fetchIssueCalls` /
  `createIssueCalls` buffers with `kind = "forgejo"`.
- **`Issue.scala`** Forgejo dispatch arms (fetch + create), a
  `forgejoToken(env)` helper reading `FORGEJO_API_TOKEN`, and reading
  `repository` + required `baseUrl` from config.
- **`Constants.EnvVars.ForgejoApiToken`** new constant.
- **teamPrefix resolution for Forgejo** in `Issue.scala:89–93` (the
  `resolveIssueId` match that currently only adds `teamPrefix` for
  `GitHub | GitLab`). Forgejo addresses issues by numeric index with an optional
  `teamPrefix` (TD-2/TD-4), so `IssueTrackerType.Forgejo` must join that arm for
  `iw issue 42` → `PREFIX-42` to work. See Approach for the decision.
- **Audit of every other exhaustive `match IssueTrackerType`** that the Phase-1
  enum addition would force. See "Match-site audit" below — the result is that
  **no new compiler-forced arms remain** in command code (Phase 1 already
  handled the model-layer exhaustive matches; the command-layer matches all use
  catch-all `_`). This is verification work, not new arms.

### Out of scope

- PR creation, CI-check polling, `ForgeType` Forgejo arm — **Phase 5**.
  (`ForgeType.resolve` at `ForgeType.scala:42–46` only maps GitHub today and is
  not exhaustive on `IssueTrackerType`; leave it.)
- `Init` / `Doctor` integration and BATS smoke — **Phase 4**. The Phase-2 stubs
  in `Init.scala:162–163` (`collectTrackerDetails`) and the `askForTracker` menu
  (`Init.scala:108–121`, no Forgejo option) stay untouched here.
- The Forgejo adapter itself — delivered in Phase 2, consumed unchanged here.

## Dependencies

### From Phase 1 (Layer 1 — model/config)

- `IssueTrackerType.Forgejo` exists (`Config.scala:103`).
- `Constants.TrackerTypeValues.Forgejo = "forgejo"` (`Constants.scala`), and the
  HOCON serializer round-trips `tracker.type = forgejo` (`Config.scala:211, 252`).
- `TrackerConfig` fields available via `ProjectConfiguration` accessors
  (`Config.scala:127–139`): `repository: Option[String]`,
  `trackerBaseUrl: Option[String]` (the `baseUrl` field), `teamPrefix:
  Option[String]`, `team: String`.

### From Phase 2 (Layer 2 — `core/adapters/ForgejoClient.scala`)

Exact signatures consumed (verified in source):

```scala
def fetchIssue(
    issueId: IssueId,
    repository: String,
    baseUrl: String,
    token: ApiToken,
    backend: SyncBackend = defaultBackend
): Either[String, Issue]                                  // ForgejoClient.scala:144

def createIssue(
    repository: String,
    title: String,
    description: String,
    baseUrl: String,
    token: ApiToken,
    backend: SyncBackend = defaultBackend
): Either[String, CreatedIssue]                           // ForgejoClient.scala:186

def validateToken(
    baseUrl: String,
    token: ApiToken,
    backend: SyncBackend = defaultBackend
): Boolean                                                // ForgejoClient.scala:224
```

- `CreatedIssue` is defined in `core/adapters/LinearClient.scala:11` (`case class
  CreatedIssue(id: String, url: String)`) and re-exported via the `adapters`
  package; `ForgejoClient.createIssue` returns it. Already imported into both
  `CommandEnv.scala` and `LiveCommandEnv.scala` as
  `iw.core.adapters.{... CreatedIssue ...}`.
- ID normalization (`split("-").last` → numeric index) is handled **inside**
  `ForgejoClient.fetchIssue` (`ForgejoClient.scala:152`); the wiring passes the
  full `IssueId` through and must **not** pre-strip it. `Issue.id` in the result
  preserves the full ID.
- `validateToken` is **not** consumed in this phase (it is for Phase 4's
  doctor/init). Only `fetchIssue` / `createIssue` are wired here.

## Approach

The wiring mirrors the **YouTrack** precedent most closely, because Forgejo
shares YouTrack's shape: a token from an env var **plus** a required `baseUrl`
from config (YouTrack lacks `repository`; Forgejo adds it, like GitLab). So
Forgejo = YouTrack's token+baseUrl resolution + GitLab's `repository` field.

### 1. Add the env-var constant

In `Constants.EnvVars` (`Constants.scala`), add:

```scala
val ForgejoApiToken = "FORGEJO_API_TOKEN"
```

mirroring `LinearApiToken` / `YouTrackApiToken` (lines 10–11).

### 2. `TrackerOps` capability methods

Add to the `TrackerOps` trait (`CommandEnv.scala`). `fetchForgejoIssue` mirrors
`fetchYouTrackIssue` (the sibling I am mirroring, `CommandEnv.scala:159–163`):

```scala
def fetchYouTrackIssue(
    issueId: IssueId,
    baseUrl: String,
    token: ApiToken
): Either[String, Issue]
```

→ Forgejo adds `repository` (string), matching the adapter signature:

```scala
def fetchForgejoIssue(
    issueId: IssueId,
    repository: String,
    baseUrl: String,
    token: ApiToken
): Either[String, Issue]

def createForgejoIssue(
    repository: String,
    title: String,
    description: String,
    baseUrl: String,
    token: ApiToken
): Either[String, CreatedIssue]
```

`createForgejoIssue` mirrors `createYouTrackIssue`
(`CommandEnv.scala:183–189`), substituting `project` → `repository`:

```scala
def createYouTrackIssue(
    project: String,
    title: String,
    description: String,
    baseUrl: String,
    token: ApiToken
): Either[String, CreatedIssue]
```

Note the `backend` parameter from the adapter is **not** on the `TrackerOps`
surface — it is an adapter-internal test seam; `LiveTrackerOps` lets it default.
This matches how the Linear/YouTrack wiring omits backend at the trait level.

### 3. `LiveTrackerOps` implementation

In `LiveCommandEnv.scala`, after the sibling block (lines 236–301), and add
`ForgejoClient` to the `iw.core.adapters.{...}` import at the top (lines 6–25):

```scala
def fetchForgejoIssue(
    issueId: IssueId,
    repository: String,
    baseUrl: String,
    token: ApiToken
): Either[String, Issue] =
  ForgejoClient.fetchIssue(issueId, repository, baseUrl, token)

def createForgejoIssue(
    repository: String,
    title: String,
    description: String,
    baseUrl: String,
    token: ApiToken
): Either[String, CreatedIssue] =
  ForgejoClient.createIssue(repository, title, description, baseUrl, token)
```

(Each lets the adapter's `backend` default to `DefaultSyncBackend()` — direct
delegation, exactly like `fetchYouTrackIssue` → `YouTrackClient.fetchIssue` at
`LiveCommandEnv.scala:242–247`.)

### 4. `FakeTracker` recording

In `FakeCommandEnv.scala`, after `fetchGitLabIssue` (lines 512–522) and
`createGitLabIssue` (lines 566–578), record into the existing buffers with
`kind = "forgejo"`, mirroring the YouTrack fakes (which carry the extra
`baseUrl`/`token` keys) plus the `repository` key from GitLab:

```scala
def fetchForgejoIssue(
    issueId: IssueId,
    repository: String,
    baseUrl: String,
    token: ApiToken
): Either[String, Issue] =
  fetchIssueCalls += FetchIssueCall(
    "forgejo",
    issueId.value,
    Map("repository" -> repository, "baseUrl" -> baseUrl, "token" -> token.value)
  )
  fetchIssueResultRef.get()

def createForgejoIssue(
    repository: String,
    title: String,
    description: String,
    baseUrl: String,
    token: ApiToken
): Either[String, CreatedIssue] =
  createIssueCalls += CreateIssueCall(
    "forgejo",
    title,
    description,
    Map("repository" -> repository, "baseUrl" -> baseUrl, "token" -> token.value)
  )
  createIssueResultRef.get()
```

`FetchIssueCall` / `CreateIssueCall` already model `kind`/`extras`
(`FakeCommandEnv.scala:448–458`); no new case classes needed.

### 5. `Issue.scala` dispatch + token + config wiring

**Token helper** — add `forgejoToken`, mirroring `youtrackToken`
(`Issue.scala:106–112`):

```scala
private def forgejoToken(env: CommandEnv): Either[String, ApiToken] =
  env.envVars
    .get(Constants.EnvVars.ForgejoApiToken)
    .flatMap(ApiToken.apply)
    .toRight(
      s"${Constants.EnvVars.ForgejoApiToken} environment variable is not set"
    )
```

**Fetch dispatch** — replace the stub at `Issue.scala:163–164`. Forgejo needs
**both** a token (like YouTrack) and a `repository` (like GitLab/GitHub), and a
required `baseUrl` (like YouTrack). Compose the YouTrack baseUrl pattern
(`:126–133`) with the GitHub/GitLab repository check (`:135–140`):

```scala
case IssueTrackerType.Forgejo =>
  for
    token <- forgejoToken(env)
    repository <- config.repository.toRight(
      "Forgejo repository not configured. Run 'iw init' first."
    )
    baseUrl <- config.trackerBaseUrl.toRight(
      s"Forgejo base URL not configured. Add 'baseUrl' to tracker section in ${Constants.Paths.ConfigFile}"
    )
    issue <- env.tracker.fetchForgejoIssue(issueId, repository, baseUrl, token)
  yield issue
```

**Create dispatch** — replace the stub at `Issue.scala:230–231`, mirroring the
YouTrack create arm (`:215–228`) with `repository` instead of `config.team`:

```scala
case IssueTrackerType.Forgejo =>
  for
    token <- forgejoToken(env)
    repository <- config.repository.toRight(
      "Forgejo repository not configured. Run 'iw init' first."
    )
    baseUrl <- config.trackerBaseUrl.toRight(
      s"Forgejo base URL not configured. Add 'baseUrl' to tracker section in ${Constants.Paths.ConfigFile}"
    )
    created <- env.tracker.createForgejoIssue(
      repository, title, description, baseUrl, token
    )
  yield created
```

**teamPrefix for ID normalization** — `resolveIssueId` (`Issue.scala:89–93`)
currently adds the config `teamPrefix` only for `GitHub | GitLab`. Forgejo
addresses issues by numeric index with an optional `teamPrefix` (TD-2/TD-4 —
same as GitHub/GitLab). Add `Forgejo` to that arm so `iw issue 42` composes
`PREFIX-42`:

```scala
val teamPrefix = config.trackerType match
  case IssueTrackerType.GitHub | IssueTrackerType.GitLab |
      IssueTrackerType.Forgejo =>
    config.teamPrefix
  case _ => None
```

The adapter strips back to the numeric index (`split("-").last`) regardless, so
both `iw issue 42` and `iw issue PREFIX-42` resolve to `…/issues/42`.

> Decision to confirm in review: applying `teamPrefix` to Forgejo IDs. It is the
> right parity with GitHub/GitLab per TD-2, but it changes ID resolution
> behavior. Keeping it is consistent with the analysis; the alternative
> (`_ => None`, no prefix) would force users to always type the bare number.

## Component specifications / API contracts

### `TrackerOps` (CommandEnv.scala) — methods to add

```scala
def fetchForgejoIssue(
    issueId: IssueId,
    repository: String,
    baseUrl: String,
    token: ApiToken
): Either[String, Issue]

def createForgejoIssue(
    repository: String,
    title: String,
    description: String,
    baseUrl: String,
    token: ApiToken
): Either[String, CreatedIssue]
```

Mirrored siblings (quoted from source):
- `fetchYouTrackIssue(issueId: IssueId, baseUrl: String, token: ApiToken):
  Either[String, Issue]` (`CommandEnv.scala:159–163`).
- `createYouTrackIssue(project: String, title: String, description: String,
  baseUrl: String, token: ApiToken): Either[String, CreatedIssue]`
  (`CommandEnv.scala:183–189`).

### `LiveTrackerOps` (LiveCommandEnv.scala) — delegations

Both delegate directly to `ForgejoClient`, defaulting the adapter `backend`.
Mirrors `fetchYouTrackIssue` → `YouTrackClient.fetchIssue`
(`LiveCommandEnv.scala:242–247`) and `createYouTrackIssue` →
`YouTrackClient.createIssue` (`:274–281`).

### `FakeTracker` (FakeCommandEnv.scala) — recorders

`kind = "forgejo"`, extras carry `repository` + `baseUrl` + `token`. Reuses
`FetchIssueCall` / `CreateIssueCall` (`FakeCommandEnv.scala:448–458`); no new
types. Default result refs (`fetchIssueResultRef` / `createIssueResultRef`,
lines 463–469) are shared with the other trackers and are scripted via the
existing `setFetchIssueResult` / `setCreateIssueResult` (lines 471–474).

## Files to modify

| File | Change | Mirror |
|------|--------|--------|
| `core/model/Constants.scala` | Add `EnvVars.ForgejoApiToken = "FORGEJO_API_TOKEN"`. | `LinearApiToken` / `YouTrackApiToken` (lines 10–11) |
| `core/commands/CommandEnv.scala` | Add `fetchForgejoIssue` + `createForgejoIssue` to `TrackerOps`. | `fetchYouTrackIssue` (159–163), `createYouTrackIssue` (183–189) |
| `core/commands/LiveCommandEnv.scala` | Add both impls delegating to `ForgejoClient`; add `ForgejoClient` to the `iw.core.adapters.{...}` import (6–25). | `fetchYouTrackIssue`→`YouTrackClient` (242–247), `createYouTrackIssue`→`YouTrackClient` (274–281) |
| `core/test/fixtures/FakeCommandEnv.scala` | Add both recorders to `FakeTracker`. | `fetchYouTrackIssue` fake (489–499), `createYouTrackIssue` fake (538–551), `fetchGitLabIssue` fake (512–522) |
| `core/commands/Issue.scala` | Add `forgejoToken` helper; replace fetch stub (163–164) and create stub (230–231) with real dispatch; add `Forgejo` to the `resolveIssueId` teamPrefix match (89–93). | `youtrackToken` (106–112), YouTrack fetch arm (126–133), YouTrack create arm (215–228), GitHub/GitLab repo check (135–140) |
| `core/test/IssueHarnessTest.scala` | Add Forgejo harness cases (TDD: write first). | Existing YouTrack/GitHub cases in the same file |

### Match-site audit (Layer-4 requirement)

`rg -n "IssueTrackerType" core/` shows every match. Phase 1 already added Forgejo
arms to the **exhaustive model-layer** matches:
`Config.toTrackerString` (`:207–211`), `Config` parse (`:244–252`),
`TrackerUrlBuilder` (`:16–29`), `RepoUrlBuilder` (`:29–42`), `CIChecks`
(`:21–34`), `teamIdentifier` (`:145–148`).

**Command-layer** matches all use a catch-all `_` and are therefore **not**
compiler-forced (no new arm required):
- `Issue.scala:89–93` (teamPrefix) — has `_ => None`; we add Forgejo by
  *choice* (TD-4), not because the compiler demands it.
- `Status.scala:26–30`, `Rm.scala:61–64`, `Open.scala:70–73`, `Start.scala:73–76`
  (teamPrefix) — all `_ => None`. Left as-is: these are not the `issue` command
  and applying a prefix there is a Phase-4/later concern, not Phase 3's
  `./iw issue` goal. **Out of scope** for this phase; flag in review if uniform
  teamPrefix handling across commands is wanted.
- `Doctor.scala:107–110` (CI platform) — `_ => "Unknown"`; Forgejo arm is
  **Phase 4** (doctor).
- `ForgeType.scala:42–46` — not exhaustive on `IssueTrackerType` (only maps
  GitHub); **Phase 5**.

Net: the only compiler-forced exhaustive match in Phase 3's reach is
`Issue.scala`'s `fetchIssue`/`createIssue` dispatch — already carrying Phase-2
stubs we now fill in. The `resolveIssueId` teamPrefix change is a deliberate
behavior addition, not a compiler requirement.

## Testing strategy

TDD: write the failing harness cases first, watch them fail (the Phase-2 stubs
return `Left("…not supported")`, so the happy-path tests fail on exit code),
then implement the wiring to green.

Extend `core/test/IssueHarnessTest.scala` (the existing per-tracker harness),
mirroring the YouTrack cases. A `forgejoConfig` fixture:

```scala
private val forgejoConfig =
  """project { name = sample }
    |tracker {
    |  type = forgejo
    |  repository = "owner/sample"
    |  teamPrefix = "SAMP"
    |  baseUrl = "https://forgejo.example.com"
    |}
    |""".stripMargin
```

Cases (each drives `Issue.run` through `FakeCommandEnv` / `FakeTracker`):

1. **Forgejo fetch without token → exit 1**, stderr mentions
   `Constants.EnvVars.ForgejoApiToken` (mirrors "linear fetch without token",
   `IssueHarnessTest.scala:64–72`).
2. **Forgejo fetch without baseUrl → exit 1**, stderr mentions `baseUrl`
   (mirrors "youtrack fetch without base url", `:154–168`). Set the token first
   so baseUrl is the failing step.
3. **Forgejo fetch without repository → exit 1**, stderr mentions
   "Forgejo repository not configured" (mirrors "github fetch without
   configured repository", `:128–141`).
4. **Forgejo fetch happy path → exit 0**: set `FORGEJO_API_TOKEN`,
   `setFetchIssueResult(Right(Issue(...)))`; assert
   `fetchIssueCallList.head.kind == "forgejo"`, `extras("repository") ==
   "owner/sample"`, `extras("baseUrl") == "https://forgejo.example.com"`,
   `extras("token")` matches, and last-seen updated (mirrors "youtrack fetch
   happy path", `:170–182`).
5. **Forgejo fetch numeric arg composes team prefix** (`iw issue 42` →
   `SAMP-42`): assert `fetchIssueCallList.head.issueId == "SAMP-42"` (mirrors
   "github fetch: numeric arg composes team prefix", `:118–126`). This is the
   test that pins the `resolveIssueId` teamPrefix change.
6. **Forgejo adapter Left surfaces**: `setFetchIssueResult(Left("boom"))` →
   exit 1, stderr contains `boom` (error-surfacing seam).
7. **Forgejo create happy path → exit 0**: `setCreateIssueResult(Right(
   CreatedIssue(...)))`; assert `createIssueCallList.head.kind == "forgejo"`,
   title/description/extras wired, stdout "Issue created" (mirrors "issue
   create for Linear", `:224–236`).
8. **Forgejo create without token → exit 1** (token resolved before adapter
   call).

Unit-test seam: dispatch/token/config logic is exercised purely through
`FakeTracker` (no `ForgejoClient`, no network). The adapter's own HTTP/JSON
behavior is already covered by Phase 2's `ForgejoClientTest` (33 tests). This
phase adds **no** adapter tests.

Run:
```bash
./mill core.test          # munit, this module
# or
./iw ./test unit          # core.test + dashboard.test via Mill
```

## Acceptance criteria

- [ ] `Constants.EnvVars.ForgejoApiToken = "FORGEJO_API_TOKEN"` exists.
- [ ] `TrackerOps` has `fetchForgejoIssue` and `createForgejoIssue` with the
      signatures above; `LiveTrackerOps` implements both by delegating to
      `ForgejoClient.fetchIssue` / `ForgejoClient.createIssue`.
- [ ] `FakeTracker` records both calls with `kind = "forgejo"` and
      `repository`/`baseUrl`/`token` extras.
- [ ] `Issue.scala` fetch and create dispatch the `IssueTrackerType.Forgejo`
      arm (no `…not supported` stub remains), resolving `FORGEJO_API_TOKEN`,
      required `baseUrl`, and `repository` from config; missing token, missing
      baseUrl, and missing repository each yield a clear `Left` / exit 1.
- [ ] `iw issue 42` for a Forgejo project with `teamPrefix` composes the full ID
      (`resolveIssueId` includes Forgejo in the teamPrefix arm).
- [ ] All `IssueTrackerType` matches compile exhaustively; no non-exhaustive
      warnings introduced.
- [ ] Compiles with `-Werror` and **no warnings**.
- [ ] New Forgejo harness cases in `IssueHarnessTest.scala` are green; the full
      `core.test` suite stays green.
- [ ] No changes to `Init`, `Doctor`, `ForgeType`, the Forgejo adapter, or any
      PR/CI path in this phase.
