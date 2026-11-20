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

package com.duckduckgo.duckchat.impl.pendingresponse

import androidx.core.net.toUri
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.FireMode
import com.duckduckgo.common.utils.AppUrl.ParamKey.QUERY
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import com.duckduckgo.dataclearing.api.plugin.DataClearingPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.RealDuckChat
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.pendingresponse.PendingResponse.ScreenChange
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.duckchat.impl.responding.DuckAiRespondingServiceController
import com.duckduckgo.duckchat.impl.responseready.DuckAiResponseReadyMonitor
import com.duckduckgo.duckchat.impl.responseready.DuckAiResponseReadyNotifier
import com.duckduckgo.duckchat.impl.responseready.WaitResult
import com.duckduckgo.duckchat.impl.responseready.responseReadySettings
import com.duckduckgo.duckchat.impl.voice.VoiceSessionStateManager
import com.duckduckgo.duckchat.store.impl.DuckAiNativeStorageDeletionListener
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

/**
 * Every call is queued on one [Channel] and handled by a single consumer, so inputs are applied in the order they were sent and
 * the state below is never touched concurrently. Active response wait [Job]s run in a thread pool and their results are reported
 * back through the channel, so they are handled in order with other state calls.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<DuckAiPendingResponseTracker>())
@ContributesBinding(AppScope::class, binding = binding<DuckAiNativeStorageDeletionListener>())
@ContributesIntoSet(AppScope::class, binding = binding<BrowserLifecycleObserver>())
@ContributesIntoSet(AppScope::class, binding = binding<DataClearingPlugin>())
class DuckAiPendingResponseTrackerImpl @Inject constructor(
    private val duckChat: DuckChat,
    private val duckChatFeature: Lazy<DuckChatFeature>,
    private val duckChatFeatureRepository: DuckChatFeatureRepository,
    @param:FireMode private val fireTabRepository: Lazy<TabRepository>,
    private val voiceSessionStateManager: VoiceSessionStateManager,
    private val readyMonitor: DuckAiResponseReadyMonitor,
    private val notifier: DuckAiResponseReadyNotifier,
    private val serviceController: DuckAiRespondingServiceController,
    private val pixels: Lazy<DuckChatPixels>,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope appCoroutineScope: CoroutineScope,
) : DuckAiPendingResponseTracker,
    BrowserLifecycleObserver,
    DataClearingPlugin,
    DuckAiNativeStorageDeletionListener {

    private val coroutineScope = CoroutineScope(appCoroutineScope.coroutineContext + dispatchers.io().limitedParallelism(1))
    private val channel = Channel<Action>(capacity = Channel.UNLIMITED)

    private val responses = mutableListOf<TrackedPendingResponse>()
    private val chatVisibility = DuckAiChatVisibility(duckChat)
    private var keepAlive = false

    init {
        coroutineScope.launch {
            for (action in channel) {
                try {
                    logOnFailure(source = { "Handling ${action::class.simpleName}" }) {
                        processAction(action)
                    }
                    updateKeepAlive()
                } finally {
                    if (action is Action.ClearChats) action.cleared.complete(Unit)
                }
            }
        }
    }

    override fun onNativePromptSubmitted(tabId: String, browserMode: BrowserMode) {
        channel.trySend(Action.NativePromptSubmitted(tabId, browserMode))
    }

    override fun onResponseState(tabId: String, status: String, browserMode: BrowserMode) {
        channel.trySend(Action.ResponseState(tabId, status, browserMode))
    }

    override fun onStopTapped(tabId: String) {
        channel.trySend(Action.StopTapped(tabId))
    }

    fun onLaunchLandingResolved(tabId: String?, url: String?) {
        channel.trySend(Action.ChatVisibility(ChatVisibilityEvent.LaunchLandingResolved(tabId, url)))
    }

    fun onDuckAiPageVisible(tabId: String, url: String) {
        channel.trySend(Action.ChatVisibility(ChatVisibilityEvent.DuckAiPageVisible(tabId, url)))
        // The page reports [onNativePromptSubmitted] for an address-bar prompt only once it has loaded,
        // which can be too late to start the service after the user leaves.
        // If the prompt is in the URL, we treat it as submitted right away and start tracking the response.
        if (isPromptHandoverWithQuery(url)) {
            channel.trySend(Action.PromptHandoverVisible(tabId))
        }
    }

    private fun isPromptHandoverWithQuery(url: String): Boolean {
        val uri = url.toUri()
        return RealDuckChat.isPromptHandoverUrl(uri) && !uri.getQueryParameter(QUERY).isNullOrBlank()
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

    // Returns once the cleared responses' waits and notifications are cancelled, since a Fire clear can kill the process right after.
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
        if (!isTrackingEnabled()) return abort(responses.toList())
        when (action) {
            is Action.ChatVisibility -> reconcileOnScreen()
            is Action.NativePromptSubmitted -> startResponse(action.tabId, action.browserMode)
            is Action.PromptHandoverVisible -> startResponse(action.tabId, browserModeOf(action.tabId))
            is Action.ResponseState -> applyResponseState(action.tabId, action.status, action.browserMode)
            is Action.StopTapped -> responses.firstOrNull { it.isOnScreenIn(action.tabId) }?.done = true
            is Action.ClearChats -> action.types.forEach { abort(responses.clearedBy(it, duckChat)) }
            is Action.PageDeletedChats -> abort(responses.deletedBy(action.chatId, action.browserMode))
            is Action.ChatIdFound -> if (action.response.wait === action.wait) action.response.onChatIdFound(action.chatId)
            is Action.WaitFinished -> onWaitFinished(action)
        }
    }

    private suspend fun abort(aborted: List<TrackedPendingResponse>) {
        aborted.forEach { response ->
            responses.remove(response)
            if (response.away) stopWaitAndNotification(response)
        }
    }

    private suspend fun browserModeOf(tabId: String): BrowserMode =
        if (fireTabRepository.value.getTab(tabId) != null) BrowserMode.FIRE else BrowserMode.REGULAR

    private suspend fun startResponse(tabId: String, browserMode: BrowserMode): TrackedPendingResponse {
        // An address-bar prompt is reported on each page load and again when the page picks it up. This deduplicates them.
        responses.firstOrNull {
            it.tabId == tabId && !it.done && !it.chatSwitchedAway && it.awaitingFirstProgressStatus
        }?.let { return it }
        abort(responses.filter { it.isOnScreenIn(tabId) })
        val response = TrackedPendingResponse(
            tabId = tabId,
            browserMode = browserMode,
            canNotify = canNotify(tabId, browserMode),
        )
        response.updateOnScreen(chatVisibility.onScreen())
        responses += response
        // A prompt from the address bar reaches the page only once it loads, which can be after the app was backgrounded or the tab switched.
        if (!chatVisibility.isAppOpen || chatVisibility.selectedTabId != tabId) {
            leave(response)
        }
        return response
    }

    private suspend fun reconcileOnScreen() {
        val screen = chatVisibility.onScreen()
        for (response in responses.toList()) {
            when (response.updateOnScreen(screen)) {
                ScreenChange.ENTERED -> if (response.away) {
                    response.away = false
                    stopWaitAndNotification(response)
                    if (response.done) responses.remove(response)
                }
                ScreenChange.EXITED -> if (response.done) {
                    responses.remove(response)
                } else {
                    leave(response)
                }
                null -> Unit
            }
        }
    }

    private suspend fun applyResponseState(tabId: String, rawStatus: String, browserMode: BrowserMode) {
        val status = PendingResponseStatus.from(rawStatus)
        val response = responses.describedByStatusFrom(tabId)
            ?: startPageInitiatedResponse(tabId, status, browserMode)
            ?: return

        when {
            status == PendingResponseStatus.Ready && response.awaitingFirstProgressStatus -> return
            status.isProgress -> {
                response.awaitingFirstProgressStatus = false
                response.done = false
            }
            status.isTerminal -> response.done = true
        }
    }

    private suspend fun startPageInitiatedResponse(
        tabId: String,
        status: PendingResponseStatus,
        browserMode: BrowserMode,
    ): TrackedPendingResponse? {
        // The page starts streams on its own too, such as an edit or a retry.
        // A reconnection recovers a stream that may already be finished, so it never starts tracking.
        return if (status is PendingResponseStatus.StartStream && !status.isReconnection) {
            startResponse(tabId, browserMode)
        } else {
            null
        }
    }

    private fun leave(response: TrackedPendingResponse) {
        response.away = true
        if (response.canNotify) startWait(response)
    }

    private fun startWait(response: TrackedPendingResponse) {
        response.wait?.job?.cancel()
        val wait = Wait()
        val tabId = response.tabId
        val chatId = response.chatId
        val settings = duckChatFeature.value.responseReadySettings()
        wait.job = coroutineScope.launch(dispatchers.io()) {
            val result = readyMonitor.await(tabId, chatId, settings) { foundChatId ->
                channel.send(Action.ChatIdFound(response, wait, chatId = foundChatId))
            }
            channel.send(Action.WaitFinished(response, wait, result))
        }
        response.wait = wait
    }

    private suspend fun onWaitFinished(action: Action.WaitFinished) {
        val response = action.response
        // A stopped or replaced wait's result is ignored.
        if (response.wait !== action.wait) return
        response.wait = null
        when (action.result) {
            WaitResult.Ready -> notifyReady(response)
            WaitResult.TimedOut -> {
                logcat(tag = TAG) { "Duck.ai response wait timed out" }
                pixels.value.reportResponseReadyWaitTimedOut()
            }
            WaitResult.Stopped -> Unit
        }
    }

    private suspend fun notifyReady(response: TrackedPendingResponse) {
        val chatId = response.chatId
        if (chatId == null) {
            logcat(tag = TAG) { "Duck.ai response ready before its chat ID was known; not notifying" }
            return
        }
        logOnFailure(source = { "Duck.ai response ready notification" }) {
            notifier.notifyReady(response.tabId, chatId)
        }
    }

    private suspend fun stopWaitAndNotification(response: TrackedPendingResponse) {
        response.wait?.job?.cancel()
        response.wait = null
        val chatId = response.chatId
        if (response.canNotify && chatId != null) {
            logOnFailure(source = { "Duck.ai response ready cancellation" }) {
                notifier.cancel(chatId)
            }
        }
    }

    private fun updateKeepAlive() {
        val enabled = responses.any { it.wait != null || (it.canNotify && !it.done && !it.away) }
        if (enabled == keepAlive) return
        keepAlive = enabled
        serviceController.setKeepAliveOnLeave(enabled)
    }

    private suspend inline fun logOnFailure(source: () -> String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            logcat(tag = TAG) { "${source()} failed: ${e::class.simpleName}" }
        }
    }

    // Toggles are read directly, not through RealDuckChat's caches, which can lag behind a remote config change.
    private suspend fun isTrackingEnabled(): Boolean {
        val feature = duckChatFeature.value
        return feature.self().isEnabled() &&
            feature.nativeChatInput().isEnabled() &&
            feature.nativeDuckAiSidebar().isEnabled() &&
            feature.useNativeStorageChatData().isEnabled() &&
            feature.duckAiNativeStorage().isEnabled() &&
            duckChatFeatureRepository.isDuckChatUserEnabled() &&
            duckChatFeatureRepository.isAIChatHistoryEnabled() &&
            feature.responseReadyNotifications().isEnabled()
    }

    private fun canNotify(tabId: String, browserMode: BrowserMode): Boolean {
        return browserMode == BrowserMode.REGULAR &&
            !voiceSessionStateManager.isVoiceSessionActive(tabId)
    }

    private sealed class Action {
        data class NativePromptSubmitted(val tabId: String, val browserMode: BrowserMode) : Action()
        data class PromptHandoverVisible(val tabId: String) : Action()
        data class ResponseState(val tabId: String, val status: String, val browserMode: BrowserMode) : Action()
        data class StopTapped(val tabId: String) : Action()
        data class ChatVisibility(val event: ChatVisibilityEvent) : Action()
        data class ClearChats(val types: Set<ClearableData>, val cleared: CompletableDeferred<Unit>) : Action()

        /** A null [chatId] means every chat in that mode. */
        data class PageDeletedChats(val chatId: String?, val browserMode: BrowserMode) : Action()

        class ChatIdFound(val response: TrackedPendingResponse, val wait: Wait, val chatId: String) : Action()
        class WaitFinished(val response: TrackedPendingResponse, val wait: Wait, val result: WaitResult) : Action()
    }

    private class TrackedPendingResponse(
        tabId: String,
        browserMode: BrowserMode,
        /** Whether a ready notification applies to it, read once when it starts. Keeps the app alive while it generates on screen. */
        val canNotify: Boolean,
    ) : PendingResponse(tabId, browserMode) {
        /** True from when the user leaves it while generating until they are back on its chat. */
        var away = false

        /** True once the page reports a terminal status or the user taps stop. A done response is dropped once it leaves or returns. */
        var done = false

        /**
         * True until the page reports progress. A new chat page opened for a prompt sent from the address bar reports 'ready' once it
         * loads, before streaming the response, so that 'ready' is ignored.
         */
        var awaitingFirstProgressStatus = true

        /** Non-null while the user is away and a wait runs for it. */
        var wait: Wait? = null

        /** Takes the chat ID a wait read from the tab URL while the chat was off screen, so clearing or deleting that chat finds it. */
        fun onChatIdFound(chatId: String) {
            if (this.chatId == null) this.chatId = chatId
        }
    }

    private class Wait {
        lateinit var job: Job
    }

    private companion object {
        const val TAG = "DuckAiPendingResponse"
    }
}
