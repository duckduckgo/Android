/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.ui

import android.webkit.WebBackForwardList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.impl.DuckChatConstants.HOST_DUCK_AI
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.helper.RealDuckChatJSHelper.Companion.DUCK_CHAT_FEATURE_NAME
import com.duckduckgo.duckchat.impl.messaging.sync.SyncStatusChangedObserver
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import logcat.logcat
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class DuckChatWebViewViewModel @Inject constructor(
    private val subscriptions: Subscriptions,
    private val duckChat: DuckChatInternal,
    private val syncStatusChangedObserver: SyncStatusChangedObserver,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = DROP_OLDEST)
    val commands = commandChannel.receiveAsFlow()

    private val _subscriptionEventDataChannel = Channel<SubscriptionEventData>(capacity = Channel.BUFFERED)
    val subscriptionEventDataFlow = _subscriptionEventDataChannel.receiveAsFlow()

    sealed class Command {
        data object SendSubscriptionAuthUpdateEvent : Command()
    }

    init {
        observeSubscriptionChanges()
        observeSyncStatusChanges()
    }

    fun handleOnSameWebView(url: String): Boolean {
        // Allow Duck.ai links to load within the same WebView (in-sheet navigation)
        return duckChat.canHandleOnAiWebView(url)
    }

    fun shouldCloseDuckChat(history: WebBackForwardList): Boolean {
        return runCatching {
            if (!duckChat.isStandaloneMigrationEnabled()) return false
            val currentItem = history.currentItem?.url
            val firstItem = history.getItemAtIndex(0).url
            currentItem?.toHttpUrl()?.topPrivateDomain() == HOST_DUCK_AI &&
                firstItem.toHttpUrl().topPrivateDomain() == AppUrl.Url.HOST
        }.getOrElse { false }
    }

    private fun observeSubscriptionChanges() {
        subscriptions.getSubscriptionStatusFlow()
            .distinctUntilChanged()
            .onEach { _ ->
                commandChannel.trySend(Command.SendSubscriptionAuthUpdateEvent)
            }.launchIn(viewModelScope)
    }

    private fun observeSyncStatusChanges() {
        syncStatusChangedObserver.syncStatusChangedEvents
            .onEach { payload ->
                withContext(dispatchers.main()) {
                    val event = SubscriptionEventData(
                        featureName = DUCK_CHAT_FEATURE_NAME,
                        subscriptionName = "submitSyncStatusChanged",
                        params = payload,
                    )
                    _subscriptionEventDataChannel.trySend(event)
                    logcat { "DuckChat-Sync: sent sync status event from DuckChatWebViewViewModel $payload" }
                }
            }
            .flowOn(dispatchers.io())
            .launchIn(viewModelScope)
    }
}
