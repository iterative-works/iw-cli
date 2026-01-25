#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw issue create command
# PURPOSE: Tests help display, argument validation, and basic command structure

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

@test "issue create for non-GitHub tracker shows not supported message" {
    # Create a Linear config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = linear
  team = TEST
}
project {
  name = test-project
}
EOF

    # Mock gh command - should not be called
    mkdir -p bin
    cat > bin/gh <<'SCRIPT'
#!/bin/bash
echo "Should not be called for Linear tracker" >&2
exit 1
SCRIPT
    chmod +x bin/gh
    export PATH="$TEST_DIR/bin:$PATH"

    # Run issue create command
    run "$PROJECT_ROOT/iw" issue create --title "Test issue"

    # Assert failure with not supported message
    [ "$status" -eq 1 ]
    [[ "$output" == *"not yet supported"* ]]
}
