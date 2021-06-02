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

package com.duckduckgo.app.beta

import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.email.AppEmailManager
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

class BetaFeaturesViewModel(
    private val emailManager: EmailManager
) : ViewModel(), LifecycleObserver {

    private val viewState: MutableStateFlow<ViewState> = MutableStateFlow(ViewState())
    val viewFlow: StateFlow<ViewState> = viewState

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val commandsFlow = commandChannel.receiveAsFlow()

    fun resume() {
        viewModelScope.launch {
            emitViewState()
        }
    }

    private suspend fun emitViewState() {
        viewState.emit(ViewState(emailState = getEmailState()))
    }

    fun onEmailSettingClicked() {
        viewModelScope.launch {
            when (val emailSetting = getEmailSetting()) {
                is EmailSetting.EmailSettingOff -> commandChannel.send(Command.LaunchEmailSignIn)
                is EmailSetting.EmailSettingOn -> commandChannel.send(Command.LaunchEmailSignOut(emailSetting.emailAddress))
            }
        }
    }

    private fun getEmailState(): EmailState {
        return when (getEmailSetting()) {
            is EmailSetting.EmailSettingOff -> {
                when (emailManager.waitlistState()) {
                    is AppEmailManager.WaitlistState.InBeta -> EmailState.Disabled
                    is AppEmailManager.WaitlistState.JoinedQueue -> EmailState.Disabled
                    is AppEmailManager.WaitlistState.NotJoinedQueue -> EmailState.JoinWaitlist
                }
            }
            is EmailSetting.EmailSettingOn -> EmailState.Enabled
        }
    }

    private fun getEmailSetting(): EmailSetting {
        val emailAddress = emailManager.getEmailAddress()

        return if (emailManager.isSignedIn()) {
            when (emailAddress) {
                null -> EmailSetting.EmailSettingOff
                else -> EmailSetting.EmailSettingOn(emailAddress)
            }
        } else {
            EmailSetting.EmailSettingOff
        }
    }

    sealed class EmailSetting {
        object EmailSettingOff : EmailSetting()
        data class EmailSettingOn(val emailAddress: String) : EmailSetting()
    }

    data class ViewState(
        val emailState: EmailState = EmailState.JoinWaitlist
    )

    sealed class Command {
        data class LaunchEmailSignOut(val emailAddress: String) : Command()
        object LaunchEmailSignIn : Command()
    }

    sealed class EmailState {
        object JoinWaitlist : EmailState()
        object Enabled : EmailState()
        object Disabled : EmailState()
    }
}

@ContributesMultibinding(AppObjectGraph::class)
class BetaFeaturesViewModelFactory @Inject constructor(
    private val emailManager: Provider<EmailManager>,
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(BetaFeaturesViewModel::class.java) -> (BetaFeaturesViewModel(emailManager.get()) as T)
                else -> null
            }
        }
    }
}
