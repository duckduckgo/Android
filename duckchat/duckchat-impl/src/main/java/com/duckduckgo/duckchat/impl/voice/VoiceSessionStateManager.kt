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

import android.content.Context
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.ui.DuckChatVoiceMicrophoneService
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

interface VoiceSessionStateManager {
    val activeVoiceSessions: Flow<Set<String>>
        get() = flowOf(emptySet())

    fun isVoiceSessionActive(tabId: String): Boolean = false
    fun onVoiceSessionStarted(tabId: String)
    fun onVoiceSessionEnded(tabId: String)

    /**
     * Emits the tab id whenever an end-voice-session action is requested (e.g. from the
     * foreground service notification). Tabs should collect this flow and dispatch the
     * end-voice-session JS event when their id is emitted.
     */
    fun observeTriggerVoiceSessionEnd(): Flow<String>
    fun triggerVoiceSessionEnd(tabId: String)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = VoiceSessionStateManager::class)
@ContributesMultibinding(AppScope::class, boundType = BrowserLifecycleObserver::class)
class RealVoiceSessionStateManager @Inject constructor(
    private val context: Context,
    private val tabRepository: TabRepository,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val duckChatFeature: DuckChatFeature,
) : VoiceSessionStateManager, BrowserLifecycleObserver {

    private val listenJob = ConflatedJob()

    private val _voiceSessionEndTrigger = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _activeVoiceSessions = MutableStateFlow<Set<String>>(emptySet())
    override val activeVoiceSessions: Flow<Set<String>> = _activeVoiceSessions.asStateFlow()

    @Synchronized
    override fun isVoiceSessionActive(tabId: String): Boolean = tabId.isNotBlank() && tabId in _activeVoiceSessions.value

    override fun observeTriggerVoiceSessionEnd(): Flow<String> = _voiceSessionEndTrigger.asSharedFlow()

    override fun triggerVoiceSessionEnd(tabId: String) {
        if (tabId.isBlank()) return
        _voiceSessionEndTrigger.tryEmit(tabId)
    }

    @Synchronized
    override fun onVoiceSessionStarted(tabId: String) {
        if (tabId.isBlank()) return
        _activeVoiceSessions.update { it + tabId }
        if (duckChatFeature.duckAiVoiceChatService().isEnabled()) {
            DuckChatVoiceMicrophoneService.start(context, tabId)
        }
        if (!listenJob.isActive) {
            listenToTabRemoval()
        }
    }

    @Synchronized
    override fun onVoiceSessionEnded(tabId: String) {
        if (tabId.isBlank()) return
        _activeVoiceSessions.update { it - tabId }
        if (_activeVoiceSessions.value.isEmpty()) {
            endAllSessions()
        }
    }

    override fun onOpen(isFreshLaunch: Boolean) {
        if (isFreshLaunch) {
            endAllSessions()
        }
    }

    override fun onExit() {
        endAllSessions()
    }

    @Synchronized
    private fun endAllSessions() {
        listenJob.cancel()
        _activeVoiceSessions.value = emptySet()
        DuckChatVoiceMicrophoneService.stop(context)
    }

    private fun listenToTabRemoval() {
        listenJob += appCoroutineScope.launch {
            tabRepository.flowTabs.drop(1).collect { tabs ->
                val existingTabIds = tabs.mapTo(mutableSetOf()) { it.tabId }
                synchronized(this@RealVoiceSessionStateManager) {
                    _activeVoiceSessions.update { current ->
                        current.filterTo(mutableSetOf()) { it in existingTabIds }
                    }
                    if (_activeVoiceSessions.value.isEmpty()) {
                        endAllSessions()
                    }
                }
            }
        }
    }
}
