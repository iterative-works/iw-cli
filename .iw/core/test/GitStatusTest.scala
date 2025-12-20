// PURPOSE: Unit tests for GitStatus domain model
// PURPOSE: Tests status indicator and CSS class generation

package iw.core.domain

import munit.FunSuite

class GitStatusTest extends FunSuite:

  test("statusIndicator returns '✓ clean' when isClean is true"):
    val status = GitStatus("main", isClean = true)
    assertEquals(status.statusIndicator, "✓ clean")

  test("statusIndicator returns '⚠ uncommitted' when isClean is false"):
    val status = GitStatus("feature-branch", isClean = false)
    assertEquals(status.statusIndicator, "⚠ uncommitted")

  test("statusCssClass returns 'git-clean' when isClean is true"):
    val clean = GitStatus("main", isClean = true)
    assertEquals(clean.statusCssClass, "git-clean")

  test("statusCssClass returns 'git-dirty' when isClean is false"):
    val dirty = GitStatus("main", isClean = false)
    assertEquals(dirty.statusCssClass, "git-dirty")

  test("GitStatus with detached HEAD branch name"):
    val status = GitStatus("HEAD", isClean = true)
    assertEquals(status.branchName, "HEAD")
    assertEquals(status.statusIndicator, "✓ clean")

  test("GitStatus with long branch name"):
    val status = GitStatus("feature/IWLE-123-add-git-status-display", isClean = false)
    assertEquals(status.branchName, "feature/IWLE-123-add-git-status-display")
    assertEquals(status.statusCssClass, "git-dirty")
