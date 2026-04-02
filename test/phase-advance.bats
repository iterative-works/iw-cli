#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw phase-advance command
# PURPOSE: Tests argument validation and error cases (full advance requires real GitHub)

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"

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

@test "phase-advance without merged PR exits with error" {
    # gh is available but there's no remote or PR, so the PR status check should fail

    run "$PROJECT_ROOT/iw" phase-advance --issue-id TEST-100 --phase-number 1

    [ "$status" -eq 1 ]
    [[ "$output" == *"PR"* ]] || [[ "$output" == *"pr"* ]] || [[ "$output" == *"check"* ]] || [[ "$output" == *"status"* ]]
}

@test "phase-advance from feature branch without --phase-number exits with error" {
    # Create feature branch
    git checkout -q -b TEST-100

    run "$PROJECT_ROOT/iw" phase-advance --issue-id TEST-100

    [ "$status" -eq 1 ]
    [[ "$output" == *"phase-number"* ]] || [[ "$output" == *"phase number"* ]]
}

@test "phase-advance happy path: review-state.json is committed and working tree is clean after advance" {
    # Create feature branch and phase sub-branch
    git checkout -q -b TEST-100
    git checkout -q -b TEST-100-phase-01

    # Commit review-state.json on the feature branch so fetchAndReset preserves it
    git checkout -q TEST-100
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
    git add "project-management/issues/TEST-100/review-state.json"
    git commit -q -m "Add review-state"

    # Sync phase branch with feature branch
    git checkout -q TEST-100-phase-01
    git merge -q TEST-100 2>/dev/null

    # Create a fake remote so fetchAndReset can pull from origin/TEST-100
    git init -q --bare "$TEST_DIR/remote.git" 2>/dev/null
    git remote add origin "$TEST_DIR/remote.git"
    git push -q origin TEST-100 TEST-100-phase-01 2>/dev/null

    # Mock gh: report the phase branch PR as merged
    mkdir -p "$TEST_DIR/mock-bin"
    cat > "$TEST_DIR/mock-bin/gh" << GHEOF
#!/usr/bin/env bash
if [[ "\$1" == "pr" && "\$2" == "list" && "\$*" == *"merged"* ]]; then
    echo '[{"url":"https://github.com/test-org/test-repo/pull/42"}]'
    exit 0
fi
exit 1
GHEOF
    chmod +x "$TEST_DIR/mock-bin/gh"

    PATH="$TEST_DIR/mock-bin:$PATH" run "$PROJECT_ROOT/iw" phase-advance

    [ "$status" -eq 0 ]

    # Working tree must be clean — review-state.json must be committed, not dirty
    [ -z "$(git status --porcelain -- "project-management/issues/TEST-100/review-state.json")" ]

    # The committed blob must contain phase_merged
    local committed_content
    committed_content="$(git show HEAD:project-management/issues/TEST-100/review-state.json)"
    [[ "$committed_content" == *"phase_merged"* ]]

    # A commit with "update review-state" message must exist
    local commit_sha
    commit_sha="$(git log --oneline --grep="update review-state" -1 --format="%H")"
    [ -n "$commit_sha" ]
    git show "$commit_sha" --name-only | grep -q "review-state.json"
}
