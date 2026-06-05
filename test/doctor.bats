#!/usr/bin/env bats
# PURPOSE: E2E smoke tests for `iw doctor` — happy path, error path, filter flags, fix mode
# PURPOSE: Per-check output formatting + summary lives in core/test/Doctor*Test.scala; per-hook
# PURPOSE: check logic lives in core/test/{Scalafmt,Scalafix,GitHooks,Contributing,CI}ChecksTest.scala

PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"

setup() {
    export IW_SERVER_DISABLED=1
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
}

teardown() {
    rm -rf "$TEST_DIR"
}

write_valid_config() {
    mkdir -p .iw
    cat > .iw/config.conf <<'EOF'
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF
}

@test "doctor fails when no config exists" {
    run "$PROJECT_ROOT/iw" doctor

    [ "$status" -eq 1 ]
    [[ "$output" == *"Configuration"* ]]
    [[ "$output" == *"Missing or invalid"* ]]
}

@test "doctor passes with valid config and runs the expected base checks" {
    write_valid_config
    unset LINEAR_API_TOKEN

    run "$PROJECT_ROOT/iw" doctor

    [[ "$output" == *"Environment Check"* ]]
    [[ "$output" == *"Git repository"* ]]
    [[ "$output" == *"Configuration"* ]]
}

@test "doctor --quality runs only quality gate checks" {
    write_valid_config
    unset LINEAR_API_TOKEN

    run "$PROJECT_ROOT/iw" doctor --quality

    [[ ! "$output" == *"Git repository"* ]]
    [[ ! "$output" == *"Configuration"* ]]
    [[ "$output" == *".scalafmt.conf"* ]]
    [[ "$output" == *".scalafix.conf"* ]]
}

@test "doctor --env runs only environment checks" {
    write_valid_config
    unset LINEAR_API_TOKEN

    run "$PROJECT_ROOT/iw" doctor --env

    [[ "$output" == *"Git repository"* ]]
    [[ "$output" == *"Configuration"* ]]
    [[ ! "$output" == *".scalafmt.conf"* ]]
    [[ ! "$output" == *".scalafix.conf"* ]]
}

@test "doctor --fix reports nothing to fix when quality gates all pass" {
    mkdir -p .iw
    cat > .iw/config.conf <<'EOF'
tracker {
  type = github
  repository = "owner/repo"
  teamPrefix = "REPO"
}

project {
  name = repo
}
EOF

    cat > .scalafmt.conf <<'EOF'
version = "3.8.1"
maxColumn = 120
EOF

    cat > .scalafix.conf <<'EOF'
rules = [
  DisableSyntax
]
DisableSyntax.noNulls = true
DisableSyntax.noVars = true
DisableSyntax.noThrows = true
DisableSyntax.noReturns = true
EOF

    mkdir -p .git-hooks
    touch .git-hooks/pre-commit .git-hooks/pre-push
    chmod +x .git-hooks/pre-commit .git-hooks/pre-push
    git config core.hooksPath .git-hooks

    cat > CONTRIBUTING.md <<'EOF'
# Contributing Guide

## CI Pipeline
Our continuous integration runs checks on every PR.

## Git Hooks
Install pre-commit hooks to run checks locally.

## Running Checks Locally
You can run all checks locally before pushing.
EOF

    mkdir -p .github/workflows
    touch .github/workflows/ci.yml

    run "$PROJECT_ROOT/iw" doctor --fix

    [ "$status" -eq 0 ]
    [[ "$output" == *"All quality gate checks pass. Nothing to fix."* ]]
}
