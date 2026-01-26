# Phase 2 Tasks: Create llms.txt Documentation

**Issue:** IW-126
**Phase:** 2 of 3
**Goal:** Create LLM-readable documentation for public API modules

## Setup

- [x] [setup] Create `.iw/docs/` directory
- [x] [setup] Create `.iw/llms.txt` skeleton with standard format

## Model Layer Documentation

- [x] [impl] Document IssueId.md (issue ID parsing and validation)
- [x] [impl] Document Issue.md (issue data types)
- [x] [impl] Document Config.md (configuration types)
- [x] [impl] Document Constants.md (project constants)
- [x] [impl] Document model value types (WorktreePath, WorktreePriority, WorktreeRegistration, ApiToken, GitStatus)
- [x] [impl] Document model data types (ReviewState, PhaseInfo, WorkflowProgress, PullRequestData)
- [x] [impl] Document server types (ServerConfig, ServerStatus, ServerState, CacheConfig, DeletionSafety)

## Adapters Layer Documentation

- [x] [impl] Document Git.md (GitAdapter - git operations)
- [x] [impl] Document GitWorktree.md (GitWorktreeAdapter - worktree management)
- [x] [impl] Document Process.md (ProcessAdapter - shell execution)
- [x] [impl] Document CommandRunner.md (CommandRunnerAdapter - command execution)
- [x] [impl] Document ConfigRepository.md (configuration loading/saving)
- [x] [impl] Document Log.md (logging adapter)
- [x] [impl] Document Prompt.md (interactive input)
- [x] [impl] Document Tmux.md (tmux session management)
- [x] [impl] Document GitHubClient.md (GitHub API via gh CLI)
- [x] [impl] Document LinearClient.md (Linear API client)
- [x] [impl] Document GitLabClient.md (GitLab API client)
- [x] [impl] Document YouTrackClient.md (YouTrack API client)

## Output Layer Documentation

- [x] [impl] Document Output.md (console formatting)
- [x] [impl] Document IssueFormatter.md (issue display formatting)
- [x] [impl] Document MarkdownRenderer.md (markdown rendering)
- [x] [impl] Document TimestampFormatter.md (timestamp formatting)

## Integration

- [x] [impl] Update llms.txt index with all documented modules
- [x] [impl] Add usage examples to key modules from .iw/commands/

## Verification

- [x] [verify] Verify all links in llms.txt resolve correctly
- [x] [verify] Verify documented signatures match source files
- [x] [verify] Verify examples are syntactically correct Scala

## Notes

- Focus on commonly-used modules first (Output, Git, Process, IssueId)
- Extract real examples from `.iw/commands/` scripts
- Group related value types into single documentation files
- Keep documentation concise - agents prefer dense info
