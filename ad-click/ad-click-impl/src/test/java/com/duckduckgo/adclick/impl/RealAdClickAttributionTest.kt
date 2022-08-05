/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.adclick.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.adclick.api.AdClickFeatureName
import com.duckduckgo.adclick.store.AdClickAttributionAllowlistEntity
import com.duckduckgo.adclick.store.AdClickAttributionDetectionEntity
import com.duckduckgo.adclick.store.AdClickAttributionLinkFormatEntity
import com.duckduckgo.adclick.store.AdClickAttributionRepository
import com.duckduckgo.feature.toggles.api.FeatureToggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealAdClickAttributionTest {

    private val mockAdClickAttributionRepository: AdClickAttributionRepository = mock()
    private val mockFeatureToggle: FeatureToggle = mock()
    private lateinit var testee: RealAdClickAttribution

    @Test
    fun whenFeatureIsNotEnabledThenIsAllowedReturnsFalse() {
        givenFeatureNotEnabled()
        testee = RealAdClickAttribution(
            mockAdClickAttributionRepository,
            mockFeatureToggle
        )

        val result = testee.isAllowed("https://example.com")

        assertFalse(result)
    }

    @Test
    fun whenFeatureEnabledAndBothDetectionsDisabledThenIsAllowedReturnsFalse() {
        givenFeatureNotEnabled()
        givenDetectionsEnabled(domainEnabled = false, heuristicEnabled = false)
        testee = RealAdClickAttribution(
            mockAdClickAttributionRepository,
            mockFeatureToggle
        )

        val result = testee.isAllowed("https://example.com")

        assertFalse(result)
    }

    @Test
    fun whenFeatureEnabledAndNullHostThenIsAllowedReturnsFalse() {
        givenFeatureEnabled()
        givenDetectionsEnabled(domainEnabled = true, heuristicEnabled = true)
        testee = RealAdClickAttribution(
            mockAdClickAttributionRepository,
            mockFeatureToggle
        )

        val result = testee.isAllowed("/example")

        assertFalse(result)
    }

    @Test
    fun whenFeatureEnabledAndHostNotInAllowlistHostThenIsAllowedReturnsFalse() {
        givenFeatureEnabled()
        givenDetectionsEnabled(domainEnabled = true, heuristicEnabled = true)
        whenever(mockAdClickAttributionRepository.allowList).thenReturn(
            listOf(
                AdClickAttributionAllowlistEntity(
                    blocklistEntry = "host.com",
                    host = "some.host.com"
                )
            )
        )
        testee = RealAdClickAttribution(
            mockAdClickAttributionRepository,
            mockFeatureToggle
        )

        val result = testee.isAllowed("https://example.com")

        assertFalse(result)
    }

    @Test
    fun whenFeatureEnabledAndHostInAllowlistHostThenIsAllowedReturnsTrue() {
        givenFeatureEnabled()
        givenDetectionsEnabled(domainEnabled = true, heuristicEnabled = true)
        whenever(mockAdClickAttributionRepository.allowList).thenReturn(
            listOf(
                AdClickAttributionAllowlistEntity(
                    blocklistEntry = "example.com",
                    host = "other.example.com"
                )
            )
        )
        testee = RealAdClickAttribution(
            mockAdClickAttributionRepository,
            mockFeatureToggle
        )

        val result = testee.isAllowed("https://some.other.example.com")

        assertTrue(result)
    }

    @Test
    fun whenFeatureIsNotEnabledThenIsAdClickReturnsFalse() {
        givenFeatureNotEnabled()
        testee = RealAdClickAttribution(
            mockAdClickAttributionRepository,
            mockFeatureToggle
        )

        val result = testee.isAdClick("https://example.com")

        assertFalse(result.first)
    }

    @Test
    fun whenFeatureEnabledAndBothDetectionsDisabledThenIsAdClickReturnsFalse() {
        givenFeatureNotEnabled()
        givenDetectionsEnabled(domainEnabled = false, heuristicEnabled = false)
        testee = RealAdClickAttribution(
            mockAdClickAttributionRepository,
            mockFeatureToggle
        )

        val result = testee.isAdClick("https://example.com")

        assertFalse(result.first)
    }

    @Test
    fun whenFeatureEnabledAndUrlDoesntMatchLinkFormatThenIsAdClickReturnsFalse() {
        givenFeatureEnabled()
        givenDetectionsEnabled(domainEnabled = true, heuristicEnabled = true)
        whenever(mockAdClickAttributionRepository.linkFormats).thenReturn(
            listOf(
                AdClickAttributionLinkFormatEntity(
                    url = "https://example.com",
                    parameterName = "",
                    parameterValue = "",
                    adDomainParameterName = ""
                )
            )
        )
        testee = RealAdClickAttribution(
            mockAdClickAttributionRepository,
            mockFeatureToggle
        )

        val result = testee.isAdClick("https://other-example.com")

        assertFalse(result.first)
    }

    @Test
    fun whenFeatureEnabledAndUrlMatchesLinkFormatThenIsAdClickReturnsTrue() {
        givenFeatureEnabled()
        givenDetectionsEnabled(domainEnabled = true, heuristicEnabled = true)
        whenever(mockAdClickAttributionRepository.linkFormats).thenReturn(
            listOf(
                AdClickAttributionLinkFormatEntity(
                    url = "https://example.com",
                    parameterName = "",
                    parameterValue = "",
                    adDomainParameterName = ""
                )
            )
        )
        testee = RealAdClickAttribution(
            mockAdClickAttributionRepository,
            mockFeatureToggle
        )

        val result = testee.isAdClick("https://example.com")

        assertTrue(result.first)
    }

    @Test
    fun whenFeatureEnabledAndUrlMatchesLinkFormatAndAdDomainParameterNameThenIsAdClickReturnsTrueAndAdDomain() {
        givenFeatureEnabled()
        givenDetectionsEnabled(domainEnabled = true, heuristicEnabled = true)
        whenever(mockAdClickAttributionRepository.linkFormats).thenReturn(
            listOf(
                AdClickAttributionLinkFormatEntity(
                    url = "https://example.com",
                    parameterName = "",
                    parameterValue = "",
                    adDomainParameterName = "ad_domain"
                )
            )
        )
        testee = RealAdClickAttribution(
            mockAdClickAttributionRepository,
            mockFeatureToggle
        )

        val result = testee.isAdClick("https://example.com?ad_domain=nike.com")

        assertTrue(result.first)
        assertEquals("nike.com", result.second)
    }

    @Test
    fun whenFeatureEnabledAndUrlMatchesLinkFormatAndParamNameThenIsAdClickReturnsTrue() {
        givenFeatureEnabled()
        givenDetectionsEnabled(domainEnabled = true, heuristicEnabled = true)
        whenever(mockAdClickAttributionRepository.linkFormats).thenReturn(
            listOf(
                AdClickAttributionLinkFormatEntity(
                    url = "https://example.com",
                    parameterName = "a",
                    parameterValue = "",
                    adDomainParameterName = ""
                )
            )
        )
        testee = RealAdClickAttribution(
            mockAdClickAttributionRepository,
            mockFeatureToggle
        )

        val result = testee.isAdClick("https://example.com?a=100")

        assertTrue(result.first)
    }

    @Test
    fun whenFeatureEnabledAndUrlMatchesLinkFormatAndParamNameAndParamValueThenIsAdClickReturnsTrue() {
        givenFeatureEnabled()
        givenDetectionsEnabled(domainEnabled = true, heuristicEnabled = true)
        whenever(mockAdClickAttributionRepository.linkFormats).thenReturn(
            listOf(
                AdClickAttributionLinkFormatEntity(
                    url = "https://example.com",
                    parameterName = "a",
                    parameterValue = "100",
                    adDomainParameterName = ""
                )
            )
        )
        testee = RealAdClickAttribution(
            mockAdClickAttributionRepository,
            mockFeatureToggle
        )

        val result = testee.isAdClick("https://example.com?a=100")

        assertTrue(result.first)
    }

    @Test
    fun whenFeatureEnabledAndUrlMatchesLinkFormatAndDoesntMatchParamNameThenIsAdClickReturnsFalse() {
        givenFeatureEnabled()
        givenDetectionsEnabled(domainEnabled = true, heuristicEnabled = true)
        whenever(mockAdClickAttributionRepository.linkFormats).thenReturn(
            listOf(
                AdClickAttributionLinkFormatEntity(
                    url = "https://example.com",
                    parameterName = "b",
                    parameterValue = "100",
                    adDomainParameterName = ""
                )
            )
        )
        testee = RealAdClickAttribution(
            mockAdClickAttributionRepository,
            mockFeatureToggle
        )

        val result = testee.isAdClick("https://example.com?a=100")

        assertFalse(result.first)
    }

    @Test
    fun whenFeatureEnabledAndUrlMatchesLinkFormatAndParamNameAndDoesntMatchParmaValueThenIsAdClickReturnsFalse() {
        givenFeatureEnabled()
        givenDetectionsEnabled(domainEnabled = true, heuristicEnabled = true)
        whenever(mockAdClickAttributionRepository.linkFormats).thenReturn(
            listOf(
                AdClickAttributionLinkFormatEntity(
                    url = "https://example.com",
                    parameterName = "a",
                    parameterValue = "0",
                    adDomainParameterName = ""
                )
            )
        )
        testee = RealAdClickAttribution(
            mockAdClickAttributionRepository,
            mockFeatureToggle
        )

        val result = testee.isAdClick("https://example.com?a=100")

        assertFalse(result.first)
    }

    @Test
    fun whenFeatureEnabledAndOnlyDomainDetectionEnabledAndUrlMatchesLinkFormatThenAdClickReturnsTrue() {
        givenFeatureEnabled()
        givenDetectionsEnabled(domainEnabled = true, heuristicEnabled = false)
        whenever(mockAdClickAttributionRepository.linkFormats).thenReturn(
            listOf(
                AdClickAttributionLinkFormatEntity(
                    url = "https://example.com",
                    parameterName = "",
                    parameterValue = "",
                    adDomainParameterName = ""
                )
            )
        )
        testee = RealAdClickAttribution(
            mockAdClickAttributionRepository,
            mockFeatureToggle
        )

        val result = testee.isAdClick("https://example.com")

        assertTrue(result.first)
    }

    @Test
    fun whenFeatureEnabledAndOnlyHeuristicDetectionEnabledAndUrlMatchesLinkFormatThenAdClickReturnsTrue() {
        givenFeatureEnabled()
        givenDetectionsEnabled(domainEnabled = false, heuristicEnabled = true)
        whenever(mockAdClickAttributionRepository.linkFormats).thenReturn(
            listOf(
                AdClickAttributionLinkFormatEntity(
                    url = "https://example.com",
                    parameterName = "",
                    parameterValue = "",
                    adDomainParameterName = ""
                )
            )
        )
        testee = RealAdClickAttribution(
            mockAdClickAttributionRepository,
            mockFeatureToggle
        )

        val result = testee.isAdClick("https://example.com")

        assertTrue(result.first)
    }

    private fun givenFeatureNotEnabled() {
        whenever(mockFeatureToggle.isFeatureEnabled(AdClickFeatureName.AdClickAttributionFeatureName.value, true)).thenReturn(false)
    }

    private fun givenFeatureEnabled() {
        whenever(mockFeatureToggle.isFeatureEnabled(AdClickFeatureName.AdClickAttributionFeatureName.value, true)).thenReturn(true)
    }

    private fun givenDetectionsEnabled(domainEnabled: Boolean, heuristicEnabled: Boolean) {
        whenever(mockAdClickAttributionRepository.detections).thenReturn(
            listOf(
                AdClickAttributionDetectionEntity(
                    id = 1,
                    heuristicDetection = if (heuristicEnabled) "enabled" else "disabled",
                    domainDetection = if (domainEnabled) "enabled" else "disabled"
                )
            )
        )
    }
}
