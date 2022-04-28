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
class VpnPagesViewModel  @Inject constructor(
    private val pixel: Pixel
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    private var viewHasShown: Boolean = false

    fun introPageBecameVisible() {
        if (!viewHasShown) {
            viewHasShown = true
            pixel.fire(AppPixelName.ONBOARDING_VPN_INTRO_SHOWN)
        }
    }

    fun permissionPageBecameVisible() {
        if (!viewHasShown) {
            viewHasShown = true
            pixel.fire(AppPixelName.ONBOARDING_VPN_PERMISSION_SHOWN)
        }
    }

    fun onContinueToVpnExplanation() {
        pixel.fire(AppPixelName.ONBOARDING_VPN_INTRO_CONTINUED)
        viewModelScope.launch {
            command.send(Command.ContinueToVpnExplanation)
        }
    }

    fun onAskVpnPermission() {
        pixel.fire(AppPixelName.ONBOARDING_VPN_PERMISSION_LAUNCHED)
        viewModelScope.launch {
            command.send(Command.AskVpnPermission)
        }
    }

    fun onFinishVpnOnboarding() {
        pixel.fire(AppPixelName.ONBOARDING_VPN_PERMISSION_CONTINUED)
        viewModelScope.launch {
            command.send(Command.ContinueToVpnExplanation)
        }
    }

    fun onLeaveVpnIntro() {
        pixel.fire(AppPixelName.ONBOARDING_VPN_INTRO_SKIPPED)
        viewModelScope.launch {
            command.send(Command.LeaveVpnIntro)
        }
    }

    fun onLeaveVpnPermission() {
        pixel.fire(AppPixelName.ONBOARDING_VPN_PERMISSION_SKIPPED)
        viewModelScope.launch {
            command.send(Command.LeaveVpnPermission)
        }
    }

    sealed class Command {
        object ContinueToVpnExplanation : Command()
        object LeaveVpnIntro : Command()
        object LeaveVpnPermission : Command()
        object AskVpnPermission : Command()
    }


}
