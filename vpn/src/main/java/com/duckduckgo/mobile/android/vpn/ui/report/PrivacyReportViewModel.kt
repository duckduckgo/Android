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

package com.duckduckgo.mobile.android.vpn.ui.report

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.ViewModel
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.formatters.time.model.dateOfLastHour
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.vpn.feature.removal.VpnFeatureRemover
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.ui.onboarding.VpnStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class PrivacyReportViewModel @Inject constructor(
    private val repository: AppTrackerBlockingStatsRepository,
    private val vpnStore: VpnStore,
    private val vpnFeatureRemover: VpnFeatureRemover,
    vpnStateMonitor: VpnStateMonitor,
    private val dispatchers: DispatcherProvider
) : ViewModel(), LifecycleObserver {

    val viewStateFlow = vpnStateMonitor.getStateFlow().combine(getReport()) { vpnState, trackersBlocked ->
        PrivacyReportView.ViewState(vpnState, trackersBlocked, shouldShowCTA())
    }

    @VisibleForTesting
    fun getReport(): Flow<PrivacyReportView.TrackersBlocked> {
        return repository.getVpnTrackers({ dateOfLastHour() }).map { trackers ->
            if (trackers.isEmpty()) {
                PrivacyReportView.TrackersBlocked("", 0, 0)
            } else {
                val perApp = trackers.groupBy { it.trackingApp }.toList().sortedByDescending { it.second.size }
                val otherAppsSize = (perApp.size - 1).coerceAtLeast(0)
                val latestApp = perApp.first().first.appDisplayName

                PrivacyReportView.TrackersBlocked(latestApp, otherAppsSize, trackers.size)
            }

        }
    }

    private suspend fun shouldShowCTA(): Boolean {
        val isFeatureRemoved = withContext(dispatchers.io()) {
            vpnFeatureRemover.isFeatureRemoved()
        }
        if (isFeatureRemoved) {
            return false
        } else {
            return vpnStore.didShowOnboarding()
        }
    }

    object PrivacyReportView {
        data class ViewState(
            val vpnState: VpnState,
            val trackersBlocked: TrackersBlocked,
            val isFeatureEnabled: Boolean
        )

        data class TrackersBlocked(
            val latestApp: String,
            val otherAppsSize: Int,
            val trackers: Int
        )
    }
}
