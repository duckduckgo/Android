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

package com.duckduckgo.app.browser.tabs.transition

import android.view.View
import logcat.logcat

/**
 * Coordinates whether the new tab showing animates in or not. We only animate the tab that the transition was registered for, and only if its
 * picture is ready, so showing a new tab never waits. Showing any other tab ends a transition still running, so that tab appears at once.
 *
 * [onRunningChanged] reports when transitions start running and when the last one ends.
 */
class NewTabTransitionCoordinator(
    private val player: NewTabTransitionPlayer,
    private val pendingTransitions: PendingNewTabTransitions,
    private val state: NewTabTransitionState,
    private val onRunningChanged: (Boolean) -> Unit,
) {

    private var current: Current? = null
    private var browserOnScreen = false
    private var awaitingPicture: AwaitingPicture? = null

    /** [tabId] is about to show in [container]. */
    fun onTabShowing(
        tabId: String,
        container: View,
    ) {
        val current = current
        if (current?.tabId == tabId && !current.covering) return

        val awaiting = awaitingPicture
        val listedAwaitingPicture = awaiting?.tabId == tabId
        if (listedAwaitingPicture) awaitingPicture = null
        if (awaiting != null && awaiting.isSupersededBy(tabId)) {
            // Another selection wins over a tab still waiting for its picture.
            awaitingPicture = null
            pendingTransitions.consume(awaiting.tabId)
        }
        val transition = pendingTransitions.consume(tabId)
        val picture = transition?.picture?.readyOrNull()?.takeUnless { listedAwaitingPicture }
        if (transition != null && picture != null) {
            start(tabId, covering = false) { onEnd -> player.play(container, picture, transition.motion, onEnd) }
            return
        }
        if (transition != null) {
            val reason = if (listedAwaitingPicture) "listed without a cover" else "picture not ready"
            logcat { "New tab transition skipped: $reason" }
        }

        if (current != null && current.isWaitedOutBy(tabId)) return

        // Set before cancelling: cancelling releases held focus, which must then go to this tab only.
        state.shownTabId = tabId
        if (current != null) player.cancel()
    }

    /**
     * While a new tab's selection catches up, the stale selection can show the tab it was on again (at a shifted index after
     * a list change, or replayed from the cached selection after a return). Only that tab passes under the cover, and only
     * while the cover's transition is still the one waiting: anything else is a real selection, which ends the cover.
     */
    private fun Current.isWaitedOutBy(tabId: String): Boolean =
        covering &&
            this.tabId != tabId &&
            pendingTransitions.peek(this.tabId) != null &&
            (coveredFromTabId == null || coveredFromTabId == tabId)

    /**
     * The tab list changed, ahead of the selection that will show a new tab. The pager keeps its position, so it can show
     * the new tab at once (adding it deleted the blank tab on screen) or pass other tabs while indices shift. If the new
     * tab's picture is ready, it covers the pager from now until the selection reaches it, otherwise onTabRegistered
     * decides once the picture arrives.
     */
    fun onTabsChanged(
        tabIds: List<String>,
        container: View,
    ) {
        val tabId = tabIds.firstOrNull { pendingTransitions.peek(it) != null } ?: return
        // Only onTabRegistered, which knows the visible page, can tell whether a tab listed before its picture is still hidden.
        if (tabId == awaitingPicture?.tabId) return
        coverListed(tabId, container)
    }

    private fun coverListed(
        tabId: String,
        container: View,
    ) {
        if (current?.tabId == tabId) return
        val pendingPicture = pendingTransitions.peek(tabId)?.picture ?: return
        if (!pendingPicture.isCompleted) {
            awaitingPicture = AwaitingPicture(tabId, fromTabId = current?.coveredFromTabId ?: state.shownTabId)
            return
        }
        awaitingPicture = null
        val picture = pendingPicture.readyOrNull()
        if (picture == null) {
            pendingTransitions.consume(tabId)
            logcat { "New tab transition skipped: picture failed" }
            return
        }
        start(tabId, covering = true) { onEnd -> player.cover(container, picture, onEnd) }
    }

    /**
     * [tabId]'s transition was just registered, or its picture just arrived.
     */
    fun onTabRegistered(
        tabId: String,
        listedTabIds: List<String>,
        visibleTabId: String?,
        container: View,
    ) {
        if (tabId == awaitingPicture?.tabId) awaitingPicture = null
        if (tabId == state.shownTabId || tabId == visibleTabId) {
            pendingTransitions.consume(tabId)
            logcat { "New tab transition skipped: tab already shown" }
            return
        }
        if (tabId in listedTabIds) coverListed(tabId, container)
    }

    /**
     * The browser is coming back on screen. If the tab manager opened a new tab, its picture covers the browser until that
     * tab shows.
     */
    fun onBrowserStarting(container: View) {
        val tabId = pendingTransitions.tabManagerTabId() ?: return
        val picture = pendingTransitions.peek(tabId)?.picture?.readyOrNull()
        if (picture == null) {
            pendingTransitions.consume(tabId)
            logcat { "New tab transition skipped: picture not ready" }
            return
        }
        start(tabId, covering = true) { onEnd -> player.cover(container, picture, onEnd) }
    }

    fun cancel() = player.cancel()

    /**
     * Whether [tabId] is the tab on screen, or the one growing in.
     */
    fun isShowing(tabId: String?): Boolean = browserOnScreen && tabId != null && tabId == state.shownTabId

    fun onBrowserResumed() {
        browserOnScreen = true
    }

    fun onBrowserStopped() {
        browserOnScreen = false
    }

    private fun start(
        tabId: String,
        covering: Boolean,
        run: (onEnd: () -> Unit) -> Unit,
    ) {
        val wasRunning = current != null
        // The cover keeps the tab it started from as the shown tab (as a picture) until the new one plays.
        val coveredFromTabId = if (covering) current?.coveredFromTabId ?: state.shownTabId else null
        val started = Current(tabId, covering, coveredFromTabId).also { current = it }
        if (!covering) state.shownTabId = tabId
        if (!wasRunning) onRunningChanged(true)
        run {
            if (current === started) {
                current = null
                if (covering) pendingTransitions.consume(tabId)
                onRunningChanged(false)
            }
        }
    }

    private class Current(
        val tabId: String,
        val covering: Boolean,
        val coveredFromTabId: String?,
    )

    private class AwaitingPicture(
        val tabId: String,
        val fromTabId: String?,
    ) {
        fun isSupersededBy(shownTabId: String): Boolean = shownTabId != tabId && fromTabId != null && shownTabId != fromTabId
    }
}
