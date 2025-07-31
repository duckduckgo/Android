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
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.ADDRESS_BAR_POSITION
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.COMPARISON_CHART
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INITIAL
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INITIAL_REINSTALL_USER
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.SKIP_ONBOARDING_OPTION
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.Finish
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.OnboardingSkipped
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.SetAddressBarPositionOptions
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowAddressBarPositionDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowComparisonChart
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowDefaultBrowserDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowInitialDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowInitialReinstallUserDialog
import com.duckduckgo.app.onboarding.ui.page.WelcomePageViewModel.Command.ShowSkipOnboardingOption
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.NOTIFICATION_RUNTIME_PERMISSION_SHOWN
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_ADDRESS_BAR_POSITION_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_BOTTOM_ADDRESS_BAR_SELECTED_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_CHOOSE_BROWSER_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_CONFIRM_SKIP_ONBOARDING_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_INTRO_REINSTALL_USER_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_INTRO_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_RESUME_ONBOARDING_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SKIP_ONBOARDING_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SKIP_ONBOARDING_SHOWN_UNIQUE
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("StaticFieldLeak")
@ContributesViewModel(FragmentScope::class)
class WelcomePageViewModel @Inject constructor(
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
    private val context: Context,
    private val pixel: Pixel,
    private val appInstallStore: AppInstallStore,
    private val settingsDataStore: SettingsDataStore,
    private val dispatchers: DispatcherProvider,
    private val appBuildConfig: AppBuildConfig,
) : ViewModel() {

    private val _commands = Channel<Command>(1, DROP_OLDEST)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    private var defaultAddressBarPosition: Boolean = true

    sealed interface Command {
        data object ShowInitialReinstallUserDialog : Command
        data object ShowInitialDialog : Command
        data object ShowComparisonChart : Command
        data object ShowSkipOnboardingOption : Command
        data class ShowDefaultBrowserDialog(val intent: Intent) : Command
        data object ShowAddressBarPositionDialog : Command
        data object Finish : Command
        data object OnboardingSkipped : Command
        data class SetAddressBarPositionOptions(val defaultOption: Boolean) : Command
    }

    fun onPrimaryCtaClicked(currentDialog: PreOnboardingDialogType) {
        when (currentDialog) {
            INITIAL_REINSTALL_USER -> {
                viewModelScope.launch {
                    _commands.send(ShowComparisonChart)
                }
            }

            INITIAL -> {
                viewModelScope.launch {
                    _commands.send(ShowComparisonChart)
                }
            }

            COMPARISON_CHART -> {
                viewModelScope.launch {
                    val isDDGDefaultBrowser =
                        if (defaultRoleBrowserDialog.shouldShowDialog()) {
                            val intent = defaultRoleBrowserDialog.createIntent(context)
                            if (intent != null) {
                                _commands.send(ShowDefaultBrowserDialog(intent))
                            } else {
                                pixel.fire(AppPixelName.DEFAULT_BROWSER_DIALOG_NOT_SHOWN)
                                _commands.send(ShowAddressBarPositionDialog)
                            }
                            false
                        } else {
                            _commands.send(Finish)
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
                    _commands.send(OnboardingSkipped)
                    pixel.fire(PREONBOARDING_CONFIRM_SKIP_ONBOARDING_PRESSED)
                }
            }

            ADDRESS_BAR_POSITION -> {
                if (!defaultAddressBarPosition) {
                    settingsDataStore.omnibarPosition = OmnibarPosition.BOTTOM
                    pixel.fire(PREONBOARDING_BOTTOM_ADDRESS_BAR_SELECTED_UNIQUE)
                }
                viewModelScope.launch {
                    _commands.send(Finish)
                }
            }
        }
    }

    fun onSecondaryCtaClicked(currentDialog: PreOnboardingDialogType) {
        when (currentDialog) {
            INITIAL_REINSTALL_USER -> {
                viewModelScope.launch {
                    _commands.send(ShowSkipOnboardingOption)
                    pixel.fire(PREONBOARDING_SKIP_ONBOARDING_PRESSED)
                }
            }

            INITIAL -> {
                // no-op
            }

            COMPARISON_CHART -> {
                // no-op
            }

            SKIP_ONBOARDING_OPTION -> {
                viewModelScope.launch {
                    _commands.send(ShowComparisonChart)
                    pixel.fire(PREONBOARDING_RESUME_ONBOARDING_PRESSED)
                }
            }

            ADDRESS_BAR_POSITION -> {
                // no-op
            }
        }
    }

    fun onDefaultBrowserSet() {
        defaultRoleBrowserDialog.dialogShown()
        appInstallStore.defaultBrowser = true
        pixel.fire(AppPixelName.DEFAULT_BROWSER_SET, mapOf(PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString()))

        viewModelScope.launch {
            _commands.send(ShowAddressBarPositionDialog)
        }
    }

    fun onDefaultBrowserNotSet() {
        defaultRoleBrowserDialog.dialogShown()
        appInstallStore.defaultBrowser = false
        pixel.fire(AppPixelName.DEFAULT_BROWSER_NOT_SET, mapOf(PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString()))

        viewModelScope.launch {
            _commands.send(ShowAddressBarPositionDialog)
        }
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

    fun onDialogShown(onboardingDialogType: PreOnboardingDialogType) {
        when (onboardingDialogType) {
            INITIAL_REINSTALL_USER -> pixel.fire(PREONBOARDING_INTRO_REINSTALL_USER_SHOWN_UNIQUE, type = Unique())
            INITIAL -> pixel.fire(PREONBOARDING_INTRO_SHOWN_UNIQUE, type = Unique())
            COMPARISON_CHART -> pixel.fire(PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE, type = Unique())
            SKIP_ONBOARDING_OPTION -> pixel.fire(PREONBOARDING_SKIP_ONBOARDING_SHOWN_UNIQUE, type = Unique())
            ADDRESS_BAR_POSITION -> pixel.fire(PREONBOARDING_ADDRESS_BAR_POSITION_SHOWN_UNIQUE, type = Unique())
        }
    }

    fun onAddressBarPositionOptionSelected(defaultOption: Boolean) {
        defaultAddressBarPosition = defaultOption
        viewModelScope.launch {
            _commands.send(SetAddressBarPositionOptions(defaultOption))
        }
    }

    fun loadDaxDialog() {
        viewModelScope.launch {
            if (isAppReinstall()) {
                _commands.send(ShowInitialReinstallUserDialog)
            } else {
                _commands.send(ShowInitialDialog)
            }
        }
    }

    private suspend fun isAppReinstall(): Boolean {
        return withContext(dispatchers.io()) {
            appBuildConfig.isAppReinstall()
        }
    }
}
