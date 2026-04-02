// PURPOSE: Unit tests for PhaseOutput JSON data types for phase commands
// PURPOSE: Tests JSON serialization of StartOutput, CommitOutput, PrOutput, and AdvanceOutput

package iw.tests

import munit.FunSuite
import iw.core.model.PhaseOutput

class PhaseOutputTest extends FunSuite:

  // StartOutput tests

  test("StartOutput.toJson produces valid pretty-printed JSON with all fields"):
    val output = PhaseOutput.StartOutput(
      issueId = "IW-238",
      phaseNumber = "01",
      branch = "IW-238-phase-01",
      baselineSha = "abc123def456"
    )
    val json = output.toJson
    val parsed = ujson.read(json)
    assertEquals(parsed("issueId").str, "IW-238")
    assertEquals(parsed("phaseNumber").str, "01")
    assertEquals(parsed("branch").str, "IW-238-phase-01")
    assertEquals(parsed("baselineSha").str, "abc123def456")

  test("StartOutput.toJson is pretty-printed with indent = 2"):
    val output = PhaseOutput.StartOutput(
      issueId = "IW-238",
      phaseNumber = "01",
      branch = "IW-238-phase-01",
      baselineSha = "abc123"
    )
    val json = output.toJson
    // Indented JSON should contain newlines and spaces
    assert(
      json.contains("\n"),
      s"Expected newlines in pretty-printed JSON, got: $json"
    )
    assert(
      json.contains("  "),
      s"Expected indentation in pretty-printed JSON, got: $json"
    )

  // CommitOutput tests

  test(
    "CommitOutput.toJson produces valid JSON with correct field names and types"
  ):
    val output = PhaseOutput.CommitOutput(
      issueId = "IW-238",
      phaseNumber = "01",
      commitSha = "deadbeef",
      filesCommitted = 5,
      message = "feat: implement phase 1"
    )
    val json = output.toJson
    val parsed = ujson.read(json)
    assertEquals(parsed("issueId").str, "IW-238")
    assertEquals(parsed("phaseNumber").str, "01")
    assertEquals(parsed("commitSha").str, "deadbeef")
    assertEquals(parsed("filesCommitted").num.toInt, 5)
    assertEquals(parsed("message").str, "feat: implement phase 1")

  // PrOutput tests

  test("PrOutput.toJson serializes 'merged = true' as JSON boolean true"):
    val output = PhaseOutput.PrOutput(
      issueId = "IW-238",
      phaseNumber = "01",
      prUrl = "https://github.com/org/repo/pull/42",
      headBranch = "IW-238-phase-01",
      baseBranch = "IW-238",
      merged = true
    )
    val json = output.toJson
    val parsed = ujson.read(json)
    assertEquals(parsed("merged").bool, true)

  test("PrOutput.toJson serializes 'merged = false' as JSON boolean false"):
    val output = PhaseOutput.PrOutput(
      issueId = "IW-238",
      phaseNumber = "01",
      prUrl = "https://github.com/org/repo/pull/42",
      headBranch = "IW-238-phase-01",
      baseBranch = "IW-238",
      merged = false
    )
    val json = output.toJson
    val parsed = ujson.read(json)
    assertEquals(parsed("merged").bool, false)

  test("empty string fields serialize as empty string, not omitted"):
    val output = PhaseOutput.StartOutput(
      issueId = "IW-238",
      phaseNumber = "01",
      branch = "",
      baselineSha = ""
    )
    val json = output.toJson
    val parsed = ujson.read(json)
    assertEquals(parsed("branch").str, "")
    assertEquals(parsed("baselineSha").str, "")

  test("PrOutput.toJson is indented (pretty-printed)"):
    val output = PhaseOutput.PrOutput(
      issueId = "IW-238",
      phaseNumber = "01",
      prUrl = "https://github.com/org/repo/pull/42",
      headBranch = "IW-238-phase-01",
      baseBranch = "IW-238",
      merged = false
    )
    val json = output.toJson
    assert(
      json.contains("\n"),
      s"Expected newlines in pretty-printed JSON, got: $json"
    )
    assert(
      json.contains("  "),
      s"Expected indentation in pretty-printed JSON, got: $json"
    )

  // AdvanceOutput tests

  test("AdvanceOutput.toJson produces valid JSON with all fields"):
    val output = PhaseOutput.AdvanceOutput(
      issueId = "IW-238",
      phaseNumber = "02",
      branch = "IW-238",
      previousBranch = "IW-238-phase-02",
      headSha = "469809c"
    )
    val json = output.toJson
    val parsed = ujson.read(json)
    assertEquals(parsed("issueId").str, "IW-238")
    assertEquals(parsed("phaseNumber").str, "02")
    assertEquals(parsed("branch").str, "IW-238")
    assertEquals(parsed("previousBranch").str, "IW-238-phase-02")
    assertEquals(parsed("headSha").str, "469809c")

  test("AdvanceOutput.toJson is pretty-printed with indent = 2"):
    val output = PhaseOutput.AdvanceOutput(
      issueId = "IW-238",
      phaseNumber = "02",
      branch = "IW-238",
      previousBranch = "IW-238-phase-02",
      headSha = "abc123"
    )
    val json = output.toJson
    assert(
      json.contains("\n"),
      s"Expected newlines in pretty-printed JSON, got: $json"
    )
    assert(
      json.contains("  "),
      s"Expected indentation in pretty-printed JSON, got: $json"
    )
