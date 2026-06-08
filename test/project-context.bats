#!/usr/bin/env bats
# PURPOSE: E2E smoke for iw project-context (full round-trip through scala-cli + live adapters)
# PURPOSE: Detailed scenarios live in core/test/ProjectContextHarnessTest.scala

PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"

setup() {
    export IW_SERVER_DISABLED=1

    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    git init -q 2>/dev/null
    git config user.email "test@example.com"
    git config user.name "Test User"
}

teardown() {
    cd /
    rm -rf "$TEST_DIR"
}

@test "project-context emits Project/Repository/Tracker lines for GitHub config" {
    mkdir -p .iw
    cat > .iw/config.conf << 'EOF'
tracker {
  type = github
  repository = "iterative-works/iw-cli"
  teamPrefix = "IW"
}
project { name = iw-cli }
EOF
    git remote add origin git@github.com:iterative-works/iw-cli.git

    run "$PROJECT_ROOT/iw" project-context

    [ "$status" -eq 0 ]
    [[ "$output" == *"**Project:** iw-cli"* ]]
    [[ "$output" == *"**Repository:** iterative-works/iw-cli"* ]]
    [[ "$output" == *"**Tracker:** GitHub"* ]]
    [[ "$output" == *"Forge: GitHub"* ]]
}
