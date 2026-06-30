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

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.promotion.FakeSyncPromotionDataStore
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType
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

    // DeviceSyncState properties
    private var isSyncEnabled = false
    private var isChatSyncEnabled = false
    private var isUserSignedIn = false

    // DuckChat properties
    private var isChatHistoryEnabled = false
    private val hasChatSuggestions = MutableStateFlow(false)

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
    fun `when duck chat has no history is disabled cannot show promotion`() = runTest {
        configurePromotionToShow()

        hasChatSuggestions.value = false
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

    private suspend fun configurePromotionToShow() {
        dataStore.clearPromoHistory(PromotionType.ChatTabPage)
        isSyncEnabled = true
        isChatSyncEnabled = true
        isUserSignedIn = false
        isChatHistoryEnabled = true
        hasChatSuggestions.value = true
    }
}
