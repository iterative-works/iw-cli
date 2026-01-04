#!/usr/bin/env bats
# PURPOSE: Integration tests for iw init command
# PURPOSE: Tests non-interactive mode with --tracker and --team flags

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

setup() {
    # Create a temporary directory for each test
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"
}

teardown() {
    # Clean up temporary directory
    rm -rf "$TEST_DIR"
}

@test "init creates config with --tracker and --team flags" {
    # Setup: create a git repo
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"

    # Run init with non-interactive flags
    run "$PROJECT_ROOT/iw" init --tracker=linear --team=IWLE

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Configuration created"* ]]

    # Assert config file exists and has correct content
    [ -f ".iw/config.conf" ]
    grep -q "type = linear" .iw/config.conf
    grep -q "team = IWLE" .iw/config.conf
}

@test "init creates config with youtrack tracker" {
    # Setup: create a git repo
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"

    # Run init with youtrack tracker
    run "$PROJECT_ROOT/iw" init --tracker=youtrack --team=TEST

    # Assert success
    [ "$status" -eq 0 ]

    # Assert config has youtrack
    grep -q "type = youtrack" .iw/config.conf
    grep -q "team = TEST" .iw/config.conf
}

@test "init fails outside git repository" {
    # No git init - just a plain directory

    # Run init
    run "$PROJECT_ROOT/iw" init --tracker=linear --team=IWLE

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Not in a git repository"* ]]

    # Assert no config created
    [ ! -f ".iw/config.conf" ]
}

@test "init fails when config already exists" {
    # Setup: create a git repo with existing config
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    mkdir -p .iw
    echo "existing config" > .iw/config.conf

    # Run init without --force
    run "$PROJECT_ROOT/iw" init --tracker=linear --team=IWLE

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Configuration already exists"* ]]
    [[ "$output" == *"--force"* ]]

    # Assert config was not overwritten
    grep -q "existing config" .iw/config.conf
}

@test "init --force overwrites existing config" {
    # Setup: create a git repo with existing config
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    mkdir -p .iw
    echo "old config" > .iw/config.conf

    # Run init with --force
    run "$PROJECT_ROOT/iw" init --force --tracker=linear --team=NEWTEAM

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Configuration created"* ]]

    # Assert config was overwritten with new content
    grep -q "type = linear" .iw/config.conf
    grep -q "team = NEWTEAM" .iw/config.conf
    ! grep -q "old config" .iw/config.conf
}

@test "init fails with invalid tracker type" {
    # Setup: create a git repo
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"

    # Run init with invalid tracker
    run "$PROJECT_ROOT/iw" init --tracker=invalid --team=IWLE

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid tracker type"* ]]
    [[ "$output" == *"linear"* ]]
    [[ "$output" == *"youtrack"* ]]

    # Assert no config created
    [ ! -f ".iw/config.conf" ]
}

@test "init shows LINEAR_API_TOKEN hint for linear tracker" {
    # Setup: create a git repo
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"

    # Run init with linear tracker
    run "$PROJECT_ROOT/iw" init --tracker=linear --team=IWLE

    # Assert output contains Linear token hint
    [ "$status" -eq 0 ]
    [[ "$output" == *"LINEAR_API_TOKEN"* ]]
}

@test "init shows YOUTRACK_API_TOKEN hint for youtrack tracker" {
    # Setup: create a git repo
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"

    # Run init with youtrack tracker
    run "$PROJECT_ROOT/iw" init --tracker=youtrack --team=TEST

    # Assert output contains YouTrack token hint
    [ "$status" -eq 0 ]
    [[ "$output" == *"YOUTRACK_API_TOKEN"* ]]
}

@test "init uses directory name as project name" {
    # Setup: create a git repo in a directory with known name
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"

    # Run init
    run "$PROJECT_ROOT/iw" init --tracker=linear --team=IWLE

    # Assert config has project name (will be the temp dir basename)
    [ "$status" -eq 0 ]
    [ -f ".iw/config.conf" ]
    grep -q "name = " .iw/config.conf
}

