#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw phase-merge command
# PURPOSE: Tests branch validation, forge type checks, and missing PR URL error cases

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

    # Create feature branch and phase sub-branch
    git checkout -q -b TEST-100
    git checkout -q -b TEST-100-phase-01

    # Initialize iw config
    mkdir -p .iw
    cat > .iw/config.conf << 'EOF'
project {
  name = testproject
}

tracker {
  type = github
  repository = "test-org/test-repo"
  teamPrefix = "TEST"
}
EOF
}

teardown() {
    cd /
    rm -rf "$TEST_DIR"
}

@test "phase-merge when not on a phase branch exits with error" {
    git checkout -q TEST-100

    run "$PROJECT_ROOT/iw" phase-merge

    [ "$status" -eq 1 ]
    [[ "$output" == *"phase"* ]]
}

@test "phase-merge without config file exits with error about missing config" {
    rm -f .iw/config.conf

    run "$PROJECT_ROOT/iw" phase-merge

    [ "$status" -eq 1 ]
    [[ "$output" == *"config"* ]] || [[ "$output" == *"init"* ]]
}

@test "phase-merge with GitLab forge type exits with not-supported error" {
    cat > .iw/config.conf << 'EOF'
project {
  name = testproject
}

tracker {
  type = gitlab
  repository = "test-group/test-repo"
  teamPrefix = "TEST"
}
EOF

    run "$PROJECT_ROOT/iw" phase-merge

    [ "$status" -eq 1 ]
    [[ "$output" == *"GitLab"* ]] || [[ "$output" == *"future phase"* ]]
}

@test "phase-merge with missing review-state.json exits with error" {
    run "$PROJECT_ROOT/iw" phase-merge

    [ "$status" -eq 1 ]
    [[ "$output" == *"review-state"* ]]
}

@test "phase-merge with review-state.json missing pr_url exits with error" {
    mkdir -p "project-management/issues/TEST-100"
    cat > "project-management/issues/TEST-100/review-state.json" << 'EOF'
{
  "version": 2,
  "issue_id": "TEST-100",
  "status": "awaiting_review"
}
EOF

    run "$PROJECT_ROOT/iw" phase-merge

    [ "$status" -eq 1 ]
    [[ "$output" == *"pr_url"* ]] || [[ "$output" == *"phase-pr"* ]]
}

@test "phase-merge happy path merges PR and updates review-state to phase_merged" {
    # Create review state with pr_url and required artifacts field
    mkdir -p "project-management/issues/TEST-100"
    cat > "project-management/issues/TEST-100/review-state.json" << 'EOF'
{
  "version": 2,
  "issue_id": "TEST-100",
  "status": "awaiting_review",
  "pr_url": "https://github.com/test-org/test-repo/pull/42",
  "artifacts": []
}
EOF

    # Create a fake remote to allow checkout/fetch operations
    git init -q --bare "$TEST_DIR/remote.git" 2>/dev/null
    git remote add origin "$TEST_DIR/remote.git"
    git push -q origin TEST-100 TEST-100-phase-01 2>/dev/null

    # Create a mock gh script that simulates all-passing checks and successful merge
    mkdir -p "$TEST_DIR/mock-bin"
    cat > "$TEST_DIR/mock-bin/gh" << 'GHEOF'
#!/usr/bin/env bash
echo "$1 $2" >> "$TEST_DIR/gh-calls.log"
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to github.com"
    exit 0
fi
if [[ "$1" == "pr" && "$2" == "checks" ]]; then
    echo '[{"link":"https://ci.example.com/1","name":"test","state":"SUCCESS"}]'
    exit 0
fi
if [[ "$1" == "pr" && "$2" == "merge" ]]; then
    exit 0
fi
echo "Unexpected gh call: $*" >&2
exit 1
GHEOF
    chmod +x "$TEST_DIR/mock-bin/gh"

    PATH="$TEST_DIR/mock-bin:$PATH" run "$PROJECT_ROOT/iw" phase-merge

    [ "$status" -eq 0 ]

    # Verify review-state was updated to phase_merged
    local state
    state="$(cat "project-management/issues/TEST-100/review-state.json")"
    [[ "$state" == *"phase_merged"* ]]

    # Verify JSON output contains expected fields
    [[ "$output" == *"TEST-100"* ]]
    [[ "$output" == *"phase_merged"* ]] || [[ "$output" == *"featureBranch"* ]]

    # Verify mock gh was actually called
    [ -f "$TEST_DIR/gh-calls.log" ]
}

@test "phase-merge with failing CI checks exits non-zero and reports failed checks" {
    # Create review state with pr_url and required artifacts field
    mkdir -p "project-management/issues/TEST-100"
    cat > "project-management/issues/TEST-100/review-state.json" << 'EOF'
{
  "version": 2,
  "issue_id": "TEST-100",
  "status": "awaiting_review",
  "pr_url": "https://github.com/test-org/test-repo/pull/42",
  "artifacts": []
}
EOF

    # Create a mock gh script that simulates failing checks and logs calls
    mkdir -p "$TEST_DIR/mock-bin"
    cat > "$TEST_DIR/mock-bin/gh" << 'GHEOF'
#!/usr/bin/env bash
echo "$1 $2" >> "$TEST_DIR/gh-calls.log"
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to github.com"
    exit 0
fi
if [[ "$1" == "pr" && "$2" == "checks" ]]; then
    echo '[{"link":"https://ci.example.com/1","name":"lint","state":"FAILURE"}]'
    exit 0
fi
echo "Unexpected gh call: $*" >&2
exit 1
GHEOF
    chmod +x "$TEST_DIR/mock-bin/gh"

    PATH="$TEST_DIR/mock-bin:$PATH" run "$PROJECT_ROOT/iw" phase-merge

    [ "$status" -eq 1 ]
    [[ "$output" == *"lint"* ]]

    # Verify mock gh was actually called
    [ -f "$TEST_DIR/gh-calls.log" ]
}
