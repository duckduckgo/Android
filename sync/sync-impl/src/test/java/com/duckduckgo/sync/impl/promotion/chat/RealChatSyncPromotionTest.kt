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

package com.duckduckgo.sync.impl.promotion.chat

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.promotion.FakeSyncPromotionDataStore
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType
import com.duckduckgo.sync.impl.promotion.chat.FakePixel.Measurement
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class RealChatSyncPromotionTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val dataStore = FakeSyncPromotionDataStore()
    private val pixel = FakePixel()

    // DeviceSyncState properties
    private var isSyncEnabled = false
    private var isChatSyncEnabled = false
    private var isUserSignedIn = false

    // DuckChat properties
    private var isChatHistoryEnabled = false
    private val hasChatSuggestions = MutableStateFlow(false)

    // BrowserModeStateHolder properties
    private val browserMode = MutableStateFlow(BrowserMode.REGULAR)

    private val testee = RealChatSyncPromotion(
        promotionDataStore = dataStore,
        syncState = mock {
            on { isFeatureEnabled() } doAnswer { isSyncEnabled }
            on { isDuckChatSyncFeatureEnabled() } doAnswer { isChatSyncEnabled }
            on { isUserSignedInOnDevice() } doAnswer { isUserSignedIn }
        },
        duckChat = mock {
            onBlocking { hasUserEnabledChatHistory() } doAnswer { isChatHistoryEnabled }
            on { observeHasChatSuggestions() } doReturn hasChatSuggestions
        },
        browserModeStateHolder = mock {
            on { currentMode } doReturn browserMode
        },
        pixel = pixel,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun `when all preconditions are met can show promotion`() = runTest {
        configurePromotionToShow()

        assertTrue(testee.canShowPromotion())
    }

    @Test
    fun `when promotion is accepted cannot show promotion`() = runTest {
        configurePromotionToShow()

        testee.recordPromotionAccepted()
        assertFalse(testee.canShowPromotion())
    }

    @Test
    fun `when promotion is dismissed cannot show promotion`() = runTest {
        configurePromotionToShow()

        testee.recordPromotionDismissed()
        assertFalse(testee.canShowPromotion())
    }

    @Test
    fun `when promotion impression cap is reached cannot show promotion`() = runTest {
        configurePromotionToShow()

        repeat(RealChatSyncPromotion.MAX_IMPRESSION_COUNT - 1) {
            testee.incrementImpressionCount()
        }
        assertTrue(testee.canShowPromotion())

        testee.incrementImpressionCount()
        assertFalse(testee.canShowPromotion())
    }

    @Test
    fun `when sync is disabled cannot show promotion`() = runTest {
        configurePromotionToShow()

        isSyncEnabled = false
        assertFalse(testee.canShowPromotion())
    }

    @Test
    fun `when duck chat sync is disabled cannot show promotion`() = runTest {
        configurePromotionToShow()

        isChatSyncEnabled = false
        assertFalse(testee.canShowPromotion())
    }

    @Test
    fun `when user is signed in is disabled cannot show promotion`() = runTest {
        configurePromotionToShow()

        isUserSignedIn = true
        assertFalse(testee.canShowPromotion())
    }

    @Test
    fun `when duck chat history is disabled cannot show promotion`() = runTest {
        configurePromotionToShow()

        isChatHistoryEnabled = false
        assertFalse(testee.canShowPromotion())
    }

    @Test
    fun `when duck chat has no suggestions cannot show promotion`() = runTest {
        configurePromotionToShow()

        hasChatSuggestions.value = false
        assertFalse(testee.canShowPromotion())
    }

    @Test
    fun `when browser is in Fire Mode cannot show promotion`() = runTest {
        configurePromotionToShow()

        browserMode.value = BrowserMode.FIRE
        assertFalse(testee.canShowPromotion())
    }

    @Test
    fun `when incrementImpressionCount then record promo impression`() = runTest {
        testee.incrementImpressionCount()
        assertEquals(1L, dataStore.getPromoImpressionCount(PromotionType.ChatTabPage))

        testee.incrementImpressionCount()
        assertEquals(2L, dataStore.getPromoImpressionCount(PromotionType.ChatTabPage))
    }

    @Test
    fun `when recordPromotionAccepted then record promo dismissed`() = runTest {
        testee.recordPromotionAccepted()
        assertTrue(dataStore.hasPromoBeenDismissed(PromotionType.ChatTabPage))
    }

    @Test
    fun `when recordPromotionDismissed then record promo dismissed`() = runTest {
        testee.recordPromotionDismissed()
        assertTrue(dataStore.hasPromoBeenDismissed(PromotionType.ChatTabPage))
    }

    @Test
    fun `when promotion impression count is increased then fire displayed pixel`() = runTest {
        testee.incrementImpressionCount()

        assertEquals(
            listOf(
                Measurement("sync_promotion_displayed", mapOf("source" to "ai_chat")),
            ),
            pixel.firedPixels,
        )
    }

    @Test
    fun `when promotion is accepted then fire confirmed pixel`() = runTest {
        testee.recordPromotionAccepted()

        assertEquals(
            listOf(
                Measurement("sync_promotion_confirmed", mapOf("source" to "ai_chat")),
            ),
            pixel.firedPixels,
        )
    }

    @Test
    fun `when promotion is dismissed then fire user_tapped dismissed pixel`() = runTest {
        testee.recordPromotionDismissed()

        assertEquals(
            listOf(
                Measurement(
                    name = "sync_promotion_dismissed",
                    params = mapOf("source" to "ai_chat", "reason" to "user_tapped"),
                ),
            ),
            pixel.firedPixels,
        )
    }

    @Test
    fun `when can't show promotion due to cap limit then fire impression_cap dismissed pixel`() = runTest {
        configurePromotionToShow()
        repeat(RealChatSyncPromotion.MAX_IMPRESSION_COUNT) {
            testee.incrementImpressionCount()
        }
        pixel.clear()

        testee.canShowPromotion()

        assertEquals(
            listOf(
                Measurement(
                    name = "sync_promotion_dismissed",
                    params = mapOf("source" to "ai_chat", "reason" to "impression_cap"),
                    type = Unique("sync_promotion_dismissed_ai_chat_impression_cap_3"),
                ),
            ),
            pixel.firedPixels,
        )
    }

    @Test
    fun `when can't show promotion due to it being dismissed then fire no pixels`() = runTest {
        configurePromotionToShow()
        testee.recordPromotionDismissed()
        pixel.clear()

        testee.canShowPromotion()

        assertTrue(pixel.firedPixels.isEmpty())
    }

    private suspend fun configurePromotionToShow() {
        dataStore.clearPromoHistory(PromotionType.ChatTabPage)
        isSyncEnabled = true
        isChatSyncEnabled = true
        isUserSignedIn = false
        isChatHistoryEnabled = true
        hasChatSuggestions.value = true
        browserMode.value = BrowserMode.REGULAR
    }
}

private class FakePixel : Pixel {
    private val _firedPixels = mutableListOf<Measurement>()
    val firedPixels get() = _firedPixels.toList()

    fun clear() = _firedPixels.clear()

    override fun fire(
        pixelName: String,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
        type: PixelType,
    ) {
        _firedPixels.add(Measurement(pixelName, parameters, encodedParameters, type))
    }

    override fun fire(
        pixel: PixelName,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
        type: PixelType,
    ) = fire(pixel.pixelName, parameters, encodedParameters, type)

    override fun enqueueFire(
        pixelName: String,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
        type: PixelType,
    ) = fire(pixelName, parameters, encodedParameters, type)

    override fun enqueueFire(
        pixel: PixelName,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
        type: PixelType,
    ) = fire(pixel, parameters, encodedParameters, type)

    data class Measurement(
        val name: String,
        val params: Map<String, String> = emptyMap(),
        val encodedParams: Map<String, String> = emptyMap(),
        val type: PixelType = PixelType.Count,
    )
}
