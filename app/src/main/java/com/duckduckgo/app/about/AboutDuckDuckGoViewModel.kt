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

package com.duckduckgo.app.about

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.feedback.AppFeedback
import com.duckduckgo.app.pixels.AppPixelName.*
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@ContributesViewModel(ActivityScope::class)
class AboutDuckDuckGoViewModel @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    private val pixel: Pixel,
    private val appFeedback: AppFeedback,
) : ViewModel() {

    data class ViewState(
        val version: String = "",
    )

    sealed class Command {
        object LaunchBrowserWithLearnMoreUrl : Command()
        object LaunchBrowserWithPrivacyProtectionsUrl : Command()
        object LaunchWebViewWithPrivacyPolicyUrl : Command()
        object LaunchFeedback : Command()
        object LaunchPproUnifiedFeedback : Command()
    }

    private val viewState = MutableStateFlow(ViewState())
    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    private var easterEggCounter = 0

    fun viewState(): Flow<ViewState> = viewState.onStart {
        viewModelScope.launch {
            viewState.emit(
                currentViewState().copy(
                    version = obtainVersion(),
                ),
            )
        }
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun onLearnMoreLinkClicked() {
        viewModelScope.launch { command.send(Command.LaunchBrowserWithLearnMoreUrl) }
        pixel.fire(SETTINGS_ABOUT_DDG_LEARN_MORE_PRESSED)
    }

    fun onPrivacyProtectionsLinkClicked() {
        viewModelScope.launch { command.send(Command.LaunchBrowserWithPrivacyProtectionsUrl) }
    }

    fun onPrivacyPolicyClicked() {
        viewModelScope.launch { command.send(Command.LaunchWebViewWithPrivacyPolicyUrl) }
        pixel.fire(SETTINGS_ABOUT_DDG_PRIVACY_POLICY_PRESSED)
    }

    fun onVersionClicked() {
        easterEggCounter++
        if (easterEggCounter >= MAX_EASTER_EGG_COUNT) {
            Timber.v("Easter egg triggered")
            resetEasterEggCounter()
            pixel.fire(SETTINGS_ABOUT_DDG_VERSION_EASTER_EGG_PRESSED)
        }
    }

    fun onProvideFeedbackClicked() {
        viewModelScope.launch {
            if (appFeedback.shouldUseUnifiedFeedback()) {
                command.send(Command.LaunchPproUnifiedFeedback)
            } else {
                command.send(Command.LaunchFeedback)
            }
        }
        pixel.fire(SETTINGS_ABOUT_DDG_SHARE_FEEDBACK_PRESSED)
    }

    fun resetEasterEggCounter() {
        easterEggCounter = 0
    }

    // This is used for testing only to check the `netPEasterEggCounter` is reset without
    // exposing it.
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun hasResetEasterEggCounter() = easterEggCounter == 0

    private fun currentViewState(): ViewState {
        return viewState.value
    }

    private fun obtainVersion(): String {
        return "${appBuildConfig.versionName} (${appBuildConfig.versionCode})"
    }

    companion object {
        internal const val MAX_EASTER_EGG_COUNT = 12
    }
}
