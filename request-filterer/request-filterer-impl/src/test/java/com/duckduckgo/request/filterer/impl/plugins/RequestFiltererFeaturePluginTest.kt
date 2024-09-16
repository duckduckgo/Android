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

package com.duckduckgo.request.filterer.impl.plugins

import com.duckduckgo.common.test.FileUtilities
import com.duckduckgo.request.filterer.api.RequestFiltererFeatureName
import com.duckduckgo.request.filterer.store.RequestFiltererExceptionEntity
import com.duckduckgo.request.filterer.store.RequestFiltererFeatureToggleRepository
import com.duckduckgo.request.filterer.store.RequestFiltererFeatureToggles
import com.duckduckgo.request.filterer.store.RequestFiltererRepository
import com.duckduckgo.request.filterer.store.SettingsEntity
import junit.framework.TestCase
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class RequestFiltererFeaturePluginTest {
    lateinit var testee: RequestFiltererFeaturePlugin

    private val mockFeatureTogglesRepository: RequestFiltererFeatureToggleRepository = mock()
    private val mockRepository: RequestFiltererRepository = mock()

    @Before
    fun before() {
        testee = RequestFiltererFeaturePlugin(mockRepository, mockFeatureTogglesRepository)
    }

    @Test
    fun whenFeatureNameDoesNotMatchRequestFiltererThenReturnFalse() {
        RequestFiltererFeatureName.values().filter { it != FEATURE_NAME }.forEach {
            assertFalse(testee.store(it.value, EMPTY_JSON_STRING))
        }
    }

    @Test
    fun whenFeatureNameMatchesRequestFiltererThenReturnTrue() {
        TestCase.assertTrue(testee.store(FEATURE_NAME_VALUE, EMPTY_JSON_STRING))
    }

    @Test
    fun whenFeatureNameMatchesRequestFiltererAndIsEnabledThenStoreFeatureEnabled() {
        val jsonString = FileUtilities.loadText(RequestFiltererFeaturePluginTest::class.java.classLoader!!, "json/request_filterer.json")

        testee.store(FEATURE_NAME_VALUE, jsonString)

        verify(mockFeatureTogglesRepository).insert(RequestFiltererFeatureToggles(FEATURE_NAME, true, null))
    }

    @Test
    fun whenFeatureNameMatchesRequestFiltererAndIsNotEnabledThenStoreFeatureDisabled() {
        val jsonString = FileUtilities.loadText(
            RequestFiltererFeaturePluginTest::class.java.classLoader!!,
            "json/request_filterer_disabled.json",
        )

        testee.store(FEATURE_NAME_VALUE, jsonString)

        verify(mockFeatureTogglesRepository).insert(RequestFiltererFeatureToggles(FEATURE_NAME, false, null))
    }

    @Test
    fun whenFeatureNameMatchesRequestFiltererAndHasMinSupportedVersionThenStoreMinSupportedVersion() {
        val jsonString =
            FileUtilities.loadText(
                RequestFiltererFeaturePluginTest::class.java.classLoader!!,
                "json/request_filterer_min_supported_version.json",
            )

        testee.store(FEATURE_NAME_VALUE, jsonString)

        verify(mockFeatureTogglesRepository).insert(RequestFiltererFeatureToggles(FEATURE_NAME, true, 1234))
    }

    @Test
    fun whenFeatureNameMatchesRequestFiltererThenUpdateAllExistingValues() {
        val jsonString = FileUtilities.loadText(RequestFiltererFeaturePluginTest::class.java.classLoader!!, "json/request_filterer.json")

        testee.store(FEATURE_NAME_VALUE, jsonString)

        val exceptionArgumentCaptor = argumentCaptor<List<RequestFiltererExceptionEntity>>()
        val settingsArgumentCaptor = argumentCaptor<SettingsEntity>()

        verify(mockRepository).updateAll(
            exceptionArgumentCaptor.capture(),
            settingsArgumentCaptor.capture(),
        )

        val exceptionEntityList = exceptionArgumentCaptor.firstValue

        assertEquals(1, exceptionEntityList.size)
        assertEquals("example.com", exceptionEntityList.first().domain)
        assertEquals("reason", exceptionEntityList.first().reason)

        val settingsEntity = settingsArgumentCaptor.firstValue

        assertEquals(100, settingsEntity.windowInMs)
    }

    companion object {
        private val FEATURE_NAME = RequestFiltererFeatureName.RequestFilterer
        private val FEATURE_NAME_VALUE = FEATURE_NAME.value
        private const val EMPTY_JSON_STRING = "{}"
    }
}
