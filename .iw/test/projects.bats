#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw projects command
# PURPOSE: Tests projects listing with state.json data

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
}

teardown() {
    # Clean up temporary directory
    cd /
    rm -rf "$TEST_DIR"
}

create_state_with_projects() {
    # Create state.json with worktrees from multiple projects
    cat > "$HOME/.local/share/iw/server/state.json" << 'EOF'
{
  "worktrees": {
    "TEST-123": {
      "issueId": "TEST-123",
      "path": "/tmp/testproject-TEST-123",
      "trackerType": "linear",
      "team": "TEST",
      "registeredAt": "2024-01-01T00:00:00Z",
      "lastSeenAt": "2024-01-01T00:00:00Z"
    },
    "TEST-456": {
      "issueId": "TEST-456",
      "path": "/tmp/testproject-TEST-456",
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

    # Create config files for the projects
    mkdir -p /tmp/testproject/.iw
    cat > /tmp/testproject/.iw/config.conf << 'CONF'
project {
  name = testproject
}

tracker {
  type = linear
  team = TEST
}
CONF

    mkdir -p /tmp/otherproject/.iw
    cat > /tmp/otherproject/.iw/config.conf << 'CONF'
project {
  name = otherproject
}

tracker {
  type = github
  team = OTHER
}
CONF
}

@test "projects with no state file shows empty message" {
    run "$PROJECT_ROOT/iw" projects
    [ "$status" -eq 0 ]
    [[ "$output" =~ "No projects registered" ]]
}

@test "projects with populated state shows project info" {
    create_state_with_projects

    run "$PROJECT_ROOT/iw" projects
    [ "$status" -eq 0 ]
    [[ "$output" =~ "testproject" ]]
    [[ "$output" =~ "otherproject" ]]
    [[ "$output" =~ "2 worktrees" ]] # testproject has 2 worktrees
    [[ "$output" =~ "1 worktree" ]]  # otherproject has 1 worktree
}

@test "projects --json with populated state outputs valid JSON" {
    create_state_with_projects

    run "$PROJECT_ROOT/iw" projects --json
    [ "$status" -eq 0 ]

    # Verify it's valid JSON by piping through jq
    echo "$output" | jq . > /dev/null
}

@test "projects --json with empty state outputs empty array" {
    run "$PROJECT_ROOT/iw" projects --json
    [ "$status" -eq 0 ]
    [ "$output" = "[]" ]
}

@test "projects --json contains expected fields" {
    create_state_with_projects

    run "$PROJECT_ROOT/iw" projects --json
    [ "$status" -eq 0 ]

    # Check for required fields
    echo "$output" | jq '.[0].name' > /dev/null
    echo "$output" | jq '.[0].path' > /dev/null
    echo "$output" | jq '.[0].trackerType' > /dev/null
    echo "$output" | jq '.[0].team' > /dev/null
    echo "$output" | jq '.[0].worktreeCount' > /dev/null
}
