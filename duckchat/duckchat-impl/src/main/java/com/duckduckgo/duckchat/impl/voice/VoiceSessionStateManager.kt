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
) : VoiceSessionStateManager, BrowserLifecycleObserver {

    private val listenJob = ConflatedJob()

    @Volatile
    private var activeSessionTabId: String? = null

    @Volatile
    override var isVoiceSessionActive: Boolean = false
        private set

    override fun onVoiceSessionStarted(tabId: String) {
        isVoiceSessionActive = true
        activeSessionTabId = tabId.ifBlank { null }
        DuckChatVoiceMicrophoneService.start(context)
        if (activeSessionTabId != null) {
            listenToTabRemoval()
        }
    }

    override fun onVoiceSessionEnded() {
        listenJob.cancel()
        isVoiceSessionActive = false
        activeSessionTabId = null
        DuckChatVoiceMicrophoneService.stop(context)
    }

    override fun onOpen(isFreshLaunch: Boolean) {
        if (isFreshLaunch) {
            if (isVoiceSessionActive) {
                onVoiceSessionEnded()
            }
        }
    }

    override fun onExit() {
        if (isVoiceSessionActive) {
            onVoiceSessionEnded()
        }
    }

    private fun listenToTabRemoval() {
        listenJob += appCoroutineScope.launch {
            tabRepository.flowTabs.drop(1).collect { tabs ->
                if (activeSessionTabId != null && tabs.none { it.tabId == activeSessionTabId }) {
                    onVoiceSessionEnded()
                }
            }
        }
    }
}
