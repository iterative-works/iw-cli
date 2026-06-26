# Phase 2: Forgejo HTTP adapter — issue read + create

**Issue:** IW-389 — Support Forgejo issue tracker
**Layer:** 2 — HTTP adapter (depends on Layer 1 domain; no command wiring yet)
**Estimate:** 5–8h

## Goals

Add a `core/adapters/ForgejoClient.scala` HTTP adapter that talks to the
Forgejo (Gitea-compatible) REST API directly over HTTP, mirroring the existing
`LinearClient` adapter. Specifically, after this phase:

- `ForgejoClient.fetchIssue(...)` does `GET
  {baseUrl}/api/v1/repos/{owner}/{repo}/issues/{index}` and parses the JSON
  into the domain `Issue`.
- `ForgejoClient.createIssue(...)` does `POST
  {baseUrl}/api/v1/repos/{owner}/{repo}/issues` and returns a `CreatedIssue`.
- `ForgejoClient.validateToken(...)` does a cheap authenticated `GET
  {baseUrl}/api/v1/user` and reports validity.
- Every method takes an **injectable `sttp.client4.SyncBackend`** (defaulting to
  the real backend) so the unit test in `core/test/ForgejoClientTest.scala`
  exercises all paths against canned JSON with **no network**.

This is the I/O adapter only. It is **not** wired into any command in this phase
— nothing calls `ForgejoClient` yet. It compiles and is fully unit-tested in
isolation.

## Scope

### In scope

- New file `core/adapters/ForgejoClient.scala`:
  - `fetchIssue(issueId, repository, baseUrl, token, backend): Either[String, Issue]`
  - `createIssue(repository, title, description, baseUrl, token, backend): Either[String, CreatedIssue]`
  - `validateToken(baseUrl, token, backend): Boolean`
  - Pure helpers split out for direct unit testing (request URL builders,
    request-body builder, response parsers) — matching how
    `LinearClient`/`YouTrackClient` expose `build…`/`parse…` methods.
  - Issue-ID normalization to the bare numeric index (Forgejo's API path uses
    the numeric `{index}`), mirroring GitHub/GitLab (see Dependencies).
  - `Authorization: token <token>` header on every request.
- New unit test `core/test/ForgejoClientTest.scala` mirroring
  `core/test/LinearClientMockTest.scala` (the `SyncBackendStub`-based test).

### Out of scope (later phases)

- `TrackerOps` capability wiring (`CommandEnv` / `LiveCommandEnv` /
  `FakeCommandEnv`) — **Phase 3**.
- Command dispatch in `Issue.scala` and token resolution (the
  `FORGEJO_API_TOKEN` env var and any auth lookup) — **Phase 3**.
- `Init` / `Doctor` integration and BATS smoke — **Phase 4**.
- PR creation, `ForgeType`, and CI-check polling — **Phase 5**.

No edits to `Issue.scala`, `CommandEnv`, `Issue.scala` command, `Init`,
`Doctor`, or any `commands/*.scala` in this phase.

## Dependencies

- **Prior layers needed:**
  - Phase 1 (Layer 1) is merged: `IssueTrackerType.Forgejo` exists and the
    `forgejo` config string round-trips. The adapter itself does **not**
    reference `IssueTrackerType` — it is keyed off it only by the *caller* in
    Phase 3 — but Phase 1 is the conceptual precondition.
  - Domain `Issue` (`core/model/Issue.scala:6–12`): `case class Issue(id:
    String, title: String, status: String, assignee: Option[String],
    description: Option[String])`. The adapter must produce exactly this.
  - `CreatedIssue` (`core/adapters/LinearClient.scala:11`): `case class
    CreatedIssue(id: String, url: String)`. **Reuse it** — it lives in the
    `adapters` package object scope already and is shared by
    `YouTrackClient`/`GitLabClient`. Do **not** define a new one.
  - `ApiToken` (`core/model/ApiToken.scala`): `token.value` returns the raw
    string; constructed via `ApiToken("…")` (returns `Option`) in tests.
  - `IssueId` (`core/model/IssueId.scala`): opaque `String`, `issueId.value`
    gives the `TEAM-NNN` string.
- **From sttp:** `sttp.client4.{SyncBackend, DefaultSyncBackend, basicRequest,
  UriContext}` and `sttp.model.StatusCode`. The test uses
  `sttp.client4.testing.SyncBackendStub`. All already on the classpath (see
  Files to Modify — no build change).
