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
 * [isChallengePage] marks a committed bot-challenge response, whose completion is replaced by a hold.
 * [isHeld] keeps the bar at [HELD_PROGRESS] until the page-initiated navigation that follows exceeds it.
 * [isHttpsUpgradeInFlight] spans the cancelled http request and its https replacement, so the cancelled finish is not an abandon.
 * Every pending-related part is guarded by `startAtIntent`; with it false the transitions match the behaviour before the flag existed.
 */
data class NavigationProgressState(
    val hasCompletedPageLoad: Boolean = false,
    val isNavigationPendingCommit: Boolean = false,
    val isChallengePage: Boolean = false,
    val isHeld: Boolean = false,
    val isHttpsUpgradeInFlight: Boolean = false,
) {
    fun onNavigationRequested(startAtIntent: Boolean): NavigationProgressState =
        if (startAtIntent) copy(hasCompletedPageLoad = false, isHttpsUpgradeInFlight = false) else copy(isHttpsUpgradeInFlight = false)

    fun onHttpsUpgraded(startAtIntent: Boolean): NavigationProgressState = if (startAtIntent) copy(isHttpsUpgradeInFlight = true) else this

    fun onNavigationPendingCommit(startAtIntent: Boolean): NavigationProgressState =
        if (startAtIntent) copy(isNavigationPendingCommit = true) else this

    // Leaves the pending flag alone: commit callbacks can be delivered after a newer navigation became pending
    fun onNavigationCommitted(): NavigationProgressState = copy(hasCompletedPageLoad = false, isHttpsUpgradeInFlight = false)

    // Not guarded by the flag: a pending set while it was on must not outlive a flip to off
    fun releasePendingNavigation(): NavigationProgressState = copy(isNavigationPendingCommit = false)

    fun onNavigationAbandoned(): NavigationProgressState = releasePendingNavigation().copy(isHttpsUpgradeInFlight = false)

    fun onErrorReceived(startAtIntent: Boolean): NavigationProgressState =
        (if (startAtIntent) onPageChanged().releasePendingNavigation() else releasePendingNavigation()).copy(isHttpsUpgradeInFlight = false)

    fun onPageLoadCompleted(): NavigationProgressState = copy(hasCompletedPageLoad = true)

    fun onPageChanged(): NavigationProgressState = copy(hasCompletedPageLoad = false)

    fun onMainFrameCommitted(statusCode: Int): NavigationProgressState = copy(isChallengePage = statusCode == CHALLENGE_STATUS_CODE)

    fun forgetChallengePage(): NavigationProgressState = copy(isChallengePage = false)

    fun hold(): NavigationProgressState = copy(isHeld = true)

    fun releaseHold(): NavigationProgressState = copy(isHeld = false)

    fun swallows(progress: Int): Boolean = isHeld && progress < 100 && progress <= HELD_PROGRESS

    fun isAwaitingCommitAtIntent(startAtIntent: Boolean): Boolean = startAtIntent && isNavigationPendingCommit

    fun ignoresProgress(behaviourUpdate: Boolean): Boolean = behaviourUpdate && hasCompletedPageLoad

    companion object {
        const val HELD_PROGRESS = 90

        // AWS WAF answers a bot challenge with 202, then reloads the page on its own.
        private const val CHALLENGE_STATUS_CODE = 202
    }
}
