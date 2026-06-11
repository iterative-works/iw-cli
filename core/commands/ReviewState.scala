// PURPOSE: review-state command logic: validate/write/update subcommands routed via dispatcher
// PURPOSE: All I/O via CommandEnv (FileSystem + GitOps + Stdin + Clock) so it runs in-VM under tests

package iw.core.commands

import iw.core.model.{
  IssueId,
  ReviewStateBuilder,
  ReviewStateCliParser,
  ReviewStateUpdater,
  ReviewStateValidator
}

object ReviewState:
  def run(args: Seq[String], env: CommandEnv): CommandResult =
    if args.isEmpty then
      env.console.err("Error: No subcommand provided")
      showHelp(env)
      CommandResult.error
    else
      args.head match
        case "--help" | "-h" =>
          showHelp(env)
          CommandResult.ok
        case "validate" => validate(args.tail.toList, env)
        case "write"    => write(args.tail.toList, env)
        case "update"   => update(args.tail.toList, env)
        case other      =>
          env.console.err(s"Error: Unknown subcommand: $other")
          env.console.out("Available subcommands: validate, write, update")
          CommandResult.error

  private def showHelp(env: CommandEnv): Unit =
    env.console.out("Usage: iw review-state <subcommand> [args...]")
    env.console.out("Available subcommands:")
    env.console.out("  validate <path>    Validate review-state.json file")
    env.console.out(
      "  write [options]    Create new review-state.json from scratch"
    )
    env.console.out("  update [options]   Update existing review-state.json")

  // ----- validate -----

  private def validate(args: List[String], env: CommandEnv): CommandResult =
    if args.contains("--help") || args.contains("-h") then
      env.console.out(
        "Validate a review-state.json file against the formal schema"
      )
      CommandResult.ok
    else
      readValidateInput(args, env) match
        case Left(err) =>
          env.console.err(s"Error: $err")
          CommandResult.error
        case Right(json) =>
          val result = ReviewStateValidator.validate(json)
          result.warnings.foreach(w => env.console.out(s"Warning: $w"))
          if result.isValid then
            env.console.out("✓ Review state is valid")
            CommandResult.ok
          else
            env.console.err("Error: Review state validation failed")
            result.errors.foreach(e =>
              env.console.out(s"  ${e.field}: ${e.message}")
            )
            CommandResult.error

  private def readValidateInput(
      args: List[String],
      env: CommandEnv
  ): Either[String, String] =
    if args.contains("--stdin") then Right(env.stdin.read())
    else
      args.filterNot(_.startsWith("--")).headOption match
        case None =>
          Left(
            "No file path provided. Usage: iw review-state validate <file-path> or --stdin"
          )
        case Some(p) =>
          val path = os.Path(p, env.cwd)
          if !env.fs.exists(path) then Left(s"File not found: $path")
          else env.fs.read(path)

  // ----- write -----

  private def write(args: List[String], env: CommandEnv): CommandResult =
    if args.contains("--help") || args.contains("-h") then
      env.console.out(
        "Write a validated review-state.json file from CLI flags or stdin"
      )
      CommandResult.ok
    else if args.contains("--from-stdin") then writeFromStdin(args, env)
    else writeFromFlags(args, env)

  private def writeFromStdin(
      args: List[String],
      env: CommandEnv
  ): CommandResult =
    val json = env.stdin.read()
    val validation = ReviewStateValidator.validate(json)
    if !validation.isValid then
      env.console.err("Error: Validation failed for stdin input")
      validation.errors.foreach(e =>
        env.console.out(s"  ${e.field}: ${e.message}")
      )
      CommandResult.error
    else
      validation.warnings.foreach(w => env.console.out(s"Warning: $w"))
      ReviewStateCliParser.extractFlag(args, "--output") match
        case None =>
          env.console.err("Error: --output is required with --from-stdin")
          CommandResult.error
        case Some(p) =>
          val outputPath = os.Path(p, env.cwd)
          finalizeWrite(
            args,
            json,
            outputPath,
            resolveIssueIdOrUnknown(args, env),
            env
          )

  private def writeFromFlags(
      args: List[String],
      env: CommandEnv
  ): CommandResult =
    resolveIssueId(args, env) match
      case Left(err) =>
        env.console.err(s"Error: $err")
        env.console.out("Use --issue-id to specify explicitly")
        CommandResult.error
      case Right(issueId) =>
        val defaults = ReviewStateCliParser.WriteDefaults(
          issueId = issueId,
          lastUpdated = java.time.Instant.ofEpochMilli(env.clock.now).toString,
          gitSha = env.git.getHeadSha(env.cwd).toOption
        )
        ReviewStateCliParser.parseWriteArgs(args, defaults) match
          case Left(err) =>
            env.console.err(s"Error: $err")
            CommandResult.error
          case Right(input) =>
            val json = ReviewStateBuilder.build(input)
            val validation = ReviewStateValidator.validate(json)
            if !validation.isValid then
              env.console.err("Error: Built review state failed validation")
              validation.errors.foreach(e =>
                env.console.out(s"  ${e.field}: ${e.message}")
              )
              CommandResult.error
            else
              validation.warnings.foreach(w => env.console.out(s"Warning: $w"))
              val outputPath =
                ReviewStateCliParser.extractFlag(args, "--output") match
                  case Some(p) => os.Path(p, env.cwd)
                  case None    =>
                    env.cwd / "project-management" / "issues" / issueId /
                      "review-state.json"
              finalizeWrite(args, json, outputPath, issueId, env)

  private def finalizeWrite(
      args: List[String],
      json: String,
      outputPath: os.Path,
      issueId: String,
      env: CommandEnv
  ): CommandResult =
    env.fs.write(outputPath, json) match
      case Left(err) =>
        env.console.err(s"Error: $err")
        CommandResult.error
      case Right(()) =>
        commitIfRequested(args, issueId, outputPath, env)
        env.console.out(s"✓ Review state written to $outputPath")
        CommandResult.ok

  // ----- update -----

  private def update(args: List[String], env: CommandEnv): CommandResult =
    if args.contains("--help") || args.contains("-h") then
      env.console.out(
        "Update an existing review-state.json file with partial changes"
      )
      CommandResult.ok
    else
      resolveIssueId(args, env) match
        case Left(err) =>
          env.console.err(s"Error: $err")
          env.console.out("Use --issue-id to specify explicitly")
          CommandResult.error
        case Right(issueId) =>
          val inputPath =
            ReviewStateCliParser.extractFlag(args, "--input") match
              case Some(p) => os.Path(p, env.cwd)
              case None    =>
                env.cwd / "project-management" / "issues" / issueId /
                  "review-state.json"
          if !env.fs.exists(inputPath) then
            env.console.err(s"Error: Review state file not found: $inputPath")
            env.console.out("Use --input to specify a different path")
            CommandResult.error
          else
            env.fs.read(inputPath) match
              case Left(err) =>
                env.console.err(s"Error: $err")
                CommandResult.error
              case Right(existing) =>
                ReviewStateCliParser.parseUpdateArgs(args) match
                  case Left(err) =>
                    env.console.err(s"Error: $err")
                    CommandResult.error
                  case Right(updateInput) =>
                    val merged = ReviewStateUpdater.merge(existing, updateInput)
                    val validation = ReviewStateValidator.validate(merged)
                    if !validation.isValid then
                      env.console.err(
                        "Error: Updated review state failed validation"
                      )
                      validation.errors.foreach(e =>
                        env.console.out(s"  ${e.field}: ${e.message}")
                      )
                      CommandResult.error
                    else
                      validation.warnings.foreach(w =>
                        env.console.out(s"Warning: $w")
                      )
                      env.fs.write(inputPath, merged) match
                        case Left(err) =>
                          env.console.err(s"Error: $err")
                          CommandResult.error
                        case Right(()) =>
                          commitIfRequested(args, issueId, inputPath, env)
                          env.console.out(
                            s"✓ Review state updated at $inputPath"
                          )
                          CommandResult.ok

  // ----- helpers -----

  private def resolveIssueId(
      args: List[String],
      env: CommandEnv
  ): Either[String, String] =
    ReviewStateCliParser.extractFlag(args, "--issue-id") match
      case Some(id) => Right(id)
      case None     =>
        for
          branch <- env.git.getCurrentBranch(env.cwd)
          id <- IssueId.fromBranch(branch)
        yield id.value

  private def resolveIssueIdOrUnknown(
      args: List[String],
      env: CommandEnv
  ): String =
    resolveIssueId(args, env).getOrElse("unknown")

  private def commitIfRequested(
      args: List[String],
      issueId: String,
      outputPath: os.Path,
      env: CommandEnv
  ): Unit =
    if args.contains("--commit") then
      val status = ReviewStateCliParser.extractFlag(args, "--status")
      val message = status match
        case Some(s) => s"chore($issueId): update review-state to $s"
        case None    => s"chore($issueId): update review-state"
      env.git
        .commitFileWithRetry(outputPath, message, outputPath / os.up)
        .left
        .foreach(err =>
          env.console.out(
            s"Warning: Failed to commit review-state update: $err"
          )
        )