@test "init creates config with github tracker and HTTPS remote" {
    # Setup: create a git repo with GitHub HTTPS remote
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin https://github.com/iterative-works/iw-cli.git

    # Run init with github tracker and team prefix
    run "$PROJECT_ROOT/iw" init --tracker=github --team-prefix=IWCLI

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Configuration created"* ]]
    [[ "$output" == *"Auto-detected repository: iterative-works/iw-cli"* ]]

    # Assert config file exists and has correct content
    [ -f ".iw/config.conf" ]
    grep -q "type = github" .iw/config.conf
    grep -q 'repository = "iterative-works/iw-cli"' .iw/config.conf
    grep -q 'teamPrefix = "IWCLI"' .iw/config.conf
    ! grep -q "team = " .iw/config.conf
}

@test "init creates config with github tracker and SSH remote" {
    # Setup: create a git repo with GitHub SSH remote
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin git@github.com:iterative-works/iw-cli.git

    # Run init with github tracker and team prefix
    run "$PROJECT_ROOT/iw" init --tracker=github --team-prefix=IWCLI

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Auto-detected repository: iterative-works/iw-cli"* ]]

    # Assert config file has correct content
    [ -f ".iw/config.conf" ]
    grep -q "type = github" .iw/config.conf
    grep -q 'repository = "iterative-works/iw-cli"' .iw/config.conf
    grep -q 'teamPrefix = "IWCLI"' .iw/config.conf
}

@test "init shows gh CLI hint for github tracker" {
    # Setup: create a git repo with GitHub remote
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin https://github.com/iterative-works/iw-cli.git

    # Run init with github tracker and team prefix
    run "$PROJECT_ROOT/iw" init --tracker=github --team-prefix=IWCLI

    # Assert output contains gh CLI hint (not API token)
    [ "$status" -eq 0 ]
    [[ "$output" == *"gh auth login"* ]]
    [[ "$output" != *"API token"* ]]
}

@test "init with github validates invalid tracker in error message" {
    # Setup: create a git repo
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"

    # Run init with invalid tracker
    run "$PROJECT_ROOT/iw" init --tracker=invalid --team=IWLE

    # Assert failure and error message includes github
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid tracker type"* ]]
    [[ "$output" == *"linear"* ]]
    [[ "$output" == *"youtrack"* ]]
    [[ "$output" == *"github"* ]]
}

@test "init with linear still works (regression test)" {
    # Setup: create a git repo
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"

    # Run init with linear tracker
    run "$PROJECT_ROOT/iw" init --tracker=linear --team=IWLE

    # Assert success
    [ "$status" -eq 0 ]
    grep -q "type = linear" .iw/config.conf
    grep -q "team = IWLE" .iw/config.conf
}

@test "init with youtrack still works (regression test)" {
    # Setup: create a git repo
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"

    # Run init with youtrack tracker
    run "$PROJECT_ROOT/iw" init --tracker=youtrack --team=TEST

    # Assert success
    [ "$status" -eq 0 ]
    grep -q "type = youtrack" .iw/config.conf
    grep -q "team = TEST" .iw/config.conf
}

@test "init with github shows warning for non-GitHub remote" {
    # Setup: create a git repo with a non-GitHub remote
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin https://gitlab.com/user/project.git

    # Run init with github tracker - this will warn and prompt for manual input
    # We use timeout to abort the interactive prompt after the warning is shown
    run timeout 2s "$PROJECT_ROOT/iw" init --tracker=github || true

    # Assert warning about non-GitHub remote appears before the prompt
    [[ "$output" == *"Could not auto-detect repository"* ]] || [[ "$output" == *"Not a GitHub URL"* ]]
}

@test "init with github and multiple remotes uses origin" {
    # Setup: create a git repo with multiple remotes (origin and upstream)
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin https://github.com/iterative-works/iw-cli.git
    git remote add upstream https://github.com/otheruser/iw-cli.git

    # Run init with github tracker and team prefix
    run "$PROJECT_ROOT/iw" init --tracker=github --team-prefix=IWCLI

    # Assert success and that origin was used (not upstream)
    [ "$status" -eq 0 ]
    [[ "$output" == *"Auto-detected repository: iterative-works/iw-cli"* ]]

    # Assert config file has repository from origin
    [ -f ".iw/config.conf" ]
    grep -q 'repository = "iterative-works/iw-cli"' .iw/config.conf
    grep -q 'teamPrefix = "IWCLI"' .iw/config.conf
}

