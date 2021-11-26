/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.privacy.config.impl.features.trackinglinks

import com.duckduckgo.privacy.config.impl.FileUtilities
import com.duckduckgo.privacy.config.store.*
import com.duckduckgo.privacy.config.store.features.trackinglinks.TrackingLinksRepository
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import junit.framework.TestCase.*
import org.junit.Before
import org.junit.Test

class TrackingLinksPluginTest {

    lateinit var testee: TrackingLinksPlugin

    private val mockFeatureTogglesRepository: PrivacyFeatureTogglesRepository = mock()
    private val mockTrackingLinksRepository: TrackingLinksRepository = mock()

    @Before
    fun before() {
        testee = TrackingLinksPlugin(mockTrackingLinksRepository, mockFeatureTogglesRepository)
    }

    @Test
    fun whenFeatureNameDoesNotMatchTrackingLinksThenReturnFalse() {
        assertFalse(testee.store("test", EMPTY_JSON_STRING))
    }

    @Test
    fun whenFeatureNameMatchesTrackingLinksThenReturnTrue() {
        assertTrue(testee.store(FEATURE_NAME, EMPTY_JSON_STRING))
    }

    @Test
    fun whenFeatureNameMatchesTrackingLinksAndIsEnabledThenStoreFeatureEnabled() {
        val jsonString = FileUtilities.loadText("json/tracking_links.json")

        testee.store(FEATURE_NAME, jsonString)

        verify(mockFeatureTogglesRepository).insert(PrivacyFeatureToggles(FEATURE_NAME, true))
    }

    @Test
    fun whenFeatureNameMatchesTrackerAllowlistAndIsNotEnabledThenStoreFeatureDisabled() {
        val jsonString = FileUtilities.loadText("json/tracking_links_disabled.json")

        testee.store(FEATURE_NAME, jsonString)

        verify(mockFeatureTogglesRepository).insert(PrivacyFeatureToggles(FEATURE_NAME, false))
    }

    @Test
    fun whenFeatureNameMatchesTrackerAllowlistThenUpdateAllExistingValues() {
        val jsonString = FileUtilities.loadText("json/tracking_links.json")

        testee.store(FEATURE_NAME, jsonString)

        val exceptionArgumentCaptor = argumentCaptor<List<TrackingLinksExceptionEntity>>()
        val ampLinkFormatArgumentCaptor = argumentCaptor<List<AmpLinkFormatEntity>>()
        val ampKeywordArgumentCaptor = argumentCaptor<List<AmpKeywordEntity>>()
        val trackingParameterArgumentCaptor = argumentCaptor<List<TrackingParameterEntity>>()

        verify(mockTrackingLinksRepository).updateAll(
            exceptionArgumentCaptor.capture(),
            ampLinkFormatArgumentCaptor.capture(),
            ampKeywordArgumentCaptor.capture(),
            trackingParameterArgumentCaptor.capture()
        )

        val trackingLinksExceptionEntityList = exceptionArgumentCaptor.firstValue

        assertEquals(1, trackingLinksExceptionEntityList.size)
        assertEquals("example.com", trackingLinksExceptionEntityList.first().domain)
        assertEquals("reason", trackingLinksExceptionEntityList.first().reason)

        val ampLinkFormatEntityList = ampLinkFormatArgumentCaptor.firstValue

        assertEquals(1, ampLinkFormatEntityList.size)
        assertEquals("ampLinkFormat", ampLinkFormatEntityList.first().format)

        val ampKeywordEntityList = ampKeywordArgumentCaptor.firstValue

        assertEquals(1, ampKeywordEntityList.size)
        assertEquals("ampKeyword", ampKeywordEntityList.first().keyword)

        val trackingParameterEntityList = trackingParameterArgumentCaptor.firstValue

        assertEquals(1, trackingParameterEntityList.size)
        assertEquals("trackingParameter", trackingParameterEntityList.first().parameter)
    }

    companion object {
        private const val FEATURE_NAME = "trackingLinks"
        private const val EMPTY_JSON_STRING = "{}"
    }
}
