# IW-389: Support Forgejo issue tracker

## Problem Statement

iw-cli currently integrates with four issue trackers: Linear, YouTrack, GitHub, and
GitLab. We need to add **Forgejo** as a fifth supported tracker so projects hosted on
Forgejo instances (e.g. Codeberg, or self-hosted Forgejo) can use iw-cli's issue
fetching and workflow tooling.

Forgejo is a self-hosted Git forge (a fork of Gitea). Its REST API lives under
`/api/v1` and is Gitea-derived — close in shape to GitHub/GitLab, but accessed
directly over HTTP rather than through an official `gh`/`glab`-style CLI.

The issue ships with **no description**, so the precise scope (read-only issue
fetching vs. full forge parity including PR/CI integration) is unsettled. The
CLARIFY markers below capture the decisions that must be made before
implementation; this is why the issue routes through the waterfall review gate.

### Acceptance Criteria (proposed — confirm during review)

- [ ] `./iw issue <id>` fetches and displays an issue from a configured Forgejo project.
- [ ] `./iw init --tracker=forgejo ...` configures a project for Forgejo (repository + base-url).
- [ ] `tracker.type = forgejo` round-trips correctly through `.iw/config.conf` (read + write).
- [ ] `./iw doctor` reports correct status for a Forgejo-configured project.
- [ ] Forgejo adapter is covered by unit tests mirroring the existing tracker adapters.

---

## Technical Decisions

### TD-1: Direct HTTP API, not a CLI shell-out

GitHub and GitLab adapters shell out to `gh`/`glab`. Forgejo has **no official
first-party CLI equivalent** — the closest is `tea` (the Gitea CLI), which is a
third-party dependency users would have to install and authenticate separately.

**Decision:** Implement `ForgejoClient` as a **direct HTTP adapter using `sttp`**,
mirroring `YouTrackClient` / `LinearClient` rather than `GitLabClient`. Auth is a
personal access token sent as `Authorization: token <token>`.

- Endpoint (fetch): `GET {baseUrl}/api/v1/repos/{owner}/{repo}/issues/{index}`
- Endpoint (create): `POST {baseUrl}/api/v1/repos/{owner}/{repo}/issues`
- Auth header: `Authorization: token <token>` (Forgejo also accepts `Bearer`)

