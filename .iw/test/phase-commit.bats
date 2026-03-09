#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw phase-commit command
# PURPOSE: Tests staging, committing, JSON output, and error cases

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

    # Commit everything so subsequent tests start clean
    git add -A 2>/dev/null
    git commit -q -m "Add config" 2>/dev/null || true
}

teardown() {
    cd /
    rm -rf "$TEST_DIR"
}

make_change() {
    echo "change" >> README.md
}

@test "phase-commit --title 'Test commit' stages, commits, outputs valid JSON" {
    make_change

    run "$PROJECT_ROOT/iw" phase-commit --title "Test commit"

    [ "$status" -eq 0 ]

    # Output should be valid JSON
    echo "$output" | jq . > /dev/null

    # Check required fields
    local issueId
    issueId="$(echo "$output" | jq -r '.issueId')"
    [ "$issueId" = "TEST-100" ]

    local phaseNumber
    phaseNumber="$(echo "$output" | jq -r '.phaseNumber')"
    [ "$phaseNumber" = "01" ]

    # commitSha should be non-empty
    local commitSha
    commitSha="$(echo "$output" | jq -r '.commitSha')"
    [ -n "$commitSha" ]

    # filesCommitted should be > 0
    local filesCommitted
    filesCommitted="$(echo "$output" | jq -r '.filesCommitted')"
    [ "$filesCommitted" -gt 0 ]

    # message should match
    local message
    message="$(echo "$output" | jq -r '.message')"
    [ "$message" = "Test commit" ]
}

@test "phase-commit SHA in output matches actual git HEAD" {
    make_change

    run "$PROJECT_ROOT/iw" phase-commit --title "Test commit"

    [ "$status" -eq 0 ]

    local commitSha
    commitSha="$(echo "$output" | jq -r '.commitSha')"

    local headSha
    headSha="$(git rev-parse HEAD)"

    [ "$commitSha" = "$headSha" ]
}

@test "phase-commit --items produces multi-line commit message with bullets" {
    make_change

    run "$PROJECT_ROOT/iw" phase-commit --title "Title" --items "item1,item2"

    [ "$status" -eq 0 ]

    local message
    message="$(echo "$output" | jq -r '.message')"

    [[ "$message" == *"- item1"* ]]
    [[ "$message" == *"- item2"* ]]
}

@test "phase-commit without --title exits with error" {
    make_change

    run "$PROJECT_ROOT/iw" phase-commit

    [ "$status" -eq 1 ]
    [[ "$output" == *"title"* ]] || [[ "$output" == *"--title"* ]]
}

@test "phase-commit when not on a phase branch exits with error" {
    # Switch to feature branch (not a phase branch)
    git checkout -q TEST-100
    make_change

    run "$PROJECT_ROOT/iw" phase-commit --title "Test"

    [ "$status" -eq 1 ]
    [[ "$output" == *"phase"* ]]
}

@test "phase-commit with no changes exits with error" {
    # No changes made - nothing to commit

    run "$PROJECT_ROOT/iw" phase-commit --title "Test"

    [ "$status" -eq 1 ]
    [[ "$output" == *"Failed to commit"* ]]
}

@test "phase-commit updates phase task file Phase Status to Complete" {
    make_change

    # Create the phase task file
    mkdir -p project-management/issues/TEST-100
    cat > project-management/issues/TEST-100/phase-01-tasks.md << 'EOF'
# Phase 1 Tasks

- [x] [impl] Task one
- [x] [test] Task two

**Phase Status:** Not Started
EOF

    run "$PROJECT_ROOT/iw" phase-commit --title "Test"

    [ "$status" -eq 0 ]

    local content
    content="$(cat project-management/issues/TEST-100/phase-01-tasks.md)"

    [[ "$content" == *"Phase Status:** Complete"* ]]
}
