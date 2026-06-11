#!/usr/bin/env bats
# PURPOSE: E2E smoke for iw doctor (full round-trip through scala-cli + live adapters)
# PURPOSE: Detailed scenarios live in core/test/DoctorHarnessTest.scala

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
  team = TEST
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

@test "doctor: prints environment check report for a valid project" {
    run "$PROJECT_ROOT/iw" doctor

    [[ "$output" == *"Environment Check"* ]]
    [[ "$output" == *"Git repository"* ]]
    [[ "$output" == *"Configuration"* ]]
}
