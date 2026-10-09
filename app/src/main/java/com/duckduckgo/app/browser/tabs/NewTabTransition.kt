/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.browser.tabs

import android.os.Build
import android.os.Bundle
import android.view.RoundedCorner
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.duckduckgo.app.browser.BrowserTabFragment
import com.duckduckgo.app.browser.R
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.toPx
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.transition.Hold
import com.google.android.material.transition.MaterialContainerTransform
import com.google.android.material.transition.MaterialSharedAxis
import com.duckduckgo.mobile.android.R as CommonR

enum class NewTabTransition {
    SHARED_AXIS,
    CONTAINER_TRANSFORM,
}

fun FragmentTransaction.applyNewTabTransition(
    transition: NewTabTransition,
    fragmentManager: FragmentManager,
    from: BrowserTabFragment,
    to: BrowserTabFragment,
    containerOptions: ContainerTransformOptions,
) {
    when (transition) {
        NewTabTransition.SHARED_AXIS -> {
            setReorderingAllowed(true)
            from.markAsTransitionGroup(fragmentManager)
            to.markAsTransitionGroup(fragmentManager)
            from.exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
            to.enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        }
        NewTabTransition.CONTAINER_TRANSFORM -> {
            val fromView = from.view as? ViewGroup ?: return
            val trigger = from.consumeNewTabTransitionSource()?.takeIf { it.isAttachedToWindow && it.visibility == View.VISIBLE }
            val source = when (containerOptions.source) {
                ContainerTransformSource.BOTTOM_CENTERED -> fromView.addTransitionAnchor(containerOptions.startSize)
                // Triggers without a control in this tab (tab switcher, links) grow from the tabs button.
                ContainerTransformSource.TOOLBAR_ICONS -> trigger ?: fromView.findVisibleViewWithId(setOf(R.id.tabsMenu, R.id.tabsButton))
            } ?: return
            setReorderingAllowed(true)
            // A control named for an earlier transition would give this tab two views with the same name.
            fromView.clearTransitionName(CONTAINER_TRANSFORM_SOURCE_NAME)
            ViewCompat.setTransitionName(source, CONTAINER_TRANSFORM_SOURCE_NAME)
            addSharedElement(source, CONTAINER_TRANSFORM_TARGET_NAME)
            to.doOnViewCreated(fragmentManager) { view -> ViewCompat.setTransitionName(view, CONTAINER_TRANSFORM_TARGET_NAME) }
            to.sharedElementEnterTransition = MaterialContainerTransform().apply {
                drawingViewId = R.id.fragmentContainer
                duration = CONTAINER_TRANSFORM_DURATION_MS
                setAllContainerColors(from.requireContext().getColorFromAttr(CommonR.attr.daxColorBackground))
                if (containerOptions.roundedCorners) {
                    setRoundedCorners(smallView = source, smallViewIsStart = true)
                }
            }
            // Keeps the old tab on screen underneath the growing container instead of hiding it straight away.
            from.exitTransition = Hold().apply { duration = CONTAINER_TRANSFORM_DURATION_MS }
        }
    }
}

/** The opening transition played backwards, for a transaction that removes [closing] and shows [revealed]. */
fun FragmentTransaction.applyCloseTabTransition(
    transition: NewTabTransition,
    fragmentManager: FragmentManager,
    closing: BrowserTabFragment,
    revealed: BrowserTabFragment,
    containerOptions: ContainerTransformOptions,
) {
    when (transition) {
        NewTabTransition.SHARED_AXIS -> {
            setReorderingAllowed(true)
            closing.markAsTransitionGroup(fragmentManager)
            revealed.markAsTransitionGroup(fragmentManager)
            closing.exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
            revealed.enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        }
        NewTabTransition.CONTAINER_TRANSFORM -> {
            val closingView = closing.view ?: return
            val revealedView = revealed.view as? ViewGroup ?: return
            val target = when (containerOptions.source) {
                ContainerTransformSource.BOTTOM_CENTERED -> revealedView.addTransitionAnchor(containerOptions.startSize)
                // The revealed tab is hidden, so its root isn't visible yet; only its contents are checked.
                ContainerTransformSource.TOOLBAR_ICONS ->
                    revealedView.children
                        .firstNotNullOfOrNull { it.findVisibleViewWithId(setOf(R.id.tabsMenu, R.id.tabsButton)) }
            } ?: return
            setReorderingAllowed(true)
            revealedView.clearTransitionName(CLOSE_TRANSFORM_TARGET_NAME)
            ViewCompat.setTransitionName(closingView, CLOSE_TRANSFORM_SOURCE_NAME)
            ViewCompat.setTransitionName(target, CLOSE_TRANSFORM_TARGET_NAME)
            addSharedElement(closingView, CLOSE_TRANSFORM_TARGET_NAME)
            revealed.sharedElementEnterTransition = MaterialContainerTransform().apply {
                drawingViewId = R.id.fragmentContainer
                duration = CONTAINER_TRANSFORM_DURATION_MS
                setAllContainerColors(closing.requireContext().getColorFromAttr(CommonR.attr.daxColorBackground))
                if (containerOptions.roundedCorners) {
                    setRoundedCorners(smallView = target, smallViewIsStart = false)
                }
            }
        }
    }
}

