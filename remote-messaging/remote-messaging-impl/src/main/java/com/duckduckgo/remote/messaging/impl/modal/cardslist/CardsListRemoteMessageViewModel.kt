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

package com.duckduckgo.remote.messaging.impl.modal.cardslist

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessageModel
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.duckduckgo.remote.messaging.impl.modal.cardslist.RealCardsListRemoteMessagePixelHelper.Companion.PARAM_NAME_DISMISS_TYPE
import com.duckduckgo.remote.messaging.impl.modal.cardslist.RealCardsListRemoteMessagePixelHelper.Companion.PARAM_VALUE_CLOSE_BUTTON
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver")
@ContributesViewModel(ViewScope::class)
class CardsListRemoteMessageViewModel @Inject constructor(
    private val remoteMessagingRepository: RemoteMessagingRepository,
    private val remoteMessagingModel: RemoteMessageModel,
    private val commandActionMapper: CommandActionMapper,
    private val dispatchers: DispatcherProvider,
    private val cardsListPixelHelper: CardsListRemoteMessagePixelHelper,
) : ViewModel(), DefaultLifecycleObserver, CardItemClickListener {

    private val _viewState = MutableStateFlow<ViewState?>(null)
    private val _command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    val commands: Flow<Command> = _command.receiveAsFlow()
    val viewState: Flow<ViewState?> = _viewState.asStateFlow()

    private var lastRemoteMessageSeen: RemoteMessage? = null

    fun init(messageId: String?) {
        if (messageId == null) {
            viewModelScope.launch {
                _command.send(Command.DismissMessage)
            }
            return
        }

        viewModelScope.launch(dispatchers.io()) {
            val message = remoteMessagingRepository.getMessageById(messageId)
            val newMessage = message?.id != lastRemoteMessageSeen?.id
            if (newMessage) {
                lastRemoteMessageSeen = message
            }
            val cardsList = message?.content as? Content.CardsList
            if (cardsList != null) {
                _viewState.value = ViewState(cardsList)
            } else {
                _command.send(Command.DismissMessage)
            }
        }
    }

    fun onMessageShown() {
        val message = lastRemoteMessageSeen ?: return
        viewModelScope.launch {
            remoteMessagingModel.onMessageShown(message)
            val cardsList = message.content as? Content.CardsList
            cardsList?.listItems?.forEach { cardItem ->
                cardsListPixelHelper.fireCardItemShownPixel(message, cardItem)
            }
        }
    }

    fun onCloseButtonClicked() {
        val message = lastRemoteMessageSeen ?: return
        viewModelScope.launch {
            _command.send(Command.DismissMessage)
            val customParams = mapOf(
                PARAM_NAME_DISMISS_TYPE to PARAM_VALUE_CLOSE_BUTTON,
            )
            cardsListPixelHelper.dismissCardsListMessage(message.id, customParams)
        }
    }

    fun onActionButtonClicked() {
        val message = lastRemoteMessageSeen ?: return
        viewModelScope.launch {
            val action = _viewState.value?.cardsLists?.primaryAction
            action?.let {
                val command = commandActionMapper.asCommand(it)
                _command.send(command)
                remoteMessagingModel.onPrimaryActionClicked(message)
            }
        }
    }

    override fun onItemClicked(item: CardItem) {
        viewModelScope.launch {
            val message = lastRemoteMessageSeen ?: return@launch
            val action = item.primaryAction
            val command = commandActionMapper.asCommand(action)
            _command.send(command)
            cardsListPixelHelper.fireCardItemClickedPixel(message, item)
        }
    }

    data class ViewState(val cardsLists: Content.CardsList)

    sealed class Command {
        data object DismissMessage : Command()
        data class LaunchPlayStore(val appPackage: String) : Command()
        data class SubmitUrl(val url: String) : Command()
        data class SubmitUrlInContext(val url: String) : Command()
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

        data object LaunchDefaultCredentialProvider : Command()
    }
}
