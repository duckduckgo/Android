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

import android.os.Bundle
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
            val fromView = from.view ?: return
            // Triggers without a control in this tab (tab switcher, links) grow from the tabs button.
            val source = from.consumeNewTabTransitionSource()?.takeIf { it.isAttachedToWindow && it.visibility == View.VISIBLE }
                ?: fromView.findVisibleViewWithId(setOf(R.id.tabsMenu, R.id.tabsButton))
                ?: return
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
            // The revealed tab is hidden, so its root isn't visible yet; only its contents are checked.
            val tabsButton = (revealed.view as? ViewGroup)?.children
                ?.firstNotNullOfOrNull { it.findVisibleViewWithId(setOf(R.id.tabsMenu, R.id.tabsButton)) }
                ?: return
            setReorderingAllowed(true)
            revealed.view?.clearTransitionName(CLOSE_TRANSFORM_TARGET_NAME)
            ViewCompat.setTransitionName(closingView, CLOSE_TRANSFORM_SOURCE_NAME)
            ViewCompat.setTransitionName(tabsButton, CLOSE_TRANSFORM_TARGET_NAME)
            addSharedElement(closingView, CLOSE_TRANSFORM_TARGET_NAME)
            revealed.sharedElementEnterTransition = MaterialContainerTransform().apply {
                drawingViewId = R.id.fragmentContainer
                duration = CONTAINER_TRANSFORM_DURATION_MS
                setAllContainerColors(closing.requireContext().getColorFromAttr(CommonR.attr.daxColorBackground))
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
    }
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
