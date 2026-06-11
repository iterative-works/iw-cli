#!/usr/bin/env bats
# PURPOSE: E2E smoke for iw config (full round-trip through scala-cli + live adapters)
# PURPOSE: Detailed scenarios live in core/test/ConfigHarnessTest.scala

PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"

setup() {
    export IW_SERVER_DISABLED=1
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    git init
    git config user.email "test@example.com"
    git config user.name "Test User"

    mkdir -p .iw
    cat > .iw/config.conf <<'EOF'
tracker {
  type = linear
  team = SMOKE
}

project {
  name = test-project
}
EOF
}

teardown() {
    cd /
    rm -rf "$TEST_DIR"
}

@test "config: get team prints SMOKE" {
    run "$PROJECT_ROOT/iw" config get team

    [ "$status" -eq 0 ]
    [[ "$output" == *"SMOKE"* ]]
}
