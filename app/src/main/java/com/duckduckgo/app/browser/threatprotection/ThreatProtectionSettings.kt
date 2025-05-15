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

package com.duckduckgo.app.browser.threatprotection

import android.content.Context
import android.view.View
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.ui.view.listitem.SettingsListItem
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.settings.api.ThreatProtectionSettingsPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ContributesMultibinding(ActivityScope::class)
@PriorityKey(100)
class ThreatProtectionSettingsTitle @Inject constructor(
    private val globalActivityStarter: GlobalActivityStarter,
    private val settingsDataStore: SettingsDataStore,
    private val toggle: FeatureToggle,
    private val maliciousSiteProtection: MaliciousSiteProtection,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
) : ThreatProtectionSettingsPlugin {
    override fun getView(context: Context): View {
        return SettingsListItem(context).apply {
            setLeadingIconResource(R.drawable.ic_threat_protection)
            setPrimaryText(context.getString(R.string.threatProtectionTitle))
            setOnClickListener {
                globalActivityStarter.start(this.context, ThreatProtectionSettingsNoParams, null)
            }
            setStatus(false)
            coroutineScope.launch(dispatcherProvider.io()) {
                val scamBlockerEnabled = maliciousSiteProtection.isFeatureEnabled() &&
                    settingsDataStore.maliciousSiteProtectionEnabled &&
                    androidBrowserConfigFeature.enableMaliciousSiteProtection().isEnabled()
                val smarterEncryptionEnabled = toggle.isFeatureEnabled(PrivacyFeatureName.HttpsFeatureName.value)
                withContext(dispatcherProvider.main()) {
                    setStatus(scamBlockerEnabled && smarterEncryptionEnabled)
                }
            }
        }
    }
}
