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

package com.duckduckgo.privacy.config.impl.features.autofill

import com.duckduckgo.app.FileUtilities
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.store.PrivacyFeatureToggles
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.duckduckgo.privacy.config.store.features.autofill.AutofillRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class AutofillPluginTest {
    lateinit var testee: AutofillPlugin

    private val mockFeatureTogglesRepository: PrivacyFeatureTogglesRepository = mock()
    private val mockAutofillRepository: AutofillRepository = mock()

    @Before
    fun before() {
        testee = AutofillPlugin(mockAutofillRepository, mockFeatureTogglesRepository)
    }

    @Test
    fun whenFeatureNameDoesNotMatchHttpsThenReturnFalse() {
        PrivacyFeatureName.values().filter { it != FEATURE_NAME }.forEach {
            assertFalse(testee.store(it.value, EMPTY_JSON_STRING))
        }
    }

    @Test
    fun whenFeatureNameMatchesHttpsThenReturnTrue() {
        assertTrue(testee.store(FEATURE_NAME_VALUE, EMPTY_JSON_STRING))
    }

    @Test
    fun whenFeatureNameMatchesHttpsAndIsEnabledThenStoreFeatureEnabled() {
        val jsonString = FileUtilities.loadText(javaClass.classLoader!!, "json/autofill.json")

        testee.store(FEATURE_NAME_VALUE, jsonString)

        verify(mockFeatureTogglesRepository).insert(PrivacyFeatureToggles(FEATURE_NAME_VALUE, true, null))
    }

    @Test
    fun whenFeatureNameMatchesHttpsAndIsNotEnabledThenStoreFeatureDisabled() {
        val jsonString = FileUtilities.loadText(javaClass.classLoader!!, "json/autofill_disabled.json")

        testee.store(FEATURE_NAME_VALUE, jsonString)

        verify(mockFeatureTogglesRepository).insert(PrivacyFeatureToggles(FEATURE_NAME_VALUE, false, null))
    }

    @Test
    fun whenFeatureNameMatchesHttpsAndHasMinSupportedVersionThenStoreMinSupportedVersion() {
        val jsonString = FileUtilities.loadText(javaClass.classLoader!!, "json/autofill_min_supported_version.json")

        testee.store(FEATURE_NAME_VALUE, jsonString)

        verify(mockFeatureTogglesRepository).insert(PrivacyFeatureToggles(FEATURE_NAME_VALUE, true, 1234))
    }

    @Test
    fun whenFeatureNameMatchesHttpsThenUpdateAllExistingExceptions() {
        val jsonString = FileUtilities.loadText(javaClass.classLoader!!, "json/autofill.json")

        testee.store(FEATURE_NAME_VALUE, jsonString)

        verify(mockAutofillRepository).updateAll(ArgumentMatchers.anyList())
    }

    companion object {
        private val FEATURE_NAME = PrivacyFeatureName.AutofillFeatureName
        private val FEATURE_NAME_VALUE = PrivacyFeatureName.AutofillFeatureName.value
        private const val EMPTY_JSON_STRING = "{}"
    }
}
