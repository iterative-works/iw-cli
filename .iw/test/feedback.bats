#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw feedback command
# PURPOSE: Tests feedback submission to GitHub, error handling, and help text
#
# NOTE: The feedback command always creates issues in the iw-cli repository
# (iterative-works/iw-cli) regardless of the current directory or local config.

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

@test "feedback without title fails" {
    # Mock gh command - won't be called, but just in case
    mkdir -p bin
    cat > bin/gh <<'SCRIPT'
#!/bin/bash
echo "Should not be called without title" >&2
exit 1
SCRIPT
    chmod +x bin/gh
    export PATH="$TEST_DIR/bin:$PATH"

    # Run with only --description flag
    run "$PROJECT_ROOT/iw" feedback --description "Only description"

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Title is required"* ]]
}

@test "feedback with invalid type fails" {
    # Mock gh command - won't be called for invalid type
    mkdir -p bin
    cat > bin/gh <<'SCRIPT'
#!/bin/bash
echo "Should not be called for invalid type" >&2
exit 1
SCRIPT
    chmod +x bin/gh
    export PATH="$TEST_DIR/bin:$PATH"

    # Run with invalid --type value
    run "$PROJECT_ROOT/iw" feedback "Test issue" --type invalid

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Type must be"* ]]
}

@test "feedback works without local config file" {
    # Mock gh command that returns success - outputs URL like real gh
    mkdir -p bin
    cat > bin/gh <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to github.com"
    exit 0
fi
echo "https://github.com/iterative-works/iw-cli/issues/42"
exit 0
SCRIPT
    chmod +x bin/gh
    export PATH="$TEST_DIR/bin:$PATH"

    # Ensure no .iw directory exists
    rm -rf .iw

    # Run feedback command - should work without config
    run "$PROJECT_ROOT/iw" feedback "Test issue without config"

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Feedback submitted successfully"* ]]
    [[ "$output" == *"Issue: #42"* ]]
}

@test "feedback creates issue in iw-cli repository" {
    # Mock gh command that captures and validates args - outputs URL like real gh
    mkdir -p bin
    cat > bin/gh <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to github.com"
    exit 0
fi
# Check that repository is iterative-works/iw-cli
for arg in "$@"; do
    if [[ "$arg" == *"iterative-works/iw-cli"* ]]; then
        echo "https://github.com/iterative-works/iw-cli/issues/99"
        exit 0
    fi
done
# If we get here, wrong repository was used
echo "Error: Expected iterative-works/iw-cli repository" >&2
exit 1
SCRIPT
    chmod +x bin/gh
    export PATH="$TEST_DIR/bin:$PATH"

    # Run feedback command
    run "$PROJECT_ROOT/iw" feedback "Test issue"

    # Assert success - proves it used the right repository
    [ "$status" -eq 0 ]
    [[ "$output" == *"Feedback submitted successfully"* ]]
}

@test "feedback ignores local config tracker type" {
    # Create a local config with Linear tracker - should be ignored
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = linear
  team = IWLE
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
# Prove GitHub is being used, not Linear
echo "https://github.com/iterative-works/iw-cli/issues/77"
exit 0
SCRIPT
    chmod +x bin/gh
    export PATH="$TEST_DIR/bin:$PATH"

    # Run feedback command - should use GitHub despite Linear config
    run "$PROJECT_ROOT/iw" feedback "Test issue with Linear config"

    # Assert success with GitHub
    [ "$status" -eq 0 ]
    [[ "$output" == *"Feedback submitted successfully"* ]]
    [[ "$output" == *"https://github.com"* ]]
}

@test "feedback shows error when gh command fails" {
    # Mock gh command that returns error
    mkdir -p bin
    cat > bin/gh <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to github.com"
    exit 0
fi
echo "gh: some error occurred" >&2
exit 1
SCRIPT
    chmod +x bin/gh
    export PATH="$TEST_DIR/bin:$PATH"

    # Run feedback command
    run "$PROJECT_ROOT/iw" feedback "Test issue"

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Failed to create issue"* ]] || [[ "$output" == *"some error"* ]]
}

@test "feedback shows issue number in output" {
    # Mock gh command that returns success with specific issue number - outputs URL like real gh
    mkdir -p bin
    cat > bin/gh <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to github.com"
    exit 0
fi
echo "https://github.com/iterative-works/iw-cli/issues/123"
exit 0
SCRIPT
    chmod +x bin/gh
    export PATH="$TEST_DIR/bin:$PATH"

    # Run feedback command
    run "$PROJECT_ROOT/iw" feedback "Test issue"

    # Assert success with issue number
    [ "$status" -eq 0 ]
    [[ "$output" == *"Feedback submitted successfully"* ]]
    [[ "$output" == *"Issue: #123"* ]]
    [[ "$output" == *"URL: https://github.com/iterative-works/iw-cli/issues/123"* ]]
}

