#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw implement command
# PURPOSE: Tests workflow-aware dispatch to claude and delegation to batch-implement

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"

# Use a unique tmux socket for test isolation
TMUX_SOCKET="iw-test-$$"

setup() {
    # Disable dashboard server communication during tests
    export IW_SERVER_DISABLED=1
    # Tell iw commands to use isolated tmux socket
    export IW_TMUX_SOCKET="$TMUX_SOCKET"

    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    # Create a git repo with initial commit
    git init -q 2>/dev/null
    git config user.email "test@example.com"
    git config user.name "Test User"
    echo "initial" > README.md
    git add README.md
    git commit -q -m "Initial commit"

    # Initialize iw with config
    mkdir -p .iw
    cat > .iw/config.conf << 'EOF'
project {
  name = testproject
}

tracker {
  type = github
  repository = "test-org/test-repo"
  teamPrefix = "IWLE"
}
EOF

    # Create stub dir for mock commands
    STUB_DIR="$(mktemp -d)"
    export STUB_DIR

    # Mock claude: records its arguments to a file and exits 0
    cat > "$STUB_DIR/claude" << 'STUB'
#!/usr/bin/env bash
echo "$@" > "$STUB_DIR/claude-args.txt"
exit 0
STUB
    chmod +x "$STUB_DIR/claude"

    # Put stub dir first in PATH
    export PATH="$STUB_DIR:$PATH"
}

teardown() {
    # Kill all sessions on the isolated test socket
    tmux -L "$TMUX_SOCKET" kill-server 2>/dev/null || true

    # Clean up worktrees in parent directory
    if [ -n "$TEST_DIR" ]; then
        local parent_dir="$(dirname "$TEST_DIR")"
        rm -rf "$parent_dir"/testproject-* 2>/dev/null || true
    fi

    cd /
    rm -rf "$TEST_DIR"
    rm -rf "$STUB_DIR" 2>/dev/null || true
}

# Helper: create a review-state.json for a given issue ID and workflow type
create_review_state() {
    local issue_id="$1"
    local workflow_type="$2"
    mkdir -p "project-management/issues/$issue_id"
    cat > "project-management/issues/$issue_id/review-state.json" << EOF
{
  "status": "implementing",
  "workflow_type": "$workflow_type",
  "displayText": "Implementing",
  "displayType": "info"
}
EOF
}

# --- Error cases ---

@test "implement without issue ID and not on issue branch exits with code 1 and shows error" {
    # main branch has no issue ID pattern
    run "$PROJECT_ROOT/iw" implement

    [ "$status" -eq 1 ]
    [[ "$output" == *"issue"* ]] || [[ "$output" == *"ID"* ]] || [[ "$output" == *"branch"* ]]
}

@test "implement with valid issue ID but missing review-state.json exits with code 1" {
    # No review-state.json created
    run "$PROJECT_ROOT/iw" implement IWLE-123

    [ "$status" -eq 1 ]
    [[ "$output" == *"review-state"* ]]
}

@test "implement with review-state.json missing workflow_type exits with code 1" {
    mkdir -p project-management/issues/IWLE-123
    cat > project-management/issues/IWLE-123/review-state.json << 'EOF'
{
  "status": "implementing",
  "displayText": "Implementing",
  "displayType": "info"
}
EOF

    run "$PROJECT_ROOT/iw" implement IWLE-123

    [ "$status" -eq 1 ]
    [[ "$output" == *"workflow"* ]] || [[ "$output" == *"workflow_type"* ]]
}

@test "implement with unrecognized workflow type exits with code 1" {
    create_review_state "IWLE-123" "unknown"

    run "$PROJECT_ROOT/iw" implement IWLE-123

    [ "$status" -eq 1 ]
    [[ "$output" == *"nknown"* ]] || [[ "$output" == *"workflow"* ]]
}

# --- Interactive mode: workflow dispatch ---

@test "implement with agile workflow spawns claude with ag-implement prompt" {
    create_review_state "IWLE-123" "agile"

    run "$PROJECT_ROOT/iw" implement IWLE-123

    [ "$status" -eq 0 ]
    [ -f "$STUB_DIR/claude-args.txt" ]
    grep -q "iterative-works:ag-implement" "$STUB_DIR/claude-args.txt"
}

@test "implement with waterfall workflow spawns claude with wf-implement prompt" {
    create_review_state "IWLE-456" "waterfall"

    run "$PROJECT_ROOT/iw" implement IWLE-456

    [ "$status" -eq 0 ]
    [ -f "$STUB_DIR/claude-args.txt" ]
    grep -q "iterative-works:wf-implement" "$STUB_DIR/claude-args.txt"
}

@test "implement with diagnostic workflow spawns claude with dx-implement prompt" {
    create_review_state "IWLE-789" "diagnostic"

    run "$PROJECT_ROOT/iw" implement IWLE-789

    [ "$status" -eq 0 ]
    [ -f "$STUB_DIR/claude-args.txt" ]
    grep -q "iterative-works:dx-implement" "$STUB_DIR/claude-args.txt"
}

# --- Flag passthrough ---

@test "implement --phase N includes phase number in the prompt string" {
    create_review_state "IWLE-123" "waterfall"

    run "$PROJECT_ROOT/iw" implement IWLE-123 --phase 2

    [ "$status" -eq 0 ]
    [ -f "$STUB_DIR/claude-args.txt" ]
    grep -q "\-\-phase 2" "$STUB_DIR/claude-args.txt"
}

@test "implement --model MODEL passes model flag to claude" {
    create_review_state "IWLE-123" "waterfall"

    run "$PROJECT_ROOT/iw" implement IWLE-123 --model sonnet

    [ "$status" -eq 0 ]
    [ -f "$STUB_DIR/claude-args.txt" ]
    grep -q "\-\-model sonnet" "$STUB_DIR/claude-args.txt"
}

# --- Batch mode ---

@test "implement --batch delegates to iw batch-implement with issue ID" {
    create_review_state "IWLE-123" "waterfall"

    # Create a wrapper iw script that intercepts batch-implement calls
    cat > "$TEST_DIR/iw" << IWSCRIPT
#!/usr/bin/env bash
if [[ "\$1" == "batch-implement" ]]; then
    echo "\$@" > "$STUB_DIR/iw-args.txt"
    exit 0
fi
exec "$PROJECT_ROOT/iw" "\$@"
IWSCRIPT
    chmod +x "$TEST_DIR/iw"

    run "$TEST_DIR/iw" implement IWLE-123 --batch

    [ "$status" -eq 0 ]
    [ -f "$STUB_DIR/iw-args.txt" ]
    grep -q "batch-implement" "$STUB_DIR/iw-args.txt"
    grep -q "IWLE-123" "$STUB_DIR/iw-args.txt"
}

# --- Issue ID resolution from branch ---

@test "implement resolves issue ID from branch name when no explicit ID given" {
    create_review_state "IWLE-999" "waterfall"

    git checkout -q -b IWLE-999

    run "$PROJECT_ROOT/iw" implement

    [ "$status" -eq 0 ]
    [ -f "$STUB_DIR/claude-args.txt" ]
    grep -q "IWLE-999" "$STUB_DIR/claude-args.txt"
}
