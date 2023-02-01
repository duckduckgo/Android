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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.FileUtilities
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.autofill.api.feature.AutofillFeatureName
import com.duckduckgo.autofill.api.feature.AutofillSubfeature
import com.duckduckgo.autofill.api.feature.AutofillSubfeatureName
import com.duckduckgo.autofill.impl.feature.plugin.AutofillFeaturePlugin
import com.duckduckgo.autofill.impl.feature.plugin.AutofillSubFeaturePlugin
import com.duckduckgo.autofill.store.feature.AutofillFeatureRepository
import com.duckduckgo.autofill.store.feature.AutofillFeatureToggleRepository
import com.duckduckgo.autofill.store.feature.AutofillFeatureToggles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyList
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class AutofillFeaturePluginTest {
    lateinit var testee: AutofillFeaturePlugin

    private val mockFeatureTogglesRepository: AutofillFeatureToggleRepository = mock()
    private val mockAutofillRepository: AutofillFeatureRepository = mock()
    private val pluginPoint = FakeAutofillSubFeaturePluginPluginPoint(listOf(FakeAutofillSubFeaturePluginPlugin()))

    @Before
    fun before() {
        testee = AutofillFeaturePlugin(
            autofillFeatureRepository = mockAutofillRepository,
            autofillFeatureToggleRepository = mockFeatureTogglesRepository,
            pluginPoint = pluginPoint,
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

        verify(mockAutofillRepository).updateAllExceptions(anyList())
    }

    @Test
    fun whenPersistPrivacyConfigAndPluginMatchesFeatureNameThenStoreCalled() {
        val jsonString = FileUtilities.loadText(javaClass.classLoader!!, "json/autofill.json")

        testee.store(FEATURE_NAME_VALUE, jsonString)

        val plugin = pluginPoint.getPlugins().first() as FakeAutofillSubFeaturePluginPlugin
        assertEquals(1, plugin.count)
    }

    class FakeAutofillSubFeaturePluginPluginPoint(private val plugins: List<AutofillSubFeaturePlugin>) : PluginPoint<AutofillSubFeaturePlugin> {
        override fun getPlugins(): Collection<AutofillSubFeaturePlugin> {
            return plugins
        }
    }

    class FakeAutofillSubFeaturePluginPlugin : AutofillSubFeaturePlugin {
        var count = 0

        override fun store(
            rawJson: String,
        ): Boolean {
            count++
            return true
        }

        override val settingName: AutofillSubfeature = AutofillSubfeatureName.InjectCredentials
    }

    companion object {
        private val FEATURE_NAME = AutofillFeatureName.Autofill
        private val FEATURE_NAME_VALUE = FEATURE_NAME.value
        private const val EMPTY_JSON_STRING = "{}"
    }
}
