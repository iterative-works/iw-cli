#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw issue create command
# PURPOSE: Tests help display, argument validation, and basic command structure

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

setup() {
    # Disable dashboard server communication during tests
    export IW_SERVER_DISABLED=1

    # Create a temporary directory for each test
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"
}

teardown() {
    # Clean up temporary directory
    rm -rf "$TEST_DIR"
}

@test "issue create without arguments shows help and exits 1" {
    # Run with no arguments
    run "$PROJECT_ROOT/iw" issue create

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Usage:"* ]]
}

@test "issue create --help shows usage and exits 0" {
    # Run with --help flag
    run "$PROJECT_ROOT/iw" issue create --help

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Usage:"* ]]
}

@test "issue create -h shows usage and exits 0" {
    # Run with -h short flag
    run "$PROJECT_ROOT/iw" issue create -h

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Usage:"* ]]
}

@test "issue create help text contains --title flag" {
    # Run with --help to get help text
    run "$PROJECT_ROOT/iw" issue create --help

    # Assert help contains --title
    [ "$status" -eq 0 ]
    [[ "$output" == *"--title"* ]]
}

@test "issue create help text contains --description flag" {
    # Run with --help to get help text
    run "$PROJECT_ROOT/iw" issue create --help

    # Assert help contains --description
    [ "$status" -eq 0 ]
    [[ "$output" == *"--description"* ]]
}

@test "issue create help text contains usage examples" {
    # Run with --help to get help text
    run "$PROJECT_ROOT/iw" issue create --help

    # Assert help contains Examples section
    [ "$status" -eq 0 ]
    [[ "$output" == *"Examples:"* ]]
}

# ========== Phase 2: GitHub Issue Creation Tests ==========

@test "issue create with title and description succeeds" {
    # Create a GitHub config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = github
  repository = "iterative-works/test-project"
  teamPrefix = TEST
}
project {
  name = test-project
}
EOF

    # Mock gh command that returns success - outputs URL like real gh
    mkdir -p bin
    cat > bin/gh <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to github.com"
    exit 0
fi
echo "https://github.com/iterative-works/test-project/issues/123"
exit 0
SCRIPT
    chmod +x bin/gh
    export PATH="$TEST_DIR/bin:$PATH"

    # Run issue create command
    run "$PROJECT_ROOT/iw" issue create --title "Test issue" --description "Test body"

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Issue created: #123"* ]]
    [[ "$output" == *"URL: https://github.com/iterative-works/test-project/issues/123"* ]]
}

@test "issue create with title only succeeds" {
    # Create a GitHub config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = github
  repository = "iterative-works/test-project"
  teamPrefix = TEST
}
project {
  name = test-project
}
EOF

    # Mock gh command that returns success
    mkdir -p bin
    cat > bin/gh <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to github.com"
    exit 0
fi
echo "https://github.com/iterative-works/test-project/issues/456"
exit 0
SCRIPT
    chmod +x bin/gh
    export PATH="$TEST_DIR/bin:$PATH"

    # Run issue create command without description
    run "$PROJECT_ROOT/iw" issue create --title "Test without description"

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Issue created: #456"* ]]
    [[ "$output" == *"URL: https://github.com/iterative-works/test-project/issues/456"* ]]
}

@test "issue create without title shows help and exits 1" {
    # Create a GitHub config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = github
  repository = "iterative-works/test-project"
  teamPrefix = TEST
}
project {
  name = test-project
}
EOF

    # Mock gh command - should not be called
    mkdir -p bin
    cat > bin/gh <<'SCRIPT'
#!/bin/bash
echo "Should not be called without title" >&2
exit 1
SCRIPT
    chmod +x bin/gh
    export PATH="$TEST_DIR/bin:$PATH"

    # Run with only --description flag
    run "$PROJECT_ROOT/iw" issue create --description "Only description"

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Usage:"* ]]
}

@test "issue create for YouTrack tracker requires YOUTRACK_API_TOKEN" {
    # Create a YouTrack config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = youtrack
  team = TEST
  baseUrl = "https://example.youtrack.cloud"
}
project {
  name = test-project
}
EOF

    # Ensure YOUTRACK_API_TOKEN is not set
    unset YOUTRACK_API_TOKEN

    # Run issue create command
    run "$PROJECT_ROOT/iw" issue create --title "Test issue"

    # Assert failure with token error
    [ "$status" -eq 1 ]
    [[ "$output" == *"YOUTRACK_API_TOKEN"* ]]
    [[ "$output" == *"not set"* ]]
}

# ========== Phase 3: Prerequisite Validation Tests ==========

@test "issue create fails with helpful message when gh CLI not installed" {
    # Create a GitHub config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = github
  repository = "iterative-works/test-project"
  teamPrefix = TEST
}
project {
  name = test-project
}
EOF

    # Mock which command to report gh is not found
    mkdir -p bin
    cat > bin/which <<'SCRIPT'
#!/bin/bash
# Return false for gh, true for everything else
if [[ "$1" == "gh" ]]; then
    exit 1
else
    # Use real which for other commands
    /usr/bin/which "$@"
fi
SCRIPT
    chmod +x bin/which
    export PATH="$TEST_DIR/bin:$PATH"

    # Run issue create command
    run "$PROJECT_ROOT/iw" issue create --title "Test issue"

    # Assert failure with helpful message
    [ "$status" -ne 0 ]
    [[ "$output" == *"gh CLI is not installed"* ]]
    [[ "$output" == *"https://cli.github.com/"* ]]
    [[ "$output" == *"gh auth login"* ]]
}

