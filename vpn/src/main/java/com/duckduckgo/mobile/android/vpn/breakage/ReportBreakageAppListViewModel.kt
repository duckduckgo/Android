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

package com.duckduckgo.mobile.android.vpn.breakage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppsRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class ReportBreakageAppListViewModel @Inject constructor(
    private val trackingProtectionAppsRepository: TrackingProtectionAppsRepository
) : ViewModel() {

    private val selectedAppFlow = MutableStateFlow<InstalledApp?>(null)

    private val command = Channel<ReportBreakageAppListView.Command>(1, BufferOverflow.DROP_OLDEST)
    internal fun commands(): Flow<ReportBreakageAppListView.Command> = command.receiveAsFlow()

    internal suspend fun getInstalledApps(): Flow<ReportBreakageAppListView.State> {
        return trackingProtectionAppsRepository.getProtectedApps()
            .combine(selectedAppFlow.asStateFlow()) { apps, selectedApp ->
                val installedApps = apps.map { InstalledApp(it.packageName, it.name) }
                selectedApp?.let { appSelected ->
                    installedApps.update(appSelected)
                } ?: installedApps
            }
            .map { ReportBreakageAppListView.State(installedApps = it, canSubmit = it.hasSelected()) }
    }

    internal fun onAppSelected(app: InstalledApp) {
        viewModelScope.launch {
            selectedAppFlow.emit(app.copy(isSelected = true))
        }
    }

    internal fun onSubmitBreakage() {
        viewModelScope.launch {
            selectedAppFlow.value?.let { command.send(ReportBreakageAppListView.Command.LaunchBreakageForm(it)) }
        }
    }

    internal fun onBreakageSubmitted(issueReport: IssueReport) {
        viewModelScope.launch {
            selectedAppFlow.value?.let {
                command.send(ReportBreakageAppListView.Command.SendBreakageInfo(issueReport.copy(appPackageId = it.packageName)))
            }
        }
    }

    private fun List<InstalledApp>.update(newValue: InstalledApp): List<InstalledApp> {
        return toMutableList().map {
            if (it.packageName == newValue.packageName) newValue else it
        }
    }

    private fun List<InstalledApp>.hasSelected() = find { it.isSelected } != null
}
