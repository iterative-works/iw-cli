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
