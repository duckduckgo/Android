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

package com.duckduckgo.duckchat.impl.responseready

import androidx.core.net.toUri
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.RegularMode
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.toChatIdOrNull
import com.duckduckgo.duckchat.impl.pendingresponse.PendingResponseStatus
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import logcat.logcat
import javax.inject.Inject
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@ContributesBinding(AppScope::class, binding = binding<DuckAiResponseReadyMonitor>())
class DuckAiResponseReadyMonitorImpl @Inject constructor(
    @RegularMode private val chatStore: DuckAiChatStore,
    @RegularMode private val tabRepository: TabRepository,
    private val storedResponseClient: DuckAiStoredResponseClient,
    private val duckChat: DuckChat,
) : DuckAiResponseReadyMonitor {

    override suspend fun await(
        tabId: String,
        chatId: String?,
        statuses: ReceiveChannel<PendingResponseStatus>,
        settings: ResponseReadyNotificationsSettings,
        onChatIdFound: suspend (String) -> Unit,
    ): WaitResult =
        try {
            when (withTimeoutOrNull(settings.timeout) { awaitReady(tabId, chatId, statuses, settings, onChatIdFound) }) {
                true -> WaitResult.Ready
                false -> WaitResult.Stopped
                null -> WaitResult.TimedOut
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "Duck.ai response wait failed: ${e::class.simpleName}" }
            WaitResult.Stopped
        }

    private suspend fun awaitReady(
        tabId: String,
        knownChatId: String?,
        statuses: ReceiveChannel<PendingResponseStatus>,
        settings: ResponseReadyNotificationsSettings,
        onChatIdFound: suspend (String) -> Unit,
    ): Boolean = coroutineScope {
        // Null until the tab is seen, and again once it is closed or marked deletable.
        val tab = MutableStateFlow<TabEntity?>(null)
        launch {
            tabRepository.flowTabs.collect { tabs -> tab.value = tabs.firstOrNull { it.tabId == tabId && !it.deletable } }
        }
        val pageReady = async { awaitPageReady(statuses, tab, settings.errorSettle) }
        val stored = async {
            val chatId = knownChatId
                ?: tab.mapNotNull { it?.url?.toUri()?.toChatIdOrNull(duckChat) }.first().also { onChatIdFound(it) }

            // Read once and frozen: a retry can mint an ID that never reaches the backend.
            val storedResponseId = awaitPendingResponseId(chatId, settings.pollInterval)

            awaitStoredResponse(storedResponseId, settings.pollInterval)
        }
        val ready = select {
            pageReady.onAwait { true }
            stored.onAwait { it }
        }
        coroutineContext.cancelChildren()
        ready
    }

    /**
     * Returns once the page's statuses show the response is ready. Statuses are ignored while the tab is closed or not seen yet: the page
     * posts `ready` shortly after a tab close aborts its fetch, while the backend may still be generating.
     */
    private suspend fun awaitPageReady(
        statuses: ReceiveChannel<PendingResponseStatus>,
        tab: StateFlow<TabEntity?>,
        errorSettle: Duration,
    ) = coroutineScope {
        val ready = CompletableDeferred<Unit>()
        val signals = PageSignals()
        var errorSettleTimer: Job? = null
        launch {
            for (status in statuses) {
                when (signals.onStatus(status)) {
                    PageSignal.READY -> if (tab.value != null) ready.complete(Unit)
                    PageSignal.START_ERROR_SETTLE -> if (tab.value != null && errorSettleTimer?.isActive != true) {
                        errorSettleTimer = launch {
                            delay(errorSettle)
                            if (tab.value != null) ready.complete(Unit)
                        }
                    }
                    PageSignal.CANCEL_ERROR_SETTLE -> errorSettleTimer?.cancel()
                    PageSignal.NONE -> Unit
                }
            }
        }
        ready.await()
        coroutineContext.cancelChildren()
    }

    /** The page can save the chat after the user left, so the saved chat is read again until it shows the pending response ID. */
    private suspend fun awaitPendingResponseId(chatId: String, pollInterval: Duration): String {
        while (true) {
            findPendingResponseId(chatStore.getChatContent(chatId))?.let { return it }
            delay(pollInterval)
        }
    }

    private suspend fun awaitStoredResponse(responseId: String, pollInterval: Duration): Boolean {
        var rateLimits = 0
        while (true) {
            val code = try {
                storedResponseClient.getStoredResponse(responseId).code()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            when (code) {
                200 -> return true
                418, 429 -> {
                    delay(rateLimitBackoff(rateLimits++))
                    continue
                }
                null, 404, 408, in 500..599 -> Unit
                else -> {
                    logcat { "Duck.ai stored response check stopped on HTTP $code" }
                    return false
                }
            }
            rateLimits = 0
            delay(pollInterval)
        }
    }
}

internal fun rateLimitBackoff(previousRateLimits: Int): Duration =
    30.seconds * 2.0.pow(previousRateLimits) * (1 + Random.nextDouble())
