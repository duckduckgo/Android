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

package com.duckduckgo.networkprotection.impl

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureSettings
import com.duckduckgo.feature.toggles.api.RemoteFeatureStoreNamed
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.store.db.AutoExcludeDao
import com.duckduckgo.networkprotection.store.db.VpnIncompatibleApp
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat

@ContributesBinding(AppScope::class)
@RemoteFeatureStoreNamed(VpnRemoteFeatures::class)
class VpnRemoteSettingsStore @Inject constructor(
    private val autoExcludeDao: AutoExcludeDao,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val networkProtectionState: NetworkProtectionState,
) : FeatureSettings.Store {

    private val jsonAdapter = Moshi.Builder().build().adapter(AutoExcludeModel::class.java)

    override fun store(jsonString: String) {
        logcat { "Received configuration: $jsonString" }

        runCatching {
            jsonAdapter.fromJson(jsonString)?.let { model ->

                val autoExcludeApps = model.autoExcludeApps

                autoExcludeDao.upsertAutoExcludeApps(autoExcludeApps)

                // Restart VPN now that the lists were updated
                coroutineScope.launch(dispatcherProvider.io()) {
                    networkProtectionState.restart()
                }
            }
        }.onFailure {
            logcat(WARN) { it.asLog() }
        }
    }

    private data class AutoExcludeModel(
        val autoExcludeApps: List<VpnIncompatibleApp>,
    )
}
