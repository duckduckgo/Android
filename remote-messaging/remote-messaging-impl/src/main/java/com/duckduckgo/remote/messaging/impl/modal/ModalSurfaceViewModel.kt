/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.remote.messaging.impl.modal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.impl.modal.cardslist.CardsListRemoteMessagePixelHelper
import com.duckduckgo.remote.messaging.impl.modal.cardslist.RealCardsListRemoteMessagePixelHelper.Companion.PARAM_NAME_DISMISS_TYPE
import com.duckduckgo.remote.messaging.impl.modal.cardslist.RealCardsListRemoteMessagePixelHelper.Companion.PARAM_VALUE_BACK_BUTTON_OR_GESTURE
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class ModalSurfaceViewModel @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val cardsListPixelHelper: CardsListRemoteMessagePixelHelper,
) : ViewModel() {
    private val _viewState = MutableStateFlow<ViewState?>(null)
    private val _command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    val commands: Flow<Command> = _command.receiveAsFlow()
    val viewState: Flow<ViewState?> = _viewState.asStateFlow()

    private var lastRemoteMessageIdSeen: String? = null

    fun onInitialise(activityParams: ModalSurfaceActivityFromMessageId?) {
        val messageId = activityParams?.messageId ?: return
        val messageType = activityParams.messageType

        if (messageType == Content.MessageType.CARDS_LIST) {
            lastRemoteMessageIdSeen = messageId
            _viewState.value = ViewState(messageId = messageId, showCardsListView = true)
        }
    }

    fun onDismiss() {
        viewModelScope.launch(dispatchers.io()) {
            _command.send(Command.DismissMessage)
        }
    }

    fun onBackPressed() {
        val currentState = _viewState.value
        if (currentState?.showCardsListView != true) {
            // If not showing CardsListView, just dismiss without pixel
            viewModelScope.launch {
                _command.send(Command.DismissMessage)
            }
            return
        }

        viewModelScope.launch {
            val customParams = mapOf(
                PARAM_NAME_DISMISS_TYPE to PARAM_VALUE_BACK_BUTTON_OR_GESTURE,
            )
            lastRemoteMessageIdSeen?.let {
                cardsListPixelHelper.dismissCardsListMessage(it, customParams)
            }
            _command.send(Command.DismissMessage)
        }
    }

    data class ViewState(val messageId: String, val showCardsListView: Boolean)

    sealed class Command {
        object DismissMessage : Command()
    }
}
