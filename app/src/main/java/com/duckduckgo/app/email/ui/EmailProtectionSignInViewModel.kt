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

package com.duckduckgo.app.email.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.duckduckgo.app.email.AppEmailManager
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.email.api.EmailService
import com.duckduckgo.app.email.waitlist.WaitlistWorkRequestBuilder
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

class EmailProtectionSignInViewModel(
    private val emailManager: EmailManager,
    private val emailService: EmailService,
    private val workManager: WorkManager,
    private val waitlistWorkRequestBuilder: WaitlistWorkRequestBuilder,
) : ViewModel() {

    private val viewStateFlow: MutableStateFlow<ViewState> = MutableStateFlow(ViewState(waitlistState = emailManager.waitlistState()))
    val viewState: StateFlow<ViewState> = viewStateFlow

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val commands = commandChannel.receiveAsFlow()

    sealed class Command {
        data class OpenUrl(val url: String) : Command()
        object ShowErrorMessage : Command()
        object ShowNotificationDialog : Command()
    }

    data class ViewState(val waitlistState: AppEmailManager.WaitlistState)

    fun haveADuckAddress() {
        viewModelScope.launch {
            commandChannel.send(Command.OpenUrl(url = LOGIN_URL))
        }
    }

    fun haveAnInviteCode() {
        viewModelScope.launch {
            commandChannel.send(Command.OpenUrl(url = "$SIGN_UP_URL${emailManager.getInviteCode()}"))
        }
    }

    fun joinTheWaitlist() {
        viewModelScope.launch {
            runCatching {
                emailService.joinWaitlist()
            }.onSuccess {
                val timestamp = it.timestamp
                val token = it.token
                if (timestamp != null && !token.isNullOrBlank()) {
                    joinedWaitlist(it.timestamp, it.token)
                } else {
                    commandChannel.send(Command.ShowErrorMessage)
                }
            }.onFailure {
                commandChannel.send(Command.ShowErrorMessage)
            }
        }
    }

    fun readBlogPost() {
        viewModelScope.launch {
            commandChannel.send(Command.OpenUrl(url = ADDRESS_BLOG_POST))
        }
    }

    fun readPrivacyGuarantees() {
        viewModelScope.launch {
            commandChannel.send(Command.OpenUrl(url = PRIVACY_GUARANTEE))
        }
    }

    fun onNotifyMeClicked() {
        viewModelScope.launch {
            emailManager.notifyOnJoinedWaitlist()
        }
    }

    fun onDialogDismissed() {
        viewModelScope.launch {
            viewStateFlow.emit(ViewState(emailManager.waitlistState()))
        }
    }

    private fun joinedWaitlist(timestamp: Int, token: String) {
        viewModelScope.launch {
            emailManager.joinWaitlist(timestamp, token)
            commandChannel.send(Command.ShowNotificationDialog)
            workManager.enqueue(waitlistWorkRequestBuilder.waitlistRequestWork(withBigDelay = false))
        }
    }

    companion object {
        const val PRIVACY_GUARANTEE = "https://quack.duckduckgo.com/email/privacy-guarantees"
        const val ADDRESS_BLOG_POST = "https://quack.duckduckgo.com/email/learn-more"
        const val SIGN_UP_URL = "https://quack.duckduckgo.com/email/start?inviteCode="
        const val LOGIN_URL = "https://quack.duckduckgo.com/email/login"
    }

}

@ContributesMultibinding(AppObjectGraph::class)
class EmailProtectionSignViewModelFactory @Inject constructor(
    private val emailManager: Provider<EmailManager>,
    private val emailService: Provider<EmailService>,
    private val workManager: Provider<WorkManager>,
    private val waitlistWorkRequestBuilder: Provider<WaitlistWorkRequestBuilder>,
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(EmailProtectionSignInViewModel::class.java) -> (EmailProtectionSignInViewModel(emailManager.get(), emailService.get(), workManager.get(), waitlistWorkRequestBuilder.get()) as T)
                else -> null
            }
        }
    }
}