@test "init with github and no remote shows error" {
    # Setup: create a git repo without any remote
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    # No git remote add - repo has no remotes

    # Run init with github tracker - this will fail to auto-detect and prompt
    # We use timeout to abort the interactive prompt
    run timeout 2s "$PROJECT_ROOT/iw" init --tracker=github || true

    # Assert that auto-detection failed (should show warning or prompt)
    [[ "$output" == *"Could not auto-detect repository"* ]] || [[ "$output" == *"GitHub repository"* ]]
}

@test "init with github and HTTPS URL with trailing slash" {
    # Setup: create a git repo with GitHub HTTPS remote with trailing slash
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin https://github.com/iterative-works/iw-cli/

    # Run init with github tracker and team prefix
    run "$PROJECT_ROOT/iw" init --tracker=github --team-prefix=IWCLI

    # Assert success - trailing slash should be handled correctly
    [ "$status" -eq 0 ]
    [[ "$output" == *"Auto-detected repository: iterative-works/iw-cli"* ]]

    # Assert config file has correct repository (without trailing slash)
    [ -f ".iw/config.conf" ]
    grep -q 'repository = "iterative-works/iw-cli"' .iw/config.conf
    grep -q 'teamPrefix = "IWCLI"' .iw/config.conf
}

@test "init with github and HTTPS URL with username prefix" {
    # Setup: create a git repo with GitHub HTTPS remote with username prefix
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin https://testuser@github.com/iterative-works/iw-cli.git

    # Run init with github tracker and team prefix
    run "$PROJECT_ROOT/iw" init --tracker=github --team-prefix=IWCLI

    # Assert success - username prefix should be handled correctly
    [ "$status" -eq 0 ]
    [[ "$output" == *"Auto-detected repository: iterative-works/iw-cli"* ]]

    # Assert config file has correct repository
    [ -f ".iw/config.conf" ]
    grep -q 'repository = "iterative-works/iw-cli"' .iw/config.conf
    grep -q 'teamPrefix = "IWCLI"' .iw/config.conf
}

# NOTE: Interactive test for "init --tracker=github without any git remote"
# requires manual verification or stdin mocking, which is not supported in this test suite.
# The scenario is covered by unit tests for GitRemote.repositoryOwnerAndName.

# ========== Team Prefix Tests for GitHub ==========

@test "init with github and --team-prefix creates config with team prefix" {
    # Setup: create a git repo with GitHub remote
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin https://github.com/iterative-works/iw-cli.git

    # Run init with github tracker and team prefix
    run "$PROJECT_ROOT/iw" init --tracker=github --team-prefix=IWCLI

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Configuration created"* ]]

    # Assert config file has team prefix
    [ -f ".iw/config.conf" ]
    grep -q "type = github" .iw/config.conf
    grep -q 'repository = "iterative-works/iw-cli"' .iw/config.conf
    grep -q 'teamPrefix = "IWCLI"' .iw/config.conf
}

@test "init with github validates team prefix format" {
    # Setup: create a git repo with GitHub remote
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin https://github.com/iterative-works/iw-cli.git

    # Run init with invalid team prefix (lowercase)
    run "$PROJECT_ROOT/iw" init --tracker=github --team-prefix=iwcli

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid team prefix"* ]]
    [[ "$output" == *"uppercase"* ]]

    # Assert no config created
    [ ! -f ".iw/config.conf" ]
}

@test "init with github validates team prefix length - too short" {
    # Setup: create a git repo with GitHub remote
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin https://github.com/iterative-works/iw-cli.git

    # Run init with too short team prefix
    run "$PROJECT_ROOT/iw" init --tracker=github --team-prefix=I

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid team prefix"* ]]
    [[ "$output" == *"2-10 characters"* ]]

    # Assert no config created
    [ ! -f ".iw/config.conf" ]
}

