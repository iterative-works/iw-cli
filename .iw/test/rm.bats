#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw rm command
# PURPOSE: Tests removal of worktrees with safety checks for uncommitted changes and active sessions

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

@test "rm successfully removes worktree with session" {
    # Create worktree and session on the test socket
    git worktree add -b IWLE-123 "../testproject-IWLE-123"
    tmux -L "$TMUX_SOCKET" new-session -d -s "testproject-IWLE-123" -c "../testproject-IWLE-123"

    # Verify both exist
    tmux -L "$TMUX_SOCKET" has-session -t "testproject-IWLE-123"
    [ -d "../testproject-IWLE-123" ]

    # Remove worktree
    run "$PROJECT_ROOT/iw" rm IWLE-123

    # Should succeed
    [ "$status" -eq 0 ]

    # Both should be removed
    ! tmux -L "$TMUX_SOCKET" has-session -t "testproject-IWLE-123" 2>/dev/null
    [ ! -d "../testproject-IWLE-123" ]
}

@test "rm successfully removes worktree without session" {
    # Create worktree without session
    git worktree add -b IWLE-456 "../testproject-IWLE-456"

    # Verify worktree exists
    [ -d "../testproject-IWLE-456" ]

    # Remove worktree
    run "$PROJECT_ROOT/iw" rm IWLE-456

    # Should succeed
    [ "$status" -eq 0 ]

    # Worktree should be removed
    [ ! -d "../testproject-IWLE-456" ]
}

@test "rm --force bypasses confirmation for uncommitted changes" {
    # Create worktree
    git worktree add -b IWLE-789 "../testproject-IWLE-789"

    # Add uncommitted changes
    echo "uncommitted" > "../testproject-IWLE-789/test.txt"

    # Remove with --force (should not prompt)
    run "$PROJECT_ROOT/iw" rm --force IWLE-789

    # Should succeed
    [ "$status" -eq 0 ]

    # Worktree should be removed
    [ ! -d "../testproject-IWLE-789" ]
}

@test "rm fails when removing active session" {
    skip "Cannot test active session from bats - requires manual verification"
}

@test "rm fails for non-existent worktree" {
    run "$PROJECT_ROOT/iw" rm IWLE-999

    [ "$status" -eq 1 ]
    [[ "$output" == *"not found"* ]] || [[ "$output" == *"does not exist"* ]]
}

@test "rm fails for invalid issue ID format" {
    run "$PROJECT_ROOT/iw" rm 123-invalid

    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid issue ID format"* ]] || [[ "$output" == *"expected"* ]]
}

@test "rm fails without config file" {
    # Remove config file
    rm .iw/config.conf

    run "$PROJECT_ROOT/iw" rm IWLE-111

    [ "$status" -eq 1 ]
    [[ "$output" == *"Cannot read configuration"* ]] || [[ "$output" == *"config"* ]]
}

@test "rm converts lowercase issue ID to uppercase" {
    # Create worktree with uppercase
    git worktree add -b IWLE-222 "../testproject-IWLE-222"

    # Remove with lowercase
    run "$PROJECT_ROOT/iw" rm iwle-222

    # Should succeed
    [ "$status" -eq 0 ]

    # Worktree should be removed
    [ ! -d "../testproject-IWLE-222" ]
}

@test "rm shows appropriate messages" {
    # Create worktree with session on the test socket
    git worktree add -b IWLE-333 "../testproject-IWLE-333"
    tmux -L "$TMUX_SOCKET" new-session -d -s "testproject-IWLE-333" -c "../testproject-IWLE-333"

    run "$PROJECT_ROOT/iw" rm IWLE-333

    # Should show removal messages
    [[ "$output" == *"Killing"* ]] || [[ "$output" == *"killing"* ]] || [[ "$output" == *"Removing"* ]]
}

@test "rm does not delete git branch" {
    # Create worktree
    git worktree add -b IWLE-444 "../testproject-IWLE-444"

    # Verify branch exists
    git show-ref --verify --quiet refs/heads/IWLE-444

    # Remove worktree
    run "$PROJECT_ROOT/iw" rm IWLE-444

    # Branch should still exist
    git show-ref --verify --quiet refs/heads/IWLE-444
}