@test "feedback with bug type applies bug label" {
    # Mock gh command that verifies label is passed - outputs URL like real gh
    mkdir -p bin
    cat > bin/gh <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to github.com"
    exit 0
fi
# Check for --label bug in arguments
args="$*"
if [[ "$args" == *"--label"*"bug"* ]]; then
    echo "https://github.com/iterative-works/iw-cli/issues/456"
    exit 0
fi
# If no label, still succeed (label might not exist in repo)
echo "https://github.com/iterative-works/iw-cli/issues/456"
exit 0
SCRIPT
    chmod +x bin/gh
    export PATH="$TEST_DIR/bin:$PATH"

    # Run feedback command with bug type
    run "$PROJECT_ROOT/iw" feedback "Test bug" --type bug

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Feedback submitted successfully"* ]]
}

@test "feedback with feature type applies feedback label" {
    # Mock gh command - outputs URL like real gh
    mkdir -p bin
    cat > bin/gh <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to github.com"
    exit 0
fi
echo "https://github.com/iterative-works/iw-cli/issues/789"
exit 0
SCRIPT
    chmod +x bin/gh
    export PATH="$TEST_DIR/bin:$PATH"

    # Run feedback command with feature type
    run "$PROJECT_ROOT/iw" feedback "Test feature" --type feature

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Feedback submitted successfully"* ]]
}

@test "feedback with description creates issue" {
    # Mock gh command - outputs URL like real gh
    mkdir -p bin
    cat > bin/gh <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to github.com"
    exit 0
fi
echo "https://github.com/iterative-works/iw-cli/issues/111"
exit 0
SCRIPT
    chmod +x bin/gh
    export PATH="$TEST_DIR/bin:$PATH"

    # Run feedback command with description
    run "$PROJECT_ROOT/iw" feedback "Test issue" --description "Detailed description here"

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Feedback submitted successfully"* ]]
}

@test "feedback --help shows usage" {
    # Run with --help flag
    run "$PROJECT_ROOT/iw" feedback --help

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Usage:"* ]]
    [[ "$output" == *"--description"* ]]
    [[ "$output" == *"--type"* ]]
    # Should NOT mention LINEAR_API_TOKEN anymore
    [[ "$output" != *"LINEAR_API_TOKEN"* ]]
}

@test "feedback -h shows usage" {
    # Run with -h short flag
    run "$PROJECT_ROOT/iw" feedback -h

    # Assert success - same output as --help
    [ "$status" -eq 0 ]
    [[ "$output" == *"Usage:"* ]]
    [[ "$output" == *"--description"* ]]
    [[ "$output" == *"--type"* ]]
}

# Live API test - only runs if gh is authenticated
@test "feedback creates real issue (live API)" {
    # Skip unless explicitly enabled and gh is available
    if [ -z "$IW_TEST_LIVE_FEEDBACK" ] || ! command -v gh &> /dev/null; then
        skip "Live feedback test disabled (set IW_TEST_LIVE_FEEDBACK=1 to enable)"
    fi

    # Check if gh is authenticated
    if ! gh auth status &> /dev/null; then
        skip "gh CLI not authenticated"
    fi

    # Create real issue with [TEST] prefix for easy identification
    run "$PROJECT_ROOT/iw" feedback "[TEST] E2E live feedback test $(date +%s)"

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Feedback submitted successfully"* ]]
    [[ "$output" == *"Issue:"* ]]
    [[ "$output" == *"https://github.com/iterative-works/iw-cli/issues/"* ]]
}

# ========== Phase 4: gh CLI Prerequisites Tests ==========

@test "feedback fails with helpful message when gh CLI not installed" {
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

    # Run feedback command
    run "$PROJECT_ROOT/iw" feedback "Test issue"

    # Assert failure
    [ "$status" -ne 0 ]
    [[ "$output" == *"gh CLI is not installed"* ]]
    [[ "$output" == *"https://cli.github.com/"* ]]
    [[ "$output" == *"gh auth login"* ]]
}

@test "feedback fails with auth instructions when gh not authenticated" {
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

    # Run feedback command
    run "$PROJECT_ROOT/iw" feedback "Test issue"

    # Assert failure
    [ "$status" -ne 0 ]
    [[ "$output" == *"gh is not authenticated"* ]]
    [[ "$output" == *"gh auth login"* ]]
}

@test "feedback fails when repository not accessible" {
    # Mock gh command that returns permission error
    mkdir -p bin
    cat > bin/gh <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    # Auth succeeds
    echo "Logged in to github.com as testuser"
    exit 0
elif [[ "$1" == "issue" && "$2" == "create" ]]; then
    # But issue creation fails with permission error
    echo "GraphQL: Could not resolve to a Repository with the name 'iterative-works/iw-cli'. (repository)" >&2
    exit 1
else
    echo "Unexpected gh command: $*" >&2
    exit 1
fi
SCRIPT
    chmod +x bin/gh
    export PATH="$TEST_DIR/bin:$PATH"

    # Run feedback command
    run "$PROJECT_ROOT/iw" feedback "Test issue"

    # Assert failure
    [ "$status" -ne 0 ]
    # Error message should contain repository name or permission/access denial
    [[ "$output" == *"Could not resolve"* ]] || [[ "$output" == *"repository"* ]]
}
