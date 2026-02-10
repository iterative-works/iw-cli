#!/usr/bin/env bats
# PURPOSE: Integration tests for iw doctor command
# PURPOSE: Tests environment validation with base checks and hooks

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

setup() {
    # Create a temporary directory for each test
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    # Setup a git repository
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
}

teardown() {
    # Clean up temporary directory
    rm -rf "$TEST_DIR"
}

@test "doctor fails when no config exists" {
    # Run without creating config
    run "$PROJECT_ROOT/iw" doctor

    # Should fail with config error
    [ "$status" -eq 1 ]
    [[ "$output" == *"Configuration"* ]]
    [[ "$output" == *"Missing or invalid"* ]]
}

@test "doctor passes with valid config and all dependencies" {
    # Setup: create valid config
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    # Unset LINEAR_API_TOKEN to skip that check
    unset LINEAR_API_TOKEN

    # Run doctor (tmux should be installed in CI)
    run "$PROJECT_ROOT/iw" doctor

    # Should show checks ran
    [[ "$output" == *"Git repository"* ]]
    [[ "$output" == *"Configuration"* ]]
    [[ "$output" == *"tmux"* ]]
}

@test "doctor shows LINEAR_API_TOKEN error when not set" {
    # Setup: create config with Linear tracker
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    # Ensure token is not set
    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should fail and show token error
    [ "$status" -eq 1 ]
    [[ "$output" == *"LINEAR_API_TOKEN"* ]]
    [[ "$output" == *"Not set"* ]]
    [[ "$output" == *"export LINEAR_API_TOKEN"* ]]
}

@test "doctor skips LINEAR_API_TOKEN for YouTrack projects" {
    # Setup: create config with YouTrack tracker
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = youtrack
  team = TEST
}

project {
  name = test-project
}
EOF

    # Ensure token is not set
    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should not check LINEAR_API_TOKEN
    [[ "$output" == *"LINEAR_API_TOKEN"* ]]
    [[ "$output" == *"Skipped"* ]]
}

@test "doctor returns exit code 1 when checks fail" {
    # Run in non-git directory
    cd "$(mktemp -d)"

    # Create config but no git repo
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should fail
    [ "$status" -eq 1 ]
    [[ "$output" == *"failed"* ]]
}

@test "doctor returns exit code 0 when all checks pass" {
    # Setup: create valid config
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = youtrack
  team = TEST
}

project {
  name = test-project
}
EOF

    # Create .scalafmt.conf to satisfy Scalafmt checks
    cat > .scalafmt.conf << EOF
version = "3.8.1"
maxColumn = 120
EOF

    # Create .scalafix.conf to satisfy Scalafix checks
    cat > .scalafix.conf << EOF
rules = [
  DisableSyntax
]
DisableSyntax.noNulls = true
DisableSyntax.noVars = true
DisableSyntax.noThrows = true
DisableSyntax.noReturns = true
EOF

    # Run doctor (YouTrack skips token check, tmux should be available)
    run "$PROJECT_ROOT/iw" doctor

    # Should pass
    [ "$status" -eq 0 ]
    [[ "$output" == *"All checks passed"* ]]
}

@test "doctor displays formatted output with symbols" {
    # Setup: create valid config
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should have formatted output with symbols
    [[ "$output" == *"Environment Check"* ]]
    [[ "$output" == *"✓"* ]]  # Success symbol
    [[ "$output" == *"✗"* ]]  # Error symbol (for missing token)
}

@test "doctor shows gh CLI check passed for GitHub project when gh installed" {
    # Setup: create config with GitHub tracker
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = github
  team = owner
  repository = "owner/repo"
}

project {
  name = repo
}
EOF

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show gh CLI check
    [[ "$output" == *"gh CLI"* ]]

    # If gh is installed (which it should be in CI), show success
    # Otherwise, show error message
    if command -v gh >/dev/null 2>&1; then
        [[ "$output" == *"gh CLI"*"Installed"* ]] || [[ "$output" == *"✓"* ]]
    else
        [[ "$output" == *"gh CLI"*"Not found"* ]]
    fi
}

