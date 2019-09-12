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

package com.duckduckgo.app.onboarding.ui.page

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.statistics.pixels.Pixel

class DefaultBrowserPageExperimentViewModel(
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val pixel: Pixel,
    private val installStore: AppInstallStore
) : ViewModel() {

    data class ViewState(
        val showSettingsUi: Boolean = false,
        val showInstructionsCard: Boolean = false,
        val showOnlyContinue: Boolean = false
    )

    sealed class Command {
        class OpenDialog(val url: String = DEFAULT_URL) : Command()
        object OpenSettings : Command()
        object ContinueToBrowser : Command()
    }

    sealed class Origin {
        object InternalBrowser : Origin()
        object ExternalBrowser : Origin()
        object Settings : Origin()
        object DialogDismissed : Origin()
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()
    var timesPressedJustOnce: Int = 0

    init {
        viewState.value = ViewState(
            showOnlyContinue = defaultBrowserDetector.isDefaultBrowser(),
            showSettingsUi = defaultBrowserDetector.hasDefaultBrowser()
        )
        pixel.fire(Pixel.PixelName.ONBOARDING_DEFAULT_BROWSER_VISUALIZED)
    }

    fun loadUI() {
        viewState.value = viewState.value?.copy(
            showOnlyContinue = defaultBrowserDetector.isDefaultBrowser(),
            showSettingsUi = defaultBrowserDetector.hasDefaultBrowser()
        )
    }

    fun onContinueToBrowser(userTriedToSetDDGAsDefault: Boolean) {
        if (!userTriedToSetDDGAsDefault && !defaultBrowserDetector.isDefaultBrowser()) {
            pixel.fire(Pixel.PixelName.ONBOARDING_DEFAULT_BROWSER_SKIPPED)
        }
        command.value = Command.ContinueToBrowser
    }

    fun onDefaultBrowserClicked() {
        var behaviourTriggered = Pixel.PixelValues.DEFAULT_BROWSER_SETTINGS
        if (defaultBrowserDetector.hasDefaultBrowser()) {
            command.value = Command.OpenSettings
        } else {
            timesPressedJustOnce++
            behaviourTriggered = Pixel.PixelValues.DEFAULT_BROWSER_DIALOG
            command.value = Command.OpenDialog()
            viewState.value = viewState.value?.copy(showInstructionsCard = true)
        }
        val params = mapOf(
            Pixel.PixelParameter.DEFAULT_BROWSER_BEHAVIOUR_TRIGGERED to behaviourTriggered
        )
        pixel.fire(Pixel.PixelName.ONBOARDING_DEFAULT_BROWSER_LAUNCHED, params)
    }

    fun handleResult(origin: Origin) {
        val isDefault = defaultBrowserDetector.isDefaultBrowser()
        val showSettingsUI = defaultBrowserDetector.hasDefaultBrowser()
        var showInstructionsCard = false
        when (origin) {
            is Origin.InternalBrowser -> {
                showInstructionsCard = handleOriginInternalBrowser(isDefault)
            }
            is Origin.DialogDismissed -> {
                fireDefaultBrowserPixelAndResetTimesPressedJustOnce(originValue = Pixel.PixelValues.DEFAULT_BROWSER_DIALOG_DISMISSED)
            }
            is Origin.ExternalBrowser -> {
                fireDefaultBrowserPixelAndResetTimesPressedJustOnce(originValue = Pixel.PixelValues.DEFAULT_BROWSER_EXTERNAL)
            }
            is Origin.Settings -> {
                fireDefaultBrowserPixelAndResetTimesPressedJustOnce(originValue = Pixel.PixelValues.DEFAULT_BROWSER_SETTINGS)
            }
        }
        viewState.value = viewState.value?.copy(
            showOnlyContinue = isDefault,
            showSettingsUi = showSettingsUI,
            showInstructionsCard = showInstructionsCard
        )
    }

    private fun handleOriginInternalBrowser(ddgIsDefaultBrowser: Boolean): Boolean {
        if (ddgIsDefaultBrowser) {
            fireDefaultBrowserPixelAndResetTimesPressedJustOnce(originValue = Pixel.PixelValues.DEFAULT_BROWSER_DIALOG)
        } else {
            if (timesPressedJustOnce < MAX_DIALOG_ATTEMPTS) {
                timesPressedJustOnce++
                command.value = Command.OpenDialog()
                pixel.fire(Pixel.PixelName.ONBOARDING_DEFAULT_BROWSER_SELECTED_JUST_ONCE)
                return true
            } else {
                command.value = Command.ContinueToBrowser
                fireDefaultBrowserPixelAndResetTimesPressedJustOnce(originValue = Pixel.PixelValues.DEFAULT_BROWSER_JUST_ONCE_MAX)
            }
        }
        return false
    }

    private fun fireDefaultBrowserPixelAndResetTimesPressedJustOnce(originValue: String) {
        timesPressedJustOnce = 0
        if (defaultBrowserDetector.isDefaultBrowser()) {
            installStore.defaultBrowser = true
            val params = mapOf(
                Pixel.PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString(),
                Pixel.PixelParameter.DEFAULT_BROWSER_SET_ORIGIN to originValue
            )
            pixel.fire(Pixel.PixelName.DEFAULT_BROWSER_SET, params)
        } else {
            installStore.defaultBrowser = false
            val params = mapOf(
                Pixel.PixelParameter.DEFAULT_BROWSER_SET_ORIGIN to originValue
            )
            pixel.fire(Pixel.PixelName.DEFAULT_BROWSER_NOT_SET, params)
        }
    }

    companion object {
        const val MAX_DIALOG_ATTEMPTS = 2
        const val DEFAULT_URL = "https://donttrack.us"
    }
}