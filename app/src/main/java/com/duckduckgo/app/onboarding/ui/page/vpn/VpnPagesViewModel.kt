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

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.ContinueToVpnExplanation
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.ContinueVpnConflictDialog
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.DismissVpnConflictDialog
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.EnableVPN
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.FinishVpnOnboarding
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.IntroPageBecameVisible
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.LearnMore
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.PermissionPageBecameVisible
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.LeaveVpnIntro
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.LeaveVpnPermission
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.OpenSettingVpnConflictDialog
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.RequestVpnPermission
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.VpnPermissionDenied
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.VpnPermissionGranted
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Command.RequestVpnPermission
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.vpn.network.VpnDetector
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class VpnPagesViewModel @Inject constructor(
    private val pixel: Pixel,
    private val vpnPixels: DeviceShieldPixels,
    private val vpnDetector: VpnDetector
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    private var viewHasShown: Boolean = false
    private var lastVpnRequestTime = -1L

    fun onAction(action: Action) {
        when (action) {
            IntroPageBecameVisible -> introPageBecameVisible()
            PermissionPageBecameVisible -> permissionPageBecameVisible()
            ContinueToVpnExplanation -> onContinueToVpnExplanation()
            EnableVPN -> onAskVpnPermission()
            FinishVpnOnboarding -> onFinishVpnOnboarding()
            LeaveVpnIntro -> onLeaveVpnIntro()
            LeaveVpnPermission -> onLeaveVpnPermission()
            LearnMore -> introPageBecameVisible()
            DismissVpnConflictDialog -> vpnPixels.didChooseToDismissVpnConflictDialog()
            OpenSettingVpnConflictDialog -> onOpenVpnSettings()
            ContinueVpnConflictDialog -> onContinueVpnConflictDialog()
            VpnPermissionGranted -> onVpnPermissionGranted()
            is VpnPermissionDenied -> onVpnPermissionDenied(action.intent)
        }
    }

    private fun sendCommand(newCommand: Command) {
        viewModelScope.launch {
            command.send(newCommand)
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
        sendCommand(Command.ContinueToVpnExplanation)
    }

    private fun onEnableVpn() {
        if (vpnDetector.isVpnDetected()) {
            vpnPixels.didShowVpnConflictDialog()
            sendCommand(Command.ShowVpnConflictDialog)
        } else {
            sendCommand(Command.CheckVPNPermission)
        }
    }

    private fun onAskVpnPermission() {
        pixel.fire(AppPixelName.ONBOARDING_VPN_PERMISSION_LAUNCHED)
        sendCommand(Command.AskVpnPermission)
    }

    private fun onFinishVpnOnboarding() {
        pixel.fire(AppPixelName.ONBOARDING_VPN_PERMISSION_CONTINUED)
        sendCommand(Command.ContinueToVpnExplanation)
    }

    private fun onLeaveVpnIntro() {
        pixel.fire(AppPixelName.ONBOARDING_VPN_INTRO_SKIPPED)
        sendCommand(Command.LeaveVpnIntro)
    }

    private fun onLeaveVpnPermission() {
        pixel.fire(AppPixelName.ONBOARDING_VPN_PERMISSION_SKIPPED)
        sendCommand(Command.LeaveVpnPermission)
    }

    private fun openFAQ() {
        pixel.fire(AppPixelName.ONBOARDING_VPN_PERMISSION_FAQ_LAUNCHED)
        sendCommand(Command.OpenVpnFAQ)
    }

    private fun onOpenVpnSettings(){
        vpnPixels.didChooseToOpenSettingsFromVpnConflictDialog()
        sendCommand(Command.OpenVpnSettings)
    }

    private fun onContinueVpnConflictDialog(){
        vpnPixels.didChooseToContinueFromVpnConflictDialog()
        sendCommand(Command.CheckVPNPermission)
    }

    private fun onVpnPermissionGranted(){
        vpnPixels.enableFromOnboarding()
        sendCommand(Command.StartVpn)
    }

    private fun onVpnPermissionDenied(intent: Intent) {
        lastVpnRequestTime = System.currentTimeMillis()
        sendCommand(RequestVpnPermission(intent))
    }

    sealed class Action {
        object IntroPageBecameVisible : Action()
        object PermissionPageBecameVisible : Action()
        object ContinueToVpnExplanation : Action()
        object EnableVPN : Action()
        object FinishVpnOnboarding : Action()
        object LeaveVpnIntro : Action()
        object LeaveVpnPermission : Action()
        object LearnMore : Action()

        object DismissVpnConflictDialog : Action()
        object OpenSettingVpnConflictDialog : Action()
        object ContinueVpnConflictDialog : Action()
        object VpnPermissionGranted : Action()
        data class VpnPermissionDenied(val intent: Intent) : Action()
    }

    sealed class Command {
        object ContinueToVpnExplanation : Command()
        object LeaveVpnIntro : Command()
        object LeaveVpnPermission : Command()
        object CheckVPNPermission : Command()
        object OpenVpnFAQ : Command()

        object ShowVpnConflictDialog : Command()
        object ShowVpnAlwaysOnConflictDialog : Command()
        object AskVpnPermission : Command()
        object OpenVpnSettings : Command()

        object StartVpn : Command()

        data class RequestVpnPermission(val intent: Intent) : Command()
    }

    sealed class VpnPermissionStatus {
        object Granted : VpnPermissionStatus()
        data class Denied(val intent: Intent) : VpnPermissionStatus()
    }
}
