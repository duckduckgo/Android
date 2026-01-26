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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.helper.RealDuckChatJSHelper
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import org.json.JSONObject
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class DuckChatContextualViewModel @Inject constructor(
    private val pageContextRepository: PageContextRepository,
    private val dispatchers: DispatcherProvider,
    private val duckChat: DuckChat,
) : ViewModel() {

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = DROP_OLDEST)
    val commands = commandChannel.receiveAsFlow()

    private val _subscriptionEventDataChannel = Channel<SubscriptionEventData>(capacity = Channel.BUFFERED)
    val subscriptionEventDataFlow = _subscriptionEventDataChannel.receiveAsFlow()

    enum class SheetMode {
        INPUT,
        WEBVIEW,
    }

    private var updatedPageContext: String = ""

    sealed class Command {
        data class LoadUrl(val url: String) : Command()
        data object SendSubscriptionAuthUpdateEvent : Command()
    }

    private val _viewState: MutableStateFlow<ViewState> =
        MutableStateFlow(
            ViewState.InputModeViewState(
                sheetMode = SheetMode.INPUT,
                sheetState = BottomSheetBehavior.STATE_HALF_EXPANDED,
                hasContext = false,
                contextUrl = "",
                contextTitle = "",
                tabId = "",
                prompt = "",
            ),
        )
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    sealed class ViewState(open val sheetState: Int) {
        data class InputModeViewState(
            val sheetMode: SheetMode = SheetMode.INPUT,
            override val sheetState: Int = BottomSheetBehavior.STATE_HALF_EXPANDED,
            val hasContext: Boolean = false,
            val contextUrl: String,
            val contextTitle: String,
            val tabId: String,
            val prompt: String,
        ) : ViewState(sheetState)

        data class ChatViewState(
            val sheetMode: SheetMode = SheetMode.WEBVIEW,
            val url: String,
            override val sheetState: Int = BottomSheetBehavior.STATE_EXPANDED,
        ) : ViewState(sheetState)
    }

    fun onSheetOpened(tabId: String) {
        viewModelScope.launch(dispatchers.io()) {
            logcat { "Duck.ai: onSheetOpened for tab=$tabId" }

            pageContextRepository.getPageContext(tabId).onEach { pageContext ->
                if (pageContext == null) {
                    return@onEach
                }

                updatedPageContext = pageContext.serializedPageData

                if (pageContext.tabId != tabId) {
                    logcat { "Duck.ai: skipping pageContext for tab=${pageContext.tabId} expected=$tabId" }
                }

                if (pageContext.isCleared) {
                    logcat { "Duck.ai: pageContext cleared for tab=$tabId" }
                }

                val json = JSONObject(updatedPageContext)
                val title = json.optString("title").takeIf { it.isNotBlank() }
                val url = json.optString("url").takeIf { it.isNotBlank() }

                if (title == null && url == null) {
                    logcat { "Duck.ai: missing title/url in pageContext for tab=$tabId json=$json" }
                } else {
                    val inputMode = _viewState.value

                    if (inputMode is ViewState.InputModeViewState) {
                        _viewState.update {
                            inputMode.copy(
                                contextTitle = title!!,
                                contextUrl = url!!,
                                tabId = tabId,
                                hasContext = true,
                            )
                        }
                    } else {
                        viewModelScope.launch(dispatchers.io()) {
                            val contextPrompt = generateContext()
                            withContext(dispatchers.main()) {
                                logcat { "Duck.ai: send new pageContext $contextPrompt" }
                                _subscriptionEventDataChannel.trySend(contextPrompt)
                            }
                        }
                    }
                }
            }.launchIn(viewModelScope)
        }
        viewModelScope.launch {
            val chatUrl = duckChat.getDuckChatUrl("", false)
            commandChannel.trySend(Command.LoadUrl(chatUrl))
        }
    }

    fun onNativeInputFocused(focused: Boolean) {
        viewModelScope.launch {
            _viewState.update { current ->
                if (current is ViewState.InputModeViewState) {
                    if (focused) {
                        current.copy(sheetState = BottomSheetBehavior.STATE_EXPANDED)
                    } else {
                        current.copy(sheetState = BottomSheetBehavior.STATE_HALF_EXPANDED)
                    }
                } else {
                    current
                }
            }
        }
    }

    fun onPromptSent(prompt: String) {
        viewModelScope.launch(dispatchers.io()) {
            val contextPrompt = generateContextPrompt(prompt)
            withContext(dispatchers.main()) {
                logcat { "Duck.ai: pageContext prompt $contextPrompt" }
                _viewState.value =
                    ViewState.ChatViewState(
                        url = "chatUrl",
                        sheetState = BottomSheetBehavior.STATE_EXPANDED,
                    )
                _subscriptionEventDataChannel.trySend(contextPrompt)
            }
        }
    }

    private fun generateContextPrompt(prompt: String): SubscriptionEventData {
        val pageContext =
            updatedPageContext
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?: run {
                    logcat { "Duck.ai: no pageContext available, skipping pageContext in prompt" }
                    null
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

    private fun generateContext(): SubscriptionEventData {
        val params =
            JSONObject().apply {
                put("pageContext", updatedPageContext)
            }

        return SubscriptionEventData(
            featureName = RealDuckChatJSHelper.DUCK_CHAT_FEATURE_NAME,
            subscriptionName = "submitPageContext",
            params = params,
        )
    }

    fun onContextualClose() {
        viewModelScope.launch {
            _viewState.update { current ->
                if (current is ViewState.InputModeViewState) {
                    current.copy(sheetState = BottomSheetBehavior.STATE_HIDDEN)
                } else {
                    val chatState = current as ViewState.ChatViewState
                    chatState.copy(sheetState = BottomSheetBehavior.STATE_HIDDEN)
                }
            }
        }
    }

    fun removePageContext() {
        viewModelScope.launch {
            _viewState.update { current ->
                if (current is ViewState.InputModeViewState) {
                    current.copy(hasContext = false)
                } else {
                    val chatState = current as ViewState.ChatViewState
                    chatState.copy(sheetState = BottomSheetBehavior.STATE_HIDDEN)
                }
            }
        }
    }

    fun addPageContext() {
        viewModelScope.launch {
            _viewState.update { current ->
                if (current is ViewState.InputModeViewState) {
                    current.copy(hasContext = true)
                } else {
                    val chatState = current as ViewState.ChatViewState
                    chatState.copy(sheetState = BottomSheetBehavior.STATE_HIDDEN)
                }
            }
        }
    }

    fun replacePrompt(prompt: String) {
        viewModelScope.launch {
            _viewState.update { current ->
                if (current is ViewState.InputModeViewState) {
                    current.copy(prompt = prompt)
                } else {
                    val chatState = current as ViewState.ChatViewState
                    chatState.copy(sheetState = BottomSheetBehavior.STATE_HIDDEN)
                }
            }
        }
    }
}
