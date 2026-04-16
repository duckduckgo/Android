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

package com.duckduckgo.app.generalsettings.showonapplaunch

import androidx.core.net.toUri
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = BrowserLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class FirstScreenHandlerImpl @Inject constructor(
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val showOnAppLaunchFeature: ShowOnAppLaunchFeature,
    private val settingsDataStore: SettingsDataStore,
    private val showOnAppLaunchOptionHandler: ShowOnAppLaunchOptionHandler,
    private val dispatcherProvider: DispatcherProvider,
    private val duckChat: DuckChat,
    private val tabRepository: TabRepository,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : BrowserLifecycleObserver {

    override fun onOpen(isFreshLaunch: Boolean) {
        appCoroutineScope.launch {
            handleFirstScreen(isFreshLaunch)
        }
    }

    private suspend fun handleFirstScreen(isFreshLaunch: Boolean) {
        if (androidBrowserConfigFeature.showNTPAfterIdleReturn().isEnabled()) {
            val timeoutMs = getTimeoutSeconds() * 1000
            val lastBackgrounded = settingsDataStore.lastSessionBackgroundTimestamp
            val elapsed = System.currentTimeMillis() - lastBackgrounded
            if (lastBackgrounded == 0L || elapsed >= timeoutMs) {
                if (!isVoiceSessionActiveOnCurrentTab()) {
                    showOnAppLaunchOptionHandler.handleAfterInactivityOption()
                }
                return
            }
        } else if (isFreshLaunch && showOnAppLaunchFeature.self().isEnabled()) {
            if (!isVoiceSessionActiveOnCurrentTab()) {
                showOnAppLaunchOptionHandler.handleAppLaunchOption()
            }
        }
    }

    private suspend fun isVoiceSessionActiveOnCurrentTab(): Boolean {
        if (!duckChat.isVoiceSessionActive()) return false
        val selectedTab = tabRepository.getSelectedTab()
        return selectedTab?.url?.toUri()?.let {
            duckChat.isDuckChatUrl(it)
        } == true
    }

    override fun onClose() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            settingsDataStore.lastSessionBackgroundTimestamp = System.currentTimeMillis()
        }
    }

    private fun getTimeoutSeconds(): Long {
        val userPref = settingsDataStore.userSelectedIdleThresholdSeconds
        if (userPref != null) return userPref

        return parseDefaultIdleThresholdSeconds(androidBrowserConfigFeature.showNTPAfterIdleReturn().getSettings())
            ?: DEFAULT_IDLE_THRESHOLD_SECONDS
    }

    companion object {
        const val DEFAULT_IDLE_THRESHOLD_SECONDS = 300L
        val DEFAULT_IDLE_THRESHOLD_OPTIONS = listOf(1L, 60L, 300L, 600L, 1800L, 3600L, 43200L, 86400L)

        fun parseDefaultIdleThresholdSeconds(settingsJson: String?): Long? {
            if (settingsJson == null) return null
            return try {
                val json = JSONObject(settingsJson)
                if (json.has("defaultIdleThresholdSeconds")) json.getLong("defaultIdleThresholdSeconds") else null
            } catch (e: Exception) {
                null
            }
        }
    }
}
