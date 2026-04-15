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

package com.duckduckgo.duckchat.impl.voice

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

interface VoiceSessionStateManager {
    val isVoiceSessionActive: StateFlow<Boolean>
    fun onVoiceSessionStarted()
    fun onVoiceSessionEnded()
}

@ContributesBinding(AppScope::class)
class RealVoiceSessionStateManager @Inject constructor() : VoiceSessionStateManager {

    private val _isVoiceSessionActive = MutableStateFlow(false)
    override val isVoiceSessionActive: StateFlow<Boolean> = _isVoiceSessionActive.asStateFlow()

    override fun onVoiceSessionStarted() {
        _isVoiceSessionActive.value = true
    }

    override fun onVoiceSessionEnded() {
        _isVoiceSessionActive.value = false
    }
}
