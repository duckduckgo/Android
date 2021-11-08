/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.pixels

import android.content.Context
import com.duckduckgo.app.statistics.api.RefreshRetentionAtbPlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppObjectGraph::class)
class DeviceShieldRetentionPixelSender @Inject constructor(
    private val context: Context,
    private val deviceShieldPixels: DeviceShieldPixels,
    vpnDatabase: VpnDatabase
) : RefreshRetentionAtbPlugin {

    private val serviceStateStatsDao = vpnDatabase.vpnServiceStateDao()

    override fun onSearchRetentionAtbRefreshed() {
        if (TrackerBlockingVpnService.isServiceRunning(context)) {
            deviceShieldPixels.deviceShieldEnabledOnSearch()
        } else {
            deviceShieldPixels.deviceShieldDisabledOnSearch()
        }
    }

    override fun onAppRetentionAtbRefreshed() {
        if (TrackerBlockingVpnService.isServiceRunning(context)) {
            deviceShieldPixels.deviceShieldEnabledOnAppLaunch()
        } else {
            if (serviceStateStatsDao.getEnableCount() == 0) {
                deviceShieldPixels.deviceShieldNeverEnabledOnAppLaunch()
            } else {
                deviceShieldPixels.deviceShieldDisabledOnAppLaunch()
            }
        }
    }
}
