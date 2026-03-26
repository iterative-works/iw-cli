#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw batch-implement command
# PURPOSE: Tests argument validation and pre-flight checks (full batch run requires real GitHub)

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

setup() {
    # Disable dashboard server communication during tests
    export IW_SERVER_DISABLED=1

    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    # Create a git repo
    git init -q 2>/dev/null
    git config user.email "test@example.com"
    git config user.name "Test User"
    echo "initial" > README.md
    git add README.md 2>/dev/null
    git commit -q -m "Initial commit" 2>/dev/null

    # Create feature branch matching IW-275 pattern
    git checkout -q -b IW-275

    # Initialize iw config
    mkdir -p .iw
    cat > .iw/config.conf << 'EOF'
project {
  name = testproject
}

tracker {
  type = github
  repository = "test-org/test-repo"
  teamPrefix = "IW"
}
EOF

    # Commit config so the tree is clean
    git add -A 2>/dev/null
    git commit -q -m "Add config" 2>/dev/null || true

    # Create project-management issue directory with tasks.md and review-state.json
    mkdir -p project-management/issues/IW-275
    cat > project-management/issues/IW-275/tasks.md << 'EOF'
# Tasks

## Phase Index

- [ ] Phase 1: First phase
- [ ] Phase 2: Second phase
EOF

    cat > project-management/issues/IW-275/review-state.json << 'EOF'
{
  "status": "implementing",
  "workflow_type": "waterfall",
  "displayText": "Implementing",
  "displayType": "info"
}
EOF

    git add -A 2>/dev/null
    git commit -q -m "Add issue files" 2>/dev/null || true

    # Create a stub claude script that records its arguments
    STUB_DIR="$(mktemp -d)"
    export STUB_DIR

    cat > "$STUB_DIR/claude" << 'STUB'
#!/usr/bin/env bash
# Stub claude: record args and succeed
echo "$@" >> "$STUB_DIR/claude-calls.txt"
exit 0
STUB
    chmod +x "$STUB_DIR/claude"

    cat > "$STUB_DIR/gh" << 'STUB'
#!/usr/bin/env bash
# Stub gh: record args and succeed
echo "$@" >> "$STUB_DIR/gh-calls.txt"
exit 0
STUB
    chmod +x "$STUB_DIR/gh"

    # Put stub dir first in PATH
    export PATH="$STUB_DIR:$PATH"
}

teardown() {
    cd /
    rm -rf "$TEST_DIR"
    rm -rf "$STUB_DIR" 2>/dev/null || true
}

# --- Pre-flight validation tests ---

@test "batch-implement exits non-zero with error about tasks.md when it is missing" {
    rm -f project-management/issues/IW-275/tasks.md
    git add -A 2>/dev/null
    git commit -q -m "Remove tasks.md" 2>/dev/null || true

    run "$PROJECT_ROOT/iw" batch-implement IW-275 wf

    [ "$status" -ne 0 ]
    [[ "$output" == *"tasks.md"* ]]
}

@test "batch-implement exits non-zero with error about claude when claude is not on PATH" {
    # Remove the stub claude so commandExists("claude") returns false
    rm -f "$STUB_DIR/claude"

    # This test only works when no real claude is installed; skip otherwise
    if command -v claude &>/dev/null; then
        skip "real claude CLI is installed; cannot test missing-claude error path"
    fi

    run "$PROJECT_ROOT/iw" batch-implement IW-275 wf

    [ "$status" -ne 0 ]
    [[ "$output" == *"claude"* ]]
}

@test "batch-implement exits non-zero with error about commit or stash when working tree is dirty" {
    # Create an uncommitted file after the last commit
    echo "dirty" > dirty-file.txt

    run "$PROJECT_ROOT/iw" batch-implement IW-275 wf

    [ "$status" -ne 0 ]
    [[ "$output" == *"commit"* ]] || [[ "$output" == *"stash"* ]]
}

