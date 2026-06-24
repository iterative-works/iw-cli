# Phase 3 Tasks: Capability wiring + command dispatch/auth

**Issue:** IW-389 — Support Forgejo issue tracker
**Phase:** 3 — Capability wiring + command dispatch/auth (Layers 3 + 4)

Wire the Phase-2 `ForgejoClient` through the `TrackerOps` capability seam and the
`Issue` command so `./iw issue <id>` works end-to-end for a Forgejo-configured
project. Mirrors the YouTrack precedent (token + required `baseUrl`) plus the
GitLab `repository` field. No adapter changes, no Init/Doctor/PR/CI work. TDD:
add the failing harness cases in `IssueHarnessTest.scala` first, then fill in the
wiring to green. The Phase-2 `Left("…not supported")` stubs in `Issue.scala`
(`:163–164` fetch, `:230–231` create) currently make the happy-path cases fail,
which is the failing-test starting point.

## Setup

- [x] [setup] Confirm the mirror anchors named in `phase-03-context.md` still hold: `TrackerOps.fetchYouTrackIssue` (`core/commands/CommandEnv.scala:159–163`) and `createYouTrackIssue` (`:183–189`); `LiveTrackerOps` YouTrack delegations (`core/commands/LiveCommandEnv.scala:242–247` fetch, `:274–281` create) and the `iw.core.adapters.{...}` import (`:6–25`); `FakeTracker` YouTrack/GitLab fakes (`core/test/fixtures/FakeCommandEnv.scala:489–499`, `:512–522`, `:538–551`) plus `FetchIssueCall`/`CreateIssueCall` (`:448–458`) and the result refs/setters (`:463–474`); `Issue.scala` `youtrackToken` (`:106–112`), `resolveIssueId` teamPrefix match (`:89–93`), Forgejo fetch stub (`:163–164`) and create stub (`:230–231`).
- [x] [setup] Add `EnvVars.ForgejoApiToken = "FORGEJO_API_TOKEN"` to `core/model/Constants.scala`, mirroring `LinearApiToken` / `YouTrackApiToken` (lines 10–11).

## Tests (write first — TDD)

Extend `core/test/IssueHarnessTest.scala` (the existing per-tracker harness),
mirroring the YouTrack/GitHub cases. Add a `forgejoConfig` fixture with
`type = forgejo`, `repository = "owner/sample"`, `teamPrefix = "SAMP"`,
`baseUrl = "https://forgejo.example.com"` (per the context spec). Each case
drives `Issue.run` through `FakeCommandEnv` / `FakeTracker`.

- [x] [test] Add the `forgejoConfig` HOCON fixture string (project + tracker block) per `phase-03-context.md`.
- [x] [test] Forgejo fetch without token → exit 1, stderr mentions `Constants.EnvVars.ForgejoApiToken` (`FORGEJO_API_TOKEN`). Mirror "linear fetch without token" (`IssueHarnessTest.scala:64–72`).
- [x] [test] Forgejo fetch without baseUrl → exit 1, stderr mentions `baseUrl`. Set `FORGEJO_API_TOKEN` first so baseUrl is the failing step. Mirror "youtrack fetch without base url" (`:154–168`).
- [x] [test] Forgejo fetch without repository → exit 1, stderr mentions "Forgejo repository not configured". Mirror "github fetch without configured repository" (`:128–141`).
- [x] [test] Forgejo fetch happy path → exit 0: set `FORGEJO_API_TOKEN`, `setFetchIssueResult(Right(Issue(...)))`; assert `fetchIssueCallList.head.kind == "forgejo"`, `extras("repository") == "owner/sample"`, `extras("baseUrl") == "https://forgejo.example.com"`, `extras("token")` matches the env token, and last-seen issue is updated. Mirror "youtrack fetch happy path" (`:170–182`).
- [x] [test] Forgejo fetch numeric arg composes team prefix (`iw issue 42` → `SAMP-42`): assert `fetchIssueCallList.head.issueId == "SAMP-42"`. Mirror "github fetch: numeric arg composes team prefix" (`:118–126`). This pins the `resolveIssueId` teamPrefix change.
- [x] [test] Forgejo adapter `Left` surfaces: `setFetchIssueResult(Left("boom"))` → exit 1, stderr contains `boom`.
- [x] [test] Forgejo create happy path → exit 0: `setCreateIssueResult(Right(CreatedIssue(...)))`; assert `createIssueCallList.head.kind == "forgejo"`, title/description and `repository`/`baseUrl`/`token` extras wired, stdout "Issue created". Mirror "issue create for Linear" (`:224–236`).
- [x] [test] Forgejo create without token → exit 1 (token resolved before adapter call).
- [x] [test] Run `./mill core.test` (or `./iw ./test unit`) and confirm the new cases fail for the right reason (missing methods / stub `Left("…not supported")`), not an unrelated failure.

## Implementation

