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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.duckduckgo.app.email.AppEmailManager
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.email.api.EmailService
import com.duckduckgo.app.email.waitlist.WaitlistSyncWorkRequestBuilder
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

class EmailProtectionViewModel(
    private val emailManager: EmailManager,
    private val emailService: EmailService,
    private val workManager: WorkManager,
    private val waitlistSyncWorkRequestBuilder: WaitlistSyncWorkRequestBuilder,
) : ViewModel() {

    private val viewState: MutableStateFlow<UiState> = MutableStateFlow(UiState(waitlistState = emailManager.waitlistState()))
    val viewFlow: StateFlow<UiState> = viewState

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val commandsFlow = commandChannel.receiveAsFlow()

    sealed class Command {
        data class OpenUrl(val url: String, val openInBrowser: Boolean) : Command()
        data class ShowErrorMessage(val error: String) : Command()
        object ShowNotificationDialog : Command()
    }

    data class UiState(val waitlistState: AppEmailManager.WaitlistState)

    fun haveADuckAddress() {
        viewModelScope.launch {
            commandChannel.send(Command.OpenUrl(url = LOGIN_URL, openInBrowser = true))
        }
    }

    fun haveAnInviteCode() {
        viewModelScope.launch {
            commandChannel.send(Command.OpenUrl(url = "$SIGN_UP_URL${emailManager.getInviteCode()}", openInBrowser = true))
        }
    }

    suspend fun joinTheWaitlist() {
        runCatching {
            emailService.joinWaitlist()
        }.onSuccess {
            joinedWaitlist(it.timestamp, it.token)
        }.onFailure {
            commandChannel.send(Command.ShowErrorMessage("Something went wrong"))
        }
    }

    fun readBlogPost() {
        viewModelScope.launch {
            commandChannel.send(Command.OpenUrl(url = ADDRESS_BLOG_POST, openInBrowser = false))
        }
    }

    fun readPrivacyGuarantees() {
        viewModelScope.launch {
            commandChannel.send(Command.OpenUrl(url = PRIVACY_GUARANTEE, openInBrowser = false))
        }
    }

    private fun joinedWaitlist(timestamp: Int, token: String) {
        viewModelScope.launch {
            emailManager.joinWaitlist(timestamp, token)
            viewState.emit(UiState(emailManager.waitlistState()))
            commandChannel.send(Command.ShowNotificationDialog)
        }

        val workRequest = waitlistSyncWorkRequestBuilder.appConfigurationWork()
        workManager.enqueueUniquePeriodicWork(WaitlistSyncWorkRequestBuilder.EMAIL_WAITLIST_SYNC_WORK_TAG, ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }

    companion object {
        const val PRIVACY_GUARANTEE = "https://help.duckduckgo.com/duckduckgo-help-pages/privacy/gpc/"
        const val ADDRESS_BLOG_POST = "https://help.duckduckgo.com/duckduckgo-help-pages/privacy/gpc/"
        const val SIGN_UP_URL = "https://quack.duckduckgo.com/email/signup?inviteCode="
        const val LOGIN_URL = "https://quack.duckduckgo.com/email/login"
    }

}

@ContributesMultibinding(AppObjectGraph::class)
class EmailProtectionViewModelFactory @Inject constructor(
    private val emailManager: Provider<EmailManager>,
    private val emailService: Provider<EmailService>,
    private val workManager: Provider<WorkManager>,
    private val waitlistSyncWorkRequestBuilder: Provider<WaitlistSyncWorkRequestBuilder>,
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(EmailProtectionViewModel::class.java) -> (EmailProtectionViewModel(emailManager.get(), emailService.get(), workManager.get(), waitlistSyncWorkRequestBuilder.get()) as T)
                else -> null
            }
        }
    }
}
