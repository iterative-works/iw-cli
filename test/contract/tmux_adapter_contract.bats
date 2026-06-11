#!/usr/bin/env bats
# PURPOSE: Pin assumptions iw-cli's TmuxAdapter makes about tmux semantics on -L socket
# PURPOSE: (has-session, new-session -d, send-keys, kill-session, capture-pane round-trip).
# PURPOSE: Gated by IW_CONTRACT_TMUX=1 because containerised CI without a TTY is flaky.

load contract_helper

CONTRACT_TMUX_SOCKET="iw-contract-$$"

setup() {
    require_tmux_contract
    # Best-effort kill in case a previous failure left state behind.
    tmux -L "$CONTRACT_TMUX_SOCKET" kill-server 2>/dev/null || true
}

teardown() {
    tmux -L "$CONTRACT_TMUX_SOCKET" kill-server 2>/dev/null || true
}

@test "tmux: binary on PATH" {
    run command -v tmux
    [ "$status" -eq 0 ]
}

@test "tmux -V exits 0 and reports a version" {
    run tmux -V
    [ "$status" -eq 0 ]
    [[ "$output" =~ tmux[[:space:]]+[0-9]+\. ]]
}

@test "tmux -L <socket> new-session -d creates a detached session that has-session sees" {
    run tmux -L "$CONTRACT_TMUX_SOCKET" new-session -d -s probe -c "$PWD"
    [ "$status" -eq 0 ]
    run tmux -L "$CONTRACT_TMUX_SOCKET" has-session -t probe
    [ "$status" -eq 0 ]
}

@test "tmux -L <socket> send-keys + capture-pane round-trip" {
    tmux -L "$CONTRACT_TMUX_SOCKET" new-session -d -s probe -c "$PWD"
    tmux -L "$CONTRACT_TMUX_SOCKET" send-keys -t probe 'echo CONTRACT_MARKER' Enter
    # Give the shell a beat to render. capture-pane needs the line in the scrollback.
    sleep 0.2
    run tmux -L "$CONTRACT_TMUX_SOCKET" capture-pane -t probe -p
    [ "$status" -eq 0 ]
    [[ "$output" == *"CONTRACT_MARKER"* ]]
}

@test "tmux -L <socket> kill-session removes the session" {
    tmux -L "$CONTRACT_TMUX_SOCKET" new-session -d -s probe -c "$PWD"
    run tmux -L "$CONTRACT_TMUX_SOCKET" kill-session -t probe
    [ "$status" -eq 0 ]
    run tmux -L "$CONTRACT_TMUX_SOCKET" has-session -t probe
    [ "$status" -ne 0 ]
}
