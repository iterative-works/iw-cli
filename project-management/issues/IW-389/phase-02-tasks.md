# Phase 2 Tasks: Forgejo HTTP adapter — issue read + create

**Issue:** IW-389 — Support Forgejo issue tracker
**Phase:** 2 — Forgejo HTTP adapter — issue read + create (Layer 2: HTTP adapter)

New `core/adapters/ForgejoClient.scala` HTTP adapter plus its unit test
`core/test/ForgejoClientTest.scala`, mirroring `LinearClient` and its
`SyncBackendStub`-based test. Injectable `SyncBackend` seam so all paths are
exercised network-free. No command wiring this phase. TDD: write the failing
tests first, then make them pass.

## Setup

- [x] [setup] Verify sttp + upickle are already on the classpath: `com.softwaremill.sttp.client4::core:4.0.15` and `com.lihaoyi::upickle:4.4.2` in `core.mvnDeps` (`build.mill:29–30`), munit in the test module (`build.mill:38`), and that `sttp.client4.testing.SyncBackendStub` resolves (it ships in sttp client4 core; `core/test/LinearClientMockTest.scala` already imports it). No build change expected — only edit `build.mill` if a missing dep actually surfaces.
- [x] [setup] Confirm the mirror anchors named in `phase-02-context.md` still hold: `LinearClient` injectable backend seam (`core/adapters/LinearClient.scala:8,16–17,64–73`), `CreatedIssue` definition (`core/adapters/LinearClient.scala:11`), domain `Issue` (`core/model/Issue.scala:6–12`), `YouTrackClient.buildCreateIssueBody` (`YouTrackClient.scala:207–218`), and GitLab ID normalization `split("-").last` (`GitLabClient.scala:262`).
- [x] [setup] Create the skeleton `core/adapters/ForgejoClient.scala` with the two-line PURPOSE header and `object ForgejoClient` (empty body / stub signatures only), and the skeleton `core/test/ForgejoClientTest.scala` as a munit `FunSuite` in package `iw.tests` with the `SyncBackendStub` import. Confirm `./mill core.test` runs (compiles and executes existing suites) before writing the new tests.

## Tests (write first — TDD)

- [x] [test] In `core/test/ForgejoClientTest.scala`, add a `buildIssueUrl` / `buildCreateIssueUrl` / `buildValidateTokenUrl` test: assert they produce `…/api/v1/repos/owner/repo/issues/123`, `…/api/v1/repos/owner/repo/issues`, and `…/api/v1/user` respectively, and that a trailing slash in `baseUrl` is handled by `stripSuffix("/")`.
- [x] [test] Add an ID-normalization test: `fetchIssue` (or `buildIssueUrl` with the pre-normalized number) for `IssueId` `"PROJ-123"` targets `…/issues/123` — assert via `SyncBackendStub.whenRequestMatches(_.uri…)` or by unit-testing `buildIssueUrl` with `"PROJ-123".split("-").last`.
- [x] [test] Add a `buildCreateIssueBody` test: parse the emitted string back with `ujson.read` and assert `title` and `body` fields equal the inputs and the JSON shape is `{"title":…,"body":…}`.
- [x] [test] Add a `fetchIssue` happy-path test: `SyncBackendStub.whenAnyRequest.thenRespondAdjust(json)` with canned Forgejo issue JSON (`number`, `title`, `body`, `state: "open"`, `assignee: {login: …}`) → `Right(Issue)` with `Issue.id` == the full passed-in ID, `status == "open"`, `assignee == Some("<login>")`, `description == Some(<body>)`.
- [x] [test] Add a `fetchIssue` null/missing-assignee test: one canned response with `assignee: null` and one with `assignee` absent → both yield `Issue.assignee == None`.
- [x] [test] Add a `fetchIssue` empty/null-body test: one canned response with `body: ""` and one with `body: null` → both yield `Issue.description == None`.
- [x] [test] Add a `fetchIssue` 401 test: `SyncBackendStub.whenAnyRequest.thenRespondUnauthorized()` → `Left`, message mentions token/expired.
- [x] [test] Add a `fetchIssue` 404 test → `Left`, message mentions not found and includes the issue ID.
- [x] [test] Add a `fetchIssue` malformed-JSON test: 200 with body `{ invalid json }` → `Left`, message mentions parse failure.
- [x] [test] Add a `fetchIssue` 500 test: `SyncBackendStub.whenAnyRequest.thenRespondServerError()` → `Left`, generic `Forgejo API error` message.
- [x] [test] Add a `createIssue` happy-path test: canned `201`/`Created` body with `number` + `html_url` → `Right(CreatedIssue(number.toString, html_url))`. Use `.thenRespondAdjust(json, StatusCode.Created)` if available; otherwise the `ResponseStub.adjust(json, StatusCode.Created)` form. **CLARIFY (resolve while writing):** confirm the exact `SyncBackendStub` helper for asserting a 201 against the version on the classpath — not a blocker, pick the form the codebase supports.
- [x] [test] Add a `createIssue` 401 test → `Left` token error.
- [x] [test] Add a `createIssue` 500 test → `Left` generic API error.
- [x] [test] Add `validateToken` tests: 200 from `GET /api/v1/user` → `true`; 401 → `false`.
- [x] [test] Run `./mill core.test` (or `./iw ./test unit`) and confirm the new tests fail for the right reason (compile errors / missing methods), not an unrelated failure.

