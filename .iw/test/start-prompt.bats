#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw start --prompt flag
# PURPOSE: Tests that --prompt sends claude command to session instead of attaching

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

# Use a unique tmux socket for test isolation
TMUX_SOCKET="iw-test-$$"

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

@test "start --prompt creates worktree and session, sends keys without attaching" {
    # Store parent directory for later assertion
    local parent_dir="$(dirname "$(pwd)")"
    local expected_worktree="$parent_dir/testproject-IWLE-123"

    # Run start with --prompt
    run "$PROJECT_ROOT/iw" start --prompt "do something" IWLE-123

    # Should succeed
    [ "$status" -eq 0 ]

    # Worktree should be created
    [ -d "$expected_worktree" ]

    # Session should be created
    tmux -L "$TMUX_SOCKET" has-session -t "testproject-IWLE-123" 2>/dev/null

    # Should show success message (not "Attaching" or "Switching")
    [[ "$output" != *"Attaching"* ]]
    [[ "$output" != *"Switching"* ]]
}

@test "start --prompt sends correct claude command to session" {
    # Run start with --prompt
    run "$PROJECT_ROOT/iw" start --prompt "analyze this code" IWLE-456

    [ "$status" -eq 0 ]

    # Give tmux a moment to process the keys
    sleep 0.1

    # Capture pane content to verify the command was sent
    local pane_content
    pane_content="$(tmux -L "$TMUX_SOCKET" capture-pane -p -t testproject-IWLE-456)"

    # Should contain the claude command
    [[ "$pane_content" == *"claude --dangerously-skip-permissions --prompt"* ]]
    [[ "$pane_content" == *"analyze this code"* ]]
}

@test "start --prompt fails without prompt text" {
    run "$PROJECT_ROOT/iw" start --prompt IWLE-789

    [ "$status" -eq 1 ]
    [[ "$output" == *"--prompt"* ]] || [[ "$output" == *"Usage"* ]] || [[ "$output" == *"requires"* ]]
}

@test "start --prompt fails without issue ID" {
    run "$PROJECT_ROOT/iw" start --prompt "do something"

    [ "$status" -eq 1 ]
    [[ "$output" == *"Missing issue ID"* ]] || [[ "$output" == *"Usage"* ]]
}

@test "start without --prompt still works (regression test)" {
    # This tests that normal start behavior is preserved
    run "$PROJECT_ROOT/iw" start IWLE-999

    # Worktree should be created
    [ -d "../testproject-IWLE-999" ]

    # Session should be created
    tmux -L "$TMUX_SOCKET" has-session -t "testproject-IWLE-999" 2>/dev/null

    # Should show attaching or switching message (normal behavior)
    [[ "$output" == *"Attaching"* ]] || [[ "$output" == *"Switching"* ]] || [[ "$output" == *"tmux session"* ]]
}

@test "start --prompt with empty string works" {
    run "$PROJECT_ROOT/iw" start --prompt "" IWLE-111

    [ "$status" -eq 0 ]

    # Session should be created
    tmux -L "$TMUX_SOCKET" has-session -t "testproject-IWLE-111" 2>/dev/null

    # Pane should contain the claude command (even with empty prompt)
    sleep 0.1
    local pane_content
    pane_content="$(tmux -L "$TMUX_SOCKET" capture-pane -p -t testproject-IWLE-111)"
    [[ "$pane_content" == *"claude --dangerously-skip-permissions --prompt"* ]]
}

@test "start --prompt handles quotes in prompt text" {
    run "$PROJECT_ROOT/iw" start --prompt 'say "hello world"' IWLE-222

    [ "$status" -eq 0 ]

    # Session should be created
    tmux -L "$TMUX_SOCKET" has-session -t "testproject-IWLE-222" 2>/dev/null

    # Pane should contain the command with escaped quotes
    sleep 0.1
    local pane_content
    pane_content="$(tmux -L "$TMUX_SOCKET" capture-pane -p -t testproject-IWLE-222)"
    [[ "$pane_content" == *"claude --dangerously-skip-permissions --prompt"* ]]
}
