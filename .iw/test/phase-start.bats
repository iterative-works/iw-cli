#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw phase-start command
# PURPOSE: Tests phase sub-branch creation, JSON output, and error cases

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

setup() {
    # Disable dashboard server communication during tests
    export IW_SERVER_DISABLED=1

    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    # Create a git repo on a feature branch
    git init -q 2>/dev/null
    git config user.email "test@example.com"
    git config user.name "Test User"
    echo "initial" > README.md
    git add README.md 2>/dev/null
    git commit -q -m "Initial commit" 2>/dev/null

    # Create and switch to a feature branch
    git checkout -q -b TEST-100

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

@test "phase-start 1 creates sub-branch and outputs valid JSON" {
    run "$PROJECT_ROOT/iw" phase-start 1

    [ "$status" -eq 0 ]

    # Output should be valid JSON parseable by jq
    echo "$output" | jq . > /dev/null

    # Check required JSON fields
    local issueId
    issueId="$(echo "$output" | jq -r '.issueId')"
    [ "$issueId" = "TEST-100" ]

    local phaseNumber
    phaseNumber="$(echo "$output" | jq -r '.phaseNumber')"
    [ "$phaseNumber" = "01" ]

    local branch
    branch="$(echo "$output" | jq -r '.branch')"
    [ "$branch" = "TEST-100-phase-01" ]

    # baselineSha should be non-empty
    local baselineSha
    baselineSha="$(echo "$output" | jq -r '.baselineSha')"
    [ -n "$baselineSha" ]
}

@test "phase-start 1 creates branch named {issue}-phase-01" {
    run "$PROJECT_ROOT/iw" phase-start 1

    [ "$status" -eq 0 ]

    # Verify we're now on the phase branch
    local currentBranch
    currentBranch="$(git branch --show-current)"
    [ "$currentBranch" = "TEST-100-phase-01" ]
}

@test "phase-start with no args exits with error and usage message" {
    run "$PROJECT_ROOT/iw" phase-start

    [ "$status" -eq 1 ]
    [[ "$output" == *"phase-number"* ]] || [[ "$output" == *"Usage"* ]] || [[ "$output" == *"phase number"* ]]
}

@test "phase-start abc exits with error for invalid phase number" {
    run "$PROJECT_ROOT/iw" phase-start abc

    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid phase number"* ]] || [[ "$output" == *"invalid"* ]]
}

@test "phase-start 1 when already on a phase sub-branch exits with error" {
    # Create and switch to a phase branch
    git checkout -q -b TEST-100-phase-01

    run "$PROJECT_ROOT/iw" phase-start 1

    [ "$status" -eq 1 ]
    [[ "$output" == *"phase"* ]]
}

@test "phase-start 1 when phase branch already exists exits with error" {
    # Create the phase branch already
    git branch TEST-100-phase-01

    run "$PROJECT_ROOT/iw" phase-start 1

    [ "$status" -eq 1 ]
}

@test "phase-start 2 with --issue-id TEST-999 uses provided issue ID in output" {
    run "$PROJECT_ROOT/iw" phase-start 2 --issue-id TEST-999

    [ "$status" -eq 0 ]

    local issueId
    issueId="$(echo "$output" | jq -r '.issueId')"
    [ "$issueId" = "TEST-999" ]

    local branch
    branch="$(echo "$output" | jq -r '.branch')"
    [ "$branch" = "TEST-100-phase-02" ]
}
