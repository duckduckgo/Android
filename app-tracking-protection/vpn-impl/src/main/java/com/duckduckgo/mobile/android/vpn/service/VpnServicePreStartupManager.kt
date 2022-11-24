/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.service

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.VpnFeature
import com.duckduckgo.mobile.android.vpn.model.VpnServiceState
import com.duckduckgo.mobile.android.vpn.model.VpnServiceStateStats
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.vpn.di.VpnCoroutineScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface VpnServicePreStartupManager {
    fun initiatePreStartup(
        feature: VpnFeature,
        onComplete: () -> Unit,
    )
}

@ContributesBinding(AppScope::class)
class RealVpnServicePreStartupManager @Inject constructor(
    @VpnCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val vpnServicePreStartupCallback: PluginPoint<VpnServicePreStartupCallback>,
    private val vpnDatabase: VpnDatabase,
) : VpnServicePreStartupManager {
    override fun initiatePreStartup(
        feature: VpnFeature,
        onComplete: () -> Unit,
    ) {
        val plugins = vpnServicePreStartupCallback.getPlugins().filter { it.supportsFeature(feature) }
        if (plugins.isNotEmpty()) {
            coroutineScope.launch(dispatcherProvider.io()) {
                vpnDatabase.vpnServiceStateDao().insert(VpnServiceStateStats(state = VpnServiceState.CONNECTING))
                plugins.forEach {
                    it.prepBeforeServiceStarts()
                }
                onComplete()
            }
        } else {
            onComplete()
        }
    }
}
