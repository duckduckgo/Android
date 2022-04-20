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

package com.duckduckgo.mobile.android.vpn.feature.removal

import androidx.work.ExistingWorkPolicy.KEEP
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.model.VpnStoppingReason.SELF_STOP
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnServiceCallbacks::class
)
@SingleInstanceIn(VpnScope::class)
class FeatureRemoverVpnStateListener @Inject constructor(
    private val workManager: WorkManager,
) : VpnServiceCallbacks {

    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        coroutineScope.launch(dispatcher) {
            Timber.d("FeatureRemoverVpnStateListener, new state ENABLED. Descheduling automatic feature removal")

        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason
    ) {
        coroutineScope.launch(dispatcher) {
            if (vpnStopReason == VpnStopReason.SELF_STOP) {
                Timber.d("FeatureRemoverVpnStateListener, new state DISABLED and it was MANUALLY. Scheduling automatic feature removal")
                scheduleFeatureRemoval()
            }
        }
    }

    private fun scheduleFeatureRemoval() {
        Timber.v("Scheduling the VpnFeatureRemoverWorker worker 7 days from now")
        val request = OneTimeWorkRequestBuilder<VpnFeatureRemoverWorker>()
            .setInitialDelay(15, TimeUnit.MINUTES)
            .addTag(VpnFeatureRemoverWorker.WORKER_VPN_FEATURE_REMOVER_TAG)
            .build()

        workManager.enqueueUniqueWork(
            VpnFeatureRemoverWorker.WORKER_VPN_FEATURE_REMOVER_TAG,
            KEEP,
            request
        )
    }
}
