#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw feedback command with GitLab tracker
# PURPOSE: Tests feedback submission to GitLab, error handling, and label fallback

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

# ========== Happy Path Tests ==========

@test "GitLab: feedback creates bug issue with bug label" {
    # Setup: create GitLab config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "owner/project"
  teamPrefix = "PROJ"
}

project {
  name = test-project
}
EOF

    # Mock glab command that returns success
    mkdir -p bin
    cat > bin/glab <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to gitlab.com"
    exit 0
elif [[ "$1" == "issue" && "$2" == "create" ]]; then
    # Verify bug label is passed
    if [[ "$*" == *"--label"*"bug"* ]]; then
        echo "https://gitlab.com/owner/project/-/issues/123"
        exit 0
    fi
    echo "Error: expected bug label" >&2
    exit 1
fi
exit 1
SCRIPT
    chmod +x bin/glab
    export PATH="$TEST_DIR/bin:$PATH"

    # Run feedback command with bug type
    run "$PROJECT_ROOT/iw" feedback "Test bug" --type bug

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Feedback submitted successfully"* ]]
    [[ "$output" == *"Issue: #123"* ]]
}

@test "GitLab: feedback creates feature request with feature label" {
    # Setup: create GitLab config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "owner/project"
  teamPrefix = "PROJ"
}

project {
  name = test-project
}
EOF

    # Mock glab command that returns success
    mkdir -p bin
    cat > bin/glab <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to gitlab.com"
    exit 0
elif [[ "$1" == "issue" && "$2" == "create" ]]; then
    # Verify feature label is passed
    if [[ "$*" == *"--label"*"feature"* ]]; then
        echo "https://gitlab.com/owner/project/-/issues/456"
        exit 0
    fi
    echo "Error: expected feature label" >&2
    exit 1
fi
exit 1
SCRIPT
    chmod +x bin/glab
    export PATH="$TEST_DIR/bin:$PATH"

    # Run feedback command with feature type
    run "$PROJECT_ROOT/iw" feedback "Test feature" --type feature

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Feedback submitted successfully"* ]]
    [[ "$output" == *"Issue: #456"* ]]
}

@test "GitLab: feedback returns issue URL" {
    # Setup: create GitLab config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "owner/project"
  teamPrefix = "PROJ"
}

project {
  name = test-project
}
EOF

    # Mock glab command
    mkdir -p bin
    cat > bin/glab <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to gitlab.com"
    exit 0
elif [[ "$1" == "issue" && "$2" == "create" ]]; then
    echo "https://gitlab.com/owner/project/-/issues/789"
    exit 0
fi
exit 1
SCRIPT
    chmod +x bin/glab
    export PATH="$TEST_DIR/bin:$PATH"

    # Run feedback command
    run "$PROJECT_ROOT/iw" feedback "Test issue"

    # Assert URL is displayed
    [ "$status" -eq 0 ]
    [[ "$output" == *"URL: https://gitlab.com/owner/project/-/issues/789"* ]]
}

@test "GitLab: feedback with description creates issue" {
    # Setup: create GitLab config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "owner/project"
  teamPrefix = "PROJ"
}

project {
  name = test-project
}
EOF

    # Mock glab command that verifies description
    mkdir -p bin
    cat > bin/glab <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to gitlab.com"
    exit 0
elif [[ "$1" == "issue" && "$2" == "create" ]]; then
    # Verify description is passed
    if [[ "$*" == *"--description"* ]]; then
        echo "https://gitlab.com/owner/project/-/issues/111"
        exit 0
    fi
    echo "Error: expected description flag" >&2
    exit 1
fi
exit 1
SCRIPT
    chmod +x bin/glab
    export PATH="$TEST_DIR/bin:$PATH"

    # Run feedback command with description
    run "$PROJECT_ROOT/iw" feedback "Test issue" --description "Detailed description here"

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Feedback submitted successfully"* ]]
}

@test "GitLab: feedback works with self-hosted GitLab baseUrl" {
    # Setup: create GitLab config with custom baseUrl
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "team/project"
  teamPrefix = "PROJ"
  baseUrl = "https://gitlab.company.com"
}

project {
  name = test-project
}
EOF

    # Mock glab command
    mkdir -p bin
    cat > bin/glab <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to gitlab.company.com"
    exit 0
elif [[ "$1" == "issue" && "$2" == "create" ]]; then
    echo "https://gitlab.company.com/team/project/-/issues/999"
    exit 0
