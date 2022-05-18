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

package com.duckduckgo.mobile.android.vpn.feature

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.feature.toggles.api.FeatureName
import com.duckduckgo.mobile.android.vpn.store.AppTpFeatureToggleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class AppTpFeatureTogglesPluginTest {

    private val appTpFeatureToggleRepository: AppTpFeatureToggleRepository = mock()
    private lateinit var plugin: AppTpFeatureTogglesPlugin
    private val mockAppBuildConfig: AppBuildConfig = mock()

    @Before
    fun setup() {
        plugin = AppTpFeatureTogglesPlugin(appTpFeatureToggleRepository, mockAppBuildConfig)
    }

    @Test
    fun whenIsEnabledCalledOnAppTpFeatureNameThenReturnRepositoryValue() {
        whenever(appTpFeatureToggleRepository.get(AppTpFeatureName.AppTrackerProtection, false)).thenReturn(true)
        assertEquals(true, plugin.isEnabled(AppTpFeatureName.AppTrackerProtection, false))

        whenever(appTpFeatureToggleRepository.get(AppTpFeatureName.AppTrackerProtection, false)).thenReturn(false)
        assertEquals(false, plugin.isEnabled(AppTpFeatureName.AppTrackerProtection, false))
    }

    @Test
    fun whenIsEnabledCalledOnOtherFeatureNameThenReturnRepositoryNull() {
        assertNull(plugin.isEnabled(TestFeatureName(), false))
    }

    @Test
    fun whenIsEnabledAndFeatureIsAppTpFeatureThenReturnTrueWhenEnabled() =
        runTest {
            givenAppTpFeatureIsEnabled()

            val isEnabled = plugin.isEnabled(AppTpFeatureName.AppTrackerProtection, true)

            assertTrue(isEnabled!!)
        }

    @Test
    fun whenIsEnabledAndFeatureIsAppTpFeatureThenReturnFalseWhenDisabled() =
        runTest {
            givenAppTpFeatureIsDisabled()

            val isEnabled = plugin.isEnabled(AppTpFeatureName.AppTrackerProtection, true)

            assertFalse(isEnabled!!)
        }

    @Test
    fun whenIsEnabledAndFeatureIsAppTpFeatureThenReturnDefaultValueIfFeatureDoesNotExist() =
        runTest {
            val defaultValue = true
            givenAppTpFeatureReturnsDefaultValue(defaultValue)

            val isEnabled =
                plugin.isEnabled(AppTpFeatureName.AppTrackerProtection, defaultValue)

            assertEquals(defaultValue, isEnabled)
        }

    @Test
    fun whenIsEnabledAndFeatureIsAppTpFeatureAndAppVersionEqualToMinSupportedVersionThenReturnTrueWhenEnabled() =
        runTest {
            givenAppTpFeatureIsEnabled()
            givenAppVersionIsEqualToMinSupportedVersion()

            val isEnabled = plugin.isEnabled(AppTpFeatureName.AppTrackerProtection, true)

            assertTrue(isEnabled!!)
        }

    @Test
    fun whenIsEnabledAndFeatureIsAppTpFeatureAndAppVersionIsGreaterThanMinSupportedVersionThenReturnTrueWhenEnabled() =
        runTest {
            givenAppTpFeatureIsEnabled()
            givenAppVersionIsGreaterThanMinSupportedVersion()

            val isEnabled = plugin.isEnabled(AppTpFeatureName.AppTrackerProtection, true)

            assertTrue(isEnabled!!)
        }

    @Test
    fun whenIsEnabledAndFeatureIsAppTpFeatureAndAppVersionIsSmallerThanMinSupportedVersionThenReturnFalseWhenEnabled() =
        runTest {
            givenAppTpFeatureIsEnabled()
            givenAppVersionIsSmallerThanMinSupportedVersion()

            val isEnabled = plugin.isEnabled(AppTpFeatureName.AppTrackerProtection, true)

            assertFalse(isEnabled!!)
        }

    private fun givenAppTpFeatureIsEnabled() {
        whenever(
            appTpFeatureToggleRepository.get(
                AppTpFeatureName.AppTrackerProtection, true
            )
        ).thenReturn(true)
    }

    private fun givenAppTpFeatureIsDisabled() {
        whenever(
            appTpFeatureToggleRepository.get(
                AppTpFeatureName.AppTrackerProtection, true
            )
        ).thenReturn(false)
    }

    private fun givenAppTpFeatureReturnsDefaultValue(defaultValue: Boolean) {
        whenever(
            appTpFeatureToggleRepository.get(
                AppTpFeatureName.AppTrackerProtection, defaultValue
            )
        ).thenReturn(defaultValue)
    }

    private fun givenAppVersionIsEqualToMinSupportedVersion() {
        whenever(
            appTpFeatureToggleRepository.getMinSupportedVersion(
                AppTpFeatureName.AppTrackerProtection
            )
        ).thenReturn(1234)

        whenever(mockAppBuildConfig.versionCode).thenReturn(1234)
    }

    private fun givenAppVersionIsGreaterThanMinSupportedVersion() {
        whenever(
            appTpFeatureToggleRepository.getMinSupportedVersion(
                AppTpFeatureName.AppTrackerProtection
            )
        ).thenReturn(1234)

        whenever(mockAppBuildConfig.versionCode).thenReturn(5678)
    }

    private fun givenAppVersionIsSmallerThanMinSupportedVersion() {
        whenever(
            appTpFeatureToggleRepository.getMinSupportedVersion(
                AppTpFeatureName.AppTrackerProtection
            )
        ).thenReturn(1234)

        whenever(mockAppBuildConfig.versionCode).thenReturn(123)
    }
}

class TestFeatureName(override val value: String = "test") : FeatureName
