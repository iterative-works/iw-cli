#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw status command
# PURPOSE: Tests error handling and argument parsing for server-backed status queries

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"

setup() {
    # Create a temporary directory for each test
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    # Override HOME to isolate server config
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

@test "status when server not running shows helpful error" {
    # Point to a port where no server is running
    export IW_SERVER_PORT=19876

    run "$PROJECT_ROOT/iw" status TEST-123
    [ "$status" -eq 1 ]
    [[ "$output" =~ "server" ]] || [[ "$output" =~ "Server" ]]
}

@test "status with server disabled shows error" {
    export IW_SERVER_DISABLED=1
    run "$PROJECT_ROOT/iw" status TEST-123
    [ "$status" -eq 1 ]
    [[ "$output" =~ "disabled" ]] || [[ "$output" =~ "Server" ]]
}

@test "status without args and not on issue branch shows error" {
    export IW_SERVER_DISABLED=1
    # On 'main' branch — can't infer issue ID
    run "$PROJECT_ROOT/iw" status
    [ "$status" -eq 1 ]
}

@test "status without args infers issue from branch name" {
    # Create and checkout an issue branch
    git checkout -b TEST-123 -q 2>/dev/null

    # Server not running, but issue ID resolution from branch should work
    # (error will be about server, not about issue ID parsing)
    run "$PROJECT_ROOT/iw" status
    [ "$status" -eq 1 ]
    # Should NOT show "Usage:" — it successfully parsed the issue ID
    [[ ! "$output" =~ "Usage:" ]]
}