@test "doctor skips gh checks for non-GitHub project (Linear)" {
    # Setup: create config with Linear tracker
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show gh CLI check as skipped
    [[ "$output" == *"gh CLI"* ]]
    [[ "$output" == *"Skipped"* ]]
    [[ "$output" == *"Not using GitHub"* ]]
}

@test "doctor shows gh auth check when gh is installed" {
    # Setup: create config with GitHub tracker
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = github
  team = owner
  repository = "owner/repo"
}

project {
  name = repo
}
EOF

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show gh auth check
    [[ "$output" == *"gh auth"* ]]

    # If gh is installed, check should run (not skip)
    if command -v gh >/dev/null 2>&1; then
        # Either authenticated (success) or not authenticated (error)
        [[ "$output" == *"gh auth"* ]]
        ! [[ "$output" == *"gh auth"*"gh not installed"* ]]
    else
        # If gh not installed, auth check should be skipped
        [[ "$output" == *"gh auth"*"Skipped"* ]]
    fi
}

@test "doctor shows Scalafmt config check when .scalafmt.conf exists" {
    # Setup: create valid config
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    # Create .scalafmt.conf with version
    cat > .scalafmt.conf << EOF
version = "3.8.1"
maxColumn = 120
EOF

    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show Scalafmt check
    [[ "$output" == *".scalafmt.conf"* ]]
    [[ "$output" == *"Found"* ]]
    [[ "$output" == *".scalafmt.conf version"* ]]
    [[ "$output" == *"Configured"* ]]
}

@test "doctor shows Scalafmt error when .scalafmt.conf missing" {
    # Setup: create valid config
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    # Do NOT create .scalafmt.conf

    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show Scalafmt check as error
    [ "$status" -eq 1 ]
    [[ "$output" == *".scalafmt.conf"* ]]
    [[ "$output" == *"Missing"* ]]
    [[ "$output" == *"Create .scalafmt.conf in project root"* ]]
}

@test "doctor shows Scalafmt warning when .scalafmt.conf exists without version" {
    # Setup: create valid config
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    # Create .scalafmt.conf WITHOUT version
    cat > .scalafmt.conf << EOF
maxColumn = 120
align.preset = more
EOF

    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show config check as success and version check as warning
    [[ "$output" == *".scalafmt.conf"* ]]
    [[ "$output" == *"Found"* ]]
    [[ "$output" == *".scalafmt.conf version"* ]]
    [[ "$output" == *"Version not specified"* ]]
    [[ "$output" == *"Add 'version = \"3.x.x\"' to .scalafmt.conf"* ]]
}

@test "doctor shows Scalafix config check when .scalafix.conf exists" {
    # Setup: create valid config
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    # Create .scalafix.conf with DisableSyntax rule and all required sub-rules
    cat > .scalafix.conf << EOF
rules = [
  DisableSyntax
]
DisableSyntax.noNulls = true
DisableSyntax.noVars = true
DisableSyntax.noThrows = true
DisableSyntax.noReturns = true
EOF

    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show Scalafix checks
    [[ "$output" == *".scalafix.conf"* ]]
    [[ "$output" == *"Found"* ]]
    [[ "$output" == *".scalafix.conf rules"* ]]
    [[ "$output" == *"Configured"* ]]
}

@test "doctor shows Scalafix error when .scalafix.conf missing" {
    # Setup: create valid config
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    # Do NOT create .scalafix.conf

    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show Scalafix check as error
    [ "$status" -eq 1 ]
    [[ "$output" == *".scalafix.conf"* ]]
    [[ "$output" == *"Missing"* ]]
    [[ "$output" == *"Create .scalafix.conf in project root"* ]]
}

