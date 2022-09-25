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
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.waitlist.trackerprotection.AppTPWaitlistWorkRequestBuilder
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.waitlist.AppTPWaitlistManager
import com.duckduckgo.mobile.android.vpn.waitlist.api.AppTrackingProtectionWaitlistService
import com.duckduckgo.mobile.android.vpn.waitlist.store.AtpWaitlistStateRepository
import com.duckduckgo.mobile.android.vpn.waitlist.store.WaitlistState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class AppTPWaitlistViewModel @Inject constructor(
    private val waitlistManager: AppTPWaitlistManager,
    private val atpWaitlistStateRepository: AtpWaitlistStateRepository,
    private val waitlistService: AppTrackingProtectionWaitlistService,
    private val workManager: WorkManager,
    private val workRequestBuilder: AppTPWaitlistWorkRequestBuilder,
    private val deviceShieldPixels: DeviceShieldPixels
) : ViewModel() {

    private val viewStateFlow: MutableStateFlow<ViewState> =
        MutableStateFlow(ViewState(atpWaitlistStateRepository.getState()))
    val viewState: StateFlow<ViewState> = viewStateFlow

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
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
        viewModelScope.launch {
            commandChannel.send(Command.ShowOnboarding)
        }
    }

    fun haveAnInviteCode() {
        viewModelScope.launch {
            commandChannel.send(Command.EnterInviteCode)
        }
    }

    fun joinTheWaitlist() {
        viewModelScope.launch {
            runCatching {
                waitlistService.joinWaitlist()
            }.onSuccess {
                val timestamp = it.timestamp
                val token = it.token
                if (timestamp != null && !token.isNullOrBlank()) {
                    joinedWaitlist(timestamp, token)
                } else {
                    commandChannel.send(Command.ShowErrorMessage)
                }
            }.onFailure {
                commandChannel.send(Command.ShowErrorMessage)
            }
        }
    }

    private fun joinedWaitlist(
        timestamp: Int,
        token: String
    ) {
        viewModelScope.launch {
            waitlistManager.joinWaitlist(timestamp, token)
            waitlistManager.notifyOnJoinedWaitlist()
            workManager.enqueue(workRequestBuilder.waitlistRequestWork(withBigDelay = false))
            viewStateFlow.emit(ViewState(atpWaitlistStateRepository.getState()))
        }
    }

    fun learnMore() {
        viewModelScope.launch {
            commandChannel.send(Command.LaunchBetaInstructions)
        }
    }

    fun onNotifyMeClicked() {
        viewModelScope.launch {
            deviceShieldPixels.didPressWaitlistDialogNotifyMe()
            waitlistManager.notifyOnJoinedWaitlist()
        }
    }

    fun onNoThanksClicked() {
        viewModelScope.launch {
            deviceShieldPixels.didPressWaitlistDialogDismiss()
        }
    }

    fun onDialogDismissed() {
        viewModelScope.launch {
            viewStateFlow.emit(ViewState(atpWaitlistStateRepository.getState()))
        }
    }

    fun onCodeRedeemed(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            viewModelScope.launch {
                viewStateFlow.emit(ViewState(WaitlistState.CodeRedeemed))
            }
        }
    }
}
