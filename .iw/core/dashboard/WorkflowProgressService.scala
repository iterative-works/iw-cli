// PURPOSE: Application service for workflow progress computation
// PURPOSE: Pure business logic with file I/O injection pattern

package iw.core.dashboard

import iw.core.model.{PhaseInfo, WorkflowProgress, CachedProgress}
import iw.core.model.Issue

/** Application service for workflow progress tracking.
  *
  * Uses file I/O injection pattern for testability.
  * All logic is pure except for file operations injected from caller.
  */
object WorkflowProgressService:

  /** Fetch workflow progress with cache support, returning CachedProgress.
    *
    * Checks cache validity using file modification times.
    * Re-parses task files if cache is invalid or missing.
    * Returns CachedProgress so caller can update server cache.
    *
    * File I/O is injected for testability (FCIS pattern).
    *
    * @param issueId Issue identifier (e.g., "IWLE-123")
    * @param worktreePath Path to worktree root
    * @param cache Existing progress cache
    * @param readFile Function to read file lines (injected I/O)
    * @param getMtime Function to get file modification time (injected I/O)
    * @return Either error message or CachedProgress
    */
  def fetchProgressCached(
    issueId: String,
    worktreePath: String,
    cache: Map[String, CachedProgress],
    readFile: String => Either[String, Seq[String]],
    getMtime: String => Either[String, Long]
  ): Either[String, CachedProgress] =
    fetchProgressInternal(issueId, worktreePath, cache, readFile, getMtime)

  /** Fetch workflow progress with cache support.
    *
    * Checks cache validity using file modification times.
    * Re-parses task files if cache is invalid or missing.
    *
    * File I/O is injected for testability (FCIS pattern).
    *
    * @param issueId Issue identifier (e.g., "IWLE-123")
    * @param worktreePath Path to worktree root
    * @param cache Existing progress cache
    * @param readFile Function to read file lines (injected I/O)
    * @param getMtime Function to get file modification time (injected I/O)
    * @return Either error message or WorkflowProgress
    */
  def fetchProgress(
    issueId: String,
    worktreePath: String,
    cache: Map[String, CachedProgress],
    readFile: String => Either[String, Seq[String]],
    getMtime: String => Either[String, Long]
  ): Either[String, WorkflowProgress] =
    fetchProgressInternal(issueId, worktreePath, cache, readFile, getMtime).map(_.progress)

  /** Internal implementation that returns CachedProgress. */
  private def fetchProgressInternal(
    issueId: String,
    worktreePath: String,
    cache: Map[String, CachedProgress],
    readFile: String => Either[String, Seq[String]],
    getMtime: String => Either[String, Long]
  ): Either[String, CachedProgress] =
    val taskDir = s"$worktreePath/project-management/issues/$issueId"
    val tasksFilePath = s"$taskDir/tasks.md"

    // Discover phase files using injected getMtime (check if files exist by trying to get mtime)
    val phaseFilePaths = (1 to 20).map { n =>
      val phaseNum = f"$n%02d"
      s"$taskDir/phase-$phaseNum-tasks.md"
    }.toList

    // Filter to files that exist (getMtime succeeds)
    val existingFiles = phaseFilePaths.zipWithIndex.flatMap { case (path, idx) =>
      getMtime(path).toOption.map { mtime =>
        DiscoveredPhaseFile(idx + 1, path)
      }
    }

    if existingFiles.isEmpty then
      Left(s"No phase files found in $taskDir")
    else
      // Get current mtimes for all phase files + tasks.md
      val mtimesResult = existingFiles.map { file =>
        getMtime(file.path).map(mtime => (file.path, mtime))
      }

      // Check if any mtime fetch failed
      val mtimeErrors = mtimesResult.collect { case Left(err) => err }
      if mtimeErrors.nonEmpty then
        Left(s"Failed to get mtimes: ${mtimeErrors.mkString(", ")}")
      else
        val currentMtimes = mtimesResult.collect { case Right(pair) => pair }.toMap

        // Also include tasks.md mtime in cache key (if it exists)
        val tasksFileMtime = getMtime(tasksFilePath).toOption
        val allMtimes = tasksFileMtime match
          case Some(mtime) => currentMtimes + (tasksFilePath -> mtime)
          case None => currentMtimes

        // Check cache validity
        cache.get(issueId) match {
          case Some(cached) if CachedProgress.isValid(cached, allMtimes) =>
            // Cache is valid, return cached CachedProgress
            Right(cached)

          case _ =>
            // Cache invalid or missing, parse files
            // Read Phase Index from tasks.md to determine current phase
            val phaseIndex = readFile(tasksFilePath)
              .map(MarkdownTaskParser.parsePhaseIndex)
              .getOrElse(List.empty)

            parsePhaseFiles(existingFiles, readFile).map { phaseInfos =>
              val progress = computeProgress(phaseInfos, phaseIndex)
              // Wrap in CachedProgress with current mtimes
              CachedProgress(progress, allMtimes)
            }
        }

  /** Parse phase files into PhaseInfo objects.
    *
    * @param phaseFiles List of discovered phase files
    * @param readFile Function to read file lines
    * @return Either error or list of PhaseInfo
    */
  private def parsePhaseFiles(
    phaseFiles: List[DiscoveredPhaseFile],
    readFile: String => Either[String, Seq[String]]
  ): Either[String, List[PhaseInfo]] =
    val results = phaseFiles.map { file =>
      readFile(file.path).map { lines =>
        val taskCount = MarkdownTaskParser.parseTasks(lines)
        val phaseName = MarkdownTaskParser.extractPhaseName(lines)
          .getOrElse(s"Phase ${file.phaseNumber}")

        PhaseInfo(
          phaseNumber = file.phaseNumber,
          phaseName = phaseName,
          taskFilePath = file.path,
          totalTasks = taskCount.total,
          completedTasks = taskCount.completed
        )
      }
    }

    // Check if any read failed
    val errors = results.collect { case Left(err) => err }
    if errors.nonEmpty then
      Left(s"Failed to read phase files: ${errors.mkString(", ")}")
    else
      Right(results.collect { case Right(info) => info })

  /** Compute workflow progress from phase info list.
    *
    * Pure function that aggregates phase information and determines current phase.
    *
    * @param phases List of phase information
    * @param phaseIndex Phase completion status from tasks.md (source of truth for current phase)
    * @return WorkflowProgress with aggregated data
    */
  def computeProgress(phases: List[PhaseInfo], phaseIndex: List[PhaseIndexEntry] = List.empty): WorkflowProgress =
    val overallTotal = phases.map(_.totalTasks).sum
    val overallCompleted = phases.map(_.completedTasks).sum
    val currentPhase = determineCurrentPhase(phases, phaseIndex)

    WorkflowProgress(
      currentPhase = currentPhase,
      totalPhases = if phaseIndex.nonEmpty then phaseIndex.size else phases.size,
      phases = phases,
      overallCompleted = overallCompleted,
      overallTotal = overallTotal
    )

  /** Determine current phase number.
    *
    * Uses Phase Index from tasks.md as source of truth when available.
    * Falls back to task-based detection if no Phase Index provided.
    *
    * With Phase Index:
    * - Returns first incomplete phase (checkbox not checked)
    * - Returns last phase if all complete
    *
    * Without Phase Index (fallback):
    * - First phase with tasks in progress (some complete, some incomplete)
    * - First phase with no tasks started (if no in-progress phases)
    * - Last phase (if all phases complete)
    *
    * @param phases List of phase information
    * @param phaseIndex Phase completion status from tasks.md
    * @return Current phase number (1-based) or None
    */
  def determineCurrentPhase(phases: List[PhaseInfo], phaseIndex: List[PhaseIndexEntry] = List.empty): Option[Int] =
    if phases.isEmpty then
      None
    else if phaseIndex.nonEmpty then
      // Use Phase Index as source of truth
      phaseIndex.find(!_.isComplete).map(_.phaseNumber)
        .orElse(phaseIndex.lastOption.map(_.phaseNumber))
    else
      // Fallback: use task-based detection
      phases.find(_.isInProgress).map(_.phaseNumber)
        .orElse {
          phases.find(_.notStarted).map(_.phaseNumber)
        }
        .orElse {
          phases.lastOption.map(_.phaseNumber)
        }

  /** Discovered phase file with number and path. */
  private case class DiscoveredPhaseFile(phaseNumber: Int, path: String)
