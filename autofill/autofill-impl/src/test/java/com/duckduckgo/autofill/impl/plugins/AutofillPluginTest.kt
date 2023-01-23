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

package com.duckduckgo.autofill.impl.plugins

import com.duckduckgo.app.FileUtilities
import com.duckduckgo.autofill.api.AutofillFeatureName
import com.duckduckgo.autofill.store.AutofillFeatureToggleRepository
import com.duckduckgo.autofill.store.AutofillFeatureToggles
import com.duckduckgo.autofill.store.AutofillRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class AutofillPluginTest {
    lateinit var testee: AutofillPlugin

    private val mockFeatureTogglesRepository: AutofillFeatureToggleRepository = mock()
    private val mockAutofillRepository: AutofillRepository = mock()

    @Before
    fun before() {
        testee = AutofillPlugin(
            mockAutofillRepository,
            mockFeatureTogglesRepository,
        )
    }

    @Test
    fun whenFeatureNameDoesNotMatchAutofillThenReturnFalse() {
        AutofillFeatureName.values().filter { it != FEATURE_NAME }.forEach {
            assertFalse(testee.store(it.value, EMPTY_JSON_STRING))
        }
    }

    @Test
    fun whenFeatureNameMatchesAutofillThenReturnTrue() {
        assertTrue(testee.store(FEATURE_NAME_VALUE, EMPTY_JSON_STRING))
    }

    @Test
    fun whenFeatureNameMatchesAutofillAndIsEnabledThenStoreFeatureEnabled() {
        val jsonString = FileUtilities.loadText(javaClass.classLoader!!, "json/autofill.json")

        testee.store(FEATURE_NAME_VALUE, jsonString)

        verify(mockFeatureTogglesRepository).insert(AutofillFeatureToggles(FEATURE_NAME, true, null))
    }

    @Test
    fun whenFeatureNameMatchesAutofillAndIsNotEnabledThenStoreFeatureDisabled() {
        val jsonString = FileUtilities.loadText(javaClass.classLoader!!, "json/autofill_disabled.json")

        testee.store(FEATURE_NAME_VALUE, jsonString)

        verify(mockFeatureTogglesRepository).insert(AutofillFeatureToggles(FEATURE_NAME, false, null))
    }

    @Test
    fun whenFeatureNameMatchesAutofillAndHasMinSupportedVersionThenStoreMinSupportedVersion() {
        val jsonString = FileUtilities.loadText(javaClass.classLoader!!, "json/autofill_min_supported_version.json")

        testee.store(FEATURE_NAME_VALUE, jsonString)

        verify(mockFeatureTogglesRepository).insert(AutofillFeatureToggles(FEATURE_NAME, true, 1234))
    }

    @Test
    fun whenFeatureNameMatchesAutofillThenUpdateAllExistingExceptions() {
        val jsonString = FileUtilities.loadText(javaClass.classLoader!!, "json/autofill.json")

        testee.store(FEATURE_NAME_VALUE, jsonString)

        verify(mockAutofillRepository).updateAll(ArgumentMatchers.anyList())
    }

    companion object {
        private val FEATURE_NAME = AutofillFeatureName.Autofill
        private val FEATURE_NAME_VALUE = AutofillFeatureName.Autofill.value
        private const val EMPTY_JSON_STRING = "{}"
    }
}