- [x] [impl] Add `fetchForgejoIssue(issueId: IssueId, repository: String, baseUrl: String, token: ApiToken): Either[String, Issue]` and `createForgejoIssue(repository: String, title: String, description: String, baseUrl: String, token: ApiToken): Either[String, CreatedIssue]` to the `TrackerOps` trait in `core/commands/CommandEnv.scala`, mirroring `fetchYouTrackIssue` (`:159–163`) + GitLab's `repository`, and `createYouTrackIssue` (`:183–189`) with `project → repository`. Do NOT put the adapter `backend` parameter on the trait surface.
- [x] [impl] In `core/commands/LiveCommandEnv.scala`, add `ForgejoClient` to the `iw.core.adapters.{...}` import (`:6–25`), then implement both methods in `LiveTrackerOps` (after the sibling block `:236–301`): `fetchForgejoIssue` → `ForgejoClient.fetchIssue(issueId, repository, baseUrl, token)`; `createForgejoIssue` → `ForgejoClient.createIssue(repository, title, description, baseUrl, token)`. Let the adapter `backend` default. Mirror the YouTrack delegations (`:242–247`, `:274–281`).
- [x] [impl] In `core/test/fixtures/FakeCommandEnv.scala`, add both recorders to `FakeTracker`: `fetchForgejoIssue` records into `fetchIssueCalls` as `FetchIssueCall("forgejo", issueId.value, Map("repository" -> repository, "baseUrl" -> baseUrl, "token" -> token.value))` then returns `fetchIssueResultRef.get()`; `createForgejoIssue` records into `createIssueCalls` as `CreateIssueCall("forgejo", title, description, Map("repository" -> repository, "baseUrl" -> baseUrl, "token" -> token.value))` then returns `createIssueResultRef.get()`. Reuse existing case classes/refs (`:448–458`, `:463–469`). Mirror the YouTrack fakes (`:489–499`, `:538–551`) + GitLab's `repository` extra (`:512–522`).
- [x] [impl] Add the `forgejoToken(env: CommandEnv): Either[String, ApiToken]` helper to `core/commands/Issue.scala`, reading `Constants.EnvVars.ForgejoApiToken` via `env.envVars.get(...).flatMap(ApiToken.apply).toRight("FORGEJO_API_TOKEN environment variable is not set")`. Mirror `youtrackToken` (`:106–112`).
- [x] [impl] Replace the Forgejo fetch stub (`Issue.scala:163–164`) with the real `case IssueTrackerType.Forgejo` arm: for-comprehension resolving `forgejoToken(env)`, required `config.repository` (`toRight("Forgejo repository not configured. Run 'iw init' first.")`), required `config.trackerBaseUrl` (`toRight(s"Forgejo base URL not configured. Add 'baseUrl' to tracker section in ${Constants.Paths.ConfigFile}")`), then `env.tracker.fetchForgejoIssue(issueId, repository, baseUrl, token)`. Compose the YouTrack baseUrl pattern (`:126–133`) with the GitHub/GitLab repository check (`:135–140`).
- [x] [impl] Replace the Forgejo create stub (`Issue.scala:230–231`) with the real `case IssueTrackerType.Forgejo` arm: for-comprehension resolving token, required `repository`, required `baseUrl` (same error messages), then `env.tracker.createForgejoIssue(repository, title, description, baseUrl, token)`. Mirror the YouTrack create arm (`:215–228`) substituting `repository` for `config.team`.
- [x] [impl] Add `IssueTrackerType.Forgejo` to the `resolveIssueId` teamPrefix match (`Issue.scala:89–93`) so it joins the `GitHub | GitLab` arm and `iw issue 42` composes `PREFIX-42`. **Review-confirm decision (per context):** applying `teamPrefix` to Forgejo IDs is the right parity with GitHub/GitLab (TD-2/TD-4), but it changes ID resolution behavior — keep it (the alternative `_ => None` would force users to always type the bare number). Flag for confirmation in review.

## Integration & Verification

- [x] [test] Run `./mill core.test` (or `./iw ./test unit`) and confirm the full `core.test` suite is green, including every new Forgejo harness case.
- [x] [impl] Compile core with `-Werror` and confirm **no warnings**: `scala-cli compile --scalac-option -Werror core/`.
- [x] [integration] Confirm every exhaustive `match IssueTrackerType` compiles without non-exhaustive-match warnings: the only compiler-forced site in this phase's reach is `Issue.scala`'s fetch/create dispatch (now filled in). Per the context match-site audit, the command-layer teamPrefix matches in `Status.scala`/`Rm.scala`/`Open.scala`/`Start.scala` use catch-all `_` and are intentionally left as-is; `Doctor.scala` (CI platform) is Phase 4; `ForgeType.scala` is Phase 5.
- [x] [integration] Confirm no changes were made to `Init.scala`, `Doctor.scala`, `ForgeType.scala`, the Forgejo adapter, or any PR/CI path in this phase (out of scope — Phases 4 and 5).
- [x] [integration] Verify acceptance criteria from `phase-03-context.md`: `Constants.EnvVars.ForgejoApiToken` exists; `TrackerOps`/`LiveTrackerOps` expose+implement both methods delegating to `ForgejoClient`; `FakeTracker` records both with `kind = "forgejo"` + `repository`/`baseUrl`/`token` extras; `Issue.scala` dispatches both arms with no `…not supported` stub remaining; missing token/baseUrl/repository each yield a clear exit 1; `iw issue 42` composes the full ID.
**Phase Status:** Complete
