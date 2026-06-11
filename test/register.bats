#!/usr/bin/env bats
# PURPOSE: E2E smoke for iw register (full round-trip through scala-cli + live adapters)
# PURPOSE: Detailed scenarios live in core/test/RegisterHarnessTest.scala

PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"

setup() {
    export IW_SERVER_DISABLED=1

    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    echo "initial" > README.md
    git add README.md
    git commit -m "Initial commit"

    mkdir -p .iw
    cat > .iw/config.conf << 'EOF'
project {
  name = testproject
}

tracker {
  type = linear
  team = TEST
}
EOF
}

teardown() {
    cd /
    rm -rf "$TEST_DIR"
}

@test "register: succeeds on issue branch with dashboard disabled" {
    git checkout -b IWLE-123

    run "$PROJECT_ROOT/iw" register

    [ "$status" -eq 0 ]
    [[ "$output" == *"Registered worktree"* ]]
    [[ "$output" == *"IWLE-123"* ]]
}
