#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw open --prompt flag
# PURPOSE: Tests that --prompt sends claude command to session instead of attaching

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"

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

@test "open --prompt with existing worktree sends keys without attaching" {
    # Create worktree without session
    git worktree add -b IWLE-123 "../testproject-IWLE-123"

    # Run open with --prompt (unset TMUX to avoid nested session detection)
    run env -u TMUX "$PROJECT_ROOT/iw" open --prompt "review this code" IWLE-123

    # Should succeed
    [ "$status" -eq 0 ]

    # Session should be created
    tmux -L "$TMUX_SOCKET" has-session -t "testproject-IWLE-123" 2>/dev/null

    # Should NOT show attaching or switching message
    [[ "$output" != *"Attaching"* ]]
    [[ "$output" != *"Switching"* ]]
}

@test "open --prompt sends correct claude command to session" {
    is_docker && skip "tmux capture-pane needs real terminal (see IW-293)"
    # Create worktree
    git worktree add -b IWLE-456 "../testproject-IWLE-456"

    # Run open with --prompt
    run env -u TMUX "$PROJECT_ROOT/iw" open --prompt "analyze this feature" IWLE-456

    [ "$status" -eq 0 ]

    # Give tmux a moment to process the keys
    sleep 0.1

    # Capture pane content to verify the command was sent
    local pane_content
    pane_content="$(tmux -L "$TMUX_SOCKET" capture-pane -p -t testproject-IWLE-456)"

    # Should contain the claude command
    [[ "$pane_content" == *"claude --dangerously-skip-permissions"* ]]
    [[ "$pane_content" == *"analyze this feature"* ]]
}

@test "open --prompt infers issue from branch and sends keys" {
    is_docker && skip "tmux capture-pane needs real terminal (see IW-293)"
    # Create worktree and switch to it
    git worktree add -b IWLE-789 "../testproject-IWLE-789"
    cd "../testproject-IWLE-789"

    # Copy config to the worktree
    mkdir -p .iw
    cp "$TEST_DIR/.iw/config.conf" .iw/

    # Open without args should infer IWLE-789 from branch
    run env -u TMUX "$PROJECT_ROOT/iw" open --prompt "fix the bug"

    [ "$status" -eq 0 ]

    # Session should be created
    tmux -L "$TMUX_SOCKET" has-session -t "testproject-IWLE-789" 2>/dev/null

    # Verify command was sent
    sleep 0.1
    local pane_content
    pane_content="$(tmux -L "$TMUX_SOCKET" capture-pane -p -t testproject-IWLE-789)"
    [[ "$pane_content" == *"claude --dangerously-skip-permissions"* ]]
    [[ "$pane_content" == *"fix the bug"* ]]
}

@test "open --prompt fails without prompt text" {
    # Create worktree
    git worktree add -b IWLE-999 "../testproject-IWLE-999"

    run "$PROJECT_ROOT/iw" open --prompt

    [ "$status" -eq 1 ]
    [[ "$output" == *"--prompt"* ]] || [[ "$output" == *"Usage"* ]] || [[ "$output" == *"requires"* ]]
}

@test "open without --prompt still works (regression test)" {
    # Create worktree
    git worktree add -b IWLE-111 "../testproject-IWLE-111"

    # Normal open should still work
    run env -u TMUX "$PROJECT_ROOT/iw" open IWLE-111

    # Session should be created
    tmux -L "$TMUX_SOCKET" has-session -t "testproject-IWLE-111" 2>/dev/null

    # Should show attaching or creating message (normal behavior)
    [[ "$output" == *"Attaching"* ]] || [[ "$output" == *"Creating session"* ]]
}

@test "open --prompt with existing session sends keys to existing session" {
    # Create worktree and session
    git worktree add -b IWLE-222 "../testproject-IWLE-222"
    tmux -L "$TMUX_SOCKET" new-session -d -s "testproject-IWLE-222" -c "../testproject-IWLE-222"

    # Open with --prompt should send keys to existing session
    run "$PROJECT_ROOT/iw" open --prompt "continue work" IWLE-222

    [ "$status" -eq 0 ]

    # Verify command was sent (remove line breaks from pane content for matching)
    sleep 0.1
    local pane_content
    pane_content="$(tmux -L "$TMUX_SOCKET" capture-pane -p -t testproject-IWLE-222 | tr -d '\n')"
    [[ "$pane_content" == *"claude --dangerously-skip-permissions"* ]]
    [[ "$pane_content" == *"continue work"* ]]
}

@test "open --prompt with empty string works" {
    is_docker && skip "tmux capture-pane needs real terminal (see IW-293)"
    # Create worktree
    git worktree add -b IWLE-333 "../testproject-IWLE-333"

    run env -u TMUX "$PROJECT_ROOT/iw" open --prompt "" IWLE-333

    [ "$status" -eq 0 ]

    # Session should be created
    tmux -L "$TMUX_SOCKET" has-session -t "testproject-IWLE-333" 2>/dev/null

    # Pane should contain the claude command
    sleep 0.1
    local pane_content
    pane_content="$(tmux -L "$TMUX_SOCKET" capture-pane -p -t testproject-IWLE-333)"
    [[ "$pane_content" == *"claude --dangerously-skip-permissions"* ]]
}

@test "open --prompt handles quotes in prompt text" {
    is_docker && skip "tmux capture-pane needs real terminal (see IW-293)"
    # Create worktree
    git worktree add -b IWLE-444 "../testproject-IWLE-444"

    run env -u TMUX "$PROJECT_ROOT/iw" open --prompt 'say "hello world"' IWLE-444

    [ "$status" -eq 0 ]

    # Session should be created
    tmux -L "$TMUX_SOCKET" has-session -t "testproject-IWLE-444" 2>/dev/null

    # Pane should contain the command with single-quoted prompt
    sleep 0.1
    local pane_content
    pane_content="$(tmux -L "$TMUX_SOCKET" capture-pane -p -t testproject-IWLE-444)"
    [[ "$pane_content" == *"claude --dangerously-skip-permissions"* ]]
}

@test "open --prompt protects against shell metacharacters" {
    is_docker && skip "tmux capture-pane needs real terminal (see IW-293)"
    # Create worktree
    git worktree add -b IWLE-555 "../testproject-IWLE-555"

    run env -u TMUX "$PROJECT_ROOT/iw" open --prompt '$(echo INJECTED)' IWLE-555

    [ "$status" -eq 0 ]

    # Give tmux a moment to process
    sleep 0.1

    # Capture pane content — the $() should appear literally (single-quoted), not executed
    local pane_content
    pane_content="$(tmux -L "$TMUX_SOCKET" capture-pane -p -t testproject-IWLE-555)"
    # The literal $(echo should appear in the pane (inside single quotes)
    [[ "$pane_content" == *'$(echo'* ]]
    [[ "$pane_content" == *"claude --dangerously-skip-permissions"* ]]
}
