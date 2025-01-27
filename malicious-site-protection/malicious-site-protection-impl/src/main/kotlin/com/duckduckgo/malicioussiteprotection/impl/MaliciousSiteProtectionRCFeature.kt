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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

interface MaliciousSiteProtectionRCFeature {
    fun isFeatureEnabled(): Boolean
    fun getHashPrefixUpdateFrequency(): Long
    fun getFilterSetUpdateFrequency(): Long
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, MaliciousSiteProtectionRCFeature::class)
@ContributesMultibinding(AppScope::class, PrivacyConfigCallbackPlugin::class)
class RealMaliciousSiteProtectionRCFeature @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val maliciousSiteProtectionFeature: MaliciousSiteProtectionFeature,
    @IsMainProcess private val isMainProcess: Boolean,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : MaliciousSiteProtectionRCFeature, PrivacyConfigCallbackPlugin {
    private var isFeatureEnabled = false

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

    override fun getFilterSetUpdateFrequency(): Long {
        return filterSetUpdateFrequency
    }

    override fun getHashPrefixUpdateFrequency(): Long {
        return hashPrefixUpdateFrequency
    }

    override fun isFeatureEnabled(): Boolean {
        return isFeatureEnabled
    }

    private fun loadToMemory() {
        appCoroutineScope.launch(dispatchers.io()) {
            isFeatureEnabled = maliciousSiteProtectionFeature.self().isEnabled()
            maliciousSiteProtectionFeature.self().getSettings()?.let {
                JSONObject(it).let { settings ->
                    hashPrefixUpdateFrequency = settings.getLong("hashPrefixUpdateFrequency")
                    filterSetUpdateFrequency = settings.getLong("filterSetUpdateFrequency")
                }
            }
        }
    }
}