/** Transitions stay on a fragment once set, so they're cleared before every tab transaction to animate only new tabs. */
fun FragmentManager.clearNewTabTransitions() {
    fragments.filterIsInstance<BrowserTabFragment>().forEach {
        it.enterTransition = null
        it.exitTransition = null
        it.sharedElementEnterTransition = null
        (it.view as? ViewGroup)?.removeTransitionAnchors()
    }
}

/**
 * An empty, invisible view in the bottom-centre of the tab that the container transform grows from or shrinks into,
 * because a shared element transition needs a real view at its start and end. It is a scaled-down copy of the screen,
 * so the tab keeps its proportions as it grows.
 */
private fun ViewGroup.addTransitionAnchor(startSize: ContainerTransformStartSize): View {
    removeTransitionAnchors()
    val anchorWidth = (width * startSize.fraction).toInt()
    val anchorHeight = (height * startSize.fraction).toInt()
    val anchor = View(context).apply {
        tag = TRANSITION_ANCHOR_TAG
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    addView(anchor, ViewGroup.LayoutParams(anchorWidth, anchorHeight))
    // Laid out by hand so its bounds exist when the transition captures them, before the next layout pass.
    anchor.layout(0, 0, anchorWidth, anchorHeight)
    anchor.translationX = (width - anchorWidth) / 2f
    anchor.translationY = (height - anchorHeight).toFloat()
    return anchor
}

private fun ViewGroup.removeTransitionAnchors() {
    children.filter { it.tag == TRANSITION_ANCHOR_TAG }.toList().forEach { removeView(it) }
}

// The tab keeps the screen's rounded corners while it moves; a toolbar icon starts or ends as a pill.
private fun MaterialContainerTransform.setRoundedCorners(
    smallView: View,
    smallViewIsStart: Boolean,
) {
    val screenShape = ShapeAppearanceModel().withCornerSize(smallView.screenCornerRadius())
    val smallShape = if (smallView.tag == TRANSITION_ANCHOR_TAG) screenShape else ShapeAppearanceModel().withCornerSize(smallView.height / 2f)
    startShapeAppearanceModel = if (smallViewIsStart) smallShape else screenShape
    endShapeAppearanceModel = if (smallViewIsStart) screenShape else smallShape
}

private fun View.screenCornerRadius(): Float {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val radius = rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0
        if (radius > 0) return radius.toFloat()
    }
    return DEFAULT_SCREEN_CORNER_RADIUS_DP.toPx().toFloat()
}

// Without this, fragment transitions animate each leaf view on its own and skip the WebView,
// so the page itself would just appear instead of moving as one.
private fun Fragment.markAsTransitionGroup(fragmentManager: FragmentManager) {
    val root = view as? ViewGroup
    if (root != null) {
        root.isTransitionGroup = true
    } else {
        doOnViewCreated(fragmentManager) { view -> (view as? ViewGroup)?.isTransitionGroup = true }
    }
}

// The new tab's view doesn't exist yet when the transaction is built.
private fun Fragment.doOnViewCreated(
    fragmentManager: FragmentManager,
    action: (View) -> Unit,
) {
    val target = this
    fragmentManager.registerFragmentLifecycleCallbacks(
        object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(
                fm: FragmentManager,
                f: Fragment,
                v: View,
                savedInstanceState: Bundle?,
            ) {
                if (f !== target) return
                action(v)
                fm.unregisterFragmentLifecycleCallbacks(this)
            }
        },
        false,
    )
}

// Not View.isShown: returning from another activity, the tab switches while the window is still marked invisible.
fun View.findVisibleViewWithId(ids: Set<Int>): View? {
    if (visibility != View.VISIBLE) return null
    if (id in ids) return this
    if (this is ViewGroup) {
        children.forEach { child -> child.findVisibleViewWithId(ids)?.let { return it } }
    }
    return null
}

private fun View.clearTransitionName(transitionName: String) {
    if (ViewCompat.getTransitionName(this) == transitionName) ViewCompat.setTransitionName(this, null)
    if (this is ViewGroup) {
        children.forEach { it.clearTransitionName(transitionName) }
    }
}

private const val CONTAINER_TRANSFORM_SOURCE_NAME = "newTabTransitionSource"
private const val CONTAINER_TRANSFORM_TARGET_NAME = "newTabTransitionTarget"
private const val CLOSE_TRANSFORM_SOURCE_NAME = "closeTabTransitionSource"
private const val CLOSE_TRANSFORM_TARGET_NAME = "closeTabTransitionTarget"
private const val CONTAINER_TRANSFORM_DURATION_MS = 450L
private const val TRANSITION_ANCHOR_TAG = "newTabTransitionAnchor"
private const val DEFAULT_SCREEN_CORNER_RADIUS_DP = 16
