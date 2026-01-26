# Phase 2 Tasks: Create llms.txt Documentation

**Issue:** IW-126
**Phase:** 2 of 3
**Goal:** Create LLM-readable documentation for public API modules

## Setup

- [ ] [setup] Create `.iw/docs/` directory
- [ ] [setup] Create `.iw/llms.txt` skeleton with standard format

## Model Layer Documentation

- [ ] [impl] Document IssueId.md (issue ID parsing and validation)
- [ ] [impl] Document Issue.md (issue data types)
- [ ] [impl] Document Config.md (configuration types)
- [ ] [impl] Document Constants.md (project constants)
- [ ] [impl] Document model value types (WorktreePath, WorktreePriority, WorktreeRegistration, ApiToken, GitStatus)
- [ ] [impl] Document model data types (ReviewState, PhaseInfo, WorkflowProgress, PullRequestData)
- [ ] [impl] Document server types (ServerConfig, ServerStatus, ServerState, CacheConfig, DeletionSafety)

## Adapters Layer Documentation

- [ ] [impl] Document Git.md (GitAdapter - git operations)
- [ ] [impl] Document GitWorktree.md (GitWorktreeAdapter - worktree management)
- [ ] [impl] Document Process.md (ProcessAdapter - shell execution)
- [ ] [impl] Document CommandRunner.md (CommandRunnerAdapter - command execution)
- [ ] [impl] Document ConfigRepository.md (configuration loading/saving)
- [ ] [impl] Document Log.md (logging adapter)
- [ ] [impl] Document Prompt.md (interactive input)
- [ ] [impl] Document Tmux.md (tmux session management)
- [ ] [impl] Document GitHubClient.md (GitHub API via gh CLI)
- [ ] [impl] Document LinearClient.md (Linear API client)
- [ ] [impl] Document GitLabClient.md (GitLab API client)
- [ ] [impl] Document YouTrackClient.md (YouTrack API client)

## Output Layer Documentation

- [ ] [impl] Document Output.md (console formatting)
- [ ] [impl] Document IssueFormatter.md (issue display formatting)
- [ ] [impl] Document MarkdownRenderer.md (markdown rendering)
- [ ] [impl] Document TimestampFormatter.md (timestamp formatting)

## Integration

- [ ] [impl] Update llms.txt index with all documented modules
- [ ] [impl] Add usage examples to key modules from .iw/commands/

## Verification

- [ ] [verify] Verify all links in llms.txt resolve correctly
- [ ] [verify] Verify documented signatures match source files
- [ ] [verify] Verify examples are syntactically correct Scala

## Notes

- Focus on commonly-used modules first (Output, Git, Process, IssueId)
- Extract real examples from `.iw/commands/` scripts
- Group related value types into single documentation files
- Keep documentation concise - agents prefer dense info