@test "doctor shows Scalafix warning when .scalafix.conf exists without DisableSyntax" {
    # Setup: create valid config
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    # Create .scalafix.conf WITHOUT DisableSyntax
    cat > .scalafix.conf << EOF
rules = [
  OrganizeImports
]
EOF

    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show config check as success and rules check as warning
    [[ "$output" == *".scalafix.conf"* ]]
    [[ "$output" == *"Found"* ]]
    [[ "$output" == *".scalafix.conf rules"* ]]
    [[ "$output" == *"DisableSyntax not configured"* ]]
    [[ "$output" == *"Add DisableSyntax rule to .scalafix.conf"* ]]
}

@test "doctor shows Scalafix warning when DisableSyntax missing some rules" {
    # Setup: create valid config
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    # Create .scalafix.conf with DisableSyntax but missing noVars and noThrows
    cat > .scalafix.conf << EOF
rules = [
  DisableSyntax
]
DisableSyntax.noNulls = true
DisableSyntax.noReturns = true
EOF

    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show config check as success and rules check as warning with missing rules
    [[ "$output" == *".scalafix.conf"* ]]
    [[ "$output" == *"Found"* ]]
    [[ "$output" == *".scalafix.conf rules"* ]]
    [[ "$output" == *"Missing rules:"* ]]
    [[ "$output" == *"noThrows"* ]]
    [[ "$output" == *"noVars"* ]]
    [[ "$output" == *"Add missing rules to DisableSyntax in .scalafix.conf"* ]]
}

@test "doctor shows git hooks dir check when .git-hooks/ exists" {
    # Setup: create valid config
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    # Create .git-hooks/ directory
    mkdir -p .git-hooks

    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show git hooks dir check
    [[ "$output" == *"Git hooks dir"* ]]
    [[ "$output" == *"Found"* ]]
}

@test "doctor shows git hooks dir error when .git-hooks/ missing" {
    # Setup: create valid config
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    # Do NOT create .git-hooks/ directory

    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show git hooks dir check as error
    [ "$status" -eq 1 ]
    [[ "$output" == *"Git hooks dir"* ]]
    [[ "$output" == *"Missing"* ]]
    [[ "$output" == *"Create .git-hooks/ directory in project root"* ]]
}

@test "doctor shows git hook files check when hooks exist" {
    # Setup: create valid config
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    # Create .git-hooks/ with hook files
    mkdir -p .git-hooks
    touch .git-hooks/pre-commit .git-hooks/pre-push
    chmod +x .git-hooks/pre-commit .git-hooks/pre-push

    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show git hook files check
    [[ "$output" == *"Git hook files"* ]]
    [[ "$output" == *"Found"* ]]
}

@test "doctor shows git hook files error when hooks missing" {
    # Setup: create valid config
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    # Create .git-hooks/ but no hook files
    mkdir -p .git-hooks

    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show git hook files check as error
    [ "$status" -eq 1 ]
    [[ "$output" == *"Git hook files"* ]]
    [[ "$output" == *"Missing: pre-commit, pre-push"* ]]
    [[ "$output" == *"Create missing hook files in .git-hooks/"* ]]
}

@test "doctor shows git hooks installed when core.hooksPath configured" {
    # Setup: create valid config
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    # Create .git-hooks/ with hook files
    mkdir -p .git-hooks
    touch .git-hooks/pre-commit .git-hooks/pre-push
    chmod +x .git-hooks/pre-commit .git-hooks/pre-push

    # Configure core.hooksPath
    git config core.hooksPath .git-hooks

    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show hooks installed
    [[ "$output" == *"Git hooks installed"* ]]
    [[ "$output" == *"Installed"* ]]

    # Clean up
    git config --unset core.hooksPath
}

@test "doctor shows git hooks not installed warning when hooks not configured" {
    # Setup: create valid config
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    # Create .git-hooks/ with hook files
    mkdir -p .git-hooks
    touch .git-hooks/pre-commit .git-hooks/pre-push
    chmod +x .git-hooks/pre-commit .git-hooks/pre-push

    # Ensure core.hooksPath is NOT configured
    git config --unset core.hooksPath || true

    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show hooks not installed warning
    [[ "$output" == *"Git hooks installed"* ]]
    [[ "$output" == *"Not installed"* ]]
    [[ "$output" == *"Run: git config core.hooksPath .git-hooks"* ]]
}

