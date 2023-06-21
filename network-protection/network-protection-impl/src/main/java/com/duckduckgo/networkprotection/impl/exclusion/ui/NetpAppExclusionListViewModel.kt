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

package com.duckduckgo.networkprotection.impl.exclusion.ui

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.exclusion.SystemAppOverridesProvider
import com.duckduckgo.mobile.android.vpn.ui.AppBreakageCategory
import com.duckduckgo.mobile.android.vpn.ui.OpenVpnBreakageCategoryWithBrokenApp
import com.duckduckgo.networkprotection.impl.R.string
import com.duckduckgo.networkprotection.impl.di.NetpBreakageCategories
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.AppType
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.FilterType
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.HeaderType
import com.duckduckgo.networkprotection.impl.exclusion.ui.HeaderContent.DEFAULT
import com.duckduckgo.networkprotection.impl.exclusion.ui.NetpAppExclusionListActivity.Companion.AppsFilter
import com.duckduckgo.networkprotection.impl.exclusion.ui.NetpAppExclusionListActivity.Companion.AppsFilter.ALL
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.store.NetPExclusionListRepository
import com.duckduckgo.networkprotection.store.db.NetPManuallyExcludedApp
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ActivityScope::class)
class NetpAppExclusionListViewModel @Inject constructor(
    private val packageManager: PackageManager,
    private val dispatcherProvider: DispatcherProvider,
    private val netPExclusionListRepository: NetPExclusionListRepository,
    @NetpBreakageCategories private val breakageCategories: List<AppBreakageCategory>,
    private val systemAppOverridesProvider: SystemAppOverridesProvider,
    private val networkProtectionPixels: NetworkProtectionPixels,
) : ViewModel(), DefaultLifecycleObserver {
    private val command = Channel<Command>(1, DROP_OLDEST)
    private val filterState = MutableStateFlow(ALL)
    private val refreshSnapshot = MutableStateFlow(System.currentTimeMillis())
    private val currentExclusionList = mutableListOf<NetPManuallyExcludedApp>()
    private val exclusionListSnapshot = mutableListOf<NetPManuallyExcludedApp>()

    private var latestSnapshot = 0L
    private var installedApps: Sequence<ApplicationInfo> = emptySequence()

    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    internal fun getApps(): Flow<ViewState> {
        return getAppsForExclusionList().combine(filterState.asStateFlow()) { list, filter ->

            val panelType = HeaderType(headerContent = DEFAULT)
            val appList = when (filter) {
                AppsFilter.PROTECTED_ONLY -> {
                    val protectedApps = list.filter { it.isProtected }.map { AppType(it) }
                    val filterType = FilterType(string.netpExclusionListFilterMenuProtectedLabel, protectedApps.size)
                    mutableListOf(panelType, filterType).plus(protectedApps)
                }

                AppsFilter.UNPROTECTED_ONLY -> {
                    val unprotectedApps = list.filter { !it.isProtected }.map { AppType(it) }
                    val filterType = FilterType(string.netpExclusionListFilterMenuUnprotectedLabel, unprotectedApps.size)
                    mutableListOf(panelType, filterType).plus(unprotectedApps)
                }

                else -> {
                    val allApps = list.map { AppType(it) }
                    val filterType = FilterType(string.netpExclusionListFilterMenuAllLabel, allApps.size)
                    listOf(panelType, filterType).plus(allApps)
                }
            }

            return@combine ViewState(appList)
        }
    }

    private fun getAppsForExclusionList(): Flow<List<NetpExclusionListApp>> {
        return netPExclusionListRepository.getManualAppExclusionListFlow()
            .map { userExclusionList ->
                installedApps
                    .map { appInfo ->
                        NetpExclusionListApp(
                            packageName = appInfo.packageName,
                            name = packageManager.getApplicationLabel(appInfo).toString(),
                            isProtected = isProtected(appInfo, userExclusionList),
                        )
                    }.sortedBy { it.name.lowercase() }
                    .toList()
            }.onStart {
                refreshInstalledApps()
            }.flowOn(dispatcherProvider.io())
    }

    private fun refreshInstalledApps() {
        installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filterNot { !systemAppOverridesProvider.getSystemAppOverridesList().contains(it.packageName) && it.isSystemApp() }
    }

    private fun ApplicationInfo.isSystemApp(): Boolean {
        return (flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }

    private fun isProtected(
        appInfo: ApplicationInfo,
        userExclusionList: List<NetPManuallyExcludedApp>,
    ): Boolean {
        return userExclusionList.find {
            it.packageId == appInfo.packageName
        }?.run {
            isProtected
        } ?: true
    }

    private fun MutableStateFlow<Long>.refresh() {
        viewModelScope.launch {
            emit(System.currentTimeMillis())
        }
    }

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

    private fun userMadeChanges(): Boolean {
        // User made changes when the manual protections entry snapshot is different from the current snapshot

        if (currentExclusionList.size != exclusionListSnapshot.size) return true
        currentExclusionList.forEach { current ->
            exclusionListSnapshot.firstOrNull { it.packageId == current.packageId }?.let { match ->
                if (match.isProtected != current.isProtected) {
                    return true
                }
            }
        }

        return false
    }

    fun initialize() {
        networkProtectionPixels.reportExclusionListShown()
        netPExclusionListRepository.getManualAppExclusionListFlow()
            .combine(refreshSnapshot.asStateFlow()) { excludedApps, timestamp ->
                ManualProtectionSnapshot(timestamp, excludedApps)
            }
            .flowOn(dispatcherProvider.io())
            .onEach {
                if (latestSnapshot != it.timestamp) {
                    latestSnapshot = it.timestamp
                    exclusionListSnapshot.clear()
                    exclusionListSnapshot.addAll(it.snapshot)
                }
                currentExclusionList.clear()
                currentExclusionList.addAll(it.snapshot)
            }
            .flowOn(dispatcherProvider.main())
            .launchIn(viewModelScope)
    }

    fun applyAppsFilter(value: AppsFilter) {
        viewModelScope.launch {
            filterState.emit(value)
        }
    }

    fun onAppProtectionChanged(
        app: NetpExclusionListApp,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            if (enabled) {
                checkForAppProtectionEnabled(app)
            } else {
                checkForAppProtectionDisabled(app)
            }
        }
    }

    private fun checkForAppProtectionEnabled(app: NetpExclusionListApp) {
        onAppProtectionEnabled(app.packageName)
    }

    private suspend fun checkForAppProtectionDisabled(app: NetpExclusionListApp) {
        command.send(Command.ShowDisableProtectionDialog(app))
    }

    fun onAppProtectionDisabled(
        appName: String,
        packageName: String,
        report: Boolean,
    ) {
        viewModelScope.launch(dispatcherProvider.io()) {
            networkProtectionPixels.reportAppAddedToExclusionList()
            netPExclusionListRepository.manuallyExcludeApp(packageName)
            if (report) {
                networkProtectionPixels.reportExclusionListLaunchBreakageReport()
                command.send(
                    Command.ShowIssueReportingPage(
                        OpenVpnBreakageCategoryWithBrokenApp(
                            launchFrom = "netp",
                            appName = appName,
                            appPackageId = packageName,
                            breakageCategories = breakageCategories,
                        ),
                    ),
                )
            } else {
                networkProtectionPixels.reportSkippedReportAfterExcludingApp()
            }
        }
    }

    private fun onAppProtectionEnabled(packageName: String) {
        viewModelScope.launch(dispatcherProvider.io()) {
            networkProtectionPixels.reportAppRemovedFromExclusionList()
            netPExclusionListRepository.manuallyEnableApp(packageName)
        }
    }

    fun canRestoreDefaults(): Boolean {
        return currentExclusionList.isNotEmpty()
    }

    fun launchFeedback() {
        viewModelScope.launch(dispatcherProvider.io()) {
            networkProtectionPixels.reportExclusionListLaunchBreakageReport()
            command.send(
                Command.ShowIssueReportingPage(
                    OpenVpnBreakageCategoryWithBrokenApp(
                        launchFrom = "netp",
                        appName = "",
                        appPackageId = "",
                        breakageCategories = breakageCategories,
                    ),
                ),
            )
        }
    }

    fun restoreProtectedApps() {
        viewModelScope.launch(dispatcherProvider.io()) {
            networkProtectionPixels.reportExclusionListRestoreDefaults()
            netPExclusionListRepository.restoreDefaultProtectedList()
            refreshSnapshot.refresh()
            command.send(Command.RestartVpn)
        }
    }
}

private data class ManualProtectionSnapshot(
    val timestamp: Long,
    val snapshot: List<NetPManuallyExcludedApp>,
)

data class ViewState(
    val apps: List<AppsProtectionType>,
)

internal sealed class Command {
    object RestartVpn : Command()
    data class ShowIssueReportingPage(val params: OpenVpnBreakageCategoryWithBrokenApp) : Command()
    data class ShowDisableProtectionDialog(val forApp: NetpExclusionListApp) : Command()
}

sealed class AppsProtectionType {
    data class HeaderType(val headerContent: HeaderContent) : AppsProtectionType()
    data class FilterType(
        val filterResId: Int,
        val appsNumber: Int,
    ) : AppsProtectionType()

    data class AppType(val appInfo: NetpExclusionListApp) : AppsProtectionType()
}

enum class HeaderContent {
    DEFAULT,
    NETP_DISABLED,
}
