// PURPOSE: Unit tests for DoctorCliFlags pure flag parsing
// PURPOSE: Absorbs the --quality/--env/--fix flag-handling scenarios from doctor.bats

package iw.core.test

import iw.core.model.DoctorCliFlags

class DoctorCliFlagsTest extends munit.FunSuite:

  test("parse with no args yields fixMode=false and no filter"):
    val flags = DoctorCliFlags.parse(Nil)
    assertEquals(flags.fixMode, false)
    assertEquals(flags.filterCategory, None)

  test("parse with --quality filters to Quality"):
    val flags = DoctorCliFlags.parse(Seq("--quality"))
    assertEquals(flags.fixMode, false)
    assertEquals(flags.filterCategory, Some("Quality"))

  test("parse with --env filters to Environment"):
    val flags = DoctorCliFlags.parse(Seq("--env"))
    assertEquals(flags.fixMode, false)
    assertEquals(flags.filterCategory, Some("Environment"))

  test("parse with --fix enables fix mode and filters to Quality"):
    val flags = DoctorCliFlags.parse(Seq("--fix"))
    assertEquals(flags.fixMode, true)
    assertEquals(flags.filterCategory, Some("Quality"))

  test("parse with --fix overrides --env (fix mode is Quality-only)"):
    val flags = DoctorCliFlags.parse(Seq("--fix", "--env"))
    assertEquals(flags.fixMode, true)
    assertEquals(flags.filterCategory, Some("Quality"))

  test("parse ignores unrelated flags"):
    val flags = DoctorCliFlags.parse(Seq("--unknown", "foo", "--quality"))
    assertEquals(flags.fixMode, false)
    assertEquals(flags.filterCategory, Some("Quality"))
