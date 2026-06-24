// PURPOSE: Harness tests for Issue.run (fetch + create subcommand)

package iw.core.test

import iw.core.adapters.CreatedIssue
import iw.core.commands.Issue as IssueCmd
import iw.core.model.{Constants, Issue, IssueId}
import iw.core.test.fixtures.FakeCommandEnv

class IssueHarnessTest extends munit.FunSuite:

  private val linearConfig =
    """project { name = sample }
      |tracker { type = linear, team = TEST }
      |""".stripMargin

  private val githubConfigWithRepo =
    """project { name = sample }
      |tracker {
      |  type = github
      |  repository = "owner/sample"
      |  teamPrefix = "SAMP"
      |}
      |""".stripMargin

  private val githubConfigNoPrefix =
    """project { name = sample }
      |tracker {
      |  type = github
      |  repository = "owner/sample"
      |}
      |""".stripMargin

  private val youtrackConfig =
    """project { name = sample }
      |tracker {
      |  type = youtrack
      |  team = TEAM
      |  baseUrl = "https://youtrack.example.com"
      |}
      |""".stripMargin

  private val gitlabConfig =
    """project { name = sample }
      |tracker {
      |  type = gitlab
      |  repository = "owner/sample"
      |  teamPrefix = "SAMP"
      |}
      |""".stripMargin

  private def seedConfig(env: FakeCommandEnv, config: String): Unit =
    env.fs.put(env.cwd / ".iw" / "config.conf", config)

  test("no config: exit 1 with init hint") {
    val env = FakeCommandEnv()

    val result = IssueCmd.run(Seq("TEST-1"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Configuration file not found"))
  }

  test("linear fetch without token: exit 1") {
    val env = FakeCommandEnv()
    seedConfig(env, linearConfig)

    val result = IssueCmd.run(Seq("TEST-1"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains(Constants.EnvVars.LinearApiToken))
  }

  test("linear fetch happy path: formats issue and updates last-seen") {
    val env = FakeCommandEnv()
    seedConfig(env, linearConfig)
    env.envVars.setEnv(Constants.EnvVars.LinearApiToken, "lin-token")
    env.tracker.setFetchIssueResult(
      Right(Issue("TEST-7", "Bug", "Open", None, Some("body")))
    )

    val result = IssueCmd.run(Seq("TEST-7"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.tracker.fetchIssueCallList.size, 1)
    assertEquals(env.tracker.fetchIssueCallList.head.kind, "linear")
    assert(env.console.stdout.contains("Bug"))
    assertEquals(env.server.updateLastSeenCallList.size, 1)
  }

  test("no args: infers issue id from current branch") {
    val env = FakeCommandEnv(initialBranch = "TEST-100")
    seedConfig(env, linearConfig)
    env.envVars.setEnv(Constants.EnvVars.LinearApiToken, "tok")

    val result = IssueCmd.run(Seq.empty, env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.tracker.fetchIssueCallList.head.issueId, "TEST-100")
  }

  test("github fetch happy path: passes full issue id to client") {
    val env = FakeCommandEnv()
    seedConfig(env, githubConfigWithRepo)
    env.tracker.setFetchIssueResult(
      Right(Issue("SAMP-9", "Hi", "Open", None, None))
    )

    val result = IssueCmd.run(Seq("SAMP-9"), env)

    assertEquals(result.exitCode, 0)
    val call = env.tracker.fetchIssueCallList.head
    assertEquals(call.kind, "github")
    assertEquals(call.issueId, "SAMP-9")
    assertEquals(call.extras("repository"), "owner/sample")
  }

  test("github fetch: numeric arg composes team prefix from config") {
    val env = FakeCommandEnv()
    seedConfig(env, githubConfigWithRepo)

    val result = IssueCmd.run(Seq("42"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.tracker.fetchIssueCallList.head.issueId, "SAMP-42")
  }

  test("github fetch without configured repository: exit 1") {
    val env = FakeCommandEnv()
    seedConfig(
      env,
      """project { name = sample }
        |tracker { type = github }
        |""".stripMargin
    )

    val result = IssueCmd.run(Seq("SAMP-1"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("GitHub repository not configured"))
  }

  test("github fetch numeric without team prefix: helpful error from IssueId") {
    val env = FakeCommandEnv()
    seedConfig(env, githubConfigNoPrefix)

    val result = IssueCmd.run(Seq("42"), env)

    assertEquals(result.exitCode, 1)
    // Error message comes from IssueId parse
    assert(env.console.stderr.contains("42"))
  }

  test("youtrack fetch without base url: exit 1") {
    val env = FakeCommandEnv()
    seedConfig(
      env,
      """project { name = sample }
        |tracker { type = youtrack, team = SAMP }
        |""".stripMargin
    )
    env.envVars.setEnv(Constants.EnvVars.YouTrackApiToken, "yt-token")

    val result = IssueCmd.run(Seq("SAMP-1"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("baseUrl"))
  }

  test("youtrack fetch happy path: passes base url + token") {
    val env = FakeCommandEnv()
    seedConfig(env, youtrackConfig)
    env.envVars.setEnv(Constants.EnvVars.YouTrackApiToken, "yt-token")

    val result = IssueCmd.run(Seq("TEAM-1"), env)

    assertEquals(result.exitCode, 0)
    val call = env.tracker.fetchIssueCallList.head
    assertEquals(call.kind, "youtrack")
    assertEquals(call.extras("baseUrl"), "https://youtrack.example.com")
    assertEquals(call.extras("token"), "yt-token")
  }

  test("issue create: missing --title returns exit 1 with help banner") {
    val env = FakeCommandEnv()
    seedConfig(env, githubConfigWithRepo)

    val result = IssueCmd.run(Seq("create"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stdout.contains("iw issue create"))
  }

  test("issue create --help: prints help, exit 0") {
    val env = FakeCommandEnv()

    val result = IssueCmd.run(Seq("create", "--help"), env)

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("Create a new issue"))
  }

  test("issue create for GitHub: routes to createGitHubIssue") {
    val env = FakeCommandEnv()
    seedConfig(env, githubConfigWithRepo)
    env.tracker.setCreateIssueResult(
      Right(CreatedIssue("17", "https://example.com/issues/17"))
    )

    val result = IssueCmd.run(
      Seq("create", "--title", "Hello world", "--description", "Body"),
      env
    )

    assertEquals(result.exitCode, 0)
    val call = env.tracker.createIssueCallList.head
    assertEquals(call.kind, "github")
    assertEquals(call.title, "Hello world")
    assertEquals(call.description, "Body")
    assertEquals(call.extras("repository"), "owner/sample")
    assert(env.console.stdout.contains("Issue created: #17"))
  }

  test("issue create for Linear: requires token, then succeeds") {
    val env = FakeCommandEnv()
    seedConfig(env, linearConfig)
    env.envVars.setEnv(Constants.EnvVars.LinearApiToken, "lt")

    val result = IssueCmd.run(Seq("create", "--title", "Linear thing"), env)

    assertEquals(result.exitCode, 0)
    val call = env.tracker.createIssueCallList.head
    assertEquals(call.kind, "linear")
    assertEquals(call.extras("teamId"), "TEST")
    assertEquals(call.extras("token"), "lt")
  }

  test("issue create for GitLab: forwards gitlab host from remote") {
    val env = FakeCommandEnv()
    seedConfig(env, gitlabConfig)
    env.git.setRemoteUrl(
      Some(iw.core.model.GitRemote("git@gitlab.example.com:owner/sample.git"))
    )

    val result = IssueCmd.run(Seq("create", "--title", "gl"), env)

    assertEquals(result.exitCode, 0)
    val call = env.tracker.createIssueCallList.head
    assertEquals(call.kind, "gitlab")
    assertEquals(call.extras("gitlabHost"), "gitlab.example.com")
  }

  test("tracker error propagates with formatted prefix") {
    val env = FakeCommandEnv()
    seedConfig(env, githubConfigWithRepo)
    env.tracker.setCreateIssueResult(Left("rate limited"))

    val result = IssueCmd.run(Seq("create", "--title", "x"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Failed to create issue"))
    assert(env.console.stderr.contains("rate limited"))
  }

  private val forgejoConfig =
    """project { name = sample }
      |tracker {
      |  type = forgejo
      |  repository = "owner/sample"
      |  teamPrefix = "SAMP"
      |  baseUrl = "https://forgejo.example.com"
      |}
      |""".stripMargin

  test(
    "forgejo fetch without token: exit 1, stderr mentions FORGEJO_API_TOKEN"
  ) {
    val env = FakeCommandEnv()
    seedConfig(env, forgejoConfig)

    val result = IssueCmd.run(Seq("SAMP-1"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains(Constants.EnvVars.ForgejoApiToken))
  }

  test("forgejo fetch without baseUrl: exit 1, stderr mentions baseUrl") {
    val env = FakeCommandEnv()
    seedConfig(
      env,
      """project { name = sample }
        |tracker {
        |  type = forgejo
        |  repository = "owner/sample"
        |  teamPrefix = "SAMP"
        |}
        |""".stripMargin
    )
    env.envVars.setEnv(Constants.EnvVars.ForgejoApiToken, "fg-token")

    val result = IssueCmd.run(Seq("SAMP-1"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("baseUrl"))
  }

  test(
    "forgejo fetch without repository: exit 1, stderr mentions Forgejo repository not configured"
  ) {
    val env = FakeCommandEnv()
    seedConfig(
      env,
      """project { name = sample }
        |tracker {
        |  type = forgejo
        |  baseUrl = "https://forgejo.example.com"
        |}
        |""".stripMargin
    )
    env.envVars.setEnv(Constants.EnvVars.ForgejoApiToken, "fg-token")

    val result = IssueCmd.run(Seq("SAMP-1"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Forgejo repository not configured"))
  }

  test(
    "forgejo fetch happy path: passes repository, baseUrl, token; updates last-seen"
  ) {
    val env = FakeCommandEnv()
    seedConfig(env, forgejoConfig)
    env.envVars.setEnv(Constants.EnvVars.ForgejoApiToken, "fg-token")
    env.tracker.setFetchIssueResult(
      Right(Issue("SAMP-1", "Forgejo bug", "Open", None, Some("body")))
    )

    val result = IssueCmd.run(Seq("SAMP-1"), env)

    assertEquals(result.exitCode, 0)
    val call = env.tracker.fetchIssueCallList.head
    assertEquals(call.kind, "forgejo")
    assertEquals(call.extras("repository"), "owner/sample")
    assertEquals(call.extras("baseUrl"), "https://forgejo.example.com")
    assertEquals(call.extras("token"), "fg-token")
    assertEquals(env.server.updateLastSeenCallList.size, 1)
  }

  test("forgejo fetch: numeric arg composes team prefix from config") {
    val env = FakeCommandEnv()
    seedConfig(env, forgejoConfig)
    env.envVars.setEnv(Constants.EnvVars.ForgejoApiToken, "fg-token")

    val result = IssueCmd.run(Seq("42"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.tracker.fetchIssueCallList.head.issueId, "SAMP-42")
  }

  test("forgejo fetch: adapter Left surfaces as exit 1 with error text") {
    val env = FakeCommandEnv()
    seedConfig(env, forgejoConfig)
    env.envVars.setEnv(Constants.EnvVars.ForgejoApiToken, "fg-token")
    env.tracker.setFetchIssueResult(Left("boom"))

    val result = IssueCmd.run(Seq("SAMP-1"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("boom"))
  }

  test("forgejo create happy path: routes to createForgejoIssue with extras") {
    val env = FakeCommandEnv()
    seedConfig(env, forgejoConfig)
    env.envVars.setEnv(Constants.EnvVars.ForgejoApiToken, "fg-token")
    env.tracker.setCreateIssueResult(
      Right(
        CreatedIssue("7", "https://forgejo.example.com/owner/sample/issues/7")
      )
    )

    val result = IssueCmd.run(
      Seq("create", "--title", "Forgejo task", "--description", "Details"),
      env
    )

    assertEquals(result.exitCode, 0)
    val call = env.tracker.createIssueCallList.head
    assertEquals(call.kind, "forgejo")
    assertEquals(call.title, "Forgejo task")
    assertEquals(call.description, "Details")
    assertEquals(call.extras("repository"), "owner/sample")
    assertEquals(call.extras("baseUrl"), "https://forgejo.example.com")
    assertEquals(call.extras("token"), "fg-token")
    assert(env.console.stdout.contains("Issue created"))
  }

  test("forgejo create without token: exit 1") {
    val env = FakeCommandEnv()
    seedConfig(env, forgejoConfig)

    val result = IssueCmd.run(Seq("create", "--title", "Needs token"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains(Constants.EnvVars.ForgejoApiToken))
  }

  test("forgejo create without baseUrl: exit 1, stderr mentions baseUrl") {
    val env = FakeCommandEnv()
    seedConfig(
      env,
      """project { name = sample }
        |tracker {
        |  type = forgejo
        |  repository = "owner/sample"
        |}
        |""".stripMargin
    )
    env.envVars.setEnv(Constants.EnvVars.ForgejoApiToken, "fg-token")

    val result = IssueCmd.run(Seq("create", "--title", "No URL"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("baseUrl"))
  }

  test(
    "forgejo create without repository: exit 1, stderr mentions Forgejo repository not configured"
  ) {
    val env = FakeCommandEnv()
    seedConfig(
      env,
      """project { name = sample }
        |tracker {
        |  type = forgejo
        |  baseUrl = "https://forgejo.example.com"
        |}
        |""".stripMargin
    )
    env.envVars.setEnv(Constants.EnvVars.ForgejoApiToken, "fg-token")

    val result = IssueCmd.run(Seq("create", "--title", "No repo"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Forgejo repository not configured"))
  }
