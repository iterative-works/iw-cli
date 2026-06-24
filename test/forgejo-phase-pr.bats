#!/usr/bin/env bats
# PURPOSE: Smoke tests for iw phase-pr command with Forgejo forge
# PURPOSE: Proves config-parse → ForgeType.resolve → Forgejo dispatch → token resolution (no network)

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"

setup() {
    # Disable dashboard server communication during tests
    export IW_SERVER_DISABLED=1

    # Create a temporary directory for each test
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    # Setup bare remote repo and local clone on a phase branch
    git init --bare "$TEST_DIR/remote.git"
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin "$TEST_DIR/remote.git"
    touch README.md
    git add README.md
    git commit -m "Initial commit"
    git push -u origin main 2>/dev/null || git push -u origin master 2>/dev/null || true
    git checkout -b "SAMP-1-phase-01"
}

teardown() {
    # Clean up temporary directory
    rm -rf "$TEST_DIR"
}

# ========== Error Wiring Smoke Test ==========

@test "Forgejo: phase-pr exits 1 with FORGEJO_API_TOKEN message when token is missing" {
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

    # Run phase-pr command (will fail at token guard before any HTTP call)
    run "$PROJECT_ROOT/iw" phase-pr --title "Test PR"

    # Assert failure with message mentioning FORGEJO_API_TOKEN
    [ "$status" -eq 1 ]
    [[ "$output" == *"FORGEJO_API_TOKEN"* ]]
}
