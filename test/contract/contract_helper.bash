# PURPOSE: Shared setup and gating predicates for the tool-contract suite
# PURPOSE: Source from each *_contract.bats file. Provides auth detection and skip helpers.

# Resolve project root from any file under test/contract/
CONTRACT_PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"
export CONTRACT_PROJECT_ROOT

# Marker so contract tests can guard "is this actually the contract run".
export IW_CONTRACT_SUITE=1

# Contract tests probe real tools, not the dashboard.
export IW_SERVER_DISABLED=1

# ----- Auth and capability predicates -----

# Succeed iff `gh auth status` reports a logged-in account on github.com.
gh_authenticated() {
    command -v gh >/dev/null 2>&1 || return 1
    gh auth status --hostname github.com >/dev/null 2>&1
}

# Succeed iff `glab auth status` reports any logged-in instance.
glab_authenticated() {
    command -v glab >/dev/null 2>&1 || return 1
    glab auth status >/dev/null 2>&1
}

# Tmux contract is off by default; opt in with IW_CONTRACT_TMUX=1.
tmux_contract_enabled() {
    [ "${IW_CONTRACT_TMUX:-}" = "1" ]
}

# ----- Skip helpers (use as the first line of a @test) -----

require_gh_auth() {
    gh_authenticated || skip "gh not authenticated (set up gh auth or run without --json-shape tests)"
}

require_glab_auth() {
    glab_authenticated || skip "glab not authenticated"
}

require_glab_project() {
    [ -n "${IW_CONTRACT_GLAB_PROJECT:-}" ] \
        || skip "IW_CONTRACT_GLAB_PROJECT not set; cannot probe glab JSON shape"
}

require_tmux_contract() {
    tmux_contract_enabled || skip "tmux contract disabled (set IW_CONTRACT_TMUX=1 to enable)"
}

# ----- Common test scratch dir -----

make_contract_tmpdir() {
    TEST_TMPDIR="$(mktemp -d -t iw-contract-XXXXXX)"
    export TEST_TMPDIR
}

cleanup_contract_tmpdir() {
    [ -n "${TEST_TMPDIR:-}" ] && [ -d "$TEST_TMPDIR" ] && rm -rf "$TEST_TMPDIR"
}

# Configure a throwaway git identity in a directory so commits succeed without
# inheriting the user's name/email.
init_throwaway_git_repo() {
    local dir="$1"
    git -C "$dir" init -q
    git -C "$dir" config user.email "contract@iw-cli.test"
    git -C "$dir" config user.name "Contract Suite"
    git -C "$dir" config commit.gpgsign false
}