@test "init with github validates team prefix length - too long" {
    # Setup: create a git repo with GitHub remote
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin https://github.com/iterative-works/iw-cli.git

    # Run init with too long team prefix
    run "$PROJECT_ROOT/iw" init --tracker=github --team-prefix=VERYLONGPREFIX

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid team prefix"* ]]
    [[ "$output" == *"2-10 characters"* ]]

    # Assert no config created
    [ ! -f ".iw/config.conf" ]
}

@test "init with github rejects team prefix with numbers" {
    # Setup: create a git repo with GitHub remote
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin https://github.com/iterative-works/iw-cli.git

    # Run init with team prefix containing numbers
    run "$PROJECT_ROOT/iw" init --tracker=github --team-prefix=IW2CLI

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid team prefix"* ]]
    [[ "$output" == *"uppercase"* ]]

    # Assert no config created
    [ ! -f ".iw/config.conf" ]
}

@test "init with github and valid short team prefix" {
    # Setup: create a git repo with GitHub remote
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin https://github.com/iterative-works/iw-cli.git

    # Run init with valid short team prefix
    run "$PROJECT_ROOT/iw" init --tracker=github --team-prefix=IW

    # Assert success
    [ "$status" -eq 0 ]

    # Assert config file has team prefix
    [ -f ".iw/config.conf" ]
    grep -q 'teamPrefix = "IW"' .iw/config.conf
}

@test "init with github and valid long team prefix" {
    # Setup: create a git repo with GitHub remote
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin https://github.com/iterative-works/iw-cli.git

    # Run init with valid long team prefix (10 chars)
    run "$PROJECT_ROOT/iw" init --tracker=github --team-prefix=VERYLONGPR

    # Assert success
    [ "$status" -eq 0 ]

    # Assert config file has team prefix
    [ -f ".iw/config.conf" ]
    grep -q 'teamPrefix = "VERYLONGPR"' .iw/config.conf
}

# ========== GitLab Tracker Tests ==========

@test "init creates config with gitlab tracker and HTTPS remote" {
    # Setup: create a git repo with GitLab HTTPS remote
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin https://gitlab.com/owner/project.git

    # Run init with gitlab tracker and team prefix
    run "$PROJECT_ROOT/iw" init --tracker=gitlab --team-prefix=PROJ

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Configuration created"* ]]
    [[ "$output" == *"Auto-detected repository: owner/project"* ]]

    # Assert config file exists and has correct content
    [ -f ".iw/config.conf" ]
    grep -q "type = gitlab" .iw/config.conf
    grep -q 'repository = "owner/project"' .iw/config.conf
    grep -q 'teamPrefix = "PROJ"' .iw/config.conf
    ! grep -q "team = " .iw/config.conf
}

@test "init creates config with gitlab tracker and SSH remote" {
    # Setup: create a git repo with GitLab SSH remote
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin git@gitlab.com:owner/project.git

    # Run init with gitlab tracker and team prefix
    run "$PROJECT_ROOT/iw" init --tracker=gitlab --team-prefix=PROJ

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Auto-detected repository: owner/project"* ]]

    # Assert config file has correct content
    [ -f ".iw/config.conf" ]
    grep -q "type = gitlab" .iw/config.conf
    grep -q 'repository = "owner/project"' .iw/config.conf
    grep -q 'teamPrefix = "PROJ"' .iw/config.conf
}

@test "init shows glab CLI hint for gitlab tracker" {
    # Setup: create a git repo with GitLab remote
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin https://gitlab.com/owner/project.git

    # Run init with gitlab tracker and team prefix
    run "$PROJECT_ROOT/iw" init --tracker=gitlab --team-prefix=PROJ

    # Assert output contains glab CLI hint (not API token)
    [ "$status" -eq 0 ]
    [[ "$output" == *"glab auth login"* ]]
    [[ "$output" != *"API token"* ]]
}

@test "init with gitlab validates tracker type in error message" {
    # Setup: create a git repo
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"

    # Run init with invalid tracker
    run "$PROJECT_ROOT/iw" init --tracker=invalid --team=IWLE

    # Assert failure and error message includes gitlab
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid tracker type"* ]]
    [[ "$output" == *"gitlab"* ]]
}

