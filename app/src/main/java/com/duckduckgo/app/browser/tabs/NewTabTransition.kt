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
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.duckduckgo.app.browser.BrowserTabFragment

/** [from] is null when something other than a tab animates out underneath the new one. */
fun FragmentTransaction.applyNewTabTransition(
    fragmentManager: FragmentManager,
    from: BrowserTabFragment?,
    to: BrowserTabFragment,
    motion: NewTabMotion,
) {
    setReorderingAllowed(true)
    from?.markAsTransitionGroup(fragmentManager)
    to.markAsTransitionGroup(fragmentManager)
    from?.exitTransition = NewTabScaleTransition(motion, isClosing = false)
    to.enterTransition = NewTabScaleTransition(motion, isClosing = false)
}

/** The opening transition played backwards, for a transaction that hides [closing] and shows [revealed]. */
fun FragmentTransaction.applyCloseTabTransition(
    fragmentManager: FragmentManager,
    closing: BrowserTabFragment,
    revealed: BrowserTabFragment,
    motion: NewTabMotion,
) {
    setReorderingAllowed(true)
    closing.markAsTransitionGroup(fragmentManager)
    revealed.markAsTransitionGroup(fragmentManager)
    closing.exitTransition = NewTabScaleTransition(motion, isClosing = true)
    revealed.enterTransition = NewTabScaleTransition(motion, isClosing = true)
}

/** Transitions stay on a fragment once set, so they're cleared before every tab transaction to animate only new tabs. */
fun FragmentManager.clearNewTabTransitions() {
    fragments.filterIsInstance<BrowserTabFragment>().forEach { it.clearNewTabTransition() }
}

fun BrowserTabFragment.clearNewTabTransition() {
    enterTransition = null
    exitTransition = null
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