- **What depends on this later:** Phase 3's `TrackerOps`/`Issue.scala` dispatch
  calls `ForgejoClient.fetchIssue`/`createIssue`/`validateToken` once
  `IssueTrackerType.Forgejo` is matched and the token is resolved.

## Approach

### Which template to mirror — Linear, not YouTrack

Two existing direct-HTTP adapters exist, and they differ in **testability**:

- `LinearClient` (`core/adapters/LinearClient.scala`) uses
  `sttp.client4.basicRequest` and **threads an injectable `SyncBackend`** through
  every method (`backend: SyncBackend = defaultBackend`, then `.send(backend)`).
  This is the seam that makes its unit test
  (`core/test/LinearClientMockTest.scala`) network-free via `SyncBackendStub`.
- `YouTrackClient` (`core/adapters/YouTrackClient.scala`) uses
  `sttp.client4.quick.quickRequest` / `.send()` with **no injectable backend**.
  Its parse helpers are unit-tested, but its HTTP methods are **not** (they would
  hit the network). This is the pattern to avoid for Forgejo.

**`ForgejoClient` must follow the Linear seam** so its HTTP methods are testable.
Concretely, copy this shape from `LinearClient.scala:8,16–17`:

```scala
import sttp.client4.{SyncBackend, DefaultSyncBackend, basicRequest, UriContext}
import sttp.model.StatusCode

object ForgejoClient:
  private def defaultBackend: SyncBackend = DefaultSyncBackend()
```

Note `basicRequest.send(backend)` yields a response whose `.body` is
`Either[String, String]` (`Left` = non-2xx body / error, `Right` = success
body). `LinearClient.fetchIssue` (lines 64–73) handles this:

```scala
response.code match
  case StatusCode.Ok =>
    response.body match
      case Right(body) => parseFetchIssueResponse(body, issueIdValue)
      case Left(_)     => Left("Empty response body")
  case StatusCode.Unauthorized => Left("API token is invalid or expired")
  case _                       => Left(s"Forgejo API error: ${response.code}")
```

Wrap the whole thing in `try … catch case e: Exception => Left(s"Network error:
${e.getMessage}")`, exactly as Linear/YouTrack do.

### JSON: ujson / upickle (same as every other adapter)

All adapters parse with `ujson.read(json)` and navigate with
`parsed("field").str` / `.arr` / `.isNull` (see
`LinearClient.scala:116–162`, `YouTrackClient.scala:42–89`,
`GitLabClient.parseFetchIssueResponse` at `GitLabClient.scala:194–239`).
`ForgejoClient` must use the **same** `ujson` approach — `import
upickle.default.*` (or just `import ujson.*` as GitLab does) and
`ujson.read`. Do not introduce a new JSON library or `derives ReadWriter`
codec; navigate the `ujson.Value` tree directly.

For building the create-issue request body, mirror
`YouTrackClient.buildCreateIssueBody` (`YouTrackClient.scala:207–218`) which
builds a `ujson.Obj(...)` and `ujson.write`s it:

```scala
def buildCreateIssueBody(title: String, description: String): String =
  import upickle.default.*
  ujson.write(ujson.Obj("title" -> title, "body" -> description))
```

### Issue-ID normalization — bare numeric index

Forgejo's API path is `…/issues/{index}` where `{index}` is the **numeric**
issue number, exactly like GitHub/GitLab. GitLab extracts it with
`issueIdValue.split("-").last` (`GitLabClient.scala:262`, also
`GitHubClient.scala:330` and `:380`). `ForgejoClient` must do the same:

```scala
val issueNumber = issueId.value.split("-").last   // "PROJ-123" -> "123"
```

The domain `Issue.id` returned, however, should preserve the **full** ID
(`issueId.value`, e.g. `"PROJ-123"`) — GitLab does exactly this:
`parseFetchIssueResponse(jsonOutput, issueIdValue)` passes the full ID into the
`Issue` (`GitLabClient.scala:203,282`). Forgejo mirrors that: numeric index in
the URL, full ID in the `Issue`.

### Component spec — `ForgejoClient`

File header (two-line PURPOSE, per project convention):

```scala
// PURPOSE: Forgejo REST API client for issue read and create operations
// PURPOSE: Provides fetchIssue, createIssue and validateToken over injectable HTTP backend
```

