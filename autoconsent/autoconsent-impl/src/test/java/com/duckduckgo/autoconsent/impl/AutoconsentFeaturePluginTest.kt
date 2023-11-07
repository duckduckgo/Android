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

package com.duckduckgo.autoconsent.impl

import com.duckduckgo.autoconsent.api.AutoconsentFeatureName
import com.duckduckgo.autoconsent.store.AutoconsentExceptionEntity
import com.duckduckgo.autoconsent.store.AutoconsentFeatureToggleRepository
import com.duckduckgo.autoconsent.store.AutoconsentFeatureToggles
import com.duckduckgo.autoconsent.store.AutoconsentRepository
import com.duckduckgo.autoconsent.store.DisabledCmpsEntity
import com.duckduckgo.common.test.FileUtilities
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class AutoconsentFeaturePluginTest {

    lateinit var plugin: AutoconsentFeaturePlugin

    private val mockAutoconsentRepository: AutoconsentRepository = mock()
    private val mockAutoconsentFeatureToggleRepository: AutoconsentFeatureToggleRepository = mock()

    @Before
    fun before() {
        plugin = AutoconsentFeaturePlugin(mockAutoconsentRepository, mockAutoconsentFeatureToggleRepository)
    }

    @Test
    fun whenFeatureNameDoesNotMatchAutoconsentFeatureNameValuesThenReturnFalse() {
        AutoconsentFeatureName.values().filter { it != FEATURE_NAME }.forEach {
            assertFalse(plugin.store(it.value, EMPTY_JSON_STRING))
        }
    }

    @Test
    fun whenFeatureNameMatchesAutoconsentThenReturnTrue() {
        assertTrue(plugin.store(FEATURE_NAME_VALUE, EMPTY_JSON_STRING))
    }

    @Test
    fun whenFeatureNameMatchesAutoconsentAndIsEnabledThenStoreFeatureEnabled() {
        val jsonString = FileUtilities.loadText(javaClass.classLoader!!, "json/autoconsent.json")

        plugin.store(FEATURE_NAME_VALUE, jsonString)

        verify(mockAutoconsentFeatureToggleRepository).insert(AutoconsentFeatureToggles(FEATURE_NAME, true, null))
    }

    @Test
    fun whenFeatureNameMatchesAutoconsentAndIsNotEnabledThenStoreFeatureDisabled() {
        val jsonString = FileUtilities.loadText(javaClass.classLoader!!, "json/autoconsent_disabled.json")

        plugin.store(FEATURE_NAME_VALUE, jsonString)

        verify(mockAutoconsentFeatureToggleRepository).insert(AutoconsentFeatureToggles(FEATURE_NAME, false, null))
    }

    @Test
    fun whenFeatureNameMatchesAutoconsentThenUpdateAllExistingLists() {
        val jsonString = FileUtilities.loadText(javaClass.classLoader!!, "json/autoconsent.json")

        plugin.store(FEATURE_NAME_VALUE, jsonString)

        val exceptions = argumentCaptor<List<AutoconsentExceptionEntity>>()
        val disabledCmps = argumentCaptor<List<DisabledCmpsEntity>>()

        verify(mockAutoconsentRepository).updateAll(exceptions = exceptions.capture(), disabledCmps = disabledCmps.capture())

        val exceptionsEntity = exceptions.firstValue
        assertEquals(2, exceptionsEntity.size)

        val disabledCmpsEntity = disabledCmps.firstValue
        assertEquals(1, disabledCmpsEntity.size)
    }

    @Test
    fun whenFeatureNameMatchesAdClickAttributionAndHasMinSupportedVersionThenStoreMinSupportedVersion() {
        val jsonString = FileUtilities.loadText(javaClass.classLoader!!, "json/autoconsent_min_supported_version.json")

        plugin.store(FEATURE_NAME_VALUE, jsonString)

        verify(mockAutoconsentFeatureToggleRepository).insert(AutoconsentFeatureToggles(FEATURE_NAME, true, 1234))
    }

    companion object {
        private val FEATURE_NAME = AutoconsentFeatureName.Autoconsent
        private val FEATURE_NAME_VALUE = FEATURE_NAME.value
        private const val EMPTY_JSON_STRING = "{}"
    }
}
