#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw analyze command
# PURPOSE: Tests that analyze delegates to iw start with the triage prompt

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

# Use a unique tmux socket for test isolation
TMUX_SOCKET="iw-test-$$"

# Detect Docker: tmux capture-pane returns empty content without a real terminal
is_docker() { [ -f /.dockerenv ] || grep -q docker /proc/1/cgroup 2>/dev/null; }

setup() {
    # Disable dashboard server communication during tests
    export IW_SERVER_DISABLED=1
    # Tell iw commands to use isolated tmux socket
    export IW_TMUX_SOCKET="$TMUX_SOCKET"

    # Create a temporary directory for each test
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    # Create a git repo with initial commit
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    echo "initial" > README.md
    git add README.md
    git commit -m "Initial commit"

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
    # Kill all sessions on the isolated test socket
    tmux -L "$TMUX_SOCKET" kill-server 2>/dev/null || true

    # Clean up worktrees in parent directory (sibling to test dir)
    if [ -n "$TEST_DIR" ]; then
        local parent_dir="$(dirname "$TEST_DIR")"
        # Remove any testproject-* directories that are siblings to our test
        rm -rf "$parent_dir"/testproject-* 2>/dev/null || true
    fi

    # Clean up temporary directory
    cd /
    rm -rf "$TEST_DIR"
}

@test "analyze without issue ID exits with code 1 and shows usage" {
    run "$PROJECT_ROOT/iw" analyze

    [ "$status" -eq 1 ]
    [[ "$output" == *"Missing issue ID"* ]] || [[ "$output" == *"Usage"* ]]
}

@test "analyze delegates to iw start and creates worktree and session" {
    local parent_dir="$(dirname "$(pwd)")"
    local expected_worktree="$parent_dir/testproject-IWLE-123"

    run "$PROJECT_ROOT/iw" analyze IWLE-123

    [ "$status" -eq 0 ]

    # Worktree should be created (proves iw start was called)
    [ -d "$expected_worktree" ]

    # Session should be created
    tmux -L "$TMUX_SOCKET" has-session -t "testproject-IWLE-123" 2>/dev/null
}

@test "analyze passes triage prompt to claude command" {
    is_docker && skip "tmux capture-pane needs real terminal (see IW-293)"

    run "$PROJECT_ROOT/iw" analyze IWLE-456

    [ "$status" -eq 0 ]

    # Give tmux a moment to process the keys
    sleep 0.1

    # Capture pane content to verify the triage prompt was sent
    local pane_content
    pane_content="$(tmux -L "$TMUX_SOCKET" capture-pane -p -t testproject-IWLE-456)"

    # Should contain the claude command with the triage prompt
    [[ "$pane_content" == *"claude --dangerously-skip-permissions"* ]]
    [[ "$pane_content" == *"/iterative-works:triage-issue"* ]]
}
