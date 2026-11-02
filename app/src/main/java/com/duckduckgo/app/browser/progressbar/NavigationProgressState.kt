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

package com.duckduckgo.app.browser.progressbar

/**
 * Page lifecycle flags that drive the progress bar, with pure transitions.
 *
 * [hasCompletedPageLoad] gates late iframe progress after a page reached 100%.
 * [isNavigationPendingCommit] holds back the outgoing page's progress between the intent to navigate and the new page's commit.
 *
 * Every pending-related part is guarded by `startAtIntent`; with it false the transitions match the behaviour before the flag existed.
 */
data class NavigationProgressState(
    val hasCompletedPageLoad: Boolean = false,
    val isNavigationPendingCommit: Boolean = false,
) {
    fun onNavigationRequested(startAtIntent: Boolean): NavigationProgressState =
        if (startAtIntent) copy(hasCompletedPageLoad = false) else this

    fun onNavigationPendingCommit(startAtIntent: Boolean): NavigationProgressState =
        if (startAtIntent) copy(isNavigationPendingCommit = true) else this

    // Leaves the pending flag alone: commit callbacks can be delivered after a newer navigation became pending
    fun onNavigationCommitted(): NavigationProgressState = copy(hasCompletedPageLoad = false)

    // Not guarded by the flag: a pending set while it was on must not outlive a flip to off
    fun releasePendingNavigation(): NavigationProgressState = copy(isNavigationPendingCommit = false)

    fun onNavigationAbandoned(): NavigationProgressState = releasePendingNavigation()

    fun onErrorReceived(startAtIntent: Boolean): NavigationProgressState =
        if (startAtIntent) onPageChanged().releasePendingNavigation() else releasePendingNavigation()

    fun onPageLoadCompleted(): NavigationProgressState = copy(hasCompletedPageLoad = true)

    fun onPageChanged(): NavigationProgressState = copy(hasCompletedPageLoad = false)

    fun isAwaitingCommitAtIntent(startAtIntent: Boolean): Boolean = startAtIntent && isNavigationPendingCommit

    fun ignoresProgress(behaviourUpdate: Boolean): Boolean = behaviourUpdate && hasCompletedPageLoad
}
