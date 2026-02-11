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

package com.duckduckgo.duckchat.impl.contextual

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.helper.DuckChatJSHelper
import com.duckduckgo.duckchat.impl.helper.NativeAction
import com.duckduckgo.duckchat.impl.helper.RealDuckChatJSHelper
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.impl.store.DuckChatContextualDataStore
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import org.json.JSONObject
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class DuckChatContextualViewModel @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val duckChat: DuckChat,
    private val duckChatInternal: DuckChatInternal,
    private val duckChatJSHelper: DuckChatJSHelper,
    private val contextualDataStore: DuckChatContextualDataStore,
    private val sessionTimeoutProvider: DuckChatContextualSessionTimeoutProvider,
    private val timeProvider: DuckChatContextualTimeProvider,
    private val duckChatPixels: DuckChatPixels,
) : ViewModel() {

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = DROP_OLDEST)
    val commands = commandChannel.receiveAsFlow()

    private val _subscriptionEventDataChannel = Channel<SubscriptionEventData>(capacity = Channel.BUFFERED)
    val subscriptionEventDataFlow = _subscriptionEventDataChannel.receiveAsFlow()

    enum class SheetMode {
        INPUT,
        WEBVIEW,
    }

    private var fullModeUrl: String = ""
    var updatedPageContext: String = ""
    var sheetTabId: String = ""

    sealed class Command {
        data class LoadUrl(val url: String) : Command()
        data object SendSubscriptionAuthUpdateEvent : Command()
        data class OpenFullscreenMode(val url: String) : Command()
        data class ChangeSheetState(val newState: Int) : Command()
    }

    private val _viewState: MutableStateFlow<ViewState> =
        MutableStateFlow(
            ViewState(
                sheetMode = SheetMode.INPUT,
                showContext = false,
                showFullscreen = true,
                allowsAutomaticContextAttachment = duckChatInternal.isAutomaticContextAttachmentEnabled(),
                userRemovedContext = false,
                contextUrl = "",
                contextTitle = "",
                tabId = "",
                prompt = "",
            ),
        )
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    data class ViewState(
        val sheetMode: SheetMode = SheetMode.INPUT,
        val showFullscreen: Boolean = true,
        val allowsAutomaticContextAttachment: Boolean = false,
        val showContext: Boolean = false,
        val userRemovedContext: Boolean = false,
        val contextUrl: String = "",
        val contextTitle: String = "",
        val tabId: String = "",
        val prompt: String = "",
    )

    fun onSheetReopened() {
        logcat { "Duck.ai: reopenSheet" }

        viewModelScope.launch(dispatchers.io()) {
            val currentState = _viewState.value
            if (currentState.sheetMode == SheetMode.WEBVIEW) {
                reopenWebViewState(currentState)
            } else {
                withContext(dispatchers.main()) {
                    logcat { "Duck.ai: reopenSheet in Input mode" }
                    commandChannel.trySend(Command.ChangeSheetState(BottomSheetBehavior.STATE_HALF_EXPANDED))
                }
            }
        }

        duckChatPixels.reportContextualSheetOpened()
    }

    private suspend fun reopenWebViewState(currentState: ViewState) {
        val tabId = currentState.tabId
        val shouldReuseSession = shouldReuseStoredChatUrl(tabId)
        if (!shouldReuseSession) {
            renderNewChatState()
            return
        }
        val existingChatUrl = contextualDataStore.getTabChatUrl(tabId)
        withContext(dispatchers.main()) {
            commandChannel.trySend(Command.ChangeSheetState(BottomSheetBehavior.STATE_EXPANDED))
        }
        if (existingChatUrl == null) {
            val urlToLoad = duckChat.getDuckChatUrl("", false, sidebar = true)
            withContext(dispatchers.main()) {
                commandChannel.trySend(Command.LoadUrl(urlToLoad))
                _viewState.update { state ->
                    state.copy(
                        sheetMode = SheetMode.WEBVIEW,
                        showFullscreen = hasChatId(urlToLoad),
                    )
                }
            }
        } else {
            withContext(dispatchers.main()) {
                _viewState.update { state ->
                    state.copy(
                        sheetMode = SheetMode.WEBVIEW,
                        showFullscreen = hasChatId(existingChatUrl),
                    )
                }
            }
            _viewState.update {
                it.copy(
                    allowsAutomaticContextAttachment = duckChatInternal.isAutomaticContextAttachmentEnabled(),
                )
            }
            duckChatPixels.reportContextualSheetSessionRestored()
        }
    }

    fun onSheetOpened(tabId: String) {
        viewModelScope.launch(dispatchers.io()) {
            logcat { "Duck.ai: onSheetOpened for tab=$tabId" }
            sheetTabId = tabId

            val existingChatUrl = contextualDataStore.getTabChatUrl(tabId)
            if (existingChatUrl.isNullOrBlank()) {
                logcat { "Duck.ai: tab=$tabId doesn't have an existing url, use the default one" }
                withContext(dispatchers.main()) {
                    commandChannel.trySend(Command.ChangeSheetState(BottomSheetBehavior.STATE_HALF_EXPANDED))
                    val chatUrl = duckChat.getDuckChatUrl("", false, sidebar = true)
                    commandChannel.trySend(Command.LoadUrl(chatUrl))
                    _viewState.update {
                        it.copy(
                            sheetMode = SheetMode.INPUT,
                            showFullscreen = true,
                            tabId = tabId,
                            allowsAutomaticContextAttachment = duckChatInternal.isAutomaticContextAttachmentEnabled(),
                        )
                    }
                }
                duckChatPixels.reportContextualPlaceholderContextShown()
                return@launch
            }

            val shouldReuseUrl = shouldReuseStoredChatUrl(tabId)
            if (shouldReuseUrl) {
                logcat { "Duck.ai: tab=$tabId has an existing url and don't need to restart the session" }
                val hasChatHistory = hasChatId(existingChatUrl)

                withContext(dispatchers.main()) {
                    _viewState.update { current ->
                        current.copy(
                            sheetMode = SheetMode.WEBVIEW,
                            showFullscreen = hasChatHistory,
                            tabId = tabId,
                            allowsAutomaticContextAttachment = duckChatInternal.isAutomaticContextAttachmentEnabled(),
                        )
                    }
                    commandChannel.trySend(Command.ChangeSheetState(BottomSheetBehavior.STATE_EXPANDED))
                    commandChannel.trySend(Command.LoadUrl(existingChatUrl))
                }
            } else {
                logcat { "Duck.ai: tab=$tabId session expired, starting a new one" }
                withContext(dispatchers.main()) {
                    _viewState.update {
                        it.copy(tabId = tabId)
                    }
                }
                renderNewChatState()
            }
        }
        duckChatPixels.reportContextualSheetOpened()
    }

    fun onPromptSent(prompt: String) {
        viewModelScope.launch(dispatchers.io()) {
            val contextPrompt = generateContextPrompt(prompt)
            withContext(dispatchers.main()) {
                logcat { "Duck.ai: pageContext prompt $contextPrompt" }
                _viewState.value =
                    _viewState.value.copy(
                        sheetMode = SheetMode.WEBVIEW,
                        prompt = "",
                    )
                _subscriptionEventDataChannel.trySend(contextPrompt)
                commandChannel.trySend(Command.ChangeSheetState(BottomSheetBehavior.STATE_EXPANDED))
            }
        }
    }

    fun onChatPageLoaded(url: String?) {
        logcat { "Duck.ai: onChatPageLoaded $url" }
        if (url == null) {
            return
        } else {
            fullModeUrl = url
            val sheetMode = _viewState.value.sheetMode
            if (sheetMode == SheetMode.WEBVIEW) {
                val hasChatId = hasChatId(url)

                viewModelScope.launch {
                    _viewState.update { current ->
                        current.copy(showFullscreen = hasChatId)
                    }
                }

                if (hasChatId) {
                    val tabId = _viewState.value.tabId
                    if (tabId.isNotBlank()) {
                        viewModelScope.launch(dispatchers.io()) {
                            contextualDataStore.persistTabChatUrl(tabId, url)
                        }
                    }
                }
            }
        }
    }

    private fun generateContextPrompt(prompt: String): SubscriptionEventData {
        val viewState = _viewState.value
        val pageContext =
            if (viewState.showContext) {
                updatedPageContext
                    .takeIf { it.isNotBlank() }
                    ?.let { runCatching { JSONObject(it) }.getOrNull() }
                    ?: run {
                        logcat { "Duck.ai: no pageContext available, skipping pageContext in prompt" }
                        null
                    }
            } else {
                null
            }

        if (pageContext == null) {
            duckChatPixels.reportContextualPromptSubmittedWithoutContextNative()
        } else {
            duckChatPixels.reportContextualPromptSubmittedWithContextNative()
        }

        val params =
            JSONObject().apply {
                put("platform", "android")
                put("tool", "query")
                put(
                    "query",
                    JSONObject().apply {
                        put("prompt", prompt)
                        put("autoSubmit", true)
                    },
                )
                pageContext?.let { put("pageContext", it) }
            }

        return SubscriptionEventData(
            featureName = RealDuckChatJSHelper.DUCK_CHAT_FEATURE_NAME,
            subscriptionName = "submitAIChatNativePrompt",
            params = params,
        )
    }

    fun onContextualClose() {
        viewModelScope.launch(dispatchers.main()) {
            commandChannel.trySend(Command.ChangeSheetState(BottomSheetBehavior.STATE_HIDDEN))
        }
    }

    fun onSheetClosed() {
        persistTabClosed()
        duckChatPixels.reportContextualSheetDismissed()
    }

    private fun persistTabClosed() {
        if (_viewState.value.sheetMode != SheetMode.WEBVIEW) {
            return
        }
        viewModelScope.launch(dispatchers.io()) {
            val tabId = _viewState.value.tabId
            if (tabId.isNotBlank()) {
                contextualDataStore.persistTabClosedTimestamp(tabId, timeProvider.currentTimeMillis())
            }
        }
    }

    fun removePageContext() {
        logcat { "Duck.ai Contextual: removePageContext" }
        viewModelScope.launch {
            _viewState.update { current ->
                current.copy(
                    showContext = false,
                    userRemovedContext = true,
                )
            }
        }
        duckChatPixels.reportContextualPlaceholderContextShown()
        duckChatPixels.reportContextualPageContextRemovedNative()
    }

    fun addPageContext() {
        logcat { "Duck.ai Contextual: addPageContext" }
        duckChatPixels.reportContextualPlaceholderContextTapped()
        viewModelScope.launch {
            val isContextValid = isContextValid(updatedPageContext)
            if (isContextValid) {
                duckChatPixels.reportContextualPageContextManuallyAttachedNative()
                _viewState.update { current ->
                    logcat { "Duck.ai Contextual: addPageContext $current context $updatedPageContext" }
                    current.copy(
                        showContext = isContextValid(updatedPageContext),
                        userRemovedContext = false,
                    )
                }
            }
        }
    }

    private fun isContextValid(pageContext: String): Boolean {
        if (pageContext.isEmpty()) return false
        val json = JSONObject(pageContext)
        val title = json.optString("title").takeIf { it.isNotBlank() }
        val content = json.optString("content").takeIf { it.isNotBlank() }

        return title != null && content != null
    }

    fun replacePrompt(input: String, prompt: String) {
        logcat { "Duck.ai Contextual: add predefined Summarize prompt" }
        duckChatPixels.reportContextualSummarizePromptSelected()
        viewModelScope.launch {
            val newPrompt = if (input.isEmpty()) {
                prompt
            } else {
                input.plus(" ").plus(prompt)
            }
            _viewState.update { current ->
                current.copy(
                    prompt = newPrompt,
                    showContext = isContextValid(updatedPageContext),
                )
            }
        }
    }

    fun onPromptCleared() {
        logcat { "Duck.ai Contextual: onPromptCleared" }
        viewModelScope.launch {
            _viewState.update { current ->
                current.copy(
                    prompt = "",
                )
            }
        }
    }

    fun onFullModeRequested() {
        logcat { "Duck.ai: request fullmode url $fullModeUrl" }
        val currentState = _viewState.value
        val chatUrl = if (currentState.sheetMode == SheetMode.INPUT) {
            duckChat.getDuckChatUrl("", false, sidebar = false)
        } else {
            fullModeUrl.ifEmpty {
                duckChat.getDuckChatUrl("", false, sidebar = false)
            }
        }
        viewModelScope.launch {
            commandChannel.trySend(Command.OpenFullscreenMode(chatUrl))
        }
        duckChatPixels.reportContextualSheetExpanded()
    }

    fun onKeyboardVisibilityChanged(isVisible: Boolean) {
        val currentState = _viewState.value
        if (currentState.sheetMode != SheetMode.INPUT) return

        val newState =
            if (isVisible) {
                BottomSheetBehavior.STATE_EXPANDED
            } else {
                BottomSheetBehavior.STATE_HALF_EXPANDED
            }

        viewModelScope.launch {
            commandChannel.trySend(Command.ChangeSheetState(newState))
        }
    }

    fun onPageContextReceived(
        tabId: String,
        pageContext: String,
    ) {
        logcat { "Duck.ai: onPageContextReceived $pageContext" }
        if (isContextValid(pageContext)) {
            logcat { "Duck.ai: onPageContextReceived is valid" }
            updatedPageContext = pageContext

            val json = JSONObject(updatedPageContext)
            val title = json.optString("title")
            val url = json.optString("url")

            val inputMode = _viewState.value

            if (inputMode.sheetMode == SheetMode.INPUT) {
                val allowsAutomaticContextAttachment = duckChatInternal.isAutomaticContextAttachmentEnabled()
                val updatedState =
                    inputMode.copy(
                        contextTitle = title,
                        contextUrl = url,
                        tabId = tabId,
                        allowsAutomaticContextAttachment = allowsAutomaticContextAttachment,
                        showContext =
                        if (allowsAutomaticContextAttachment) {
                            !inputMode.userRemovedContext
                        } else {
                            inputMode.showContext
                        },
                    )
                if (updatedState.showContext && !inputMode.showContext) {
                    duckChatPixels.reportContextualPageContextAutoAttached()
                }
                _viewState.update { updatedState }
            } else {
                _viewState.update {
                    inputMode.copy(
                        contextTitle = title,
                        contextUrl = url,
                        tabId = tabId,
                        allowsAutomaticContextAttachment = duckChatInternal.isAutomaticContextAttachmentEnabled(),
                    )
                }
            }
        } else {
            updatedPageContext = ""
            duckChatPixels.reportContextualPageContextCollectionEmpty()
        }
    }

    fun handleJSCall(method: String): Boolean {
        when (method) {
            RealDuckChatJSHelper.METHOD_CLOSE_AI_CHAT -> {
                logcat { "Duck.ai: $method handled at the VM level" }
                onContextualClose()
                return true
            }

            else -> {
                return false
            }
        }
    }

    fun onNewChatRequested() {
        renderNewChatState()
        duckChatPixels.reportContextualSheetNewChat()
    }

    private fun renderNewChatState() {
        viewModelScope.launch(dispatchers.io()) {
            val currentTabId = _viewState.value.tabId
            if (currentTabId.isNotBlank()) {
                contextualDataStore.clearTabChatUrl(currentTabId)

                withContext(dispatchers.main()) {
                    _viewState.update {
                        it.copy(
                            sheetMode = SheetMode.INPUT,
                            showFullscreen = true,
                            prompt = "",
                        )
                    }
                    commandChannel.trySend(Command.ChangeSheetState(BottomSheetBehavior.STATE_HALF_EXPANDED))

                    val subscriptionEvent = duckChatJSHelper.onNativeAction(NativeAction.NEW_CHAT)
                    _subscriptionEventDataChannel.trySend(subscriptionEvent)
                }
            }
        }
        duckChatPixels.reportContextualPlaceholderContextShown()
    }

    private fun hasChatId(url: String?): Boolean {
        return url?.toUri()?.getQueryParameter("chatID")
            .orEmpty()
            .isNotBlank()
    }

    private suspend fun shouldReuseStoredChatUrl(tabId: String): Boolean {
        val lastClosedTimestamp = contextualDataStore.getTabClosedTimestamp(tabId) ?: return true
        val timeoutMs = sessionTimeoutProvider.sessionTimeoutMillis()
        if (timeoutMs <= 0) return false
        val elapsedMs = timeProvider.currentTimeMillis() - lastClosedTimestamp
        return elapsedMs <= timeoutMs
    }
}
