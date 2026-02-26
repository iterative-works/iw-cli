#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw status command
# PURPOSE: Tests detailed worktree status display with state.json and git data

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

create_state_with_worktree() {
    local worktree_path="$TEST_DIR"

    # Create state.json with a worktree pointing to current directory
    cat > "$HOME/.local/share/iw/server/state.json" << EOF
{
  "worktrees": {
    "TEST-123": {
      "issueId": "TEST-123",
      "path": "$worktree_path",
      "trackerType": "linear",
      "team": "TEST",
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

@test "status with explicit issue ID shows status" {
    create_state_with_worktree

    run "$PROJECT_ROOT/iw" status TEST-123
    [ "$status" -eq 0 ]
    [[ "$output" =~ "TEST-123" ]]
}

@test "status --json outputs valid JSON object" {
    create_state_with_worktree

    run "$PROJECT_ROOT/iw" status --json TEST-123
    [ "$status" -eq 0 ]

    # Verify it's valid JSON by piping through jq
    echo "$output" | jq . > /dev/null
    # Verify it's an object, not an array
    [ "$(echo "$output" | jq 'type')" = '"object"' ]
}

@test "status for nonexistent issue ID exits with error" {
    run "$PROJECT_ROOT/iw" status NONEXISTENT-99
    [ "$status" -eq 1 ]
    [[ "$output" =~ "not found" ]]
}

@test "status --json contains expected fields" {
    create_state_with_worktree

    run "$PROJECT_ROOT/iw" status --json TEST-123
    [ "$status" -eq 0 ]

    # Check for required fields
    echo "$output" | jq '.issueId' > /dev/null
    echo "$output" | jq '.path' > /dev/null
    echo "$output" | jq '.needsAttention' > /dev/null
}

@test "status without args infers issue from branch" {
    create_state_with_worktree

    # Create and checkout a branch with issue ID
    git checkout -b TEST-123 -q 2>/dev/null

    run "$PROJECT_ROOT/iw" status
    [ "$status" -eq 0 ]
    [[ "$output" =~ "TEST-123" ]]
}
