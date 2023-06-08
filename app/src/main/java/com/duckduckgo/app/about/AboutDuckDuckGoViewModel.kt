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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.networkprotection.impl.waitlist.NetPWaitlistState
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class AboutDuckDuckGoViewModel @Inject constructor(
    private val netpWaitlistRepository: NetPWaitlistRepository,
    private val appBuildConfig: AppBuildConfig,
    private val variantManager: VariantManager,
) : ViewModel() {

    data class ViewState(
        val networkProtectionWaitlistState: NetPWaitlistState = NetPWaitlistState.NotUnlocked,
        val version: String = "",
    )

    sealed class Command {
        object LaunchBrowserWithLearnMoreUrl : Command()
        object LaunchWebViewWithPrivacyPolicyUrl : Command()
        object ShowNetPUnlockedSnackbar : Command()
        object LaunchNetPWaitlist : Command()
        object LaunchFeedback : Command()
    }

    private val viewState = MutableStateFlow(ViewState())
    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    private var netPEasterEggCounter = 0

    fun viewState(): StateFlow<ViewState> {
        return viewState
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun start() {
        val variant = variantManager.getVariant()

        viewModelScope.launch {
            viewState.emit(
                currentViewState().copy(
                    networkProtectionWaitlistState = netpWaitlistRepository.getState(appBuildConfig.isInternalBuild()),
                    version = obtainVersion(variant.key),
                ),
            )
        }
    }

    fun onLearnMoreLinkClicked() {
        viewModelScope.launch { command.send(Command.LaunchBrowserWithLearnMoreUrl) }
    }

    fun onPrivacyPolicyClicked() {
        viewModelScope.launch { command.send(Command.LaunchWebViewWithPrivacyPolicyUrl) }
    }

    fun onVersionClicked() {
        if (viewState.value.networkProtectionWaitlistState == NetPWaitlistState.NotUnlocked) {
            netPEasterEggCounter++
            if (netPEasterEggCounter >= MAX_EASTER_EGG_COUNT) {
                viewModelScope.launch { command.send(Command.ShowNetPUnlockedSnackbar) }
                resetNetPEasterEggCounter()
            }
        }
    }

    fun onProvideFeedbackClicked() {
        viewModelScope.launch { command.send(Command.LaunchFeedback) }
    }

    fun onNetPUnlockedActionClicked() {
        viewModelScope.launch { command.send(Command.LaunchNetPWaitlist) }
    }

    fun resetNetPEasterEggCounter() {
        netPEasterEggCounter = 0
    }

    // This is used for testing only to check the `netPEasterEggCounter` is reset without
    // exposing it.
    internal fun hasResetNetPEasterEggCounter() = netPEasterEggCounter == 0

    private fun currentViewState(): ViewState {
        return viewState.value
    }

    private fun obtainVersion(variantKey: String): String {
        val formattedVariantKey = if (variantKey.isBlank()) " " else " $variantKey "
        return "${appBuildConfig.versionName}$formattedVariantKey(${appBuildConfig.versionCode})"
    }

    companion object {
        internal const val MAX_EASTER_EGG_COUNT = 12
    }
}
