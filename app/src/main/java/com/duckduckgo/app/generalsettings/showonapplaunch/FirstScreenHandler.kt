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
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
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
    private val tabRepository: TabRepository,
    private val showOnAppLaunchOptionHandler: ShowOnAppLaunchOptionHandler,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : BrowserLifecycleObserver {

    override fun onOpen(isFreshLaunch: Boolean) {
        appCoroutineScope.launch {
            handleFirstScreen()
        }
    }

    private suspend fun handleFirstScreen() {
        if (androidBrowserConfigFeature.showNTPAfterIdleReturn().isEnabled()) {
            val timeoutMs = getTimeoutMs()
            if (timeoutMs != null) {
                val lastBackgrounded = settingsDataStore.lastSessionBackgroundTimestamp
                val elapsed = System.currentTimeMillis() - lastBackgrounded
                if (lastBackgrounded == 0L || elapsed >= timeoutMs) {
                    tabRepository.add()
                    return
                }
            }
        }

        if (showOnAppLaunchFeature.self().isEnabled()) {
            showOnAppLaunchOptionHandler.handleAppLaunchOption()
        }
    }

    override fun onClose() {
        settingsDataStore.lastSessionBackgroundTimestamp = System.currentTimeMillis()
    }

    private fun getTimeoutMs(): Long? {
        val settings = androidBrowserConfigFeature.showNTPAfterIdleReturn().getSettings()
            ?: return null
        return runCatching {
            JSONObject(settings).getLong("timeoutMinutes") * 60 * 1000
        }.getOrNull()
    }

}
