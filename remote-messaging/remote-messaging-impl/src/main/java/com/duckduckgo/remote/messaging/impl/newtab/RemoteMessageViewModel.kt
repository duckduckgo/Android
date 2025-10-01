/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.remote.messaging.impl.newtab

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.playstore.PlayStoreUtils
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.Action.AppTpOnboarding
import com.duckduckgo.remote.messaging.api.Action.DefaultBrowser
import com.duckduckgo.remote.messaging.api.Action.Dismiss
import com.duckduckgo.remote.messaging.api.Action.Navigation
import com.duckduckgo.remote.messaging.api.Action.PlayStore
import com.duckduckgo.remote.messaging.api.Action.Share
import com.duckduckgo.remote.messaging.api.Action.Survey
import com.duckduckgo.remote.messaging.api.Action.Url
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessageModel
import com.duckduckgo.survey.api.SurveyParameterManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ViewScope::class)
class RemoteMessageViewModel @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val remoteMessagingModel: RemoteMessageModel,
    private val playStoreUtils: PlayStoreUtils,
    private val surveyParameterManager: SurveyParameterManager,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(
        val message: RemoteMessage? = null,
        val newMessage: Boolean = false,
    )

    sealed class Command {
        data object DismissMessage : Command()
        data class LaunchPlayStore(val appPackage: String) : Command()
        data class SubmitUrl(val url: String) : Command()
        data object LaunchDefaultBrowser : Command()
        data object LaunchAppTPOnboarding : Command()
        data class SharePromoLinkRMF(
            val url: String,
            val shareTitle: String,
        ) : Command()

        data class LaunchScreen(
            val screen: String,
            val payload: String,
        ) : Command()
    }

    private var lastRemoteMessageSeen: RemoteMessage? = null

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()
    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    fun commands(): Flow<Command> = command.receiveAsFlow()

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)

        viewModelScope.launch(dispatchers.io()) {
            remoteMessagingModel.getActiveMessages()
                .flowOn(dispatchers.io())
                .onEach { message ->
                    withContext(dispatchers.main()) {
                        val newMessage = message?.id != lastRemoteMessageSeen?.id
                        if (newMessage) {
                            lastRemoteMessageSeen = message
                        }

                        _viewState.emit(
                            viewState.value.copy(
                                message = message,
                                newMessage = newMessage,
                            ),
                        )
                    }
                }
                .flowOn(dispatchers.main())
                .launchIn(viewModelScope)
        }
    }

    fun onMessageShown() {
        val message = lastRemoteMessageSeen ?: return
        viewModelScope.launch {
            remoteMessagingModel.onMessageShown(message)
        }
    }

    fun onMessageCloseButtonClicked() {
        val message = lastRemoteMessageSeen ?: return
        viewModelScope.launch {
            remoteMessagingModel.onMessageDismissed(message)
        }
    }

    fun onMessagePrimaryButtonClicked() {
        val message = lastRemoteMessageSeen ?: return
        viewModelScope.launch {
            val action = remoteMessagingModel.onPrimaryActionClicked(message) ?: return@launch
            command.send(action.asNewTabCommand())
        }
    }

    fun onMessageSecondaryButtonClicked() {
        val message = lastRemoteMessageSeen ?: return
        viewModelScope.launch {
            val action = remoteMessagingModel.onSecondaryActionClicked(message) ?: return@launch
            command.send(action.asNewTabCommand())
        }
    }

    fun onMessageActionButtonClicked() {
        val message = lastRemoteMessageSeen ?: return
        viewModelScope.launch {
            val action = remoteMessagingModel.onActionClicked(message) ?: return@launch
            command.send(action.asNewTabCommand())
        }
    }

    fun openPlayStore(appPackage: String) {
        playStoreUtils.launchPlayStore(appPackage)
    }

    private suspend fun Action.asNewTabCommand(): Command {
        return when (this) {
            is Dismiss -> Command.DismissMessage
            is PlayStore -> Command.LaunchPlayStore(this.value)
            is Url -> Command.SubmitUrl(this.value)
            is DefaultBrowser -> Command.LaunchDefaultBrowser
            is AppTpOnboarding -> Command.LaunchAppTPOnboarding
            is Share -> Command.SharePromoLinkRMF(this.value, this.title)
            is Navigation -> {
                Command.LaunchScreen(this.value, this.additionalParameters?.get("payload").orEmpty())
            }

            is Survey -> {
                val queryParams = additionalParameters?.get("queryParams")?.split(";") ?: emptyList()
                Command.SubmitUrl(surveyParameterManager.buildSurveyUrl(value, queryParams))
            }
        }
    }
}
