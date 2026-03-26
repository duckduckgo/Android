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

package com.duckduckgo.app.attributed.metrics.impl

import com.duckduckgo.app.attributed.metrics.AttributedMetricsConfigFeature
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.moshi.Moshi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OriginParamManagerTest {

    private val mockAttributedMetricsConfigFeature: AttributedMetricsConfigFeature = mock()
    private val mockSendOriginParamToggle: Toggle = mock()
    private val moshi = Moshi.Builder().build()

    private lateinit var testee: RealOriginParamManager

    @Before
    fun setup() {
        whenever(mockAttributedMetricsConfigFeature.sendOriginParam()).thenReturn(mockSendOriginParamToggle)
    }

    @Test
    fun whenToggleDisabledThenReturnsFalse() {
        whenever(mockSendOriginParamToggle.isEnabled()).thenReturn(false)
        testee = RealOriginParamManager(mockAttributedMetricsConfigFeature, moshi)

        val result = testee.shouldSendOrigin("campaign_paid_test")

        assertFalse(result)
    }

    @Test
    fun whenOriginIsNullThenReturnsFalse() {
        whenever(mockSendOriginParamToggle.isEnabled()).thenReturn(true)
        whenever(mockSendOriginParamToggle.getSettings()).thenReturn("""{"originCampaignSubstrings":["paid"]}""")
        testee = RealOriginParamManager(mockAttributedMetricsConfigFeature, moshi)

        val result = testee.shouldSendOrigin(null)

        assertFalse(result)
    }

    @Test
    fun whenOriginIsBlankThenReturnsFalse() {
        whenever(mockSendOriginParamToggle.isEnabled()).thenReturn(true)
        whenever(mockSendOriginParamToggle.getSettings()).thenReturn("""{"originCampaignSubstrings":["paid"]}""")
        testee = RealOriginParamManager(mockAttributedMetricsConfigFeature, moshi)

        val result = testee.shouldSendOrigin("   ")

        assertFalse(result)
    }

    @Test
    fun whenSubstringListIsEmptyThenReturnsFalse() {
        whenever(mockSendOriginParamToggle.isEnabled()).thenReturn(true)
        whenever(mockSendOriginParamToggle.getSettings()).thenReturn("""{"originCampaignSubstrings":[]}""")
        testee = RealOriginParamManager(mockAttributedMetricsConfigFeature, moshi)

        val result = testee.shouldSendOrigin("campaign_paid_test")

        assertFalse(result)
    }

    @Test
    fun whenOriginMatchesSubstringThenReturnsTrue() {
        whenever(mockSendOriginParamToggle.isEnabled()).thenReturn(true)
        whenever(mockSendOriginParamToggle.getSettings()).thenReturn("""{"originCampaignSubstrings":["paid"]}""")
        testee = RealOriginParamManager(mockAttributedMetricsConfigFeature, moshi)

        val result = testee.shouldSendOrigin("campaign_paid_test")

        assertTrue(result)
    }

    @Test
    fun whenOriginDoesNotMatchSubstringThenReturnsFalse() {
        whenever(mockSendOriginParamToggle.isEnabled()).thenReturn(true)
        whenever(mockSendOriginParamToggle.getSettings()).thenReturn("""{"originCampaignSubstrings":["paid"]}""")
        testee = RealOriginParamManager(mockAttributedMetricsConfigFeature, moshi)

        val result = testee.shouldSendOrigin("campaign_organic_test")

        assertFalse(result)
    }

    @Test
    fun whenOriginMatchesAnyOfMultipleSubstringsThenReturnsTrue() {
        whenever(mockSendOriginParamToggle.isEnabled()).thenReturn(true)
        whenever(mockSendOriginParamToggle.getSettings()).thenReturn("""{"originCampaignSubstrings":["paid","sponsored","affiliate"]}""")
        testee = RealOriginParamManager(mockAttributedMetricsConfigFeature, moshi)

        val result = testee.shouldSendOrigin("campaign_sponsored_search")

        assertTrue(result)
    }

    @Test
    fun whenMatchingIsCaseInsensitiveThenReturnsTrue() {
        whenever(mockSendOriginParamToggle.isEnabled()).thenReturn(true)
        whenever(mockSendOriginParamToggle.getSettings()).thenReturn("""{"originCampaignSubstrings":["paid"]}""")
        testee = RealOriginParamManager(mockAttributedMetricsConfigFeature, moshi)

        val resultUpperCase = testee.shouldSendOrigin("campaign_PAID_test")
        val resultMixedCase = testee.shouldSendOrigin("campaign_PaId_test")

        assertTrue(resultUpperCase)
        assertTrue(resultMixedCase)
    }

    @Test
    fun whenSettingsParsingFailsThenReturnsFalse() {
        whenever(mockSendOriginParamToggle.isEnabled()).thenReturn(true)
        whenever(mockSendOriginParamToggle.getSettings()).thenReturn("invalid json")
        testee = RealOriginParamManager(mockAttributedMetricsConfigFeature, moshi)

        val result = testee.shouldSendOrigin("campaign_paid_test")

        assertFalse(result)
    }

    @Test
    fun whenSettingsIsNullThenReturnsFalse() {
        whenever(mockSendOriginParamToggle.isEnabled()).thenReturn(true)
        whenever(mockSendOriginParamToggle.getSettings()).thenReturn(null)
        testee = RealOriginParamManager(mockAttributedMetricsConfigFeature, moshi)

        val result = testee.shouldSendOrigin("campaign_paid_test")

        assertFalse(result)
    }

    @Test
    fun whenOriginContainsSubstringWithinWordThenReturnsTrue() {
        whenever(mockSendOriginParamToggle.isEnabled()).thenReturn(true)
        whenever(mockSendOriginParamToggle.getSettings()).thenReturn("""{"originCampaignSubstrings":["paid"]}""")
        testee = RealOriginParamManager(mockAttributedMetricsConfigFeature, moshi)

        val resultHipaid = testee.shouldSendOrigin("funnel_hipaid_us")
        val resultPaidctv = testee.shouldSendOrigin("funnel_paidctv_us")
        val resultPaid = testee.shouldSendOrigin("funnel_paid_us")

        assertTrue(resultHipaid)
        assertTrue(resultPaidctv)
        assertTrue(resultPaid)
    }
}
