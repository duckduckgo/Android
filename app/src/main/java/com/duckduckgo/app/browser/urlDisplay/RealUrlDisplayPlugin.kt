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

package com.duckduckgo.app.browser.urlDisplay

import android.content.Context
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PrivacyConfigCallbackPlugin::class,
)
class RealUrlDisplayPlugin @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val browserConfigFeature: AndroidBrowserConfigFeature,
    private val context: Context,
) : PrivacyConfigCallbackPlugin {
    override fun onPrivacyConfigDownloaded() {
        // User has explicitly set their preference - always honor it
        if (settingsDataStore.hasUrlPreferenceSet()) {
            return
        }
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val isNewInstall = packageInfo.firstInstallTime == packageInfo.lastUpdateTime
        val defaultValue = !(isNewInstall && browserConfigFeature.shorterUrlDefault().isEnabled())
        settingsDataStore.isFullUrlEnabled = defaultValue
    }
}
