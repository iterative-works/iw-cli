#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw phase-advance command
# PURPOSE: Tests argument validation and error cases (full advance requires real GitHub)

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

setup() {
    # Disable dashboard server communication during tests
    export IW_SERVER_DISABLED=1

    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    # Create a git repo
    git init -q 2>/dev/null
    git config user.email "test@example.com"
    git config user.name "Test User"
    echo "initial" > README.md
    git add README.md 2>/dev/null
    git commit -q -m "Initial commit" 2>/dev/null

    # Initialize iw config
    mkdir -p .iw
    cat > .iw/config.conf << 'EOF'
project {
  name = testproject
}

tracker {
  type = github
  repository = "test-org/test-repo"
  teamPrefix = "TEST"
}
EOF
}

teardown() {
    cd /
    rm -rf "$TEST_DIR"
}

@test "phase-advance without merged PR exits with error" {
    # gh is available but there's no remote or PR, so the PR status check should fail

    run "$PROJECT_ROOT/iw" phase-advance --issue-id TEST-100 --phase-number 1

    [ "$status" -eq 1 ]
    [[ "$output" == *"PR"* ]] || [[ "$output" == *"pr"* ]] || [[ "$output" == *"check"* ]] || [[ "$output" == *"status"* ]]
}

@test "phase-advance from feature branch without --phase-number exits with error" {
    # Create feature branch
    git checkout -q -b TEST-100

    run "$PROJECT_ROOT/iw" phase-advance --issue-id TEST-100

    [ "$status" -eq 1 ]
    [[ "$output" == *"phase-number"* ]] || [[ "$output" == *"phase number"* ]]
}
