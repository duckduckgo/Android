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

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

interface LimitsHandler {
    val imageUploadLimitReached: StateFlow<Boolean>
    val conversationImagesSent: StateFlow<Int>

    fun setImageUploadLimitReached(reached: Boolean)
    fun setConversationImagesUsed(count: Int)
    fun resetConversationImagesSent()
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = LimitsHandler::class)
class RealLimitsHandler @Inject constructor() : LimitsHandler {

    private val _imageUploadLimitReached = MutableStateFlow(false)
    override val imageUploadLimitReached: StateFlow<Boolean> = _imageUploadLimitReached

    private val _conversationImagesSent = MutableStateFlow(0)
    override val conversationImagesSent: StateFlow<Int> = _conversationImagesSent

    override fun setImageUploadLimitReached(reached: Boolean) {
        _imageUploadLimitReached.value = reached
    }

    override fun setConversationImagesUsed(count: Int) {
        _conversationImagesSent.value = count
    }

    override fun resetConversationImagesSent() {
        _conversationImagesSent.value = 0
    }
}
