/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.promotion

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.desktopapppromotion.api.DesktopAppPromotionInteractionHandler.Interaction
import com.duckduckgo.sync.impl.pixels.SyncPixelName
import com.duckduckgo.sync.impl.promotion.SyncGetOnOtherPlatformsLaunchSource.SOURCE_SYNC_ENABLED
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class SyncDesktopAppPromotionInteractionHandlerTest {

    private val pixelMock: Pixel = mock()
    private val testee = SyncDesktopAppPromotionInteractionHandler(SOURCE_SYNC_ENABLED, pixelMock)

    @Test
    fun whenImpressionThenImpressionPixelFiresWithSourceAndInteractionIsHandled() = runTest {
        val handled = testee.onInteraction(Interaction.IMPRESSION)

        verify(pixelMock).fire(SyncPixelName.SYNC_GET_OTHER_DEVICES_SCREEN_SHOWN, mapOf("source" to "activated"))
        assertTrue(handled)
    }

    @Test
    fun whenShareClickedThenSharePixelFiresWithSourceAndInteractionIsHandled() = runTest {
        val handled = testee.onInteraction(Interaction.SHARE_CLICKED)

        verify(pixelMock).fire(SyncPixelName.SYNC_GET_OTHER_DEVICES_LINK_SHARED, mapOf("source" to "activated"))
        assertTrue(handled)
    }

    @Test
    fun whenLinkCopiedThenLinkPixelFiresWithSourceAndInteractionIsHandled() = runTest {
        val handled = testee.onInteraction(Interaction.LINK_COPIED)

        verify(pixelMock).fire(SyncPixelName.SYNC_GET_OTHER_DEVICES_LINK_COPIED, mapOf("source" to "activated"))
        assertTrue(handled)
    }

    @Test
    fun whenShareCompletedOrDismissedThenNothingFiresAndNoDefaultIsNeeded() = runTest {
        assertTrue(testee.onInteraction(Interaction.SHARE_COMPLETED))
        assertTrue(testee.onInteraction(Interaction.DISMISSED))
    }

    @Test
    fun handlerIdEncodesTheSource() {
        assertEquals(
            "sync_get_other_devices_activated",
            SyncDesktopAppPromotionInteractionHandler(SOURCE_SYNC_ENABLED, pixelMock).handlerId,
        )
    }
}
