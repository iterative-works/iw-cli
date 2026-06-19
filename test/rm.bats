#!/usr/bin/env bats
# PURPOSE: E2E smoke for iw rm (full round-trip through scala-cli + live adapters)
# PURPOSE: Detailed scenarios live in core/test/RmHarnessTest.scala

PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"
TMUX_SOCKET="iw-test-$$"

setup() {
    export IW_SERVER_DISABLED=1
    export IW_TMUX_SOCKET="$TMUX_SOCKET"

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
    tmux -L "$TMUX_SOCKET" kill-server 2>/dev/null || true
    if [ -n "$TEST_DIR" ]; then
        local parent_dir="$(dirname "$TEST_DIR")"
        rm -rf "$parent_dir"/testproject-* 2>/dev/null || true
    fi
    cd /
    rm -rf "$TEST_DIR"
}

@test "rm: removes worktree and tmux session" {
    git worktree add -b IWLE-123 "../testproject-IWLE-123"
    tmux -L "$TMUX_SOCKET" new-session -d -s "testproject-IWLE-123" -c "../testproject-IWLE-123"

    tmux -L "$TMUX_SOCKET" has-session -t "testproject-IWLE-123"
    [ -d "../testproject-IWLE-123" ]

    run "$PROJECT_ROOT/iw" rm IWLE-123

    [ "$status" -eq 0 ]
    ! tmux -L "$TMUX_SOCKET" has-session -t "testproject-IWLE-123" 2>/dev/null
    [ ! -d "../testproject-IWLE-123" ]
}

@test "rm: cleanup hook runs before worktree is removed" {
    git worktree add -b IWLE-456 "../testproject-IWLE-456"
    [ -d "../testproject-IWLE-456" ]

    # Write a hook-rm.scala that prints a sentinel and returns Nil
    mkdir -p .iw/commands
    cat > .iw/commands/sentinel.hook-rm.scala << 'EOF'
// PURPOSE: Test cleanup hook for rm E2E smoke test
import iw.core.model.{CleanupAction, CleanupContext}

object SentinelHookRm:
  val action: CleanupAction = new CleanupAction:
    def cleanup(ctx: CleanupContext): List[String] =
      println("HOOK_RAN")
      Nil
EOF

    run "$PROJECT_ROOT/iw" rm IWLE-456

    [ "$status" -eq 0 ]

    # Assert sentinel appears in output
    echo "$output" | grep -q "HOOK_RAN" || fail "HOOK_RAN sentinel not found in rm output"

    # Assert sentinel appears BEFORE "Worktree removed"
    hook_line=$(echo "$output" | grep -n "HOOK_RAN" | head -1 | cut -d: -f1)
    removed_line=$(echo "$output" | grep -n "Worktree removed" | head -1 | cut -d: -f1)
    [ -n "$hook_line" ]
    [ -n "$removed_line" ]
    [ "$hook_line" -lt "$removed_line" ]
}
