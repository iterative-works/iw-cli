#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw phase-pr command
# PURPOSE: Tests argument validation and error cases (full PR creation requires real GitHub)

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

    # Create feature branch and phase sub-branch
    git checkout -q -b TEST-100
    git checkout -q -b TEST-100-phase-01

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

@test "phase-pr when not on a phase branch exits with error" {
    # Switch to the feature branch
    git checkout -q TEST-100

    run "$PROJECT_ROOT/iw" phase-pr --title "Test PR"

    [ "$status" -eq 1 ]
    [[ "$output" == *"phase"* ]]
}

@test "phase-pr without --title exits with error" {
    run "$PROJECT_ROOT/iw" phase-pr

    [ "$status" -eq 1 ]
    [[ "$output" == *"title"* ]] || [[ "$output" == *"--title"* ]]
}

@test "phase-pr without config file exits with error about missing config" {
    rm -f .iw/config.conf

    run "$PROJECT_ROOT/iw" phase-pr --title "Test PR"

    [ "$status" -eq 1 ]
    [[ "$output" == *"config"* ]] || [[ "$output" == *"init"* ]]
}
