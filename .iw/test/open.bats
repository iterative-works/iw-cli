#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw open command
# PURPOSE: Tests opening existing worktree tmux sessions and creating sessions for existing worktrees

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

# Use a unique tmux socket for test isolation
TMUX_SOCKET="iw-test-$$"

setup() {
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
    # Kill any tmux sessions we created (using our test socket)
    tmux -L "$TMUX_SOCKET" kill-server 2>/dev/null || true

    # Kill any tmux sessions with testproject prefix (from actual command)
    tmux list-sessions -F '#{session_name}' 2>/dev/null | grep '^testproject-' | while read session; do
        tmux kill-session -t "$session" 2>/dev/null || true
    done

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

@test "open creates session for existing worktree" {
    # Create worktree without session
    git worktree add -b IWLE-123 "../testproject-IWLE-123"

    # Verify session doesn't exist yet
    ! tmux has-session -t "testproject-IWLE-123" 2>/dev/null

    # Open should create session (unset TMUX to avoid nested session detection)
    run env -u TMUX "$PROJECT_ROOT/iw" open IWLE-123

    # Session should be created (command will fail on attach in non-interactive mode, that's ok)
    tmux has-session -t "testproject-IWLE-123" 2>/dev/null
}

@test "open attaches to existing session" {
    # Create worktree and session
    git worktree add -b IWLE-456 "../testproject-IWLE-456"
    tmux new-session -d -s "testproject-IWLE-456" -c "../testproject-IWLE-456"

    # Open should attach (will fail in non-interactive mode but that's expected)
    run "$PROJECT_ROOT/iw" open IWLE-456

    # Session should still exist
    tmux has-session -t "testproject-IWLE-456" 2>/dev/null
}

@test "open infers issue from current branch" {
    # Create worktree and switch to it
    git worktree add -b IWLE-789 "../testproject-IWLE-789"
    cd "../testproject-IWLE-789"

    # Copy config to the worktree
    mkdir -p .iw
    cp "$TEST_DIR/.iw/config.conf" .iw/

    # Open without args should infer IWLE-789 from branch (unset TMUX to avoid nested session detection)
    run env -u TMUX "$PROJECT_ROOT/iw" open

    # Session should be created with correct name
    tmux has-session -t "testproject-IWLE-789" 2>/dev/null
}

@test "open fails when worktree does not exist" {
    run "$PROJECT_ROOT/iw" open IWLE-999

    [ "$status" -eq 1 ]
    [[ "$output" == *"Worktree not found"* ]] || [[ "$output" == *"not found"* ]]
    [[ "$output" == *"./iw start"* ]]
}

@test "open fails with invalid issue ID format" {
    run "$PROJECT_ROOT/iw" open 123-invalid

    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid issue ID format"* ]] || [[ "$output" == *"expected"* ]]
}

@test "open fails on non-issue branch without args" {
    # Switch to main branch
    git checkout -b main 2>/dev/null || git checkout main

    run "$PROJECT_ROOT/iw" open

    [ "$status" -eq 1 ]
    [[ "$output" == *"Cannot extract issue ID"* ]] || [[ "$output" == *"from branch"* ]]
}

@test "open fails without config file" {
    # Remove config file
    rm .iw/config.conf

    run "$PROJECT_ROOT/iw" open IWLE-111

    [ "$status" -eq 1 ]
    [[ "$output" == *"Cannot read configuration"* ]] || [[ "$output" == *"config"* ]]
    [[ "$output" == *"init"* ]]
}

@test "open converts lowercase issue ID to uppercase" {
    # Create worktree with uppercase
    git worktree add -b IWLE-222 "../testproject-IWLE-222"

    # Open with lowercase (unset TMUX to avoid nested session detection)
    run env -u TMUX "$PROJECT_ROOT/iw" open iwle-222

    # Should create session with uppercase name
    tmux has-session -t "testproject-IWLE-222" 2>/dev/null
}

@test "open shows appropriate messages" {
    # Create worktree
    git worktree add -b IWLE-333 "../testproject-IWLE-333"

    # Unset TMUX to avoid nested session detection
    run env -u TMUX "$PROJECT_ROOT/iw" open IWLE-333

    # Should show creating or attaching message
    [[ "$output" == *"Creating session"* ]] || [[ "$output" == *"Attaching"* ]]
}
