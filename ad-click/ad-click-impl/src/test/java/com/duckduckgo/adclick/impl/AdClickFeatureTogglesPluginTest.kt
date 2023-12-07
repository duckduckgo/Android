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

package com.duckduckgo.adclick.impl

import com.duckduckgo.adclick.api.AdClickFeatureName
import com.duckduckgo.adclick.store.AdClickFeatureToggleRepository
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AdClickFeatureTogglesPluginTest {

    private val adClickFeatureToggleRepository: AdClickFeatureToggleRepository = mock()
    private lateinit var plugin: AdClickFeatureTogglesPlugin
    private val mockAppBuildConfig: AppBuildConfig = mock()

    @Before
    fun setup() {
        plugin = AdClickFeatureTogglesPlugin(adClickFeatureToggleRepository, mockAppBuildConfig)
    }

    @Test
    fun whenIsEnabledCalledOnAdCLickFeatureNameThenReturnRepositoryValue() {
        whenever(adClickFeatureToggleRepository.get(AdClickFeatureName.AdClickAttributionFeatureName, false)).thenReturn(true)
        Assert.assertEquals(true, plugin.isEnabled(AdClickFeatureName.AdClickAttributionFeatureName.value, false))

        whenever(adClickFeatureToggleRepository.get(AdClickFeatureName.AdClickAttributionFeatureName, false)).thenReturn(false)
        Assert.assertEquals(false, plugin.isEnabled(AdClickFeatureName.AdClickAttributionFeatureName.value, false))
    }

    @Test
    fun whenIsEnabledCalledOnOtherFeatureNameThenReturnRepositoryNull() {
        Assert.assertNull(plugin.isEnabled(TestFeatureName().value, false))
    }

    @Test
    fun whenIsEnabledAndFeatureIsAdCLickeatureThenReturnTrueWhenEnabled() =
        runTest {
            givenAdCLickFeatureIsEnabled()

            val isEnabled = plugin.isEnabled(AdClickFeatureName.AdClickAttributionFeatureName.value, true)

            Assert.assertTrue(isEnabled!!)
        }

    @Test
    fun whenIsEnabledAndFeatureIsAdCLickFeatureThenReturnFalseWhenDisabled() =
        runTest {
            givenAdCLickFeatureIsDisabled()

            val isEnabled = plugin.isEnabled(AdClickFeatureName.AdClickAttributionFeatureName.value, true)

            Assert.assertFalse(isEnabled!!)
        }

    @Test
    fun whenIsEnabledAndFeatureIsAdCLickFeatureThenReturnDefaultValueIfFeatureDoesNotExist() =
        runTest {
            val defaultValue = true
            givenAdCLickFeatureReturnsDefaultValue(defaultValue)

            val isEnabled =
                plugin.isEnabled(AdClickFeatureName.AdClickAttributionFeatureName.value, defaultValue)

            Assert.assertEquals(defaultValue, isEnabled)
        }

    @Test
    fun whenIsEnabledAndFeatureIsAdCLickFeatureAndAppVersionEqualToMinSupportedVersionThenReturnTrueWhenEnabled() =
        runTest {
            givenAdCLickFeatureIsEnabled()
            givenAppVersionIsEqualToMinSupportedVersion()

            val isEnabled = plugin.isEnabled(AdClickFeatureName.AdClickAttributionFeatureName.value, true)

            Assert.assertTrue(isEnabled!!)
        }

    @Test
    fun whenIsEnabledAndFeatureIsAdCLickFeatureAndAppVersionIsGreaterThanMinSupportedVersionThenReturnTrueWhenEnabled() =
        runTest {
            givenAdCLickFeatureIsEnabled()
            givenAppVersionIsGreaterThanMinSupportedVersion()

            val isEnabled = plugin.isEnabled(AdClickFeatureName.AdClickAttributionFeatureName.value, true)

            Assert.assertTrue(isEnabled!!)
        }

    @Test
    fun whenIsEnabledAndFeatureIsAdCLickFeatureAndAppVersionIsSmallerThanMinSupportedVersionThenReturnFalseWhenEnabled() =
        runTest {
            givenAdCLickFeatureIsEnabled()
            givenAppVersionIsSmallerThanMinSupportedVersion()

            val isEnabled = plugin.isEnabled(AdClickFeatureName.AdClickAttributionFeatureName.value, true)

            Assert.assertFalse(isEnabled!!)
        }

    private fun givenAdCLickFeatureIsEnabled() {
        whenever(
            adClickFeatureToggleRepository.get(
                AdClickFeatureName.AdClickAttributionFeatureName,
                true,
            ),
        ).thenReturn(true)
    }

    private fun givenAdCLickFeatureIsDisabled() {
        whenever(
            adClickFeatureToggleRepository.get(
                AdClickFeatureName.AdClickAttributionFeatureName,
                true,
            ),
        ).thenReturn(false)
    }

    private fun givenAdCLickFeatureReturnsDefaultValue(defaultValue: Boolean) {
        whenever(
            adClickFeatureToggleRepository.get(
                AdClickFeatureName.AdClickAttributionFeatureName,
                defaultValue,
            ),
        ).thenReturn(defaultValue)
    }

    private fun givenAppVersionIsEqualToMinSupportedVersion() {
        whenever(
            adClickFeatureToggleRepository.getMinSupportedVersion(
                AdClickFeatureName.AdClickAttributionFeatureName,
            ),
        ).thenReturn(1234)

        whenever(mockAppBuildConfig.versionCode).thenReturn(1234)
    }

    private fun givenAppVersionIsGreaterThanMinSupportedVersion() {
        whenever(
            adClickFeatureToggleRepository.getMinSupportedVersion(
                AdClickFeatureName.AdClickAttributionFeatureName,
            ),
        ).thenReturn(1234)

        whenever(mockAppBuildConfig.versionCode).thenReturn(5678)
    }

    private fun givenAppVersionIsSmallerThanMinSupportedVersion() {
        whenever(
            adClickFeatureToggleRepository.getMinSupportedVersion(
                AdClickFeatureName.AdClickAttributionFeatureName,
            ),
        ).thenReturn(1234)

        whenever(mockAppBuildConfig.versionCode).thenReturn(123)
    }
}

class TestFeatureName(val value: String = "test")
