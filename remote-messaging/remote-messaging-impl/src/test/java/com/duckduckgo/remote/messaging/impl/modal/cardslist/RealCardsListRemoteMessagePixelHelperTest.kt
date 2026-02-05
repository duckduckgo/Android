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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.CardItemType
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.duckduckgo.remote.messaging.api.Surface
import com.duckduckgo.remote.messaging.impl.modal.cardslist.RealCardsListRemoteMessagePixelHelper.Companion.PARAM_NAME_DISMISS_TYPE
import com.duckduckgo.remote.messaging.impl.modal.cardslist.RealCardsListRemoteMessagePixelHelper.Companion.PARAM_VALUE_CLOSE_BUTTON
import com.duckduckgo.remote.messaging.impl.pixels.RemoteMessagingPixelName
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class RealCardsListRemoteMessagePixelHelperTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private lateinit var pixelHelper: RealCardsListRemoteMessagePixelHelper
    private val pixel: Pixel = mock()
    private val remoteMessagingRepository: RemoteMessagingRepository = mock()

    private val testRemoteMessage = RemoteMessage(
        id = "test-message-123",
        content = Content.CardsList(
            titleText = "Test Title",
            descriptionText = "Test Description",
            placeholder = Content.Placeholder.DDG_ANNOUNCE,
            listItems = emptyList(),
            primaryActionText = "Action",
            primaryAction = Action.Dismiss,
        ),
        matchingRules = emptyList(),
        exclusionRules = emptyList(),
        surfaces = listOf(Surface.MODAL),
    )

    private val testCardItem = CardItem.ListItem(
        id = "card-item-456",
        titleText = "Card Title",
        descriptionText = "Card Description",
        primaryAction = Action.Dismiss,
        type = CardItemType.TWO_LINE_LIST_ITEM,
        placeholder = Content.Placeholder.DDG_ANNOUNCE,
        matchingRules = emptyList(),
        exclusionRules = emptyList(),
    )

    @Before
    fun setup() {
        pixelHelper = RealCardsListRemoteMessagePixelHelper(
            pixel = pixel,
            remoteMessagingRepository = remoteMessagingRepository,
        )
    }

    @Test
    fun whenFireCardItemShownPixelThenCorrectPixelFiredWithMessageIdAndCardId() {
        pixelHelper.fireCardItemShownPixel(testRemoteMessage, testCardItem)

        val expectedParams = mapOf(
            "message" to "test-message-123",
            "card" to "card-item-456",
        )

        verify(pixel).fire(
            pixel = eq(CardsListRemoteMessagePixelName.REMOTE_MESSAGE_CARD_SHOWN),
            parameters = eq(expectedParams),
            encodedParameters = any(),
            type = any(),
        )
    }

    @Test
    fun whenFireCardItemClickedPixelThenCorrectPixelFiredWithMessageIdAndCardId() {
        pixelHelper.fireCardItemClickedPixel(testRemoteMessage, testCardItem)

        val expectedParams = mapOf(
            "message" to "test-message-123",
            "card" to "card-item-456",
        )

        verify(pixel).fire(
            pixel = eq(CardsListRemoteMessagePixelName.REMOTE_MESSAGE_CARD_CLICKED),
            parameters = eq(expectedParams),
            encodedParameters = any(),
            type = any(),
        )
    }

    @Test
    fun whenDismissCardsListMessageWithNoCustomParamsThenPixelFiredAndMessageDismissed() = runTest {
        pixelHelper.dismissCardsListMessage(testRemoteMessage.id)

        val expectedParams = mapOf(
            "message" to "test-message-123",
        )

        verify(pixel).fire(
            pixel = eq(RemoteMessagingPixelName.REMOTE_MESSAGE_DISMISSED),
            parameters = eq(expectedParams),
            encodedParameters = any(),
            type = any(),
        )

        verify(remoteMessagingRepository).dismissMessage(eq("test-message-123"))
    }

    @Test
    fun whenDismissCardsListMessageWithCustomParamsThenPixelIncludesCustomParams() = runTest {
        val customParams = mapOf(
            PARAM_NAME_DISMISS_TYPE to PARAM_VALUE_CLOSE_BUTTON,
        )

        pixelHelper.dismissCardsListMessage(testRemoteMessage.id, customParams)

        val expectedParams = mapOf(
            "message" to "test-message-123",
            PARAM_NAME_DISMISS_TYPE to PARAM_VALUE_CLOSE_BUTTON,
        )

        verify(pixel).fire(
            pixel = eq(RemoteMessagingPixelName.REMOTE_MESSAGE_DISMISSED),
            parameters = eq(expectedParams),
            encodedParameters = any(),
            type = any(),
        )

        verify(remoteMessagingRepository).dismissMessage(eq("test-message-123"))
    }

    @Test
    fun whenDismissCardsListMessageWithMultipleCustomParamsThenAllParamsIncluded() = runTest {
        val customParams = mapOf(
            PARAM_NAME_DISMISS_TYPE to PARAM_VALUE_CLOSE_BUTTON,
            "extraParam1" to "value1",
            "extraParam2" to "value2",
        )

        pixelHelper.dismissCardsListMessage(testRemoteMessage.id, customParams)

        val expectedParams = mapOf(
            "message" to "test-message-123",
            PARAM_NAME_DISMISS_TYPE to PARAM_VALUE_CLOSE_BUTTON,
            "extraParam1" to "value1",
            "extraParam2" to "value2",
        )

        verify(pixel).fire(
            pixel = eq(RemoteMessagingPixelName.REMOTE_MESSAGE_DISMISSED),
            parameters = eq(expectedParams),
            encodedParameters = any(),
            type = any(),
        )

        verify(remoteMessagingRepository).dismissMessage(eq("test-message-123"))
    }
}
