// PURPOSE: Application service for workflow progress computation
// PURPOSE: Pure business logic with file I/O injection pattern

package iw.core.application

import iw.core.domain.{PhaseInfo, WorkflowProgress, CachedProgress}

/** Application service for workflow progress tracking.
  *
  * Uses file I/O injection pattern for testability.
  * All logic is pure except for file operations injected from caller.
  */
object WorkflowProgressService:

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
    val taskDir = s"$worktreePath/project-management/issues/$issueId"

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
      // Get current mtimes for all phase files
      val mtimesResult = existingFiles.map { file =>
        getMtime(file.path).map(mtime => (file.path, mtime))
      }

      // Check if any mtime fetch failed
      val mtimeErrors = mtimesResult.collect { case Left(err) => err }
      if mtimeErrors.nonEmpty then
        Left(s"Failed to get mtimes: ${mtimeErrors.mkString(", ")}")
      else
        val currentMtimes = mtimesResult.collect { case Right(pair) => pair }.toMap

        // Check cache validity
        cache.get(issueId) match {
          case Some(cached) if CachedProgress.isValid(cached, currentMtimes) =>
            // Cache is valid, return cached progress
            Right(cached.progress)

          case _ =>
            // Cache invalid or missing, parse files
            parsePhaseFiles(existingFiles, readFile).map { phaseInfos =>
              computeProgress(phaseInfos)
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
    * @return WorkflowProgress with aggregated data
    */
  def computeProgress(phases: List[PhaseInfo]): WorkflowProgress =
    val overallTotal = phases.map(_.totalTasks).sum
    val overallCompleted = phases.map(_.completedTasks).sum
    val currentPhase = determineCurrentPhase(phases)

    WorkflowProgress(
      currentPhase = currentPhase,
      totalPhases = phases.size,
      phases = phases,
      overallCompleted = overallCompleted,
      overallTotal = overallTotal
    )

  /** Determine current phase number from phase list.
    *
    * Returns:
    * - First phase with tasks in progress (some complete, some incomplete)
    * - First phase with no tasks started (if no in-progress phases)
    * - Last phase (if all phases complete)
    * - None (if no phases)
    *
    * @param phases List of phase information
    * @return Current phase number (1-based) or None
    */
  def determineCurrentPhase(phases: List[PhaseInfo]): Option[Int] =
    if phases.isEmpty then
      None
    else
      // First check for in-progress phases
      phases.find(_.isInProgress).map(_.phaseNumber)
        .orElse {
          // Then check for not-started phases
          phases.find(_.notStarted).map(_.phaseNumber)
        }
        .orElse {
          // All complete, return last phase
          phases.lastOption.map(_.phaseNumber)
        }

  /** Discovered phase file with number and path. */
  private case class DiscoveredPhaseFile(phaseNumber: Int, path: String)
