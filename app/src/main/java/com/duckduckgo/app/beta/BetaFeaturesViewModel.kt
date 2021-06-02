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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.email.AppEmailManager
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Provider

class BetaFeaturesViewModel(
    private val emailManager: EmailManager
) : ViewModel(), LifecycleObserver {

    val viewState: MutableLiveData<ViewState> = MutableLiveData<ViewState>().apply {
        value = ViewState()
    }

    private fun currentViewState(): ViewState {
        return viewState.value!!
    }

    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    init {
        emailManager.signedInFlow().onEach {
            loadInitialData()
        }.launchIn(viewModelScope)
    }

    fun loadInitialData() {
        viewState.value = ViewState(emailState = getEmailState())
    }

    fun onEmailSettingClicked() {
        when (val emailSetting = getEmailSetting()) {
            is EmailSetting.EmailSettingOff -> command.value = Command.LaunchEmailSignIn
            is EmailSetting.EmailSettingOn -> command.value = Command.LaunchEmailSignOut(emailSetting.emailAddress)
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
