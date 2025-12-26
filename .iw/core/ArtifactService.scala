// PURPOSE: Application service for loading and rendering artifact content
// PURPOSE: Coordinates path validation, file reading, and markdown rendering with I/O injection

package iw.core.application

import iw.core.PathValidator
import iw.core.domain.ServerState
import iw.core.infrastructure.MarkdownRenderer
import java.nio.file.{Path, Paths}

object ArtifactService:
  /** Load and render artifact content.
    *
    * @param issueId Issue identifier
    * @param artifactPath Relative path from worktree root
    * @param state Current server state (for worktree lookup)
    * @param readFile Function to read file content (injected I/O)
    * @param resolveSymlinks Function to resolve symlinks (injected I/O, for testing)
    * @return Either error message or (artifact label, rendered HTML, worktree path)
    */
  def loadArtifact(
    issueId: String,
    artifactPath: String,
    state: ServerState,
    readFile: Path => Either[String, String],
    resolveSymlinks: Path => Either[String, Path] = (p: Path) => Right(p)
  ): Either[String, (String, String, String)] =
    // 1. Resolve worktree path from state
    state.worktrees.get(issueId) match
      case None => Left("Worktree not found")
      case Some(worktree) =>
        val worktreePath = Paths.get(worktree.path)

        // 2. Validate artifact path (security)
        PathValidator.validateArtifactPath(worktreePath, artifactPath, resolveSymlinks) match
          case Left(error) => Left(error)
          case Right(validatedPath) =>
            // 3. Read file content
            readFile(validatedPath).flatMap { content =>
              // 4. Render markdown to HTML
              val html = MarkdownRenderer.toHtml(content)

              // 5. Extract artifact label from filename
              val label = extractLabel(validatedPath)

              Right((label, html, worktree.path))
            }

  /** Extract human-readable label from file path.
    *
    * Examples:
    *   analysis.md → "analysis.md"
    *   phase-03-context.md → "phase-03-context.md"
    *   project-management/issues/46/review.md → "review.md"
    */
  def extractLabel(path: Path): String =
    path.getFileName.toString
