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

package com.duckduckgo.privacy.config.impl.features.trackingparameters

import com.duckduckgo.app.FileUtilities
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.store.*
import com.duckduckgo.privacy.config.store.features.trackingparameters.TrackingParametersRepository
import junit.framework.TestCase.*
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class TrackingParametersPluginTest {

    lateinit var testee: TrackingParametersPlugin

    private val mockFeatureTogglesRepository: PrivacyFeatureTogglesRepository = mock()
    private val mockTrackingParametersRepository: TrackingParametersRepository = mock()

    @Before
    fun before() {
        testee = TrackingParametersPlugin(mockTrackingParametersRepository, mockFeatureTogglesRepository)
    }

    @Test
    fun whenFeatureNameDoesNotMatchTrackingParametersThenReturnFalse() {
        PrivacyFeatureName.values().filter { it != FEATURE_NAME }.forEach {
            Assert.assertFalse(testee.store(it, EMPTY_JSON_STRING))
        }
    }

    @Test
    fun whenFeatureNameMatchesTrackingParametersThenReturnTrue() {
        assertTrue(testee.store(FEATURE_NAME, EMPTY_JSON_STRING))
    }

    @Test
    fun whenFeatureNameMatchesTrackingParametersAndIsEnabledThenStoreFeatureEnabled() {
        val jsonString = FileUtilities.loadText(TrackingParametersPluginTest::class.java.classLoader!!, "json/tracking_parameters.json")

        testee.store(FEATURE_NAME, jsonString)

        verify(mockFeatureTogglesRepository).insert(PrivacyFeatureToggles(FEATURE_NAME, true))
    }

    @Test
    fun whenFeatureNameMatchesTrackingParametersAndIsNotEnabledThenStoreFeatureDisabled() {
        val jsonString = FileUtilities.loadText(
            TrackingParametersPluginTest::class.java.classLoader!!,
            "json/tracking_parameters_disabled.json"
        )

        testee.store(FEATURE_NAME, jsonString)

        verify(mockFeatureTogglesRepository).insert(PrivacyFeatureToggles(FEATURE_NAME, false))
    }

    @Test
    fun whenFeatureNameMatchesTrackingParametersThenUpdateAllExistingValues() {
        val jsonString = FileUtilities.loadText(TrackingParametersPluginTest::class.java.classLoader!!, "json/tracking_parameters.json")

        testee.store(FEATURE_NAME, jsonString)

        val exceptionArgumentCaptor = argumentCaptor<List<TrackingParameterExceptionEntity>>()
        val trackingParameterArgumentCaptor = argumentCaptor<List<TrackingParameterEntity>>()

        verify(mockTrackingParametersRepository).updateAll(
            exceptionArgumentCaptor.capture(),
            trackingParameterArgumentCaptor.capture()
        )

        val trackingParameterExceptionEntityList = exceptionArgumentCaptor.firstValue

        assertEquals(1, trackingParameterExceptionEntityList.size)
        assertEquals("example.com", trackingParameterExceptionEntityList.first().domain)
        assertEquals("reason", trackingParameterExceptionEntityList.first().reason)

        val trackingParameterEntityList = trackingParameterArgumentCaptor.firstValue

        assertEquals(1, trackingParameterEntityList.size)
        assertEquals("parameter", trackingParameterEntityList.first().parameter)
    }

    companion object {
        private val FEATURE_NAME = PrivacyFeatureName.TrackingParametersFeatureName
        private const val EMPTY_JSON_STRING = "{}"
    }
}