fi
exit 1
SCRIPT
    chmod +x bin/glab
    export PATH="$TEST_DIR/bin:$PATH"

    # Run feedback command
    run "$PROJECT_ROOT/iw" feedback "Self-hosted test"

    # Assert success with self-hosted URL
    [ "$status" -eq 0 ]
    [[ "$output" == *"Feedback submitted successfully"* ]]
    [[ "$output" == *"https://gitlab.company.com"* ]]
}

# ========== Error Scenario Tests ==========

@test "GitLab: feedback fails when glab not installed" {
    # Setup: create GitLab config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "owner/project"
  teamPrefix = "PROJ"
}

project {
  name = test-project
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

    # Run feedback command
    run "$PROJECT_ROOT/iw" feedback "Test issue"

    # Assert failure with helpful message
    [ "$status" -ne 0 ]
    [[ "$output" == *"glab CLI is not installed"* ]]
    [[ "$output" == *"https://gitlab.com/gitlab-org/cli"* ]]
    [[ "$output" == *"glab auth login"* ]]
}

@test "GitLab: feedback fails when glab not authenticated" {
    # Setup: create GitLab config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "owner/project"
  teamPrefix = "PROJ"
}

project {
  name = test-project
}
EOF

    # Mock glab command that returns auth error
    mkdir -p bin
    cat > bin/glab <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "You are not logged in to any GitLab hosts." >&2
    exit 1
fi
# Should not reach here
echo "Unexpected: should not execute issue command when not authenticated" >&2
exit 1
SCRIPT
    chmod +x bin/glab
    export PATH="$TEST_DIR/bin:$PATH"

    # Run feedback command
    run "$PROJECT_ROOT/iw" feedback "Test issue"

    # Assert failure with auth instructions
    [ "$status" -ne 0 ]
    [[ "$output" == *"glab is not authenticated"* ]]
    [[ "$output" == *"glab auth login"* ]]
}

@test "GitLab: feedback without title fails" {
    # Setup: create GitLab config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "owner/project"
  teamPrefix = "PROJ"
}

project {
  name = test-project
}
EOF

    # Mock glab command - won't be called, but just in case
    mkdir -p bin
    cat > bin/glab <<'SCRIPT'
#!/bin/bash
echo "Should not be called without title" >&2
exit 1
SCRIPT
    chmod +x bin/glab
    export PATH="$TEST_DIR/bin:$PATH"

    # Run with only --description flag
    run "$PROJECT_ROOT/iw" feedback --description "Only description"

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Title is required"* ]]
}

@test "GitLab: feedback with invalid type fails" {
    # Setup: create GitLab config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "owner/project"
  teamPrefix = "PROJ"
}

project {
  name = test-project
}
EOF

    # Mock glab command - won't be called for invalid type
    mkdir -p bin
    cat > bin/glab <<'SCRIPT'
#!/bin/bash
echo "Should not be called for invalid type" >&2
exit 1
SCRIPT
    chmod +x bin/glab
    export PATH="$TEST_DIR/bin:$PATH"

    # Run with invalid --type value
    run "$PROJECT_ROOT/iw" feedback "Test issue" --type invalid

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Type must be"* ]]
}

@test "GitLab: feedback shows error when glab command fails" {
    # Setup: create GitLab config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "owner/project"
  teamPrefix = "PROJ"
}

project {
  name = test-project
}
EOF

    # Mock glab command that returns error
    mkdir -p bin
    cat > bin/glab <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to gitlab.com"
    exit 0
elif [[ "$1" == "issue" && "$2" == "create" ]]; then
    echo "glab: some error occurred" >&2
    exit 1
fi
exit 1
SCRIPT
    chmod +x bin/glab
    export PATH="$TEST_DIR/bin:$PATH"

    # Run feedback command
    run "$PROJECT_ROOT/iw" feedback "Test issue"

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Failed to create issue"* ]] || [[ "$output" == *"some error"* ]]
}

# ========== Label Fallback Tests ==========

@test "GitLab: feedback retries without label when label not found" {
    # Setup: create GitLab config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "owner/project"
  teamPrefix = "PROJ"
}

project {
  name = test-project
}
EOF

    # Mock glab command that fails on first attempt, succeeds on second
    mkdir -p bin
    cat > bin/glab <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to gitlab.com"
    exit 0
elif [[ "$1" == "issue" && "$2" == "create" ]]; then
    # First attempt with label fails
    if [[ "$*" == *"--label"* ]]; then
        echo "Error: label 'bug' not found in project" >&2
        exit 1
    else
        # Second attempt without label succeeds
        echo "https://gitlab.com/owner/project/-/issues/222"
        exit 0
    fi
