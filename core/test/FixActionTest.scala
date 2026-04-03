// PURPOSE: Unit tests for FixAction trait and DoctorFixContext case class
// PURPOSE: Verifies context construction and trait contract
package iw.tests

import iw.core.model.{
  BuildSystem,
  DoctorFixContext,
  FixAction,
  IssueTrackerType,
  ProjectConfiguration
}
import munit.FunSuite

class FixActionTest extends FunSuite:

  val testConfig: ProjectConfiguration =
    ProjectConfiguration.create(IssueTrackerType.GitHub, "TEST", "test-project")

  test("DoctorFixContext holds all fields"):
    val ctx = DoctorFixContext(
      failedChecks = List("check1", "check2"),
      buildSystem = BuildSystem.Mill,
      ciPlatform = "GitHub Actions",
      config = testConfig
    )
    assertEquals(ctx.failedChecks, List("check1", "check2"))
    assertEquals(ctx.buildSystem, BuildSystem.Mill)
    assertEquals(ctx.ciPlatform, "GitHub Actions")
    assertEquals(ctx.config.team, "TEST")

  test("FixAction implementation returns exit code"):
    val action = new FixAction:
      def fix(ctx: DoctorFixContext): Int =
        if ctx.failedChecks.isEmpty then 0 else 1

    val ctxWithFailures = DoctorFixContext(
      List("check1"),
      BuildSystem.SBT,
      "GitHub Actions",
      testConfig
    )
    val ctxNoFailures = DoctorFixContext(
      Nil,
      BuildSystem.SBT,
      "GitHub Actions",
      testConfig
    )
    assertEquals(action.fix(ctxWithFailures), 1)
    assertEquals(action.fix(ctxNoFailures), 0)