@test "doctor shows CONTRIBUTING.md check when file exists with all sections" {
    # Setup: create valid config
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    # Create CONTRIBUTING.md with all required sections
    cat > CONTRIBUTING.md << EOF
# Contributing Guide

## CI Pipeline
Our continuous integration runs checks on every PR.

## Git Hooks
Install pre-commit hooks to run checks locally.

## Running Checks Locally
You can run all checks locally before pushing.
EOF

    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show CONTRIBUTING.md checks
    [[ "$output" == *"CONTRIBUTING.md"* ]]
    [[ "$output" == *"Found"* ]]
    [[ "$output" == *"CONTRIBUTING.md sections"* ]]
    [[ "$output" == *"Complete"* ]]
}

@test "doctor shows CONTRIBUTING.md warning when file missing" {
    # Setup: create valid config
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    # Do NOT create CONTRIBUTING.md

    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show CONTRIBUTING.md check as warning (not error)
    [[ "$output" == *"CONTRIBUTING.md"* ]]
    [[ "$output" == *"Missing"* ]]
}

@test "doctor shows CONTRIBUTING.md sections warning when sections missing" {
    # Setup: create valid config
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    # Create CONTRIBUTING.md WITHOUT all required sections (missing CI)
    cat > CONTRIBUTING.md << EOF
# Contributing Guide

## Git Hooks
Install pre-commit hooks.

## Running Checks Locally
You can run checks locally.
EOF

    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show CONTRIBUTING.md file check as success
    [[ "$output" == *"CONTRIBUTING.md"* ]]
    [[ "$output" == *"Found"* ]]
    # Should show sections check as warning with missing topics
    [[ "$output" == *"CONTRIBUTING.md sections"* ]]
    [[ "$output" == *"Missing: CI"* ]]
    [[ "$output" == *"Add sections covering: CI"* ]]
}

@test "doctor shows CI workflow check for GitHub when ci.yml exists" {
    # Setup: create config with GitHub tracker
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = github
  repository = "owner/repo"
  teamPrefix = "REPO"
}

project {
  name = repo
}
EOF

    # Create .github/workflows/ci.yml
    mkdir -p .github/workflows
    touch .github/workflows/ci.yml

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show CI workflow check
    [[ "$output" == *"CI workflow"* ]]
    [[ "$output" == *"Found (.github/workflows/ci.yml)"* ]]
}

@test "doctor shows CI workflow error for GitHub when ci.yml missing" {
    # Setup: create config with GitHub tracker
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = github
  repository = "owner/repo"
  teamPrefix = "REPO"
}

project {
  name = repo
}
EOF

    # Do NOT create .github/workflows/ci.yml

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show CI workflow check as error
    [ "$status" -eq 1 ]
    [[ "$output" == *"CI workflow"* ]]
    [[ "$output" == *"Missing"* ]]
    [[ "$output" == *"Create .github/workflows/ci.yml"* ]]
}

@test "doctor shows CI workflow check for GitLab when .gitlab-ci.yml exists" {
    # Setup: create config with GitLab tracker
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = gitlab
  repository = "owner/repo"
  teamPrefix = "REPO"
}

project {
  name = repo
}
EOF

    # Create .gitlab-ci.yml
    touch .gitlab-ci.yml

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show CI workflow check
    [[ "$output" == *"CI workflow"* ]]
    [[ "$output" == *"Found (.gitlab-ci.yml)"* ]]
}

@test "doctor shows CI workflow error for GitLab when .gitlab-ci.yml missing" {
    # Setup: create config with GitLab tracker
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = gitlab
  repository = "owner/repo"
  teamPrefix = "REPO"
}

project {
  name = repo
}
EOF

    # Do NOT create .gitlab-ci.yml

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show CI workflow check as error
    [ "$status" -eq 1 ]
    [[ "$output" == *"CI workflow"* ]]
    [[ "$output" == *"Missing"* ]]
    [[ "$output" == *"Create .gitlab-ci.yml"* ]]
}