fi
exit 1
SCRIPT
    chmod +x bin/glab
    export PATH="$TEST_DIR/bin:$PATH"

    # Run feedback command (will try with label, then retry without)
    run "$PROJECT_ROOT/iw" feedback "Test bug" --type bug

    # Assert success (after retry)
    [ "$status" -eq 0 ]
    [[ "$output" == *"Feedback submitted successfully"* ]]
    [[ "$output" == *"Issue: #222"* ]]
}

@test "GitLab: feedback does not retry on non-label error" {
    # Setup: create GitLab config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "owner/project"
  teamPrefix = "PROJ"
}

project {
  name = test-project
}
EOF

    # Mock glab command that returns network error
    mkdir -p bin
    cat > bin/glab <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to gitlab.com"
    exit 0
elif [[ "$1" == "issue" && "$2" == "create" ]]; then
    echo "Network timeout" >&2
    exit 1
fi
exit 1
SCRIPT
    chmod +x bin/glab
    export PATH="$TEST_DIR/bin:$PATH"

    # Run feedback command
    run "$PROJECT_ROOT/iw" feedback "Test issue"

    # Assert failure without retry
    [ "$status" -eq 1 ]
    [[ "$output" == *"Failed to create issue"* ]] || [[ "$output" == *"Network timeout"* ]]
}

# ========== Integration Tests ==========

@test "GitLab: feedback passes title and description correctly to glab" {
    # Setup: create GitLab config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "owner/project"
  teamPrefix = "PROJ"
}

project {
  name = test-project
}
EOF

    # Mock glab command that captures arguments
    mkdir -p bin
    cat > bin/glab <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to gitlab.com"
    exit 0
elif [[ "$1" == "issue" && "$2" == "create" ]]; then
    # Verify title and description are passed correctly
    if [[ "$*" == *"--title"*"My test title"* ]] && [[ "$*" == *"--description"*"My description"* ]]; then
        echo "https://gitlab.com/owner/project/-/issues/333"
        exit 0
    fi
    echo "Error: expected title and description" >&2
    exit 1
fi
exit 1
SCRIPT
    chmod +x bin/glab
    export PATH="$TEST_DIR/bin:$PATH"

    # Run feedback command with specific title and description
    run "$PROJECT_ROOT/iw" feedback "My test title" --description "My description"

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Feedback submitted successfully"* ]]
}

@test "GitLab: feedback works with nested groups repository" {
    # Setup: create GitLab config with nested groups
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "company/team/project"
  teamPrefix = "PROJ"
}

project {
  name = test-project
}
EOF

    # Mock glab command that verifies repository
    mkdir -p bin
    cat > bin/glab <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to gitlab.com"
    exit 0
elif [[ "$*" == *"--repo company/team/project"* ]]; then
    echo "https://gitlab.com/company/team/project/-/issues/444"
    exit 0
fi
echo "Error: wrong repository" >&2
exit 1
SCRIPT
    chmod +x bin/glab
    export PATH="$TEST_DIR/bin:$PATH"

    # Run feedback command
    run "$PROJECT_ROOT/iw" feedback "Nested group issue"

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Feedback submitted successfully"* ]]
}

# ========== Regression Tests ==========

@test "GitLab: feedback does not affect GitHub/Linear/YouTrack functionality" {
    # This test verifies that GitLab feedback doesn't break other trackers
    # by creating a GitHub config and ensuring feedback still works

    # Setup: create GitHub config (not GitLab)
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = github
  repository = "iterative-works/iw-cli"
  teamPrefix = "IWCLI"
}

project {
  name = test-project
}
EOF

    # Mock gh command (not glab) - feedback should use GitHub, not GitLab
    mkdir -p bin
    cat > bin/gh <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to github.com"
    exit 0
elif [[ "$1" == "issue" && "$2" == "create" ]]; then
    echo "https://github.com/iterative-works/iw-cli/issues/100"
    exit 0
fi
exit 1
SCRIPT
    chmod +x bin/gh
    export PATH="$TEST_DIR/bin:$PATH"

    # Run feedback command - should use GitHub, not GitLab
    run "$PROJECT_ROOT/iw" feedback "GitHub feedback test"

    # Assert success with GitHub (proves GitLab doesn't interfere)
    [ "$status" -eq 0 ]
    [[ "$output" == *"Feedback submitted successfully"* ]]
    [[ "$output" == *"github.com"* ]]
}
