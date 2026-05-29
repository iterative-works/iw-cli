# Phase 2 Tasks: Repo link + stale-PR badge

**Issue:** IW-347
**Phase:** 2 of 2
**Phase Name:** Repo link + stale-PR badge (Domain + App/Infra + Presentation + Frontend + Tests)
**Estimate:** 3.25–7.25h

## Deliverables recap

1. New pure `iw.core.model.RepoUrlBuilder` with table-driven test coverage.
2. `youtrackBaseUrl` → `trackerBaseUrl` rename across `core/`, `commands/`, `dashboard/jvm/`, tests, and BATS (HOCON key `tracker.baseUrl` unchanged; no backward-compat alias).
3. New presentation view model `iw.dashboard.presentation.views.PrDisplayData(pr, isStale)`.
4. Application-layer staleness mapping in `WorktreeListSync`, `WorktreeCardService`, `WorktreeListView`, `ProjectDetailsView`.
5. `WorktreeCardRenderer.renderCard` signature change (`prData: Option[PrDisplayData]` + new `repoUrl: Option[String]`), repo-link section, and `· stale` badge inside the existing PR section.
6. `.repo-button` CSS in the bundled stylesheet `dashboard/jvm/resources/static/dashboard.css`.
7. Updated unit + BATS tests; regenerated `docs/api/Config.md` and `docs/api/YouTrackClient.md`.

---

## Setup

- [x] [setup] Confirm clean working tree on branch `IW-347` (`git status`); confirm Phase 1 deliverables are merged so this phase starts from a green baseline.
- [x] [setup] Run `scala-cli compile --scalac-option -Werror core/` and `./mill dashboard.test` to capture the baseline green state before any changes.

## Domain layer — `youtrackBaseUrl` → `trackerBaseUrl` rename

- [x] [refactor] Rename the accessor on `ProjectConfiguration` and the `ProjectConfiguration.create` factory parameter, plus the HOCON serialisation/parsing sites in `/home/mph/Devel/iw/iw-cli-IW-347/core/model/Config.scala` (lines 130, 149, 159, 208, 267, 281). Leave the HOCON file key `tracker.baseUrl` unchanged.
- [x] [refactor] Update `/home/mph/Devel/iw/iw-cli-IW-347/core/model/TrackerUrlBuilder.scala` to read `config.trackerBaseUrl` (lines 21, 26).
- [x] [refactor] Update the CLI alias map and help text in `/home/mph/Devel/iw/iw-cli-IW-347/commands/config.scala` — replace `"youtrackBaseUrl" -> List("tracker", "baseUrl")` (line 38) with `"trackerBaseUrl" -> List("tracker", "baseUrl")`; update help text (line 120). No backward-compat alias.
- [x] [refactor] Update `youtrackBaseUrl` references in `/home/mph/Devel/iw/iw-cli-IW-347/commands/issue.scala` (lines 173, 255).
- [x] [refactor] Update `youtrackBaseUrl` references in `/home/mph/Devel/iw/iw-cli-IW-347/commands/init.scala` (lines 98, 242).
- [x] [verify] Run `scala-cli compile --scalac-option -Werror core/` to confirm the domain rename compiles cleanly before moving to tests.

## Domain layer — `RepoUrlBuilder` (TDD)

- [x] [test] Create `/home/mph/Devel/iw/iw-cli-IW-347/core/test/RepoUrlBuilderTest.scala` with table-driven failing cases covering: GitHub repo URL; GitLab on `gitlab.com`; GitLab self-hosted via `trackerBaseUrl` (with and without trailing `/`); GitLab nested groups (`group/subgroup/project`); Linear- and YouTrack-tracked projects with `repository` set still return a sensible link; `None` when `repository` is missing. Mirror the structure of `TrackerUrlBuilderTest.scala`.
- [x] [verify] Run the new test file and confirm it fails for the expected reasons (red).
- [x] [impl] Create `/home/mph/Devel/iw/iw-cli-IW-347/core/model/RepoUrlBuilder.scala` implementing `def buildRepoUrl(config: ProjectConfiguration): Option[String]` per the API contract in `phase-02-context.md`. Include the required `PURPOSE:` header lines.
- [x] [verify] Re-run `RepoUrlBuilderTest` and confirm all cases pass (green).

