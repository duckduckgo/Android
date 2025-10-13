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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.dao.VpnFeatureRemoverState
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnServiceCallbacks::class,
)
class VpnFeatureRemoverStateListener @Inject constructor(
    private val workManager: WorkManager,
    private val vpnDatabase: VpnDatabase,
    private val dispatcherProvider: DispatcherProvider,
) : VpnServiceCallbacks {

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        coroutineScope.launch(dispatcherProvider.io()) {
            logcat { "FeatureRemoverVpnStateListener, new state ENABLED. Descheduling automatic feature removal" }
            resetState()
        }
    }

    private suspend fun resetState() {
        vpnDatabase.vpnFeatureRemoverDao().getState()?.let {
            logcat { "FeatureRemoverVpnStateListener, feature was removed, setting it back to not removed" }
            vpnDatabase.vpnFeatureRemoverDao().insert(VpnFeatureRemoverState(isFeatureRemoved = false))
        }
        workManager.cancelAllWorkByTag(VpnFeatureRemoverWorker.WORKER_VPN_FEATURE_REMOVER_TAG)
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
        if (vpnStopReason is VpnStopReason.SELF_STOP) {
            coroutineScope.launch(dispatcherProvider.io()) {
                logcat { "FeatureRemoverVpnStateListener, new state DISABLED and it was MANUALLY. Scheduling automatic feature removal" }
                scheduleFeatureRemoval()
            }
        }
    }

    private fun scheduleFeatureRemoval() {
        logcat { "Scheduling the VpnFeatureRemoverWorker worker 7 days from now" }
        val request = OneTimeWorkRequestBuilder<VpnFeatureRemoverWorker>()
            .setInitialDelay(7, TimeUnit.DAYS)
            .addTag(VpnFeatureRemoverWorker.WORKER_VPN_FEATURE_REMOVER_TAG)
            .build()

        workManager.enqueueUniqueWork(
            VpnFeatureRemoverWorker.WORKER_VPN_FEATURE_REMOVER_TAG,
            KEEP,
            request,
        )
    }
}
