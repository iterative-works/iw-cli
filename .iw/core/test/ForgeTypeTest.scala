// PURPOSE: Unit tests for ForgeType enum and host/remote detection logic
// PURPOSE: Verifies GitHub and GitLab detection from various host patterns

package iw.tests

import munit.FunSuite
import iw.core.model.{ForgeType, GitRemote}

class ForgeTypeTest extends FunSuite:

  // fromHost tests

  test("ForgeType.fromHost('github.com') returns GitHub"):
    assertEquals(ForgeType.fromHost("github.com"), ForgeType.GitHub)

  test("ForgeType.fromHost('gitlab.com') returns GitLab"):
    assertEquals(ForgeType.fromHost("gitlab.com"), ForgeType.GitLab)

  test("ForgeType.fromHost('gitlab.e-bs.cz') returns GitLab"):
    assertEquals(ForgeType.fromHost("gitlab.e-bs.cz"), ForgeType.GitLab)

  test("ForgeType.fromHost with unknown host returns GitLab"):
    assertEquals(ForgeType.fromHost("bitbucket.org"), ForgeType.GitLab)

  test("ForgeType.fromHost with empty host returns GitLab"):
    assertEquals(ForgeType.fromHost(""), ForgeType.GitLab)

  // fromRemote tests

  test("ForgeType.fromRemote with github.com URL returns GitHub"):
    val remote = GitRemote("https://github.com/iterative-works/iw-cli.git")
    assertEquals(ForgeType.fromRemote(remote), ForgeType.GitHub)

  test("ForgeType.fromRemote with gitlab.com URL returns GitLab"):
    val remote = GitRemote("https://gitlab.com/org/project.git")
    assertEquals(ForgeType.fromRemote(remote), ForgeType.GitLab)

  test("ForgeType.fromRemote with self-hosted gitlab URL returns GitLab"):
    val remote = GitRemote("https://gitlab.e-bs.cz/iterative-works/project.git")
    assertEquals(ForgeType.fromRemote(remote), ForgeType.GitLab)

  test("ForgeType.fromRemote with invalid URL returns GitLab as default"):
    val remote = GitRemote("not-a-valid-url")
    assertEquals(ForgeType.fromRemote(remote), ForgeType.GitLab)
