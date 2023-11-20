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

import com.duckduckgo.adclick.api.AdClickFeatureName
import com.duckduckgo.adclick.store.AdClickAttributionAllowlistEntity
import com.duckduckgo.adclick.store.AdClickAttributionDetectionEntity
import com.duckduckgo.adclick.store.AdClickAttributionExpirationEntity
import com.duckduckgo.adclick.store.AdClickAttributionLinkFormatEntity
import com.duckduckgo.adclick.store.AdClickAttributionRepository
import com.duckduckgo.adclick.store.AdClickFeatureToggleRepository
import com.duckduckgo.adclick.store.AdClickFeatureToggles
import com.duckduckgo.common.test.FileUtilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class AdClickAttributionPluginTest {

    lateinit var testee: AdClickAttributionPlugin

    private val mockFeatureTogglesRepository: AdClickFeatureToggleRepository = mock()
    private val mockAdClickAttributionRepository: AdClickAttributionRepository = mock()

    @Before
    fun before() {
        testee = AdClickAttributionPlugin(
            mockAdClickAttributionRepository,
            mockFeatureTogglesRepository,
        )
    }

    @Test
    fun whenFeatureNameDoesNotMatchAdClickAttributionThenReturnFalse() {
        AdClickFeatureName.values().filter { it != FEATURE_NAME }.forEach {
            assertFalse(testee.store(it.value, EMPTY_JSON_STRING))
        }
    }

    @Test
    fun whenFeatureNameMatchesAdClickAttributionThenReturnTrue() {
        assertTrue(testee.store(FEATURE_NAME_VALUE, EMPTY_JSON_STRING))
    }

    @Test
    fun whenFeatureNameMatchesAdClickAttributionAndIsEnabledThenStoreFeatureEnabled() {
        val jsonString = FileUtilities.loadText(javaClass.classLoader!!, "json/ad_click_attribution.json")

        testee.store(FEATURE_NAME_VALUE, jsonString)

        verify(mockFeatureTogglesRepository).insert(AdClickFeatureToggles(FEATURE_NAME, true, null))
    }

    @Test
    fun whenFeatureNameMatchesAdClickAttributionAndIsNotEnabledThenStoreFeatureDisabled() {
        val jsonString = FileUtilities.loadText(javaClass.classLoader!!, "json/ad_click_attribution_disabled.json")

        testee.store(FEATURE_NAME_VALUE, jsonString)

        verify(mockFeatureTogglesRepository).insert(AdClickFeatureToggles(FEATURE_NAME, false, null))
    }

    @Test
    fun whenFeatureNameMatchesAdClickAttributionThenUpdateAllExistingLists() {
        val jsonString = FileUtilities.loadText(javaClass.classLoader!!, "json/ad_click_attribution.json")

        testee.store(FEATURE_NAME_VALUE, jsonString)

        val linkFormatCaptor = argumentCaptor<List<AdClickAttributionLinkFormatEntity>>()
        val allowlistCaptor = argumentCaptor<List<AdClickAttributionAllowlistEntity>>()
        val expirationCaptor = argumentCaptor<List<AdClickAttributionExpirationEntity>>()
        val detectionsCaptor = argumentCaptor<List<AdClickAttributionDetectionEntity>>()

        verify(mockAdClickAttributionRepository).updateAll(
            linkFormats = linkFormatCaptor.capture(),
            allowList = allowlistCaptor.capture(),
            expirations = expirationCaptor.capture(),
            detections = detectionsCaptor.capture(),
        )

        val linkFormatEntity = linkFormatCaptor.firstValue
        assertEquals(6, linkFormatEntity.size)
        assertEquals("duckduckgo.com/y.js", linkFormatEntity[0].url)
        assertEquals("ad_domain", linkFormatEntity[0].adDomainParameterName)
        assertEquals("links.duckduckgo.com/m.js", linkFormatEntity[3].url)
        assertEquals("ad_domain", linkFormatEntity[3].adDomainParameterName)
        val allowlistEntity = allowlistCaptor.firstValue
        assertEquals(3, allowlistEntity.size)
        assertEquals("bing.com", allowlistEntity[0].blocklistEntry)
        assertEquals("bat.bing.com", allowlistEntity[0].host)
        assertEquals("ad-company.site", allowlistEntity[1].blocklistEntry)
        assertEquals("convert.ad-company.site", allowlistEntity[1].host)
        assertEquals("ad-company.example", allowlistEntity[2].blocklistEntry)
        assertEquals("convert.ad-company.example", allowlistEntity[2].host)

        val expirationEntity = expirationCaptor.firstValue
        assertEquals(1, expirationEntity.size)
        assertEquals(1800, expirationEntity[0].navigationExpiration)
        assertEquals(604800, expirationEntity[0].totalExpiration)

        val detectionsEntity = detectionsCaptor.firstValue
        assertEquals(1, detectionsEntity.size)
        assertEquals(ENABLED_STATE, detectionsEntity[0].heuristicDetection)
        assertEquals(ENABLED_STATE, detectionsEntity[0].domainDetection)
    }

    @Test
    fun whenFeatureNameMatchesAdClickAttributionAndHasMinSupportedVersionThenStoreMinSupportedVersion() {
        val jsonString = FileUtilities.loadText(javaClass.classLoader!!, "json/ad_click_attribution_min_supported_version.json")

        testee.store(FEATURE_NAME_VALUE, jsonString)

        verify(mockFeatureTogglesRepository).insert(AdClickFeatureToggles(FEATURE_NAME, true, 1234))
    }

    companion object {
        private val FEATURE_NAME = AdClickFeatureName.AdClickAttributionFeatureName
        private val FEATURE_NAME_VALUE = FEATURE_NAME.value
        private const val EMPTY_JSON_STRING = "{}"
        private const val ENABLED_STATE = "enabled"
    }
}
