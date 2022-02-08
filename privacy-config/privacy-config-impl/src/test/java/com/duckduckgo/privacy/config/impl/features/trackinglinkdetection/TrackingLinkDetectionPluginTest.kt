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

package com.duckduckgo.privacy.config.impl.features.trackinglinkdetection

import com.duckduckgo.app.FileUtilities
import com.duckduckgo.privacy.config.store.*
import com.duckduckgo.privacy.config.store.features.trackinglinkdetection.TrackingLinkDetectionRepository
import junit.framework.TestCase.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class TrackingLinkDetectionPluginTest {

    lateinit var testee: TrackingLinkDetectionPlugin

    private val mockFeatureTogglesRepository: PrivacyFeatureTogglesRepository = mock()
    private val mockTrackingLinkDetectionRepository: TrackingLinkDetectionRepository = mock()

    @Before
    fun before() {
        testee = TrackingLinkDetectionPlugin(mockTrackingLinkDetectionRepository, mockFeatureTogglesRepository)
    }

    @Test
    fun whenFeatureNameDoesNotMatchTrackingLinkDetectionThenReturnFalse() {
        assertFalse(testee.store("test", EMPTY_JSON_STRING))
    }

    @Test
    fun whenFeatureNameMatchesTrackingLinkDetectionThenReturnTrue() {
        assertTrue(testee.store(FEATURE_NAME, EMPTY_JSON_STRING))
    }

    @Test
    fun whenFeatureNameMatchesTrackingLinkDetectionAndIsEnabledThenStoreFeatureEnabled() {
        val jsonString = FileUtilities.loadText(TrackingLinkDetectionPluginTest::class.java.classLoader!!, "json/tracking_link_detection.json")

        testee.store(FEATURE_NAME, jsonString)

        verify(mockFeatureTogglesRepository).insert(PrivacyFeatureToggles(FEATURE_NAME, true))
    }

    @Test
    fun whenFeatureNameMatchesTrackingLinkDetectionAndIsNotEnabledThenStoreFeatureDisabled() {
        val jsonString = FileUtilities.loadText(
            TrackingLinkDetectionPluginTest::class.java.classLoader!!,
            "json/tracking_link_detection_disabled.json"
        )

        testee.store(FEATURE_NAME, jsonString)

        verify(mockFeatureTogglesRepository).insert(PrivacyFeatureToggles(FEATURE_NAME, false))
    }

    @Test
    fun whenFeatureNameMatchesTrackingLinkDetectionThenUpdateAllExistingValues() {
        val jsonString = FileUtilities.loadText(TrackingLinkDetectionPluginTest::class.java.classLoader!!, "json/tracking_link_detection.json")

        testee.store(FEATURE_NAME, jsonString)

        val exceptionArgumentCaptor = argumentCaptor<List<TrackingLinkExceptionEntity>>()
        val ampLinkFormatArgumentCaptor = argumentCaptor<List<AmpLinkFormatEntity>>()
        val ampKeywordArgumentCaptor = argumentCaptor<List<AmpKeywordEntity>>()

        verify(mockTrackingLinkDetectionRepository).updateAll(
            exceptionArgumentCaptor.capture(),
            ampLinkFormatArgumentCaptor.capture(),
            ampKeywordArgumentCaptor.capture(),
        )

        val trackingLinkExceptionEntityList = exceptionArgumentCaptor.firstValue

        assertEquals(1, trackingLinkExceptionEntityList.size)
        assertEquals("example.com", trackingLinkExceptionEntityList.first().domain)
        assertEquals("reason", trackingLinkExceptionEntityList.first().reason)

        val ampLinkFormatEntityList = ampLinkFormatArgumentCaptor.firstValue

        assertEquals(1, ampLinkFormatEntityList.size)
        assertEquals("linkFormat", ampLinkFormatEntityList.first().format)

        val ampKeywordEntityList = ampKeywordArgumentCaptor.firstValue

        assertEquals(1, ampKeywordEntityList.size)
        assertEquals("keyword", ampKeywordEntityList.first().keyword)
    }

    companion object {
        private const val FEATURE_NAME = "ampLinks"
        private const val EMPTY_JSON_STRING = "{}"
    }
}