@test "issue create fails with auth instructions when gh not authenticated" {
    # Create a GitHub config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = github
  repository = "iterative-works/test-project"
  teamPrefix = TEST
}
project {
  name = test-project
}
EOF

    # Mock gh command that returns exit code 4 for auth status
    mkdir -p bin
    cat > bin/gh <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "You are not logged in to any GitHub hosts. Run gh auth login to authenticate." >&2
    exit 4
elif [[ "$1" == "issue" && "$2" == "create" ]]; then
    # This should not be reached since validation should fail first
    echo "Unexpected: issue create called when not authenticated" >&2
    exit 1
else
    echo "Unexpected gh command: $*" >&2
    exit 1
fi
SCRIPT
    chmod +x bin/gh
    export PATH="$TEST_DIR/bin:$PATH"

    # Run issue create command
    run "$PROJECT_ROOT/iw" issue create --title "Test issue"

    # Assert failure with auth instructions
    [ "$status" -ne 0 ]
    [[ "$output" == *"gh is not authenticated"* ]]
    [[ "$output" == *"gh auth login"* ]]
}

# ========== Phase 5: Linear Issue Creation Tests ==========

@test "issue create for Linear tracker requires LINEAR_API_TOKEN" {
    # Create a Linear config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = linear
  team = "test-team-uuid"
}
project {
  name = test-project
}
EOF

    # Ensure LINEAR_API_TOKEN is not set
    unset LINEAR_API_TOKEN

    # Run issue create command
    run "$PROJECT_ROOT/iw" issue create --title "Test issue"

    # Assert failure with token error
    [ "$status" -eq 1 ]
    [[ "$output" == *"LINEAR_API_TOKEN"* ]]
    [[ "$output" == *"not set"* ]]
}

# ========== Phase 6: GitLab Issue Creation Tests ==========

@test "issue create fails with helpful message when glab CLI not installed" {
    # Create a GitLab config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "company/platform/api-service"
  teamPrefix = API
}
project {
  name = api-service
}
EOF

    # Mock which command to report glab is not found
    mkdir -p bin
    cat > bin/which <<'SCRIPT'
#!/bin/bash
# Return false for glab, true for everything else
if [[ "$1" == "glab" ]]; then
    exit 1
else
    # Use real which for other commands
    /usr/bin/which "$@"
fi
SCRIPT
    chmod +x bin/which
    export PATH="$TEST_DIR/bin:$PATH"

    # Run issue create command
    run "$PROJECT_ROOT/iw" issue create --title "Test issue"

    # Assert failure with helpful message
    [ "$status" -ne 0 ]
    [[ "$output" == *"glab CLI is not installed"* ]]
    [[ "$output" == *"https://gitlab.com/gitlab-org/cli"* ]]
    [[ "$output" == *"glab auth login"* ]]
}

@test "issue create fails with auth instructions when glab not authenticated" {
    # Create a GitLab config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "company/platform/api-service"
  teamPrefix = API
}
project {
  name = api-service
}
EOF

    # Mock glab command that returns exit code for auth status failure
    mkdir -p bin
    cat > bin/glab <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "No accounts logged in." >&2
    exit 1
elif [[ "$1" == "issue" && "$2" == "create" ]]; then
    # This should not be reached since validation should fail first
    echo "Unexpected: issue create called when not authenticated" >&2
    exit 1
else
    echo "Unexpected glab command: $*" >&2
    exit 1
fi
SCRIPT
    chmod +x bin/glab
    export PATH="$TEST_DIR/bin:$PATH"

    # Run issue create command
    run "$PROJECT_ROOT/iw" issue create --title "Test issue"

    # Assert failure with auth instructions
    [ "$status" -ne 0 ]
    [[ "$output" == *"glab is not authenticated"* ]]
    [[ "$output" == *"glab auth login"* ]]
}

@test "issue create with GitLab tracker succeeds" {
    # Create a GitLab config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "company/platform/api-service"
  teamPrefix = API
}
project {
  name = api-service
}
EOF

    # Mock glab command that returns success
    mkdir -p bin
    cat > bin/glab <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to gitlab.com"
    exit 0
fi
echo "https://gitlab.com/company/platform/api-service/-/issues/789"
exit 0
SCRIPT
    chmod +x bin/glab
    export PATH="$TEST_DIR/bin:$PATH"

    # Run issue create command
    run "$PROJECT_ROOT/iw" issue create --title "Test GitLab issue" --description "Test body"

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Issue created: #789"* ]]
    [[ "$output" == *"URL: https://gitlab.com/company/platform/api-service/-/issues/789"* ]]
}

# ========== Phase 7: YouTrack Issue Creation Tests ==========

@test "issue create for YouTrack requires baseUrl in config" {
    # Create a YouTrack config without baseUrl
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

    # Set token so we don't fail on that first
    export YOUTRACK_API_TOKEN="test-token-12345"

    # Run issue create command
    run "$PROJECT_ROOT/iw" issue create --title "Test issue"

    # Assert failure with baseUrl error
    [ "$status" -eq 1 ]
    [[ "$output" == *"baseUrl"* ]] || [[ "$output" == *"base URL"* ]]
}
