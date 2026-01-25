#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw start command
# PURPOSE: Tests worktree creation and tmux session management

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

# Use a unique tmux socket for test isolation
TMUX_SOCKET="iw-test-$$"

setup() {
    # Disable dashboard server communication during tests
    export IW_SERVER_DISABLED=1

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

    # Kill any tmux sessions with testproject prefix (from actual start command)
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

# Helper to create tmux session with test socket
create_test_session() {
    local name="$1"
    local dir="${2:-.}"
    tmux -L "$TMUX_SOCKET" new-session -d -s "$name" -c "$dir"
}

# Helper to check if session exists with test socket
session_exists() {
    local name="$1"
    tmux -L "$TMUX_SOCKET" has-session -t "$name" 2>/dev/null
}

@test "start creates worktree for valid issue ID" {
    # Store parent directory for later assertion
    local parent_dir="$(dirname "$(pwd)")"
    local expected_worktree="$parent_dir/testproject-IWLE-123"

    # Run start command
    # Note: We can't actually test tmux attachment in non-interactive mode
    # so we just verify worktree creation and branch
    run "$PROJECT_ROOT/iw" start IWLE-123

    # The command will fail when trying to attach to tmux in non-interactive mode
    # but worktree and session should still be created

    # Check worktree was created as sibling (use absolute path)
    [ -d "$expected_worktree" ]

    # Check it's a valid worktree
    [ -d "$expected_worktree/.git" ] || [ -f "$expected_worktree/.git" ]

    # Check branch was created
    git branch --list IWLE-123 | grep -q IWLE-123
}

@test "start fails with missing issue ID" {
    run "$PROJECT_ROOT/iw" start

    [ "$status" -eq 1 ]
    [[ "$output" == *"Missing issue ID"* ]]
    [[ "$output" == *"Usage:"* ]]
}

@test "start fails with invalid issue ID format - invalid characters" {
    # 'not-valid' has lowercase letters where PROJECT should be, which fails after uppercase conversion
    run "$PROJECT_ROOT/iw" start not-valid

    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid issue ID format"* ]] || [[ "$output" == *"expected"* ]]
}

@test "start fails with invalid issue ID format - no number" {
    run "$PROJECT_ROOT/iw" start IWLE-

    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid issue ID format"* ]] || [[ "$output" == *"expected"* ]]
}

@test "start fails with invalid issue ID format - no dash" {
    run "$PROJECT_ROOT/iw" start IWLE123

    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid issue ID format"* ]] || [[ "$output" == *"expected"* ]]
}

@test "start fails when directory already exists" {
    # Create the directory manually (not as worktree)
    mkdir -p "../testproject-IWLE-456"

    run "$PROJECT_ROOT/iw" start IWLE-456

    [ "$status" -eq 1 ]
    [[ "$output" == *"already exists"* ]]
}

@test "start fails when worktree already exists and suggests using open" {
    # Create worktree first
    git worktree add -b IWLE-789 "../testproject-IWLE-789"

    run "$PROJECT_ROOT/iw" start IWLE-789

    [ "$status" -eq 1 ]
    [[ "$output" == *"already exists"* ]]
    [[ "$output" == *"./iw open"* ]]
}

@test "start fails when tmux session already exists and suggests using open" {
    # Create a tmux session with the expected name
    # Note: This test uses the default tmux socket since start.scala does
    tmux new-session -d -s "testproject-IWLE-999"

    run "$PROJECT_ROOT/iw" start IWLE-999

    # Clean up the session we created
    tmux kill-session -t "testproject-IWLE-999" 2>/dev/null || true

    [ "$status" -eq 1 ]
    [[ "$output" == *"already exists"* ]]
    [[ "$output" == *"./iw open"* ]]
}

@test "start fails without config file and suggests init" {
    # Remove config file
    rm .iw/config.conf

    run "$PROJECT_ROOT/iw" start IWLE-111

    [ "$status" -eq 1 ]
    [[ "$output" == *"Cannot read configuration"* ]] || [[ "$output" == *"config"* ]]
    [[ "$output" == *"init"* ]]
}

@test "start uses existing branch if present" {
    # Create a branch first
    git branch IWLE-222

    run "$PROJECT_ROOT/iw" start IWLE-222

    # Check output mentions using existing branch
    [[ "$output" == *"existing branch"* ]] || [[ "$output" == *"Using"* ]]

    # Worktree should be created
    [ -d "../testproject-IWLE-222" ]
}

@test "start creates worktree as sibling directory" {
    run "$PROJECT_ROOT/iw" start IWLE-333

    # Verify sibling relationship
    [ -d "../testproject-IWLE-333" ]

    # Get absolute paths and verify they share parent
    local test_parent="$(dirname "$(pwd)")"
    local worktree_parent="$(dirname "$(cd ../testproject-IWLE-333 && pwd)")"
    [ "$test_parent" = "$worktree_parent" ]
}

@test "start creates branch matching issue ID" {
    run "$PROJECT_ROOT/iw" start IWLE-444

    # Verify branch exists
    git branch --list IWLE-444 | grep -q IWLE-444

    # Verify worktree is on that branch
    local branch_in_worktree
    branch_in_worktree="$(cd ../testproject-IWLE-444 && git branch --show-current)"
    [ "$branch_in_worktree" = "IWLE-444" ]
}

@test "start converts lowercase issue ID to uppercase" {
    run "$PROJECT_ROOT/iw" start iwle-555

    # Should create worktree with uppercase ID
    [ -d "../testproject-IWLE-555" ]

    # Branch should be uppercase
    git branch --list IWLE-555 | grep -q IWLE-555
}

@test "start shows progress messages" {
    run "$PROJECT_ROOT/iw" start IWLE-666

    # Check for expected progress messages
    [[ "$output" == *"Creating worktree"* ]]
    [[ "$output" == *"Creating"* ]] || [[ "$output" == *"branch"* ]]
}

# ========== GitHub Team Prefix Tests ==========

@test "start with github tracker and numeric ID applies team prefix" {
    # Setup config for GitHub with team prefix
    cat > .iw/config.conf << 'EOF'
project {
  name = testproject
}

tracker {
  type = github
  repository = "iterative-works/iw-cli"
  teamPrefix = "IWCLI"
}
EOF

    # Store parent directory for assertions
    local parent_dir="$(dirname "$(pwd)")"
    local expected_worktree="$parent_dir/testproject-IWCLI-51"

    # Run start with numeric ID
    run "$PROJECT_ROOT/iw" start 51

    # Worktree should be created with IWCLI-51 format
    [ -d "$expected_worktree" ]

    # Branch should be IWCLI-51
    git branch --list IWCLI-51 | grep -q IWCLI-51

    # Verify worktree is on that branch
    local branch_in_worktree
    branch_in_worktree="$(cd "$expected_worktree" && git branch --show-current)"
    [ "$branch_in_worktree" = "IWCLI-51" ]
}

@test "start with github tracker and full TEAM-NNN format works" {
    # Setup config for GitHub with team prefix
    cat > .iw/config.conf << 'EOF'
project {
  name = testproject
}

tracker {
  type = github
  repository = "iterative-works/iw-cli"
  teamPrefix = "IWCLI"
}
EOF

    # Store parent directory for assertions
    local parent_dir="$(dirname "$(pwd)")"
    local expected_worktree="$parent_dir/testproject-IWCLI-99"

    # Run start with full IWCLI-99 format
    run "$PROJECT_ROOT/iw" start IWCLI-99

    # Worktree should be created
    [ -d "$expected_worktree" ]

    # Branch should be IWCLI-99
    git branch --list IWCLI-99 | grep -q IWCLI-99
}

@test "start with github applies team prefix to large issue numbers" {
    # Setup config for GitHub with team prefix
    cat > .iw/config.conf << 'EOF'
project {
  name = testproject
}

tracker {
  type = github
  repository = "iterative-works/iw-cli"
  teamPrefix = "IWCLI"
}
EOF

    # Run start with large numeric ID
    run "$PROJECT_ROOT/iw" start 99999

    # Branch should be IWCLI-99999
    git branch --list IWCLI-99999 | grep -q IWCLI-99999

    # Worktree should exist
    [ -d "../testproject-IWCLI-99999" ]
}

@test "start with github and single-digit issue number" {
    # Setup config for GitHub with team prefix
    cat > .iw/config.conf << 'EOF'
project {
  name = testproject
}

tracker {
  type = github
  repository = "iterative-works/iw-cli"
  teamPrefix = "IWCLI"
}
EOF

    # Run start with single-digit numeric ID
    run "$PROJECT_ROOT/iw" start 1

    # Branch should be IWCLI-1
    git branch --list IWCLI-1 | grep -q IWCLI-1

    # Worktree should exist
    [ -d "../testproject-IWCLI-1" ]
}
