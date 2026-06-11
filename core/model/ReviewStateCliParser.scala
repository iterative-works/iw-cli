// PURPOSE: Pure parser for review-state CLI arguments (write and update subcommands)
// PURPOSE: Returns Either[String, T] for errors; sys.exit happens at the call site

package iw.core.model

object ReviewStateCliParser:

  /** Defaults that the imperative shell must supply before parsing write args
    * (issue id inferred from branch, current timestamp, current git SHA).
    */
  case class WriteDefaults(
      issueId: String,
      lastUpdated: String,
      gitSha: Option[String]
  )

  // ----- Flag extraction primitives -----

  def extractFlag(args: List[String], flag: String): Option[String] =
    val idx = args.indexOf(flag)
    if idx >= 0 && idx + 1 < args.length then Some(args(idx + 1))
    else None

  def extractRepeatedFlag(args: List[String], flag: String): List[String] =
    args
      .sliding(2)
      .collect {
        case List(`flag`, value) if !value.startsWith("--") => value
      }
      .toList

  def hasFlag(args: List[String], flag: String): Boolean =
    args.contains(flag)

  // ----- Format parsers -----

  def parseBadge(raw: String): Either[String, (String, String)] =
    val parts = raw.split(":", 2)
    if parts.length < 2 then
      Left(s"Invalid badge format '$raw', expected label:type")
    else Right((parts(0), parts(1)))

  def parseTaskList(raw: String): Either[String, (String, String)] =
    val parts = raw.split(":", 2)
    if parts.length < 2 then
      Left(s"Invalid task-list format '$raw', expected label:path")
    else Right((parts(0), parts(1)))

  def parseArtifact(
      raw: String
  ): Either[String, (String, String, Option[String])] =
    val colonIdx = raw.indexOf(':')
    if colonIdx < 0 then
      Left(
        s"Invalid artifact format '$raw', expected label:path or label:path=category"
      )
    else
      val label = raw.substring(0, colonIdx)
      val pathAndCategory = raw.substring(colonIdx + 1)
      val eqIdx = pathAndCategory.lastIndexOf('=')
      if eqIdx > 0 then
        val path = pathAndCategory.substring(0, eqIdx)
        val category = pathAndCategory.substring(eqIdx + 1)
        Right((label, path, Some(category)))
      else Right((label, pathAndCategory, None))

  def parseAction(
      raw: String
  ): Either[String, (String, String, String)] =
    val parts = raw.split(":", 3)
    if parts.length < 3 then
      Left(s"Invalid action format '$raw', expected id:label:skill")
    else Right((parts(0), parts(1), parts(2)))

  def parseCheckpoint(raw: String): Either[String, (String, String)] =
    val parts = raw.split(":", 2)
    if parts.length < 2 then
      Left(s"Invalid checkpoint format '$raw', expected phase:sha")
    else Right((parts(0), parts(1)))

  // ----- Array-mode detection (clear > replace > append) -----

  def parseArrayMode[T](
      args: List[String],
      replaceFlag: String,
      appendFlag: String,
      clearFlag: String
  )(
      parse: String => Either[String, T]
  ): Either[String, (Option[List[T]], ReviewStateUpdater.ArrayMergeMode)] =
    if hasFlag(args, clearFlag) then
      Right((Some(Nil), ReviewStateUpdater.ArrayMergeMode.Clear))
    else
      val replaceValues = extractRepeatedFlag(args, replaceFlag)
      val appendValues = extractRepeatedFlag(args, appendFlag)
      if replaceValues.nonEmpty then
        sequence(replaceValues.map(parse)).map { values =>
          (Some(values), ReviewStateUpdater.ArrayMergeMode.Replace)
        }
      else if appendValues.nonEmpty then
        sequence(appendValues.map(parse)).map { values =>
          (Some(values), ReviewStateUpdater.ArrayMergeMode.Append)
        }
      else Right((None, ReviewStateUpdater.ArrayMergeMode.Replace))

  // ----- High-level assembly -----

  def parseWriteArgs(
      args: List[String],
      defaults: WriteDefaults
  ): Either[String, ReviewStateBuilder.BuildInput] =
    val version =
      extractFlag(args, "--version").flatMap(_.toIntOption).getOrElse(2)

    val displayResult
        : Either[String, Option[(String, Option[String], String)]] =
      extractFlag(args, "--display-text") match
        case None       => Right(None)
        case Some(text) =>
          extractFlag(args, "--display-type") match
            case None =>
              Left("--display-type is required when --display-text is provided")
            case Some(displayType) =>
              val subtext = extractFlag(args, "--display-subtext")
              Right(Some((text, subtext, displayType)))

    for
      display <- displayResult
      badges <- sequence(
        extractRepeatedFlag(args, "--badge").map(parseBadge)
      )
      taskLists <- sequence(
        extractRepeatedFlag(args, "--task-list").map(parseTaskList)
      )
      artifacts <- sequence(
        extractRepeatedFlag(args, "--artifact").map(parseArtifact)
      )
      actions <- sequence(
        extractRepeatedFlag(args, "--action").map(parseAction)
      )
      checkpoints <- sequence(
        extractRepeatedFlag(args, "--checkpoint").map(parseCheckpoint)
      )
    yield ReviewStateBuilder.BuildInput(
      version = version,
      issueId = extractFlag(args, "--issue-id").getOrElse(defaults.issueId),
      lastUpdated = defaults.lastUpdated,
      artifacts = artifacts,
      status = extractFlag(args, "--status"),
      display = display,
      badges = badges,
      taskLists = taskLists,
      needsAttention =
        if hasFlag(args, "--needs-attention") then Some(true) else None,
      message = extractFlag(args, "--message"),
      actions = actions,
      prUrl = extractFlag(args, "--pr-url"),
      gitSha = defaults.gitSha,
      phaseCheckpoints = checkpoints.toMap,
      activity = extractFlag(args, "--activity"),
      workflowType = extractFlag(args, "--workflow-type")
    )

  def parseUpdateArgs(
      args: List[String]
  ): Either[String, ReviewStateUpdater.UpdateInput] =
    for
      artifactsField <- parseArrayMode(
        args,
        "--artifact",
        "--append-artifact",
        "--clear-artifacts"
      )(parseArtifact)
      badgesField <- parseArrayMode(
        args,
        "--badge",
        "--append-badge",
        "--clear-badges"
      )(parseBadge)
      taskListsField <- parseArrayMode(
        args,
        "--task-list",
        "--append-task-list",
        "--clear-task-lists"
      )(parseTaskList)
      actionsField <- parseArrayMode(
        args,
        "--action",
        "--append-action",
        "--clear-actions"
      )(parseAction)
      checkpointsField <- parseArrayMode(
        args,
        "--checkpoint",
        "--append-checkpoint",
        "--clear-checkpoints"
      )(parseCheckpoint)
    yield ReviewStateUpdater.UpdateInput(
      status = extractFlag(args, "--status"),
      message = extractFlag(args, "--message"),
      needsAttention =
        if hasFlag(args, "--needs-attention") then Some(true) else None,
      prUrl = extractFlag(args, "--pr-url"),
      gitSha = extractFlag(args, "--git-sha"),
      activity = extractFlag(args, "--activity"),
      workflowType = extractFlag(args, "--workflow-type"),
      displayText = extractFlag(args, "--display-text"),
      displaySubtext = extractFlag(args, "--display-subtext"),
      displayType = extractFlag(args, "--display-type"),
      clearDisplaySubtext = hasFlag(args, "--clear-display-subtext"),
      artifacts = artifactsField._1,
      artifactsMode = artifactsField._2,
      badges = badgesField._1,
      badgesMode = badgesField._2,
      taskLists = taskListsField._1,
      taskListsMode = taskListsField._2,
      actions = actionsField._1,
      actionsMode = actionsField._2,
      phaseCheckpoints = checkpointsField._1.map(_.toMap),
      phaseCheckpointsMode = checkpointsField._2,
      clearStatus = hasFlag(args, "--clear-status"),
      clearMessage = hasFlag(args, "--clear-message"),
      clearNeedsAttention = hasFlag(args, "--clear-needs-attention"),
      clearPrUrl = hasFlag(args, "--clear-pr-url"),
      clearDisplay = hasFlag(args, "--clear-display"),
      clearActivity = hasFlag(args, "--clear-activity"),
      clearWorkflowType = hasFlag(args, "--clear-workflow-type")
    )

  // ----- helpers -----

  private def sequence[T](
      results: List[Either[String, T]]
  ): Either[String, List[T]] =
    results.foldRight[Either[String, List[T]]](Right(Nil)) { (cur, acc) =>
      for
        h <- cur
        t <- acc
      yield h :: t
    }
