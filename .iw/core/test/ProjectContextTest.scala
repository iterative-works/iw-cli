// PURPOSE: Unit tests for ProjectContext rendering logic
// PURPOSE: Verifies correct output for GitHub, GitLab, and edge cases

package iw.core.model

class ProjectContextTest extends munit.FunSuite:

  test("render GitHub project with all fields"):
    val input = ProjectContext.Input(
      projectName = "kanon",
      repository = Some("iterative-works/dev-docs"),
      trackerType = IssueTrackerType.GitHub,
      teamPrefix = Some("DEVDOCS"),
      forgeType = ForgeType.GitHub,
      gitlabHost = None
    )
    val output = ProjectContext.render(input)
    assert(output.contains("**Project:** kanon"))
    assert(output.contains("**Repository:** iterative-works/dev-docs"))
    assert(output.contains("**Tracker:** GitHub (prefix: DEVDOCS)"))
    assert(output.contains("### Forge: GitHub"))
    assert(output.contains("`gh`"))
    assert(output.contains("Never use `glab`"))
    assert(!output.contains("GITLAB_HOST"))

  test("render GitLab project with host"):
    val input = ProjectContext.Input(
      projectName = "my-app",
      repository = Some("company/my-app"),
      trackerType = IssueTrackerType.GitLab,
      teamPrefix = Some("APP"),
      forgeType = ForgeType.GitLab,
      gitlabHost = Some("gitlab.company.com")
    )
    val output = ProjectContext.render(input)
    assert(output.contains("**Project:** my-app"))
    assert(output.contains("**Repository:** company/my-app"))
    assert(output.contains("**Tracker:** GitLab (prefix: APP)"))
    assert(output.contains("### Forge: GitLab"))
    assert(output.contains("`glab`"))
    assert(output.contains("Never use `gh`"))
    assert(output.contains("GITLAB_HOST=gitlab.company.com"))

  test("render GitLab project without host omits GITLAB_HOST prefix in commands"):
    val input = ProjectContext.Input(
      projectName = "my-app",
      repository = Some("company/my-app"),
      trackerType = IssueTrackerType.GitLab,
      teamPrefix = None,
      forgeType = ForgeType.GitLab,
      gitlabHost = None
    )
    val output = ProjectContext.render(input)
    assert(output.contains("### Forge: GitLab"))
    assert(output.contains("`glab`"))
    assert(!output.contains("CRITICAL"))
    assert(!output.contains("GITLAB_HOST="))

  test("render without repository shows N/A"):
    val input = ProjectContext.Input(
      projectName = "test",
      repository = None,
      trackerType = IssueTrackerType.GitHub,
      teamPrefix = None,
      forgeType = ForgeType.GitHub,
      gitlabHost = None
    )
    val output = ProjectContext.render(input)
    assert(!output.contains("**Repository:**"))

  test("render without team prefix omits prefix"):
    val input = ProjectContext.Input(
      projectName = "test",
      repository = Some("owner/repo"),
      trackerType = IssueTrackerType.GitHub,
      teamPrefix = None,
      forgeType = ForgeType.GitHub,
      gitlabHost = None
    )
    val output = ProjectContext.render(input)
    assert(output.contains("**Tracker:** GitHub"))
    assert(!output.contains("prefix:"))

  test("render is deterministic"):
    val input = ProjectContext.Input(
      projectName = "test",
      repository = Some("owner/repo"),
      trackerType = IssueTrackerType.GitLab,
      teamPrefix = Some("TST"),
      forgeType = ForgeType.GitLab,
      gitlabHost = Some("gitlab.example.com")
    )
    assertEquals(ProjectContext.render(input), ProjectContext.render(input))

  test("render GitLab includes iw phase-pr handles GITLAB_HOST automatically"):
    val input = ProjectContext.Input(
      projectName = "test",
      repository = Some("owner/repo"),
      trackerType = IssueTrackerType.GitLab,
      teamPrefix = None,
      forgeType = ForgeType.GitLab,
      gitlabHost = Some("gitlab.com")
    )
    val output = ProjectContext.render(input)
    assert(output.contains("phase-pr"))
    assert(output.contains("phase-merge"))
    assert(output.contains("automatically"))

  test("fromConfig extracts all fields for GitHub"):
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.GitHub,
      projectName = "kanon",
      repository = Some("iterative-works/dev-docs"),
      teamPrefix = Some("DEVDOCS")
    )
    val remote = Some(GitRemote("git@github.com:iterative-works/dev-docs.git"))
    val input = ProjectContext.fromConfig(config, remote)
    assertEquals(input.projectName, "kanon")
    assertEquals(input.repository, Some("iterative-works/dev-docs"))
    assertEquals(input.trackerType, IssueTrackerType.GitHub)
    assertEquals(input.teamPrefix, Some("DEVDOCS"))
    assertEquals(input.forgeType, ForgeType.GitHub)
    assertEquals(input.gitlabHost, None)

  test("fromConfig extracts GitLab host from remote"):
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.GitLab,
      projectName = "my-app",
      repository = Some("company/my-app"),
      teamPrefix = Some("APP")
    )
    val remote = Some(GitRemote("git@gitlab.company.com:company/my-app.git"))
    val input = ProjectContext.fromConfig(config, remote)
    assertEquals(input.forgeType, ForgeType.GitLab)
    assertEquals(input.gitlabHost, Some("gitlab.company.com"))

  test("fromConfig with no remote falls back to tracker type for forge"):
    val config = ProjectConfiguration.create(
      trackerType = IssueTrackerType.GitLab,
      projectName = "my-app",
      repository = Some("company/my-app")
    )
    val input = ProjectContext.fromConfig(config, None)
    assertEquals(input.forgeType, ForgeType.GitLab)
    assertEquals(input.gitlabHost, None)
