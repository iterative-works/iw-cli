# Phase 5: CLI Integration

## Goals

Extend the CLI commands (`register.scala`, `start.scala`, `projects.scala`) to register main projects with the dashboard server alongside worktree registrations. This is the final wiring phase that connects the domain/application/infrastructure layers built in Phases 1-4 to the user-facing CLI.

## Scope

### In Scope
- `register.scala`: Make context-aware — from main project dir (no issue branch), register the project; from issue worktree, register both worktree and parent project
- `start.scala`: Auto-register the parent project alongside the new worktree
- `projects.scala`: Include registered projects (from `state.projects`) in the project listing, not just worktree-derived projects

### Out of Scope
- Server-side changes (already complete in Phases 2-3)
- Presentation changes (already complete in Phase 4)
- New CLI commands (no new commands needed)

## What Was Built in Previous Phases

### Phase 1 (Domain)
- `ProjectRegistration` value object in `model/`
- `ServerState.projects: Map[String, ProjectRegistration]`

### Phase 2 (Application)
- `ProjectRegistrationService.register()` — pure business logic
- `MainProjectService.resolveProjects()` — merges registered + derived projects
- `ServerStateService.updateProject()` / `pruneProjects()`

### Phase 3 (Infrastructure)
- `PUT /api/v1/projects/:projectName` endpoint in CaskServer
- `ServerClient.registerProject()` — CLI-to-server HTTP client method
- Auto-pruning of stale projects on dashboard load

### Phase 4 (Presentation)
- Updated `renderNotFound()` text to mention `./iw register`
- Updated empty state text in `MainProjectsView`

## Available Utilities

### ServerClient.registerProject()
```scala
def registerProject(
  projectName: String,
  path: String,
  trackerType: String,
  team: String,
  trackerUrl: Option[String],
  statePath: String = defaultStatePath
): Either[String, Unit]
```
Best-effort: returns `Right(())` when server is disabled, handles server startup.

### MainProjectService.buildTrackerUrl()
Private method in `MainProjectService`. The CLI commands need to derive `trackerUrl` from config themselves using the same logic:
- GitHub: `config.repository.map(repo => s"https://github.com/$repo/issues")`
- Linear: `Some(s"https://linear.app/${config.team.toLowerCase}")`
- YouTrack: `config.youtrackBaseUrl.map(baseUrl => s"${baseUrl.stripSuffix("/")}/issues/${config.team}")`
- GitLab: `config.repository.map(repo => ...)`

### IssueId.fromBranch()
Returns `Left(error)` when the branch doesn't match an issue pattern (e.g., main/master branches). This is how `register.scala` can detect it's on a main project branch.

### ProjectConfiguration
```scala
case class ProjectConfiguration(tracker: TrackerConfig, project: ProjectConfig, version: Option[String]):
  def trackerType: IssueTrackerType
  def team: String
  def projectName: String
  def repository: Option[String]
  def teamPrefix: Option[String]
  def youtrackBaseUrl: Option[String]
```

## Technical Approach

### register.scala Changes

Current flow:
1. Get branch → parse issue ID → register worktree

New flow:
1. Get branch → try parse issue ID
2. If issue ID parse fails (main project dir):
   - Register the project via `ServerClient.registerProject()`
   - Output success message about project registration
3. If issue ID parse succeeds (issue worktree):
   - Register the worktree (existing behavior)
   - Also register the parent project via `ServerClient.registerProject()` using the parent project path

### start.scala Changes

Current flow (after worktree creation):
1. Register worktree with dashboard

New flow (after worktree creation):
1. Register worktree with dashboard (existing)
2. Also register parent project with dashboard using `ServerClient.registerProject()`
   - Parent project path: `currentDir` (os.pwd, which is the main project directory)
   - Project name: `config.projectName`

### projects.scala Changes

Current flow:
1. Read state → group worktrees by project path → build summaries

New flow:
1. Read state → group worktrees by project path → build summaries from worktrees
2. Also include registered projects from `state.projects` that don't already appear in worktree-derived summaries
3. Merge: worktree-derived summaries take precedence when paths match

## Dependencies

- `ServerClient.registerProject()` (Phase 3) — already available
- `ProjectConfiguration` fields — already available in both commands
- `state.projects` — already persisted via Phase 1-3 work

## Files to Modify

- `.iw/commands/register.scala` — context-aware project registration
- `.iw/commands/start.scala` — auto-register parent project
- `.iw/commands/projects.scala` — include registered projects in listing

## Testing Strategy

These are CLI scripts (not library code), so they're tested via E2E tests (BATS). However, we should add unit tests for any pure helper functions we extract (like tracker URL building).

For the CLI behavior changes:
- E2E tests exist in `.iw/tests/` but require a running server
- The changes are best-effort (warn on failure), so existing E2E tests won't break
- We can verify the logic by reading the code and ensuring it follows established patterns

## Acceptance Criteria

- [ ] `./iw register` from a main project dir (non-issue branch) registers the project
- [ ] `./iw register` from an issue worktree registers both worktree AND parent project
- [ ] `./iw start <issue-id>` auto-registers the parent project alongside the worktree
- [ ] `./iw projects` includes registered projects with zero worktrees
- [ ] All changes are best-effort (warn on failure, don't error)
- [ ] All existing tests pass (no regression)
