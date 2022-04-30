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

package com.duckduckgo.privacy.config.impl.features.gpc

import com.duckduckgo.app.FileUtilities
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.store.PrivacyFeatureToggles
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.duckduckgo.privacy.config.store.features.gpc.GpcRepository
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList

class GpcPluginTest {
    lateinit var testee: GpcPlugin

    private val mockFeatureTogglesRepository: PrivacyFeatureTogglesRepository = mock()
    private val mockGpcRepository: GpcRepository = mock()

    @Before
    fun before() {
        testee = GpcPlugin(mockGpcRepository, mockFeatureTogglesRepository)
    }

    @Test
    fun whenFeatureNameDoesNotMatchGpcThenReturnFalse() {
        PrivacyFeatureName.values().filter { it != FEATURE_NAME }.forEach {
            assertFalse(testee.store(it, EMPTY_JSON_STRING))
        }
    }

    @Test
    fun whenFeatureNameMatchesGpcThenReturnTrue() {
        assertTrue(testee.store(FEATURE_NAME, EMPTY_JSON_STRING))
    }

    @Test
    fun whenFeatureNameMatchesGpcAndIsEnabledThenStoreFeatureEnabled() {
        val jsonString = FileUtilities.loadText(javaClass.classLoader!!, "json/gpc.json")

        testee.store(FEATURE_NAME, jsonString)

        verify(mockFeatureTogglesRepository).insert(PrivacyFeatureToggles(FEATURE_NAME, true))
    }

    @Test
    fun whenFeatureNameMatchesGpcAndIsNotEnabledThenStoreFeatureDisabled() {
        val jsonString = FileUtilities.loadText(javaClass.classLoader!!, "json/gpc_disabled.json")

        testee.store(FEATURE_NAME, jsonString)

        verify(mockFeatureTogglesRepository).insert(PrivacyFeatureToggles(FEATURE_NAME, false))
    }

    @Test
    fun whenFeatureNameMatchesGpcThenUpdateAllExistingExceptionsAndHeaders() {
        val jsonString = FileUtilities.loadText(javaClass.classLoader!!, "json/gpc.json")

        testee.store(FEATURE_NAME, jsonString)

        verify(mockGpcRepository).updateAll(anyList(), anyList())
    }

    companion object {
        private val FEATURE_NAME = PrivacyFeatureName.GpcFeatureName
        private const val EMPTY_JSON_STRING = "{}"
    }
}
