#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw worktrees command
# PURPOSE: Tests worktrees listing with state.json data and filtering

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

setup() {
    # Disable dashboard server communication during tests
    export IW_SERVER_DISABLED=1

    # Create a temporary directory for each test
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    # Override HOME to control state.json location
    export HOME="$TEST_DIR"
    mkdir -p "$HOME/.local/share/iw/server"

    # Create a git repo with iw config
    git init -q 2>/dev/null
    git config user.email "test@example.com"
    git config user.name "Test User"
    echo "initial" > README.md
    git add README.md 2>/dev/null
    git commit -q -m "Initial commit" 2>/dev/null

    # Initialize iw with config
    mkdir -p .iw
    cat > .iw/config.conf << 'EOF'
project {
  name = testproject
}

tracker {
  type = linear
  team = TEST
}
EOF
}

teardown() {
    # Clean up temporary directory
    cd /
    rm -rf "$TEST_DIR"
}

create_state_with_worktrees() {
    # Create state.json with worktrees from multiple projects
    cat > "$HOME/.local/share/iw/server/state.json" << EOF
{
  "worktrees": {
    "TEST-123": {
      "issueId": "TEST-123",
      "path": "$TEST_DIR-TEST-123",
      "trackerType": "linear",
      "team": "TEST",
      "registeredAt": "2024-01-01T00:00:00Z",
      "lastSeenAt": "2024-01-01T00:00:00Z"
    },
    "TEST-456": {
      "issueId": "TEST-456",
      "path": "$TEST_DIR-TEST-456",
      "trackerType": "linear",
      "team": "TEST",
      "registeredAt": "2024-01-01T00:00:00Z",
      "lastSeenAt": "2024-01-01T00:00:00Z"
    },
    "OTHER-789": {
      "issueId": "OTHER-789",
      "path": "/tmp/otherproject-OTHER-789",
      "trackerType": "github",
      "team": "OTHER",
      "registeredAt": "2024-01-01T00:00:00Z",
      "lastSeenAt": "2024-01-01T00:00:00Z"
    }
  },
  "issueCache": {},
  "progressCache": {},
  "prCache": {},
  "reviewStateCache": {}
}
EOF
}

@test "worktrees with no state file shows empty message" {
    run "$PROJECT_ROOT/iw" worktrees
    [ "$status" -eq 0 ]
    [[ "$output" =~ "No worktrees found" ]]
}

@test "worktrees --all shows all worktrees" {
    create_state_with_worktrees

    run "$PROJECT_ROOT/iw" worktrees --all
    [ "$status" -eq 0 ]
    [[ "$output" =~ "TEST-123" ]]
    [[ "$output" =~ "TEST-456" ]]
    [[ "$output" =~ "OTHER-789" ]]
}

@test "worktrees --json outputs valid JSON array" {
    create_state_with_worktrees

    run "$PROJECT_ROOT/iw" worktrees --json --all
    [ "$status" -eq 0 ]

    # Verify it's valid JSON by piping through jq
    echo "$output" | jq . > /dev/null
}

@test "worktrees --json with empty state outputs empty array" {
    run "$PROJECT_ROOT/iw" worktrees --json --all
    [ "$status" -eq 0 ]
    [ "$output" = "[]" ]
}

@test "worktrees --json contains expected fields" {
    create_state_with_worktrees

    run "$PROJECT_ROOT/iw" worktrees --json --all
    [ "$status" -eq 0 ]

    # Check for required fields
    echo "$output" | jq '.[0].issueId' > /dev/null
    echo "$output" | jq '.[0].path' > /dev/null
    echo "$output" | jq '.[0].needsAttention' > /dev/null
}

@test "worktrees --json includes workflowDisplay field" {
    # Create state with a review state that has display text
    cat > "$HOME/.local/share/iw/server/state.json" << EOF
{
  "worktrees": {
    "TEST-100": {
      "issueId": "TEST-100",
      "path": "$TEST_DIR-TEST-100",
      "trackerType": "linear",
      "team": "TEST",
      "registeredAt": "2024-01-01T00:00:00Z",
      "lastSeenAt": "2024-01-01T00:00:00Z"
    }
  },
  "issueCache": {},
  "progressCache": {},
  "prCache": {},
  "reviewStateCache": {
    "TEST-100": {
      "state": {
        "display": {"text": "Waiting for review", "subtext": null, "displayType": "info"},
        "badges": null,
        "taskLists": null,
        "needsAttention": false,
        "message": null,
        "artifacts": []
      },
      "filesMtime": {}
    }
  }
}
EOF

    run "$PROJECT_ROOT/iw" worktrees --json --all
    [ "$status" -eq 0 ]

    # Verify workflowDisplay field exists
    result=$(echo "$output" | jq '.[0] | has("workflowDisplay")')
    [ "$result" = "true" ]

    # Verify the value is populated from display.text
    value=$(echo "$output" | jq -r '.[0].workflowDisplay')
    [ "$value" = "Waiting for review" ]
}

