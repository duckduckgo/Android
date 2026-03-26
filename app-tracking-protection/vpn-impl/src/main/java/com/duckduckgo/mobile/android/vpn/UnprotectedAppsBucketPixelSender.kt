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

package com.duckduckgo.mobile.android.vpn

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppsRepository
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnServiceCallbacks::class,
)
class UnprotectedAppsBucketPixelSender @Inject constructor(
    private val excludedApps: TrackingProtectionAppsRepository,
    private val deviceShieldPixels: DeviceShieldPixels,
    private val dispatcherProvider: DispatcherProvider,
) : VpnServiceCallbacks {

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        coroutineScope.launch(dispatcherProvider.io()) {
            excludedApps.getAppsAndProtectionInfo().collectLatest { list ->
                val totalCount = list.size
                if (totalCount != 0) {
                    val unprotectedCount = list.count { it.isExcluded }
                    val percentage = unprotectedCount / totalCount.toDouble() * 100
                    val bucketSize = when {
                        percentage <= BUCKET_SIZE_20 -> BUCKET_SIZE_20
                        percentage <= BUCKET_SIZE_40 -> BUCKET_SIZE_40
                        percentage <= BUCKET_SIZE_60 -> BUCKET_SIZE_60
                        percentage <= BUCKET_SIZE_80 -> BUCKET_SIZE_80
                        else -> BUCKET_SIZE_100
                    }
                    deviceShieldPixels.reportUnprotectedAppsBucket(bucketSize)
                }
            }
        }
    }

    override fun onVpnStopped(coroutineScope: CoroutineScope, vpnStopReason: VpnStateMonitor.VpnStopReason) {
        // no-op
    }

    companion object {
        private const val BUCKET_SIZE_20 = 20
        private const val BUCKET_SIZE_40 = 40
        private const val BUCKET_SIZE_60 = 60
        private const val BUCKET_SIZE_80 = 80
        private const val BUCKET_SIZE_100 = 100
    }
}
