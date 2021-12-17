/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.waitlist.trackerprotection.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.waitlist.trackerprotection.AppTPWaitlistWorkRequestBuilder
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.waitlist.TrackingProtectionWaitlistManager
import com.duckduckgo.mobile.android.vpn.waitlist.WaitlistState
import com.duckduckgo.mobile.android.vpn.waitlist.api.AppTrackingProtectionWaitlistService
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class AppTPWaitlistViewModel(
    private val waitlistManager: TrackingProtectionWaitlistManager,
    private val waitlistService: AppTrackingProtectionWaitlistService,
    private val workManager: WorkManager,
    private val workRequestBuilder: AppTPWaitlistWorkRequestBuilder,
    private val deviceShieldPixels: DeviceShieldPixels
) : ViewModel() {

    private val viewStateFlow: MutableStateFlow<ViewState> =
        MutableStateFlow(ViewState(waitlistManager.waitlistState()))
    val viewState: StateFlow<ViewState> = viewStateFlow

    private val commandChannel =
        Channel<Command>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val commands = commandChannel.receiveAsFlow()

    sealed class Command {
        object LaunchBetaInstructions : Command()
        object EnterInviteCode : Command()
        object ShowErrorMessage : Command()
        object ShowNotificationDialog : Command()
        object ShowOnboarding : Command()
    }

    data class ViewState(val waitlist: WaitlistState)

    fun getStarted() {
        viewModelScope.launch { commandChannel.send(Command.ShowOnboarding) }
    }

    fun haveAnInviteCode() {
        viewModelScope.launch { commandChannel.send(Command.EnterInviteCode) }
    }

    fun joinTheWaitlist() {
        viewModelScope.launch {
            runCatching { waitlistService.joinWaitlist() }
                .onSuccess {
                    val timestamp = it.timestamp
                    val token = it.token
                    if (timestamp != null && !token.isNullOrBlank()) {
                        joinedWaitlist(timestamp, token)
                    } else {
                        commandChannel.send(Command.ShowErrorMessage)
                    }
                }
                .onFailure { commandChannel.send(Command.ShowErrorMessage) }
        }
    }

    private fun joinedWaitlist(timestamp: Int, token: String) {
        deviceShieldPixels.didShowWaitlistDialog()
        viewModelScope.launch {
            waitlistManager.joinWaitlist(timestamp, token)
            commandChannel.send(Command.ShowNotificationDialog)
            workManager.enqueue(workRequestBuilder.waitlistRequestWork(withBigDelay = false))
        }
    }

    fun learnMore() {
        viewModelScope.launch { commandChannel.send(Command.LaunchBetaInstructions) }
    }

    fun onNotifyMeClicked() {
        deviceShieldPixels.didPressWaitlistDialogNotifyMe()
        viewModelScope.launch { waitlistManager.notifyOnJoinedWaitlist() }
    }

    fun onNoThanksClicked() {
        deviceShieldPixels.didPressWaitlistDialogDismiss()
    }

    fun onDialogDismissed() {
        viewModelScope.launch { viewStateFlow.emit(ViewState(waitlistManager.waitlistState())) }
    }

    fun onCodeRedeemed(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            viewModelScope.launch { viewStateFlow.emit(ViewState(WaitlistState.CodeRedeemed)) }
        }
    }
}

@ContributesMultibinding(AppScope::class)
class AppTPWaitlistViewModelFactory
@Inject
constructor(
    private val waitlistManager: Provider<TrackingProtectionWaitlistManager>,
    private val waitlistService: Provider<AppTrackingProtectionWaitlistService>,
    private val workManager: Provider<WorkManager>,
    private val workRequestBuilder: Provider<AppTPWaitlistWorkRequestBuilder>,
    private val deviceShieldPixels: Provider<DeviceShieldPixels>,
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(AppTPWaitlistViewModel::class.java) ->
                    (AppTPWaitlistViewModel(
                        waitlistManager.get(),
                        waitlistService.get(),
                        workManager.get(),
                        workRequestBuilder.get(),
                        deviceShieldPixels.get(),
                    ) as
                        T)
                else -> null
            }
        }
    }
}