@test "init with gitlab and nested group repository" {
    # Setup: create a git repo with GitLab HTTPS remote with nested groups
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin https://gitlab.com/group/subgroup/project.git

    # Run init with gitlab tracker and team prefix
    run "$PROJECT_ROOT/iw" init --tracker=gitlab --team-prefix=PROJ

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Auto-detected repository: group/subgroup/project"* ]]

    # Assert config file has nested group path
    [ -f ".iw/config.conf" ]
    grep -q 'repository = "group/subgroup/project"' .iw/config.conf
}

@test "init with gitlab and self-hosted instance with --base-url" {
    # Setup: create a git repo with self-hosted GitLab remote
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin https://gitlab.company.com/team/project.git

    # Run init with gitlab tracker, team prefix, and base URL
    run "$PROJECT_ROOT/iw" init --tracker=gitlab --team-prefix=PROJ --base-url=https://gitlab.company.com

    # Assert success
    [ "$status" -eq 0 ]

    # Assert config file has baseUrl
    [ -f ".iw/config.conf" ]
    grep -q "type = gitlab" .iw/config.conf
    grep -q 'repository = "team/project"' .iw/config.conf
    grep -q 'baseUrl = "https://gitlab.company.com"' .iw/config.conf
}

@test "init with gitlab validates team prefix format" {
    # Setup: create a git repo with GitLab remote
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin https://gitlab.com/owner/project.git

    # Run init with invalid team prefix (lowercase)
    run "$PROJECT_ROOT/iw" init --tracker=gitlab --team-prefix=proj

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid team prefix"* ]]
    [[ "$output" == *"uppercase"* ]]

    # Assert no config created
    [ ! -f ".iw/config.conf" ]
}

@test "init with gitlab shows warning for non-GitLab remote" {
    # Setup: create a git repo with a non-GitLab remote
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin https://github.com/user/project.git

    # Run init with gitlab tracker - this will warn and prompt for manual input
    # We use timeout to abort the interactive prompt after the warning is shown
    run timeout 2s "$PROJECT_ROOT/iw" init --tracker=gitlab || true

    # Assert warning about non-GitLab remote appears before the prompt
    [[ "$output" == *"Could not auto-detect repository"* ]] || [[ "$output" == *"Not a GitLab URL"* ]]
}

@test "init with gitlab and multiple remotes uses origin" {
    # Setup: create a git repo with multiple remotes (origin and upstream)
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin https://gitlab.com/owner/project.git
    git remote add upstream https://gitlab.com/otheruser/project.git

    # Run init with gitlab tracker and team prefix
    run "$PROJECT_ROOT/iw" init --tracker=gitlab --team-prefix=PROJ

    # Assert success and that origin was used (not upstream)
    [ "$status" -eq 0 ]
    [[ "$output" == *"Auto-detected repository: owner/project"* ]]

    # Assert config file has repository from origin
    [ -f ".iw/config.conf" ]
    grep -q 'repository = "owner/project"' .iw/config.conf
}

@test "init with gitlab and HTTPS URL with trailing slash" {
    # Setup: create a git repo with GitLab HTTPS remote with trailing slash
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin https://gitlab.com/owner/project/

    # Run init with gitlab tracker and team prefix
    run "$PROJECT_ROOT/iw" init --tracker=gitlab --team-prefix=PROJ

    # Assert success - trailing slash should be handled correctly
    [ "$status" -eq 0 ]
    [[ "$output" == *"Auto-detected repository: owner/project"* ]]

    # Assert config file has correct repository (without trailing slash)
    [ -f ".iw/config.conf" ]
    grep -q 'repository = "owner/project"' .iw/config.conf
}

@test "init with gitlab still works (regression test)" {
    # Setup: create a git repo with GitLab remote
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin https://gitlab.com/owner/project.git

    # Run init with gitlab tracker
    run "$PROJECT_ROOT/iw" init --tracker=gitlab --team-prefix=PROJ

    # Assert success
    [ "$status" -eq 0 ]
    grep -q "type = gitlab" .iw/config.conf
    grep -q 'repository = "owner/project"' .iw/config.conf
    grep -q 'teamPrefix = "PROJ"' .iw/config.conf
}
