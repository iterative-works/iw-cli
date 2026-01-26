// PURPOSE: Comprehensive tests for GitRemote.host method
package iw.tests

// PURPOSE: Verifies host extraction from various git URL formats and error handling
import iw.core.model.GitRemote

class GitRemoteTest extends munit.FunSuite:

  test("SSH format extracts host correctly"):
    val remote = GitRemote("git@github.com:user/repo.git")
    assertEquals(remote.host, Right("github.com"))

  test("SSH with subdomain extracts host correctly"):
    val remote = GitRemote("git@gitlab.company.com:team/project.git")
    assertEquals(remote.host, Right("gitlab.company.com"))

  test("HTTPS format extracts host correctly"):
    val remote = GitRemote("https://github.com/user/repo.git")
    assertEquals(remote.host, Right("github.com"))

  test("HTTP format extracts host correctly"):
    val remote = GitRemote("http://github.com/user/repo.git")
    assertEquals(remote.host, Right("github.com"))

  test("SSH URL without .git suffix extracts host correctly"):
    val remote = GitRemote("git@github.com:user/repo")
    assertEquals(remote.host, Right("github.com"))

  test("HTTPS URL without .git suffix extracts host correctly"):
    val remote = GitRemote("https://github.com/user/repo")
    assertEquals(remote.host, Right("github.com"))

  test("HTTPS with subdomain extracts host correctly"):
    val remote = GitRemote("https://gitlab.e-bs.cz/iterative-works/kanon.git")
    assertEquals(remote.host, Right("gitlab.e-bs.cz"))

  test("SSH with complex subdomain extracts host correctly"):
    val remote = GitRemote("git@git.internal.company.example.com:org/project.git")
    assertEquals(remote.host, Right("git.internal.company.example.com"))

  test("unsupported format returns Left with error"):
    val remote = GitRemote("ftp://github.com/user/repo.git")
    assert(remote.host.isLeft)
    remote.host match
      case Left(error) => assert(error.contains("Unsupported git URL format"))
      case Right(_) => fail("Expected Left but got Right")

  test("empty URL returns Left with error"):
    val remote = GitRemote("")
    assert(remote.host.isLeft)
    remote.host match
      case Left(error) => assert(error.contains("Unsupported git URL format"))
      case Right(_) => fail("Expected Left but got Right")

  test("malformed SSH URL without colon returns Left"):
    val remote = GitRemote("git@github.com/user/repo.git")
    assert(remote.host.isLeft)
    remote.host match
      case Left(error) => assert(error.contains("Unsupported git URL format"))
      case Right(_) => fail("Expected Left but got Right")

  test("malformed HTTPS URL without slashes returns Left"):
    val remote = GitRemote("https:github.com/user/repo.git")
    assert(remote.host.isLeft)
    remote.host match
      case Left(error) => assert(error.contains("Unsupported git URL format"))
      case Right(_) => fail("Expected Left but got Right")

  test("URL with only protocol returns Left"):
    val remote = GitRemote("https://")
    assert(remote.host.isLeft)

  test("SSH format with only git@ prefix returns Left"):
    val remote = GitRemote("git@")
    assert(remote.host.isLeft)

  test("random text returns Left"):
    val remote = GitRemote("not-a-valid-url")
    assert(remote.host.isLeft)
