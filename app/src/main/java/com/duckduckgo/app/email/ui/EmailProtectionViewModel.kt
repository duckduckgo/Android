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
import com.duckduckgo.app.email.api.EmailService
import com.duckduckgo.app.email.db.EmailDataStore
import com.duckduckgo.app.email.job.WaitlistSyncWorkRequestBuilder
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

class EmailProtectionViewModel(
    private val pixel: Pixel,
    private val emailDataStore: EmailDataStore,
    private val emailService: EmailService,
    private val workManager: WorkManager,
    private val waitlistSyncWorkRequestBuilder: WaitlistSyncWorkRequestBuilder
) : ViewModel() {

    private val viewState: MutableStateFlow<UiState> = MutableStateFlow(calculateUiState())
    val viewFlow: StateFlow<UiState> = viewState

    private val commandChannel = Channel<Command>(Channel.BUFFERED)
    val commandsFlow = commandChannel.receiveAsFlow()

    sealed class Command {
        data class OpenUrl(val url: String) : Command()
        data class ShowErrorMessage(val error: String) : Command()
    }

    sealed class UiState {
        object NotJoinedQueue : UiState()
        data class JoinedQueue(val timestamp: String) : UiState()
        data class InBeta(val code: String) : UiState()
    }

    fun haveDuckAddress() {
        viewModelScope.launch {
            commandChannel.send(Command.OpenUrl(LOGIN_URL))
        }
    }

    fun haveInviteCode() {
        viewModelScope.launch {
            commandChannel.send(Command.OpenUrl(SIGN_UP_URL))
        }
    }

    suspend fun joinWaitlist() {
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
            commandChannel.send(Command.OpenUrl(ADDRESS_BLOG_POST))
        }
    }

    fun readPrivacyGuarantees() {
        viewModelScope.launch {
            commandChannel.send(Command.OpenUrl(PRIVACY_GUARANTEE))
        }
    }

    private fun joinedWaitlist(timestamp: Int, token: String) {
        viewModelScope.launch {
            if (emailDataStore.waitlistTimestamp == -1) { emailDataStore.waitlistTimestamp = timestamp }
            if (emailDataStore.waitlistToken == null) { emailDataStore.waitlistToken = token }
            viewState.emit(calculateUiState())
        }

        val workRequest = waitlistSyncWorkRequestBuilder.appConfigurationWork()
        workManager.enqueueUniquePeriodicWork(WaitlistSyncWorkRequestBuilder.EMAIL_WAITLIST_SYNC_WORK_TAG, ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }

    private fun calculateUiState(): UiState {
        if (emailDataStore.waitlistTimestamp != -1 && emailDataStore.inviteCode == null) {
            return UiState.JoinedQueue(emailDataStore.waitlistTimestamp.toString())
        }
        emailDataStore.inviteCode?.let {
            return UiState.InBeta(it)
        }
        return UiState.NotJoinedQueue
    }

    companion object {
        const val PRIVACY_GUARANTEE = "https://help.duckduckgo.com/duckduckgo-help-pages/privacy/gpc/"
        const val ADDRESS_BLOG_POST = "https://help.duckduckgo.com/duckduckgo-help-pages/privacy/gpc/"
        const val SIGN_UP_URL = "https://quack.duckduckgo.com/email/signup"
        const val LOGIN_URL = "https://quack.duckduckgo.com/email/login"
    }

}

@ContributesMultibinding(AppObjectGraph::class)
class EmailProtectionViewModelFactory @Inject constructor(
    private val pixel: Provider<Pixel>,
    private val emailDataStore: Provider<EmailDataStore>,
    private val emailService: Provider<EmailService>,
    private val workManager: Provider<WorkManager>,
    private val waitlistSyncWorkRequestBuilder: Provider<WaitlistSyncWorkRequestBuilder>
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(EmailProtectionViewModel::class.java) -> (EmailProtectionViewModel(pixel.get(), emailDataStore.get(), emailService.get(), workManager.get(), waitlistSyncWorkRequestBuilder.get()) as T)
                else -> null
            }
        }
    }
}
