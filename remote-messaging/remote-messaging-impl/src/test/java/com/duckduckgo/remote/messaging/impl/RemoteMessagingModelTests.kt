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

package com.duckduckgo.remote.messaging.impl

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.Content.Placeholder.VISUAL_DESIGN_UPDATE
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.duckduckgo.remote.messaging.impl.pixels.RemoteMessagingPixels
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class RemoteMessagingModelTests {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val remoteMessagingRepository: RemoteMessagingRepository = mock()
    private val remoteMessagingPixels: RemoteMessagingPixels = mock()

    private lateinit var testee: RealRemoteMessageModel

    val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList())

    @Before
    fun setup() {
        testee = RealRemoteMessageModel(remoteMessagingRepository, remoteMessagingPixels, coroutineRule.testDispatcherProvider)
    }

    @Test
    fun onMessageShownThenPixelIsFired() = runTest {
        testee.onMessageShown(remoteMessage)

        verify(remoteMessagingPixels).fireRemoteMessageShownPixel(remoteMessage)
    }

    @Test
    fun onMessageDismissedThenPixelIsFiredAndMessageDismissed() = runTest {
        testee.onMessageDismissed(remoteMessage)

        verify(remoteMessagingPixels).fireRemoteMessageDismissedPixel(remoteMessage)
        verify(remoteMessagingRepository).dismissMessage(remoteMessage.id)
    }

    @Test
    fun onPrimaryActionClickedThenPixelFiredAndMessageDismissed() = runTest {
        val action = testee.onPrimaryActionClicked(remoteMessage)

        verify(remoteMessagingPixels).fireRemoteMessagePrimaryActionClickedPixel(remoteMessage)
        verify(remoteMessagingRepository).dismissMessage(remoteMessage.id)
        assertEquals(action, null)
    }

    @Test
    fun onSecondaryActionClickedThenPixelFiredAndMessageDismissed() = runTest {
        val action = testee.onSecondaryActionClicked(remoteMessage)

        verify(remoteMessagingPixels).fireRemoteMessageSecondaryActionClickedPixel(remoteMessage)
        verify(remoteMessagingRepository).dismissMessage(remoteMessage.id)
        assertEquals(action, null)
    }

    @Test
    fun onActionClickedThenPixelFiredAndMessageDismissed() = runTest {
        val action = testee.onActionClicked(remoteMessage)

        verify(remoteMessagingPixels).fireRemoteMessageActionClickedPixel(remoteMessage)
        verify(remoteMessagingRepository).dismissMessage(remoteMessage.id)
        assertEquals(action, null)
    }

    @Test
    fun onActionClickedThenPixelFiredAndMessageNotDismissedIfActionShare() = runTest {
        val action = Action.Share(
            value = "",
            additionalParameters = null,
        )
        val remoteMessage = RemoteMessage(
            id = "id1",
            Content.PromoSingleAction(
                titleText = "",
                descriptionText = "",
                placeholder = VISUAL_DESIGN_UPDATE,
                actionText = "",
                action = action,
            ),
            matchingRules = emptyList(),
            exclusionRules = emptyList(),
        )
        val result = testee.onActionClicked(remoteMessage)

        verify(remoteMessagingPixels).fireRemoteMessageActionClickedPixel(remoteMessage)
        verify(remoteMessagingRepository, never()).dismissMessage(remoteMessage.id)
        assertEquals(action, result)
    }
}
