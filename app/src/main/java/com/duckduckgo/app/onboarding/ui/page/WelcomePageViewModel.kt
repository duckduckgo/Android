/*
 * Copyright (c) 2020 DuckDuckGo
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.ui.customisationexperiment.DDGFeatureOnboardingOption
import com.duckduckgo.app.onboarding.ui.customisationexperiment.DDGFeatureOnboardingOption.FASTER_PAGE_LOADS
import com.duckduckgo.app.onboarding.ui.customisationexperiment.DDGFeatureOnboardingOption.FEWER_ADS
import com.duckduckgo.app.onboarding.ui.customisationexperiment.DDGFeatureOnboardingOption.ONE_CLICK_DATA_CLEARING
import com.duckduckgo.app.onboarding.ui.customisationexperiment.DDGFeatureOnboardingOption.PRIVATE_SEARCH
import com.duckduckgo.app.onboarding.ui.customisationexperiment.DDGFeatureOnboardingOption.SMALLER_DIGITAL_FOOTPRINT
import com.duckduckgo.app.onboarding.ui.customisationexperiment.DDGFeatureOnboardingOption.TRACKER_BLOCKING
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.isOnboardingCustomizationExperimentEnabled
import com.duckduckgo.app.statistics.pixels.Pixel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@SuppressLint("StaticFieldLeak")
class WelcomePageViewModel(
    private val appInstallStore: AppInstallStore,
    private val context: Context,
    private val pixel: Pixel,
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
    private val variantManager: VariantManager,
) : ViewModel() {

    fun reduce(event: WelcomePageView.Event): Flow<WelcomePageView.State> {
        return when (event) {
            WelcomePageView.Event.OnPrimaryCtaClicked -> onPrimaryCtaClicked()
            WelcomePageView.Event.OnDefaultBrowserSet -> onDefaultBrowserSet()
            WelcomePageView.Event.OnDefaultBrowserNotSet -> onDefaultBrowserNotSet()
            WelcomePageView.Event.OnSkipOptions -> onSkipOptionsClicked()
            is WelcomePageView.Event.OnContinueOptions -> onContinueWithOptionsClicked(event.options)
        }
    }

    private fun onSkipOptionsClicked(): Flow<WelcomePageView.State> = flow {
        pixel.fire(AppPixelName.ONBOARDING_OPTION_SKIP)
        onCtaOnboardingFlowFinished()
    }

    private fun onContinueWithOptionsClicked(options: Map<DDGFeatureOnboardingOption, Boolean>): Flow<WelcomePageView.State> = flow {
        val optionsSelected = options.filter { it.value }.map { it.key }.toList()
        if (optionsSelected.isEmpty()) {
            pixel.fire(AppPixelName.ONBOARDING_OPTION_SKIP)
        } else {
            optionsSelected.forEach { option ->
                when (option) {
                    PRIVATE_SEARCH -> pixel.fire(AppPixelName.ONBOARDING_OPTION_PRIVATE_SEARCH_SELECTED)
                    TRACKER_BLOCKING -> pixel.fire(AppPixelName.ONBOARDING_OPTION_TRACKER_BLOCKING_SELECTED)
                    SMALLER_DIGITAL_FOOTPRINT -> pixel.fire(AppPixelName.ONBOARDING_OPTION_SMALLER_DIGITAL_FOOTPRINT_SELECTED)
                    FASTER_PAGE_LOADS -> pixel.fire(AppPixelName.ONBOARDING_OPTION_FASTER_PAGE_LOADS_SELECTED)
                    FEWER_ADS -> pixel.fire(AppPixelName.ONBOARDING_OPTION_FEWER_ADS_SELECTED)
                    ONE_CLICK_DATA_CLEARING -> pixel.fire(AppPixelName.ONBOARDING_OPTION_ONE_CLICK_DATA_CLEARING_SELECTED)
                }
            }
            pixel.fire(AppPixelName.ONBOARDING_OPTIONS_SELECTED)
        }
        onCtaOnboardingFlowFinished()
    }

    private fun onPrimaryCtaClicked(): Flow<WelcomePageView.State> = flow {
        when (variantManager.isOnboardingCustomizationExperimentEnabled()) {
            true -> emit(WelcomePageView.State.ShowFeatureOptionsCta)
            false -> onCtaOnboardingFlowFinished()
        }
    }

    private fun onCtaOnboardingFlowFinished(): Flow<WelcomePageView.State> = flow {
        if (defaultRoleBrowserDialog.shouldShowDialog()) {
            val intent = defaultRoleBrowserDialog.createIntent(context)
            if (intent != null) {
                emit(WelcomePageView.State.ShowDefaultBrowserDialog(intent))
            } else {
                pixel.fire(AppPixelName.DEFAULT_BROWSER_DIALOG_NOT_SHOWN)
                emit(WelcomePageView.State.Finish)
            }
        } else {
            emit(WelcomePageView.State.Finish)
        }
    }

    private fun onDefaultBrowserSet(): Flow<WelcomePageView.State> = flow {
        defaultRoleBrowserDialog.dialogShown()

        appInstallStore.defaultBrowser = true

        pixel.fire(
            AppPixelName.DEFAULT_BROWSER_SET,
            mapOf(
                Pixel.PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString(),
            ),
        )

        emit(WelcomePageView.State.Finish)
    }

    private fun onDefaultBrowserNotSet(): Flow<WelcomePageView.State> = flow {
        defaultRoleBrowserDialog.dialogShown()

        appInstallStore.defaultBrowser = false

        pixel.fire(
            AppPixelName.DEFAULT_BROWSER_NOT_SET,
            mapOf(
                Pixel.PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString(),
            ),
        )

        emit(WelcomePageView.State.Finish)
    }
}

@Suppress("UNCHECKED_CAST")
class WelcomePageViewModelFactory(
    private val appInstallStore: AppInstallStore,
    private val context: Context,
    private val pixel: Pixel,
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
    private val variantManager: VariantManager,
) : ViewModelProvider.NewInstanceFactory() {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return with(modelClass) {
            when {
                isAssignableFrom(WelcomePageViewModel::class.java) -> WelcomePageViewModel(
                    appInstallStore,
                    context,
                    pixel,
                    defaultRoleBrowserDialog,
                    variantManager,
                )
                else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        } as T
    }
}
