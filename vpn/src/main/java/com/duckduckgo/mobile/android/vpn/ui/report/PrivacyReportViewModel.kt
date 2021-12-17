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

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.model.dateOfLastHour
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.ui.onboarding.DeviceShieldOnboardingStore
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PrivacyReportViewModel(
    private val repository: AppTrackerBlockingStatsRepository,
    private val deviceShieldPixels: DeviceShieldPixels,
    private val deviceShieldOnboarding: DeviceShieldOnboardingStore,
    private val applicationContext: Context,
) : ViewModel(), LifecycleObserver {

    private val vpnRunningState =
        MutableStateFlow(PrivacyReportView.RunningState(isRunning = true, hasValueChanged = false))

    val viewStateFlow =
        vpnRunningState.combine(getReport()) { runningState, trackersBlocked ->
            PrivacyReportView.ViewState(
                runningState.isRunning,
                runningState.hasValueChanged,
                trackersBlocked,
                deviceShieldOnboarding.didShowOnboarding())
        }

    fun pollDeviceShieldState() {
        viewModelScope.launch {
            while (isActive) {
                val isRunning = TrackerBlockingVpnService.isServiceRunning(applicationContext)
                val oldValue = vpnRunningState.value
                val hasValueChanged = oldValue.isRunning != isRunning
                vpnRunningState.emit(PrivacyReportView.RunningState(isRunning, hasValueChanged))

                delay(1_000)
            }
        }
    }

    @VisibleForTesting
    fun getReport(): Flow<PrivacyReportView.TrackersBlocked> {
        return repository
            .getVpnTrackers({ dateOfLastHour() })
            .map { trackers ->
                if (trackers.isEmpty()) {
                    PrivacyReportView.TrackersBlocked("", 0, 0)
                } else {
                    val perApp =
                        trackers.groupBy { it.trackingApp }.toList().sortedByDescending {
                            it.second.size
                        }
                    val otherAppsSize = (perApp.size - 1).coerceAtLeast(0)
                    val latestApp = perApp.first().first.appDisplayName

                    PrivacyReportView.TrackersBlocked(latestApp, otherAppsSize, trackers.size)
                }
            }
            .onStart { pollDeviceShieldState() }
    }

    object PrivacyReportView {
        data class ViewState(
            val isRunning: Boolean,
            val hasValueChanged: Boolean,
            val trackersBlocked: TrackersBlocked,
            val onboardingComplete: Boolean
        )
        data class RunningState(val isRunning: Boolean, val hasValueChanged: Boolean)
        data class TrackersBlocked(
            val latestApp: String,
            val otherAppsSize: Int,
            val trackers: Int
        )
    }
}

@ContributesMultibinding(AppScope::class)
class PrivacyReportViewModelFactory
@Inject
constructor(
    private val appTrackerBlockingStatsRepository: AppTrackerBlockingStatsRepository,
    private val deviceShieldPixels: DeviceShieldPixels,
    private val deviceShieldOnboardingStore: DeviceShieldOnboardingStore,
    private val context: Context
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(PrivacyReportViewModel::class.java) ->
                    (PrivacyReportViewModel(
                        appTrackerBlockingStatsRepository,
                        deviceShieldPixels,
                        deviceShieldOnboardingStore,
                        context) as
                        T)
                else -> null
            }
        }
    }
}
