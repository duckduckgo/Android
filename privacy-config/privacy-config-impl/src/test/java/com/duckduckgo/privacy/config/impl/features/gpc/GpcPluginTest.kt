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

import com.duckduckgo.privacy.config.impl.FileUtilities
import com.duckduckgo.privacy.config.impl.plugins.JsonString
import com.duckduckgo.privacy.config.store.PrivacyFeatureToggles
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.duckduckgo.privacy.config.store.features.gpc.GpcRepository
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers

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
        assertFalse(testee.store("test", JsonString.fromString("{}")))
    }

    @Test
    fun whenFeatureNameMatchesGpcThenReturnTrue() {
        assertTrue(testee.store(FEATURE_NAME, JsonString.fromString("{}")))
    }

    @Test
    fun whenFeatureNameMatchesGpcAndIsEnabledThenStoreFeatureEnabled() {
        val jsonObject = FileUtilities.loadText("json/gpc.json")

        testee.store(FEATURE_NAME, JsonString.fromString(jsonObject))

        verify(mockFeatureTogglesRepository).insert(PrivacyFeatureToggles(FEATURE_NAME, true))
    }

    @Test
    fun whenFeatureNameMatchesGpcAndIsNotEnabledThenStoreFeatureDisabled() {
        val jsonObject = FileUtilities.loadText("json/gpc_disabled.json")

        testee.store(FEATURE_NAME, JsonString.fromString(jsonObject))

        verify(mockFeatureTogglesRepository).insert(PrivacyFeatureToggles(FEATURE_NAME, false))
    }

    @Test
    fun whenFeatureNameMatchesGpcThenUpdateAllExistingExceptions() {
        val jsonObject = FileUtilities.loadText("json/gpc.json")

        testee.store(FEATURE_NAME, JsonString.fromString(jsonObject))

        verify(mockGpcRepository).updateAll(ArgumentMatchers.anyList())
    }

    companion object {
        private const val FEATURE_NAME = "gpc"
    }
}
