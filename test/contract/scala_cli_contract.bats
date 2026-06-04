#!/usr/bin/env bats
# PURPOSE: Pin assumptions iw-run makes about scala-cli (`scala-cli run -q --jar <core.jar>`
# PURPOSE: with project.scala header still resolves and executes user scripts).

load contract_helper

setup() {
    make_contract_tmpdir
}

teardown() {
    cleanup_contract_tmpdir
}

resolve_core_jar() {
    local jar
    jar="$(cd "$CONTRACT_PROJECT_ROOT" \
        && ./mill --ticker false show core.jar 2>/dev/null \
        | jq -r '.' \
        | sed -E 's#^ref:v[0-9]+:[a-f0-9]+:##')"
    echo "$jar"
}

@test "scala-cli: binary on PATH" {
    run command -v scala-cli
    [ "$status" -eq 0 ]
}

@test "scala-cli --version exits 0 and reports a version" {
    run scala-cli --version
    [ "$status" -eq 0 ]
    [[ "$output" =~ [0-9]+\.[0-9]+\.[0-9]+ ]]
}

@test "scala-cli run -q with --jar produces working classpath for iw.core.model" {
    local core_jar
    core_jar="$(resolve_core_jar)"
    [ -n "$core_jar" ] || skip "core.jar not built (run ./mill core.jar first)"
    [ -f "$core_jar" ] || skip "core.jar resolved but file missing"

    cat > "$TEST_TMPDIR/probe.scala" <<'EOF'
//> using scala 3.3.7

import iw.core.model.GitRemote

@main def probe(): Unit =
    val r = GitRemote("git@github.com:owner/repo.git")
    println(s"OK ${r.url}")
EOF

    # Match what iw-run does at commands/*.scala dispatch time.
    run scala-cli run -q --suppress-outdated-dependency-warning \
        "$TEST_TMPDIR/probe.scala" \
        "$CONTRACT_PROJECT_ROOT/core/project.scala" \
        --jar "$core_jar"
    [ "$status" -eq 0 ]
    [[ "$output" == *"OK git@github.com:owner/repo.git"* ]]
}

@test "scala-cli compile --scalac-option -Werror succeeds on core/" {
    # Mirrors the pre-commit and CI compile step.
    run scala-cli compile --scalac-option -Werror "$CONTRACT_PROJECT_ROOT/core/"
    [ "$status" -eq 0 ]
}
