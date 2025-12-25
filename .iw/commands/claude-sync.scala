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

  val promptFile = os.pwd / ".iw" / "scripts" / "claude-skill-prompt.md"

  if !os.exists(promptFile) then
    Output.error(s"Prompt file not found: $promptFile")
    sys.exit(1)

  val prompt = os.read(promptFile)

  // Check if skills already exist (unless --force)
  val skillsDir = os.pwd / ".claude" / "skills"
  val existingSkills = if os.exists(skillsDir) then
    os.list(skillsDir)
      .filter(os.isDir)
      .filter(d => d.last.startsWith("iw-") && d.last != "iw-command-creation")
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
  Output.info("Running Claude to analyze codebase and generate skills...")
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
      .filter(d => d.last.startsWith("iw-") && d.last != "iw-command-creation")
      .toList

    if newSkills.nonEmpty then
      Output.success("Skills generated:")
      newSkills.foreach(s => Output.info(s"  - ${s.last}"))
    else
      Output.warning("No new iw skills were generated")
  else
    Output.warning("Skills directory not created")
