// PURPOSE: Unit tests for RecoveryAction trait and RecoveryContext case class
// PURPOSE: Verifies context construction and trait contract
package iw.tests

import iw.core.model.{
  CICheckResult,
  CICheckStatus,
  RecoveryAction,
  RecoveryContext
}
import munit.FunSuite

class RecoveryActionTest extends FunSuite:

  test("RecoveryContext holds all fields"):
    val checks = List(
      CICheckResult("build", CICheckStatus.Failed, Some("http://ci/1"))
    )
    val ctx = RecoveryContext(
      failedChecks = checks,
      prUrl = "https://github.com/org/repo/pull/42",
      branch = "IW-100/phase-01",
      attempt = 0,
      maxRetries = 2
    )
    assertEquals(ctx.failedChecks, checks)
    assertEquals(ctx.prUrl, "https://github.com/org/repo/pull/42")
    assertEquals(ctx.branch, "IW-100/phase-01")
    assertEquals(ctx.attempt, 0)
    assertEquals(ctx.maxRetries, 2)

  test("RecoveryAction implementation returns exit code"):
    val action = new RecoveryAction:
      def recover(ctx: RecoveryContext): Int = 0

    val ctx = RecoveryContext(
      failedChecks = Nil,
      prUrl = "https://github.com/org/repo/pull/1",
      branch = "main",
      attempt = 0,
      maxRetries = 1
    )
    assertEquals(action.recover(ctx), 0)