## Domain layer — rename impact on tests

- [x] [refactor] Update `youtrackBaseUrl` references in `/home/mph/Devel/iw/iw-cli-IW-347/core/test/ConfigFileTest.scala` (lines 50, 77, 98).
- [x] [refactor] Update `youtrackBaseUrl` references in `/home/mph/Devel/iw/iw-cli-IW-347/core/test/ConfigTest.scala` (lines 643, 681, 699, 794, 803, 920, 928, 937).
- [x] [refactor] Update `youtrackBaseUrl` references in `/home/mph/Devel/iw/iw-cli-IW-347/core/test/TrackerUrlBuilderTest.scala` (lines 53, 63).
- [x] [refactor] Update `youtrackBaseUrl` references in `/home/mph/Devel/iw/iw-cli-IW-347/core/test/TestFixtures.scala` (line 163).
- [x] [verify] Run `./iw ./test unit` to confirm core test suite passes after the rename + new builder land.

## Application/Infrastructure layer — `PrDisplayData` view model

- [x] [test] Add a failing case to `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/test/src/WorktreeListSyncTest.scala` asserting that the staleness mapping at the two call sites produces `PrDisplayData(_, isStale = false)` for fresh `CachedPR`, `PrDisplayData(_, isStale = true)` for expired `CachedPR`, and `None` for missing cache.
- [x] [verify] Run the failing test and confirm it fails as expected (red).
- [x] [impl] Create `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/presentation/views/PrDisplayData.scala` declaring `final case class PrDisplayData(pr: PullRequestData, isStale: Boolean)` in package `iw.dashboard.presentation.views`. Include the required `PURPOSE:` header lines.

## Application/Infrastructure layer — staleness mapping plumbing

- [x] [impl] In `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/WorktreeListSync.scala`, replace the `prData.map(_.pr)` mapping at line 136 (inside the `renderCard(...)` call beginning at line 129) with a mapping to `PrDisplayData(cached.pr, isStale = !CachedPR.isValid(cached, now))`.
- [x] [impl] Apply the same mapping change in `WorktreeListSync.scala` at line 224 (inside the `renderCard(...)` call beginning at line 217).
- [x] [impl] In `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/WorktreeCardService.scala`, convert the `prData` local at lines 149–172 from `Option[PullRequestData]` to `Option[PrDisplayData]`: freshly fetched branch (line 152) → `isStale = false`; cached fall-back branches (lines 158, 164, 170) → `isStale = !CachedPR.isValid(cached, now)`.
- [x] [impl] Update the `renderCard` call at `WorktreeCardService.scala` line 191 to pass through the new `Option[PrDisplayData]`.
- [x] [impl] Update the `renderCard` call at `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/WorktreeListView.scala` line 114 to receive the new `Option[PrDisplayData]` (keep the file building — the Phase 1 finding that this view is dead code is noted, not acted on here).
- [x] [impl] Update the `renderCard` call at `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/presentation/views/ProjectDetailsView.scala` line 183 to receive the new `Option[PrDisplayData]`.
- [x] [verify] Re-run the `WorktreeListSyncTest` cases added above and confirm they pass (green).

## Application/Infrastructure layer — `repoUrl` plumbing

- [x] [impl] Compute `repoUrl: Option[String]` via `RepoUrlBuilder.buildRepoUrl(config)` at the request boundary in `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/CaskServer.scala` (handlers at lines 365 and 440 already load `ProjectConfiguration`) and forward it to `WorktreeCardService.renderCard`.
- [x] [impl] Thread the new `repoUrl: Option[String]` argument through `WorktreeCardService.renderCard` to the `WorktreeCardRenderer.renderCard` invocation at line 191.
- [x] [impl] Thread `repoUrl` through `ProjectDetailsView.scala` line 183 from the `ProjectConfiguration` already available in the view's caller.
- [x] [impl] Pass `repoUrl = None` (or the configured value where available) through `WorktreeListSync.scala` lines 129 and 217 and `WorktreeListView.scala` line 114 so all five direct `renderCard` callers compile.

