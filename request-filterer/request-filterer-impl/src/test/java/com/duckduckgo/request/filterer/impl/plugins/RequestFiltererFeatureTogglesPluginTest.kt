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

package com.duckduckgo.request.filterer.impl.plugins

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.request.filterer.api.RequestFiltererFeatureName.RequestFilterer
import com.duckduckgo.request.filterer.store.RequestFiltererFeatureToggleRepository
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

class RequestFiltererFeatureTogglesPluginTest {

    @get:Rule var coroutineRule = CoroutineTestRule()
    lateinit var testee: RequestFiltererFeatureTogglesPlugin

    private val mockFeatureTogglesRepository: RequestFiltererFeatureToggleRepository = mock()
    private val mockAppBuildConfig: AppBuildConfig = mock()

    @Before
    fun before() {
        testee = RequestFiltererFeatureTogglesPlugin(mockFeatureTogglesRepository, mockAppBuildConfig)
    }

    @Test
    fun whenIsEnabledAndFeatureIsNotRequestFiltererFeatureThenReturnNull() = runTest {
        assertNull(testee.isEnabled(NonRequestFiltererFeature().value, true))
    }

    @Test
    fun whenIsEnabledAndFeatureIsRequestFiltererFeatureThenReturnTrueWhenEnabled() =
        runTest {
            givenRequestFiltererFeatureIsEnabled()

            val isEnabled = testee.isEnabled(RequestFilterer.value, true)

            assertTrue(isEnabled!!)
        }

    @Test
    fun whenIsEnabledAndFeatureIsRequestFiltererFeatureThenReturnFalseWhenDisabled() =
        runTest {
            givenRequestFiltererFeatureIsDisabled()

            val isEnabled = testee.isEnabled(RequestFilterer.value, true)

            assertFalse(isEnabled!!)
        }

    @Test
    fun whenIsEnabledAndFeatureIsRequestFiltererFeatureThenReturnDefaultValueIfFeatureDoesNotExist() =
        runTest {
            val defaultValue = true
            givenRequestFiltererFeatureReturnsDefaultValue(defaultValue)

            val isEnabled =
                testee.isEnabled(RequestFilterer.value, defaultValue)

            assertEquals(defaultValue, isEnabled)
        }

    @Test
    fun whenIsEnabledAndFeatureIsRequestFiltererFeatureAndAppVersionEqualToMinSupportedVersionThenReturnTrueWhenEnabled() =
        runTest {
            givenRequestFiltererFeatureIsEnabled()
            givenAppVersionIsEqualToMinSupportedVersion()

            val isEnabled = testee.isEnabled(RequestFilterer.value, true)

            assertTrue(isEnabled!!)
        }

    @Test
    fun whenIsEnabledAndFeatureIsRequestFiltererFeatureAndAppVersionIsGreaterThanMinSupportedVersionThenReturnTrueWhenEnabled() =
        runTest {
            givenRequestFiltererFeatureIsEnabled()
            givenAppVersionIsGreaterThanMinSupportedVersion()

            val isEnabled = testee.isEnabled(RequestFilterer.value, true)

            assertTrue(isEnabled!!)
        }

    @Test
    fun whenIsEnabledAndFeatureIsRequestFiltererFeatureAndAppVersionIsSmallerThanMinSupportedVersionThenReturnFalseWhenEnabled() =
        runTest {
            givenRequestFiltererFeatureIsEnabled()
            givenAppVersionIsSmallerThanMinSupportedVersion()

            val isEnabled = testee.isEnabled(RequestFilterer.value, true)

            assertFalse(isEnabled!!)
        }

    private fun givenRequestFiltererFeatureIsEnabled() {
        whenever(mockFeatureTogglesRepository.get(RequestFilterer, true)).thenReturn(true)
    }

    private fun givenRequestFiltererFeatureIsDisabled() {
        whenever(mockFeatureTogglesRepository.get(RequestFilterer, true)).thenReturn(false)
    }

    private fun givenRequestFiltererFeatureReturnsDefaultValue(defaultValue: Boolean) {
        whenever(mockFeatureTogglesRepository.get(RequestFilterer, defaultValue)).thenReturn(defaultValue)
    }

    private fun givenAppVersionIsEqualToMinSupportedVersion() {
        whenever(mockFeatureTogglesRepository.getMinSupportedVersion(RequestFilterer)).thenReturn(1234)
        whenever(mockAppBuildConfig.versionCode).thenReturn(1234)
    }

    private fun givenAppVersionIsGreaterThanMinSupportedVersion() {
        whenever(
            mockFeatureTogglesRepository.getMinSupportedVersion(
                RequestFilterer,
            ),
        ).thenReturn(1234)

        whenever(mockAppBuildConfig.versionCode).thenReturn(5678)
    }

    private fun givenAppVersionIsSmallerThanMinSupportedVersion() {
        whenever(mockFeatureTogglesRepository.getMinSupportedVersion(RequestFilterer)).thenReturn(1234)
        whenever(mockAppBuildConfig.versionCode).thenReturn(123)
    }

    data class NonRequestFiltererFeature(val value: String = "test")
}
