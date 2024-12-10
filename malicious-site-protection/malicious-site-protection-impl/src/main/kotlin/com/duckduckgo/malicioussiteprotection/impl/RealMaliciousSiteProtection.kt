/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.malicioussiteprotection.impl

import android.net.Uri
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber

@ContributesBinding(AppScope::class, MaliciousSiteProtection::class)
@ContributesMultibinding(AppScope::class, PrivacyConfigCallbackPlugin::class)
class RealMaliciousSiteProtection @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val maliciousSiteProtectionFeature: MaliciousSiteProtectionFeature,
    @IsMainProcess private val isMainProcess: Boolean,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : MaliciousSiteProtection, PrivacyConfigCallbackPlugin {

    private var _isFeatureEnabled = false
    override val isFeatureEnabled: Boolean
        get() = _isFeatureEnabled

    private var hashPrefixUpdateFrequency = 20L
    private var filterSetUpdateFrequency = 720L

    init {
        if (isMainProcess) {
            loadToMemory()
        }
    }

    override fun onPrivacyConfigDownloaded() {
        loadToMemory()
    }

    private fun loadToMemory() {
        appCoroutineScope.launch(dispatchers.io()) {
            _isFeatureEnabled = maliciousSiteProtectionFeature.self().isEnabled()
            maliciousSiteProtectionFeature.self().getSettings()?.let {
                JSONObject(it).let { settings ->
                    hashPrefixUpdateFrequency = settings.getLong("hashPrefixUpdateFrequency")
                    filterSetUpdateFrequency = settings.getLong("filterSetUpdateFrequency")
                }
            }
        }
    }

    override suspend fun isMalicious(url: Uri, onSiteBlockedAsync: () -> Unit): Boolean {
        Timber.tag("MaliciousSiteProtection").d("isMalicious $url")
        // TODO (cbarreiro): Implement the logic to check if the URL is malicious
        return false
    }
}