@test "batch-implement exits non-zero with usage hint when no issue ID on CLI and branch has none" {
    # Check out a branch with no issue ID pattern
    git checkout -q -b no-issue-branch

    # Remove any positional args
    run "$PROJECT_ROOT/iw" batch-implement wf

    [ "$status" -ne 0 ]
    # Should mention how to provide an issue ID
    [[ "$output" == *"issue"* ]] || [[ "$output" == *"ISSUE"* ]] || [[ "$output" == *"ID"* ]]
}

@test "batch-implement exits non-zero with error about forge CLI when gh is not on PATH" {
    rm -f "$STUB_DIR/gh"

    if command -v gh &>/dev/null; then
        skip "real gh CLI is installed; cannot test missing-forge error path"
    fi

    run "$PROJECT_ROOT/iw" batch-implement IW-275 wf

    [ "$status" -ne 0 ]
    [[ "$output" == *"gh"* ]] || [[ "$output" == *"CLI"* ]]
}

# --- Argument parsing tests ---

@test "batch-implement uses issue ID from positional arg in claude prompt" {
    run "$PROJECT_ROOT/iw" batch-implement IW-275 wf

    [ -f "$STUB_DIR/claude-calls.txt" ]
    grep -q "IW-275 --batch --phase" "$STUB_DIR/claude-calls.txt"
}

@test "batch-implement uses workflow code from positional arg in claude prompt" {
    # Use ag instead of wf to differentiate from auto-detection
    cat > project-management/issues/IW-275/review-state.json << 'EOF'
{
  "status": "implementing",
  "workflow_type": "agile",
  "displayText": "Implementing",
  "displayType": "info"
}
EOF
    git add -A 2>/dev/null
    git commit -q -m "Update review-state" 2>/dev/null || true

    run "$PROJECT_ROOT/iw" batch-implement IW-275 ag

    [ -f "$STUB_DIR/claude-calls.txt" ]
    grep -q "iterative-works:ag-implement" "$STUB_DIR/claude-calls.txt"
}

@test "batch-implement auto-detects issue ID from branch name IW-275" {
    # Don't provide a positional issue ID; branch name is IW-275
    run "$PROJECT_ROOT/iw" batch-implement wf

    [ -f "$STUB_DIR/claude-calls.txt" ]
    grep -q "IW-275 --batch --phase" "$STUB_DIR/claude-calls.txt"
}

@test "batch-implement auto-detects workflow code from review-state.json" {
    # review-state.json has "workflow_type": "waterfall" → should resolve to "wf"
    run "$PROJECT_ROOT/iw" batch-implement IW-275

    [ -f "$STUB_DIR/claude-calls.txt" ]
    grep -q "iterative-works:wf-implement" "$STUB_DIR/claude-calls.txt"
}

# --- phase-merge integration tests ---
# Verify that batch-implement delegates merge responsibility to ./iw phase-merge.

@test "batch-implement invokes iw phase-merge when review-state status is awaiting_review after agent runs" {
    # Stub claude: sets review-state to awaiting_review with a pr_url
    cat > "$STUB_DIR/claude" << STUB
#!/usr/bin/env bash
echo "\$@" >> "$STUB_DIR/claude-calls.txt"
cat > "$TEST_DIR/project-management/issues/IW-275/review-state.json" << 'JSON'
{
  "status": "awaiting_review",
  "workflow_type": "waterfall",
  "pr_url": "https://github.com/test-org/test-repo/pull/42"
}
JSON
exit 0
STUB
    chmod +x "$STUB_DIR/claude"

    # Create a wrapper iw script in TEST_DIR that intercepts phase-merge
    cat > "$TEST_DIR/iw" << IWSCRIPT
#!/usr/bin/env bash
if [[ "\$1" == "phase-merge" ]]; then
    echo "phase-merge \$*" >> "$TEST_DIR/phase-merge-calls.log"
    cat > "$TEST_DIR/project-management/issues/IW-275/review-state.json" << 'JSON'
{
  "status": "phase_merged",
  "workflow_type": "waterfall",
  "pr_url": "https://github.com/test-org/test-repo/pull/42"
}
JSON
    exit 0
fi
exec "$PROJECT_ROOT/iw" "\$@"
IWSCRIPT
    chmod +x "$TEST_DIR/iw"

    # Commit wrapper so working tree stays clean
    git add -A 2>/dev/null
    git commit -q -m "Add iw wrapper" 2>/dev/null || true

    run "$TEST_DIR/iw" batch-implement IW-275 wf

    [ "$status" -eq 0 ]

    # phase-merge was invoked
    [ -f "$TEST_DIR/phase-merge-calls.log" ]

    # tasks.md has phase 1 checked off
    grep -q "\[x\] Phase 1" "$TEST_DIR/project-management/issues/IW-275/tasks.md"

    # No direct gh pr merge calls (assert even if gh was never called)
    ! grep -q "pr merge" "$STUB_DIR/gh-calls.txt" 2>/dev/null
}

