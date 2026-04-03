#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw open --prompt flag
# PURPOSE: Tests that --prompt passes prompt to session action hooks

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

@test "open --prompt with existing worktree warns about missing hook" {
    # Create worktree without session
    git worktree add -b IWLE-123 "../testproject-IWLE-123"

    # Run open with --prompt (unset TMUX to avoid nested session detection)
    run env -u TMUX "$PROJECT_ROOT/iw" open --prompt "review this code" IWLE-123

    # Should succeed (graceful degradation)
    [ "$status" -eq 0 ]

    # Session should be created
    tmux -L "$TMUX_SOCKET" has-session -t "testproject-IWLE-123" 2>/dev/null

    # Without a session action hook, --prompt is ignored with a warning
    [[ "$output" == *"no session action hook"* ]]
}

@test "open --prompt infers issue from branch and warns about missing hook" {
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

    # Should warn about missing hook
    [[ "$output" == *"no session action hook"* ]]
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

    # Should NOT warn about missing hook (no --prompt)
    [[ "$output" != *"no session action hook"* ]]
}

@test "open --prompt with existing session warns about missing hook" {
    # Create worktree and session
    git worktree add -b IWLE-222 "../testproject-IWLE-222"
    tmux -L "$TMUX_SOCKET" new-session -d -s "testproject-IWLE-222" -c "../testproject-IWLE-222"

    # Open with --prompt should warn about missing hook
    run "$PROJECT_ROOT/iw" open --prompt "continue work" IWLE-222

    [ "$status" -eq 0 ]

    # Should warn about missing hook
    [[ "$output" == *"no session action hook"* ]]
}
