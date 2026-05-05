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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

interface VoiceSessionStateManager {
    val isVoiceSessionActive: Boolean
        get() = false
    fun onVoiceSessionStarted(tabId: String)
    fun onVoiceSessionEnded()
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

    // null = no session, STANDALONE_SESSION_ID = session without a browser tab, non-empty = tab session
    @Volatile
    private var activeSessionTabId: String? = null

    override val isVoiceSessionActive: Boolean
        get() = activeSessionTabId != null

    @Synchronized
    override fun onVoiceSessionStarted(tabId: String) {
        activeSessionTabId = tabId.ifBlank { STANDALONE_SESSION_ID }
        if (duckChatFeature.duckAiVoiceChatService().isEnabled()) {
            DuckChatVoiceMicrophoneService.start(context)
        }
        if (tabId.isNotBlank()) {
            listenToTabRemoval()
        }
    }

    @Synchronized
    override fun onVoiceSessionEnded() {
        listenJob.cancel()
        activeSessionTabId = null
        DuckChatVoiceMicrophoneService.stop(context)
    }

    override fun onOpen(isFreshLaunch: Boolean) {
        if (isFreshLaunch) {
            onVoiceSessionEnded()
        }
    }

    override fun onExit() {
        onVoiceSessionEnded()
    }

    private fun listenToTabRemoval() {
        listenJob += appCoroutineScope.launch {
            tabRepository.flowTabs.drop(1).collect { tabs ->
                val tabId = activeSessionTabId ?: return@collect
                if (tabs.none { it.tabId == tabId }) {
                    onVoiceSessionEnded()
                }
            }
        }
    }

    companion object {
        private const val STANDALONE_SESSION_ID = "__duck_ai_standalone__"
    }
}
