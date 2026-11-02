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

package com.duckduckgo.duckchat.impl.rmf

import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.promo.PromoCampaignRepository
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class IsDuckAiBoostActiveAttributeMatcherPluginTest {
    private val duckChatFeature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java)
    private val mockPromoCampaignRepository: PromoCampaignRepository = mock()
    private val testee = IsDuckAiBoostActiveAttributeMatcherPlugin(duckChatFeature, mockPromoCampaignRepository)

    @Test
    fun `when promo campaigns enabled and promo active and remote value true, then returns true`() = runTest {
        givenBoostState(promoCampaignsEnabled = true, promoActive = true)

        val result = testee.evaluate(IsDuckAiBoostActiveMatchingAttribute(remoteValue = true))

        assertEquals(true, result)
    }

    @Test
    fun `when promo campaigns enabled and promo inactive and remote value true, then returns false`() = runTest {
        givenBoostState(promoCampaignsEnabled = true, promoActive = false)

        val result = testee.evaluate(IsDuckAiBoostActiveMatchingAttribute(remoteValue = true))

        assertEquals(false, result)
    }

    @Test
    fun `when promo campaigns disabled and promo active and remote value true, then returns false`() = runTest {
        givenBoostState(promoCampaignsEnabled = false, promoActive = true)

        val result = testee.evaluate(IsDuckAiBoostActiveMatchingAttribute(remoteValue = true))

        assertEquals(false, result)
    }

    @Test
    fun `when promo campaigns disabled and promo inactive and remote value true, then returns false`() = runTest {
        givenBoostState(promoCampaignsEnabled = false, promoActive = false)

        val result = testee.evaluate(IsDuckAiBoostActiveMatchingAttribute(remoteValue = true))

        assertEquals(false, result)
    }

    @Test
    fun `when promo campaigns enabled and promo active and remote value false, then returns false`() = runTest {
        givenBoostState(promoCampaignsEnabled = true, promoActive = true)

        val result = testee.evaluate(IsDuckAiBoostActiveMatchingAttribute(remoteValue = false))

        assertEquals(false, result)
    }

    @Test
    fun `when promo campaigns enabled and promo inactive and remote value false, then returns true`() = runTest {
        givenBoostState(promoCampaignsEnabled = true, promoActive = false)

        val result = testee.evaluate(IsDuckAiBoostActiveMatchingAttribute(remoteValue = false))

        assertEquals(true, result)
    }

    @Test
    fun `when promo campaigns disabled and promo active and remote value false, then returns true`() = runTest {
        givenBoostState(promoCampaignsEnabled = false, promoActive = true)

        val result = testee.evaluate(IsDuckAiBoostActiveMatchingAttribute(remoteValue = false))

        assertEquals(true, result)
    }

    @Test
    fun `when promo campaigns disabled and promo inactive and remote value false, then returns true`() = runTest {
        givenBoostState(promoCampaignsEnabled = false, promoActive = false)

        val result = testee.evaluate(IsDuckAiBoostActiveMatchingAttribute(remoteValue = false))

        assertEquals(true, result)
    }

    @Test
    fun `when promo campaigns disabled, then does not query promo record`() = runTest {
        duckChatFeature.duckAiPromoCampaigns().setRawStoredState(Toggle.State(enable = false))

        testee.evaluate(IsDuckAiBoostActiveMatchingAttribute(remoteValue = true))

        verifyNoInteractions(mockPromoCampaignRepository)
    }

    @Test
    fun `when promo state changes between evaluations, then result reflects current state`() = runTest {
        duckChatFeature.duckAiPromoCampaigns().setRawStoredState(Toggle.State(enable = true))
        whenever(mockPromoCampaignRepository.isPromoActive()).thenReturn(true, false)
        val attribute = IsDuckAiBoostActiveMatchingAttribute(remoteValue = true)

        assertEquals(true, testee.evaluate(attribute))
        assertEquals(false, testee.evaluate(attribute))
    }

    @Test
    fun `when promo campaigns flag changes between evaluations, then result reflects current state`() = runTest {
        givenBoostState(promoCampaignsEnabled = true, promoActive = true)
        val attribute = IsDuckAiBoostActiveMatchingAttribute(remoteValue = true)

        assertEquals(true, testee.evaluate(attribute))

        duckChatFeature.duckAiPromoCampaigns().setRawStoredState(Toggle.State(enable = false))

        assertEquals(false, testee.evaluate(attribute))
    }

    @Test
    fun `when attribute is different, then returns null without querying promo record`() = runTest {
        val result = testee.evaluate(IsDuckAiEnabledMatchingAttribute(remoteValue = true))

        assertNull(result)
        verifyNoInteractions(mockPromoCampaignRepository)
    }

    private suspend fun givenBoostState(promoCampaignsEnabled: Boolean, promoActive: Boolean) {
        duckChatFeature.duckAiPromoCampaigns().setRawStoredState(Toggle.State(enable = promoCampaignsEnabled))
        whenever(mockPromoCampaignRepository.isPromoActive()).thenReturn(promoActive)
    }
}
