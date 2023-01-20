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
import com.duckduckgo.autofill.api.feature.AutofillFeatureName
import com.duckduckgo.autofill.impl.feature.plugin.AutofillFeaturePlugin
import com.duckduckgo.autofill.impl.feature.plugin.AutofillSubfeatureJsonParser
import com.duckduckgo.autofill.store.AutofillExceptionEntity
import com.duckduckgo.autofill.store.feature.AutofillFeatureRepository
import com.duckduckgo.autofill.store.feature.AutofillFeatureToggleRepository
import com.duckduckgo.autofill.store.feature.AutofillFeatureToggles
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class AutofillFeaturePluginTest {
    lateinit var testee: AutofillFeaturePlugin

    private val mockFeatureTogglesRepository: AutofillFeatureToggleRepository = mock()
    private val mockAutofillRepository: AutofillFeatureRepository = mock()
    private val mockAutofillSubfeatureParser: AutofillSubfeatureJsonParser = mock()
    private val captor = argumentCaptor<List<AutofillExceptionEntity>>()

    @Before
    fun before() {
        testee = AutofillFeaturePlugin(
            autofillFeatureRepository = mockAutofillRepository,
            autofillFeatureToggleRepository = mockFeatureTogglesRepository,
            moshi = Moshi.Builder().build(),
            autofillSubfeatureJsonParser = mockAutofillSubfeatureParser,
        )
    }

    @Test
    fun whenFeatureNameDoesNotMatchAutofillThenReturnFalse() {
        assertFalse(testee.store("non-autofill-feature", EMPTY_JSON_STRING))
    }

    @Test
    fun whenFeatureNameMatchesAutofillThenReturnTrue() {
        assertTrue(testee.store(FEATURE_NAME_VALUE, EMPTY_JSON_STRING))
    }

    @Test
    fun whenJsonSpecifiesFeatureIsEnabledThenStoreFeatureEnabled() {
        testee.store(FEATURE_NAME_VALUE, "autofill.json".loadJson())
        verify(mockFeatureTogglesRepository).insert(AutofillFeatureToggles(FEATURE_NAME, true, anyOrNull()))
    }

    @Test
    fun whenJsonSpecifiesFeatureIsDisabledThenStoreFeatureDisabled() {
        testee.store(FEATURE_NAME_VALUE, "autofill_disabled.json".loadJson())
        verify(mockFeatureTogglesRepository).insert(AutofillFeatureToggles(FEATURE_NAME, false, anyOrNull()))
    }

    @Test
    fun whenJsonSpecifiesNoMinimumSupportedVersionThenNullSaved() {
        testee.store(FEATURE_NAME_VALUE, "autofill_no_min_supported_version.json".loadJson())
        verify(mockFeatureTogglesRepository).insert(AutofillFeatureToggles(FEATURE_NAME, true, null))
    }

    @Test
    fun whenJsonSpecifiesMinimumSupportedVersionThenMinVersionIsSaved() {
        testee.store(FEATURE_NAME_VALUE, "autofill_min_supported_version_specified.json".loadJson())
        verify(mockFeatureTogglesRepository).insert(AutofillFeatureToggles(FEATURE_NAME, true, 1234))
    }

    @Test
    fun whenJsonHasEmptyExceptionsThenUpdatedWithEmptyList() {
        testee.store(FEATURE_NAME_VALUE, "autofill_empty_exceptions.json".loadJson())
        verify(mockAutofillRepository).updateAllExceptions(captor.capture())
        assertTrue(captor.firstValue.isEmpty())
    }

    @Test
    fun whenJsonHasExceptionsThenExistingExceptionsCleared() {
        testee.store(FEATURE_NAME_VALUE, "autofill_multiple_exceptions.json".loadJson())
        verify(mockAutofillRepository).updateAllExceptions(captor.capture())
        assertEquals(2, captor.firstValue.size)
    }

    @Test
    fun whenJsonHasEmptySettingsBlockThenSubfeatureParserNotCalled() {
        testee.store(FEATURE_NAME_VALUE, "autofill_empty_settings.json".loadJson())
        verify(mockAutofillSubfeatureParser, never()).processSubfeatures(anyOrNull())
    }

    private fun String.loadJson(): String = FileUtilities.loadText(this@AutofillFeaturePluginTest.javaClass.classLoader!!, "json/$this")

    companion object {
        private val FEATURE_NAME = AutofillFeatureName.Autofill
        private val FEATURE_NAME_VALUE = FEATURE_NAME.value
        private const val EMPTY_JSON_STRING = "{}"
    }
}
