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
