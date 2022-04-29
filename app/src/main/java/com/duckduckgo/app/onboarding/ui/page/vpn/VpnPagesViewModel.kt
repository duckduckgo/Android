/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui.page.vpn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.AskVpnPermission
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.ContinueToVpnExplanation
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.FinishVpnOnboarding
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.IntroPageBecameVisible
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.LearnMore
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.PermissionPageBecameVisible
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.LeaveVpnIntro
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.LeaveVpnPermission
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Command.OpenVpnFAQ
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.FragmentScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class VpnPagesViewModel @Inject constructor(
    private val pixel: Pixel
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    private var viewHasShown: Boolean = false

    fun onAction(action: Action) {
        when (action) {
            IntroPageBecameVisible -> introPageBecameVisible()
            PermissionPageBecameVisible -> permissionPageBecameVisible()
            ContinueToVpnExplanation -> onContinueToVpnExplanation()
            AskVpnPermission -> onAskVpnPermission()
            FinishVpnOnboarding -> onFinishVpnOnboarding()
            LeaveVpnIntro -> onLeaveVpnIntro()
            LeaveVpnPermission -> onLeaveVpnPermission()
            LearnMore -> introPageBecameVisible()
        }
    }

    private fun introPageBecameVisible() {
        if (!viewHasShown) {
            viewHasShown = true
            pixel.fire(AppPixelName.ONBOARDING_VPN_INTRO_SHOWN)
        }
    }

    private fun permissionPageBecameVisible() {
        if (!viewHasShown) {
            viewHasShown = true
            pixel.fire(AppPixelName.ONBOARDING_VPN_PERMISSION_SHOWN)
        }
    }

    private fun onContinueToVpnExplanation() {
        pixel.fire(AppPixelName.ONBOARDING_VPN_INTRO_CONTINUED)
        viewModelScope.launch {
            command.send(Command.ContinueToVpnExplanation)
        }
    }

    private fun onAskVpnPermission() {
        pixel.fire(AppPixelName.ONBOARDING_VPN_PERMISSION_LAUNCHED)
        viewModelScope.launch {
            command.send(Command.AskVpnPermission)
        }
    }

    private fun onFinishVpnOnboarding() {
        pixel.fire(AppPixelName.ONBOARDING_VPN_PERMISSION_CONTINUED)
        viewModelScope.launch {
            command.send(Command.ContinueToVpnExplanation)
        }
    }

    private fun onLeaveVpnIntro() {
        pixel.fire(AppPixelName.ONBOARDING_VPN_INTRO_SKIPPED)
        viewModelScope.launch {
            command.send(Command.LeaveVpnIntro)
        }
    }

    private fun onLeaveVpnPermission() {
        pixel.fire(AppPixelName.ONBOARDING_VPN_PERMISSION_SKIPPED)
        viewModelScope.launch {
            command.send(Command.LeaveVpnPermission)
        }
    }

    private fun openFAQ() {
        pixel.fire(AppPixelName.ONBOARDING_VPN_PERMISSION_FAQ_LAUNCHED)
        viewModelScope.launch {
            command.send(Command.OpenVpnFAQ)
        }
    }

    sealed class Action {
        object IntroPageBecameVisible : Action()
        object PermissionPageBecameVisible : Action()
        object ContinueToVpnExplanation : Action()
        object AskVpnPermission : Action()
        object FinishVpnOnboarding : Action()
        object LeaveVpnIntro : Action()
        object LeaveVpnPermission : Action()
        object LearnMore : Action()
    }

    sealed class Command {
        object ContinueToVpnExplanation : Command()
        object LeaveVpnIntro : Command()
        object LeaveVpnPermission : Command()
        object AskVpnPermission : Command()

        object OpenVpnFAQ : Command()
    }
}
