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

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.duckduckgo.remote.messaging.impl.pixels.RemoteMessagingPixelName
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/**
 * Helper class for handling CardsList-specific remote message operations.
 * Encapsulates pixel firing and repository interactions specific to CardsList messages.
 */
interface CardsListRemoteMessagePixelHelper {

    /**
     * Fires pixel when a card item is shown to the user.
     *
     * @param remoteMessage The remote message containing the card
     * @param cardItem The specific card item that was shown
     */
    fun fireCardItemShownPixel(remoteMessage: RemoteMessage, cardItem: CardItem)

    /**
     * Fires pixel when a card item is clicked by the user.
     *
     * @param remoteMessage The remote message containing the card
     * @param cardItem The specific card item that was clicked
     */
    fun fireCardItemClickedPixel(remoteMessage: RemoteMessage, cardItem: CardItem)

    /**
     * Fires pixel and dismisses the CardsList message with custom parameters.
     * This should be used instead of the generic RemoteMessageModel.onMessageDismissed()
     * for CardsList messages to include CardsList-specific parameters.
     *
     * @param remoteMessageId The id of the remote message to dismiss
     * @param customParams Additional parameters for the dismiss pixel (e.g. dismissType)
     */
    suspend fun dismissCardsListMessage(remoteMessageId: String, customParams: Map<String, String> = emptyMap())
}

@ContributesBinding(AppScope::class)
class RealCardsListRemoteMessagePixelHelper @Inject constructor(
    private val pixel: Pixel,
    private val remoteMessagingRepository: RemoteMessagingRepository,
) : CardsListRemoteMessagePixelHelper {

    override fun fireCardItemShownPixel(remoteMessage: RemoteMessage, cardItem: CardItem) {
        val pixelParams = mapOf(
            PARAM_NAME_MESSAGE_ID to remoteMessage.id,
            PARAM_NAME_CARD_ID to cardItem.id,
        )
        pixel.fire(
            pixel = CardsListRemoteMessagePixelName.REMOTE_MESSAGE_CARD_SHOWN,
            parameters = pixelParams,
        )
    }

    override fun fireCardItemClickedPixel(remoteMessage: RemoteMessage, cardItem: CardItem) {
        val pixelParams = mapOf(
            PARAM_NAME_MESSAGE_ID to remoteMessage.id,
            PARAM_NAME_CARD_ID to cardItem.id,
        )
        pixel.fire(
            pixel = CardsListRemoteMessagePixelName.REMOTE_MESSAGE_CARD_CLICKED,
            parameters = pixelParams,
        )
    }

    override suspend fun dismissCardsListMessage(
        remoteMessageId: String,
        customParams: Map<String, String>,
    ) {
        val pixelParams = buildMap {
            put(PARAM_NAME_MESSAGE_ID, remoteMessageId)
            putAll(customParams)
        }
        pixel.fire(
            pixel = RemoteMessagingPixelName.REMOTE_MESSAGE_DISMISSED,
            parameters = pixelParams,
        )
        remoteMessagingRepository.dismissMessage(remoteMessageId)
    }

    companion object {
        private const val PARAM_NAME_MESSAGE_ID = "message"
        private const val PARAM_NAME_CARD_ID = "card"
        internal const val PARAM_NAME_DISMISS_TYPE = "dismissType"
        internal const val PARAM_VALUE_CLOSE_BUTTON = "close_button"
        internal const val PARAM_VALUE_BACK_BUTTON_OR_GESTURE = "back_button_or_gesture"
    }
}

/**
 * Pixel names specific to CardsList remote messages.
 */
enum class CardsListRemoteMessagePixelName(override val pixelName: String) : Pixel.PixelName {
    REMOTE_MESSAGE_CARD_SHOWN("m_remote_message_card_shown"),
    REMOTE_MESSAGE_CARD_CLICKED("m_remote_message_card_clicked"),
}