@test "worktrees --json includes activity and workflowType fields" {
    # Create state with review state containing activity and workflowType
    cat > "$HOME/.local/share/iw/server/state.json" << EOF
{
  "worktrees": {
    "TEST-200": {
      "issueId": "TEST-200",
      "path": "$TEST_DIR-TEST-200",
      "trackerType": "linear",
      "team": "TEST",
      "registeredAt": "2024-01-01T00:00:00Z",
      "lastSeenAt": "2024-01-02T00:00:00Z"
    }
  },
  "issueCache": {},
  "progressCache": {},
  "prCache": {},
  "reviewStateCache": {
    "TEST-200": {
      "state": {
        "display": null,
        "badges": null,
        "taskLists": null,
        "needsAttention": null,
        "message": null,
        "artifacts": [],
        "activity": "working",
        "workflowType": "waterfall"
      },
      "filesMtime": {}
    }
  }
}
EOF

    run "$PROJECT_ROOT/iw" worktrees --json --all
    [ "$status" -eq 0 ]

    # Verify activity field is present and populated
    activity=$(echo "$output" | jq -r '.[0].activity')
    [ "$activity" = "working" ]

    # Verify workflowType field is present and populated
    wftype=$(echo "$output" | jq -r '.[0].workflowType')
    [ "$wftype" = "waterfall" ]
}

@test "worktrees --json includes progress fields" {
    # Create state with progressCache data
    cat > "$HOME/.local/share/iw/server/state.json" << EOF
{
  "worktrees": {
    "TEST-300": {
      "issueId": "TEST-300",
      "path": "$TEST_DIR-TEST-300",
      "trackerType": "linear",
      "team": "TEST",
      "registeredAt": "2024-01-01T00:00:00Z",
      "lastSeenAt": "2024-01-01T00:00:00Z"
    }
  },
  "issueCache": {},
  "progressCache": {
    "TEST-300": {
      "progress": {
        "currentPhase": 2,
        "totalPhases": 4,
        "phases": [
          {"phaseNumber": 1, "phaseName": "Setup", "taskFilePath": "/p/phase-01.md", "totalTasks": 5, "completedTasks": 5},
          {"phaseNumber": 2, "phaseName": "Impl", "taskFilePath": "/p/phase-02.md", "totalTasks": 7, "completedTasks": 3}
        ],
        "overallCompleted": 8,
        "overallTotal": 12
      },
      "filesMtime": {}
    }
  },
  "prCache": {},
  "reviewStateCache": {}
}
EOF

    run "$PROJECT_ROOT/iw" worktrees --json --all
    [ "$status" -eq 0 ]

    # Verify progress fields are present and populated
    currentPhase=$(echo "$output" | jq '.[0].currentPhase')
    [ "$currentPhase" = "2" ]

    totalPhases=$(echo "$output" | jq '.[0].totalPhases')
    [ "$totalPhases" = "4" ]

    completedTasks=$(echo "$output" | jq '.[0].completedTasks')
    [ "$completedTasks" = "8" ]

    totalTasks=$(echo "$output" | jq '.[0].totalTasks')
    [ "$totalTasks" = "12" ]
}

@test "worktrees --json includes URL and timestamp fields" {
    # Create state with issueCache, prCache, and timestamps in worktree registration
    cat > "$HOME/.local/share/iw/server/state.json" << EOF
{
  "worktrees": {
    "TEST-400": {
      "issueId": "TEST-400",
      "path": "$TEST_DIR-TEST-400",
      "trackerType": "linear",
      "team": "TEST",
      "registeredAt": "2024-03-01T10:00:00Z",
      "lastSeenAt": "2024-03-15T14:30:00Z"
    }
  },
  "issueCache": {
    "TEST-400": {
      "data": {
        "id": "test-uuid-400",
        "title": "Test Issue",
        "status": "In Progress",
        "assignee": null,
        "description": null,
        "url": "https://linear.app/test/issue/TEST-400",
        "fetchedAt": "2024-03-15T12:00:00Z"
      },
      "ttlMinutes": 5
    }
  },
  "progressCache": {},
  "prCache": {
    "TEST-400": {
      "pr": {
        "url": "https://github.com/org/repo/pull/42",
        "state": "Open",
        "number": 42,
        "title": "Add feature"
      },
      "fetchedAt": "2024-03-15T12:00:00Z",
      "ttlMinutes": 2
    }
  },
  "reviewStateCache": {}
}
EOF

    run "$PROJECT_ROOT/iw" worktrees --json --all
    [ "$status" -eq 0 ]

    # Verify URL fields
    issueUrl=$(echo "$output" | jq -r '.[0].issueUrl')
    [ "$issueUrl" = "https://linear.app/test/issue/TEST-400" ]

    prUrl=$(echo "$output" | jq -r '.[0].prUrl')
    [ "$prUrl" = "https://github.com/org/repo/pull/42" ]

    # Verify timestamp fields are present (from WorktreeRegistration)
    registeredAt=$(echo "$output" | jq -r '.[0].registeredAt')
    [[ "$registeredAt" == *"2024-03-01"* ]]

    lastActivityAt=$(echo "$output" | jq -r '.[0].lastActivityAt')
    [[ "$lastActivityAt" == *"2024-03-15"* ]]
}
