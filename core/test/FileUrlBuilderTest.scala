// PURPOSE: Tests for FileUrlBuilder pure URL construction
// PURPOSE: Verifies GitHub and GitLab blob URL patterns from various remote URL formats

package iw.core.test

import iw.core.model.{FileUrlBuilder, GitRemote}

class FileUrlBuilderTest extends munit.FunSuite:

  test("GitHub HTTPS remote with .git suffix produces correct blob URL"):
    val remote = GitRemote("https://github.com/owner/repo.git")
    val result = FileUrlBuilder.build(remote, "main")
    assertEquals(result, Right("https://github.com/owner/repo/blob/main/"))

  test("GitHub SSH remote produces the same HTTPS blob URL"):
    val remote = GitRemote("git@github.com:owner/repo.git")
    val result = FileUrlBuilder.build(remote, "main")
    assertEquals(result, Right("https://github.com/owner/repo/blob/main/"))

  test("GitLab HTTPS remote with .git suffix produces correct GitLab blob URL"):
    val remote = GitRemote("https://gitlab.com/owner/project.git")
    val result = FileUrlBuilder.build(remote, "main")
    assertEquals(result, Right("https://gitlab.com/owner/project/-/blob/main/"))

  test("GitLab SSH remote produces the same HTTPS GitLab blob URL"):
    val remote = GitRemote("git@gitlab.com:owner/project.git")
    val result = FileUrlBuilder.build(remote, "main")
    assertEquals(result, Right("https://gitlab.com/owner/project/-/blob/main/"))

  test("Remote URL without .git suffix still produces a correct URL"):
    val remote = GitRemote("https://github.com/owner/repo")
    val result = FileUrlBuilder.build(remote, "main")
    assertEquals(result, Right("https://github.com/owner/repo/blob/main/"))

  test("Non-github.com host uses the GitLab /-/blob/ URL pattern"):
    val remote = GitRemote("https://git.mycompany.com/owner/project.git")
    val result = FileUrlBuilder.build(remote, "develop")
    assertEquals(
      result,
      Right("https://git.mycompany.com/owner/project/-/blob/develop/")
    )

  test("Branch name is correctly interpolated into the output URL"):
    val remote = GitRemote("https://github.com/owner/repo.git")
    val result = FileUrlBuilder.build(remote, "feature/my-branch")
    assertEquals(
      result,
      Right("https://github.com/owner/repo/blob/feature/my-branch/")
    )

  test("Unrecognisable remote URL format returns Left"):
    val remote = GitRemote("not-a-valid-url")
    val result = FileUrlBuilder.build(remote, "main")
    assert(result.isLeft)
