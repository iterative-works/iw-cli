// PURPOSE: Generate Claude Code skill files from iw-cli metadata
// USAGE: iw claude-sync [--force]
// ARGS:
//   [--force]: Regenerate skills even if they exist

import iw.core.*

@main def `claude-sync`(args: String*): Unit =
  val force = args.contains("--force")

  // Check claude CLI is available
  if !ProcessAdapter.commandExists("claude") then
    Output.error("Claude CLI not found")
    Output.info("Install from: https://claude.ai/code")
    sys.exit(1)

  // Resolve paths from IW_COMMANDS_DIR (installation dir) or fall back to os.pwd
  // IW_COMMANDS_DIR points to .iw/commands, so we go up one level to .iw, then to scripts/
  val iwDir = sys.env.get(Constants.EnvVars.IwCommandsDir)
    .map(p => os.Path(p) / os.up)  // Go from .iw/commands to .iw
    .getOrElse(os.pwd / ".iw")
  val iwInstallRoot = iwDir / os.up  // Root of iw-cli installation
  val promptFile = iwDir / "scripts" / "claude-skill-prompt.md"

  // Source for the command-creation skill (bundled with iw-cli)
  val commandCreationSkillSource = iwInstallRoot / ".claude" / "skills" / "iw-command-creation"

  if !os.exists(promptFile) then
    Output.error(s"Template file not found: $promptFile")
    Output.info("This template is part of the iw-cli installation.")
    Output.info("Suggestions:")
    Output.info("  - Check if IW_HOME is set correctly")
    Output.info("  - Try reinstalling iw-cli")
    sys.env.get(Constants.EnvVars.IwCommandsDir).foreach { dir =>
      Output.info(s"  - Installation directory detected: $dir")
    }
    sys.exit(1)

  // Replace relative .iw/ paths with absolute paths for core and commands,
  // so Claude reads iw-cli source from the installation directory, not the target project.
  // Note: .iw/config.conf stays relative - it should be the target project's config.
  val prompt = os.read(promptFile)
    .replace(".iw/core/", s"$iwDir/core/")
    .replace(".iw/commands/", s"$iwDir/commands/")

  // Check if skills already exist (unless --force)
  val skillsDir = os.pwd / ".claude" / "skills"
  val existingSkills = if os.exists(skillsDir) then
    os.list(skillsDir)
      .filter(os.isDir)
      .filter(d => d.last.startsWith("iw-"))
      .toList
  else
    List.empty

  if existingSkills.nonEmpty && !force then
    Output.warning("Existing iw skills found:")
    existingSkills.foreach(s => Output.info(s"  - ${s.last}"))
    Output.info("Use --force to regenerate")
    sys.exit(0)

  // Remove existing iw skills if forcing
  if force && existingSkills.nonEmpty then
    Output.info("Removing existing iw skills...")
    existingSkills.foreach { skill =>
      os.remove.all(skill)
      Output.info(s"  Removed ${skill.last}")
    }

  Output.section("Generating iw-cli skills")

  // Ensure skills directory exists
  os.makeDir.all(skillsDir)

  // Copy the iw-command-creation skill from the installation
  val commandCreationSkillTarget = skillsDir / "iw-command-creation"
  if os.exists(commandCreationSkillSource) then
    Output.info("Copying iw-command-creation skill...")
    os.copy.over(commandCreationSkillSource, commandCreationSkillTarget)
    Output.success("Copied iw-command-creation")
  else
    Output.warning(s"iw-command-creation skill not found at: $commandCreationSkillSource")

  // Generate iw-cli-ops skill via Claude
  Output.info("Running Claude to analyze codebase and generate iw-cli-ops skill...")
  Output.info("This may take a moment...")

  // Run claude with the prompt piped via stdin
  // Restrict to read/search tools plus Write (prompt directs writes to .claude/skills/)
  val result = os.proc(
    "claude",
    "--print",
    "--allowedTools", "Read,Glob,Grep,Write"
  ).call(
      cwd = os.pwd,
      stdin = prompt,
      check = false,
      stdout = os.Inherit,
      stderr = os.Inherit
    )

  if result.exitCode != 0 then
    Output.error("Claude exited with error")
    sys.exit(1)

  // Report what was generated
  if os.exists(skillsDir) then
    val newSkills = os.list(skillsDir)
      .filter(os.isDir)
      .filter(d => d.last.startsWith("iw-"))
      .toList

    if newSkills.nonEmpty then
      Output.success("Skills synced:")
      newSkills.foreach(s => Output.info(s"  - ${s.last}"))
    else
      Output.warning("No iw skills found after sync")
  else
    Output.warning("Skills directory not created")
