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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.mobile.android.vpn.exclusion.SystemAppOverridesProvider
import com.duckduckgo.mobile.android.vpn.ui.AppBreakageCategory
import com.duckduckgo.mobile.android.vpn.ui.OpenVpnBreakageCategoryWithBrokenApp
import com.duckduckgo.networkprotection.impl.R.string
import com.duckduckgo.networkprotection.impl.autoexclude.AutoExcludeAppsRepository
import com.duckduckgo.networkprotection.impl.autoexclude.AutoExcludePrompt
import com.duckduckgo.networkprotection.impl.autoexclude.AutoExcludePrompt.Trigger.INCOMPATIBLE_APP_MANUALLY_EXCLUDED
import com.duckduckgo.networkprotection.impl.di.NetpBreakageCategories
import com.duckduckgo.networkprotection.impl.exclusion.isSystemApp
import com.duckduckgo.networkprotection.impl.exclusion.systemapps.SystemAppsExclusionRepository
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.AppType
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.DividerType
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.FilterType
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.HeaderType
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.SystemAppCategoryType
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppsProtectionType.SystemAppHeaderType
import com.duckduckgo.networkprotection.impl.exclusion.ui.Command.ShowAutoExcludePrompt
import com.duckduckgo.networkprotection.impl.exclusion.ui.Command.ShowUnifiedPproAppFeedback
import com.duckduckgo.networkprotection.impl.exclusion.ui.Command.ShowUnifiedPproFeedback
import com.duckduckgo.networkprotection.impl.exclusion.ui.HeaderContent.Default
import com.duckduckgo.networkprotection.impl.exclusion.ui.HeaderContent.WithToggle
import com.duckduckgo.networkprotection.impl.exclusion.ui.NetpAppExclusionListActivity.Companion.AppsFilter
import com.duckduckgo.networkprotection.impl.exclusion.ui.NetpAppExclusionListActivity.Companion.AppsFilter.ALL
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.settings.NetPSettingsLocalConfig
import com.duckduckgo.networkprotection.store.NetPManualExclusionListRepository
import com.duckduckgo.networkprotection.store.db.NetPManuallyExcludedApp
import com.duckduckgo.networkprotection.store.db.VpnIncompatibleApp
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource.VPN_EXCLUDED_APPS
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource.VPN_MANAGEMENT
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ActivityScope::class)
class NetpAppExclusionListViewModel @Inject constructor(
    private val packageManager: PackageManager,
    private val dispatcherProvider: DispatcherProvider,
    private val netPManualExclusionListRepository: NetPManualExclusionListRepository,
    @NetpBreakageCategories private val breakageCategories: List<AppBreakageCategory>,
    private val systemAppOverridesProvider: SystemAppOverridesProvider,
    private val networkProtectionPixels: NetworkProtectionPixels,
    private val systemAppsExclusionRepository: SystemAppsExclusionRepository,
    private val privacyProUnifiedFeedback: PrivacyProUnifiedFeedback,
    private val localConfig: NetPSettingsLocalConfig,
    private val autoExcludeAppsRepository: AutoExcludeAppsRepository,
    private val autoExcludePrompt: AutoExcludePrompt,
) : ViewModel(), DefaultLifecycleObserver {
    private val command = Channel<Command>(1, DROP_OLDEST)
    private val filterState = MutableStateFlow(ALL)
    private val forceRefreshList = MutableStateFlow(System.currentTimeMillis())
    private val refreshSnapshot = MutableStateFlow(System.currentTimeMillis())
    private val currentExclusionList = mutableListOf<NetPManuallyExcludedApp>()
    private val exclusionListSnapshot = mutableListOf<NetPManuallyExcludedApp>()
    private var forceRestart: Boolean = false

    private var latestSnapshot = 0L
    private var installedApps: Sequence<ApplicationInfo> = emptySequence()

    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    internal fun getApps(): Flow<ViewState> {
        return getAppsForExclusionList().combine(filterState.asStateFlow()) { list, filter ->
            val systemAppsCategories = systemAppsExclusionRepository.getAvailableCategories().map {
                SystemAppCategoryType(
                    NetpExclusionListSystemAppCategory(
                        category = it,
                        text = it.name,
                        isEnabled = !systemAppsExclusionRepository.isCategoryExcluded(it),
                    ),
                )
            }

            val panelType = if (autoExcludeAppsRepository.getAllIncompatibleApps().isNotEmpty()) {
                HeaderType(headerContent = WithToggle(localConfig.autoExcludeBrokenApps().isEnabled()))
            } else {
                HeaderType(headerContent = Default)
            }

            val appList = when (filter) {
                AppsFilter.PROTECTED_ONLY -> {
                    val protectedApps = list.filter { it.isProtected }.map { AppType(it) }
                    val filterType = FilterType(string.netpExclusionListFilterMenuProtectedLabel, protectedApps.size)
                    buildList {
                        add(panelType)
                        if (systemAppsCategories.isNotEmpty()) {
                            add(SystemAppHeaderType)
                            addAll(systemAppsCategories)
                            add(DividerType)
                        }
                        add(filterType)
                        addAll(protectedApps)
                    }
                }

                AppsFilter.UNPROTECTED_ONLY -> {
                    val unprotectedApps = list.filter { !it.isProtected }.map { AppType(it) }
                    val filterType = FilterType(string.netpExclusionListFilterMenuUnprotectedLabel, unprotectedApps.size)
                    buildList {
                        add(panelType)
                        if (systemAppsCategories.isNotEmpty()) {
                            add(SystemAppHeaderType)
                            addAll(systemAppsCategories)
                            add(DividerType)
                        }
                        add(filterType)
                        addAll(unprotectedApps)
                    }
                }

                else -> {
                    val allApps = list.map { AppType(it) }
                    val filterType = FilterType(string.netpExclusionListFilterMenuAllLabel, allApps.size)
                    buildList {
                        add(panelType)
                        if (systemAppsCategories.isNotEmpty()) {
                            add(SystemAppHeaderType)
                            addAll(systemAppsCategories)
                            add(DividerType)
                        }
                        add(filterType)
                        addAll(allApps)
                    }
                }
            }

            return@combine ViewState(
                apps = appList,
            )
        }.flowOn(dispatcherProvider.io())
    }

    /**
     * This method takes all installed apps on the device and transforms each to items to be rendered on the exclusion list taking
     * into consideration the user's manual exclusion list and the auto-exclude feature.
     */
    private fun getAppsForExclusionList(): Flow<List<NetpExclusionListApp>> {
        return combine(
            forceRefreshList, // allows us to manually force refresh the list
            netPManualExclusionListRepository.getManualAppExclusionListFlow(), // provides user's manual exclusion list
            autoExcludeAppsRepository.getAllIncompatibleAppPackagesFlow(), // provides all apps in the auto exclude list
        ) { _, userExclusionList, autoExcludeList ->
            val autoExcludeFeatureEnabled = autoExcludeAppsRepository.getAllIncompatibleApps().isNotEmpty()
            val autoExcludeEnabled = localConfig.autoExcludeBrokenApps().isEnabled()

            installedApps.map { appInfo ->
                val userExcludedApp = userExclusionList.find { it.packageId == appInfo.packageName }
                NetpExclusionListApp(
                    packageName = appInfo.packageName,
                    name = packageManager.getApplicationLabel(appInfo).toString(),
                    /**
                     * If app is part of user exclusion list, the value is whatever isProtected in exclusion list is.
                     * Else, if app is part of the auto-exclude list, the value is whatever the state of the auto exclude setting is.
                     * Else, if app is not part of the user exclusion and auto exclude, isProtected is true.
                     */
                    isProtected = userExcludedApp?.isProtected ?: if (autoExcludeFeatureEnabled && autoExcludeList.contains(appInfo.packageName)) {
                        !autoExcludeEnabled
                    } else {
                        true
                    },
                    // If auto exclude feature is available, we set this to true if the app is part of auto exclude list. Else, it is set to false.
                    // This value is used to show the incompatible label
                    isNotCompatibleWithVPN = if (autoExcludeFeatureEnabled) {
                        autoExcludeList.contains(appInfo.packageName)
                    } else {
                        false
                    },
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

    private fun MutableStateFlow<Long>.refresh() {
        viewModelScope.launch {
            delay(100)
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
            if (userMadeChanges() || forceRestart) {
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
        netPManualExclusionListRepository.getManualAppExclusionListFlow()
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
            netPManualExclusionListRepository.manuallyExcludeApp(packageName)
            if (report) {
                networkProtectionPixels.reportExclusionListLaunchBreakageReport()
                if (privacyProUnifiedFeedback.shouldUseUnifiedFeedback(source = VPN_EXCLUDED_APPS)) {
                    command.send(
                        ShowUnifiedPproAppFeedback(
                            appName = appName,
                            appPackageName = packageName,
                        ),
                    )
                } else {
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
                }
            } else {
                networkProtectionPixels.reportSkippedReportAfterExcludingApp()
            }

            if (!localConfig.autoExcludeBrokenApps().isEnabled()) {
                if (autoExcludeAppsRepository.isAppMarkedAsIncompatible(packageName)) {
                    autoExcludePrompt.getAppsForPrompt(INCOMPATIBLE_APP_MANUALLY_EXCLUDED).also {
                        if (it.isNotEmpty()) {
                            command.send(ShowAutoExcludePrompt(it))
                        }
                    }
                }
            }
        }
    }

    private fun onAppProtectionEnabled(packageName: String) {
        viewModelScope.launch(dispatcherProvider.io()) {
            networkProtectionPixels.reportAppRemovedFromExclusionList()
            netPManualExclusionListRepository.manuallyEnableApp(packageName)
        }
    }

    fun canRestoreDefaults(): Boolean {
        return currentExclusionList.isNotEmpty()
    }

    fun launchFeedback() {
        viewModelScope.launch(dispatcherProvider.io()) {
            networkProtectionPixels.reportExclusionListLaunchBreakageReport()
            if (privacyProUnifiedFeedback.shouldUseUnifiedFeedback(VPN_MANAGEMENT)) {
                command.send(ShowUnifiedPproFeedback)
            } else {
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
    }

    @SuppressLint("DenyListedApi")
    fun restoreProtectedApps() {
        viewModelScope.launch(dispatcherProvider.io()) {
            networkProtectionPixels.reportExclusionListRestoreDefaults()
            netPManualExclusionListRepository.restoreDefaultProtectedList()
            systemAppsExclusionRepository.restoreDefaults()
            localConfig.autoExcludeBrokenApps().setRawStoredState(State(enable = false))
            forceRestart = true
            refreshSnapshot.refresh()
            forceRefreshList.refresh()
            command.send(Command.RestartVpn)
        }
    }

    fun onSystemAppCategoryStateChanged(
        category: NetpExclusionListSystemAppCategory,
        enabled: Boolean,
    ) {
        viewModelScope.launch(dispatcherProvider.io()) {
            if (systemAppsExclusionRepository.hasShownWarning()) {
                forceRestart = true
                if (enabled) {
                    systemAppsExclusionRepository.includeCategory(category.category)
                    networkProtectionPixels.reportExcludeSystemAppsDisabledForCategory(category.category.name)
                } else {
                    systemAppsExclusionRepository.excludeCategory(category.category)
                    networkProtectionPixels.reportExcludeSystemAppsEnabledForCategory(category.category.name)
                }
            } else {
                systemAppsExclusionRepository.markWarningShown()
                command.send(Command.ShowSystemAppsExclusionWarning(category))
            }
        }
    }

    @SuppressLint("DenyListedApi")
    fun onAutoExcludeToggled(enabled: Boolean) {
        viewModelScope.launch(dispatcherProvider.io()) {
            localConfig.autoExcludeBrokenApps().setRawStoredState(State(enable = enabled))
            forceRefreshList.refresh()
            if (enabled) {
                networkProtectionPixels.reportAutoExcludeEnableViaExclusionList()
            } else {
                networkProtectionPixels.reportAutoExcludeDisableViaExclusionList()
            }
            command.send(Command.RestartVpn)
        }
    }

    fun forceRefresh() {
        forceRefreshList.refresh()
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
    data object RestartVpn : Command()
    data class ShowIssueReportingPage(val params: OpenVpnBreakageCategoryWithBrokenApp) : Command()
    data object ShowUnifiedPproFeedback : Command()

    data class ShowUnifiedPproAppFeedback(
        val appName: String,
        val appPackageName: String,
    ) : Command()

    data class ShowDisableProtectionDialog(val forApp: NetpExclusionListApp) : Command()
    data class ShowSystemAppsExclusionWarning(val category: NetpExclusionListSystemAppCategory) : Command()
    data class ShowAutoExcludePrompt(val apps: List<VpnIncompatibleApp>) : Command()
}

sealed class AppsProtectionType {
    data class HeaderType(val headerContent: HeaderContent) : AppsProtectionType()
    data class FilterType(
        val filterResId: Int,
        val appsNumber: Int,
    ) : AppsProtectionType()

    data class AppType(val appInfo: NetpExclusionListApp) : AppsProtectionType()
    data object SystemAppHeaderType : AppsProtectionType()
    data class SystemAppCategoryType(val category: NetpExclusionListSystemAppCategory) : AppsProtectionType()

    data object DividerType : AppsProtectionType()
}

sealed class HeaderContent {
    data object Default : HeaderContent()
    data object NetpDisabled : HeaderContent()
    data class WithToggle(
        val enabled: Boolean,
    ) : HeaderContent()
}
