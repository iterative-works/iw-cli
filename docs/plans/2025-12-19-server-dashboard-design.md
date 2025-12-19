# iw-server: Status Dashboard Design

## Overview

Add a lightweight HTTP server to iw-cli that provides a web dashboard for monitoring worktrees across projects. The primary use case is remote visibility - checking work status and accessing PR links from a phone or different machine.

## Problem Statement

When working with multiple worktrees and running agentic workflows:
- Hard to see the overall picture of what's active, stalled, or needs attention
- Context switching requires remembering state across workstreams
- When away from terminal, no way to check status or review PRs on mobile
- Future: want to nudge/interact with agents remotely (out of scope for MVP)

## MVP Scope

**In scope:**
- Status dashboard showing all tracked worktrees
- Phase/task progress for agile workflow
- Links to open PRs (GitHub/GitLab) and issues (Linear)
- Auto-refresh and mobile-friendly UI

**Out of scope (future):**
- Interactive agent feedback
- Triggering commands from UI
- Real-time agent transcript viewing

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    User's Machine                        │
│                                                          │
│  ┌──────────┐         HTTP          ┌──────────────┐    │
│  │ Browser  │◄──────────────────────►│              │    │
│  │ (HTMX)   │    localhost:9876      │  iw-server   │    │
│  └──────────┘                        │              │    │
│                                      │  - Cask      │    │
│  ┌──────────┐         HTTP          │  - Scalatags │    │
│  │ iw CLI   │◄──────────────────────►│              │    │
│  └──────────┘                        └──────┬───────┘    │
│       │                                     │            │
│       │ creates                             │ reads      │
│       ▼                                     ▼            │
│  ┌──────────┐                        ┌──────────────┐    │
│  │Worktrees │                        │ Server State │    │
│  │ (git)    │                        │ (~/.local/   │    │
│  └──────────┘                        │  share/iw/)  │    │
│                                      └──────────────┘    │
└─────────────────────────────────────────────────────────┘
```

### Components

**iw-server:** Persistent daemon running per-user, serving dashboard and API.

**iw CLI:** Existing commands modified to register worktrees with server.

**Server state:** JSON file tracking registered worktrees and cached external data.

## Tech Stack

- **HTTP Server:** Cask (Li Haoyi's micro-framework)
- **HTML Templating:** Scalatags
- **Interactivity:** HTMX (minimal JS, server-rendered partials)
- **State:** JSON file persistence

Rationale: Cask is minimal, works well with scala-cli, and is from the same author as os-lib and Scalatags (already in use). HTMX keeps the frontend simple - no SPA complexity for what's essentially a status page.

## Server Lifecycle

### Starting the Server

**Lazy start (default):** Any `iw` command that needs the server starts it automatically.

1. CLI checks for `~/.local/share/iw/server/server.pid`
2. If exists, verify process is alive
3. If not running, spawn server as background process
4. Wait for `/health` endpoint to respond
5. Proceed with original command

**Explicit control:**
```bash
iw server start   # Start daemon
iw server stop    # Graceful shutdown
iw server status  # Running state, port, worktree count
```

**Systemd user service (optional):** For persistent headless operation, enabling remote access even when not logged in.

### Server Location

```
~/.local/share/iw/
├── versions/              # (existing) downloaded tool jars
├── server/
│   ├── state.json         # tracked worktrees, cached data
│   └── server.pid         # running server PID
```

### Port

Default: `9876` (configurable via `IW_SERVER_PORT` env var)

## Worktree Registration

**Lazy registration:** Any `iw` command run in a worktree registers it with the server. Zero friction - no explicit registration step needed.

**Registration flow:**
```bash
cd ~/projects/myproject-IWLE-123
iw issue  # CLI registers this worktree, then fetches issue
```

**Deregistration:**
- `iw rm IWLE-123` removes worktree and unregisters
- Manual: future `iw unregister` command if needed
- Auto-prune: non-existent paths removed on any state read

## State Model

### Stored State (`state.json`)

```json
{
  "worktrees": {
    "IWLE-123": {
      "path": "/home/mph/projects/myproject-IWLE-123",
      "tracker": "linear",
      "team": "IWLE",
      "registeredAt": "2025-12-19T10:30:00Z",
      "lastSeen": "2025-12-19T14:22:00Z"
    }
  },
  "cache": {
    "issues": {
      "IWLE-123": {
        "title": "Add user authentication",
        "status": "In Progress",
        "assignee": "mph",
        "fetchedAt": "2025-12-19T14:20:00Z"
      }
    },
    "prs": {
      "IWLE-123": {
        "phasePr": { "url": "https://github.com/...", "state": "open" },
        "featurePr": null,
        "fetchedAt": "2025-12-19T14:20:00Z"
      }
    }
  }
}
```

### Derived State (computed on read)

- **Workflow progress:** Parsed from `project-management/issues/{ID}/tasks.md`
- **Task counts:** Parsed from `phase-NN-tasks.md` files
- **Git status:** Current branch, clean/dirty, ahead/behind
- **Worktree existence:** Filesystem check

### Cache TTL

- Issue data: 5 minutes
- PR data: 2 minutes
- Force refresh on dashboard load or explicit request

## HTTP API

### Dashboard Endpoints (HTML)

```
GET /                         → Full dashboard page
GET /worktrees                → Worktree list partial (HTMX)
GET /worktrees/IWLE-123       → Single worktree card partial (HTMX)
```

### API Endpoints (JSON)

```
PUT /api/worktrees/IWLE-123
  Body: { "path": "...", "tracker": "linear", "team": "IWLE" }
  → 200 OK

