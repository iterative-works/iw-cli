// PURPOSE: Unit tests for PhaseMerge model object
// PURPOSE: Tests pure decision functions for CI check evaluation

package iw.core.application

import munit.FunSuite
import iw.core.model.CICheckStatus
import iw.core.model.CICheckStatus.*
import iw.core.model.CICheckResult
import iw.core.model.CIVerdict
import iw.core.model.CIVerdict.*
import iw.core.model.PhaseMergeConfig
import iw.core.model.PhaseMerge

class PhaseMergeTest extends FunSuite:

  // CICheckStatus enum tests

  test("CICheckStatus has all five values"):
    val values = List(Passed, Failed, Pending, Cancelled, Unknown)
    assertEquals(values.length, 5)

  // CICheckResult case class tests

  test("CICheckResult can be constructed with name and status"):
    val check = CICheckResult("build", Passed)
    assertEquals(check.name, "build")
    assertEquals(check.status, Passed)
    assertEquals(check.url, None)

  test("CICheckResult can be constructed with name, status, and URL"):
    val check = CICheckResult("build", Failed, Some("https://ci.example.com/run/42"))
    assertEquals(check.name, "build")
    assertEquals(check.status, Failed)
    assertEquals(check.url, Some("https://ci.example.com/run/42"))

  // PhaseMergeConfig defaults tests

  test("PhaseMergeConfig default timeout is 30 minutes in milliseconds"):
    assertEquals(PhaseMergeConfig().timeoutMs, 1_800_000L)

  test("PhaseMergeConfig default poll interval is 30 seconds in milliseconds"):
    assertEquals(PhaseMergeConfig().pollIntervalMs, 30_000L)

  test("PhaseMergeConfig default max retries is 2"):
    assertEquals(PhaseMergeConfig().maxRetries, 2)

  // evaluateChecks — empty and all-passed

  test("evaluateChecks empty list returns NoChecksFound"):
    assertEquals(PhaseMerge.evaluateChecks(Nil), NoChecksFound)

  test("evaluateChecks all Passed returns AllPassed"):
    val checks = List(
      CICheckResult("build", Passed),
      CICheckResult("lint", Passed),
      CICheckResult("test", Passed)
    )
    assertEquals(PhaseMerge.evaluateChecks(checks), AllPassed)

  test("evaluateChecks single Passed returns AllPassed"):
    assertEquals(PhaseMerge.evaluateChecks(List(CICheckResult("build", Passed))), AllPassed)

  // evaluateChecks — failure scenarios

  test("evaluateChecks some Failed with others Passed returns SomeFailed carrying failed checks"):
    val failedCheck = CICheckResult("test", Failed)
    val checks = List(
      CICheckResult("build", Passed),
      failedCheck,
      CICheckResult("lint", Passed)
    )
    PhaseMerge.evaluateChecks(checks) match
      case SomeFailed(failed) =>
        assertEquals(failed, List(failedCheck))
      case other => fail(s"Expected SomeFailed but got $other")

  test("evaluateChecks all Cancelled returns SomeFailed"):
    val checks = List(
      CICheckResult("build", Cancelled),
      CICheckResult("test", Cancelled)
    )
    PhaseMerge.evaluateChecks(checks) match
      case SomeFailed(failed) =>
        assertEquals(failed.length, 2)
        assert(failed.forall(_.status == Cancelled))
      case other => fail(s"Expected SomeFailed but got $other")

  test("evaluateChecks mix of Failed and Cancelled returns SomeFailed with both"):
    val failedCheck = CICheckResult("test", Failed)
    val cancelledCheck = CICheckResult("deploy", Cancelled)
    val checks = List(
      CICheckResult("build", Passed),
      failedCheck,
      cancelledCheck
    )
    PhaseMerge.evaluateChecks(checks) match
      case SomeFailed(failed) =>
        assertEquals(failed.toSet, Set(failedCheck, cancelledCheck))
      case other => fail(s"Expected SomeFailed but got $other")

  // evaluateChecks — pending scenarios

  test("evaluateChecks some Pending with none Failed returns StillRunning"):
    val checks = List(
      CICheckResult("build", Passed),
      CICheckResult("test", Pending)
    )
    assertEquals(PhaseMerge.evaluateChecks(checks), StillRunning)

  test("evaluateChecks single Pending returns StillRunning"):
    assertEquals(PhaseMerge.evaluateChecks(List(CICheckResult("build", Pending))), StillRunning)

  // evaluateChecks — precedence

  test("evaluateChecks mix of Pending and Failed returns SomeFailed"):
    val failedCheck = CICheckResult("test", Failed)
    val checks = List(
      CICheckResult("build", Pending),
      failedCheck
    )
    PhaseMerge.evaluateChecks(checks) match
      case SomeFailed(failed) =>
        assertEquals(failed, List(failedCheck))
      case other => fail(s"Expected SomeFailed but got $other")

  // evaluateChecks — Unknown handling

  test("evaluateChecks all Unknown returns AllPassed"):
    val checks = List(
      CICheckResult("build", Unknown),
      CICheckResult("test", Unknown)
    )
    assertEquals(PhaseMerge.evaluateChecks(checks), AllPassed)

  test("evaluateChecks Unknown mixed with Passed returns AllPassed"):
    val checks = List(
      CICheckResult("build", Passed),
      CICheckResult("test", Unknown)
    )
    assertEquals(PhaseMerge.evaluateChecks(checks), AllPassed)

  test("evaluateChecks Unknown mixed with Failed returns SomeFailed"):
    val failedCheck = CICheckResult("test", Failed)
    val checks = List(
      CICheckResult("build", Unknown),
      failedCheck
    )
    PhaseMerge.evaluateChecks(checks) match
      case SomeFailed(failed) =>
        assertEquals(failed, List(failedCheck))
      case other => fail(s"Expected SomeFailed but got $other")

  // shouldRetry tests

  test("shouldRetry attempt 0 with max 2 returns true"):
    assert(PhaseMerge.shouldRetry(0, PhaseMergeConfig(maxRetries = 2)))

  test("shouldRetry attempt 1 with max 2 returns true"):
    assert(PhaseMerge.shouldRetry(1, PhaseMergeConfig(maxRetries = 2)))

  test("shouldRetry attempt 2 with max 2 returns false"):
    assert(!PhaseMerge.shouldRetry(2, PhaseMergeConfig(maxRetries = 2)))

  test("shouldRetry attempt 5 with max 2 returns false"):
    assert(!PhaseMerge.shouldRetry(5, PhaseMergeConfig(maxRetries = 2)))

  test("shouldRetry max retries 0 always returns false"):
    assert(!PhaseMerge.shouldRetry(0, PhaseMergeConfig(maxRetries = 0)))

  // buildRecoveryPrompt tests

  test("buildRecoveryPrompt single failed check includes name and status"):
    val checks = List(CICheckResult("build", Failed))
    val prompt = PhaseMerge.buildRecoveryPrompt(checks)
    assert(prompt.contains("build"), s"Expected check name in: $prompt")
    assert(prompt.contains("Failed"), s"Expected status in: $prompt")

  test("buildRecoveryPrompt multiple failed checks includes all names"):
    val checks = List(
      CICheckResult("build", Failed),
      CICheckResult("lint", Cancelled)
    )
    val prompt = PhaseMerge.buildRecoveryPrompt(checks)
    assert(prompt.contains("build"), s"Expected 'build' in: $prompt")
    assert(prompt.contains("lint"), s"Expected 'lint' in: $prompt")

  test("buildRecoveryPrompt checks with URLs includes the URLs"):
    val url = "https://ci.example.com/run/42"
    val checks = List(CICheckResult("test", Failed, Some(url)))
    val prompt = PhaseMerge.buildRecoveryPrompt(checks)
    assert(prompt.contains(url), s"Expected URL in: $prompt")

  test("buildRecoveryPrompt checks without URLs produces a valid prompt"):
    val checks = List(CICheckResult("test", Failed, None))
    val prompt = PhaseMerge.buildRecoveryPrompt(checks)
    assert(prompt.nonEmpty, "Expected non-empty prompt")
    assert(prompt.contains("test"), s"Expected check name in: $prompt")

  test("buildRecoveryPrompt formats each line as '- name: status (url)'"):
    val checks = List(
      CICheckResult("build", Failed, Some("https://ci.example.com/1")),
      CICheckResult("lint", Cancelled, None)
    )
    val expected =
      "The following CI checks failed:\n- build: Failed (https://ci.example.com/1)\n- lint: Cancelled"
    assertEquals(PhaseMerge.buildRecoveryPrompt(checks), expected)

  test("buildRecoveryPrompt empty list returns header with no check lines"):
    assertEquals(PhaseMerge.buildRecoveryPrompt(Nil), "The following CI checks failed:\n")

  // extractPrNumber — GitHub URLs

  test("extractPrNumber extracts number from standard GitHub PR URL"):
    assertEquals(PhaseMerge.extractPrNumber("https://github.com/owner/repo/pull/42"), Right(42))

  test("extractPrNumber extracts number from real GitHub org PR URL"):
    assertEquals(
      PhaseMerge.extractPrNumber("https://github.com/iterative-works/iw-cli/pull/290"),
      Right(290)
    )

  // extractPrNumber — GitLab URLs

  test("extractPrNumber extracts number from standard GitLab MR URL"):
    assertEquals(
      PhaseMerge.extractPrNumber("https://gitlab.com/group/project/-/merge_requests/15"),
      Right(15)
    )

  test("extractPrNumber extracts number from self-hosted GitLab MR URL"):
    assertEquals(
      PhaseMerge.extractPrNumber("https://git.company.com/team/project/-/merge_requests/7"),
      Right(7)
    )

  test("extractPrNumber extracts number from nested GitLab group MR URL"):
    assertEquals(
      PhaseMerge.extractPrNumber("https://gitlab.com/group/sub/project/-/merge_requests/99"),
      Right(99)
    )

  // extractPrNumber — whitespace handling

  test("extractPrNumber handles URL with trailing whitespace"):
    assertEquals(
      PhaseMerge.extractPrNumber("https://github.com/owner/repo/pull/42  "),
      Right(42)
    )

  test("extractPrNumber handles URL with leading whitespace"):
    assertEquals(
      PhaseMerge.extractPrNumber("  https://github.com/owner/repo/pull/42"),
      Right(42)
    )

  // extractPrNumber — error cases

  test("extractPrNumber returns Left for empty string"):
    assertEquals(PhaseMerge.extractPrNumber(""), Left("URL must not be blank"))

  test("extractPrNumber returns Left for whitespace-only string"):
    assertEquals(PhaseMerge.extractPrNumber("   "), Left("URL must not be blank"))

  test("extractPrNumber returns Left with input in message for non-URL string"):
    val input = "not-a-url"
    val result = PhaseMerge.extractPrNumber(input)
    assert(result.isLeft)
    assert(result.left.exists(_.contains(input)))

  test("extractPrNumber returns Left for Bitbucket URL"):
    val input = "https://bitbucket.org/owner/repo/pull-requests/42"
    val result = PhaseMerge.extractPrNumber(input)
    assert(result.isLeft)
    assert(result.left.exists(_.contains(input)))

  // parseGhChecksJson tests

  test("parseGhChecksJson all-passing checks returns Right with all Passed statuses"):
    val json = """[
      {"bucket":"pass","link":"https://ci.example.com/1","name":"test","state":"SUCCESS"},
      {"bucket":"pass","link":"https://ci.example.com/2","name":"build","state":"SUCCESS"}
    ]"""
    val result = PhaseMerge.parseGhChecksJson(json)
    assert(result.isRight)
    val checks = result.toOption.get
    assertEquals(checks.length, 2)
    assert(checks.forall(_.status == CICheckStatus.Passed))

  test("parseGhChecksJson maps SUCCESS, FAILURE, PENDING to correct statuses"):
    val json = """[
      {"bucket":"pass","link":"https://ci.example.com/1","name":"test","state":"SUCCESS"},
      {"bucket":"fail","link":"https://ci.example.com/2","name":"lint","state":"FAILURE"},
      {"bucket":"pending","link":"","name":"build","state":"PENDING"}
    ]"""
    val result = PhaseMerge.parseGhChecksJson(json)
    assert(result.isRight)
    val checks = result.toOption.get
    assertEquals(checks.find(_.name == "test").map(_.status), Some(CICheckStatus.Passed))
    assertEquals(checks.find(_.name == "lint").map(_.status), Some(CICheckStatus.Failed))
    assertEquals(checks.find(_.name == "build").map(_.status), Some(CICheckStatus.Pending))

  test("parseGhChecksJson maps CANCELLED state to CICheckStatus.Cancelled"):
    val json = """[{"bucket":"fail","link":"","name":"deploy","state":"CANCELLED"}]"""
    val result = PhaseMerge.parseGhChecksJson(json)
    assert(result.isRight)
    val checks = result.toOption.get
    assertEquals(checks.head.status, CICheckStatus.Cancelled)

  test("parseGhChecksJson maps unknown state string to CICheckStatus.Unknown"):
    val json = """[{"bucket":"pending","link":"","name":"whatever","state":"SKIPPED"}]"""
    val result = PhaseMerge.parseGhChecksJson(json)
    assert(result.isRight)
    val checks = result.toOption.get
    assertEquals(checks.head.status, CICheckStatus.Unknown)

  test("parseGhChecksJson empty array returns Right(Nil)"):
    val result = PhaseMerge.parseGhChecksJson("[]")
    assertEquals(result, Right(Nil))

  test("parseGhChecksJson invalid JSON returns Left with error"):
    val result = PhaseMerge.parseGhChecksJson("not valid json {{{")
    assert(result.isLeft)

  test("parseGhChecksJson JSON object (not array) returns Left with error"):
    val result = PhaseMerge.parseGhChecksJson("""{"name":"test"}""")
    assert(result.isLeft)

  test("parseGhChecksJson array entry missing name field returns Left with error"):
    val json = """[{"bucket":"pass","link":"https://ci.example.com/1","state":"SUCCESS"}]"""
    val result = PhaseMerge.parseGhChecksJson(json)
    assert(result.isLeft)

  test("parseGhChecksJson check with empty link returns None for url"):
    val json = """[{"bucket":"pending","link":"","name":"build","state":"PENDING"}]"""
    val result = PhaseMerge.parseGhChecksJson(json)
    assert(result.isRight)
    assertEquals(result.toOption.get.head.url, None)

  test("parseGhChecksJson check with non-empty link returns Some(link) for url"):
    val link = "https://ci.example.com/run/42"
    val json = s"""[{"bucket":"pass","link":"$link","name":"test","state":"SUCCESS"}]"""
    val result = PhaseMerge.parseGhChecksJson(json)
    assert(result.isRight)
    assertEquals(result.toOption.get.head.url, Some(link))
