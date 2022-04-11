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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
<<<<<<< HEAD:vpn/src/main/java/com/duckduckgo/mobile/android/vpn/apps/ManageAppsProtectionViewModel.kt
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.di.scopes.AppScope
=======
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.apps.ui.ManuallyDisableAppProtectionDialog
>>>>>>> develop:vpn/src/main/java/com/duckduckgo/mobile/android/vpn/apps/ExcludedAppsViewModel.kt
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageScreen
import com.duckduckgo.mobile.android.vpn.model.BucketizedVpnTracker
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository.TimeWindow
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit.DAYS
import javax.inject.Inject
import javax.inject.Provider
import kotlin.coroutines.coroutineContext

<<<<<<< HEAD:vpn/src/main/java/com/duckduckgo/mobile/android/vpn/apps/ManageAppsProtectionViewModel.kt
@ContributesViewModel(AppScope::class)
class ManageAppsProtectionViewModel @Inject constructor(
=======
@ContributesViewModel(ActivityScope::class)
class ExcludedAppsViewModel @Inject constructor(
>>>>>>> develop:vpn/src/main/java/com/duckduckgo/mobile/android/vpn/apps/ExcludedAppsViewModel.kt
    private val excludedApps: TrackingProtectionAppsRepository,
    private val appTrackersRepository: AppTrackerBlockingStatsRepository,
    private val pixel: DeviceShieldPixels,
    private val vpnStateMonitor: VpnStateMonitor,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()
    private val manualChanges: MutableList<String> = mutableListOf()

    private val defaultTimeWindow = TimeWindow(5, DAYS)

    internal suspend fun getProtectedApps() = excludedApps.getProtectedApps().map { ViewState(it) }

    internal suspend fun getRecentApps() =
        appTrackersRepository.getMostRecentVpnTrackers { defaultTimeWindow.asString() }.map { aggregateDataPerApp(it) }
            .combine(excludedApps.getProtectedApps()) { recentAppsBlocked, protectedApps ->
                recentAppsBlocked.map {
                    protectedApps.first { protectedApp -> protectedApp.packageName == it.packageId }
                }.take(5)
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

        return sourceData
    }

    fun onAppProtectionDisabled(
        appName: String,
        packageName: String,
        report: Boolean
    ) {
        recordManualChange(packageName)
        viewModelScope.launch {

            excludedApps.manuallyExcludedApp(packageName)

            if (report) {
                pixel.didSubmitManuallyDisableAppProtectionDialog()
                command.send(Command.LaunchFeedback(ReportBreakageScreen.IssueDescriptionForm(appName, packageName)))
            } else {
                pixel.didSkipManuallyDisableAppProtectionDialog()
            }
        }
    }

    fun onAppProtectionEnabled(
        packageName: String,
        excludingReason: Int,
        needsPixel: Boolean = false
    ) {
        recordManualChange(packageName)
        viewModelScope.launch {
            excludedApps.manuallyEnabledApp(packageName)
        }
    }

    private fun recordManualChange(packageName: String) {
        if (manualChanges.contains(packageName)) {
            manualChanges.remove(packageName)
        } else {
            manualChanges.add(packageName)
        }
    }

    fun restoreProtectedApps() {
        pixel.restoreDefaultProtectionList()
        manualChanges.clear()
        viewModelScope.launch {
            excludedApps.restoreDefaultProtectedList()
            command.send(Command.RestartVpn)
        }
    }

    fun userMadeChanges() = manualChanges.isNotEmpty()

    fun onLeavingScreen() {
        viewModelScope.launch {
            if (userMadeChanges()) {
                manualChanges.clear()
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
            onAppProtectionEnabled(excludedAppInfo.packageName, excludedAppInfo.knownProblem)
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

    companion object DeviceShieldPixelParameter {
        private const val PACKAGE_NAME = "packageName"
        private const val EXCLUDING_REASON = "reason"
    }
}

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
