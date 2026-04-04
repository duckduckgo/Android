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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.api.NtpAfterIdleManager
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
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
    private val ntpAfterIdleManager: NtpAfterIdleManager,
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
            logcat { "FirstScreen: timeout is $timeoutMs ms and lastBackgrounded is $lastBackgrounded" }
            val elapsed = System.currentTimeMillis() - lastBackgrounded
            logcat { "FirstScreen: time elapsed $elapsed" }
            if (lastBackgrounded == 0L || elapsed >= timeoutMs) {
                logcat { "FirstScreen: handleAppLaunchOption (after idle)" }
                ntpAfterIdleManager.onNtpShownAfterIdle()
                showOnAppLaunchOptionHandler.handleAppLaunchOption()
            }
            return
        }

        if (isFreshLaunch && showOnAppLaunchFeature.self().isEnabled()) {
            ntpAfterIdleManager.onNtpShownUserInitiated()
            showOnAppLaunchOptionHandler.handleAppLaunchOption()
        }
    }

    override fun onClose() {
        settingsDataStore.lastSessionBackgroundTimestamp = System.currentTimeMillis()
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
