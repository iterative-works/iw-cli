#!/usr/bin/env bats
# PURPOSE: Smoke tests for iw issue command with Forgejo tracker
# PURPOSE: Proves config-parse → dispatch → token resolution end-to-end (no network)

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"

setup() {
    # Disable dashboard server communication during tests
    export IW_SERVER_DISABLED=1

    # Create a temporary directory for each test
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    # Setup git repo
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    touch README.md
    git add README.md
    git commit -m "Initial commit"
}

teardown() {
    # Clean up temporary directory
    rm -rf "$TEST_DIR"
}

# ========== Error Wiring Smoke Test ==========

@test "Forgejo: issue exits 1 with FORGEJO_API_TOKEN message when token is missing" {
    # Setup: create Forgejo config (no FORGEJO_API_TOKEN in env)
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = forgejo
  repository = "owner/sample"
  teamPrefix = "SAMP"
  baseUrl = "https://codeberg.org"
}

project {
  name = test-project
}
EOF

    # Ensure the token is not set
    unset FORGEJO_API_TOKEN

    # Run issue command
    run "$PROJECT_ROOT/iw" issue 1

    # Assert failure with message mentioning FORGEJO_API_TOKEN
    [ "$status" -eq 1 ]
    [[ "$output" == *"FORGEJO_API_TOKEN"* ]]
}
