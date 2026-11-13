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

package com.duckduckgo.duckchat.impl.attention

import androidx.core.net.toUri
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import com.duckduckgo.dataclearing.api.plugin.DataClearingPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.toChatIdOrNull
import com.duckduckgo.duckchat.impl.attention.PendingResponse.ScreenChange
import com.duckduckgo.duckchat.impl.attention.ResponseAttentionEvent.LeftWhileGenerating
import com.duckduckgo.duckchat.impl.attention.ResponseAttentionEvent.Returned
import com.duckduckgo.duckchat.impl.attention.ResponseAttentionEvent.StatusWhileAway
import com.duckduckgo.duckchat.impl.attention.ResponseAttentionEvent.TrackingAborted
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.duckchat.store.impl.DuckAiNativeStorageDeletionListener
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

/**
 * Every call is queued on one [Channel] and handled by a single consumer, so inputs are applied in the order they were sent,
 * the state below is never touched concurrently, and listeners are called in event order.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<DuckAiResponseAttentionTracker>())
@ContributesBinding(AppScope::class, binding = binding<DuckAiNativeStorageDeletionListener>())
@ContributesIntoSet(AppScope::class, binding = binding<BrowserLifecycleObserver>())
@ContributesIntoSet(AppScope::class, binding = binding<DataClearingPlugin>())
class DuckAiResponseAttentionTrackerImpl @Inject constructor(
    private val listeners: Set<ResponseAttentionListener>,
    private val duckChat: DuckChat,
    private val duckChatFeature: Lazy<DuckChatFeature>,
    private val duckChatFeatureRepository: DuckChatFeatureRepository,
    dispatchers: DispatcherProvider,
    @AppCoroutineScope appCoroutineScope: CoroutineScope,
) : DuckAiResponseAttentionTracker,
    BrowserLifecycleObserver,
    DataClearingPlugin,
    DuckAiNativeStorageDeletionListener {

    private val coroutineScope = CoroutineScope(appCoroutineScope.coroutineContext + dispatchers.io())
    private val channel = Channel<Action>(capacity = Channel.UNLIMITED)

    private val responses = mutableListOf<AttentionPendingResponse>()
    private val chatVisibility = DuckAiChatVisibility(duckChat)
    private var nextId = 1L

    init {
        coroutineScope.launch {
            for (action in channel) {
                try {
                    processAction(action)
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    logcat(tag = TAG) { "Error handling ${action::class.simpleName}: ${e::class.simpleName}" }
                } finally {
                    if (action is Action.ClearChats) action.cleared.complete(Unit)
                }
            }
        }
    }

    override fun onNativePromptSubmitted(tabId: String, browserMode: BrowserMode) {
        channel.trySend(Action.NativePromptSubmitted(tabId, browserMode))
    }

    override fun onResponseState(tabId: String, status: String) {
        channel.trySend(Action.ResponseState(tabId, status))
    }

    override fun onStopTapped(tabId: String) {
        channel.trySend(Action.StopTapped(tabId))
    }

    fun onLaunchLandingResolved(tabId: String?, url: String?) {
        channel.trySend(Action.ChatVisibility(ChatVisibilityEvent.LaunchLandingResolved(tabId, url)))
    }

    fun onDuckAiPageVisible(tabId: String, url: String) {
        channel.trySend(Action.ChatVisibility(ChatVisibilityEvent.DuckAiPageVisible(tabId, url)))
    }

    fun onSelectedTabChanged(tabId: String?, url: String?) {
        channel.trySend(Action.ChatVisibility(ChatVisibilityEvent.SelectedTabChanged(tabId, url)))
    }

    override fun onOpen(isFreshLaunch: Boolean) {
        channel.trySend(Action.ChatVisibility(ChatVisibilityEvent.AppOpened))
    }

    override fun onClose() {
        channel.trySend(Action.ChatVisibility(ChatVisibilityEvent.AppClosed))
    }

    // Returns once listeners have received TrackingAborted, since a Fire clear can kill the process right after.
    override suspend fun onClearData(types: Set<ClearableData>) {
        val cleared = CompletableDeferred<Unit>()
        channel.send(Action.ClearChats(types, cleared))
        cleared.await()
    }

    override fun onChatDeleted(chatId: String, browserMode: BrowserMode) {
        channel.trySend(Action.PageDeletedChats(chatId, browserMode))
    }

    override fun onAllChatsDeleted(browserMode: BrowserMode) {
        channel.trySend(Action.PageDeletedChats(chatId = null, browserMode = browserMode))
    }

    private suspend fun processAction(action: Action) {
        // Chat visibility is tracked even while the feature is off, so turning it on starts from the real screen state.
        if (action is Action.ChatVisibility) chatVisibility.update(action.event)
        if (isFeatureDisabled()) return abort(responses.toList())
        when (action) {
            is Action.ChatVisibility -> reconcileOnScreen()
            is Action.NativePromptSubmitted -> startResponse(action.tabId, action.browserMode)
            is Action.ResponseState -> applyResponseState(action.tabId, action.status)
            is Action.StopTapped -> responses.firstOrNull { it.isOnScreenIn(action.tabId) }?.done = true
            is Action.ClearChats -> action.types.forEach { abort(clearedBy(it)) }
            is Action.PageDeletedChats -> abort(
                responses.filter {
                    it.browserMode == action.browserMode && (action.chatId == null || it.chatId == action.chatId)
                },
            )
        }
    }

    private fun clearedBy(type: ClearableData): List<AttentionPendingResponse> = when (type) {
        is ClearableData.DuckChats.All -> responses.toList()
        is ClearableData.DuckChats.AllForMode -> responses.filter { it.browserMode == type.mode }
        is ClearableData.DuckChats.SelectedForMode -> {
            val chatIds = type.chatUrls.mapNotNull { it.toUri().toChatIdOrNull(duckChat) }.toSet()
            responses.filter { it.browserMode == type.mode && it.chatId in chatIds }
        }
        else -> emptyList()
    }

    private suspend fun abort(aborted: List<AttentionPendingResponse>) {
        aborted.forEach { response ->
            responses.remove(response)
            if (response.away) emit(TrackingAborted(response.snapshot()))
        }
    }

    private suspend fun startResponse(tabId: String, browserMode: BrowserMode) {
        // The tab's on-screen response is never away, so no listener is watching it.
        responses.removeAll { it.isOnScreenIn(tabId) }
        val response = AttentionPendingResponse(id = nextId++, tabId = tabId, browserMode = browserMode)
        response.updateOnScreen(chatVisibility.onScreen())
        responses += response
        // A prompt from the address bar reaches the page only once it loads, which can be after the app was backgrounded.
        if (!chatVisibility.isAppOpen) {
            response.away = true
            emit(LeftWhileGenerating(response.snapshot()))
        }
    }

    private suspend fun reconcileOnScreen() {
        val screen = chatVisibility.onScreen()
        for (response in responses.toList()) {
            when (response.updateOnScreen(screen)) {
                ScreenChange.ENTERED -> if (response.away) {
                    response.away = false
                    emit(Returned(response.snapshot()))
                    if (response.done) responses.remove(response)
                }
                ScreenChange.EXITED -> if (response.done) {
                    responses.remove(response)
                } else {
                    response.away = true
                    emit(LeftWhileGenerating(response.snapshot()))
                }
                null -> Unit
            }
        }
    }

    private suspend fun applyResponseState(tabId: String, rawStatus: String) {
        val response = responses.describedByStatusFrom(tabId) ?: return
        val status = PendingResponseStatus.from(rawStatus)

        when {
            status == PendingResponseStatus.Ready && response.awaitingFirstProgressStatus -> return
            status.isProgress -> {
                response.awaitingFirstProgressStatus = false
                response.done = false
            }
            status.isTerminal -> response.done = true
        }
        if (response.away) emit(StatusWhileAway(response.snapshot(), rawStatus))
    }

    private suspend fun emit(event: ResponseAttentionEvent) {
        listeners.forEach { listener ->
            try {
                listener.onEvent(event)
            } catch (e: Exception) {
                // A cancellation the listener raised itself must not stop the consumer.
                currentCoroutineContext().ensureActive()
                logcat(tag = TAG) { "Listener failed on ${event::class.simpleName}: ${e::class.simpleName}" }
            }
        }
    }

    // Toggles are read directly, not through RealDuckChat's caches, which can lag behind a remote config change.
    private suspend fun isFeatureDisabled(): Boolean {
        val feature = duckChatFeature.value
        return !feature.self().isEnabled() ||
            !feature.nativeChatInput().isEnabled() ||
            !feature.nativeDuckAiSidebar().isEnabled() ||
            !feature.useNativeStorageChatData().isEnabled() ||
            !duckChatFeatureRepository.isDuckChatUserEnabled()
    }

    private sealed class Action {
        data class NativePromptSubmitted(val tabId: String, val browserMode: BrowserMode) : Action()
        data class ResponseState(val tabId: String, val status: String) : Action()
        data class StopTapped(val tabId: String) : Action()
        data class ChatVisibility(val event: ChatVisibilityEvent) : Action()
        data class ClearChats(val types: Set<ClearableData>, val cleared: CompletableDeferred<Unit>) : Action()

        /** A null [chatId] means every chat in that mode. */
        data class PageDeletedChats(val chatId: String?, val browserMode: BrowserMode) : Action()
    }

    private class AttentionPendingResponse(
        val id: Long,
        tabId: String,
        val browserMode: BrowserMode,
    ) : PendingResponse(tabId) {
        /** True from [LeftWhileGenerating] until [Returned]. [StatusWhileAway] and [TrackingAborted] are sent only while it is true. */
        var away = false

        /** True once the page reports a terminal status or the user taps stop. A done response is dropped once it leaves or returns. */
        var done = false

        /**
         * True until the page reports progress. A new chat page opened for a prompt sent from the address bar reports 'ready' once it
         * loads, before streaming the response, so that 'ready' is ignored.
         */
        var awaitingFirstProgressStatus = true

        fun snapshot() = TrackedResponse(id = id, tabId = tabId, chatId = chatId, browserMode = browserMode)
    }

    private companion object {
        const val TAG = "DuckAiResponseAttention"
    }
}
