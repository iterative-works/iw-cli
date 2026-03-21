#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw phase-pr command
# PURPOSE: Tests argument validation and error cases (full PR creation requires real GitHub)

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

@test "phase-pr non-batch: review-state.json is committed and working tree is clean after PR creation" {
    # Create a committed review-state.json
    mkdir -p "project-management/issues/TEST-100"
    cat > "project-management/issues/TEST-100/review-state.json" << 'EOF'
{
  "version": 2,
  "issue_id": "TEST-100",
  "status": "in_progress",
  "artifacts": []
}
EOF
    git add "project-management/issues/TEST-100/review-state.json"
    git commit -q -m "Add review-state"

    # Create a fake bare remote so push succeeds
    git init -q --bare "$TEST_DIR/remote.git" 2>/dev/null
    git remote add origin "$TEST_DIR/remote.git"
    git push -q origin TEST-100 TEST-100-phase-01 2>/dev/null

    # Create a mock gh that handles pr create
    mkdir -p "$TEST_DIR/mock-bin"
    cat > "$TEST_DIR/mock-bin/gh" << GHEOF
#!/usr/bin/env bash
if [[ "\$1" == "auth" && "\$2" == "status" ]]; then
    echo "Logged in to github.com"
    exit 0
fi
if [[ "\$1" == "pr" && "\$2" == "create" ]]; then
    echo "https://github.com/test-org/test-repo/pull/42"
    exit 0
fi
echo "Unexpected gh call: \$*" >&2
exit 1
GHEOF
    chmod +x "$TEST_DIR/mock-bin/gh"

    PATH="$TEST_DIR/mock-bin:$PATH" run "$PROJECT_ROOT/iw" phase-pr --title "Phase 1: Implementation"

    [ "$status" -eq 0 ]

    # review-state.json must not appear as modified or staged (M or A in porcelain output)
    local porcelain
    porcelain="$(git status --porcelain -- "project-management/issues/TEST-100/review-state.json")"
    [ -z "$porcelain" ]

    # The latest commit must contain the updated review-state.json
    local changed_files
    changed_files="$(git diff --name-only HEAD~1 HEAD)"
    [[ "$changed_files" == *"review-state.json"* ]]

    # review-state must contain awaiting_review status
    local state
    state="$(cat "project-management/issues/TEST-100/review-state.json")"
    [[ "$state" == *"awaiting_review"* ]]
}

@test "phase-pr when not on a phase branch exits with error" {
    # Switch to the feature branch
    git checkout -q TEST-100

    run "$PROJECT_ROOT/iw" phase-pr --title "Test PR"

    [ "$status" -eq 1 ]
    [[ "$output" == *"phase"* ]]
}

@test "phase-pr without --title exits with error" {
    run "$PROJECT_ROOT/iw" phase-pr

    [ "$status" -eq 1 ]
    [[ "$output" == *"title"* ]] || [[ "$output" == *"--title"* ]]
}

@test "phase-pr without config file exits with error about missing config" {
    rm -f .iw/config.conf

    run "$PROJECT_ROOT/iw" phase-pr --title "Test PR"

    [ "$status" -eq 1 ]
    [[ "$output" == *"config"* ]] || [[ "$output" == *"init"* ]]
}