See [CLARIFY: Adapter transport](#clarify-adapter-transport).

### TD-2: Config shape — `repository` + `baseUrl`, like self-hosted GitLab

Forgejo is always self-hosted (or on Codeberg), so a `baseUrl` is **required**, and
issues are addressed by `repository` (`owner/repo`). This is the GitLab-self-hosted
field set, combined with the token-via-env-var auth of YouTrack.

| Field        | Forgejo | Source analogue        |
|--------------|---------|------------------------|
| `repository` | required (`owner/repo`) | GitHub/GitLab |
| `baseUrl`    | required (instance URL) | YouTrack / GitLab self-hosted |
| `teamPrefix` | optional (2–10 uppercase) | GitHub/GitLab |
| `team`       | not used | — |

### TD-3: Auth via environment variable token

Token-based trackers read `LINEAR_API_TOKEN` / `YOUTRACK_API_TOKEN`. Forgejo follows
the same model with a new env var.

**Proposed:** `FORGEJO_API_TOKEN`. See [CLARIFY: Auth env var name](#clarify-auth-env-var-name).

### TD-4: Issue ID handling

GitHub/GitLab address issues by bare numeric index. Forgejo's API also uses a numeric
index (`/issues/{index}`). The `teamPrefix` (e.g. `IW-389`) is an iw-cli convention
for branch/worktree naming; the numeric tail (`389`) is what the Forgejo API receives —
same normalization GitHub/GitLab already perform.

---

## Architecture Design (Layer Decomposition)

This is an additive feature that threads a new tracker variant through the existing
tracker abstraction. Dependencies flow inward: the domain/config layer has no
dependencies; the adapter depends on the HTTP backend; wiring depends on the adapter;
commands depend on wiring. No existing layer needs to depend on anything new.

### Layer 1 — Domain & Configuration (no dependencies)

**Files:** `core/model/Config.scala`, `core/model/Constants.scala`

- Add `Forgejo` to `enum IssueTrackerType` (`core/model/Config.scala:102`).
- Add `Constants.TrackerTypeValues.Forgejo = "forgejo"`.
- Extend `TrackerConfig` validation: Forgejo requires `repository` and `baseUrl`
  (reuse existing validation predicates used for GitLab self-hosted + YouTrack).
- `ConfigSerializer` (`core/model/Config.scala:203–318`): parse and write
  `tracker.type = forgejo` plus its fields. This is pure (HOCON ↔ model).

**Interface/contract:** `IssueTrackerType` is the discriminator every downstream
`match` exhausts; adding the case forces the compiler to surface every site that must
handle Forgejo (a desirable failure mode — no silent gaps).

**Estimate:** 2–4h (incl. config round-trip unit tests).

### Layer 2 — Forgejo HTTP Adapter (depends on: model, sttp backend)

**File:** `core/adapters/ForgejoClient.scala` (new)

Mirror `YouTrackClient.scala`:
- `validateToken(baseUrl, token)` — cheap authenticated GET (e.g. `/api/v1/user`).
- `fetchIssue(issueId, repository, baseUrl, token): Either[String, Issue]` — GET the
  issue, parse JSON (`title`, `body`, `state`, `assignee.login`) into the domain
  `Issue` (`core/model/Issue.scala`).
- `createIssue(repository, title, description, baseUrl, token)` — POST. **(scope —
  see CLARIFY)**
- `listRecentIssues` / `searchIssues` — **(scope — see CLARIFY)**

HTTP via `sttp` with an injectable backend (the `SyncBackend` seam the Linear/YouTrack
tests already use), so the adapter is unit-testable against canned JSON without network.

**Interface/contract:** Returns `Either[String, Issue]` exactly like the other
adapters — no new error model.

**Estimate:** 5–8h (HTTP client, JSON model + parsing, error mapping, unit tests).

### Layer 3 — Capability Wiring (depends on: adapter)

**Files:** `core/commands/CommandEnv.scala`, `core/commands/LiveCommandEnv.scala`,
`core/test/fixtures/FakeCommandEnv.scala`

- Add `fetchForgejoIssue(...)` (and `createForgejoIssue(...)` if in scope) to the
  `TrackerOps` capability trait (`CommandEnv.scala:117–202`).
- Implement in `LiveTrackerOps` (`LiveCommandEnv.scala:236–301`) delegating to
  `ForgejoClient`.
- Extend `FakeTracker` to record Forgejo calls for harness tests.

**Interface/contract:** Commands depend only on `TrackerOps`, never on `ForgejoClient`
directly — preserves the test seam.

**Estimate:** 2–4h.

### Layer 4 — Command Dispatch & Auth (depends on: wiring)

**File:** `core/commands/Issue.scala` (+ any other command that matches on tracker type)

- Add the `IssueTrackerType.Forgejo` case to the dispatch in `Issue.scala:119–151`,
  resolving the token (`forgejoToken(env)` reading `FORGEJO_API_TOKEN`) and `baseUrl`
  from config, then calling `env.tracker.fetchForgejoIssue(...)`.
- Audit for **other** exhaustive `match IssueTrackerType` sites the compiler flags and
  handle each (this is what Layer 1's enum change surfaces).

**Estimate:** 1–3h.

### Layer 5 — Init & Doctor (depends on: model/config)

**Files:** `core/commands/Init.scala`, `core/commands/Doctor.scala`

- `Init.scala`: add Forgejo to the tracker-selection menu (`:107–120`), collect
  `repository` + `baseUrl` (+ optional `teamPrefix`), and a next-steps prompt
  (`export FORGEJO_API_TOKEN=...`). Optional host auto-detection for `codeberg.org`
  (see CLARIFY).
- `Doctor.scala`: CI-platform detection (`:107–110`) → add a Forgejo arm
  (e.g. "Forgejo Actions"); confirm base config checks cover the Forgejo case.

**Estimate:** 3–5h (interactive prompts + tests).

### Layer 6 — Tests & Smoke (cross-cutting)

**Files:** `core/test/ForgejoClientTest.scala` (new), harness tests, `test/*.bats`

- Unit: `ForgejoClientTest` mirroring `YouTrackClientTest` (JSON parsing, error cases,
  token validation) using the `sttp` mock backend.
- Harness: `*HarnessTest.scala` for the `Issue` dispatch path via `FakeCommandEnv`.
- E2E smoke: one round-trip is sufficient (BATS keeps the wiring smoke test only, per
  the project testing guide).

**Estimate:** folded into the per-layer estimates above; budget +1–2h for the BATS
smoke + harness wiring.

---

## CLARIFY Markers

### CLARIFY: Scope — issue-read-only vs. full forge parity

The title says "issue tracker," but GitHub/GitLab adapters also create issues, create
PRs/MRs, and poll CI checks (used by `phase-pr`, `phase-merge`). **Question:** Is the
deliverable (a) read-only `fetchIssue` for `./iw issue` and `./iw start`, (b) issue
read + create, or (c) full forge parity including pull-request creation and CI-check
polling? Option (c) additionally requires extending the `ForgeType`/PR-creation paths
and significantly enlarges Layers 2–4. **Recommendation:** start with (a)+(b) (issue
read + create) and treat PR/CI integration as a follow-up issue unless Forgejo-hosted
PR workflows are needed now.

### CLARIFY: Adapter transport

Confirm the **direct-HTTP-via-sttp** approach (TD-1) rather than shelling out to the
third-party `tea` CLI. HTTP keeps Forgejo dependency-free for users and matches the
Linear/YouTrack test seam. Any reason to prefer `tea`?

### CLARIFY: Auth env var name

Confirm `FORGEJO_API_TOKEN` as the token env var (consistent with
`LINEAR_API_TOKEN` / `YOUTRACK_API_TOKEN`).

### CLARIFY: Init host auto-detection

`init` auto-detects GitHub/GitLab from the git remote host. Forgejo is self-hosted with
arbitrary hostnames, so reliable auto-detection isn't possible in general. Should we
auto-detect the flagship `codeberg.org` → Forgejo, and otherwise rely on the explicit
`--tracker=forgejo` flag / menu selection? **Recommendation:** auto-detect
`codeberg.org` only; everything else is explicit.

### CLARIFY: `tracker.team` semantics

Forgejo addresses issues by `repository` (`owner/repo`), so `tracker.team` is unused —
matching GitHub/GitLab. Confirm we do **not** introduce a Forgejo-specific use of
`team`.

---

## Testing Strategy

Follows the project's three-tier pyramid (see `docs/testing.md`):

1. **Unit (primary):** `ForgejoClientTest.scala` exercises JSON parsing, token
   validation, and error mapping against the `sttp` `SyncBackend` mock — no network.
   Mirrors `YouTrackClientTest` / `LinearClientMockTest`. Config round-trip covered in
   the `Config` serializer tests.
2. **Harness:** `*HarnessTest.scala` drives the `Issue` command through `FakeCommandEnv`
   with `FakeTracker` recording the Forgejo call — verifies dispatch, token resolution,
   and error surfacing without real I/O.
3. **E2E smoke (BATS):** one `./iw issue` round-trip for a Forgejo-configured project,
   with `IW_SERVER_DISABLED=1` per the test harness convention. Keep it to the single
   wiring smoke test.

TDD throughout: write the failing adapter/parse test first, then implement.

---

## Implementation Sequence

Strict dependency order — each phase compiles and tests green before the next:

1. **Layer 1** (model + config) — unblocks everything; the enum change makes the
   compiler enumerate remaining work.
2. **Layer 2** (Forgejo HTTP adapter) — the substantive, independently-testable core.
3. **Layers 3 + 4** (wiring + command dispatch) — makes `./iw issue` work end-to-end
   for Forgejo.
4. **Layer 5** (init + doctor) — makes Forgejo configurable and diagnosable.
5. **Layer 6** smoke/harness coverage folded in as each layer lands.

---

## Recommended Phase Plan

Total estimate **~13–22h** → multi-phase. Each phase clears the 3h low-end floor and
respects dependency order.

| Phase | Scope | Layers | Estimate |
|-------|-------|--------|----------|
| **Phase 1** | Domain, config & serialization for Forgejo | Layer 1 | 2–4h |
| **Phase 2** | Forgejo HTTP adapter + unit tests | Layer 2 | 5–8h |
| **Phase 3** | Capability wiring + command dispatch/auth (`./iw issue` works) | Layers 3 + 4 | 3–6h |
| **Phase 4** | Init + doctor integration + smoke/harness coverage | Layers 5 + 6 | 3–5h |

> Phase 1's low end (2h) is below the 3h floor on its own; if it lands quickly it can be
> merged forward into Phase 2 during implementation. Kept separate here because the
> config round-trip tests realistically push it toward the floor and it's a clean,
> independently-reviewable unit.

Scope decisions in the CLARIFY markers (especially **Scope** and **transport**) can move
the total: full forge parity (PR/CI) would add a Phase 5 of roughly 4–8h.
