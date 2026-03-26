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

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.*
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.isIgnoringBatteryOptimizations
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.apps.AppsProtectionType.AppInfoType
import com.duckduckgo.mobile.android.vpn.apps.AppsProtectionType.FilterType
import com.duckduckgo.mobile.android.vpn.apps.AppsProtectionType.InfoPanelType
import com.duckduckgo.mobile.android.vpn.apps.ui.TrackingProtectionExclusionListActivity.Companion.AppsFilter
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageScreen
import com.duckduckgo.mobile.android.vpn.di.AppTpBreakageCategories
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.model.VpnTrackerWithEntity
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository.TimeWindow
import com.duckduckgo.mobile.android.vpn.ui.AppBreakageCategory
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit.DAYS
import javax.inject.Inject
import javax.inject.Qualifier
import kotlin.coroutines.coroutineContext

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ActivityScope::class)
class ManageAppsProtectionViewModel @Inject constructor(
    private val excludedApps: TrackingProtectionAppsRepository,
    private val appTrackersRepository: AppTrackerBlockingStatsRepository,
    private val pixel: DeviceShieldPixels,
    private val dispatcherProvider: DispatcherProvider,
    @AppTpBreakageCategories private val breakageCategories: List<AppBreakageCategory>,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    @InternalApi private val isIgnoringBatteryOptimizations: () -> Boolean,
) : ViewModel(), DefaultLifecycleObserver {

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()
    private val currentManualProtections = mutableListOf<Pair<String, Boolean>>()
    private val refreshSnapshot = MutableStateFlow(System.currentTimeMillis())
    private val entryProtectionSnapshot = mutableListOf<Pair<String, Boolean>>()
    private var latestSnapshot = 0L
    private val defaultTimeWindow = TimeWindow(5, DAYS)

    // A state flow behaves identically to a shared flow when it is created with the following parameters
    // See https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/
    // See also https://github.com/Kotlin/kotlinx.coroutines/issues/2515
    //
    // WARNING: only use _state to emit values, for anything else use getState()
    private val _recommendedSettingsState: MutableSharedFlow<RecommendedSettings> = MutableSharedFlow(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private fun MutableStateFlow<Long>.refresh() {
        viewModelScope.launch {
            emit(System.currentTimeMillis())
        }
    }

    private val filterState = MutableStateFlow(AppsFilter.ALL)

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

        coroutineScope.launch(dispatcherProvider.io()) {
            _recommendedSettingsState.tryEmit(RecommendedSettings(isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations()))
        }
    }

    internal fun recommendedSettings(): Flow<RecommendedSettings> {
        return _recommendedSettingsState.distinctUntilChanged()
    }

    internal suspend fun getProtectedApps(): Flow<ViewState> {
        return excludedApps.getAppsAndProtectionInfo()
            .combine(filterState.asStateFlow()) { list, filter ->
                val protectedApps = list.filter { !it.isExcluded }.map { AppInfoType(it) }
                val unprotectedApps = list.filter { it.isExcluded }.map { AppInfoType(it) }
                val allApps = list.map { AppInfoType(it) }
                val customProtection = list.any { it.isProblematic() && !it.isExcluded }

                when (filter) {
                    AppsFilter.PROTECTED_ONLY -> {
                        val panelType =
                            InfoPanelType(if (customProtection) BannerContent.CUSTOMISED_PROTECTION else BannerContent.ALL_OR_PROTECTED_APPS)
                        val filterType = FilterType(R.string.atp_ExcludedAppsFilterProtectedLabel, protectedApps.size)
                        val protectedAppsList = mutableListOf(panelType, filterType).plus(protectedApps)

                        return@combine ViewState(protectedAppsList)
                    }

                    AppsFilter.UNPROTECTED_ONLY -> {
                        val panelType = InfoPanelType(if (customProtection) BannerContent.CUSTOMISED_PROTECTION else BannerContent.UNPROTECTED_APPS)
                        val filterType = FilterType(R.string.atp_ExcludedAppsFilterUnprotectedLabel, unprotectedApps.size)
                        val unProtectedAppsList = mutableListOf(panelType, filterType).plus(unprotectedApps)

                        return@combine ViewState(unProtectedAppsList)
                    }

                    else -> {
                        val panelType =
                            InfoPanelType(if (customProtection) BannerContent.CUSTOMISED_PROTECTION else BannerContent.ALL_OR_PROTECTED_APPS)
                        val filterType = FilterType(R.string.atp_ExcludedAppsFilterAllLabel, allApps.size)
                        val appsList = listOf(panelType, filterType).plus(allApps)

                        return@combine ViewState(appsList)
                    }
                }
            }
    }

    internal suspend fun getRecentApps() =
        appTrackersRepository.getMostRecentVpnTrackers { defaultTimeWindow.asString() }.map { aggregateDataPerApp(it) }
            .combine(excludedApps.getAppsAndProtectionInfo()) { recentAppsBlocked, protectedApps ->
                recentAppsBlocked.map {
                    protectedApps.firstOrNull { protectedApp -> protectedApp.packageName == it.packageId }
                }
                    .filterNotNull()
                    .take(5)
                    .map { AppInfoType(it) }
            }.map { ViewState(it) }
            .onStart { pixel.didShowExclusionListActivity() }
            .flowOn(dispatcherProvider.io())

    private suspend fun aggregateDataPerApp(
        trackerData: List<VpnTrackerWithEntity>,
    ): List<TrackingApp> {
        val sourceData = mutableListOf<TrackingApp>()
        val perSessionData = trackerData.groupBy { it.tracker.bucket }

        perSessionData.values.forEach { sessionTrackers ->
            coroutineContext.ensureActive()

            val perAppData = sessionTrackers.groupBy { it.tracker.trackingApp.packageId }

            perAppData.values.forEach { appTrackers ->
                val item = appTrackers.sortedByDescending { it.tracker.timestamp }.first()
                sourceData.add(item.tracker.trackingApp)
            }
        }

        return sourceData.distinct()
    }

    fun onAppProtectionDisabled(
        appName: String,
        packageName: String,
        report: Boolean,
    ) {
        pixel.didDisableAppProtectionFromApps()
        viewModelScope.launch {
            excludedApps.manuallyExcludeApp(packageName)
            pixel.didSubmitManuallyDisableAppProtectionDialog()
            if (report) {
                command.send(
                    Command.LaunchFeedback(
                        ReportBreakageScreen.IssueDescriptionForm("apptp", breakageCategories, appName, packageName),
                    ),
                )
            } else {
                pixel.didSkipManuallyDisableAppProtectionDialog()
            }
        }
    }

    fun onAppProtectionEnabled(
        packageName: String,
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

    fun applyAppsFilter(value: AppsFilter) {
        viewModelScope.launch {
            filterState.emit(value)
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        fun updateRecommendedSettings() {
            owner.lifecycleScope.launch(dispatcherProvider.io()) {
                _recommendedSettingsState.tryEmit(
                    RecommendedSettings(isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations()),
                )
            }
        }

        updateRecommendedSettings()
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
        enabled: Boolean,
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
        position: Int,
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
            command.send(
                Command.LaunchFeedback(
                    ReportBreakageScreen.ListOfInstalledApps("apptp", breakageCategories),
                ),
            )
        }
    }

    fun launchManageAppsProtection() {
        pixel.didOpenExclusionListActivityFromManageAppsProtectionScreen()
        viewModelScope.launch {
            command.send(Command.LaunchAllAppsProtection)
        }
    }

    data class RecommendedSettings(val isIgnoringBatteryOptimizations: Boolean)
}

private data class ManualProtectionSnapshot(
    val timestamp: Long,
    val snapshot: List<Pair<String, Boolean>>,
)

data class ViewState(
    val excludedApps: List<AppsProtectionType>,
)

sealed class AppsProtectionType {
    data class InfoPanelType(val bannerContent: BannerContent) : AppsProtectionType()
    data class FilterType(
        val filterResId: Int,
        val appsNumber: Int,
    ) : AppsProtectionType()

    data class AppInfoType(val appInfo: TrackingProtectionAppInfo) : AppsProtectionType()
}

enum class BannerContent {
    ALL_OR_PROTECTED_APPS,
    UNPROTECTED_APPS,
    CUSTOMISED_PROTECTION,
}

internal sealed class Command {
    data object RestartVpn : Command()
    data class LaunchFeedback(val reportBreakageScreen: ReportBreakageScreen) : Command()
    data object LaunchAllAppsProtection : Command()
    data class ShowEnableProtectionDialog(
        val excludingReason: TrackingProtectionAppInfo,
        val position: Int,
    ) : Command()

    data class ShowDisableProtectionDialog(val excludingReason: TrackingProtectionAppInfo) : Command()
}

@Retention(AnnotationRetention.BINARY)
@Qualifier
private annotation class InternalApi

@Module
@ContributesTo(ActivityScope::class)
class IgnoringBatteryOptimizationsModule {
    @Provides
    @InternalApi
    fun providesIsIgnoringBatteryOptimizations(context: Context): () -> Boolean {
        return { context.isIgnoringBatteryOptimizations() }
    }
}
