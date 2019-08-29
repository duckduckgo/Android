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
import timber.log.Timber

class DefaultBrowserPageExperimentViewModel(
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val pixel: Pixel,
    private val installStore: AppInstallStore
) : ViewModel() {

    data class ViewState(
        val showSettingsUI: Boolean = false,
        val showInstructionsCard: Boolean = false,
        val showOnlyContinue: Boolean = false,
        val isLastAttempt: Boolean = false
    )

    sealed class Command {
        class OpenDialog(val timesOpened: Int = 0) : Command()
        object OpenSettings : Command()
        object ContinueToBrowser : Command()
    }

    sealed class Origin {
        class InternalBrowser(val timesOpened: Int = 0) : Origin()
        object ExternalBrowser : Origin()
        object Settings : Origin()
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    init {
        viewState.value = ViewState(showSettingsUI = defaultBrowserDetector.hasDefaultBrowser())
    }

    fun reloadInstructions() {
        viewState.value = viewState.value?.copy(showSettingsUI = defaultBrowserDetector.hasDefaultBrowser())
    }

    fun onDefaultBrowserClicked() {
        if (defaultBrowserDetector.hasDefaultBrowser()) {
            pixel.fire(Pixel.PixelName.ONBOARDING_DEFAULT_BROWSER_SETTINGS_LAUNCHED)
            command.value = Command.OpenSettings
        } else {
            command.value = Command.OpenDialog()
            viewState.value = viewState.value?.copy(showInstructionsCard = true)
        }
    }

    fun handleResult(origin: Origin) {
        val isDefault = defaultBrowserDetector.isDefaultBrowser()
        when (origin) {
            is Origin.InternalBrowser -> {
                if (isDefault) {
                    viewState.value = viewState.value?.copy(showOnlyContinue = true, showInstructionsCard = false)
                } else {
                    when {
                        origin.timesOpened < MAX_DIALOG_ATTEMPTS -> {
                            viewState.value = viewState.value?.copy(showOnlyContinue = false, showInstructionsCard = true)
                            command.value = Command.OpenDialog(origin.timesOpened)
                        }
                        origin.timesOpened >= MAX_DIALOG_ATTEMPTS -> {
                            viewState.value = viewState.value?.copy(showInstructionsCard = false)
                            command.value = Command.ContinueToBrowser
                        }
                        else -> {
                            viewState.value = viewState.value?.copy(
                                showSettingsUI = defaultBrowserDetector.hasDefaultBrowser(),
                                showOnlyContinue = false,
                                showInstructionsCard = false
                            )
                        }
                    }
                }
            }
            is Origin.ExternalBrowser -> {
                viewState.value = viewState.value?.copy(showSettingsUI = defaultBrowserDetector.hasDefaultBrowser(), showInstructionsCard = false)
            }
            is Origin.Settings -> {
                viewState.value = viewState.value?.copy(showSettingsUI = true, showInstructionsCard = false)
                val setText = if (isDefault) "was" else "was not"
                Timber.i("User returned from default settings; DDG $setText set as default")

                if (isDefault) {
                    installStore.defaultBrowser = true
                    pixel.fire(Pixel.PixelName.DEFAULT_BROWSER_SET)
                }
            }
        }
    }

    companion object {
        const val MAX_DIALOG_ATTEMPTS = 2
    }
}