#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw register command
# PURPOSE: Tests registering current worktree to the dashboard

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

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
    # Clean up temporary directory
    cd /
    rm -rf "$TEST_DIR"
}

@test "register succeeds in valid worktree with issue branch" {
    # Create and checkout issue branch
    git checkout -b IWLE-123

    run "$PROJECT_ROOT/iw" register

    [ "$status" -eq 0 ]
    [[ "$output" == *"Registered worktree"* ]]
    [[ "$output" == *"IWLE-123"* ]]
    [[ "$output" == *"$TEST_DIR"* ]]
}

@test "register fails when not in a git repository" {
    # Create a non-git directory
    cd /tmp
    mkdir -p iw-test-no-git-$$
    cd iw-test-no-git-$$

    run "$PROJECT_ROOT/iw" register

    # Cleanup
    rm -rf /tmp/iw-test-no-git-$$

    [ "$status" -eq 1 ]
    [[ "$output" == *"Not in a git repository"* ]] || [[ "$output" == *"git"* ]]
}

@test "register fails on branch without issue ID" {
    # Create a non-issue branch name
    git checkout -b feature-xyz

    run "$PROJECT_ROOT/iw" register

    [ "$status" -eq 1 ]
    [[ "$output" == *"Cannot extract issue ID"* ]] || [[ "$output" == *"from branch"* ]]
}

@test "register fails without config file" {
    # Create and checkout issue branch
    git checkout -b IWLE-456

    # Remove config file
    rm .iw/config.conf

    run "$PROJECT_ROOT/iw" register

    [ "$status" -eq 1 ]
    [[ "$output" == *"Cannot read configuration"* ]] || [[ "$output" == *"config"* ]]
    [[ "$output" == *"init"* ]]
}

@test "register shows warning if dashboard communication fails but exits 0" {
    # Create and checkout issue branch
    git checkout -b IWLE-789

    # Note: Dashboard will likely fail (server not running) but command should still succeed
    run "$PROJECT_ROOT/iw" register

    # Command should succeed even if dashboard fails
    [ "$status" -eq 0 ]
    [[ "$output" == *"Registered worktree"* ]]
}

@test "register converts lowercase issue ID from branch to uppercase" {
    # Create and checkout lowercase issue branch
    git checkout -b iwle-999

    run "$PROJECT_ROOT/iw" register

    [ "$status" -eq 0 ]
    [[ "$output" == *"Registered worktree"* ]]
    [[ "$output" == *"IWLE-999"* ]]
}
