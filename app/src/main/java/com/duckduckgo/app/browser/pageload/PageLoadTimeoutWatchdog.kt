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

package com.duckduckgo.app.browser.pageload

import com.duckduckgo.app.browser.pageload.PageLoadTimeoutWatchdog.Phase
import com.duckduckgo.app.browser.pageload.PageLoadTimeoutWatchdog.Timeout
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Per-tab detector for main-frame loads that hang before any content is visible.
 *
 * It only observes: a timeout is reported on [timeouts] and the load itself is never stopped.
 * Must be called from the main thread.
 */
interface PageLoadTimeoutWatchdog {
    /** Emits once for each hung navigation. */
    val timeouts: Flow<Timeout>

    /**
     * A main-frame navigation is about to load [url] (null when unknown, e.g. reload or back/forward).
     * A redirect keeps the running clock; any other navigation supersedes the previous one.
     */
    fun onNavigationStarted(url: String?, isRedirect: Boolean = false, isSameDocument: Boolean = false)

    /** The app took over the redirect to [url], so no load is going to follow it. */
    fun onNavigationCancelled(url: String)

    /** `onPageStarted`: the navigation committed. */
    fun onCommitted(url: String?)

    /** `onPageCommitVisible`. */
    fun onFirstContentVisible(url: String?)

    /** `onPageFinished`. */
    fun onFinished(url: String?)

    /**
     * `doUpdateVisitedHistory`. Before a commit it can only mean a same-document navigation (back/forward to a hash or `pushState` entry),
     * which produces no commit callbacks at all.
     */
    fun onHistoryUpdated()

    /** The engine decided the load's outcome: main-frame error, HTTP error, SSL error or render process gone. */
    fun onEngineError()

    /** The tab was closed or the user left the page. */
    fun onNavigatedAway()

    data class Timeout(val phase: Phase)

    enum class Phase { NOT_COMMITTED, COMMITTED_NO_CONTENT }
}

/** A load of [targetUrl] that only differs from [currentUrl] by its fragment does not reload the document. */
fun isSameDocumentNavigation(currentUrl: String?, targetUrl: String?): Boolean =
    currentUrl != null && targetUrl != null && '#' in targetUrl && currentUrl.substringBefore('#') == targetUrl.substringBefore('#')

class RealPageLoadTimeoutWatchdog @AssistedInject constructor(
    @Assisted private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val rxBytesProvider: RxBytesProvider,
) : PageLoadTimeoutWatchdog {

    @AssistedFactory
    interface Factory {
        fun create(scope: CoroutineScope): RealPageLoadTimeoutWatchdog
    }

    private enum class State { IDLE, ARMED, TIMED_OUT }

    private data class Snapshot(val committed: Boolean, val rxBytes: Long)

    private val timeoutChannel = Channel<Timeout>(Channel.BUFFERED)
    override val timeouts: Flow<Timeout> = timeoutChannel.receiveAsFlow()

    private val watchJob = ConflatedJob()
    private var state = State.IDLE
    private var url: String? = null
    private var committed = false
    private var pendingRedirectUrl: String? = null

    override fun onNavigationStarted(url: String?, isRedirect: Boolean, isSameDocument: Boolean) {
        if (isSameDocument) return
        if (url != null && !url.isWeb()) {
            disarm()
            return
        }
        when {
            isRedirect && state == State.ARMED -> {
                this.url = url
                pendingRedirectUrl = url
            }
            isRedirect && state == State.TIMED_OUT -> Unit
            else -> arm(url, committed = false)
        }
    }

    override fun onNavigationCancelled(url: String) {
        if (state == State.ARMED && pendingRedirectUrl == url) disarm()
    }

    override fun onCommitted(url: String?) {
        when (state) {
            State.IDLE -> if (url != null && url.isWeb()) arm(url, committed = true)
            State.ARMED -> if (isCurrent(url)) {
                this.url = url ?: this.url
                committed = true
            }
            State.TIMED_OUT -> Unit
        }
    }

    override fun onFirstContentVisible(url: String?) = disarmIfCurrent(url)

    override fun onFinished(url: String?) = disarmIfCurrent(url)

    override fun onHistoryUpdated() {
        if (state == State.ARMED && !committed) disarm()
    }

    override fun onEngineError() = disarm()

    override fun onNavigatedAway() = disarm()

    private fun arm(url: String?, committed: Boolean) {
        state = State.ARMED
        this.url = url
        this.committed = committed
        pendingRedirectUrl = null
        watchJob += scope.launch(dispatchers.main()) { watch() }
    }

    private fun disarm() {
        watchJob.cancel()
        state = State.IDLE
        url = null
        pendingRedirectUrl = null
    }

    private fun disarmIfCurrent(url: String?) {
        if (state != State.IDLE && isCurrent(url)) disarm()
    }

    // Signals of a superseded load carry its URL, so they are told apart by comparing it with the latest URL of the current navigation.
    // Same-URL loads and navigations whose URL is not known up front cannot be told apart.
    private fun isCurrent(url: String?): Boolean {
        val current = this.url
        return current == null || url == null || current.normalized() == url.normalized()
    }

    private suspend fun watch() {
        delay(FIRST_CHECK_MS)
        var previous = snapshot()
        delay(CONFIRM_MS - FIRST_CHECK_MS)
        while (true) {
            val current = snapshot()
            if (!current.progressedSince(previous)) {
                timeOut()
                return
            }
            previous = current
            delay(EXTEND_MS)
        }
    }

    private fun timeOut() {
        state = State.TIMED_OUT
        val phase = if (committed) Phase.COMMITTED_NO_CONTENT else Phase.NOT_COMMITTED
        timeoutChannel.trySend(Timeout(phase))
    }

    private fun snapshot() = Snapshot(committed, rxBytesProvider.uidRxBytes())

    private fun Snapshot.progressedSince(previous: Snapshot): Boolean {
        val reachedMilestone = committed && !previous.committed
        val receivedBytes = previous.rxBytes >= 0 && rxBytes >= 0 && rxBytes - previous.rxBytes > IDLE_RX_BYTES_PER_WINDOW
        return reachedMilestone || receivedBytes
    }

    private fun String.isWeb() = startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

    private fun String.normalized() = trimEnd('/')

    companion object {
        const val FIRST_CHECK_MS = 30_000L
        const val CONFIRM_MS = 45_000L
        const val EXTEND_MS = 15_000L
        const val IDLE_RX_BYTES_PER_WINDOW = 2_048L
    }
}
