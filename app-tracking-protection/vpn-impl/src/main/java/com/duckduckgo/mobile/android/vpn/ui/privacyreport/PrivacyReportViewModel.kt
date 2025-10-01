/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.ui.privacyreport

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.formatters.time.model.dateOfLastHour
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.feature.removal.VpnFeatureRemover
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.ui.onboarding.VpnStore
import com.duckduckgo.mobile.android.vpn.ui.privacyreport.PrivacyReportViewModel.PrivacyReportView.TrackersBlocked
import com.duckduckgo.mobile.android.vpn.ui.privacyreport.PrivacyReportViewModel.PrivacyReportView.ViewState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesViewModel(ViewScope::class)
class PrivacyReportViewModel @Inject constructor(
    private val repository: AppTrackerBlockingStatsRepository,
    private val vpnStore: VpnStore,
    private val vpnFeatureRemover: VpnFeatureRemover,
    vpnStateMonitor: VpnStateMonitor,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    val viewStateFlow =
        vpnStateMonitor.getStateFlow(AppTpVpnFeature.APPTP_VPN).combine(getReport()) { vpnState, trackersBlocked ->
            ViewState(vpnState, trackersBlocked, shouldShowCTA())
        }

    @VisibleForTesting
    fun getReport(): Flow<TrackersBlocked> =
        repository.getVpnTrackers({ dateOfLastHour() }).map { trackers ->
            if (trackers.isEmpty()) {
                TrackersBlocked("", 0, 0)
            } else {
                val perApp = trackers.groupBy { it.trackingApp }.toList().sortedByDescending { it.second.sumOf { t -> t.count } }
                val otherAppsSize = (perApp.size - 1).coerceAtLeast(0)
                val latestApp = perApp.first().first.appDisplayName

                TrackersBlocked(latestApp, otherAppsSize, trackers.sumOf { it.count })
            }
        }

    private suspend fun shouldShowCTA(): Boolean =
        withContext(dispatchers.io()) {
            if (vpnFeatureRemover.isFeatureRemoved()) {
                false
            } else {
                vpnStore.didShowOnboarding()
            }
        }

    object PrivacyReportView {
        data class ViewState(
            val vpnState: VpnState,
            val trackersBlocked: TrackersBlocked,
            val isFeatureEnabled: Boolean,
        )

        data class TrackersBlocked(
            val latestApp: String,
            val otherAppsSize: Int,
            val trackers: Int,
        )
    }
}
