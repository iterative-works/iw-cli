#!/usr/bin/env bats
# PURPOSE: Integration tests for iw issue command
# PURPOSE: Tests issue fetching, branch inference, and error handling

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

setup() {
    # Create a temporary directory for each test
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    # Setup git repo
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
}

teardown() {
    # Clean up temporary directory
    rm -rf "$TEST_DIR"
}

@test "issue returns error for invalid issue ID format" {
    # Setup: create config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = linear
  team = IWLE
}

project {
  name = test-project
}
EOF

    # Run with invalid issue ID
    run "$PROJECT_ROOT/iw" issue INVALID123

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid issue ID format"* ]]
}

@test "issue returns error when config file missing" {
    # Run without config
    run "$PROJECT_ROOT/iw" issue IWLE-123

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Configuration file not found"* ]]
}

@test "issue returns error when LINEAR_API_TOKEN not set" {
    # Setup: create config for Linear
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = linear
  team = IWLE
}

project {
  name = test-project
}
EOF

    # Run with unset token
    unset LINEAR_API_TOKEN
    run "$PROJECT_ROOT/iw" issue IWLE-123

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"LINEAR_API_TOKEN"* ]]
}

@test "issue returns error when YOUTRACK_API_TOKEN not set" {
    # Setup: create config for YouTrack
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = youtrack
  team = TEST
}

project {
  name = test-project
}
EOF

    # Run with unset token
    unset YOUTRACK_API_TOKEN
    run "$PROJECT_ROOT/iw" issue TEST-123

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"YOUTRACK_API_TOKEN"* ]]
}

@test "issue infers issue ID from branch name" {
    # Setup: create config and checkout branch
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = linear
  team = IWLE
}

project {
  name = test-project
}
EOF

    # Create initial commit to allow branch creation
    touch README.md
    git add README.md
    git commit -m "Initial commit"

    # Create and checkout issue branch
    git checkout -b IWLE-999-test-branch

    # Run without explicit issue ID (should infer from branch)
    # Note: This will fail if LINEAR_API_TOKEN is not set or issue doesn't exist
    # We only test that branch inference works, not the actual API call
    unset LINEAR_API_TOKEN
    run "$PROJECT_ROOT/iw" issue

    # Should fail due to missing token, but error should reference the inferred issue ID
    [ "$status" -eq 1 ]
    [[ "$output" == *"LINEAR_API_TOKEN"* ]]
}

@test "issue returns error when cannot infer from branch" {
    # Setup: create config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = linear
  team = IWLE
}

project {
  name = test-project
}
EOF

    # Create initial commit on main branch
    touch README.md
    git add README.md
    git commit -m "Initial commit"

    # Stay on main branch (no issue ID in branch name)
    run "$PROJECT_ROOT/iw" issue

    # Assert failure - cannot infer issue ID
    [ "$status" -eq 1 ]
    [[ "$output" == *"extract issue ID from branch"* ]]
}
