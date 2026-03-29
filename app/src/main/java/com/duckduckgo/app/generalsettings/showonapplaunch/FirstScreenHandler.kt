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
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
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
                logcat { "FirstScreen: handleAppLaunchOption" }
                showOnAppLaunchOptionHandler.handleAppLaunchOption()
            }
            return
        }

        if (isFreshLaunch && showOnAppLaunchFeature.self().isEnabled()) {
            showOnAppLaunchOptionHandler.handleAppLaunchOption()
        }
    }

    override fun onClose() {
        settingsDataStore.lastSessionBackgroundTimestamp = System.currentTimeMillis()
    }

    internal fun getTimeoutSeconds(): Long {
        val userSelected = settingsDataStore.userSelectedIdleThresholdSeconds
        if (userSelected != null) return userSelected

        val settings = parseIdleThresholdSettings(
            androidBrowserConfigFeature.showNTPAfterIdleReturn().getSettings(),
        ) ?: return DEFAULT_IDLE_THRESHOLD_SECONDS

        return if (settings.idleThresholdOptions.isEmpty() || settings.defaultIdleThresholdSeconds in settings.idleThresholdOptions) {
            settings.defaultIdleThresholdSeconds
        } else {
            DEFAULT_IDLE_THRESHOLD_SECONDS
        }
    }
}