## Implementation

- [x] [impl] In `ForgejoClient.scala`, add the imports and `private def defaultBackend: SyncBackend = DefaultSyncBackend()` seam, mirroring `LinearClient.scala:8,16–17`: `import sttp.client4.{SyncBackend, DefaultSyncBackend, basicRequest, UriContext}` and `import sttp.model.StatusCode`.
- [x] [impl] Implement the URL builders `buildIssueUrl(baseUrl, repository, issueNumber)`, `buildCreateIssueUrl(baseUrl, repository)`, and `buildValidateTokenUrl(baseUrl)` per the context spec, each using `baseUrl.stripSuffix("/")`.
- [x] [impl] Implement `buildCreateIssueBody(title, description): String` building `ujson.Obj("title" -> title, "body" -> description)` and `ujson.write`-ing it, mirroring `YouTrackClient.buildCreateIssueBody`.
- [x] [impl] Implement `parseFetchIssueResponse(json, issueIdValue): Either[String, Issue]` with `ujson.read`: read `title` and `state` (kept verbatim), guard optional `assignee` with `.obj.contains(...)` + `.isNull` reading `assignee.login`, guard optional `body` with `.obj.contains(...)` + `.isNull` + empty-string check, return `Issue(issueIdValue, title, status, assignee, description)`; catch exceptions → `Left("Failed to parse Forgejo response: …")`.
- [x] [impl] Implement `parseCreateIssueResponse(json): Either[String, CreatedIssue]` with `ujson.read`: `number.num.toInt.toString` and `html_url.str` → `Right(CreatedIssue(number, url))`; reuse the existing `CreatedIssue` (do not define a new one); catch → `Left("Failed to parse Forgejo response: …")`.
- [x] [impl] Implement `fetchIssue(issueId, repository, baseUrl, token, backend = defaultBackend): Either[String, Issue]`: normalize `issueId.value.split("-").last`, issue `basicRequest.get(uri"…")` with `Authorization: token <token>` and `Accept: application/json`, `.send(backend)`; match `response.code`: `Ok` → parse with full `issueId.value`, `Unauthorized` → token error, `NotFound` → not-found with the ID, `_` → generic API error; wrap in `try/catch` → `Left("Network error: …")`.
- [x] [impl] Implement `createIssue(repository, title, description, baseUrl, token, backend = defaultBackend): Either[String, CreatedIssue]`: build the body, `basicRequest.post(uri"…")` with `Authorization: token <token>`, `Content-Type: application/json`, `Accept: application/json`, `.body(body).send(backend)`; match `Created` (and defensively `Ok`) → `parseCreateIssueResponse`, `Unauthorized` → token error, `_` → generic API error; wrap in `try/catch`. **CLARIFY (resolve while implementing):** confirm `201 Created` is the success code on the target Forgejo version; the defensive `Ok` arm covers a `200`-returning deployment — note as a confirmed decision, not a blocker.
- [x] [impl] Implement `validateToken(baseUrl, token, backend = defaultBackend): Boolean`: `GET buildValidateTokenUrl(baseUrl)` with `Authorization: token <token>`, return `response.code == StatusCode.Ok`; `try/catch` → `false`. **CLARIFY (resolve while implementing):** keep the return type `Boolean` (matching `LinearClient.validateToken`) unless Phase 3 needs a reason string — note the decision, not a blocker.

## Integration & Verification

- [x] [test] Run `./mill core.test` (or `./iw ./test unit`) and confirm the full `core.test` suite is green, including every new Forgejo test case.
- [x] [impl] Compile core with `-Werror` and confirm **no warnings**: `scala-cli compile --scalac-option -Werror core/` (in particular no non-exhaustive-match warnings introduced by the new file).
- [x] [test] Verify all tests are network-free: only `SyncBackendStub` is used; no real Forgejo instance is contacted.
- [x] [test] Verify acceptance criteria from `phase-02-context.md`: file exists with two-line PURPOSE header; methods take an injectable `SyncBackend`; `fetchIssue` GETs `…/issues/{index}` with `Authorization: token <token>` and parses into domain `Issue` preserving the full passed-in ID; ID normalized via `split("-").last`; missing/null `assignee` and empty/null `body` → `None`; `createIssue` POSTs `{"title":…,"body":…}` and returns reused `CreatedIssue`; `validateToken` true on 200 / false otherwise; non-2xx and malformed JSON → meaningful `Left(String)` (401→token, 404→not found, parse→parse failure, other→generic) with no escaping exceptions; parsing uses `ujson`/`upickle` only.
- [x] [impl] Confirm no changes were made to commands, `CommandEnv`, `Issue.scala`, `Init`, or `Doctor` in this phase (adapter is unwired — Phase 3 wiring is out of scope).
