#!/usr/bin/env bats
# PURPOSE: Pin assumptions iw-cli's GitAdapter and GitWorktreeAdapter make about git's CLI
# PURPOSE: surface (porcelain shapes, worktree commands, push to file:// remote).

load contract_helper

setup() {
    make_contract_tmpdir
}

teardown() {
    cleanup_contract_tmpdir
}

@test "git: rev-parse HEAD returns 40 hex chars" {
    init_throwaway_git_repo "$TEST_TMPDIR"
    git -C "$TEST_TMPDIR" commit -q --allow-empty -m "first"
    run git -C "$TEST_TMPDIR" rev-parse HEAD
    [ "$status" -eq 0 ]
    [[ "$output" =~ ^[0-9a-f]{40}$ ]]
}

@test "git: rev-parse --short HEAD returns at least 7 hex chars" {
    init_throwaway_git_repo "$TEST_TMPDIR"
    git -C "$TEST_TMPDIR" commit -q --allow-empty -m "first"
    run git -C "$TEST_TMPDIR" rev-parse --short HEAD
    [ "$status" -eq 0 ]
    [[ "$output" =~ ^[0-9a-f]{7,}$ ]]
}

@test "git: status --porcelain returns XY-prefixed lines" {
    init_throwaway_git_repo "$TEST_TMPDIR"
    git -C "$TEST_TMPDIR" commit -q --allow-empty -m "first"
    echo "untracked" > "$TEST_TMPDIR/new.txt"
    echo "staged"   > "$TEST_TMPDIR/staged.txt"
    git -C "$TEST_TMPDIR" add staged.txt
    run git -C "$TEST_TMPDIR" status --porcelain
    [ "$status" -eq 0 ]
    # Untracked: "?? <path>"; staged-added: "A  <path>"
    [[ "$output" == *"?? new.txt"* ]]
    [[ "$output" == *"A  staged.txt"* ]]
}

@test "git worktree list --porcelain returns 'worktree <path>' lines" {
    init_throwaway_git_repo "$TEST_TMPDIR"
    git -C "$TEST_TMPDIR" commit -q --allow-empty -m "first"
    run git -C "$TEST_TMPDIR" worktree list --porcelain
    [ "$status" -eq 0 ]
    # Must contain a line starting with "worktree " followed by the main worktree path.
    # GitWorktreeAdapter.worktreeExists greps for "worktree $path" verbatim.
    [[ "$output" == *"worktree $TEST_TMPDIR"* ]]
}

@test "git worktree add -b creates a worktree on a new branch" {
    init_throwaway_git_repo "$TEST_TMPDIR"
    git -C "$TEST_TMPDIR" commit -q --allow-empty -m "first"
    local wt="$TEST_TMPDIR/wt"
    run git -C "$TEST_TMPDIR" worktree add -b feature/x "$wt"
    [ "$status" -eq 0 ]
    [ -d "$wt" ]
    run git -C "$wt" rev-parse --abbrev-ref HEAD
    [ "$output" = "feature/x" ]
}

@test "git worktree remove cleans up after worktree add -b" {
    init_throwaway_git_repo "$TEST_TMPDIR"
    git -C "$TEST_TMPDIR" commit -q --allow-empty -m "first"
    local wt="$TEST_TMPDIR/wt"
    git -C "$TEST_TMPDIR" worktree add -b feature/x "$wt"
    run git -C "$TEST_TMPDIR" worktree remove "$wt"
    [ "$status" -eq 0 ]
    [ ! -d "$wt" ]
    # worktree list no longer mentions it
    run git -C "$TEST_TMPDIR" worktree list --porcelain
    [[ "$output" != *"$wt"* ]]
}

@test "git push to file:// remote succeeds and updates remote HEAD" {
    local remote="$TEST_TMPDIR/remote.git"
    local clone="$TEST_TMPDIR/clone"
    git init -q --bare "$remote"
    git clone -q "$remote" "$clone"
    git -C "$clone" config user.email "contract@iw-cli.test"
    git -C "$clone" config user.name "Contract Suite"
    git -C "$clone" config commit.gpgsign false
    echo "hello" > "$clone/file.txt"
    git -C "$clone" add file.txt
    git -C "$clone" commit -q -m "add file"
    run git -C "$clone" push -u origin HEAD:main
    [ "$status" -eq 0 ]
    # Remote now has a ref pointing at our commit.
    local local_sha remote_sha
    local_sha="$(git -C "$clone" rev-parse HEAD)"
    remote_sha="$(git -C "$remote" rev-parse refs/heads/main)"
    [ "$local_sha" = "$remote_sha" ]
}
