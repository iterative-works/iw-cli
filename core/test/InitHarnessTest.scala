// PURPOSE: Harness tests for Init.run

package iw.core.test

import iw.core.commands.Init
import iw.core.model.{
  Constants,
  GitRemote,
  IssueTrackerType,
  ProjectConfiguration
}
import iw.core.test.fixtures.FakeCommandEnv

class InitHarnessTest extends munit.FunSuite:

  private val configPath = (env: FakeCommandEnv) =>
    env.cwd / Constants.Paths.IwDir / "config.conf"

  test("not in git repo: exit 1") {
    val env = FakeCommandEnv()
    env.git.setIsRepository(false)

    val result = Init.run(Seq.empty, env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Not in a git repository"))
    assert(env.config.writeCallList.isEmpty)
  }

  test("config already exists without --force: exit 1") {
    val env = FakeCommandEnv()
    env.fs.put(configPath(env), "old contents")

    val result = Init.run(Seq("--tracker=linear", "--team=TEST"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Configuration already exists"))
    assert(env.console.stdout.contains("--force"))
    assert(env.config.writeCallList.isEmpty)
  }

  test("--force overwrites existing config") {
    val env = FakeCommandEnv()
    env.fs.put(configPath(env), "old contents")

    val result =
      Init.run(Seq("--force", "--tracker=linear", "--team=TEST"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.config.writeCallList.size, 1)
  }

  test("invalid --tracker arg: exit 1") {
    val env = FakeCommandEnv()

    val result = Init.run(Seq("--tracker=jira"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Invalid tracker type"))
  }

  test("linear with all args: writes config without prompting") {
    val env = FakeCommandEnv(cwd = os.root / "tmp" / "myproject")

    val result = Init.run(Seq("--tracker=linear", "--team=TEAM"), env)

    assertEquals(result.exitCode, 0)
    val writes = env.config.writeCallList
    assertEquals(writes.size, 1)
    assertEquals(writes.head._2.trackerType, IssueTrackerType.Linear)
    assertEquals(writes.head._2.team, "TEAM")
    assertEquals(writes.head._2.projectName, "myproject")
    assert(env.prompt.askCallList.isEmpty)
    assert(env.console.stdout.contains("Set your API token"))
  }

  test("linear without team: prompts for team") {
    val env = FakeCommandEnv()
    env.prompt.queueAskAnswers("MYTEAM")

    val result = Init.run(Seq("--tracker=linear"), env)

    assertEquals(result.exitCode, 0)
    assertEquals(env.config.writeCallList.head._2.team, "MYTEAM")
    assert(env.prompt.askCallList.exists(_.contains("team/project")))
  }

  test("github with repository + team-prefix args: skips prompts") {
    val env = FakeCommandEnv()

    val result = Init.run(
      Seq(
        "--tracker=github",
        "--repository=owner/repo",
        "--team-prefix=ABC"
      ),
      env
    )

    assertEquals(result.exitCode, 0)
    val cfg = env.config.writeCallList.head._2
    assertEquals(cfg.trackerType, IssueTrackerType.GitHub)
    assertEquals(cfg.repository, Some("owner/repo"))
    assertEquals(cfg.teamPrefix, Some("ABC"))
    assert(env.prompt.askCallList.isEmpty)
    assert(env.console.stdout.contains("GitHub tracker configured"))
  }

  test("github invalid team-prefix arg: exit 1") {
    val env = FakeCommandEnv()

    val result = Init.run(
      Seq(
        "--tracker=github",
        "--repository=owner/repo",
        "--team-prefix=lowercase"
      ),
      env
    )

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("Invalid team prefix"))
  }

  test("github auto-detects repository from git remote when no --repository") {
    val env = FakeCommandEnv()
    env.git.setRemoteUrl(
      Some(GitRemote("git@github.com:auto/detect.git"))
    )

    val result = Init.run(
      Seq("--tracker=github", "--team-prefix=AUTO"),
      env
    )

    assertEquals(result.exitCode, 0)
    val cfg = env.config.writeCallList.head._2
    assertEquals(cfg.repository, Some("auto/detect"))
    assert(env.console.stdout.contains("Auto-detected repository"))
  }

  test("gitlab self-hosted: prompts for base URL") {
    val env = FakeCommandEnv()
    env.git.setRemoteUrl(
      Some(GitRemote("git@gitlab.company.com:owner/sample.git"))
    )
    env.prompt.queueAskAnswers("https://gitlab.company.com")

    val result = Init.run(
      Seq(
        "--tracker=gitlab",
        "--repository=owner/sample",
        "--team-prefix=PROJ"
      ),
      env
    )

    assertEquals(result.exitCode, 0)
    val cfg = env.config.writeCallList.head._2
    assertEquals(cfg.trackerBaseUrl, Some("https://gitlab.company.com"))
  }

  test("gitlab.com: no base URL prompt") {
    val env = FakeCommandEnv()
    env.git.setRemoteUrl(
      Some(GitRemote("git@gitlab.com:owner/sample.git"))
    )

    val result = Init.run(
      Seq(
        "--tracker=gitlab",
        "--repository=owner/sample",
        "--team-prefix=PROJ"
      ),
      env
    )

    assertEquals(result.exitCode, 0)
    val cfg = env.config.writeCallList.head._2
    assertEquals(cfg.trackerBaseUrl, None)
    assert(env.prompt.askCallList.isEmpty)
  }

  test("youtrack with team + base-url args: no prompts") {
    val env = FakeCommandEnv()

    val result = Init.run(
      Seq(
        "--tracker=youtrack",
        "--team=MEDH",
        "--base-url=https://youtrack.example.com"
      ),
      env
    )

    assertEquals(result.exitCode, 0)
    val cfg = env.config.writeCallList.head._2
    assertEquals(cfg.trackerType, IssueTrackerType.YouTrack)
    assertEquals(cfg.team, "MEDH")
    assertEquals(cfg.trackerBaseUrl, Some("https://youtrack.example.com"))
  }

  test("auto-detect from github remote and accept suggestion") {
    val env = FakeCommandEnv()
    env.git.setRemoteUrl(
      Some(GitRemote("git@github.com:owner/sample.git"))
    )
    env.prompt.queueAnswers(true) // accept suggested github tracker

    val result = Init.run(Seq("--team-prefix=SAMP"), env)

    assertEquals(result.exitCode, 0)
    val cfg = env.config.writeCallList.head._2
    assertEquals(cfg.trackerType, IssueTrackerType.GitHub)
    assert(env.console.stdout.contains("Detected tracker"))
  }

  test("decline suggestion: falls back to numeric prompt") {
    val env = FakeCommandEnv()
    env.git.setRemoteUrl(
      Some(GitRemote("git@github.com:owner/sample.git"))
    )
    env.prompt.queueAnswers(false) // decline suggested
    env.prompt.queueAskAnswers("1") // pick Linear

    val result = Init.run(Seq("--team=LIN"), env)

    assertEquals(result.exitCode, 0)
    val cfg = env.config.writeCallList.head._2
    assertEquals(cfg.trackerType, IssueTrackerType.Linear)
  }

  test("config write failure: exit 1") {
    val env = FakeCommandEnv()
    env.config.setWriteResult(Left("disk full"))

    val result = Init.run(Seq("--tracker=linear", "--team=T"), env)

    assertEquals(result.exitCode, 1)
    assert(env.console.stderr.contains("disk full"))
  }

  test(
    "forgejo with repository + base-url + team-prefix args: no prompts"
  ) {
    val env = FakeCommandEnv()

    val result = Init.run(
      Seq(
        "--tracker=forgejo",
        "--repository=owner/sample",
        "--base-url=https://codeberg.org",
        "--team-prefix=SAMP"
      ),
      env
    )

    assertEquals(result.exitCode, 0)
    val cfg = env.config.writeCallList.head._2
    assertEquals(cfg.trackerType, IssueTrackerType.Forgejo)
    assertEquals(cfg.repository, Some("owner/sample"))
    assertEquals(cfg.trackerBaseUrl, Some("https://codeberg.org"))
    assertEquals(cfg.teamPrefix, Some("SAMP"))
    assertEquals(cfg.team, "")
    assert(env.prompt.askCallList.isEmpty)
  }

  test("forgejo without base-url: prompts for base URL") {
    val env = FakeCommandEnv()
    env.prompt.queueAskAnswers(
      "https://codeberg.org"
    ) // only base URL; team-prefix supplied via arg

    val result = Init.run(
      Seq(
        "--tracker=forgejo",
        "--repository=owner/sample",
        "--team-prefix=SAMP"
      ),
      env
    )

    assertEquals(result.exitCode, 0)
    val cfg = env.config.writeCallList.head._2
    assertEquals(cfg.trackerBaseUrl, Some("https://codeberg.org"))
    assertEquals(env.prompt.askCallList.size, 1)
  }

  test("forgejo next steps print FORGEJO_API_TOKEN") {
    val env = FakeCommandEnv()

    val result = Init.run(
      Seq(
        "--tracker=forgejo",
        "--repository=owner/sample",
        "--base-url=https://codeberg.org",
        "--team-prefix=SAMP"
      ),
      env
    )

    assertEquals(result.exitCode, 0)
    assert(env.console.stdout.contains("FORGEJO_API_TOKEN"))
  }

  test("init auto-detects forgejo from codeberg.org remote") {
    val env = FakeCommandEnv()
    env.git.setRemoteUrl(
      Some(GitRemote("git@codeberg.org:owner/sample.git"))
    )
    env.prompt.queueAnswers(true) // accept suggested forgejo tracker
    env.prompt.queueAskAnswers(
      "owner/sample", // repository prompt
      "https://codeberg.org", // base URL prompt
      "SAMP" // team prefix suggestion (accept)
    )

    val result = Init.run(Seq.empty, env)

    assertEquals(result.exitCode, 0)
    val cfg = env.config.writeCallList.head._2
    assertEquals(cfg.trackerType, IssueTrackerType.Forgejo)
    assertEquals(cfg.repository, Some("owner/sample"))
    assertEquals(cfg.trackerBaseUrl, Some("https://codeberg.org"))
    assertEquals(cfg.teamPrefix, Some("SAMP"))
    assert(env.console.stdout.contains("Detected tracker"))
  }

  test("askForTracker menu selects forgejo") {
    val env = FakeCommandEnv()
    // No remote that auto-detects, so goes straight to menu
    env.prompt.queueAskAnswers(
      "5", // select Forgejo from menu
      "owner/sample", // repository prompt
      "https://codeberg.org", // base URL prompt
      "SAMP" // team prefix suggestion (accept)
    )

    val result = Init.run(Seq.empty, env)

    assertEquals(result.exitCode, 0)
    val cfg = env.config.writeCallList.head._2
    assertEquals(cfg.trackerType, IssueTrackerType.Forgejo)
    assertEquals(cfg.repository, Some("owner/sample"))
    assertEquals(cfg.trackerBaseUrl, Some("https://codeberg.org"))
    assertEquals(cfg.teamPrefix, Some("SAMP"))
  }
