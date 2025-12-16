// PURPOSE: Unit tests for Constants object containing all magic strings
// PURPOSE: Tests that constants are accessible and have expected values
package iw.tests

import iw.core.*
import munit.FunSuite

class ConstantsTest extends FunSuite:
  test("Constants.EnvVars contains LINEAR_API_TOKEN"):
    assertEquals(Constants.EnvVars.LinearApiToken, "LINEAR_API_TOKEN")

  test("Constants.EnvVars contains YOUTRACK_API_TOKEN"):
    assertEquals(Constants.EnvVars.YouTrackApiToken, "YOUTRACK_API_TOKEN")

  test("Constants.EnvVars contains IW_HOOK_CLASSES"):
    assertEquals(Constants.EnvVars.IwHookClasses, "IW_HOOK_CLASSES")

  test("Constants.EnvVars contains TMUX"):
    assertEquals(Constants.EnvVars.Tmux, "TMUX")

  test("Constants.ConfigKeys contains tracker.type"):
    assertEquals(Constants.ConfigKeys.TrackerType, "tracker.type")

  test("Constants.ConfigKeys contains tracker.team"):
    assertEquals(Constants.ConfigKeys.TrackerTeam, "tracker.team")

  test("Constants.ConfigKeys contains tracker.baseUrl"):
    assertEquals(Constants.ConfigKeys.TrackerBaseUrl, "tracker.baseUrl")

  test("Constants.ConfigKeys contains project.name"):
    assertEquals(Constants.ConfigKeys.ProjectName, "project.name")

  test("Constants.ConfigKeys contains version"):
    assertEquals(Constants.ConfigKeys.Version, "version")

  test("Constants.Paths contains config file path"):
    assertEquals(Constants.Paths.ConfigFile, ".iw/config.conf")

  test("Constants.Paths contains .iw directory"):
    assertEquals(Constants.Paths.IwDir, ".iw")

  test("Constants.SystemProps contains user.dir"):
    assertEquals(Constants.SystemProps.UserDir, "user.dir")

  test("Constants.ScalaReflection contains MODULE$ field name"):
    assertEquals(Constants.ScalaReflection.ModuleField, "MODULE$")

  test("Constants.TrackerTypeValues contains linear"):
    assertEquals(Constants.TrackerTypeValues.Linear, "linear")

  test("Constants.TrackerTypeValues contains youtrack"):
    assertEquals(Constants.TrackerTypeValues.YouTrack, "youtrack")

  test("Constants.Encoding contains UTF-8"):
    assertEquals(Constants.Encoding.Utf8, "UTF-8")
