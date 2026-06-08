#!/usr/bin/env bats
# PURPOSE: E2E smoke for iw phase-pr (full round-trip through scala-cli + live adapters)
# PURPOSE: Detailed scenarios live in core/test/PhasePrHarnessTest.scala

PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"

setup() {
    export IW_SERVER_DISABLED=1

    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    git init -q 2>/dev/null
    git config user.email "test@example.com"
    git config user.name "Test User"
    echo "initial" > README.md
    git add README.md 2>/dev/null
    git commit -q -m "Initial commit" 2>/dev/null

    git checkout -q -b TEST-100
    git checkout -q -b TEST-100-phase-01

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

@test "phase-pr happy path: real git push + mock gh, review-state committed, JSON" {
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

    git init -q --bare "$TEST_DIR/remote.git" 2>/dev/null
    git remote add origin "$TEST_DIR/remote.git"
    git push -q origin TEST-100 TEST-100-phase-01 2>/dev/null

    mkdir -p "$TEST_DIR/mock-bin"
    cat > "$TEST_DIR/mock-bin/gh" << 'GHEOF'
#!/usr/bin/env bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to github.com"
    exit 0
fi
if [[ "$1" == "pr" && "$2" == "create" ]]; then
    echo "https://github.com/test-org/test-repo/pull/42"
    exit 0
fi
echo "Unexpected gh call: $*" >&2
exit 1
GHEOF
    chmod +x "$TEST_DIR/mock-bin/gh"

    PATH="$TEST_DIR/mock-bin:$PATH" run "$PROJECT_ROOT/iw" phase-pr --title "Phase 1: Implementation"

    [ "$status" -eq 0 ]

    [ -z "$(git status --porcelain -- "project-management/issues/TEST-100/review-state.json")" ]

    git diff --name-only HEAD~1 HEAD | grep -q "review-state.json"
    grep -q "awaiting_review" "project-management/issues/TEST-100/review-state.json"
}
