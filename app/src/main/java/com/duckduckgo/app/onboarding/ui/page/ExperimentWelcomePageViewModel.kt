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
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.ui.page.ExperimentWelcomePage.Companion.PreOnboardingDialogType
import com.duckduckgo.app.onboarding.ui.page.ExperimentWelcomePage.Companion.PreOnboardingDialogType.CELEBRATION
import com.duckduckgo.app.onboarding.ui.page.ExperimentWelcomePage.Companion.PreOnboardingDialogType.COMPARISON_CHART
import com.duckduckgo.app.onboarding.ui.page.ExperimentWelcomePage.Companion.PreOnboardingDialogType.INITIAL
import com.duckduckgo.app.onboarding.ui.page.ExperimentWelcomePageViewModel.Command.Finish
import com.duckduckgo.app.onboarding.ui.page.ExperimentWelcomePageViewModel.Command.ShowComparisonChart
import com.duckduckgo.app.onboarding.ui.page.ExperimentWelcomePageViewModel.Command.ShowDefaultBrowserDialog
import com.duckduckgo.app.onboarding.ui.page.ExperimentWelcomePageViewModel.Command.ShowSuccessDialog
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.OnboardingExperimentPixel.PixelName.NOTIFICATION_RUNTIME_PERMISSION_SHOWN
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.OnboardingExperimentPixel.PixelName.PREONBOARDING_AFFIRMATION_SHOWN
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.OnboardingExperimentPixel.PixelName.PREONBOARDING_CHOOSE_BROWSER_PRESSED
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.OnboardingExperimentPixel.PixelName.PREONBOARDING_COMPARISON_CHART_SHOWN
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.OnboardingExperimentPixel.PixelName.PREONBOARDING_INTRO_SHOWN
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.OnboardingExperimentPixel.PixelParameter
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.UNIQUE
import com.duckduckgo.di.scopes.FragmentScope
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak")
@ContributesViewModel(FragmentScope::class)
class ExperimentWelcomePageViewModel @Inject constructor(
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
    private val context: Context,
    private val pixel: Pixel,
    private val appInstallStore: AppInstallStore,
) : ViewModel() {

    private val _commands = Channel<Command>(1, DROP_OLDEST)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    sealed interface Command {
        data object ShowComparisonChart : Command
        data class ShowDefaultBrowserDialog(val intent: Intent) : Command
        data object ShowSuccessDialog : Command
        data object Finish : Command
    }

    fun onPrimaryCtaClicked(currentDialog: PreOnboardingDialogType) {
        when (currentDialog) {
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
                                _commands.send(Finish)
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

            CELEBRATION -> {
                viewModelScope.launch {
                    _commands.send(Finish)
                }
            }
        }
    }

    fun onDefaultBrowserSet() {
        defaultRoleBrowserDialog.dialogShown()
        appInstallStore.defaultBrowser = true
        pixel.fire(AppPixelName.DEFAULT_BROWSER_SET, mapOf(Pixel.PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString()))

        viewModelScope.launch {
            _commands.send(ShowSuccessDialog)
        }
    }

    fun onDefaultBrowserNotSet() {
        defaultRoleBrowserDialog.dialogShown()
        appInstallStore.defaultBrowser = false
        pixel.fire(AppPixelName.DEFAULT_BROWSER_NOT_SET, mapOf(Pixel.PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString()))

        viewModelScope.launch {
            _commands.send(Finish)
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
            INITIAL -> {
                pixel.fire(OnboardingExperimentPixel.PixelName.PREONBOARDING_INTRO_SHOWN)
                pixel.fire(OnboardingExperimentPixel.PixelName.PREONBOARDING_INTRO_SHOWN_UNIQUE, type = UNIQUE)
            }
            COMPARISON_CHART -> {
                pixel.fire(OnboardingExperimentPixel.PixelName.PREONBOARDING_COMPARISON_CHART_SHOWN)
                pixel.fire(OnboardingExperimentPixel.PixelName.PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE, type = UNIQUE)
            }
            CELEBRATION -> {
                pixel.fire(OnboardingExperimentPixel.PixelName.PREONBOARDING_AFFIRMATION_SHOWN)
                pixel.fire(OnboardingExperimentPixel.PixelName.PREONBOARDING_AFFIRMATION_SHOWN_UNIQUE, type = UNIQUE)
            }
        }
    }
}
