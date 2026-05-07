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

package com.duckduckgo.duckchat.impl.ui.nativeinput.attachment

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

interface LimitsHandler {
    val imageUploadLimitReached: StateFlow<Boolean>
    val conversationImagesSent: StateFlow<Int>

    fun setImageUploadLimitReached(reached: Boolean)
    fun addConversationImagesSent(count: Int)
    fun resetConversationImagesSent()
    fun prepareForNewChat(pendingImages: Int)
    fun onNewChatStarted()
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class RealLimitsHandler @Inject constructor(
    @AppCoroutineScope private val appScope: CoroutineScope,
) : LimitsHandler, MainProcessLifecycleObserver {

    private val _imageUploadLimitReached = MutableStateFlow(false)
    override val imageUploadLimitReached: StateFlow<Boolean> = _imageUploadLimitReached

    private val _hasActiveChat = MutableStateFlow(false)
    private val _rawConversationImagesSent = MutableStateFlow(0)

    override val conversationImagesSent: StateFlow<Int> = combine(
        _hasActiveChat,
        _rawConversationImagesSent,
    ) { isActive, count -> if (isActive) count else 0 }
        .stateIn(appScope, SharingStarted.Eagerly, 0)

    @Volatile private var pendingNewChatImages: Int = 0

    override fun onStart(owner: LifecycleOwner) {
        _imageUploadLimitReached.value = false
        _hasActiveChat.value = false
        _rawConversationImagesSent.value = 0
        pendingNewChatImages = 0
    }

    override fun setImageUploadLimitReached(reached: Boolean) {
        _imageUploadLimitReached.value = reached
    }

    override fun addConversationImagesSent(count: Int) {
        _rawConversationImagesSent.value += count
    }

    override fun resetConversationImagesSent() {
        pendingNewChatImages = 0
        _hasActiveChat.value = false
        _rawConversationImagesSent.value = 0
    }

    override fun prepareForNewChat(pendingImages: Int) {
        pendingNewChatImages = pendingImages
    }

    override fun onNewChatStarted() {
        _rawConversationImagesSent.value = pendingNewChatImages
        pendingNewChatImages = 0
        _hasActiveChat.value = true
    }
}
