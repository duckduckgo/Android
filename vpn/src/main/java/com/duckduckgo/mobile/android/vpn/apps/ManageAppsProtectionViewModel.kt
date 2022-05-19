/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.apps

import androidx.lifecycle.*
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageScreen
import com.duckduckgo.mobile.android.vpn.model.BucketizedVpnTracker
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository.TimeWindow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit.DAYS
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

@ContributesViewModel(ActivityScope::class)
class ManageAppsProtectionViewModel @Inject constructor(
    private val excludedApps: TrackingProtectionAppsRepository,
    private val appTrackersRepository: AppTrackerBlockingStatsRepository,
    private val pixel: DeviceShieldPixels,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel(), DefaultLifecycleObserver {

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()
    private val currentManualProtections = mutableListOf<Pair<String, Boolean>>()
    private val refreshSnapshot = MutableStateFlow(System.currentTimeMillis())
    private val entryProtectionSnapshot = mutableListOf<Pair<String, Boolean>>()
    private var latestSnapshot = 0L
    private val defaultTimeWindow = TimeWindow(5, DAYS)

    private fun MutableStateFlow<Long>.refresh() {
        viewModelScope.launch {
            emit(System.currentTimeMillis())
        }
    }

    init {
        excludedApps.manuallyExcludedApps()
            .combine(refreshSnapshot.asStateFlow()) { excludedApps, timestamp -> ManualProtectionSnapshot(timestamp, excludedApps) }
            .flowOn(dispatcherProvider.io())
            .onEach {
                if (latestSnapshot != it.timestamp) {
                    latestSnapshot = it.timestamp
                    entryProtectionSnapshot.clear()
                    entryProtectionSnapshot.addAll(it.snapshot)
                }
                currentManualProtections.clear()
                currentManualProtections.addAll(it.snapshot)
            }
            .flowOn(dispatcherProvider.main())
            .launchIn(viewModelScope)
    }

    internal suspend fun getProtectedApps() = excludedApps.getAppsAndProtectionInfo().map { ViewState(it) }

    internal suspend fun getRecentApps() =
        appTrackersRepository.getMostRecentVpnTrackers { defaultTimeWindow.asString() }.map { aggregateDataPerApp(it) }
            .combine(excludedApps.getAppsAndProtectionInfo()) { recentAppsBlocked, protectedApps ->
                recentAppsBlocked.map {
                    protectedApps.firstOrNull { protectedApp -> protectedApp.packageName == it.packageId }
                }
                    .filterNotNull()
                    .take(5)
            }.map { ViewState(it) }
            .onStart { pixel.didShowExclusionListActivity() }
            .flowOn(dispatcherProvider.io())

    private suspend fun aggregateDataPerApp(
        trackerData: List<BucketizedVpnTracker>
    ): List<TrackingApp> {
        val sourceData = mutableListOf<TrackingApp>()
        val perSessionData = trackerData.groupBy { it.bucket }

        perSessionData.values.forEach { sessionTrackers ->
            coroutineContext.ensureActive()

            val perAppData = sessionTrackers.groupBy { it.trackerCompanySignal.tracker.trackingApp.packageId }

            perAppData.values.forEach { appTrackers ->
                val item = appTrackers.sortedByDescending { it.trackerCompanySignal.tracker.timestamp }.first()
                sourceData.add(item.trackerCompanySignal.tracker.trackingApp)
            }
        }

        return sourceData.distinct()
    }

    fun onAppProtectionDisabled(
        appName: String,
        packageName: String,
        report: Boolean
    ) {
        pixel.didDisableAppProtectionFromApps()
        viewModelScope.launch {
            excludedApps.manuallyExcludeApp(packageName)
            pixel.didSubmitManuallyDisableAppProtectionDialog()
            if (report) {
                command.send(Command.LaunchFeedback(ReportBreakageScreen.IssueDescriptionForm(appName, packageName)))
            } else {
                pixel.didSkipManuallyDisableAppProtectionDialog()
            }
        }
    }

    fun onAppProtectionEnabled(
        packageName: String
    ) {
        pixel.didEnableAppProtectionFromApps()
        viewModelScope.launch {
            excludedApps.manuallyEnabledApp(packageName)
        }
    }

    fun restoreProtectedApps() {
        pixel.restoreDefaultProtectionList()
        viewModelScope.launch {
            excludedApps.restoreDefaultProtectedList()
            // as product wanted to restart VPN as soon as we restored protections, we need to refresh the snapshot here
            refreshSnapshot.refresh()
            command.send(Command.RestartVpn)
        }
    }

    fun userMadeChanges(): Boolean {
        // User made changes when the manual protections entry snapshot is different from the current snapshot
        if (currentManualProtections.size != entryProtectionSnapshot.size) return true

        currentManualProtections.forEach { protection ->
            entryProtectionSnapshot.firstOrNull { it.first == protection.first }?.let { match ->
                if (match.second != protection.second) return true
            }
        }

        return false
    }

    fun canRestoreDefaults() = currentManualProtections.isNotEmpty()

    override fun onResume(owner: LifecycleOwner) {
        refreshSnapshot.refresh()
    }

    override fun onPause(owner: LifecycleOwner) {
        onLeavingScreen()
    }

    private fun onLeavingScreen() {
        viewModelScope.launch {
            if (userMadeChanges()) {
                command.send(Command.RestartVpn)
            }
        }
    }

    fun onAppProtectionChanged(
        excludedAppInfo: TrackingProtectionAppInfo,
        position: Int,
        enabled: Boolean
    ) {
        viewModelScope.launch {
            if (enabled) {
                checkForAppProtectionEnabled(excludedAppInfo, position)
            } else {
                checkForAppProtectionDisabled(excludedAppInfo)
            }
        }
    }

    private suspend fun checkForAppProtectionEnabled(
        excludedAppInfo: TrackingProtectionAppInfo,
        position: Int
    ) {
        if (!excludedAppInfo.isProblematic()) {
            onAppProtectionEnabled(excludedAppInfo.packageName)
        } else {
            command.send(Command.ShowEnableProtectionDialog(excludedAppInfo, position))
        }
    }

    private suspend fun checkForAppProtectionDisabled(excludedAppInfo: TrackingProtectionAppInfo) {
        if (!excludedAppInfo.isProblematic()) {
            command.send(Command.ShowDisableProtectionDialog(excludedAppInfo))
        } else {
            onAppProtectionDisabled(appName = excludedAppInfo.name, packageName = excludedAppInfo.packageName, report = false)
        }
    }

    fun launchFeedback() {
        pixel.launchAppTPFeedback()
        viewModelScope.launch {
            command.send(Command.LaunchFeedback(ReportBreakageScreen.ListOfInstalledApps))
        }
    }

    fun launchManageAppsProtection() {
        pixel.didOpenExclusionListActivityFromManageAppsProtectionScreen()
        viewModelScope.launch {
            command.send(Command.LaunchAllAppsProtection)
        }
    }
}

private data class ManualProtectionSnapshot(
    val timestamp: Long,
    val snapshot: List<Pair<String, Boolean>>
)

internal data class ViewState(val excludedApps: List<TrackingProtectionAppInfo>)
internal sealed class Command {
    object RestartVpn : Command()
    data class LaunchFeedback(val reportBreakageScreen: ReportBreakageScreen) : Command()
    object LaunchAllAppsProtection : Command()
    data class ShowEnableProtectionDialog(
        val excludingReason: TrackingProtectionAppInfo,
        val position: Int
    ) : Command()

    data class ShowDisableProtectionDialog(val excludingReason: TrackingProtectionAppInfo) : Command()
}