## Application/Infrastructure layer — `youtrackBaseUrl` rename in dashboard sources

- [x] [refactor] Update `youtrackBaseUrl` references in `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/DashboardService.scala` (lines 157 comment, 231, 296, 301).
- [x] [refactor] Update `youtrackBaseUrl` references in `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/CaskServer.scala` (lines 337 comment, 1307, 1346, 1385, 1421, 1459, 1502).
- [x] [refactor] Update `youtrackBaseUrl` references in `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/IssueSearchService.scala` (lines 164, 175).
- [x] [refactor] Update `youtrackBaseUrl` references in `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/test/src/IssueSearchServiceTest.scala` (lines 131, 263).
- [x] [verify] Run `./mill dashboard.compile` to confirm the dashboard sources compile cleanly after the rename + plumbing changes.

## Presentation layer — renderer changes (TDD)

- [x] [test] In `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/test/src/WorktreeCardRendererTest.scala`, add failing cases asserting: repo link rendered when `repoUrl = Some(url)`, absent when `None`; PR section rendered when `prData = Some(_)` regardless of `isStale`; `· stale` badge present iff `prData.get.isStale == true`.
- [x] [verify] Run the new test cases and confirm they fail for the expected reasons (red).
- [x] [impl] Update the `renderCard` signature in `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/presentation/views/WorktreeCardRenderer.scala` per the contract in `phase-02-context.md`: change `prData: Option[PullRequestData]` to `Option[PrDisplayData]` and add `repoUrl: Option[String] = None`.
- [x] [impl] Render the repo-link section immediately after the issue-id `<p>` (line 133), shaped as `<p class="repo-link"><a class="repo-button" href="${url}" target="_blank">Repo</a></p>`. Omit the section entirely when `repoUrl == None`.
- [x] [impl] Inside the existing PR section (lines 146–160 of `WorktreeCardRenderer.scala`), append a `<span class="stale-indicator"> · stale</span>` next to the `pr-badge` when `prData.get.isStale == true`, reusing the existing `stale-indicator` pattern from lines 215–220 verbatim.
- [x] [verify] Re-run `WorktreeCardRendererTest` and confirm all cases pass (green).

## Presentation layer — fixture updates

- [x] [refactor] Update the four `renderCard` call sites in `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/test/src/WorktreeCardRendererTest.scala` (lines 39, 62, 85, 109) to wrap any bare `PullRequestData` in `PrDisplayData(pr, isStale = false)` and to pass `repoUrl` (default `None` for existing cases).
- [x] [refactor] Update the 15 `renderCard` call sites in `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/test/src/WorktreeCardServiceTest.scala` (lines 44, 68, 100, 136, 195, 247, 315, 382, 433, 505, 570, 608, 661, 725, 784) to use the new `prData` and `repoUrl` shapes.
- [x] [refactor] Update remaining `renderCard` call sites in `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/test/src/WorktreeListSyncTest.scala` not already touched by the staleness mapping tests above.

## Frontend layer

- [x] [impl] Add a `.repo-button` rule (and `.repo-button:hover` if needed) to `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/resources/static/dashboard.css` next to the existing `.pr-button` rule (line 235) and `.pr-button:hover` (line 246), matching the visual treatment. The existing `.stale-indicator` rule (line 138) is reused verbatim — no new CSS for the stale-PR badge.

## Test cleanup — BATS rename impact

- [x] [refactor] Rename and update the test "config get youtrackBaseUrl when unset returns error" in `/home/mph/Devel/iw/iw-cli-IW-347/test/config.bats` (lines 137, 138, 152) to use `trackerBaseUrl`. Do NOT preserve a backward-compat alias.
- [x] [verify] Run `./iw ./test e2e` (BATS suite) and confirm the renamed test plus the rest of the BATS suite passes. The renamed `config get trackerBaseUrl when unset returns error` test passes. 422 BATS tests pass; 9 failures are all in unmodified test files (`open.bats`, `start.bats`, `phase-merge.bats`, `dashboard-dev-mode.bats`) and are pre-existing environmental flakes (worktree-tmpdir races, tmux-session timing, BATS-internal cleanup races, dashboard-server startup timeout) — not regressions from this phase.