@test "batch-implement stops immediately when phase-merge exits non-zero" {
    # Stub claude: sets review-state to awaiting_review with a pr_url
    cat > "$STUB_DIR/claude" << STUB
#!/usr/bin/env bash
echo "\$@" >> "$STUB_DIR/claude-calls.txt"
cat > "$TEST_DIR/project-management/issues/IW-275/review-state.json" << 'JSON'
{
  "status": "awaiting_review",
  "workflow_type": "waterfall",
  "pr_url": "https://github.com/test-org/test-repo/pull/42"
}
JSON
exit 0
STUB
    chmod +x "$STUB_DIR/claude"

    # Create a wrapper iw script where phase-merge always fails
    cat > "$TEST_DIR/iw" << IWSCRIPT
#!/usr/bin/env bash
if [[ "\$1" == "phase-merge" ]]; then
    echo "phase-merge failed" >&2
    exit 1
fi
exec "$PROJECT_ROOT/iw" "\$@"
IWSCRIPT
    chmod +x "$TEST_DIR/iw"

    # Commit wrapper so working tree stays clean
    git add -A 2>/dev/null
    git commit -q -m "Add failing iw wrapper" 2>/dev/null || true

    run "$TEST_DIR/iw" batch-implement IW-275 wf

    [ "$status" -ne 0 ]

    # tasks.md does NOT have phase 1 checked off
    ! grep -q "\[x\] Phase 1" "$TEST_DIR/project-management/issues/IW-275/tasks.md"
}

# --- Claude invocation flag tests ---

@test "batch-implement passes --dangerously-skip-permissions to claude" {
    run "$PROJECT_ROOT/iw" batch-implement IW-275 wf

    [ -f "$STUB_DIR/claude-calls.txt" ]
    grep -q "\-\-dangerously-skip-permissions" "$STUB_DIR/claude-calls.txt"
}

@test "batch-implement does not hang when claude subprocess has no stdin" {
    # Stub claude that reads from stdin — if stdin is properly closed, read returns immediately.
    # Also sets review-state to awaiting_review so the phase progresses.
    cat > "$STUB_DIR/claude" << STUB
#!/usr/bin/env bash
echo "\$@" >> "$STUB_DIR/claude-calls.txt"
# Try to read from stdin — if stdin is properly closed, read returns immediately
read -t 5 line
# Record that read returned (didn't hang)
echo "read_returned" >> "$STUB_DIR/stdin-test.txt"
# Set review-state so the phase completes
cat > "$TEST_DIR/project-management/issues/IW-275/review-state.json" << 'JSON'
{
  "status": "phase_merged",
  "workflow_type": "waterfall"
}
JSON
exit 0
STUB
    chmod +x "$STUB_DIR/claude"

    # 30-second timeout: if stdin isn't closed, `read -t 5` waits 5s per invocation
    # but with closed stdin it returns instantly
    run timeout 30 "$PROJECT_ROOT/iw" batch-implement IW-275 wf

    # Should complete without timing out
    [ "$status" -eq 0 ]
    # Verify read actually returned (stdin was closed)
    [ -f "$STUB_DIR/stdin-test.txt" ]
    grep -q "read_returned" "$STUB_DIR/stdin-test.txt"
}