#### URL builders (pure, unit-tested directly)

```scala
def buildIssueUrl(baseUrl: String, repository: String, issueNumber: String): String =
  val base = baseUrl.stripSuffix("/")
  s"$base/api/v1/repos/$repository/issues/$issueNumber"

def buildCreateIssueUrl(baseUrl: String, repository: String): String =
  val base = baseUrl.stripSuffix("/")
  s"$base/api/v1/repos/$repository/issues"

def buildValidateTokenUrl(baseUrl: String): String =
  s"${baseUrl.stripSuffix("/")}/api/v1/user"
```

`repository` is the `owner/repo` string from config (already validated to
`owner/repo` format in Phase 1's serializer); it slots directly into the path.
(`stripSuffix("/")` mirrors `YouTrackClient.scala:204,221`.)

#### `fetchIssue`

```scala
def fetchIssue(
    issueId: IssueId,
    repository: String,
    baseUrl: String,
    token: ApiToken,
    backend: SyncBackend = defaultBackend
): Either[String, Issue]
```

- Normalize: `val issueNumber = issueId.value.split("-").last`.
- `basicRequest.get(uri"${buildIssueUrl(...)}").header("Authorization", s"token
  ${token.value}").header("Accept", "application/json").send(backend)`.
- Match `response.code`: `Ok` → `parseFetchIssueResponse(body, issueId.value)`;
  `Unauthorized` → `Left("API token is invalid or expired")`; `NotFound` →
  `Left(s"Issue ${issueId.value} not found")`; `_` → `Left(s"Forgejo API error:
  ${response.code}")`. Wrap in try/catch for network errors.

#### `parseFetchIssueResponse` (pure, the heart of the test surface)

Forgejo/Gitea `GET …/issues/{index}` returns a single Issue JSON object. The
fields this phase reads (stable across Gitea and Forgejo):

| JSON field      | Type                         | Maps to            |
|-----------------|------------------------------|--------------------|
| `number`        | int                          | (not stored; we keep the full domain ID) |
| `title`         | string                       | `Issue.title`      |
| `body`          | string (may be `""`/`null`)  | `Issue.description`|
| `state`         | string: `"open"`/`"closed"`  | `Issue.status`     |
| `assignee`      | object or `null`             | `Issue.assignee`   |
| `assignee.login`| string                       | the assignee name  |

```scala
def parseFetchIssueResponse(json: String, issueIdValue: String): Either[String, Issue] =
  try
    import ujson.*
    val parsed = read(json)
    val title = parsed("title").str
    val status = parsed("state").str   // "open" / "closed", kept as-is

    val assignee =
      if parsed.obj.contains("assignee") && !parsed("assignee").isNull
      then Some(parsed("assignee")("login").str)
      else None

    val description =
      if parsed.obj.contains("body") && !parsed("body").isNull then
        val b = parsed("body").str
        if b.isEmpty then None else Some(b)
      else None

    Right(Issue(issueIdValue, title, status, assignee, description))
  catch case e: Exception => Left(s"Failed to parse Forgejo response: ${e.getMessage}")
```

Notes:
- Keep `state` verbatim (`"open"`/`"closed"`). GitLab *normalizes* `"opened"` →
  `"open"` (`GitLabClient.scala:211–214`) because GitLab uses `"opened"`;
  Forgejo already emits `"open"`/`"closed"`, so no remap is needed. **CLARIFY:**
  confirm whether downstream display expects a particular casing/vocabulary —
  if a later phase needs uniform status strings across trackers, normalization
  belongs there, not here. Defaulting to verbatim is consistent with how
  YouTrack/Linear pass the tracker's own state name through.
- `assignee` is a single object in Gitea/Forgejo (there is also an `assignees`
  array; the single `assignee` mirrors the first). Use `.assignee.login` to
  match the task spec; this parallels GitLab reading
  `assignees.head("username")` (`GitLabClient.scala:217–219`).
- Guard each optional field with `.obj.contains(...)` + `.isNull` exactly as
  Linear does (`LinearClient.scala:143–157`).

#### `createIssue`

```scala
def createIssue(
    repository: String,
    title: String,
    description: String,
    baseUrl: String,
    token: ApiToken,
    backend: SyncBackend = defaultBackend
): Either[String, CreatedIssue]
```

- Body: `buildCreateIssueBody(title, description)` → `{"title":…,"body":…}`.
- `basicRequest.post(uri"${buildCreateIssueUrl(...)}")
  .header("Authorization", s"token ${token.value}")
  .header("Content-Type", "application/json")
  .header("Accept", "application/json").body(body).send(backend)`.
- Forgejo returns **`201 Created`** on success (not 200). Match on
  `StatusCode.Created` (and accept `StatusCode.Ok` defensively). The response
  body is the created Issue object including `number` and `html_url`.
- `parseCreateIssueResponse(body)`:

```scala
def parseCreateIssueResponse(json: String): Either[String, CreatedIssue] =
  try
    val parsed = ujson.read(json)
    val number = parsed("number").num.toInt.toString
    val url    = parsed("html_url").str
    Right(CreatedIssue(number, url))
  catch case e: Exception => Left(s"Failed to parse Forgejo response: ${e.getMessage}")
```

  `CreatedIssue.id` is a `String` (`LinearClient.scala:11`), so stringify the
  numeric `number`. **CLARIFY:** confirm `201` is the success code on the target
  Forgejo version (Gitea/Forgejo `POST issues` returns `201`); if a deployment
  returns `200`, the defensive `Ok` arm covers it.

#### `validateToken`

```scala
def validateToken(
    baseUrl: String,
    token: ApiToken,
    backend: SyncBackend = defaultBackend
): Boolean =
  try
    val response = basicRequest
      .get(uri"${buildValidateTokenUrl(baseUrl)}")
      .header("Authorization", s"token ${token.value}")
      .send(backend)
    response.code == StatusCode.Ok
  catch case _: Exception => false
```

Mirrors `LinearClient.validateToken` (`LinearClient.scala:30–47`): returns
`Boolean`, `200` ⇒ valid. **CLARIFY:** the task lists the signature as
`validateToken(baseUrl, token)`. Linear's returns `Boolean`; keep `Boolean`
unless Phase 3 needs a reason string — flag if a richer `Either` return is
wanted.

### Error mapping summary

All errors are returned as `Left(String)` values (never thrown); network/parse
exceptions are caught and converted, per project convention and the existing
adapters. Non-2xx → `Left` with a message that distinguishes 401
(token), 404 (not found), and a generic `Forgejo API error: <code>` fallback.

## Files to Modify

| File | Change |
|------|--------|
| `core/adapters/ForgejoClient.scala` | **New.** `object ForgejoClient` with `fetchIssue`, `createIssue`, `validateToken`, plus pure `buildIssueUrl` / `buildCreateIssueUrl` / `buildValidateTokenUrl` / `buildCreateIssueBody` / `parseFetchIssueResponse` / `parseCreateIssueResponse` helpers. Injectable `SyncBackend = defaultBackend`. Reuses `CreatedIssue` and domain `Issue`. Two-line PURPOSE header. |
| `core/test/ForgejoClientTest.scala` | **New.** Munit `FunSuite` mirroring `LinearClientMockTest`, using `sttp.client4.testing.SyncBackendStub`. Package `iw.tests`. |
| `build.mill` | **No change expected.** `com.softwaremill.sttp.client4::core:4.0.15` and `com.lihaoyi::upickle:4.4.2` are already in `core.mvnDeps` (`build.mill:29–30`); the test module already pulls munit (`build.mill:38`) and `SyncBackendStub` ships in sttp client4 core (Linear's test already uses it). Verify only — do not edit unless a missing dep surfaces. |

## Testing Strategy

TDD: write `core/test/ForgejoClientTest.scala` first, watch it fail (red),
implement `ForgejoClient` to green. Mirror the structure of
`core/test/LinearClientMockTest.scala` — canned JSON + `SyncBackendStub`:

```scala
val backend = SyncBackendStub.whenAnyRequest.thenRespondAdjust(json)        // 200 + body
val backend = SyncBackendStub.whenAnyRequest.thenRespondUnauthorized()      // 401
val backend = SyncBackendStub.whenAnyRequest.thenRespondServerError()       // 500
```

For the `201`/`Created` create path, use
`.thenRespondAdjust(json, StatusCode.Created)` (the `thenRespondAdjust`
overload accepts a status; confirm against `LinearClientMockTest` /
`SyncBackendStub` API and use the form the codebase already relies on — if only
the body overload is available, use `.thenRespond(ResponseStub.adjust(json,
StatusCode.Created))` style). **CLARIFY:** verify the exact `SyncBackendStub`
helper for asserting a 201 response while writing the test.

Concrete cases to cover:

1. **`fetchIssue` happy path** — canned Forgejo issue JSON (`number`, `title`,
   `body`, `state: "open"`, `assignee: {login: …}`) parses into `Issue` with all
   fields populated; `Issue.id` is the full passed-in ID, `status == "open"`,
   `assignee == Some("<login>")`, `description == Some(<body>)`.
2. **`fetchIssue` null/missing assignee** — `assignee: null` (and an
   `assignee`-absent variant) → `Issue.assignee == None`.
3. **`fetchIssue` empty/null body** — `body: ""` and `body: null` →
   `Issue.description == None`.
4. **`fetchIssue` 401** → `Left`, message mentions token/expired.
5. **`fetchIssue` 404** → `Left`, message mentions not found (issue ID in it).
6. **`fetchIssue` malformed JSON** (`{ invalid json }`) → `Left`, message
   mentions parse failure.
7. **`fetchIssue` 500** → `Left`, generic API error.
8. **URL builders** — `buildIssueUrl`/`buildCreateIssueUrl`/`buildValidateTokenUrl`
   produce `…/api/v1/repos/owner/repo/issues/123` etc., and `stripSuffix("/")`
   handles a trailing slash in `baseUrl`.
9. **ID normalization** — `fetchIssue` with `IssueId` `"PROJ-123"` requests
   `…/issues/123` (assert via a `SyncBackendStub.whenRequestMatches(_.uri…)` or
   by unit-testing `buildIssueUrl` with the normalized number).
10. **`buildCreateIssueBody`** — emits valid JSON `{"title":…,"body":…}`
    (parse it back with `ujson.read` and assert fields).
11. **`createIssue` happy path** — canned `201` body with `number` + `html_url`
    → `Right(CreatedIssue(number.toString, html_url))`.
12. **`createIssue` 401** → `Left` token error.
13. **`createIssue` 500** → `Left` API error.
14. **`validateToken` 200** → `true`; **`validateToken` 401** → `false`.

Run:
```bash
./mill core.test         # munit, this module only
# or
./iw ./test unit         # core.test + dashboard.test via Mill
```

All tests must be network-free (only `SyncBackendStub`); no real Forgejo
instance is contacted.

## Acceptance Criteria

- [ ] `core/adapters/ForgejoClient.scala` exists, starts with the two-line
      `// PURPOSE:` header, and compiles with `-Werror` and **no warnings**.
- [ ] Methods take an injectable `SyncBackend` (default `DefaultSyncBackend()`);
      no method hits the network when a `SyncBackendStub` is supplied.
- [ ] `fetchIssue` issues `GET {baseUrl}/api/v1/repos/{owner}/{repo}/issues/{index}`
      with header `Authorization: token <token>`, and parses a canned Forgejo
      issue JSON into the domain `Issue` (title, body→description, state→status,
      assignee.login→assignee), preserving the full passed-in ID as `Issue.id`.
- [ ] Issue ID is normalized to the bare numeric index in the URL
      (`split("-").last`), matching GitHub/GitLab.
- [ ] Missing/null `assignee` and empty/null `body` yield `None`.
- [ ] `createIssue` issues `POST {baseUrl}/api/v1/repos/{owner}/{repo}/issues`
      with body `{"title":…,"body":…}` and `Authorization: token <token>`, and
      on success returns `CreatedIssue(number, html_url)` (reusing the existing
      `CreatedIssue`, not a new type).
- [ ] `validateToken` returns `true` on `200` from `GET /api/v1/user`, `false`
      otherwise.
- [ ] Non-2xx and malformed-JSON cases return `Left(String)` with meaningful
      messages (401 → token, 404 → not found, parse → parse failure, other →
      generic API error); no exceptions escape.
- [ ] Parsing uses `ujson`/`upickle` (the same approach as
      `LinearClient`/`YouTrackClient`/`GitLabClient`); no new JSON library.
- [ ] `core/test/ForgejoClientTest.scala` covers the cases above and is green;
      the full `core.test` suite stays green.
- [ ] No changes to commands, `CommandEnv`, `Issue.scala`, `Init`, or `Doctor`
      in this phase.
```
