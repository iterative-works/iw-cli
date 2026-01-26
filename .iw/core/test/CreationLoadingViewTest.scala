// PURPOSE: Unit tests for CreationLoadingView component
// PURPOSE: Tests loading state rendering with spinner and message

package iw.core.presentation.views

import munit.FunSuite
import iw.core.dashboard.presentation.views.CreationLoadingView

class CreationLoadingViewTest extends FunSuite:

  test("render includes loading message"):
    val html = CreationLoadingView.render().render

    assert(html.contains("Creating worktree"), "Should show loading message")

  test("render includes spinner element"):
    val html = CreationLoadingView.render().render

    assert(html.contains("spinner"), "Should have spinner element")

  test("render has htmx-indicator class"):
    val html = CreationLoadingView.render().render

    assert(html.contains("htmx-indicator"), "Should have htmx-indicator class")

  test("render has creation-spinner id"):
    val html = CreationLoadingView.render().render

    assert(html.contains("id=\"creation-spinner\""), "Should have creation-spinner ID")

  test("render returns valid HTML fragment"):
    val frag = CreationLoadingView.render()

    val html = frag.render
    assert(html.nonEmpty, "Should produce non-empty HTML")