DELETE /api/worktrees/IWLE-123
  → 200 OK / 404 Not Found

GET /api/worktrees
  → [{ issueId, path, status, ... }]

GET /api/worktrees/IWLE-123
  → { issueId, path, status, phase, tasks, prs, ... }

GET /api/status
  → { running, worktreeCount, uptime }

GET /health
  → 200 OK
```

## Dashboard UI

### Worktree List View

```
┌─────────────────────────────────────────────────────────────┐
│  iw dashboard                                    [Refresh]  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  IWLE-123 · Add user authentication                         │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ Phase 2/4: Validation errors         8/15 tasks        ││
│  │ ████████████░░░░░░░░░░░░░░░░░░░░░░░  53%               ││
│  │                                                         ││
│  │ Status: Awaiting review                                 ││
│  │ Branch: IWLE-123-user-auth-phase-02  ✓ clean           ││
│  │                                                         ││
│  │ [View PR ↗]  [View Issue ↗]                   2m ago   ││
│  └─────────────────────────────────────────────────────────┘│
│                                                             │
│  IWLE-456 · Fix payment calculation                         │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ Phase 1/2: Happy path                 3/10 tasks       ││
│  │ ████░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░  30%               ││
│  │                                                         ││
│  │ Status: Implementing                                    ││
│  │ Branch: IWLE-456-fix-payment-phase-01  ⚠ uncommitted   ││
│  │                                                         ││
│  │ [View Issue ↗]                              15m ago    ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

### Card Information

Each worktree card displays:
- Issue ID and title
- Phase progress (N/M phases)
- Task progress within current phase
- Workflow state (implementing / awaiting review / blocked / complete)
- Git branch and status (clean / uncommitted changes)
- Links: PR (if exists), Issue tracker
- Last activity timestamp

### HTMX Interactions

- Manual refresh button: `hx-get="/worktrees"`
- Auto-refresh: `hx-trigger="every 30s"`
- Individual card refresh on click

### Mobile Considerations

- Simple vertical stack layout
- Large tap targets for PR/issue links
- No complex interactions needed

## CLI Integration

### Modified Commands

Existing commands register worktree with server (best-effort, non-blocking):

- `iw start IWLE-123` → registers after creating worktree
- `iw open IWLE-123` → registers when opening
- `iw rm IWLE-123` → unregisters after removing
- `iw issue` → registers current worktree

### New Commands

```bash
iw server start    # Start daemon explicitly
iw server stop     # Stop daemon gracefully
iw server status   # Show running state, port, tracked count

iw dashboard       # Open browser to localhost:9876
```

### New Core Module: ServerClient

```scala
object ServerClient:
  def ensureRunning(): Either[String, Unit]
  def registerWorktree(issueId: String, path: String, config: ProjectConfiguration): Either[String, Unit]
  def unregisterWorktree(issueId: String): Either[String, Unit]
  def getStatus(): Either[String, ServerStatus]
```

### Failure Handling

If server communication fails, CLI commands still work normally. Registration is best-effort - the dashboard is a convenience, not a dependency.

## Project Structure

### New Files

```
.iw/
├── commands/
│   ├── server.scala         # iw server start/stop/status
│   └── dashboard.scala      # iw dashboard (opens browser)
├── core/
│   ├── ServerClient.scala   # CLI → Server HTTP client
│   ├── server/
│   │   ├── Server.scala     # Cask main, routes, lifecycle
│   │   ├── State.scala      # ServerState model + JSON persistence
│   │   ├── Views.scala      # Scalatags HTML templates
│   │   ├── WorktreeStatus.scala  # Derives status from git/filesystem
│   │   └── CacheRefresher.scala  # Background refresh of issue/PR data
```

### New Dependencies

```scala
//> using dep com.lihaoyi::cask::0.9.4
//> using dep com.lihaoyi::scalatags::0.13.1
```

(sttp already present for Linear/YouTrack clients)

## Future Considerations

Not in MVP, but informed design decisions:

- **Agent interaction:** Server could eventually manage Claude sessions, capture transcripts, relay user input
- **Remote access:** With auth token, dashboard could be exposed beyond localhost
- **Notifications:** Could push status changes to mobile via webhooks
- **Command triggering:** "Continue" button to run `ag-implement` from UI
