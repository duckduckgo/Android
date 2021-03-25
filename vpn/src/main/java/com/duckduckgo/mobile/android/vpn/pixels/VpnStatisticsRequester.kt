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
import com.duckduckgo.app.statistics.api.StatisticsRequester
import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService

/**
 * This is a temporary implementation of the [StatisticsUpdater] that replaces the
 * real [StatisticsRequester].
 * Why?
 *  For AppTB F&F release we need to disable atb to avoid privacy issues with small cohorts
 *  In addition, we also need to send pixels instead of calling atb endpoints so that we can get
 *  some retention-like metrics.
 *
 *  THIS CLASS WILL BE REMOVED AFTER appTB F&F RELEASE
 */
class VpnStatisticsRequester(
    private val context: Context,
    private val deviceShieldPixels: DeviceShieldPixels,
) : StatisticsUpdater {

    override fun initializeAtb() {
        deviceShieldPixels.deviceShieldInstalled()
    }

    override fun refreshSearchRetentionAtb() {
        if (TrackerBlockingVpnService.isServiceRunning(context)) {
            deviceShieldPixels.deviceShieldEnabledOnSearch()
        } else {
            deviceShieldPixels.deviceShieldDisabledOnSearch()
        }
    }

    override fun refreshAppRetentionAtb() {
        // noop
    }
}