@test "doctor shows CI workflow warning for Linear when no CI file found" {
    # Setup: create config with Linear tracker
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    # Do NOT create any CI file

    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show CI workflow check as warning (not error)
    [[ "$output" == *"CI workflow"* ]]
    [[ "$output" == *"No CI workflow found"* ]]
}

@test "doctor shows CI workflow success for Linear when GitHub Actions exists" {
    # Setup: create config with Linear tracker
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    # Create .github/workflows/ci.yml
    mkdir -p .github/workflows
    touch .github/workflows/ci.yml

    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show CI workflow check as success
    [[ "$output" == *"CI workflow"* ]]
    [[ "$output" == *"Found (.github/workflows/ci.yml)"* ]]
}

@test "doctor shows grouped output with section headers" {
    # Setup: create valid config
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show section headers
    [[ "$output" == *"=== Environment ==="* ]]
    [[ "$output" == *"=== Project Quality Gates ==="* ]]

    # Environment checks should come before quality checks
    # Extract line numbers to verify order
    env_line=$(echo "$output" | grep -n "=== Environment ===" | cut -d: -f1 | head -1)
    quality_line=$(echo "$output" | grep -n "=== Project Quality Gates ===" | cut -d: -f1 | head -1)

    # Environment section should come first
    [ "$env_line" -lt "$quality_line" ]
}

@test "doctor --quality shows only quality gate checks" {
    # Setup: create valid config
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    unset LINEAR_API_TOKEN

    # Run doctor with --quality flag
    run "$PROJECT_ROOT/iw" doctor --quality

    # Should NOT show environment checks (Git repository, Configuration)
    [[ ! "$output" == *"Git repository"* ]]
    [[ ! "$output" == *"Configuration"* ]]

    # Should show quality gate checks
    [[ "$output" == *".scalafmt.conf"* ]]
    [[ "$output" == *".scalafix.conf"* ]]
    [[ "$output" == *"Git hooks"* ]]
}

@test "doctor --env shows only environment checks" {
    # Setup: create valid config
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    unset LINEAR_API_TOKEN

    # Run doctor with --env flag
    run "$PROJECT_ROOT/iw" doctor --env

    # Should show environment checks
    [[ "$output" == *"Git repository"* ]]
    [[ "$output" == *"Configuration"* ]]

    # Should NOT show quality gate checks
    [[ ! "$output" == *".scalafmt.conf"* ]]
    [[ ! "$output" == *".scalafix.conf"* ]]
    [[ ! "$output" == *"Git hooks dir"* ]]
}

@test "doctor --quality exit code reflects only quality gate checks" {
    # Setup: create valid config with all quality gates passing
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    # Create all quality gate files to make checks pass
    cat > .scalafmt.conf << EOF
version = "3.8.1"
maxColumn = 120
EOF

    cat > .scalafix.conf << EOF
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

    cat > CONTRIBUTING.md << EOF
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

    unset LINEAR_API_TOKEN

    # Run doctor with --quality flag (should pass despite missing LINEAR_API_TOKEN)
    run "$PROJECT_ROOT/iw" doctor --quality

    # Should succeed because quality checks pass (environment checks are not run)
    [ "$status" -eq 0 ]
    [[ "$output" == *"All checks passed"* ]]

    # Clean up
    git config --unset core.hooksPath
}

@test "doctor --env exit code reflects only environment checks" {
    # Setup: create valid config with environment passing but quality failing
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = youtrack
  team = TEST
}

project {
  name = test-project
}
EOF

    # Do NOT create quality gate files (will fail if run)
    # But run with --env flag, so only environment checks matter

    # Run doctor with --env flag
    run "$PROJECT_ROOT/iw" doctor --env

    # Should succeed because environment checks pass (quality checks are not run)
    [ "$status" -eq 0 ]
    [[ "$output" == *"All checks passed"* ]]
}
