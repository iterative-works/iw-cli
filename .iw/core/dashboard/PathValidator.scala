// PURPOSE: Validate artifact paths to prevent directory traversal attacks
// PURPOSE: Ensures paths stay within worktree boundaries with secure error messages
package iw.core.dashboard

import java.nio.file.{Path, Paths}

object PathValidator:

  /**
   * Validate that an artifact path is safe to serve.
   *
   * @param worktreePath The root path of the worktree
   * @param artifactPath The relative path to validate
   * @param resolveSymlinks Function to resolve symlinks (injected for testing/I/O isolation)
   * @return Right(resolvedPath) if valid, Left(errorMessage) if invalid
   */
  def validateArtifactPath(
    worktreePath: Path,
    artifactPath: String,
    resolveSymlinks: Path => Either[String, Path] = defaultSymlinkResolver
  ): Either[String, Path] =
    // Reject empty or whitespace-only paths
    if artifactPath.trim.isEmpty then
      Left("Invalid artifact path")
    // Reject absolute paths
    else if isAbsolute(artifactPath) then
      Left("Artifact path must be relative")
    else
      // Resolve the path against the worktree
      val resolvedPath = worktreePath.resolve(artifactPath).normalize()

      // Check if path is within boundary (before symlink resolution)
      if !isWithinBoundary(worktreePath, resolvedPath) then
        Left("Artifact not found")
      else
        // Resolve symlinks and check final target is within boundary
        resolveSymlinks(resolvedPath).flatMap { realPath =>
          if !isWithinBoundary(worktreePath, realPath) then
            Left("Artifact not found")
          else
            Right(realPath)
        }

  /**
   * Check if a path is absolute.
   * Handles both Unix (/path) and Windows (C:\path) paths.
   */
  private def isAbsolute(path: String): Boolean =
    path.startsWith("/") ||
    (path.length >= 2 && path.charAt(1) == ':') // Windows drive letter

  /**
   * Check if the resolved path is within the base path boundary.
   *
   * @param basePath The boundary (worktree root)
   * @param targetPath The path to check (must be normalized)
   * @return true if targetPath is within basePath
   */
  def isWithinBoundary(basePath: Path, targetPath: Path): Boolean =
    val normalizedBase = basePath.normalize().toAbsolutePath
    val normalizedTarget = targetPath.normalize().toAbsolutePath
    normalizedTarget.startsWith(normalizedBase)

  /**
   * Default symlink resolver that uses toRealPath to resolve symlinks.
   * Returns Left if the path doesn't exist or symlink resolution fails.
   *
   * @param path The path to resolve
   * @return Right(realPath) if successful, Left(errorMessage) if not
   */
  private def defaultSymlinkResolver(path: Path): Either[String, Path] =
    try
      import java.nio.file.{Files, LinkOption}
      // Check if the path exists
      if !Files.exists(path, LinkOption.NOFOLLOW_LINKS) then
        Left("Artifact not found")
      else
        // toRealPath follows symlinks and resolves to canonical path
        Right(path.toRealPath())
    catch
      case _: java.nio.file.NoSuchFileException =>
        Left("Artifact not found")
      case _: java.io.IOException =>
        Left("Artifact not found")
