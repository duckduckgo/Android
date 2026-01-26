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
import com.duckduckgo.duckchat.impl.helper.RealDuckChatJSHelper
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import org.json.JSONObject
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class DuckChatContextualViewModel @Inject constructor(
    private val pageContextRepository: PageContextRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = DROP_OLDEST)
    val commands = commandChannel.receiveAsFlow()

    private val _subscriptionEventDataChannel = Channel<SubscriptionEventData>(capacity = Channel.BUFFERED)
    val subscriptionEventDataFlow = _subscriptionEventDataChannel.receiveAsFlow()

    private var updatedPageContext: String = ""

    sealed class Command {
        data object SendSubscriptionAuthUpdateEvent : Command()
        data class PageContextUpdated(
            val pageTitle: String,
            val pageUrl: String,
            val tabId: String,
        ) : Command()
    }

    fun observePageContextChanges(tabId: String) {
        viewModelScope.launch(dispatchers.io()) {
            logcat { "Duck.ai: observePageContextChanges started for tab=$tabId" }
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

                logcat { "Duck.ai: pageContext update for tab=$tabId (length=${updatedPageContext.length})" }

                val json = JSONObject(updatedPageContext)
                val title = json.optString("title").takeIf { it.isNotBlank() }
                val url = json.optString("url").takeIf { it.isNotBlank() }

                if (title == null && url == null) {
                    logcat { "Duck.ai: missing title/url in pageContext for tab=$tabId json=$json" }
                } else {
                    withContext(dispatchers.main()) {
                        commandChannel.trySend(Command.PageContextUpdated(title!!, url!!, tabId))
                    }
                }
            }.launchIn(viewModelScope)
        }
    }

    fun onPromptSent(prompt: String) {
        viewModelScope.launch(dispatchers.io()) {
            val contextPrompt = generateContextPrompt(prompt)
            withContext(dispatchers.main()) {
                logcat { "Duck.ai: pageContext prompt $contextPrompt" }
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

    // {
    //     "platform": "android",
    //     "tool": "query",
    //     "query": {
    //     "prompt": "Summarize this page",
    //     "autoSubmit": true
    // },
    //     "pageContext": {
    //     "title": "Page Title",
    //     "url": "https://example.com",
    //     "content": "Extracted DOM text...",
    //     "favicon": [{"href": "data:image/png;base64,...", "rel": "icon"}],
    //     "truncated": false,
    //     "fullContentLength": 1234
    // }
    // }
}
