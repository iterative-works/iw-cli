#!/usr/bin/env bats
# PURPOSE: E2E smoke for iw worktrees (full round-trip through scala-cli + live adapters)
# PURPOSE: Detailed scenarios live in core/test/WorktreesHarnessTest.scala

PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"

setup() {
    export IW_SERVER_DISABLED=1

    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    export HOME="$TEST_DIR"
    mkdir -p "$HOME/.local/share/iw/server"
}

teardown() {
    cd /
    rm -rf "$TEST_DIR"
}

@test "worktrees --all --json: reads state.json, emits JSON array with each worktree" {
    cat > "$HOME/.local/share/iw/server/state.json" << EOF
{
  "worktrees": {
    "TEST-123": {
      "issueId": "TEST-123",
      "path": "$TEST_DIR-TEST-123",
      "trackerType": "linear",
      "team": "TEST",
      "registeredAt": "2024-01-01T00:00:00Z",
      "lastSeenAt": "2024-01-01T00:00:00Z"
    }
  },
  "issueCache": {},
  "progressCache": {},
  "prCache": {},
  "reviewStateCache": {}
}
EOF

    run "$PROJECT_ROOT/iw" worktrees --all --json
    [ "$status" -eq 0 ]
    echo "$output" | jq . > /dev/null
    [ "$(echo "$output" | jq -r '.[0].issueId')" = "TEST-123" ]
}
