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
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.apps.ui.ManuallyDisableAppProtectionDialog
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageScreen
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

class ExcludedAppsViewModel(
    private val excludedApps: TrackingProtectionAppsRepository,
    private val pixel: DeviceShieldPixels
) : ViewModel() {

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()
    private val manualChanges: MutableList<String> = mutableListOf()

    internal suspend fun getProtectedApps() = excludedApps.getProtectedApps().map { ViewState(it) }

    fun onAppProtectionDisabled(answer: Int = 0, appName: String, packageName: String, skippedReport: Boolean) {
        if (skippedReport) {
            pixel.disableAppProtectionReportingSkipped()
        } else {
            if (answer > ManuallyDisableAppProtectionDialog.NO_REASON_NEEDED) {
                pixel.disableAppProtection(mapOf(PACKAGE_NAME to packageName, EXCLUDING_REASON to answer.toString()))
            }
        }

        recordManualChange(packageName)

        viewModelScope.launch {
            excludedApps.manuallyExcludedApp(packageName)
            if (answer == ManuallyDisableAppProtectionDialog.STOPPED_WORKING) {
                command.send(Command.LaunchFeedback(ReportBreakageScreen.IssueDescriptionForm(appName, packageName)))
            }
        }
    }

    fun onAppProtectionEnabled(packageName: String, excludingReason: Int, needsPixel: Boolean = false) {
        recordManualChange(packageName)
        if (needsPixel && excludingReason > 0) {
            pixel.enableAppProtection(mapOf(PACKAGE_NAME to packageName, EXCLUDING_REASON to excludingReason.toString()))
        }
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
                command.send(Command.RestartVpn)
            }
        }
    }

    fun onAppProtectionChanged(excludedAppInfo: TrackingProtectionAppInfo, position: Int, enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                checkForAppProtectionEnabled(excludedAppInfo, position)
            } else {
                checkForAppProtectionDisabled(excludedAppInfo)
            }
        }
    }

    private suspend fun checkForAppProtectionEnabled(excludedAppInfo: TrackingProtectionAppInfo, position: Int) {
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
            onAppProtectionDisabled(
                ManuallyDisableAppProtectionDialog.NO_REASON_NEEDED,
                appName = excludedAppInfo.name,
                packageName = excludedAppInfo.packageName,
                skippedReport = false
            )
        }
    }

    fun launchFeedback() {
        pixel.launchAppTPFeedback()
        viewModelScope.launch {
            command.send(Command.LaunchFeedback(ReportBreakageScreen.ListOfInstalledApps))
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
    data class ShowEnableProtectionDialog(val excludingReason: TrackingProtectionAppInfo, val position: Int) : Command()
    data class ShowDisableProtectionDialog(val excludingReason: TrackingProtectionAppInfo) : Command()
}

@ContributesMultibinding(AppObjectGraph::class)
class ExcludedAppsViewModelFactory @Inject constructor(
    private val deviceShieldExcludedApps: Provider<TrackingProtectionAppsRepository>,
    private val deviceShieldPixels: Provider<DeviceShieldPixels>
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(ExcludedAppsViewModel::class.java) -> (
                    ExcludedAppsViewModel(
                        deviceShieldExcludedApps.get(),
                        deviceShieldPixels.get()
                    ) as T
                    )
                else -> null
            }
        }
    }
}
