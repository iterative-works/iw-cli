// PURPOSE: Unit tests for ForgeType enum and host/remote detection logic
// PURPOSE: Verifies GitHub and GitLab detection from various host patterns

package iw.tests

import munit.FunSuite
import iw.core.model.{ForgeType, GitRemote, IssueTrackerType}

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

  test("ForgeType.fromRemote with github.com URL returns Right(GitHub)"):
    val remote = GitRemote("https://github.com/iterative-works/iw-cli.git")
    assertEquals(ForgeType.fromRemote(remote), Right(ForgeType.GitHub))

  test("ForgeType.fromRemote with gitlab.com URL returns Right(GitLab)"):
    val remote = GitRemote("https://gitlab.com/org/project.git")
    assertEquals(ForgeType.fromRemote(remote), Right(ForgeType.GitLab))

  test(
    "ForgeType.fromRemote with self-hosted gitlab URL returns Right(GitLab)"
  ):
    val remote = GitRemote("https://gitlab.e-bs.cz/iterative-works/project.git")
    assertEquals(ForgeType.fromRemote(remote), Right(ForgeType.GitLab))

  test("ForgeType.fromRemote with invalid URL returns Left"):
    val remote = GitRemote("not-a-valid-url")
    assert(ForgeType.fromRemote(remote).isLeft)

  // cliTool tests (Option[String] - None for Forgejo, Some for GitHub/GitLab)

  test("ForgeType.GitHub.cliTool is Some('gh')"):
    assertEquals(ForgeType.GitHub.cliTool, Some("gh"))

  test("ForgeType.GitLab.cliTool is Some('glab')"):
    assertEquals(ForgeType.GitLab.cliTool, Some("glab"))

  // installUrl tests (Option[String] - None for Forgejo, Some for GitHub/GitLab)

  test("ForgeType.GitHub.installUrl points to GitHub CLI"):
    assertEquals(ForgeType.GitHub.installUrl, Some("https://cli.github.com/"))

  test("ForgeType.GitLab.installUrl points to GitLab CLI"):
    assertEquals(
      ForgeType.GitLab.installUrl,
      Some("https://gitlab.com/gitlab-org/cli")
    )

  // resolve tests

  test("ForgeType.resolve uses remote when remote is available and parseable"):
    val remote = Some(GitRemote("https://github.com/org/repo.git"))
    assertEquals(
      ForgeType.resolve(remote, IssueTrackerType.GitLab),
      ForgeType.GitHub
    )

  test("ForgeType.resolve uses tracker type when no remote"):
    assertEquals(
      ForgeType.resolve(None, IssueTrackerType.GitHub),
      ForgeType.GitHub
    )

  test(
    "ForgeType.resolve falls back to GitLab when no remote and non-GitHub tracker"
  ):
    assertEquals(
      ForgeType.resolve(None, IssueTrackerType.YouTrack),
      ForgeType.GitLab
    )

  test(
    "ForgeType.resolve falls back to tracker type when remote URL is unparseable"
  ):
    val remote = Some(GitRemote("not-a-valid-url"))
    assertEquals(
      ForgeType.resolve(remote, IssueTrackerType.GitHub),
      ForgeType.GitHub
    )

  test(
    "ForgeType.resolve falls back to GitLab when remote is unparseable and tracker is non-GitHub"
  ):
    val remote = Some(GitRemote("not-a-valid-url"))
    assertEquals(
      ForgeType.resolve(remote, IssueTrackerType.Linear),
      ForgeType.GitLab
    )

  // Forgejo resolution tests

  test(
    "ForgeType.resolve with Forgejo tracker type and no remote returns Forgejo"
  ):
    assertEquals(
      ForgeType.resolve(None, IssueTrackerType.Forgejo),
      ForgeType.Forgejo
    )

  test(
    "ForgeType.resolve with Forgejo tracker type and self-hosted remote returns Forgejo (tracker wins over host)"
  ):
    val selfHostedRemote = Some(GitRemote("git@git.example.com:owner/repo.git"))
    assertEquals(
      ForgeType.resolve(selfHostedRemote, IssueTrackerType.Forgejo),
      ForgeType.Forgejo
    )

  test("ForgeType.fromHost('codeberg.org') returns Forgejo"):
    assertEquals(ForgeType.fromHost("codeberg.org"), ForgeType.Forgejo)

  // Regression: GitHub/GitLab resolution unchanged

  test(
    "ForgeType.resolve with GitHub remote and GitHub tracker returns GitHub"
  ):
    val githubRemote = Some(GitRemote("git@github.com:org/repo.git"))
    assertEquals(
      ForgeType.resolve(githubRemote, IssueTrackerType.GitHub),
      ForgeType.GitHub
    )

  test(
    "ForgeType.resolve with GitLab remote and GitLab tracker returns GitLab"
  ):
    val gitlabRemote = Some(GitRemote("git@gitlab.com:org/repo.git"))
    assertEquals(
      ForgeType.resolve(gitlabRemote, IssueTrackerType.GitLab),
      ForgeType.GitLab
    )

  test("ForgeType.resolve with no remote and GitHub tracker returns GitHub"):
    assertEquals(
      ForgeType.resolve(None, IssueTrackerType.GitHub),
      ForgeType.GitHub
    )

  test("ForgeType.resolve with no remote and GitLab tracker returns GitLab"):
    assertEquals(
      ForgeType.resolve(None, IssueTrackerType.GitLab),
      ForgeType.GitLab
    )

  test("ForgeType.Forgejo.cliTool returns None (no CLI binary)"):
    assertEquals(ForgeType.Forgejo.cliTool, None)

  test("ForgeType.Forgejo.installUrl returns None (no CLI to install)"):
    assertEquals(ForgeType.Forgejo.installUrl, None)
