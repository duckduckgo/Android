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

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.feature.AutofillFeatureName
import com.duckduckgo.autofill.impl.feature.plugin.AutofillFeatureTogglesPlugin
import com.duckduckgo.autofill.store.feature.AutofillFeatureToggleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class AutofillFeatureTogglesPluginTest {

    @get:Rule var coroutineRule = CoroutineTestRule()
    lateinit var testee: AutofillFeatureTogglesPlugin

    private val mockFeatureTogglesRepository: AutofillFeatureToggleRepository = mock()
    private val mockAppBuildConfig: AppBuildConfig = mock()

    @Before
    fun before() {
        testee = AutofillFeatureTogglesPlugin(mockFeatureTogglesRepository, mockAppBuildConfig)
    }

    @Test
    fun whenIsEnabledAndFeatureIsNotAutofillFeatureThenReturnNull() = runTest {
        assertNull(testee.isEnabled(NonPrivacyFeature().value, true))
    }

    @Test
    fun whenIsEnabledAndFeatureIsAutofillFeatureThenReturnTrueWhenEnabled() = runTest {
        givenAutofillFeatureIsEnabled()

        val isEnabled = testee.isEnabled(AutofillFeatureName.Autofill.value, true)

        assertTrue(isEnabled!!)
    }

    @Test
    fun whenIsEnabledAndFeatureIsAutofillFeatureThenReturnFalseWhenDisabled() = runTest {
        givenAutofillFeatureIsDisabled()

        val isEnabled = testee.isEnabled(AutofillFeatureName.Autofill.value, true)

        assertFalse(isEnabled!!)
    }

    @Test
    fun whenIsEnabledAndFeatureIsAutofillFeatureThenReturnDefaultValueIfFeatureDoesNotExist() = runTest {
        val defaultValue = true
        givenAutofillFeatureReturnsDefaultValue(defaultValue)

        val isEnabled =
            testee.isEnabled(AutofillFeatureName.Autofill.value, defaultValue)

        assertEquals(defaultValue, isEnabled)
    }

    @Test
    fun whenIsEnabledAndFeatureIsAutofillFeatureAndAppVersionEqualToMinSupportedVersionThenReturnTrueWhenEnabled() = runTest {
        givenAutofillFeatureIsEnabled()
        givenAppVersionIsEqualToMinSupportedVersion()

        val isEnabled = testee.isEnabled(AutofillFeatureName.Autofill.value, true)

        assertTrue(isEnabled!!)
    }

    @Test
    fun whenIsEnabledAndFeatureIsAutofillFeatureAndAppVersionIsGreaterThanMinSupportedVersionThenReturnTrueWhenEnabled() = runTest {
        givenAutofillFeatureIsEnabled()
        givenAppVersionIsGreaterThanMinSupportedVersion()

        val isEnabled = testee.isEnabled(AutofillFeatureName.Autofill.value, true)

        assertTrue(isEnabled!!)
    }

    @Test
    fun whenIsEnabledAndFeatureIsAutofillFeatureAndAppVersionIsSmallerThanMinSupportedVersionThenReturnFalseWhenEnabled() = runTest {
        givenAutofillFeatureIsEnabled()
        givenAppVersionIsSmallerThanMinSupportedVersion()

        val isEnabled = testee.isEnabled(AutofillFeatureName.Autofill.value, true)

        assertFalse(isEnabled!!)
    }

    private fun givenAutofillFeatureIsEnabled() {
        whenever(mockFeatureTogglesRepository.get(AutofillFeatureName.Autofill, true)).thenReturn(true)
    }

    private fun givenAutofillFeatureIsDisabled() {
        whenever(mockFeatureTogglesRepository.get(AutofillFeatureName.Autofill, true)).thenReturn(false)
    }

    private fun givenAutofillFeatureReturnsDefaultValue(defaultValue: Boolean) {
        whenever(mockFeatureTogglesRepository.get(AutofillFeatureName.Autofill, defaultValue)).thenReturn(defaultValue)
    }

    private fun givenAppVersionIsEqualToMinSupportedVersion() {
        whenever(mockFeatureTogglesRepository.getMinSupportedVersion(AutofillFeatureName.Autofill)).thenReturn(1234)
        whenever(mockAppBuildConfig.versionCode).thenReturn(1234)
    }

    private fun givenAppVersionIsGreaterThanMinSupportedVersion() {
        whenever(mockFeatureTogglesRepository.getMinSupportedVersion(AutofillFeatureName.Autofill)).thenReturn(1234)

        whenever(mockAppBuildConfig.versionCode).thenReturn(5678)
    }

    private fun givenAppVersionIsSmallerThanMinSupportedVersion() {
        whenever(mockFeatureTogglesRepository.getMinSupportedVersion(AutofillFeatureName.Autofill)).thenReturn(1234)
        whenever(mockAppBuildConfig.versionCode).thenReturn(123)
    }

    data class NonPrivacyFeature(val value: String = "test")
}
