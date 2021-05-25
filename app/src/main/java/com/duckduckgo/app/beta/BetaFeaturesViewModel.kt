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

package com.duckduckgo.app.beta

import android.content.Context
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.apps.DeviceShieldExcludedApps
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.ui.onboarding.DeviceShieldOnboarding
import com.duckduckgo.mobile.android.vpn.ui.onboarding.DeviceShieldOnboardingStore
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider

class BetaFeaturesViewModel(
    private val appContext: Context,
    private val deviceShieldPixels: DeviceShieldPixels,
    private val deviceShieldExcludedApps: DeviceShieldExcludedApps,
    private val deviceShieldOnboarding: DeviceShieldOnboarding,
    private val deviceShieldOnboardingStore: DeviceShieldOnboardingStore,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel(), LifecycleObserver {

    val viewState: MutableLiveData<ViewState> = MutableLiveData<ViewState>().apply {
        value = ViewState()
    }

    private fun currentViewState(): ViewState {
        return viewState.value!!
    }

    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    fun loadInitialData() {
        viewState.value = ViewState(
            deviceShieldEnabled = TrackerBlockingVpnService.isServiceRunning(appContext),
            deviceShieldOnboardingComplete = deviceShieldOnboardingStore.hasOnboardingBeenShown()
        )

        viewModelScope.launch {
            val excludedApps = withContext(dispatchers.io()) {
                getExcludedAppsInfo()
            }
            viewState.postValue(currentViewState().copy(excludedAppsInfo = excludedApps))

            while (isActive) {
                val isDeviceShieldEnabled = TrackerBlockingVpnService.isServiceRunning(appContext)
                if (currentViewState().deviceShieldEnabled != isDeviceShieldEnabled) {
                    viewState.value = currentViewState().copy(
                        deviceShieldEnabled = isDeviceShieldEnabled,
                        deviceShieldOnboardingComplete = deviceShieldOnboardingStore.hasOnboardingBeenShown()
                    )
                }
                delay(1_000)
            }
        }
    }

    suspend fun getExcludedAppsInfo(): String {
        val apps = deviceShieldExcludedApps.getExclusionAppList().filterNot { it.isDdgApp }
        return when (apps.size) {
            0 -> "None"
            1 -> "${apps.first().name}"
            2 -> "${apps.first().name} and ${apps.take(2)[1].name}"
            else -> "${apps.first().name}, ${apps.take(2)[1].name} and more"
        }
    }

    fun onExcludedAppsClicked() {
        command.value = Command.LaunchExcludedAppList
    }

    fun onDeviceShieldPrivacyReportClicked() {
        command.value = Command.LaunchDeviceShieldPrivacyReport
    }

    fun onDeviceShieldOnboardingClicked() {
        command.value = Command.LaunchDeviceShieldOnboarding
    }

    fun onDeviceShieldSettingChanged(enabled: Boolean) {
        Timber.i("Device Shield, is now enabled: $enabled")

        if (enabled) {
            deviceShieldPixels.enableFromSettings()
        } else {
            deviceShieldPixels.disableFromSettings()
        }

        val deviceShieldOnboardingIntent = deviceShieldOnboarding.prepare(appContext)
        command.value = when {
            enabled && deviceShieldOnboardingIntent != null -> Command.LaunchDeviceShieldOnboarding
            enabled -> Command.StartDeviceShield
            else -> Command.StopDeviceShield
        }
    }

    data class ViewState(
        val deviceShieldEnabled: Boolean = false,
        val deviceShieldOnboardingComplete: Boolean = false,
        val excludedAppsInfo: String = ""
    )

    sealed class Command {
        object LaunchExcludedAppList : Command()
        object LaunchDeviceShieldPrivacyReport : Command()
        object StartDeviceShield : Command()
        object LaunchDeviceShieldOnboarding : Command()
        object StopDeviceShield : Command()
    }
}

@ContributesMultibinding(AppObjectGraph::class)
class BetaFeaturesViewModelFactory @Inject constructor(
    private val deviceShieldPixels: Provider<DeviceShieldPixels>,
    private val deviceShieldPixelsOnboardingStore: Provider<DeviceShieldOnboardingStore>,
    private val appContext: Provider<Context>,
    private val deviceShieldExcludedApps: Provider<DeviceShieldExcludedApps>,
    private val deviceShieldOnboarding: Provider<DeviceShieldOnboarding>,
    private val dispatcherProvider: Provider<DispatcherProvider>
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(BetaFeaturesViewModel::class.java) -> (
                    BetaFeaturesViewModel(
                        appContext.get(),
                        deviceShieldPixels.get(),
                        deviceShieldExcludedApps.get(),
                        deviceShieldOnboarding.get(),
                        deviceShieldPixelsOnboardingStore.get(),
                        dispatcherProvider.get()
                    ) as T
                    )
                else -> null
            }
        }
    }
}
