#!/usr/bin/env bats
# PURPOSE: Integration tests for iw project-context command
# PURPOSE: Tests context output for GitHub and GitLab configurations

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

setup() {
    # Disable dashboard server communication during tests
    export IW_SERVER_DISABLED=1

    # Create a temporary directory for each test
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    # Create a git repo (required for iw commands)
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
}

teardown() {
    # Clean up temporary directory
    rm -rf "$TEST_DIR"
}

@test "project-context outputs GitHub context for GitHub config" {
    # Setup: create GitHub config with remote
    mkdir -p .iw
    cat > .iw/config.conf << 'EOF'
tracker {
  type = github
  repository = "iterative-works/iw-cli"
  teamPrefix = "IW"
}
project {
  name = iw-cli
}
EOF
    git remote add origin git@github.com:iterative-works/iw-cli.git

    # Run command
    run "$PROJECT_ROOT/iw" project-context

    # Assert
    [ "$status" -eq 0 ]
    [[ "$output" == *"**Project:** iw-cli"* ]]
    [[ "$output" == *"**Repository:** iterative-works/iw-cli"* ]]
    [[ "$output" == *"**Tracker:** GitHub (prefix: IW)"* ]]
    [[ "$output" == *"### Forge: GitHub"* ]]
    [[ "$output" == *'`gh`'* ]]
    [[ "$output" == *"Never use \`glab\`"* ]]
}

@test "project-context outputs GitLab context with GITLAB_HOST" {
    # Setup: create GitLab config with remote
    mkdir -p .iw
    cat > .iw/config.conf << 'EOF'
tracker {
  type = gitlab
  repository = "fifty-forms/posuzovani-shody"
  teamPrefix = "CPH"
}
project {
  name = posuzovani-shody
}
EOF
    git remote add origin git@gitlab.com:fifty-forms/posuzovani-shody.git

    # Run command
    run "$PROJECT_ROOT/iw" project-context

    # Assert
    [ "$status" -eq 0 ]
    [[ "$output" == *"**Project:** posuzovani-shody"* ]]
    [[ "$output" == *"### Forge: GitLab"* ]]
    [[ "$output" == *'`glab`'* ]]
    [[ "$output" == *"Never use \`gh\`"* ]]
    [[ "$output" == *"GITLAB_HOST=gitlab.com"* ]]
}

@test "project-context fails without config" {
    # No .iw/config.conf created

    # Run command
    run "$PROJECT_ROOT/iw" project-context

    # Assert
    [ "$status" -ne 0 ]
    [[ "$output" == *"Configuration file not found"* ]]
}

@test "project-context works in real iw-cli repo" {
    # Run directly in the real project root
    cd "$PROJECT_ROOT"

    run "$PROJECT_ROOT/iw" project-context

    [ "$status" -eq 0 ]
    [[ "$output" == *"**Project:** iw-cli"* ]]
    [[ "$output" == *"GitHub"* ]]
    [[ "$output" == *"iterative-works/iw-cli"* ]]
}