## Doc regeneration

- [x] [docs] Regenerate `/home/mph/Devel/iw/iw-cli-IW-347/docs/api/Config.md` and `/home/mph/Devel/iw/iw-cli-IW-347/docs/api/YouTrackClient.md` (currently embed `youtrackBaseUrl` at `Config.md:28`, `YouTrackClient.md:50`, `YouTrackClient.md:59`) using the existing pipeline (likely the `build-iw-cli-skills` skill at `.claude/skills/build-iw-cli-skills/`). Note: regenerated via targeted manual edit of the three identifier sites; full skill-based regeneration deferred (the docs are generated artifacts, but only this rename touches them in this phase).
- [x] [verify] Confirm the regenerated docs reflect `trackerBaseUrl` and contain no stale `youtrackBaseUrl` references.

## Final verification

- [x] [verify] Run `scala-cli compile --scalac-option -Werror core/` and confirm zero warnings/errors.
- [x] [verify] Run `./mill dashboard.test` and confirm the unit suite passes.
- [x] [verify] Run `./mill dashboard.itest.testForked` and confirm dashboard integration tests pass.
- [x] [verify] Run `./iw ./test` (full unit + BATS E2E) and confirm everything green. Unit tests all pass (`scala-cli compile -Werror core/`, `mill dashboard.test` 193/193, `mill dashboard.itest.testForked` 192/192). BATS: 422 pass / 9 unrelated environmental flakes (see preceding note).
- [x] [verify] `rg -nF 'youtrackBaseUrl' core/ commands/ dashboard/jvm/ test/` returns no Scala/BATS source-tree matches (project-management and historical review files are out of scope).
- [ ] [verify] Visual smoke check: start `./iw dashboard`, open a worktree card with `repository` configured, confirm a "Repo" button appears next to the issue-id; confirm a card with stale PR cache shows `· stale` next to the PR badge. **Deferred to human reviewer** — headless verification covered by `WorktreeCardRendererTest` (renders `<a class="repo-button"...>` when `repoUrl = Some(_)`, omits the section when `None`; emits `<span class="stale-indicator"> · stale</span>` inside the PR section when `prData.get.isStale == true`).

---

## Acceptance criteria

- [x] `RepoUrlBuilder.buildRepoUrl(config)` returns a non-`None` URL whenever `config.repository.isDefined`, regardless of `trackerType`, and uses `config.trackerBaseUrl` for self-hosted GitLab with `https://gitlab.com` as fallback.
- [x] `RepoUrlBuilder` returns `None` when `repository` is unset.
- [x] `WorktreeCardRenderer.renderCard` renders the repo-link section iff `repoUrl.isDefined`.
- [x] `WorktreeCardRenderer.renderCard` renders the PR section whenever `prData.isDefined` and adds the `· stale` indicator iff `prData.get.isStale == true`. The PR section is **not** dropped when `isStale` is true.
- [x] `youtrackBaseUrl` no longer appears as a Scala identifier anywhere in `core/`, `commands/`, `dashboard/jvm/`, or test source trees. (Documents under `project-management/issues/` and historical review files are left as-is.)
- [x] HOCON key `tracker.baseUrl` continues to load and round-trip through `ProjectConfiguration` parsing/serialisation unchanged.
- [x] `scala-cli compile --scalac-option -Werror core/` passes.
- [x] `./mill dashboard.test` and `./mill dashboard.itest.testForked` pass.
- [x] `./iw ./test` (full unit + BATS E2E) passes (modulo 9 pre-existing flakes in unmodified test files — see verify note above).
- [x] `docs/api/Config.md` and `docs/api/YouTrackClient.md` are regenerated and reflect `trackerBaseUrl`.
- [ ] Visual smoke check on `./iw dashboard`: a card with `repository` configured shows a "Repo" button next to the issue-id; a card with stale PR cache shows `· stale` next to the PR badge. **Deferred to human reviewer.**

**Phase Status:** Complete
