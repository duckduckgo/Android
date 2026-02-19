/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui.page

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.ADDRESS_BAR_POSITION
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.COMPARISON_CHART
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INITIAL
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INITIAL_REINSTALL_USER
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INPUT_SCREEN
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.SKIP_ONBOARDING_OPTION
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.NOTIFICATION_RUNTIME_PERMISSION_SHOWN
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_ADDRESS_BAR_POSITION_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_AICHAT_SELECTED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_BOTTOM_ADDRESS_BAR_SELECTED_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_CHOOSE_BROWSER_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_CHOOSE_SEARCH_EXPERIENCE_IMPRESSIONS_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_CONFIRM_SKIP_ONBOARDING_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_INTRO_REINSTALL_USER_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_INTRO_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_RESUME_ONBOARDING_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SEARCH_ONLY_SELECTED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SKIP_ONBOARDING_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SKIP_ONBOARDING_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SPLIT_ADDRESS_BAR_SELECTED_UNIQUE
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.inputscreen.wideevents.InputScreenOnboardingWideEvent
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@SuppressLint("StaticFieldLeak")
@ContributesViewModel(FragmentScope::class)
class BrandDesignUpdatePageViewModel @Inject constructor(
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
    private val context: Context,
    private val pixel: Pixel,
    private val appInstallStore: AppInstallStore,
    private val settingsDataStore: SettingsDataStore,
    private val dispatchers: DispatcherProvider,
    private val appBuildConfig: AppBuildConfig,
    private val onboardingStore: OnboardingStore,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val duckChat: DuckChat,
    private val inputScreenOnboardingWideEvent: InputScreenOnboardingWideEvent,
) : ViewModel() {

    data class ViewState(
        val currentDialog: PreOnboardingDialogType? = null,
        val selectedAddressBarPosition: OmnibarType = OmnibarType.SINGLE_TOP,
        val inputScreenSelected: Boolean = true,
        val showSplitOption: Boolean = false,
        val isReinstallUser: Boolean = false,
    )

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    private val _commands = Channel<Command>(1, DROP_OLDEST)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    private var maxPageCount: Int = 2

    init {
        viewModelScope.launch(dispatchers.io()) {
            maxPageCount = if (androidBrowserConfigFeature.showInputScreenOnboarding().isEnabled()) {
                3
            } else {
                2
            }
        }
    }

    sealed interface Command {
        data class ShowDefaultBrowserDialog(val intent: Intent) : Command
        data object Finish : Command
        data object OnboardingSkipped : Command
    }

    private fun setCurrentDialog(dialogType: PreOnboardingDialogType) {
        _viewState.update { it.copy(currentDialog = dialogType) }
        fireDialogShownPixel(dialogType)
    }

    private fun fireDialogShownPixel(dialogType: PreOnboardingDialogType) {
        when (dialogType) {
            INITIAL_REINSTALL_USER -> pixel.fire(PREONBOARDING_INTRO_REINSTALL_USER_SHOWN_UNIQUE, type = Unique())
            INITIAL -> pixel.fire(PREONBOARDING_INTRO_SHOWN_UNIQUE, type = Unique())
            COMPARISON_CHART -> pixel.fire(PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE, type = Unique())
            SKIP_ONBOARDING_OPTION -> pixel.fire(PREONBOARDING_SKIP_ONBOARDING_SHOWN_UNIQUE, type = Unique())
            ADDRESS_BAR_POSITION -> pixel.fire(PREONBOARDING_ADDRESS_BAR_POSITION_SHOWN_UNIQUE, type = Unique())
            INPUT_SCREEN -> pixel.fire(PREONBOARDING_CHOOSE_SEARCH_EXPERIENCE_IMPRESSIONS_UNIQUE, type = Unique())
        }
    }

    fun loadDaxDialog() {
        viewModelScope.launch {
            val isReinstall = isAppReinstall()
            val dialogType = if (isReinstall) INITIAL_REINSTALL_USER else INITIAL
            _viewState.update {
                it.copy(
                    isReinstallUser = isReinstall,
                    currentDialog = dialogType,
                )
            }
            fireDialogShownPixel(dialogType)
        }
    }

    fun onPrimaryCtaClicked() {
        val currentDialog = _viewState.value.currentDialog ?: return
        when (currentDialog) {
            INITIAL_REINSTALL_USER, INITIAL -> {
                setCurrentDialog(COMPARISON_CHART)
            }

            COMPARISON_CHART -> {
                viewModelScope.launch {
                    val isDDGDefaultBrowser =
                        if (defaultRoleBrowserDialog.shouldShowDialog()) {
                            val intent = defaultRoleBrowserDialog.createIntent(context)
                            if (intent != null) {
                                _commands.send(Command.ShowDefaultBrowserDialog(intent))
                            } else {
                                pixel.fire(AppPixelName.DEFAULT_BROWSER_DIALOG_NOT_SHOWN)
                                _viewState.update { it.copy(showSplitOption = isSplitOmnibarEnabled()) }
                                setCurrentDialog(ADDRESS_BAR_POSITION)
                            }
                            false
                        } else {
                            _commands.send(Command.Finish)
                            true
                        }
                    pixel.fire(
                        PREONBOARDING_CHOOSE_BROWSER_PRESSED,
                        mapOf(PixelParameter.DEFAULT_BROWSER to isDDGDefaultBrowser.toString()),
                    )
                }
            }

            SKIP_ONBOARDING_OPTION -> {
                viewModelScope.launch {
                    _commands.send(Command.OnboardingSkipped)
                    pixel.fire(PREONBOARDING_CONFIRM_SKIP_ONBOARDING_PRESSED)
                }
            }

            ADDRESS_BAR_POSITION -> {
                viewModelScope.launch {
                    val selectedPosition = _viewState.value.selectedAddressBarPosition
                    when (selectedPosition) {
                        OmnibarType.SINGLE_BOTTOM -> {
                            settingsDataStore.omnibarType = OmnibarType.SINGLE_BOTTOM
                            pixel.fire(PREONBOARDING_BOTTOM_ADDRESS_BAR_SELECTED_UNIQUE)
                        }
                        OmnibarType.SPLIT -> {
                            if (isSplitOmnibarEnabled()) {
                                settingsDataStore.omnibarType = OmnibarType.SPLIT
                                pixel.fire(PREONBOARDING_SPLIT_ADDRESS_BAR_SELECTED_UNIQUE)
                            } else {
                                settingsDataStore.omnibarType = OmnibarType.SINGLE_TOP
                            }
                        }
                        OmnibarType.SINGLE_TOP -> {
                            settingsDataStore.omnibarType = OmnibarType.SINGLE_TOP
                        }
                    }
                    if (androidBrowserConfigFeature.showInputScreenOnboarding().isEnabled()) {
                        setCurrentDialog(INPUT_SCREEN)
                    } else {
                        _commands.send(Command.Finish)
                    }
                }
            }

            INPUT_SCREEN -> {
                viewModelScope.launch(dispatchers.io()) {
                    val inputSelected = _viewState.value.inputScreenSelected
                    val isReinstall = _viewState.value.isReinstallUser
                    if (inputSelected) {
                        pixel.fire(PREONBOARDING_AICHAT_SELECTED)
                        inputScreenOnboardingWideEvent.onInputScreenEnabledDuringOnboarding(reinstallUser = isReinstall)
                    } else {
                        pixel.fire(PREONBOARDING_SEARCH_ONLY_SELECTED)
                    }
                    duckChat.setCosmeticInputScreenUserSetting(inputSelected)
                    onboardingStore.storeInputScreenSelection(inputSelected)
                    _commands.send(Command.Finish)
                }
            }
        }
    }

    fun onSecondaryCtaClicked() {
        val currentDialog = _viewState.value.currentDialog ?: return
        when (currentDialog) {
            INITIAL_REINSTALL_USER -> {
                _viewState.update { it.copy(isReinstallUser = true) }
                setCurrentDialog(SKIP_ONBOARDING_OPTION)
                pixel.fire(PREONBOARDING_SKIP_ONBOARDING_PRESSED)
            }

            SKIP_ONBOARDING_OPTION -> {
                setCurrentDialog(COMPARISON_CHART)
                pixel.fire(PREONBOARDING_RESUME_ONBOARDING_PRESSED)
            }

            INITIAL, COMPARISON_CHART, ADDRESS_BAR_POSITION, INPUT_SCREEN -> {
                // no-op
            }
        }
    }

    fun onDefaultBrowserSet() {
        defaultRoleBrowserDialog.dialogShown()
        appInstallStore.defaultBrowser = true
        pixel.fire(AppPixelName.DEFAULT_BROWSER_SET, mapOf(PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString()))
        _viewState.update { it.copy(showSplitOption = isSplitOmnibarEnabled()) }
        setCurrentDialog(ADDRESS_BAR_POSITION)
    }

    fun onDefaultBrowserNotSet() {
        defaultRoleBrowserDialog.dialogShown()
        appInstallStore.defaultBrowser = false
        pixel.fire(AppPixelName.DEFAULT_BROWSER_NOT_SET, mapOf(PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString()))
        _viewState.update { it.copy(showSplitOption = isSplitOmnibarEnabled()) }
        setCurrentDialog(ADDRESS_BAR_POSITION)
    }

    fun onAddressBarPositionOptionSelected(selectedOption: OmnibarType) {
        _viewState.update { it.copy(selectedAddressBarPosition = selectedOption) }
    }

    fun onInputScreenOptionSelected(withAi: Boolean) {
        _viewState.update { it.copy(inputScreenSelected = withAi) }
    }

    fun notificationRuntimePermissionRequested() {
        pixel.fire(NOTIFICATION_RUNTIME_PERMISSION_SHOWN)
    }

    fun notificationRuntimePermissionGranted() {
        pixel.fire(
            AppPixelName.NOTIFICATIONS_ENABLED,
            mapOf(PixelParameter.FROM_ONBOARDING to true.toString()),
        )
    }

    fun getMaxPageCount(): Int {
        return maxPageCount
    }

    private suspend fun isAppReinstall(): Boolean =
        withContext(dispatchers.io()) {
            appBuildConfig.isAppReinstall()
        }

    private fun isSplitOmnibarEnabled(): Boolean =
        androidBrowserConfigFeature.splitOmnibar().isEnabled() &&
            androidBrowserConfigFeature.splitOmnibarWelcomePage().isEnabled()
}
