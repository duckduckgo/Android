/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.rekey

import androidx.work.WorkManager
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.rekey.NetPRekeyScheduler.Companion.DAILY_NETP_REKEY_TAG
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import logcat.logcat

interface NetPRekeyer {
    suspend fun doRekey()
}

@ContributesBinding(AppScope::class)
class RealNetPRekeyer @Inject constructor(
    private val workManager: WorkManager,
    private val networkProtectionRepository: NetworkProtectionRepository,
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
    private val networkProtectionPixels: NetworkProtectionPixels,
) : NetPRekeyer {

    override suspend fun doRekey() {
        logcat { "Rekeying client" }
        networkProtectionRepository.privateKey = null
        if (vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)) {
            logcat { "Restarting VPN after clearing client keys" }
            vpnFeaturesRegistry.refreshFeature(NetPVpnFeature.NETP_VPN)
        } else {
            logcat { "Cancelling scheduled rekey" }
            workManager.cancelUniqueWork(DAILY_NETP_REKEY_TAG)
        }
        networkProtectionPixels.reportRekeyCompleted()
    }
}
