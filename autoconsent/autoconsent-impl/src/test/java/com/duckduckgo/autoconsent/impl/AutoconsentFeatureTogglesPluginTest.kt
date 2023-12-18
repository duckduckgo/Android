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

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autoconsent.api.AutoconsentFeatureName.Autoconsent
import com.duckduckgo.autoconsent.store.AutoconsentFeatureToggleRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AutoconsentFeatureTogglesPluginTest {

    private val autoconsentFeatureToggleRepository: AutoconsentFeatureToggleRepository = mock()
    private lateinit var plugin: AutoconsentFeatureTogglesPlugin
    private val mockAppBuildConfig: AppBuildConfig = mock()

    @Before
    fun setup() {
        plugin = AutoconsentFeatureTogglesPlugin(autoconsentFeatureToggleRepository, mockAppBuildConfig)
    }

    @Test
    fun whenIsEnabledCalledOnAutoconsentFeatureNameThenReturnRepositoryValue() {
        whenever(autoconsentFeatureToggleRepository.get(Autoconsent, false)).thenReturn(true)
        assertEquals(true, plugin.isEnabled(Autoconsent.value, false))

        whenever(autoconsentFeatureToggleRepository.get(Autoconsent, false)).thenReturn(false)
        assertEquals(false, plugin.isEnabled(Autoconsent.value, false))
    }

    @Test
    fun whenIsEnabledCalledOnOtherFeatureNameThenReturnRepositoryNull() {
        assertNull(plugin.isEnabled(TestFeatureName().value, false))
    }

    @Test
    fun whenIsEnabledAndFeatureIsAutoconsentThenReturnTrueWhenEnabled() = runTest {
        givenAutoconsentFeatureIsEnabled()

        val isEnabled = plugin.isEnabled(Autoconsent.value, true)

        assertTrue(isEnabled!!)
    }

    @Test
    fun whenIsEnabledAndFeatureIsAutoconsentThenReturnFalseWhenDisabled() = runTest {
        givenAutoconsentFeatureIsDisabled()

        val isEnabled = plugin.isEnabled(Autoconsent.value, true)

        assertFalse(isEnabled!!)
    }

    @Test
    fun whenIsEnabledAndFeatureIsAutoconsentThenReturnDefaultValueIfFeatureDoesNotExist() = runTest {
        givenAutoconsentFeatureReturnsDefaultValue(true)
        assertTrue(plugin.isEnabled(Autoconsent.value, true)!!)

        givenAutoconsentFeatureReturnsDefaultValue(false)
        assertFalse(plugin.isEnabled(Autoconsent.value, false)!!)
    }

    @Test
    fun whenIsEnabledAndFeatureIsAutoconsentAndAppVersionEqualToMinSupportedVersionThenReturnTrueWhenEnabled() = runTest {
        givenAutoconsentFeatureIsEnabled()
        givenAppVersionIsEqualToMinSupportedVersion()

        val isEnabled = plugin.isEnabled(Autoconsent.value, true)

        assertTrue(isEnabled!!)
    }

    @Test
    fun whenIsEnabledAndFeatureIsAutoconsentAndAppVersionIsGreaterThanMinSupportedVersionThenReturnTrueWhenEnabled() = runTest {
        givenAutoconsentFeatureIsEnabled()
        givenAppVersionIsGreaterThanMinSupportedVersion()

        val isEnabled = plugin.isEnabled(Autoconsent.value, true)

        assertTrue(isEnabled!!)
    }

    @Test
    fun whenIsEnabledAndFeatureAutoconsentAndAppVersionIsSmallerThanMinSupportedVersionThenReturnFalseWhenEnabled() = runTest {
        givenAutoconsentFeatureIsEnabled()
        givenAppVersionIsSmallerThanMinSupportedVersion()

        val isEnabled = plugin.isEnabled(Autoconsent.value, true)

        assertFalse(isEnabled!!)
    }

    private fun givenAutoconsentFeatureIsEnabled() {
        whenever(autoconsentFeatureToggleRepository.get(Autoconsent, true)).thenReturn(true)
    }

    private fun givenAutoconsentFeatureIsDisabled() {
        whenever(autoconsentFeatureToggleRepository.get(Autoconsent, true)).thenReturn(false)
    }

    private fun givenAutoconsentFeatureReturnsDefaultValue(defaultValue: Boolean) {
        whenever(autoconsentFeatureToggleRepository.get(Autoconsent, defaultValue)).thenReturn(defaultValue)
    }

    private fun givenAppVersionIsEqualToMinSupportedVersion() {
        whenever(autoconsentFeatureToggleRepository.getMinSupportedVersion(Autoconsent)).thenReturn(1234)
        whenever(mockAppBuildConfig.versionCode).thenReturn(1234)
    }

    private fun givenAppVersionIsGreaterThanMinSupportedVersion() {
        whenever(autoconsentFeatureToggleRepository.getMinSupportedVersion(Autoconsent)).thenReturn(1234)
        whenever(mockAppBuildConfig.versionCode).thenReturn(5678)
    }

    private fun givenAppVersionIsSmallerThanMinSupportedVersion() {
        whenever(autoconsentFeatureToggleRepository.getMinSupportedVersion(Autoconsent)).thenReturn(1234)
        whenever(mockAppBuildConfig.versionCode).thenReturn(123)
    }
}

class TestFeatureName(val value: String = "test")
