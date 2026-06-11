#!/usr/bin/env bats
# PURPOSE: E2E smoke for iw phase-commit (full round-trip through scala-cli + live adapters)
# PURPOSE: Detailed scenarios live in core/test/PhaseCommitHarnessTest.scala

PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"

setup() {
    export IW_SERVER_DISABLED=1

    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    git init -q 2>/dev/null
    git config user.email "test@example.com"
    git config user.name "Test User"
    echo "initial" > README.md
    git add README.md 2>/dev/null
    git commit -q -m "Initial commit" 2>/dev/null

    git checkout -q -b TEST-100
    git checkout -q -b TEST-100-phase-01

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

    git add -A 2>/dev/null
    git commit -q -m "Add config" 2>/dev/null || true
}

teardown() {
    cd /
    rm -rf "$TEST_DIR"
}

@test "phase-commit happy path: stages, commits, prints JSON, SHA matches HEAD" {
    echo "change" >> README.md
    git add README.md

    run "$PROJECT_ROOT/iw" phase-commit --title "Test commit"

    [ "$status" -eq 0 ]

    echo "$output" | jq . > /dev/null
    [ "$(echo "$output" | jq -r '.issueId')" = "TEST-100" ]
    [ "$(echo "$output" | jq -r '.phaseNumber')" = "01" ]
    [ "$(echo "$output" | jq -r '.message')" = "Test commit" ]
    [ "$(echo "$output" | jq -r '.commitSha')" = "$(git rev-parse HEAD)" ]
}

@test "phase-commit includes task file update and leaves a clean worktree" {
    echo "change" >> README.md
    git add README.md

    mkdir -p project-management/issues/TEST-100
    cat > project-management/issues/TEST-100/phase-01-tasks.md << 'EOF'
# Phase 1 Tasks

- [x] [impl] Task one

**Phase Status:** Not Started
EOF
    git add project-management/issues/TEST-100/phase-01-tasks.md

    run "$PROJECT_ROOT/iw" phase-commit --title "Test"

    [ "$status" -eq 0 ]
    grep -q "Phase Status:\*\* Complete" project-management/issues/TEST-100/phase-01-tasks.md

    run git status --porcelain
    [ -z "$output" ]
}
